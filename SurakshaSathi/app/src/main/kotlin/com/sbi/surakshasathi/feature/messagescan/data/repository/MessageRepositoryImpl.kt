package com.sbi.surakshasathi.feature.messagescan.data.repository

import com.sbi.surakshasathi.core.common.AppError
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.messagescan.data.classifier.HybridDecisionEngine
import com.sbi.surakshasathi.feature.messagescan.data.local.dao.MessageDao
import com.sbi.surakshasathi.feature.messagescan.data.local.entity.MessageEntity
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageClassification
import com.sbi.surakshasathi.feature.messagescan.domain.model.RawIncomingMessage
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [MessageRepository].
 *
 * Coordinates:
 * 1. [HybridDecisionEngine] — classification (TFLite + rules)
 * 2. [MessageDao] — Room persistence
 *
 * Performance target: < 50 ms per message (§8A).
 * All operations are on [Dispatchers.Default] (CPU) via the use case layer.
 */
@Singleton
class MessageRepositoryImpl
    @Inject
    constructor(
        private val messageDao: MessageDao,
        private val decisionEngine: HybridDecisionEngine,
    ) : MessageRepository {
        override fun observeMessages(): Flow<List<Message>> = messageDao.observeAll().map { entities -> entities.map { it.toDomain() } }

        override fun observeFlaggedMessages(): Flow<List<Message>> =
            messageDao.observeFlagged().map { entities -> entities.map { it.toDomain() } }

        override fun observeQuarantinedMessages(): Flow<List<Message>> =
            messageDao.observeQuarantined().map { entities -> entities.map { it.toDomain() } }

        override suspend fun classifyAndStore(raw: RawIncomingMessage): Result<Message> =
            safeCall {
                // Check for duplicate (dedup by body hash)
                val bodyHash = sha256(raw.body)
                val existing = messageDao.getByHash(bodyHash)
                if (existing != null) return@safeCall existing.toDomain()

                // Write the UNCLASSIFIED row first, before running the classifier -- this is what
                // makes "read, not yet processed" (the Message Verification screen's white state)
                // a real, observable row rather than a value nothing ever persists. Classification
                // below is CPU work (tokenization + TFLite inference); any observer collecting
                // observeMessages() during that window sees this row exactly as it is: read, not
                // yet scored.
                val pendingEntity =
                    MessageEntity(
                        body = raw.body,
                        bodyHash = bodyHash,
                        sender = raw.sender,
                        source = raw.source.name,
                        receivedAtMillis = raw.receivedAtMillis,
                        extractedUrlsRaw = raw.extractedUrls.joinToString("|"),
                        classification = MessageClassification.UNCLASSIFIED.name,
                        ragEscalated = false,
                    )
                val insertedId = messageDao.insert(pendingEntity)

                // Run hybrid classification
                val decision = decisionEngine.decide(raw.body, raw.sender, raw.source)

                val classifiedEntity =
                    pendingEntity.copy(
                        id = insertedId,
                        classification = decision.classification.name,
                        riskScore = decision.riskScore,
                        mlScore = decision.mlScore,
                        ruleScore = decision.ruleScore,
                    )
                messageDao.update(classifiedEntity)
                classifiedEntity.toDomain()
            }

        override suspend fun getById(id: Long): Result<Message> =
            safeCall {
                messageDao.getById(id)?.toDomain()
                    ?: throw AppError.UnknownError("Message $id not found")
            }

        override suspend fun markRagEscalated(messageId: Long): Result<Unit> =
            safeCall {
                messageDao.markRagEscalated(messageId)
            }

        override suspend fun saveRagWarning(
            messageId: Long,
            warning: String,
            guideline: String,
            verdict: String,
            threatType: String,
            confidence: Float,
            suspiciousSignals: List<String>,
            patternMatched: String,
            microLesson: String,
            resolvedDestination: String?,
            domainAgeDays: Int?,
            isPwaSpoofing: Boolean,
        ): Result<Unit> =
            safeCall {
                messageDao.saveRagWarning(
                    id = messageId,
                    warning = warning,
                    guideline = guideline,
                    verdict = verdict,
                    threatType = threatType,
                    confidence = confidence,
                    suspiciousSignalsRaw = suspiciousSignals.joinToString("|"),
                    patternMatched = patternMatched,
                    microLesson = microLesson,
                    resolvedDestination = resolvedDestination,
                    domainAgeDays = domainAgeDays,
                    isPwaSpoofing = isPwaSpoofing,
                )
            }

        override suspend fun quarantineMessage(
            messageId: Long,
            untilMillis: Long?,
            permanent: Boolean,
        ): Result<Unit> =
            safeCall {
                messageDao.setQuarantine(id = messageId, untilMillis = untilMillis, permanent = permanent)
            }

        override suspend fun clearExpiredQuarantines(): Result<Int> =
            safeCall {
                messageDao.clearExpiredQuarantines(System.currentTimeMillis())
            }

        override suspend fun deleteMessagesOlderThan(ttlDays: Long): Result<Int> =
            safeCall {
                val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttlDays)
                messageDao.deleteOlderThan(threshold)
            }

        override suspend fun enforceMaxMessages(maxCount: Int): Result<Unit> =
            safeCall {
                messageDao.deleteOldestBeyondLimit(maxCount)
            }

        override suspend fun purgeMessageBodiesOlderThan(bodyTtlDays: Long): Result<Int> =
            safeCall {
                val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(bodyTtlDays)
                messageDao.purgeBodyOlderThan(threshold)
            }

        private fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
