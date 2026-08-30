package com.sbi.surakshasathi.feature.awareness.domain.model

/** One completed round of any Learn-tab mini-game (§7c Phase 7) — the shared result shape all 5 games funnel into. */
data class GameOutcome(
    val gameId: String,
    val score: Int,
    val totalPossible: Int,
    val correctCount: Int,
    val totalCount: Int,
    val recap: List<ScenarioRecapItem>,
    val relatedAdvisoryIds: List<String>,
    val completedAtMillis: Long,
) {
    val scoreRatio: Float get() = if (totalPossible == 0) 0f else score.toFloat() / totalPossible
}

/** A single ✅/❌ line in the post-game "what was right/wrong" recap — the detailed-learning payoff. */
data class ScenarioRecapItem(
    val scenarioId: String,
    val promptSummary: String,
    val wasCorrect: Boolean,
    val explanation: String,
)

/**
 * Builds a [GameOutcome] from a one-point-per-scenario recap (correct pick = 1 point) — shared by
 * every game whose scoring is simply "how many of these did you get right" (Fake App Detective,
 * Fraud Traffic Control, Bubble Pop Scam, Cyber Shield Defender). Secure Phone Builder scores by
 * checklist item weight instead, so it builds its own [GameOutcome] directly.
 */
fun buildGameOutcome(
    gameId: String,
    recap: List<ScenarioRecapItem>,
): GameOutcome {
    val correctCount = recap.count { it.wasCorrect }
    return GameOutcome(
        gameId = gameId,
        score = correctCount,
        totalPossible = recap.size,
        correctCount = correctCount,
        totalCount = recap.size,
        recap = recap,
        relatedAdvisoryIds = emptyList(),
        completedAtMillis = System.currentTimeMillis(),
    )
}

/** Whether this outcome cleared the game's badge threshold (see [GameBadges]). */
fun GameOutcome.isBadgeEarned(): Boolean = GameBadges.BY_GAME[gameId]?.let { scoreRatio >= it.minScoreRatio } ?: false
