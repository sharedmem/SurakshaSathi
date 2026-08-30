package com.sbi.surakshasathi.feature.apkscan.domain.notifier

import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkScanResult

/**
 * Domain-layer contract for surfacing a malicious/impersonating
 * [ApkScanResult] to the user (§5 blocking full-screen warning). Kept as an
 * interface so Android notification types stay out of the domain layer.
 */
interface ApkAlertDispatcher {
    fun notify(result: ApkScanResult)
}
