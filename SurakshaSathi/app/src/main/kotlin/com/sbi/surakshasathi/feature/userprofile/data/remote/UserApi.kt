package com.sbi.surakshasathi.feature.userprofile.data.remote

import com.sbi.surakshasathi.feature.userprofile.data.remote.dto.UserRegistrationRequestDto
import com.sbi.surakshasathi.feature.userprofile.data.remote.dto.UserRegistrationResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit contract for Rag_model's `POST /v1/users/register` (`api/routes/user_registration.py`)
 * — a plain JSON body, unlike [com.sbi.surakshasathi.feature.ragwarning.data.remote.RagApi]'s
 * multipart shape, since there's no file attachment here.
 */
interface UserApi {
    @POST("v1/users/register")
    suspend fun registerUser(
        @Body body: UserRegistrationRequestDto,
    ): UserRegistrationResponseDto
}
