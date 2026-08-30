package com.sbi.surakshasathi.core.common

import java.io.IOException

/**
 * Typed error hierarchy for all application errors.
 *
 * Rules:
 * - Domain and data layers produce [AppError] subtypes.
 * - ViewModels map [AppError] to user-facing UiState error messages.
 * - Never pass raw [Throwable] or stack traces to the UI.
 */
sealed class AppError : Exception() {
    // ── Network Errors ────────────────────────────────────────────────────────

    /** No network connectivity. */
    data object NoNetworkError : AppError() {
        override val message: String = "No network connection. Please check your internet."

        private fun readResolve(): Any = NoNetworkError
    }

    /** HTTP error from a backend call. */
    data class HttpError(
        val code: Int,
        override val message: String,
    ) : AppError()

    /** Unexpected API response shape or serialization failure. */
    data class ParseError(override val cause: Throwable? = null) : AppError() {
        override val message: String = "Unexpected response from server."
    }

    /** Request timed out. */
    data object TimeoutError : AppError() {
        override val message: String = "Request timed out. Please try again."

        private fun readResolve(): Any = TimeoutError
    }

    // ── Local / Data Errors ───────────────────────────────────────────────────

    /** Room or SQLCipher DB error. */
    data class DatabaseError(override val cause: Throwable? = null) : AppError() {
        override val message: String = "Local storage error. Please restart the app."
    }

    /** DataStore read/write failure. */
    data class PreferencesError(override val cause: Throwable? = null) : AppError() {
        override val message: String = "Failed to read/write preferences."
    }

    // ── ML / Classification Errors ────────────────────────────────────────────

    /** TFLite model could not be loaded or interpreted. */
    data class ModelLoadError(override val cause: Throwable? = null) : AppError() {
        override val message: String = "On-device model unavailable. Using rule-based fallback."
    }

    // ── Permission / Policy Errors ────────────────────────────────────────────

    /** A required permission was not granted by the user. */
    data class PermissionDeniedError(val permissionName: String) : AppError() {
        override val message: String = "Permission denied: $permissionName"
    }

    /** Notification Listener Service is not enabled. */
    data object NotificationListenerNotEnabled : AppError() {
        override val message: String = "Notification access not enabled."

        private fun readResolve(): Any = NotificationListenerNotEnabled
    }

    // ── Security / Integrity Errors ───────────────────────────────────────────

    /** Play Integrity API check failed or returned an unexpected verdict. */
    data class IntegrityCheckError(override val cause: Throwable? = null) : AppError() {
        override val message: String = "Device integrity check failed."
    }

    /** APK impersonation detected — package looks like Bank/YONO but isn't. */
    data class ImpersonationDetectedError(val packageName: String) : AppError() {
        override val message: String =
            "⚠️ Impersonation detected: $packageName appears to be a fake Bank app."
    }

    // ── Generic / Unknown ─────────────────────────────────────────────────────

    /** Catch-all for unexpected errors not handled by other subtypes. */
    data class UnknownError(
        override val message: String = "An unexpected error occurred.",
        override val cause: Throwable? = null,
    ) : AppError()

    // ── Utilities ─────────────────────────────────────────────────────────────

    fun toException(): Exception = RuntimeException(message, cause)

    companion object {
        /**
         * Maps any [Throwable] to a typed [AppError].
         * Add more specific mappings as new error cases arise.
         */
        fun fromException(e: Throwable): AppError =
            when (e) {
                is AppError -> e
                is IOException ->
                    if (e.message?.contains("timeout", ignoreCase = true) == true) {
                        TimeoutError
                    } else {
                        NoNetworkError
                    }
                is retrofit2.HttpException ->
                    HttpError(code = e.code(), message = e.message())
                is kotlinx.serialization.SerializationException ->
                    ParseError(cause = e)
                else -> UnknownError(message = e.message ?: "Unknown error", cause = e)
            }
    }
}
