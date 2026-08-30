package com.sbi.surakshasathi.feature.adaptivefriction.data.liveness

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Face Detection wrapper for the liveness challenge (§6). Approximates
 * liveness with blink-probability + head-turn/smile motion — NOT true
 * facial-depth mapping, which needs a depth sensor most devices lack. Every
 * caller-facing surface must label this honestly (see [com.sbi.surakshasathi.feature.adaptivefriction.presentation.LivenessCheckScreen]).
 *
 * On-device only; no frame ever leaves the phone.
 */
@Singleton
class LivenessAnalyzer
    @Inject
    constructor() {
        private val detector =
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .build(),
            )

        /** Analyzes one camera frame. Always closes [imageProxy] — the frame is never retained. */
        @OptIn(ExperimentalGetImage::class)
        suspend fun analyze(imageProxy: ImageProxy): FaceAnalysisFrame {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return EMPTY_FRAME
            }
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            return try {
                val faces =
                    suspendCancellableCoroutine { continuation ->
                        detector.process(inputImage)
                            .addOnSuccessListener { faces -> continuation.resume(faces) }
                            .addOnFailureListener { error -> continuation.resumeWithException(error) }
                    }
                val face = faces.firstOrNull() ?: return EMPTY_FRAME
                FaceAnalysisFrame(
                    leftEyeOpenProbability = face.leftEyeOpenProbability,
                    rightEyeOpenProbability = face.rightEyeOpenProbability,
                    headEulerAngleY = face.headEulerAngleY,
                    smilingProbability = face.smilingProbability,
                    faceDetected = true,
                )
            } catch (e: Exception) {
                EMPTY_FRAME
            } finally {
                imageProxy.close()
            }
        }

        fun close() = detector.close()

        private companion object {
            val EMPTY_FRAME = FaceAnalysisFrame(null, null, 0f, null, faceDetected = false)
        }
    }
