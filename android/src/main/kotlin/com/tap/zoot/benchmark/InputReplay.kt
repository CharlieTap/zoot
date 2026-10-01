package com.tap.zoot.benchmark

import com.tap.n64.input.ControllerState

internal class InputReplay private constructor(
    private val intervals: Array<Interval>,
) {
    fun stateAt(tick: Long): ControllerState {
        for (interval in intervals) {
            if (tick in interval.first..interval.last) return interval.state
        }
        return ControllerState.Neutral
    }

    private class Interval(
        val first: Long,
        val last: Long,
        val state: ControllerState,
    )

    companion object {
        fun parse(script: String): InputReplay {
            val intervals =
                script
                    .trim('\'', '"')
                    .split(';')
                    .filter(String::isNotBlank)
                    .map { encoded ->
                        val fields = encoded.split(':')
                        val bounds = fields[0].split('-')
                        Interval(
                            first = bounds[0].toLong(),
                            last = bounds.getOrElse(1) { bounds[0] }.toLong(),
                            state =
                                ControllerState.of(
                                    buttons = fields[1].toInt(16),
                                    stickX = fields.getOrElse(2) { "0" }.toInt(),
                                    stickY = fields.getOrElse(3) { "0" }.toInt(),
                                ),
                        )
                    }.toTypedArray()
            return InputReplay(intervals)
        }
    }
}
