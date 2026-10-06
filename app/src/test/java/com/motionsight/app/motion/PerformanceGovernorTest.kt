package com.motionsight.app.motion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceGovernorTest {
    @Test
    fun fallbackRequiresSustainedOverBudgetProcessing() {
        val governor = PerformanceGovernor(sampleLimit = 10, overBudgetRatio = 0.6f)
        repeat(9) { assertFalse(governor.add(45f).shouldFallback) }
        assertTrue(governor.add(45f).shouldFallback)
    }
}
