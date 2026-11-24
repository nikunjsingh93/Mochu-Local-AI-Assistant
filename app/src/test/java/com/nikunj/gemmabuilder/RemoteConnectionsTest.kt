package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteConnectionsTest {
    @Test fun migratesLegacyServerAndModel() {
        val legacy = RemoteModelConfig("https://example.com/v1", "key", "model", 8192)
        assertEquals(
            listOf(RemoteConnection("legacy", "My server", legacy.baseUrl, "key", 8192, "model")),
            decodeRemoteConnections(null, legacy)
        )
        assertEquals(emptyList<RemoteConnection>(), decodeRemoteConnections("[]", legacy))
    }

    @Test fun keepsMultipleKeysAndModelSelectionsForTheSameAddress() {
        val connections = listOf(
            RemoteConnection("1", "Work", "https://example.com/v1", "key-1", 8192, "work-model"),
            RemoteConnection("2", "Personal", "https://example.com/v1", "key-2", lastModel = "personal-model"),
            RemoteConnection("3", "Home", "http://192.168.1.20:11434/v1", "")
        )
        assertEquals(connections, decodeRemoteConnections(encodeRemoteConnections(connections), null))
    }

    @Test fun validatesAndNormalizesServerAddresses() {
        assertEquals("http://192.168.1.20:11434/v1", remoteConnectionAddress("192.168.1.20:11434"))
        assertEquals("https://example.com/openai/v1", remoteConnectionAddress("https://example.com/openai/v1/"))
        assertNull(remoteConnectionAddress("https://user:password@example.com"))
        assertNull(remoteConnectionAddress("ftp://example.com"))
        assertNull(remoteConnectionAddress(" "))
    }
}
