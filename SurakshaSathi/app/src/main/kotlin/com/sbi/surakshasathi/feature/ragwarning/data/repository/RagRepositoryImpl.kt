package com.sbi.surakshasathi.feature.ragwarning.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.ragwarning.data.remote.RagApi
import com.sbi.surakshasathi.feature.ragwarning.data.remote.dto.MlModelMetadataDto
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning
import com.sbi.surakshasathi.feature.ragwarning.domain.repository.RagRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val TEXT_PLAIN = "text/plain".toMediaTypeOrNull()

/**
 * Production implementation of [RagRepository] — the real, always-default
 * data source (§1.5 constraint: Fake* is a fallback only, never the default).
 *
 * Calls the real, deployed Rag_model route `POST /v1/scan/message`
 * (`multipart/form-data`, see [RagApi]) with:
 * - the on-device [com.sbi.surakshasathi.feature.messagescan.data.classifier.HybridDecisionEngine]
 *   decision, JSON-encoded into `ml_model_metadata` — this is how the ML
 *   model's classification reaches the RAG agent's prompt (§ "ML model output
 *   feeds the RAG agent" requirement);
 * - the user's in-app [UserPreferencesDataStore.selectedLanguage] as the
 *   `language` field, which the backend uses to localize the returned
 *   warning/guideline text (see `api/routes/message_scan.py:_localize_report`).
 *
 * Resilience (§8D, offline-first): if the live call fails for any reason (no
 * network, backend down, timeout, bad response) OR the user has explicitly
 * enabled the offline-fallback runtime flag, this falls back to
 * [FakeRagDataSource]'s canned localized copy rather than leaving the user
 * with no warning at all.
 */
@Singleton
class RagRepositoryImpl
    @Inject
    constructor(
        private val ragApi: RagApi,
        private val fakeRagDataSource: FakeRagDataSource,
        private val preferences: UserPreferencesDataStore,
        private val json: Json,
    ) : RagRepository {
        override suspend fun analyzeMessage(message: Message): Result<RagWarning> {
            val prefs = preferences.userPreferences.first()

            if (prefs.useOfflineFallback) {
                return Result.Success(fakeRagDataSource.analyze(message, prefs.selectedLanguage))
            }

            val liveResult =
                safeCall {
                    ragApi
                        .scanMessage(
                            messageId = message.id.toString().toPart(),
                            originalMessage = message.body.toPart(),
                            language = prefs.selectedLanguage.toPart(),
                            mlModelMetadata = json.encodeToString(message.toMlModelMetadataDto()).toPart(),
                            userPersona = prefs.userPersona?.toPart(),
                            userPhone = prefs.userPhone?.toPart(),
                        ).report
                        .toDomain(messageId = message.id)
                }

            return when (liveResult) {
                is Result.Success -> liveResult
                is Result.Error -> {
                    Timber.w("RAG live analyze failed (${liveResult.error.message}) — using offline fallback")
                    Result.Success(fakeRagDataSource.analyze(message, prefs.selectedLanguage))
                }
                is Result.Loading -> liveResult
            }
        }

        private fun String.toPart(): RequestBody = toRequestBody(TEXT_PLAIN)

        private fun Message.toMlModelMetadataDto(): MlModelMetadataDto =
            MlModelMetadataDto(
                sender = sender,
                source = source.name,
                classification = classification.name,
                riskScore = riskScore,
                mlScore = mlScore,
                ruleScore = ruleScore,
                extractedUrls = extractedUrls,
            )
    }
