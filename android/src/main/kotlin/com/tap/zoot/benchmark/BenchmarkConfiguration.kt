package com.tap.zoot.benchmark

import android.content.Intent

internal data class BenchmarkConfiguration(
    val label: String,
    val ticks: Long,
    val entranceTick: Long?,
    val replay: InputReplay?,
) {
    companion object {
        fun from(intent: Intent): BenchmarkConfiguration? {
            val ticks = intent.getIntExtra("benchmark_ticks", 0).toLong()
            val label = intent.getStringExtra("benchmark_label")
            if (ticks <= 0 || label.isNullOrBlank()) return null
            return BenchmarkConfiguration(
                label = label,
                ticks = ticks,
                entranceTick = intent.getIntExtra("benchmark_entrance_tick", -1).takeIf { it >= 0 }?.toLong(),
                replay = intent.getStringExtra("benchmark_inputs")?.let(InputReplay::parse),
            )
        }
    }
}
