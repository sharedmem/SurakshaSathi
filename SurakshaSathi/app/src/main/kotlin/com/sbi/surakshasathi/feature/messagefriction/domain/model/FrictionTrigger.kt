package com.sbi.surakshasathi.feature.messagefriction.domain.model

/**
 * What the user tried to do that triggered the adaptive friction engine (see package doc). Not to
 * be confused with [com.sbi.surakshasathi.feature.adaptivefriction] (an unrelated feature: step-up
 * auth for the demo money-transfer screen, keyed off typing-behavior risk scoring) — this trigger
 * fires off a RAG-flagged *message*, not a protected transaction.
 *
 * Serialized into the nav route as a simple string tag + optional url — see
 * [com.sbi.surakshasathi.app.navigation.Screen]'s Flow 1c routes.
 */
sealed class FrictionTrigger {
    /** The message contains a URL the user tapped. */
    data class LinkTap(val url: String) : FrictionTrigger()

    /** The message points at an APK download / install prompt. */
    data class ApkLinkTap(val url: String) : FrictionTrigger()

    /** No specific URL/file — the message is a pure social-engineering ask (e.g. "reply with your
     * OTP") and the user indicated they still want to respond to it. */
    data object GenericProceed : FrictionTrigger()

    val urlOrNull: String?
        get() =
            when (this) {
                is LinkTap -> url
                is ApkLinkTap -> url
                GenericProceed -> null
            }

    companion object {
        const val TAG_LINK = "link"
        const val TAG_APK = "apk"
        const val TAG_GENERIC = "generic"

        fun tagFor(trigger: FrictionTrigger): String =
            when (trigger) {
                is LinkTap -> TAG_LINK
                is ApkLinkTap -> TAG_APK
                GenericProceed -> TAG_GENERIC
            }

        /** Inverse of [tagFor] — used when reconstructing the trigger from nav-route arguments. */
        fun from(
            tag: String,
            url: String?,
        ): FrictionTrigger =
            when (tag) {
                TAG_LINK -> LinkTap(url.orEmpty())
                TAG_APK -> ApkLinkTap(url.orEmpty())
                else -> GenericProceed
            }
    }
}
