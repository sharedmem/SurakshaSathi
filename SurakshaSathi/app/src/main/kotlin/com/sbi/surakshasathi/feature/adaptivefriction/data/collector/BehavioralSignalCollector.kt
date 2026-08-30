package com.sbi.surakshasathi.feature.adaptivefriction.data.collector

import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.BehavioralSignals
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Instruments input on a protected screen and accumulates raw events into
 * [BehavioralSignals] (§6). One instance per protected-screen session — NOT
 * a singleton, so each `AdaptiveFrictionViewModel` gets a clean collector.
 *
 * Compose feeds this via plain callbacks (onFocus/onValueChange/onDrag) so
 * the collector itself stays framework-light and unit-testable without a
 * Compose UI test harness.
 */
class BehavioralSignalCollector
    @Inject
    constructor() {
        private val keystrokeTimestamps = mutableListOf<Long>()
        private var focusTimestampMs: Long? = null
        private var correctionCount = 0
        private var totalKeystrokes = 0
        private val swipeVelocities = mutableListOf<Float>()
        private val touchContactSizes = mutableListOf<Float>()
        private val recentActionTimestamps = ArrayDeque<Long>()

        fun onFieldFocused(nowMs: Long = System.currentTimeMillis()) {
            focusTimestampMs = nowMs
        }

        /** Call on every text change; [correction] true if this change shortened the field (backspace/delete). */
        fun onTextChanged(
            correction: Boolean,
            nowMs: Long = System.currentTimeMillis(),
        ) {
            keystrokeTimestamps.add(nowMs)
            totalKeystrokes++
            if (correction) correctionCount++
        }

        fun onSwipe(velocityDpPerMs: Float) {
            swipeVelocities.add(velocityDpPerMs)
        }

        fun onTouch(contactSize: Float) {
            touchContactSizes.add(contactSize)
        }

        /** Call once per sensitive action attempt — feeds the velocity-of-actions signal. */
        fun recordAction(nowMs: Long = System.currentTimeMillis()) {
            recentActionTimestamps.addLast(nowMs)
            while (recentActionTimestamps.isNotEmpty() && nowMs - recentActionTimestamps.first() > RECENT_ACTION_WINDOW_MS) {
                recentActionTimestamps.removeFirst()
            }
        }

        fun buildSignals(deviceIntegrityFailed: Boolean): BehavioralSignals {
            val interKeystrokeDeltas = keystrokeTimestamps.zipWithNext { a, b -> (b - a).toFloat() }
            val avgInterKeystroke = interKeystrokeDeltas.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
            val rhythmVariance = coefficientOfVariation(interKeystrokeDeltas)
            val correctionRate = if (totalKeystrokes > 0) correctionCount.toFloat() / totalKeystrokes else 0f
            val dwellTime =
                (keystrokeTimestamps.firstOrNull() ?: 0L).let { first ->
                    val focus = focusTimestampMs ?: return@let 0f
                    if (first == 0L) 0f else (first - focus).toFloat().coerceAtLeast(0f)
                }
            val avgSwipeVelocity = swipeVelocities.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
            val avgTouchPressure = touchContactSizes.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            return BehavioralSignals(
                avgInterKeystrokeMs = avgInterKeystroke,
                keystrokeRhythmVariance = rhythmVariance,
                correctionRate = correctionRate,
                dwellTimeMs = dwellTime,
                avgSwipeVelocity = avgSwipeVelocity,
                avgTouchPressure = avgTouchPressure,
                recentActionCount = recentActionTimestamps.size,
                isUnusualTimeOfDay = hour < 6 || hour >= 23,
                deviceIntegrityFailed = deviceIntegrityFailed,
            )
        }

        fun reset() {
            keystrokeTimestamps.clear()
            focusTimestampMs = null
            correctionCount = 0
            totalKeystrokes = 0
            swipeVelocities.clear()
            touchContactSizes.clear()
            // recentActionTimestamps intentionally NOT cleared — velocity-of-actions
            // is a cross-session signal within the rolling window.
        }

        private fun coefficientOfVariation(values: List<Float>): Float {
            if (values.size < 2) return 0f
            val mean = values.average()
            if (mean == 0.0) return 0f
            val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
            return (sqrt(variance) / mean).toFloat().coerceIn(0f, 5f)
        }

        private companion object {
            const val RECENT_ACTION_WINDOW_MS = 5 * 60 * 1000L // 5 minutes
        }
    }
