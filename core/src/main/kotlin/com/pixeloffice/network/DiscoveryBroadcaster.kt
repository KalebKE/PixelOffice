package com.pixeloffice.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends a UDP multicast heartbeat so LAN clients can discover the game server.
 *
 * Broadcasts "PIXELOFFICE:<tcpPort>" to the multicast group every [intervalMs] ms.
 */
class DiscoveryBroadcaster(
    private val tcpPort: Int = 9999,
    private val multicastGroup: String = "239.255.80.79",
    private val udpPort: Int = 9998,
    private val intervalMs: Long = 2000
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var socket: DatagramSocket? = null

    fun start() {
        if (running.get()) return
        running.set(true)

        thread = Thread({
            try {
                val sock = DatagramSocket()
                socket = sock
                val group = InetAddress.getByName(multicastGroup)
                val message = "PIXELOFFICE:$tcpPort".toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(message, message.size, group, udpPort)

                while (running.get()) {
                    try {
                        sock.send(packet)
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                    Thread.sleep(intervalMs)
                }
            } catch (_: Exception) {
                // Thread exits on error or shutdown
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }, "DiscoveryBroadcaster").apply {
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
}
