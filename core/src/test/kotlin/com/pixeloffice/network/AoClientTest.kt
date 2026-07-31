package com.pixeloffice.network

import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

class AoClientTest {

    @Test
    fun `accepts wrapped snapshots and drains the latest batch`() {
        val port = ServerSocket(0).use { it.localPort }
        val client = AoClient("http://127.0.0.1:$port")

        try {
            client.start()
            val connection = URL("http://127.0.0.1:$port/api/events")
                .openConnection() as HttpURLConnection
            val payload = """
                {
                  "type": "snapshot",
                  "sessions": [
                    {
                      "id": "agent-1",
                      "projectId": "pixel-office",
                      "status": "implementing",
                      "activity": "active",
                      "attentionLevel": null,
                      "lastActivityAt": "2026-07-30T12:00:00Z"
                    }
                  ]
                }
            """.trimIndent()

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(payload.toByteArray()) }

            assertEquals(200, connection.responseCode)
            connection.inputStream.close()

            val snapshots = client.drainSnapshots()
            assertEquals(1, snapshots?.size)
            assertEquals("agent-1", snapshots?.single()?.id)
            assertEquals("pixel-office", snapshots?.single()?.projectId)
            assertEquals("implementing", snapshots?.single()?.status)
            assertEquals("active", snapshots?.single()?.activity)
        } finally {
            client.stop()
        }
    }
}
