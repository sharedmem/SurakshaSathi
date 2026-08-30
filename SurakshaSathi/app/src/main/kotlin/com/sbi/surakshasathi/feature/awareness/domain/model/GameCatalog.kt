package com.sbi.surakshasathi.feature.awareness.domain.model

/**
 * The 5 Learn-tab mini-games (§7c Phase 7). Domain layer only — zero Android/framework imports;
 * icons/colors are mapped from [id] in the presentation layer (see `GameHubScreen.kt`).
 */
enum class GameType(val id: String, val title: String, val tagline: String) {
    SHIELD_DEFENDER(
        id = "shield_defender",
        title = "Cyber Shield Defender",
        tagline = "Drag the right shield onto each incoming attack",
    ),
    FRAUD_TRAFFIC_CONTROL(
        id = "fraud_traffic_control",
        title = "Fraud Traffic Control",
        tagline = "Allow the genuine, block the scam — decide fast",
    ),
    BUBBLE_POP_SCAM(
        id = "bubble_pop_scam",
        title = "Bubble Pop Scam",
        tagline = "Pop only the scam bubbles before time runs out",
    ),
    SECURE_PHONE_BUILDER(
        id = "secure_phone_builder",
        title = "Build Your Secure Phone",
        tagline = "Harden a broken phone's settings and score its safety",
    ),
    ;

    companion object {
        fun fromId(id: String): GameType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Which games are most relevant to each persona, for Game Hub ordering only — every game stays
 * playable by everyone, this just decides display order. Mirrors the brainstormed
 * Persona → Primary Games mapping (business owners lean toward payment/traffic scenarios,
 * seniors toward device/checklist basics, etc.).
 */
object GamePersonaOrdering {
    private val PRIMARY_GAMES: Map<String, List<GameType>> =
        mapOf(
            "business_owner" to listOf(GameType.FRAUD_TRAFFIC_CONTROL, GameType.SHIELD_DEFENDER, GameType.BUBBLE_POP_SCAM),
            "student" to listOf(GameType.SHIELD_DEFENDER, GameType.BUBBLE_POP_SCAM, GameType.FRAUD_TRAFFIC_CONTROL),
            "homemaker" to listOf(GameType.FRAUD_TRAFFIC_CONTROL, GameType.BUBBLE_POP_SCAM, GameType.SHIELD_DEFENDER),
            "senior_citizen" to listOf(GameType.SECURE_PHONE_BUILDER, GameType.BUBBLE_POP_SCAM, GameType.SHIELD_DEFENDER),
            "salaried_professional" to listOf(GameType.FRAUD_TRAFFIC_CONTROL, GameType.SHIELD_DEFENDER, GameType.BUBBLE_POP_SCAM),
        )

    fun orderFor(persona: String?): List<GameType> {
        val primary = PRIMARY_GAMES[persona].orEmpty()
        return primary + GameType.entries.filter { it !in primary }
    }
}
