package com.sbi.surakshasathi.feature.adaptivefriction.presentation

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity

/**
 * PIN_CHALLENGE (§6) — delegates "secondary PIN" to the system's own
 * confirm-device-credential flow via [BiometricPrompt], rather than building
 * a custom PIN screen: it's more secure (no app-level PIN to leak/replicate),
 * reuses hardware-backed biometrics when enrolled, and falls back to the
 * device PIN/pattern/password automatically via DEVICE_CREDENTIAL.
 */
private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

fun canShowPinChallenge(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    return manager.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
}

fun showPinChallenge(
    activity: FragmentActivity,
    onResult: (Boolean) -> Unit,
) {
    val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
    val prompt =
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    onResult(false)
                }

                override fun onAuthenticationFailed() {
                    // A single failed attempt — let the user retry via the prompt's own UI.
                }
            },
        )

    val promptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm it's you")
            .setSubtitle("Verify your identity to continue this transfer")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

    prompt.authenticate(promptInfo)
}
