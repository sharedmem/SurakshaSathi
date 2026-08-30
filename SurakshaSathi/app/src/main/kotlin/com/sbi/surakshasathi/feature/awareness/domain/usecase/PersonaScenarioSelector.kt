package com.sbi.surakshasathi.feature.awareness.domain.usecase

/** Persona key every game/advisory content pool falls back to when nothing more specific fits. */
const val GENERAL_PERSONA = "general"

/**
 * Stateless picker shared by every game ViewModel (and advisory content selection): prioritizes
 * items tagged for [persona], fills the remainder from [GENERAL_PERSONA]-tagged items, then any
 * leftovers, and shuffles the result into a round of [roundSize]. Not a Hilt class — a plain
 * function is the right amount of "shared engine" for 5 games whose interaction mechanics are
 * otherwise too different to unify further (§7c Phase 7 plan).
 */
object PersonaScenarioSelector {
    fun <T> pickRound(
        pool: List<T>,
        personaTagsOf: (T) -> List<String>,
        persona: String?,
        roundSize: Int,
    ): List<T> {
        if (pool.isEmpty()) return emptyList()

        val personaMatches = if (persona != null) pool.filter { personaTagsOf(it).contains(persona) } else emptyList()
        val generalMatches = pool.filter { personaTagsOf(it).contains(GENERAL_PERSONA) && it !in personaMatches }
        val remainder = pool.filter { it !in personaMatches && it !in generalMatches }

        val ordered = (personaMatches.shuffled() + generalMatches.shuffled() + remainder.shuffled())
        return ordered.distinct().take(roundSize.coerceAtMost(ordered.size))
    }
}
