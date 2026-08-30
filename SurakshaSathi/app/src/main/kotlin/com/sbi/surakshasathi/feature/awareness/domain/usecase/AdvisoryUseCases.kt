package com.sbi.surakshasathi.feature.awareness.domain.usecase

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.awareness.domain.model.Advisory
import com.sbi.surakshasathi.feature.awareness.domain.repository.AdvisoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveAdvisoriesUseCase
    @Inject
    constructor(private val repository: AdvisoryRepository) {
        operator fun invoke(language: String): Flow<List<Advisory>> = repository.observeAdvisories(language)
    }

/** Persona-tagged advisories first, then general ones — same priority order as [PersonaScenarioSelector]. */
class ObserveAdvisoriesForPersonaUseCase
    @Inject
    constructor(private val repository: AdvisoryRepository) {
        operator fun invoke(
            language: String,
            persona: String?,
        ): Flow<List<Advisory>> =
            repository.observeAdvisories(language).map { advisories ->
                val personaMatches = if (persona != null) advisories.filter { persona in it.personaTags } else emptyList()
                val rest = advisories.filter { it !in personaMatches }
                personaMatches + rest
            }
    }

class RefreshAdvisoriesUseCase
    @Inject
    constructor(private val repository: AdvisoryRepository) {
        suspend operator fun invoke(language: String): Result<Unit> = repository.refreshAdvisories(language)
    }
