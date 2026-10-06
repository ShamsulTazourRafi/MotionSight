package com.motionsight.app.motion

import kotlin.math.ceil

class PerformanceGovernor(
    private val budgetMs: Float = 33.3f,
    private val sampleLimit: Int = 90,
    private val overBudgetRatio: Float = 0.65f,
) {
    private val samples = ArrayDeque<Float>()

    data class Snapshot(
        val medianMs: Float,
        val p95Ms: Float,
        val shouldFallback: Boolean,
    )

    fun add(durationMs: Float): Snapshot {
        samples.addLast(durationMs)
        while (samples.size > sampleLimit) samples.removeFirst()
        val sorted = samples.sorted()
        val median = percentile(sorted, 0.5f)
        val p95 = percentile(sorted, 0.95f)
        val overBudget = samples.count { it > budgetMs }.toFloat() / samples.size.coerceAtLeast(1)
        return Snapshot(
            medianMs = median,
            p95Ms = p95,
            shouldFallback = samples.size >= sampleLimit && overBudget >= overBudgetRatio,
        )
    }

    fun clear() = samples.clear()

    private fun percentile(sorted: List<Float>, fraction: Float): Float {
        if (sorted.isEmpty()) return 0f
        val index = (ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }
}
