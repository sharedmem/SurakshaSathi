package com.sbi.surakshasathi.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Canonical message envelope (§4 spec) — the wire format shared by every
 * consumer of an ingested message. Built once by
 * [com.sbi.surakshasathi.feature.messagescan.data.mapper.IncomingMessageMapper]
 * and sent as-is to the RAG service (`POST /rag/analyze`, Flow 1b). Lives in
 * `core/network` (not a single feature package) because it is a cross-feature
 * wire contract, not feature-private data.
 */
@Serializable
data class MessageEnvelope(
    val message: String,
    val metadata: MessageMetadata,
)

@Serializable
data class MessageMetadata(
    val sender: String,
    val source: String,
    val receivedAt: Long,
    val device: DeviceInfo,
    val extractedUrls: List<String>,
)

@Serializable
data class DeviceInfo(
    val model: String,
    val osVersion: String,
    val locale: String,
)
