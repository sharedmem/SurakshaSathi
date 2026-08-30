package com.sbi.surakshasathi.app.navigation

/**
 * All navigation routes in the SurakshaSathi app.
 *
 * Uses a sealed class hierarchy so routes are exhaustive and typo-safe.
 * Deep link routes (e.g. from notification "Report" action) must match
 * the intent-filter in AndroidManifest.xml.
 */
sealed class Screen(val route: String) {
    // ── Onboarding ─────────────────────────────────────────────────────────────
    data object Splash : Screen("splash")

    data object Onboarding : Screen("onboarding")

    data object Permissions : Screen("permissions")

    // First-run only, shown once after Permissions — collects phone/email/persona/language and
    // creates the user's account. Skipped entirely on every subsequent launch once
    // UserPreferencesDataStore.registrationComplete is true (see SplashScreen).
    data object Registration : Screen("registration")

    // ── Main Bottom-Nav Tabs ───────────────────────────────────────────────────
    data object Home : Screen("home")

    data object Alerts : Screen("alerts")

    data object Dashboard : Screen("dashboard")

    data object Learn : Screen("learn")

    // ── Feature Screens ────────────────────────────────────────────────────────

    // Flow 1: Message Scan
    data object MessageDetail : Screen("message/{messageId}") {
        fun createRoute(id: Long) = "message/$id"
    }

    // Flow 1b: RAG Warning
    data object RagWarning : Screen("rag_warning/{messageId}") {
        fun createRoute(id: Long) = "rag_warning/$id"
    }

    // Flow 2: APK Scan
    data object ApkScan : Screen("apk_scan")

    data object ApkAlert : Screen("apk_alert/{packageName}") {
        fun createRoute(pkg: String) = "apk_alert/$pkg"
    }

    // Flow 3: Adaptive Friction (step-up auth for the demo money-transfer screen)
    data object ConfirmTransfer : Screen("confirm_transfer")

    data object LivenessCheck : Screen("liveness_check")

    // Flow 1c: Message-Triggered Adaptive Friction (see feature/messagefriction) — a separate,
    // unrelated feature from Flow 3 above despite the similar name: this one escalates friction on
    // a RAG-flagged *message* the user is trying to act on, not a protected transaction. Chained
    // from RagWarningScreen. `triggerTag`/`url` round-trip a [com.sbi.surakshasathi.feature.messagefriction.domain.model.FrictionTrigger]
    // across the three layers; `url` is URL-encoded since it may contain '/','?','&'.
    data object IntentConfirmation : Screen("friction/intent/{messageId}/{triggerTag}?url={url}") {
        fun createRoute(
            messageId: Long,
            triggerTag: String,
            url: String?,
        ) = "friction/intent/$messageId/$triggerTag?url=${encodeUrl(url)}"
    }

    data object MicroEducation : Screen("friction/education/{messageId}/{triggerTag}?url={url}") {
        fun createRoute(
            messageId: Long,
            triggerTag: String,
            url: String?,
        ) = "friction/education/$messageId/$triggerTag?url=${encodeUrl(url)}"
    }

    data object ProtectedActionChoice : Screen("friction/choice/{messageId}/{triggerTag}?url={url}") {
        fun createRoute(
            messageId: Long,
            triggerTag: String,
            url: String?,
        ) = "friction/choice/$messageId/$triggerTag?url=${encodeUrl(url)}"
    }

    data object SafeSimulation : Screen("friction/simulation/{messageId}?url={url}") {
        fun createRoute(
            messageId: Long,
            url: String?,
        ) = "friction/simulation/$messageId?url=${encodeUrl(url)}"
    }

    data object GuardianChat : Screen("friction/guardian/{messageId}") {
        fun createRoute(messageId: Long) = "friction/guardian/$messageId"
    }

    // Flow 4a: Fraud Dashboard
    data object FraudHeatmap : Screen("fraud_heatmap")

    // Flow 4b: NCRP Report. contextType selects which auto-populate path NcrpReportViewModel
    // uses ("message" | "apk" | "manual") -- previously a single ambiguous contextId string was
    // sniffed (numeric = message, else = APK package name), which meant a manual report
    // (no message/APK context) silently misfired into the APK lookup branch.
    data object NcrpReport : Screen("ncrp_report/{contextType}/{contextId}") {
        fun createRouteForMessage(messageId: Long) = "ncrp_report/message/$messageId"

        fun createRouteForApk(packageName: String) = "ncrp_report/apk/$packageName"

        fun createRouteManual() = "ncrp_report/manual/none"
    }

    // Flow 5: Awareness
    data object LessonList : Screen("lesson_list")

    data object LessonDetail : Screen("lesson/{lessonId}") {
        fun createRoute(id: String) = "lesson/$id"
    }

    data object Badges : Screen("badges")

    // Flow 7: Games & Advisories
    data object GameHub : Screen("game_hub")

    data object ShieldDefenderGame : Screen("game/shield_defender")

    data object FraudTrafficControlGame : Screen("game/fraud_traffic_control")

    data object BubblePopScamGame : Screen("game/bubble_pop_scam")

    data object SecurePhoneBuilderGame : Screen("game/secure_phone_builder")

    data object AdvisoryList : Screen("advisories")

    data object AdvisoryDetail : Screen("advisory/{advisoryId}") {
        fun createRoute(id: String) = "advisory/$id"
    }

    companion object {
        /** Shared by the Flow 1c route builders above — a url query arg may contain '/','?','&'
         * which would otherwise break Navigation-Compose's path/query parsing. */
        fun encodeUrl(url: String?): String =
            url?.let { java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()

        fun decodeUrl(encoded: String?): String? =
            encoded?.takeIf { it.isNotEmpty() }?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }
}
