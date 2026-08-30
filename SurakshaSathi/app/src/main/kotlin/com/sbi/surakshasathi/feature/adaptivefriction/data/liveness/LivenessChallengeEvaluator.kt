package com.sbi.surakshasathi.feature.adaptivefriction.data.liveness

import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.LivenessChallengeType
import com.sbi.surakshasathi.feature.adaptivefriction.domain.model.LivenessResult
import javax.inject.Inject
import kotlin.math.abs

/**
 * Stateful pass/fail evaluator for the liveness challenge sequence (§6):
 * blink (eye-open → closed → open transition) followed by a head-turn OR
 * smile. Fed one [FaceAnalysisFrame] at a time from the camera analyzer;
 * call [reset] before starting a new attempt.
 */
class LivenessChallengeEvaluator
    @Inject
    constructor() {
        private var sawEyesClosed = false
        private var blinkComplete = false
        private var baselineYaw: Float? = null
        private var headTurnComplete = false
        private var smileComplete = false
        private var framesWithoutFace = 0

        fun reset() {
            sawEyesClosed = false
            blinkComplete = false
            baselineYaw = null
            headTurnComplete = false
            smileComplete = false
            framesWithoutFace = 0
        }

        /** Returns null while the challenge is still in progress; a terminal [LivenessResult] once resolved. */
        fun onFrame(frame: FaceAnalysisFrame): LivenessResult? {
            if (!frame.faceDetected) {
                framesWithoutFace++
                return if (framesWithoutFace > MAX_FRAMES_WITHOUT_FACE) {
                    LivenessResult(
                        passed = false,
                        completedChallenges = completed(),
                        failureReason = "Face not detected — hold your phone steady and face the camera.",
                    )
                } else {
                    null
                }
            }
            framesWithoutFace = 0

            evaluateBlink(frame)
            evaluateHeadTurnOrSmile(frame)

            val isComplete = blinkComplete && (headTurnComplete || smileComplete)
            return if (isComplete) LivenessResult(passed = true, completedChallenges = completed()) else null
        }

        private fun evaluateBlink(frame: FaceAnalysisFrame) {
            if (blinkComplete) return
            val leftOpen = frame.leftEyeOpenProbability ?: return
            val rightOpen = frame.rightEyeOpenProbability ?: return
            val avgOpen = (leftOpen + rightOpen) / 2f

            if (avgOpen < EYES_CLOSED_THRESHOLD) {
                sawEyesClosed = true
            } else if (avgOpen > EYES_OPEN_THRESHOLD && sawEyesClosed) {
                blinkComplete = true
            }
        }

        private fun evaluateHeadTurnOrSmile(frame: FaceAnalysisFrame) {
            if (headTurnComplete || smileComplete) return

            if (baselineYaw == null) baselineYaw = frame.headEulerAngleY
            val baseline = baselineYaw ?: return
            if (abs(frame.headEulerAngleY - baseline) > HEAD_TURN_DEGREES_THRESHOLD) {
                headTurnComplete = true
            }

            val smiling = frame.smilingProbability ?: 0f
            if (smiling > SMILE_THRESHOLD) {
                smileComplete = true
            }
        }

        private fun completed(): Set<LivenessChallengeType> =
            buildSet {
                if (blinkComplete) add(LivenessChallengeType.BLINK)
                if (headTurnComplete) add(LivenessChallengeType.HEAD_TURN)
                if (smileComplete) add(LivenessChallengeType.SMILE)
            }

        private companion object {
            const val EYES_CLOSED_THRESHOLD = 0.3f
            const val EYES_OPEN_THRESHOLD = 0.7f
            const val HEAD_TURN_DEGREES_THRESHOLD = 15f
            const val SMILE_THRESHOLD = 0.7f
            const val MAX_FRAMES_WITHOUT_FACE = 90 // ~3s at ~30fps analysis throttle
        }
    }
