package com.sbi.surakshasathi.feature.awareness.domain.model

enum class TrafficChannel { SMS, CALL, QR, APK, WEBSITE }

/** One incoming item in the Fraud Traffic Control decision queue — Allow the genuine, Block the scam. */
data class TrafficItem(
    val id: String,
    val channel: TrafficChannel,
    val summary: String,
    val isGenuine: Boolean,
    val personaTags: List<String>,
    val explanation: String,
)
