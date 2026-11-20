package com.example.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueLogicTest {

    @Test
    fun nextStopsAtEnd() {
        assertEquals(1, nextIndex(3, 0))
        assertEquals(2, nextIndex(3, 1))
        assertEquals(2, nextIndex(3, 2))
        assertEquals(0, nextIndex(3, -1))
        assertEquals(0, nextIndex(0, 0))
    }

    @Test
    fun prevRestartsTrackAfterThreeSeconds() {
        // 播到 5 秒：回本曲开头
        assertEquals(2, prevIndex(4, 2, 5_000L))
        // 播到 1 秒：回上一首
        assertEquals(1, prevIndex(4, 2, 1_000L))
    }

    @Test
    fun prevOnFirstTrackStaysAtZero() {
        assertEquals(0, prevIndex(4, 0, 10_000L))
        assertEquals(0, prevIndex(4, 0, 0L))
    }

    @Test
    fun prevWithEmptyQueueIsSafe() {
        assertEquals(0, prevIndex(0, 0, 0L))
    }
}