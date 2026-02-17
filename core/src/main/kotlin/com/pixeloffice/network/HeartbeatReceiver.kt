package com.pixeloffice.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UDP server that receives heartbeat messages from Claude terminals.
 *
 * Listens for "HEARTBEAT:<terminal_id>" messages and tracks the last
 * heartbeat time for each terminal. Provides methods to check for
 * new terminals and stale (disconnected) terminals.
 */
class HeartbeatReceiver(
    private val port: Int = 9997,
    private val onNewTerminal: ((String) -> Unit)? = null
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var socket: DatagramSocket? = null

    // Terminal ID → last heartbeat timestamp (ms)
    private val lastHeartbeat = ConcurrentHashMap<String, Long>()

    // Track which terminals we've already notified about
    private val knownTerminals = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        if (running.get()) return
        running.set(true)

        thread = Thread({
            try {
                val sock = DatagramSocket(port)
                socket = sock
                sock.soTimeout = 1000  // 1 second timeout for graceful shutdown

                val buffer = ByteArray(256)
                val packet = DatagramPacket(buffer, buffer.size)

                while (running.get()) {
                    try {
                        sock.receive(packet)
                        val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()

                        if (message.startsWith("HEARTBEAT:")) {
                            val terminalId = message.removePrefix("HEARTBEAT:")
                            val now = System.currentTimeMillis()
                            lastHeartbeat[terminalId] = now

                            // Notify about new terminals
                            if (knownTerminals.add(terminalId)) {
                                onNewTerminal?.invoke(terminalId)
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Expected timeout, continue loop
                    } catch (e: Exception) {
                        if (!running.get()) break
                    }
                }
            } catch (e: Exception) {
                System.err.println("HeartbeatReceiver error: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }, "HeartbeatReceiver").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        thread?.join(2000)
        thread = null
    }

    /**
     * Get terminal IDs that haven't sent a heartbeat within the timeout period.
     */
    fun getStaleTerminals(timeoutMs: Long): List<String> {
        val now = System.currentTimeMillis()
        return lastHeartbeat.entries
            .filter { (_, timestamp) -> now - timestamp > timeoutMs }
            .map { it.key }
    }

    /**
     * Remove a terminal from tracking (call after removing its developer).
     */
    fun removeTerminal(terminalId: String) {
        lastHeartbeat.remove(terminalId)
        knownTerminals.remove(terminalId)
    }

    /**
     * Check if a terminal is currently being tracked.
     */
    fun hasTerminal(terminalId: String): Boolean = knownTerminals.contains(terminalId)

    /**
     * Get all currently tracked terminal IDs.
     */
    fun getAllTerminals(): Set<String> = knownTerminals.toSet()
}
