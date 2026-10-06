package com.motionsight.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRatePolicyTest {
    @Test
    fun crawlingUsesLowerPowerFrameRate() {
        val crawling = FrameRatePolicy.targetFps(MotionMode.FIXED, TargetPreset.CRAWLING)
        val flying = FrameRatePolicy.targetFps(MotionMode.FIXED, TargetPreset.FLYING)
        assertEquals(15, crawling)
        assertTrue(flying > crawling)
    }

    @Test
    fun intervalMatchesTargetFrameRate() {
        assertEquals(
            66_666_666L,
            FrameRatePolicy.minimumIntervalNanos(MotionMode.FIXED, TargetPreset.CRAWLING),
        )
    }
}
