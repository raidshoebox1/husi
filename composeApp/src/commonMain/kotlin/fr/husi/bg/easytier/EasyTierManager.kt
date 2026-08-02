package fr.husi.bg.easytier

import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.plugin.PluginManager
import fr.husi.repository.resolveRepository
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.delay

object EasyTierManager {

    private const val TAG = "EasyTier"
    private const val SOCKS5_PROBE_RETRIES = 10
    private const val SOCKS5_PROBE_INTERVAL_MS = 500L

    @Volatile
    private var running = false

    @Volatile
    private var socks5Port: Int = 0

    @Volatile
    private var rpcPort: Int = 0

    @Volatile
    private var meshCidrs: List<String> = emptyList()

    @Volatile
    private var configFile: File? = null

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var process: Process? = null

    private val logBuffer = StringBuffer()

    @Volatile
    private var lastError: String? = null

    fun isEnabled(): Boolean = DataStore.easyTierEnabled && DataStore.easyTierNetworkName.isNotBlank()

    fun isRunning(): Boolean = running

    fun getSocks5Port(): Int = socks5Port

    fun getMeshCidrs(): List<String> = meshCidrs

    @Synchronized
    fun getLogs(): String = logBuffer.toString()

    @Synchronized
    fun clearLogs() {
        logBuffer.setLength(0)
    }

    fun getLastError(): String? = lastError

    fun getStatusText(): String {
        if (!isEnabled()) return "disabled"
        return if (running) "running" else "stopped"
    }

    fun getNetworkInfo(): String {
        if (!running) return "not running"
        val sb = StringBuilder()
        sb.appendLine("Instance: ${DataStore.easyTierNetworkName}")
        sb.appendLine("SOCKS5: 127.0.0.1:$socks5Port")
        sb.appendLine("RPC: 127.0.0.1:$rpcPort")
        if (meshCidrs.isNotEmpty()) {
            sb.appendLine("Discovered CIDRs:")
            meshCidrs.forEach { sb.appendLine("  $it") }
        } else {
            sb.appendLine("CIDRs: (none discovered)")
        }
        return sb.toString()
    }

    /**
     * Start the EasyTier process.
     * Must be called before buildConfig() so that CIDRs are available.
     * Returns true if EasyTier started successfully or was already running.
     */
    suspend fun start(): Boolean {
        if (running) return true
        if (!isEnabled()) return false

        lastError = null
        clearLogs()

        val config = buildConfigFromDataStore()
        socks5Port = config.socks5Port
        rpcPort = config.rpcPort

        val executable = try {
            PluginManager.init("easytier-plugin")?.path
        } catch (e: Exception) {
            lastError = "Plugin not found: ${e.message}"
            Logs.w("[$TAG] $lastError")
            return false
        }
        if (executable == null) {
            lastError = "EasyTier plugin not installed"
            Logs.w("[$TAG] $lastError")
            return false
        }

        val cacheDir = File(resolveRepository().cacheDir, "tmpcfg")
        cacheDir.mkdirs()
        val ts = System.currentTimeMillis()
        configFile = File(cacheDir, "easytier_$ts.toml").also {
            it.writeText(config.toToml())
        }
        logFile = File(cacheDir, "easytier_$ts.log")

        val args = config.toCliArgs(configFile!!.absolutePath).toMutableList()
        args.add(0, executable)

        Logs.i("[$TAG] Starting: ${args.joinToString(" ")}")
        appendLog("Starting EasyTier...")

        return try {
            val pb = ProcessBuilder(args).apply {
                redirectErrorStream(true)
                if (logFile != null) {
                    redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                }
            }
            process = pb.start()
            running = true
            appendLog("Process started (pid: ${process?.pid()})")

            if (!waitForSocks5()) {
                lastError = "SOCKS5 port ${config.socks5Port} not ready"
                appendLog("ERROR: $lastError")
                stopInternal()
                return false
            }
            appendLog("SOCKS5 ready on 127.0.0.1:${config.socks5Port}")

            meshCidrs = discoverMeshCidrs(config.rpcPort)
            appendLog("Discovered ${meshCidrs.size} CIDRs: $meshCidrs")
            Logs.i("[$TAG] Discovered CIDRs: $meshCidrs")
            true
        } catch (e: Exception) {
            lastError = e.message
            appendLog("ERROR: ${e.message}")
            Logs.w("[$TAG] Start failed", e)
            stopInternal()
            false
        }
    }

    /**
     * Stop the EasyTier process.
     */
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        running = false
        process?.let {
            try {
                it.destroy()
                if (!it.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    it.destroyForcibly()
                }
            } catch (e: Exception) {
                Logs.w("[$TAG] Stop error", e)
            }
        }
        process = null
        configFile?.let { it.delete(); configFile = null }
        logFile?.let { it.delete(); logFile = null }
        meshCidrs = emptyList()
        socks5Port = 0
        rpcPort = 0
        appendLog("EasyTier stopped")
    }

    private suspend fun waitForSocks5(): Boolean {
        repeat(SOCKS5_PROBE_RETRIES) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", socks5Port), 1000)
                    return true
                }
            } catch (_: Exception) {
                val proc = process
                if (proc != null && !proc.isAlive) {
                    appendLog("Process exited prematurely with code ${proc.exitValue()}")
                    return false
                }
                delay(SOCKS5_PROBE_INTERVAL_MS)
            }
        }
        return false
    }

    private suspend fun discoverMeshCidrs(rpcPort: Int): List<String> {
        // TODO: Implement RPC Portal query to get actual mesh CIDRs.
        // Until then, fall back to the default private /LAN CIDRs so mesh
        // addresses are reachable through the EasyTier SOCKS5 outbound.
        return try {
            val cidrs = EasyTierRpcClient(rpcPort).collectMeshCidrs()
            if (cidrs.isNotEmpty()) cidrs else EasyTierConfig.DEFAULT_LAN_CIDRS
        } catch (e: Exception) {
            appendLog("CIDR discovery failed: ${e.message}")
            Logs.w("[$TAG] CIDR discovery failed: ${e.message}")
            EasyTierConfig.DEFAULT_LAN_CIDRS
        }
    }

    @Synchronized
    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        logBuffer.appendLine("[$timestamp] $message")
        Logs.d("[$TAG] $message")
    }

    private fun buildConfigFromDataStore(): EasyTierConfig {
        return EasyTierConfig(
            instanceName = EasyTierConfig.DEFAULT_INSTANCE_NAME,
            hostname = DataStore.easyTierHostname,
            networkName = DataStore.easyTierNetworkName,
            networkSecret = DataStore.easyTierNetworkSecret,
            virtualIp = DataStore.easyTierVirtualIp,
            peers = DataStore.easyTierPeers.split("\n").map { it.trim() }.filter { it.isNotBlank() },
            listeners = DataStore.easyTierListeners.split("\n").map { it.trim() }.filter { it.isNotBlank() },
            socks5Port = DataStore.easyTierSocks5Port,
            rpcPort = DataStore.easyTierRpcPort,
            noTun = DataStore.easyTierNoTun,
            mtu = DataStore.easyTierMtu,
            logLevel = DataStore.easyTierLogLevel,
        )
    }
}
