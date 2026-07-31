package com.pixeloffice.network

import com.pixeloffice.integration.AoSessionSnapshot
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * HTTP server that listens for snapshot POSTs from the agent-orchestrator.
 * The AO pushes data TO Pixel Office (not the other way around).
 */
class AoClient(private val baseUrl: String = "http://localhost:3001") {
    private val snapshotQueue = ConcurrentLinkedQueue<List<AoSessionSnapshot>>()
    private val json = Json { ignoreUnknownKeys = true }
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    fun start() {
        check(server == null) { "AoClient is already running" }

        val uri = URI.create(baseUrl)
        val port = if (uri.port > 0) uri.port else 3001
        val serverExecutor = Executors.newFixedThreadPool(2)
        executor = serverExecutor

        server = HttpServer.create(InetSocketAddress(port), 0).apply {
            createContext("/api/events") { exchange ->
                if (exchange.requestMethod == "POST") {
                    val json = exchange.requestBody.bufferedReader().readText()
                    com.badlogic.gdx.Gdx.app?.log("AoClient", "POST /api/events body: $json")
                    try {
                        parseSnapshot(json)
                        val resp = """{"ok":true}"""
                        exchange.sendResponseHeaders(200, resp.length.toLong())
                        exchange.responseBody.use { it.write(resp.toByteArray()) }
                    } catch (e: Exception) {
                        com.badlogic.gdx.Gdx.app?.log("AoClient", "Parse error: ${e.message}")
                        val resp = """{"ok":false,"error":"${e.message}"}"""
                        exchange.sendResponseHeaders(400, resp.length.toLong())
                        exchange.responseBody.use { it.write(resp.toByteArray()) }
                    }
                } else {
                    val resp = """{"ok":true}"""
                    exchange.sendResponseHeaders(200, resp.length.toLong())
                    exchange.responseBody.use { it.write(resp.toByteArray()) }
                }
            }
            executor = serverExecutor
            start()
        }
        com.badlogic.gdx.Gdx.app?.log("AoClient", "Listening on port $port")
    }

    private fun parseSnapshot(payload: String) {
        // Accept both wrapped {"type":"snapshot","sessions":[...]} and bare [...]
        val root = json.parseToJsonElement(payload)
        val sessionElements = when (root) {
            is JsonArray -> root
            is JsonObject -> root["sessions"] as? JsonArray ?: return
            else -> return
        }

        val sessions = sessionElements.mapNotNull { element ->
            val session = element as? JsonObject ?: return@mapNotNull null
            val id = session.string("id") ?: return@mapNotNull null
            val status = session.string("status") ?: return@mapNotNull null
            AoSessionSnapshot(
                id = id,
                projectId = session.string("projectId").orEmpty(),
                status = status,
                activity = session.string("activity"),
                attentionLevel = session.string("attentionLevel"),
                lastActivityAt = session.string("lastActivityAt")
            )
        }

        if (sessions.isNotEmpty()) {
            snapshotQueue.add(sessions)
        }
    }

    /**
     * Drain the snapshot queue, returning only the latest batch (or null if empty).
     * Call this from the GL thread each frame.
     */
    fun drainSnapshots(): List<AoSessionSnapshot>? {
        var latest: List<AoSessionSnapshot>? = null
        while (true) {
            val next = snapshotQueue.poll() ?: break
            latest = next
        }
        return latest
    }

    fun stop() {
        server?.stop(0)
        server = null
        executor?.shutdownNow()
        executor = null
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}
