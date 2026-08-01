package com.pixeloffice.network

import com.pixeloffice.integration.AgentEvent
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AgentEventReceiver(
    private val host: String = "127.0.0.1",
    private val udpPort: Int = 9997,
    private val httpPort: Int = 3003
) {
    companion object {
        private const val MAX_PACKET_SIZE = 8192
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val events = ConcurrentLinkedQueue<AgentEvent>()
    private val running = AtomicBoolean(false)
    private var udpSocket: DatagramSocket? = null
    private var udpThread: Thread? = null
    private var httpServer: HttpServer? = null
    private var httpExecutor: ExecutorService? = null

    var boundUdpPort: Int = -1
        private set
    var boundHttpPort: Int = -1
        private set

    fun start() {
        check(running.compareAndSet(false, true)) { "AgentEventReceiver is already running" }
        try {
            startUdp()
            startHttp()
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    fun drainEvents(): List<AgentEvent> {
        val drained = mutableListOf<AgentEvent>()
        while (true) {
            drained += events.poll() ?: break
        }
        return drained
    }

    fun stop() {
        running.set(false)
        udpSocket?.close()
        udpSocket = null
        udpThread?.join(2000)
        udpThread = null
        httpServer?.stop(0)
        httpServer = null
        httpExecutor?.shutdownNow()
        httpExecutor = null
        boundUdpPort = -1
        boundHttpPort = -1
    }

    private fun startUdp() {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(host, udpPort))
            soTimeout = 500
        }
        udpSocket = socket
        boundUdpPort = socket.localPort
        udpThread = Thread({
            val buffer = ByteArray(MAX_PACKET_SIZE)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val payload = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    val event = json.decodeFromString<AgentEvent>(payload)
                    if (event.version == 1) {
                        events.add(event)
                    }
                } catch (_: SocketTimeoutException) {
                    // Periodically wake up to observe shutdown.
                } catch (e: Exception) {
                    if (running.get()) {
                        System.err.println("AgentEventReceiver ignored UDP event: ${e.message}")
                    }
                }
            }
        }, "AgentEventReceiver-UDP").apply {
            isDaemon = true
            start()
        }
    }

    private fun startHttp() {
        val executor = Executors.newFixedThreadPool(2)
        httpExecutor = executor
        httpServer = HttpServer.create(InetSocketAddress(host, httpPort), 0).apply {
            createContext("/api/events") { exchange ->
                val response = if (exchange.requestMethod == "POST") {
                    try {
                        val payload = exchange.requestBody.bufferedReader().use { it.readText() }
                        val event = json.decodeFromString<AgentEvent>(payload)
                        require(event.version == 1) { "Unsupported event version" }
                        events.add(event)
                        200 to """{"ok":true}"""
                    } catch (e: Exception) {
                        400 to """{"ok":false,"error":"invalid payload"}"""
                    }
                } else {
                    200 to """{"ok":true}"""
                }
                val bytes = response.second.toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(response.first, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            this.executor = executor
            start()
            boundHttpPort = address.port
        }
    }
}
