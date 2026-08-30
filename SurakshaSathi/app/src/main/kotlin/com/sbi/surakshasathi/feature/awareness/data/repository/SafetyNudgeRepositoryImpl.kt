package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.core.database.AppDatabase
import com.sbi.surakshasathi.feature.awareness.data.local.dao.SafetyNudgeDao
import com.sbi.surakshasathi.feature.awareness.data.local.entity.SafetyNudgeEntity
import com.sbi.surakshasathi.feature.awareness.domain.model.SafetyNudge
import com.sbi.surakshasathi.feature.awareness.domain.repository.SafetyNudgeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed safety nudge cache (§7c 5.3), fed by
 * [com.sbi.surakshasathi.app.service.SurakshaSathiFcmService] when a push
 * arrives on a segment topic. Enforces the anti-nag frequency cap here (max
 * [MAX_NUDGES_PER_DAY] stored per rolling 24h) — quiet-hours suppression of
 * the system notification itself lives in the FCM service, since that's
 * where "should this interrupt the user right now" is decided.
 */
@Singleton
class SafetyNudgeRepositoryImpl
    @Inject
    constructor(
        private val safetyNudgeDao: SafetyNudgeDao,
    ) : SafetyNudgeRepository {
        override fun observeNudges(): Flow<List<SafetyNudge>> =
            safetyNudgeDao.observeAll().map {
                    entities ->
                entities.map { it.toDomain() }
            }

        override suspend fun markSeen(nudgeId: String): Result<Unit> =
            safeCall {
                safetyNudgeDao.markSeen(nudgeId)
            }

        override suspend fun storeIncomingNudge(nudge: SafetyNudge): Result<Unit> =
            safeCall {
                val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                if (safetyNudgeDao.countSince(since) >= MAX_NUDGES_PER_DAY) {
                    return@safeCall // Anti-nag: silently drop, not an error.
                }
                safetyNudgeDao.upsert(
                    SafetyNudgeEntity(
                        id = nudge.id, title = nudge.title, body = nudge.body, videoUrl = nudge.videoUrl,
                        language = nudge.language, persona = nudge.persona, region = nudge.region,
                        priority = nudge.priority, seen = false, createdAtMillis = System.currentTimeMillis(),
                    ),
                )
                safetyNudgeDao.evictBeyond(AppDatabase.MAX_SAFETY_NUDGES)
            }

        private fun SafetyNudgeEntity.toDomain() =
            SafetyNudge(
                id = id, title = title, body = body, videoUrl = videoUrl, language = language,
                persona = persona, region = region, priority = priority, seen = seen,
            )

        private companion object {
            const val MAX_NUDGES_PER_DAY = 3
        }
    }
