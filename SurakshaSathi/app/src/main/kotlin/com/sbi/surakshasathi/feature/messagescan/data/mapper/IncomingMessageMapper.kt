package com.sbi.surakshasathi.feature.messagescan.data.mapper

import android.os.Build
import com.sbi.surakshasathi.core.network.dto.DeviceInfo
import com.sbi.surakshasathi.core.network.dto.MessageEnvelope
import com.sbi.surakshasathi.core.network.dto.MessageMetadata
import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageSource
import com.sbi.surakshasathi.feature.messagescan.domain.model.RawIncomingMessage
import com.sbi.surakshasathi.feature.messagescan.domain.usecase.ExtractUrlsUseCase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts raw ingested data (from any source) into [RawIncomingMessage]
 * and generates the canonical JSON metadata envelope used for RAG analysis.
 *
 * Canonical envelope (§4 spec):
 * ```json
 * {
 *   "message": "<raw text>",
 *   "metadata": {
 *     "sender": "...", "source": "SMS|WHATSAPP|TELEGRAM",
 *     "receivedAt": 1234567890,
 *     "device": { "model": "...", "osVersion": "...", "locale": "..." },
 *     "extractedUrls": ["..."]
 *   }
 * }
 * ```
 *
 * Owner: Swami (per team ownership in §3)
 */
@Singleton
class IncomingMessageMapper
    @Inject
    constructor(
        private val extractUrlsUseCase: ExtractUrlsUseCase,
        private val json: Json,
    ) {
        /**
         * Builds a [RawIncomingMessage] from notification/SMS fields.
         * Extracts URLs automatically if [preExtractedUrls] is empty.
         */
        fun map(
            body: String,
            sender: String,
            source: MessageSource,
            receivedAtMillis: Long = System.currentTimeMillis(),
            preExtractedUrls: List<String> = emptyList(),
        ): RawIncomingMessage {
            val urls = preExtractedUrls.ifEmpty { extractUrlsUseCase(body) }
            return RawIncomingMessage(
                body = body,
                sender = sender,
                source = source,
                receivedAtMillis = receivedAtMillis,
                extractedUrls = urls,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                deviceOsVersion = Build.VERSION.RELEASE,
                deviceLocale = Locale.getDefault().toLanguageTag(),
            )
        }

        /**
         * Builds the canonical [MessageEnvelope] for transmission to the RAG
         * service (`POST /rag/analyze`, Flow 1b) — a typed Retrofit request body,
         * not a hand-serialized string.
         */
        fun toEnvelope(raw: RawIncomingMessage): MessageEnvelope =
            MessageEnvelope(
                message = raw.body,
                metadata =
                    MessageMetadata(
                        sender = raw.sender,
                        source = raw.source.name,
                        receivedAt = raw.receivedAtMillis,
                        device =
                            DeviceInfo(
                                model = raw.deviceModel,
                                osVersion = raw.deviceOsVersion,
                                locale = raw.deviceLocale,
                            ),
                        extractedUrls = raw.extractedUrls,
                    ),
            )

        /** JSON string form of [toEnvelope] — for logging/debugging only. */
        fun toJsonEnvelope(raw: RawIncomingMessage): String = json.encodeToString(toEnvelope(raw))

        /**
         * Builds the [MessageEnvelope] for an already-persisted [Message] (used
         * when escalating to RAG in Flow 1b). [Message] doesn't retain
         * per-ingestion device metadata — it's the same physical device, so we
         * read current [Build] values rather than adding storage for it.
         */
        fun toEnvelopeFromMessage(message: Message): MessageEnvelope =
            MessageEnvelope(
                message = message.body,
                metadata =
                    MessageMetadata(
                        sender = message.sender,
                        source = message.source.name,
                        receivedAt = message.receivedAtMillis,
                        device =
                            DeviceInfo(
                                model = "${Build.MANUFACTURER} ${Build.MODEL}",
                                osVersion = Build.VERSION.RELEASE,
                                locale = Locale.getDefault().toLanguageTag(),
                            ),
                        extractedUrls = message.extractedUrls,
                    ),
            )
    }
