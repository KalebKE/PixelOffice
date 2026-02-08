package com.pixeloffice.network

import com.badlogic.gdx.Gdx
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Data tagged with the connection it came from.
 */
data class TaggedData(val connectionId: String, val data: String)

/**
 * State for a single client connection.
 */
private class ClientConnection(
    val socket: Socket,
    val thread: Thread,
    var bytesReceived: Long = 0L,
    var lastDataTime: Long = 0L
)

/**
 * TCP socket receiver for tmux pipe-pane output.
 *
 * Listens for connections on a specified port and receives
 * data streamed from tmux via `tmux pipe-pane -o 'nc localhost PORT'`.
 *
 * Supports multiple simultaneous connections — each gets a unique connectionId.
 */
class TmuxReceiver(
    private val host: String = "localhost",
    private val port: Int = 9999,
    private val bufferSize: Int = 4096,
    private val reconnectDelay: Float = 5.0f
) {
    // Server socket
    private var serverSocket: ServerSocket? = null

    // Threading
    private val running = AtomicBoolean(false)
    private var acceptThread: Thread? = null
    private val dataQueue = ConcurrentLinkedQueue<TaggedData>()

    // Multi-connection state
    private val clients = ConcurrentHashMap<String, ClientConnection>()
    private val connectionCounter = AtomicInteger(0)

    // Callbacks (called on receiver thread, NOT GL thread!)
    var onConnect: ((connectionId: String) -> Unit)? = null
    var onDisconnect: ((connectionId: String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * Start the receiver in a background thread.
     */
    fun start() {
        if (running.get()) return

        running.set(true)
        acceptThread = Thread({ run() }, "TmuxReceiver-Accept").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Stop the receiver and close all connections.
     */
    fun stop() {
        running.set(false)

        // Close all client sockets to unblock their reader threads
        for ((_, client) in clients) {
            try {
                client.socket.close()
            } catch (_: Exception) {}
        }
        clients.clear()

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        acceptThread?.join(2000)
        acceptThread = null
    }

    /**
     * Get all available data from the queue, tagged with connection IDs.
     */
    fun drainData(): List<TaggedData> {
        val data = mutableListOf<TaggedData>()
        while (true) {
            val item = dataQueue.poll() ?: break
            data.add(item)
        }
        return data
    }

    /**
     * Check if any client is connected.
     */
    fun isConnected(): Boolean = clients.isNotEmpty()

    /**
     * Get the number of active connections.
     */
    fun getConnectionCount(): Int = clients.size

    /**
     * Post a runnable to be executed on the GL thread.
     * Use this when you need to update game state from network callbacks.
     */
    fun postToGLThread(runnable: Runnable) {
        Gdx.app?.postRunnable(runnable)
    }

    // Internal methods

    private fun run() {
        while (running.get()) {
            try {
                createServer()
                acceptLoop()
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "Unknown error")

                if (running.get()) {
                    Thread.sleep((reconnectDelay * 1000).toLong())
                }
            }
        }
    }

    private fun createServer() {
        serverSocket = ServerSocket(port).apply {
            reuseAddress = true
            soTimeout = 1000
        }
        Gdx.app?.log("TmuxReceiver", "Server listening on $host:$port")
    }

    private fun acceptLoop() {
        while (running.get()) {
            val socket = try {
                serverSocket?.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                if (running.get()) {
                    onError?.invoke(e.message ?: "Accept error")
                }
                break
            } ?: continue

            val connId = "conn_${connectionCounter.incrementAndGet()}"

            val readerThread = Thread({
                receiveLoop(connId, socket)
            }, "TmuxReceiver-$connId").apply {
                isDaemon = true
            }

            val client = ClientConnection(socket, readerThread)
            clients[connId] = client

            Gdx.app?.log("TmuxReceiver", "Client connected: $connId (${clients.size} total)")
            onConnect?.invoke(connId)

            readerThread.start()
        }

        // Clean up server socket when done
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun receiveLoop(connId: String, socket: Socket) {
        try {
            socket.soTimeout = 1000
            val buffer = ByteArray(bufferSize)

            while (running.get()) {
                try {
                    val bytesRead = socket.inputStream.read(buffer)

                    if (bytesRead == -1) break

                    if (bytesRead > 0) {
                        val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        val client = clients[connId]
                        if (client != null) {
                            client.lastDataTime = System.currentTimeMillis()
                            client.bytesReceived += bytesRead
                        }
                        dataQueue.offer(TaggedData(connId, text))
                    }
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
            }
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}

            clients.remove(connId)
            Gdx.app?.log("TmuxReceiver", "Client disconnected: $connId (${clients.size} remaining)")
            onDisconnect?.invoke(connId)
        }
    }
}
