package com.sbi.surakshasathi.feature.messagefriction.domain.model

/** Layer C's two mediated experiences — the raw message/link is never handed back to the user
 * directly, only through one of these. */
enum class SimulationMode {
    SAFE_SIMULATION,
    GUARDIAN,
    ;

    companion object {
        /**
         * Sensible default per the message's content — a link/QR defaults to a sandboxed preview
         * (something concrete to render safely); an app-install ask or a pure text-only social-
         * engineering message (nothing to sandbox) defaults to the Guardian conversation instead.
         * The user can always switch on [com.sbi.surakshasathi.feature.messagefriction.presentation.ProtectedActionChoiceScreen].
         */
        fun defaultFor(trigger: FrictionTrigger): SimulationMode =
            when (trigger) {
                is FrictionTrigger.LinkTap -> SAFE_SIMULATION
                is FrictionTrigger.ApkLinkTap -> GUARDIAN
                FrictionTrigger.GenericProceed -> GUARDIAN
            }
    }
}
