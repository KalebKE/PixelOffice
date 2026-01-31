package com.pixeloffice.network

import com.badlogic.gdx.Gdx
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * State of the network connection.
 */
data class ConnectionState(
    var connected: Boolean = false,
    var lastDataTime: Long = 0L,
    var bytesReceived: Long = 0L,
    var error: String? = null
)

/**
 * TCP socket receiver for tmux pipe-pane output.
 *
 * Listens for connections on a specified port and receives
 * data streamed from tmux via `tmux pipe-pane -o 'nc localhost PORT'`.
 */
class TmuxReceiver(
    private val host: String = "localhost",
    private val port: Int = 9999,
    private val bufferSize: Int = 4096,
    private val reconnectDelay: Float = 5.0f
) {
    // Server socket
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    // Threading
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private val dataQueue = ConcurrentLinkedQueue<String>()

    // State
    private val state = ConnectionState()

    // Callbacks (called on receiver thread, NOT GL thread!)
    var onConnect: (() -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    var onData: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * Start the receiver in a background thread.
     */
    fun start() {
        if (running.get()) return

        running.set(true)
        thread = Thread({ run() }, "TmuxReceiver").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Stop the receiver.
     */
    fun stop() {
        running.set(false)

        // Close sockets to unblock accept/recv
        try {
            clientSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        clientSocket = null

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverSocket = null

        thread?.join(2000)
        thread = null
    }

    /**
     * Get data from the receive queue.
     *
     * @return Received data string, or null if no data available.
     */
    fun getData(): String? = dataQueue.poll()

    /**
     * Get all available data from the queue.
     */
    fun drainData(): List<String> {
        val data = mutableListOf<String>()
        while (true) {
            val item = dataQueue.poll() ?: break
            data.add(item)
        }
        return data
    }

    /**
     * Get current connection state.
     */
    fun getState(): ConnectionState = state

    /**
     * Check if a client is connected.
     */
    fun isConnected(): Boolean = state.connected

    // Internal methods

    private fun run() {
        while (running.get()) {
            try {
                createServer()
                acceptAndReceive()
            } catch (e: Exception) {
                state.error = e.message
                onError?.invoke(e.message ?: "Unknown error")

                // Wait before retrying
                if (running.get()) {
                    Thread.sleep((reconnectDelay * 1000).toLong())
                }
            }
        }
    }

    private fun createServer() {
        serverSocket = ServerSocket(port).apply {
            reuseAddress = true
            soTimeout = 1000 // Allow periodic checks for running flag
        }
        Gdx.app?.log("TmuxReceiver", "Server listening on $host:$port")
    }

    private fun acceptAndReceive() {
        while (running.get()) {
            try {
                // Wait for connection
                clientSocket = try {
                    serverSocket?.accept()
                } catch (e: SocketTimeoutException) {
                    continue
                }

                // Connected
                state.connected = true
                state.error = null
                Gdx.app?.log("TmuxReceiver", "Client connected")
                onConnect?.invoke()

                // Receive data
                receiveLoop()

            } catch (e: Exception) {
                if (running.get()) {
                    state.error = e.message
                }
            } finally {
                // Disconnected
                try {
                    clientSocket?.close()
                } catch (e: Exception) {
                    // Ignore
                }
                clientSocket = null

                if (state.connected) {
                    state.connected = false
                    Gdx.app?.log("TmuxReceiver", "Client disconnected")
                    onDisconnect?.invoke()
                }
            }
        }

        // Clean up server socket when done
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverSocket = null
    }

    private fun receiveLoop() {
        val socket = clientSocket ?: return
        socket.soTimeout = 1000

        val buffer = ByteArray(bufferSize)

        while (running.get()) {
            try {
                val bytesRead = socket.inputStream.read(buffer)

                if (bytesRead == -1) {
                    // Client disconnected
                    break
                }

                if (bytesRead > 0) {
                    // Decode and queue data
                    val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    state.lastDataTime = System.currentTimeMillis()
                    state.bytesReceived += bytesRead

                    dataQueue.offer(text)
                    onData?.invoke(text)
                }

            } catch (e: SocketTimeoutException) {
                // Normal - check if we should continue
                continue
            } catch (e: Exception) {
                break
            }
        }
    }

    /**
     * Post a runnable to be executed on the GL thread.
     * Use this when you need to update game state from network callbacks.
     */
    fun postToGLThread(runnable: Runnable) {
        Gdx.app?.postRunnable(runnable)
    }
}
