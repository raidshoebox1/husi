package fr.husi.bg.easytier

import java.net.InetSocketAddress
import java.net.Socket

/**
 * Client for querying the EasyTier RPC Portal (default: 127.0.0.1:15888).
 *
 * The RPC Portal uses a custom protobuf-based TCP protocol (prost RPC).
 * For now, this implementation returns default LAN CIDRs as a fallback.
 * Full RPC protocol implementation can be added in a future iteration
 * by either:
 * 1. Building easytier-cli alongside easytier-core and invoking it as a subprocess
 * 2. Implementing the prost RPC wire protocol in Kotlin
 */
class EasyTierRpcClient(private val rpcPort: Int) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 1000
    }

    /**
     * Collect mesh CIDRs from the running EasyTier instance.
     *
     * Currently returns an empty list, so no mesh CIDR route rule is injected
     * until the RPC protocol is implemented. This is a placeholder for future
     * RPC-based dynamic CIDR discovery.
     */
    fun collectMeshCidrs(): List<String> {
        // TODO: Implement RPC Portal query to get actual mesh CIDRs.
        // For now, return empty list so the caller uses default LAN CIDRs.
        return emptyList()
    }

    /**
     * Check if the RPC Portal is reachable.
     */
    fun isReachable(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", rpcPort), CONNECT_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
