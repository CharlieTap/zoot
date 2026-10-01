package com.tap.zoot.launch

import android.content.Intent
import com.tap.zoot.benchmark.BenchmarkConfiguration
import com.tap.zoot.graphics.upscaler.UpscalerId

internal class LaunchOptions(
    val upscaler: UpscalerId?,
    val benchmark: BenchmarkConfiguration?,
) {
    companion object {
        fun from(intent: Intent): LaunchOptions =
            LaunchOptions(
                upscaler = intent.getStringExtra("upscaler")?.let(::UpscalerId),
                benchmark = BenchmarkConfiguration.from(intent),
            )
    }
}
