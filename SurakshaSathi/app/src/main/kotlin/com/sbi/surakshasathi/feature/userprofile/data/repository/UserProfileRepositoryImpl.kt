package com.sbi.surakshasathi.feature.userprofile.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.userprofile.data.remote.UserApi
import com.sbi.surakshasathi.feature.userprofile.data.remote.dto.UserRegistrationRequestDto
import com.sbi.surakshasathi.feature.userprofile.domain.model.UserProfile
import com.sbi.surakshasathi.feature.userprofile.domain.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [UserProfileRepository] — the real, always-default data source
 * (§1.5: no Fake* here since there's nothing to fall back to; a failed sync just means the
 * account exists locally and not yet server-side, which [register]'s caller treats as non-fatal).
 */
@Singleton
class UserProfileRepositoryImpl
    @Inject
    constructor(
        private val userApi: UserApi,
    ) : UserProfileRepository {
        override suspend fun register(profile: UserProfile): Result<Unit> =
            safeCall {
                userApi.registerUser(
                    UserRegistrationRequestDto(
                        phone = profile.phone,
                        email = profile.email,
                        persona = profile.persona,
                        language = profile.language,
                    ),
                )
                Unit
            }
    }
