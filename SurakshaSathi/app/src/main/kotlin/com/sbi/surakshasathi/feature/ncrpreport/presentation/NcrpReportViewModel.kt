package com.sbi.surakshasathi.feature.ncrpreport.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.datastore.UserPreferencesDataStore
import com.sbi.surakshasathi.feature.apkscan.domain.repository.ApkScanRepository
import com.sbi.surakshasathi.feature.apkscan.domain.usecase.CheckDeviceIntegrityUseCase
import com.sbi.surakshasathi.feature.messagescan.domain.repository.MessageRepository
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.ForensicReport
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.NcrpCaseResult
import com.sbi.surakshasathi.feature.ncrpreport.domain.model.ReportSource
import com.sbi.surakshasathi.feature.ncrpreport.domain.usecase.SubmitNcrpReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Editable fields shown to the user — both for a from-scratch manual report and for reviewing/
 * correcting an auto-populated one (auto-populated fields are pre-filled but stay editable, since
 * the on-device diagnosis can be wrong or incomplete and the user often has more context). */
data class ManualReportFormState(
    val offendingSender: String = "",
    val offendingMessageBody: String = "",
    val offendingUrls: String = "", // one per line in the UI; split on submit
    val apkPackageName: String = "",
    val userDescription: String = "",
    val reporterName: String = "",
    val reporterPhone: String = "",
    val reporterEmail: String = "",
    val regionLabel: String = "",
    val consentChecked: Boolean = false,
)

sealed interface NcrpReportUiState {
    data object Loading : NcrpReportUiState

    data class Editing(
        val form: ManualReportFormState,
        val source: ReportSource,
        /** Read-only bullet points summarizing the auto-populated diagnosis (verdict, risk
         * signals, etc.) that isn't itself directly editable but IS submitted verbatim alongside
         * the editable fields — see [NcrpReportViewModel.diagnosisReport]. Empty for MANUAL. */
        val diagnosisSummary: List<String>,
        val validationError: String? = null,
    ) : NcrpReportUiState

    data object Submitting : NcrpReportUiState

    data class Submitted(val result: NcrpCaseResult) : NcrpReportUiState

    data class Error(val message: String) : NcrpReportUiState
}

/**
 * Backs the NCRP report screen (§7b) — both reporting paths described in the product ask:
 * 1. Automatic: [contextType] is "message" or "apk" — pre-populates from that message's/APK's
 *    on-device + RAG/Tier-1 diagnosis (see [Companion CONTEXT_TYPE_*]).
 * 2. Manual: [contextType] is "manual" — every field starts blank (except remembered complainant
 *    details) for the user to fill in themselves.
 *
 * Either way the user reviews/edits before consenting; nothing is sent until [onSubmit] with
 * consent checked. Submission transport itself (NcrpApi -> real I4C/NCRP endpoint) is a separate,
 * deliberately-deferred concern — see [com.sbi.surakshasathi.feature.ncrpreport.data.repository.NcrpReportRepositoryImpl],
 * which already queues guaranteed-delivery retries regardless of whether that endpoint is live.
 *
 * NOTE: location lat/lng is intentionally left blank in this build — collecting it would require
 * a separate location-permission flow this screen doesn't request; [regionLabel] is pre-filled
 * from [UserPreferencesDataStore.userRegion] instead (no extra permission needed), and the domain
 * model still supports precise lat/lng for when that's wired in.
 */
