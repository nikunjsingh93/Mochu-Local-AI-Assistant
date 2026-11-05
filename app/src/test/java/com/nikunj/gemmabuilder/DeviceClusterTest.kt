package com.nikunj.gemmabuilder

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClusterTest {
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
