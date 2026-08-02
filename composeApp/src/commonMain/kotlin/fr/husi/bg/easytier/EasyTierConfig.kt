package fr.husi.bg.easytier

data class EasyTierConfig(
    var instanceName: String = DEFAULT_INSTANCE_NAME,
    var hostname: String = "",
    var networkName: String = "",
    var networkSecret: String = "",
    var virtualIp: String = "",
    var peers: List<String> = emptyList(),
    var listeners: List<String> = emptyList(),
    var socks5Port: Int = DEFAULT_SOCKS5_PORT,
    var rpcPort: Int = DEFAULT_RPC_PORT,
    var noTun: Boolean = true,
    var mtu: Int = 0,
    var logLevel: String = "warn",
) {
    fun toToml(): String {
        val sb = StringBuilder()
        sb.appendLine("instance_name = \"${escapeToml(instanceName)}\"")
        if (hostname.isNotBlank()) {
            sb.appendLine("hostname = \"${escapeToml(hostname)}\"")
        }
        sb.appendLine("socks5_portal = \"socks5://127.0.0.1:$socks5Port\"")
        if (virtualIp.isNotBlank()) {
            sb.appendLine("ipv4 = \"${escapeToml(virtualIp)}\"")
        }
        if (listeners.isNotEmpty()) {
            sb.appendLine("listeners = [${listeners.joinToString(",") { "\"${escapeToml(it)}\"" }}]")
        }
        sb.appendLine("[network_identity]")
        sb.appendLine("network_name = \"${escapeToml(networkName)}\"")
        if (networkSecret.isNotEmpty()) {
            sb.appendLine("network_secret = \"${escapeToml(networkSecret)}\"")
        }
        for (peer in peers) {
            if (peer.isBlank()) continue
            sb.appendLine("[[peer]]")
            sb.appendLine("uri = \"${escapeToml(peer)}\"")
        }
        sb.appendLine("[flags]")
        sb.appendLine("no_tun = $noTun")
        if (mtu > 0) {
            sb.appendLine("mtu = $mtu")
        }
        return sb.toString()
    }

    fun toCliArgs(configFilePath: String): List<String> {
        val args = mutableListOf(
            "--config-file", configFilePath,
            "--rpc-portal", "127.0.0.1:$rpcPort",
            "--console-log-level", logLevel,
        )
        return args
    }

    companion object {
        const val DEFAULT_INSTANCE_NAME = "husi"
        const val DEFAULT_SOCKS5_PORT = 10852
        const val DEFAULT_RPC_PORT = 15888

        val DEFAULT_LAN_CIDRS = listOf(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
        )

        private fun escapeToml(s: String): String =
            s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\u000C", "\\f")
    }
}
