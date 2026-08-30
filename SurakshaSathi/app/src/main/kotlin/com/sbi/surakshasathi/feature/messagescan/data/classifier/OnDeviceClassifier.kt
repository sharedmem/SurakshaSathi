package com.sbi.surakshasathi.feature.messagescan.data.classifier

/**
 * Contract for on-device message classifiers.
 *
 * Implementations:
 * - [TFLiteSpamClassifier] — the real trained model, served via TensorFlow Lite
 * - [RuleBasedClassifier] — deterministic keyword/pattern rules
 *
 * Domain-adjacent interface (data layer), but kept framework-free
 * so it can be tested without Android instrumentation.
 */
interface OnDeviceClassifier {
    /**
     * Returns a spam/phishing probability in [0.0, 1.0].
     * 0.0 = definitely safe, 1.0 = definitely malicious.
     *
     * Must complete in < 50 ms (§8A performance budget).
     * Must never throw — return 0.0 on any internal error (fail-safe).
     */
    fun classify(text: String): Float

    /** Human-readable name of this classifier (for logging / debug UIs). */
    val name: String
}
