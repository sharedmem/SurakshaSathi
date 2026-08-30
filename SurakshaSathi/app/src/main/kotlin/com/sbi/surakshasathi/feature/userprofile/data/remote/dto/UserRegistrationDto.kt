package com.sbi.surakshasathi.feature.userprofile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors Rag_model's `UserRegistrationRequest` pydantic model in `api/routes/user_registration.py`
 * (snake_case, the FastAPI/pydantic default; kotlinx.serialization does not auto-convert casing). */
@Serializable
data class UserRegistrationRequestDto(
    val phone: String,
    val email: String,
    val persona: String,
    val language: String,
)

/** Mirrors `UserRegistrationResponse` — `userId` is the phone number (natural key on the
 * backend's `user_map` table, same convention as `db/seed/seed_blue_db.py`). */
@Serializable
data class UserRegistrationResponseDto(
    @SerialName("user_id") val userId: String,
    val status: String,
)
