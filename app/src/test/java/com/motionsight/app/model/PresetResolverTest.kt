package com.motionsight.app.model

import org.junit.Assert.assertTrue
import org.junit.Test

class PresetResolverTest {
    @Test
    fun flyingPresetAcceptsSmallerRegionsThanCrawling() {
        val crawling = PresetResolver.resolve(MotionMode.FIXED, TargetPreset.CRAWLING)
        val flying = PresetResolver.resolve(MotionMode.FIXED, TargetPreset.FLYING)
        assertTrue(flying.minimumAreaPx < crawling.minimumAreaPx)
        assertTrue(flying.persistenceFrames < crawling.persistenceFrames)
    }

    @Test
    fun handheldIsMoreConservativeThanFixed() {
        val fixed = PresetResolver.resolve(MotionMode.FIXED, TargetPreset.CRAWLING)
        val handheld = PresetResolver.resolve(MotionMode.HANDHELD, TargetPreset.CRAWLING)
        assertTrue(handheld.minimumAreaPx > fixed.minimumAreaPx)
        assertTrue(handheld.instabilityThreshold > fixed.instabilityThreshold)
    }
}