@HiltViewModel
class NcrpReportViewModel
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val apkScanRepository: ApkScanRepository,
        private val checkDeviceIntegrityUseCase: CheckDeviceIntegrityUseCase,
        private val submitNcrpReportUseCase: SubmitNcrpReportUseCase,
        private val preferences: UserPreferencesDataStore,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val contextType: String = savedStateHandle.get<String>("contextType") ?: CONTEXT_TYPE_MANUAL
        private val contextId: String = savedStateHandle.get<String>("contextId").orEmpty()

        private val _uiState = MutableStateFlow<NcrpReportUiState>(NcrpReportUiState.Loading)
        val uiState: StateFlow<NcrpReportUiState> = _uiState.asStateFlow()

        /** Non-editable diagnosis fields (RAG verdict, APK tier/rule hits, etc.) captured at load
         * time and merged with the editable [ManualReportFormState] only at submit time. */
        private var diagnosisReport =
            ForensicReport(reportSource = ReportSource.MANUAL, reportedAtMillis = 0, reporterConsent = false)

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val prefs = preferences.userPreferences.first()
            val baseForm =
                ManualReportFormState(
                    reporterName = prefs.reporterName.orEmpty(),
                    reporterPhone = prefs.reporterPhone.orEmpty(),
                    reporterEmail = prefs.reporterEmail.orEmpty(),
                    regionLabel = prefs.userRegion.orEmpty(),
                )

            when (contextType) {
                CONTEXT_TYPE_MESSAGE -> loadFromMessage(baseForm)
                CONTEXT_TYPE_APK -> loadFromApk(baseForm)
                else -> {
                    diagnosisReport = ForensicReport(reportSource = ReportSource.MANUAL, reportedAtMillis = 0, reporterConsent = false)
                    _uiState.value = NcrpReportUiState.Editing(form = baseForm, source = ReportSource.MANUAL, diagnosisSummary = emptyList())
                }
            }
        }

        private suspend fun loadFromMessage(baseForm: ManualReportFormState) {
            val messageId = contextId.toLongOrNull()
            val message = messageId?.let { (messageRepository.getById(it) as? Result.Success)?.data }

            diagnosisReport =
                ForensicReport(
                    reportSource = ReportSource.AUTO_MESSAGE,
                    ragVerdict = message?.ragVerdict,
                    ragThreatType = message?.ragThreatType,
                    ragConfidence = message?.ragConfidence,
                    ragSuspiciousSignals = message?.ragSuspiciousSignals.orEmpty(),
                    reportedAtMillis = 0,
                    reporterConsent = false,
                )

            val summary =
                buildList {
                    message?.let { add("On-device classification: ${it.classification}") }
                    message?.ragVerdict?.let { verdict ->
                        add("RAG verdict: $verdict (${message.ragThreatType ?: "unspecified"})")
                    }
                    message?.ragConfidence?.let { add("RAG confidence: ${(it * 100).toInt()}%") }
                    if (message?.ragSuspiciousSignals.orEmpty().isNotEmpty()) {
                        add("Signals: " + message!!.ragSuspiciousSignals.joinToString(", "))
                    }
                    if (message == null) add("Original message could not be loaded — fill in details manually below.")
                }

            _uiState.value =
                NcrpReportUiState.Editing(
                    form =
                        baseForm.copy(
                            offendingSender = message?.sender.orEmpty(),
                            offendingMessageBody = message?.body.orEmpty(),
                            offendingUrls = message?.extractedUrls.orEmpty().joinToString("\n"),
                        ),
                    source = ReportSource.AUTO_MESSAGE,
                    diagnosisSummary = summary,
                )
        }

        private suspend fun loadFromApk(baseForm: ManualReportFormState) {
            val apkResult = (apkScanRepository.getCachedResult(contextId) as? Result.Success)?.data

            diagnosisReport =
                ForensicReport(
                    reportSource = ReportSource.AUTO_APK,
                    apkSha256 = apkResult?.sha256,
                    apkVerdict = apkResult?.verdict?.name,
                    apkIsImpersonation = apkResult?.isImpersonation ?: false,
                    apkTierReached = apkResult?.tierReached?.name,
                    apkLocalRiskScore = apkResult?.localRiskScore,
                    apkTriggeredRuleIds = apkResult?.triggeredRuleIds.orEmpty(),
                    apkEngineHits = apkResult?.engineHits,
                    reportedAtMillis = 0,
                    reporterConsent = false,
                )

            val summary =
                buildList {
                    apkResult?.verdict?.let { add("Verdict: $it") }
                    if (apkResult?.isImpersonation == true) add("Impersonates official banking app branding")
                    apkResult?.tierReached?.let { add("Detected at: $it") }
                    if (apkResult?.triggeredRuleIds.orEmpty().isNotEmpty()) {
                        add("Rules triggered: " + apkResult!!.triggeredRuleIds.joinToString(", "))
                    }
                    if (apkResult == null) add("Scan result could not be loaded — fill in the app details manually below.")
                }

            _uiState.value =
                NcrpReportUiState.Editing(
                    form = baseForm.copy(apkPackageName = apkResult?.packageName ?: contextId),
                    source = ReportSource.AUTO_APK,
                    diagnosisSummary = summary,
                )
        }

        fun onFormChange(form: ManualReportFormState) {
            val current = _uiState.value as? NcrpReportUiState.Editing ?: return
            _uiState.value = current.copy(form = form, validationError = null)
        }

        fun onSubmit() {
            val current = _uiState.value as? NcrpReportUiState.Editing ?: return
            val form = current.form
            if (!form.consentChecked) {
                _uiState.value = current.copy(validationError = "Please confirm consent before submitting.")
                return
            }
            val hasAnyEvidence =
                form.offendingMessageBody.isNotBlank() || form.apkPackageName.isNotBlank() ||
                    form.userDescription.isNotBlank() || diagnosisReport.reportSource != ReportSource.MANUAL
            if (!hasAnyEvidence) {
                _uiState.value = current.copy(validationError = "Describe what happened, or add the message/app details, before submitting.")
                return
            }

            viewModelScope.launch {
                _uiState.value = NcrpReportUiState.Submitting

                preferences.setReporterDetails(
                    name = form.reporterName.ifBlank { null },
                    phone = form.reporterPhone.ifBlank { null },
                    email = form.reporterEmail.ifBlank { null },
                )

                val integrity =
                    when (val result = checkDeviceIntegrityUseCase()) {
                        is Result.Success -> result.data
                        else -> null
                    }

                val report =
                    diagnosisReport.copy(
                        offendingSender = form.offendingSender.ifBlank { null },
                        offendingMessageBody = form.offendingMessageBody.ifBlank { null },
                        offendingUrls = form.offendingUrls.lines().map { it.trim() }.filter { it.isNotBlank() },
                        apkPackageName = form.apkPackageName.ifBlank { null },
                        userDescription = form.userDescription.ifBlank { null },
                        reporterName = form.reporterName.ifBlank { null },
                        reporterPhone = form.reporterPhone.ifBlank { null },
                        reporterEmail = form.reporterEmail.ifBlank { null },
                        regionLabel = form.regionLabel.ifBlank { null },
                        deviceIntegrityStatus = integrity?.status?.name,
                        deviceRooted = integrity?.isRooted ?: false,
                        reportedAtMillis = System.currentTimeMillis(),
                        reporterConsent = true,
                    )

                when (val result = submitNcrpReportUseCase(report)) {
                    is Result.Success -> _uiState.value = NcrpReportUiState.Submitted(result.data)
                    is Result.Error -> _uiState.value = NcrpReportUiState.Error(result.error.message ?: "Submission failed")
                    is Result.Loading -> Unit
                }
            }
        }

        fun onRetry() {
            _uiState.value =
                NcrpReportUiState.Editing(
                    form = ManualReportFormState(consentChecked = true),
                    source = diagnosisReport.reportSource,
                    diagnosisSummary = emptyList(),
                )
            viewModelScope.launch { load() }
        }

        companion object {
            const val CONTEXT_TYPE_MESSAGE = "message"
            const val CONTEXT_TYPE_APK = "apk"
            const val CONTEXT_TYPE_MANUAL = "manual"
        }
    }
