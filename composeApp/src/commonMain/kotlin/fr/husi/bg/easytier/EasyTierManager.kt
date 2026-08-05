package fr.husi.bg.easytier

import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.plugin.PluginManager
import fr.husi.repository.resolveRepository
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object EasyTierManager {

    private const val TAG = "EasyTier"
    private const val SOCKS5_PROBE_RETRIES = 10
    private const val SOCKS5_PROBE_INTERVAL_MS = 500L

    /**
     * Private scope for the log-streaming coroutine. Owned by the
     * EasyTierManager singleton, so it lives for the process lifetime;
     * individual log-reader jobs are cancelled in [stopInternal].
     *
     * Note: this is a single-process object. On Android the UI and :bg
     * processes each have their own instance, so [isRunning] only reflects
     * the calling process's view. The :bg instance is the authoritative
     * one used by ConfigBuilder; the UI instance is only meaningful on
     * desktop.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    private var process: Process? = null

    private var logReaderJob: Job? = null

    private val logBuffer = StringBuilder()

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
        val listeners = DataStore.easyTierListeners.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        sb.appendLine("Listeners: ${if (listeners.isEmpty()) EasyTierConfig.DEFAULT_LISTENER else listeners.joinToString(", ")}")
        val peers = DataStore.easyTierPeers.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        sb.appendLine("Peers: ${if (peers.isEmpty()) "(none)" else peers.joinToString(", ")}")
        if (peers.isEmpty()) {
            sb.appendLine("Hint: EasyTier needs a reachable seed to connect.")
            sb.appendLine("Add at least one peer (e.g. tcp://<other-node-ip>:11010),")
            sb.appendLine("or share this node's listener address with other devices.")
        }
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

        val args = config.toCliArgs(configFile!!.absolutePath).toMutableList()
        args.add(0, executable)

        Logs.i("[$TAG] Starting: ${args.joinToString(" ")}")
        appendLog("Starting EasyTier...")

        return try {
            val pb = ProcessBuilder(args).apply {
                redirectErrorStream(true)
            }
            process = pb.start()
            running = true
            appendLog("Process started")
            streamProcessOutput()

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
     * Stream easytier-core's stderr/stdout into the in-app log buffer so the
     * user (and developer) can see Easytier's own connection/diagnostic logs.
     */
    private fun streamProcessOutput() {
        val proc = process ?: return
        val reader = proc.inputStream.bufferedReader()
        logReaderJob = scope.launch {
            try {
                for (line in reader.lineSequence()) {
                    if (line.isNotBlank()) {
                        appendRawLog(line)
                    }
                }
            } catch (_: Exception) {
                // stream closed
            } finally {
                runCatching { reader.close() }
            }
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
        logReaderJob?.cancel()
        logReaderJob = null
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

    @Synchronized
    private fun appendRawLog(line: String) {
        logBuffer.appendLine("easytier: $line")
        Logs.d("[$TAG] easytier: $line")
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
