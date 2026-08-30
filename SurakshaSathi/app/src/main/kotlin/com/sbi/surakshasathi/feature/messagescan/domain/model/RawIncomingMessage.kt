package com.sbi.surakshasathi.feature.messagescan.domain.model

/**
 * Raw input arriving from any ingestion channel (SMS/WhatsApp/Telegram)
 * BEFORE classification. Passed to [MessageRepository.classifyAndStore].
 *
 * This is NOT stored in the DB — only the resulting [Message] is persisted.
 * Domain layer: zero Android imports.
 */
data class RawIncomingMessage(
    val body: String,
    val sender: String,
    val source: MessageSource,
    val receivedAtMillis: Long = System.currentTimeMillis(),
    /** Pre-extracted URLs (may be empty; use case will extract if empty). */
    val extractedUrls: List<String> = emptyList(),
    /** Device metadata for the JSON envelope (model, OS version, locale). */
    val deviceModel: String = "",
    val deviceOsVersion: String = "",
    val deviceLocale: String = "",
)
