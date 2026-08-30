package com.sbi.surakshasathi.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.Personas
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.userprofile.domain.model.UserProfile
import com.sbi.surakshasathi.feature.userprofile.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class RegistrationFormState(
    val phone: String = "",
    val email: String = "",
    val persona: String = Personas.ALL.first().key,
    val language: String = "en",
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

/**
 * Backs [RegistrationScreen] — the one-time account-creation step that runs once, right after
 * Permissions, on a first install. On submit: persists the profile locally (the source of truth
 * for [SplashViewModel]'s routing decision) and best-effort syncs it to the backend's user record
 * — a sync failure is logged and swallowed, never blocking the user from reaching Home, per the
 * same offline-first discipline every other backend call in this app follows (§8D).
 */
@HiltViewModel
class RegistrationViewModel
    @Inject
    constructor(
        private val preferences: UserPreferencesDataStore,
        private val userProfileRepository: UserProfileRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RegistrationFormState())
        val state: StateFlow<RegistrationFormState> = _state.asStateFlow()

        fun onPhoneChanged(value: String) = _state.update { it.copy(phone = value, error = null) }

        fun onEmailChanged(value: String) = _state.update { it.copy(email = value, error = null) }

        fun onPersonaSelected(value: String) = _state.update { it.copy(persona = value) }

        fun onLanguageSelected(value: String) = _state.update { it.copy(language = value) }

        fun submit(onComplete: () -> Unit) {
            val phone = _state.value.phone.trim()
            val email = _state.value.email.trim()

            if (phone.length < 10) {
                _state.update { it.copy(error = "Enter a valid phone number") }
                return
            }
            if (!email.contains("@") || !email.substringAfter("@").contains(".")) {
                _state.update { it.copy(error = "Enter a valid email address") }
                return
            }

            viewModelScope.launch {
                _state.update { it.copy(isSubmitting = true, error = null) }
                val persona = _state.value.persona
                val language = _state.value.language

                when (val result = userProfileRepository.register(UserProfile(phone, email, persona, language))) {
                    is Result.Error -> Timber.w("User registration backend sync failed (non-fatal): ${result.error.message}")
                    else -> Unit
                }

                preferences.completeRegistration(phone = phone, email = email, persona = persona, language = language)
                _state.update { it.copy(isSubmitting = false) }
                onComplete()
            }
        }
    }
