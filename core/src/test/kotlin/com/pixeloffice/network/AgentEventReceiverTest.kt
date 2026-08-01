package com.pixeloffice.network

import com.pixeloffice.integration.AgentEvent
import com.pixeloffice.integration.AgentEventKind
import com.pixeloffice.integration.AgentState
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentEventReceiverTest {

    @Test
    fun `receives canonical UDP events`() {
        val receiver = AgentEventReceiver(udpPort = 0, httpPort = 0)
        receiver.start()
        try {
            val payload = """
                {
                  "version": 1,
                  "provider": "codex",
                  "sessionId": "session-1",
                  "agentId": "main",
                  "projectId": "/tmp/project",
                  "projectLabel": "project",
                  "kind": "upsert",
                  "state": "coding",
                  "activity": "edit",
                  "occurredAt": 1234
                }
            """.trimIndent().toByteArray()
            DatagramSocket().use { socket ->
                socket.send(
                    DatagramPacket(
                        payload,
                        payload.size,
                        InetAddress.getByName("127.0.0.1"),
                        receiver.boundUdpPort
                    )
                )
            }

            val event = awaitEvent(receiver)
            assertEquals("codex", event.provider)
            assertEquals(AgentEventKind.UPSERT, event.kind)
            assertEquals(AgentState.CODING, event.state)
        } finally {
            receiver.stop()
        }
    }

    @Test
    fun `accepts canonical events over HTTP`() {
        val receiver = AgentEventReceiver(udpPort = 0, httpPort = 0)
        receiver.start()
        try {
            val connection = URL("http://127.0.0.1:${receiver.boundHttpPort}/api/events")
                .openConnection() as HttpURLConnection
            val payload = """
                {
                  "version": 1,
                  "provider": "claude",
                  "sessionId": "session-http",
                  "agentId": "main",
                  "projectId": "/tmp/pixel-office",
                  "projectLabel": "pixel-office",
                  "kind": "upsert",
                  "state": "thinking",
                  "activity": "post_tool",
                  "occurredAt": 5678
                }
            """.trimIndent()

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(payload.toByteArray()) }

            assertEquals(200, connection.responseCode)
            connection.inputStream.close()
            val event = awaitEvent(receiver)
            assertEquals("claude", event.provider)
            assertEquals("session-http", event.sessionId)
            assertEquals(AgentState.THINKING, event.state)
        } finally {
            receiver.stop()
        }
    }

    @Test
    fun `rejects snapshot-shaped HTTP payloads`() {
        val receiver = AgentEventReceiver(udpPort = 0, httpPort = 0)
        receiver.start()
        try {
            val connection = URL("http://127.0.0.1:${receiver.boundHttpPort}/api/events")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write("""{"sessions":[]}""".toByteArray()) }

            assertEquals(400, connection.responseCode)
            connection.errorStream?.close()
            assertTrue(receiver.drainEvents().isEmpty())
        } finally {
            receiver.stop()
        }
    }

    @Test
    fun `ignores malformed UDP packets and remains available`() {
        val receiver = AgentEventReceiver(udpPort = 0, httpPort = 0)
        receiver.start()
        try {
            val payload = "not-json".toByteArray()
            DatagramSocket().use { socket ->
                socket.send(
                    DatagramPacket(
                        payload,
                        payload.size,
                        InetAddress.getByName("127.0.0.1"),
                        receiver.boundUdpPort
                    )
                )
            }
            Thread.sleep(50)
            assertTrue(receiver.drainEvents().isEmpty())

            val connection = URL("http://127.0.0.1:${receiver.boundHttpPort}/api/events")
                .openConnection() as HttpURLConnection
            assertEquals(200, connection.responseCode)
            connection.inputStream.close()
        } finally {
            receiver.stop()
        }
    }

    private fun awaitEvent(receiver: AgentEventReceiver): AgentEvent {
        repeat(100) {
            receiver.drainEvents().firstOrNull()?.let { return it }
            Thread.sleep(10)
        }
        error("Timed out waiting for receiver update")
    }
}
