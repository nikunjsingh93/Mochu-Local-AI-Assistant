package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeviceClusterTest {
    @Test
    fun clusterProtocolMatchesLlamaCpp() {
        // A llama.cpp update that changes the RPC protocol must also change CLUSTER_PROTOCOL, so older
        // devices are told to update instead of failing inside llama.cpp.
        val header = listOf("src/main/cpp/llama.cpp/ggml/include/ggml-rpc.h", "app/src/main/cpp/llama.cpp/ggml/include/ggml-rpc.h")
            .map(::File).first { it.exists() }
        val major = Regex("""#define\s+RPC_PROTO_MAJOR_VERSION\s+(\d+)""").find(header.readText())!!.groupValues[1].toInt()
        assertEquals(major, CLUSTER_PROTOCOL)
    }

    @Test
    fun readsWorkerInfoFromNewAndOldWorkers() {
        val new = parseWorkerInfo("OK 2900 onn_8_Core 5650 9.4 $CLUSTER_PROTOCOL")!!
        assertEquals(2900L, new.freeMb)
        assertEquals(5650L, new.totalMb)
        assertEquals(9.4, new.bandwidth, 0.01)
        assertEquals(CLUSTER_PROTOCOL, new.protocol)
        // Mochu 2.3.0 and older send no protocol.
        assertEquals(0, parseWorkerInfo("OK 2900 onn_8_Core 5650 9.4")!!.protocol)
        assertNull(parseWorkerInfo("NO"))
    }

    @Test
    fun explainsVersionMismatches() {
        assertNull(clusterVersionProblem("Pixel 9", CLUSTER_PROTOCOL))
        val older = clusterVersionProblem("Pixel 9", 0)
        assertNotNull(older)
        assertTrue(older!!.contains("older Mochu") && older.contains("Update Mochu on Pixel 9"))
        assertTrue(clusterVersionProblem("Pixel 9", CLUSTER_PROTOCOL + 1)!!.contains("Update Mochu here"))
    }

    @Test
    fun sharesFollowWeightsWhenEverythingFits() {
        val s = fitSharesToMemory(listOf(2.0, 1.0), listOf(8000.0, 8000.0), 3000.0)
        assertEquals(2000.0, s[0], 0.1)
        assertEquals(1000.0, s[1], 0.1)
    }

    @Test
    fun aFullDeviceGivesItsExtraToTheOthers() {
        // Fast but small second device: it can hold only 1000 MB of the 6000 MB model.
        val s = fitSharesToMemory(listOf(1.0, 3.0, 1.0), listOf(5000.0, 1000.0, 5000.0), 6000.0)
        assertEquals(1000.0, s[1], 0.1)
        assertEquals(2500.0, s[0], 0.1)
        assertEquals(2500.0, s[2], 0.1)
    }

    @Test
    fun modesPickTheirWeights() {
        val ids = listOf("local", "a")
        val free = listOf(6000L, 3000L)
        val speed = listOf(20.0, 40.0)
        // By speed: the faster second device gets two thirds.
        assertEquals(2.0, DeviceCluster.shares("speed", ids, free, speed, emptyMap(), 0.0).let { it[1] / it[0] }, 0.01)
        assertEquals(0.5, DeviceCluster.shares("memory", ids, free, speed, emptyMap(), 0.0).let { it[1] / it[0] }, 0.01)
        assertEquals(1.0, DeviceCluster.shares("even", ids, free, speed, emptyMap(), 0.0).let { it[1] / it[0] }, 0.01)
        assertEquals(3.0, DeviceCluster.shares("manual", ids, free, speed, mapOf("local" to 1f, "a" to 3f), 0.0).let { it[1] / it[0] }, 0.01)
        // Unknown speed falls back to memory.
        assertEquals(0.5, DeviceCluster.shares("speed", ids, free, listOf(20.0, 0.0), emptyMap(), 0.0).let { it[1] / it[0] }, 0.01)
    }
}
