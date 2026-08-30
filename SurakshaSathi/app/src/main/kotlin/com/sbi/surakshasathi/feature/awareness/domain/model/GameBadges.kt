package com.sbi.surakshasathi.feature.awareness.domain.model

/**
 * Reward configuration for each game — reuses the existing [Badge]/`BadgeDao` mechanism
 * (see `LessonRepository.awardBadge`) rather than a parallel reward primitive. Single tier per
 * game: one completion badge, unlocked once [Reward.minScoreRatio] is cleared.
 */
object GameBadges {
    data class Reward(
        val badgeId: String,
        val title: String,
        val description: String,
        val minScoreRatio: Float = 0.8f,
    )

    val BY_GAME: Map<String, Reward> =
        mapOf(
            GameType.SHIELD_DEFENDER.id to
                Reward(
                    badgeId = "badge_shield_defender",
                    title = "Shield Defender",
                    description = "Matched the right response to every kind of scam attack.",
                ),
            GameType.FRAUD_TRAFFIC_CONTROL.id to
                Reward(
                    badgeId = "badge_traffic_control",
                    title = "Traffic Controller",
                    description = "Let the genuine through and blocked every scam.",
                ),
            GameType.BUBBLE_POP_SCAM.id to
                Reward(
                    badgeId = "badge_bubble_pop",
                    title = "Bubble Popper",
                    description = "Cleared a round of Bubble Pop Scam with a sharp eye.",
                ),
            GameType.SECURE_PHONE_BUILDER.id to
                Reward(
                    badgeId = "badge_secure_phone",
                    title = "Secure Phone Builder",
                    description = "Hardened a phone's settings to a strong security score.",
                ),
        )
}
