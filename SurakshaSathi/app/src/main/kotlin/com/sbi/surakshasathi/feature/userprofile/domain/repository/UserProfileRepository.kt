package com.sbi.surakshasathi.feature.userprofile.domain.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.feature.userprofile.domain.model.UserProfile

/**
 * Syncs a newly-created [UserProfile] to the backend's user record (Rag_model `POST
 * /v1/users/register`, upserted into the same Blue DB `user_map` table the clustering/alerting
 * pipeline already reads — see that endpoint's docstring for why no separate database is needed).
 *
 * This is sync-only: local persistence of the profile is
 * [com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore]'s job, not this repository's —
 * a failed [register] call must never block account creation on-device (§8D offline-first, same
 * discipline as every other backend call in this app).
 */
interface UserProfileRepository {
    suspend fun register(profile: UserProfile): Result<Unit>
}
