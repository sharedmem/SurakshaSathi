package com.sbi.surakshasathi.feature.ragwarning.data.repository

import com.sbi.surakshasathi.feature.messagescan.domain.model.Message
import com.sbi.surakshasathi.feature.messagescan.domain.model.MessageClassification
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagPersona
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagVerdict
import com.sbi.surakshasathi.feature.ragwarning.domain.model.RagWarning
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-resilience fallback for the RAG agent (§1.5, §8D) — canned,
 * localized warning/guideline text keyed by language.
 *
 * Used ONLY when:
 * 1. The live `POST /rag/analyze` call fails (no network / backend down), or
 * 2. `USE_OFFLINE_FALLBACK`/the runtime offline flag is explicitly set.
 *
 * Never the default data source — see [RagRepositoryImpl].
 *
 * NOTE: translations are a reasonable best-effort for the offline demo path;
 * have a native speaker review before shipping (real vernacular strings for
 * the live RAG agent are owned by that service, not this canned fallback).
 */
@Singleton
class FakeRagDataSource
    @Inject
    constructor() {
        fun analyze(
            message: Message,
            language: String,
        ): RagWarning {
            val verdict =
                when (message.classification) {
                    MessageClassification.MALICIOUS -> RagVerdict.PHISHING
                    MessageClassification.SUSPICIOUS -> RagVerdict.SCAM
                    else -> RagVerdict.SAFE
                }
            val copy = CANNED_COPY[language to verdict] ?: CANNED_COPY[("en" to verdict)]!!
            return RagWarning(
                messageId = message.id,
                verdict = verdict,
                warning = copy.first,
                guideline = copy.second,
                language = language,
                persona = RagPersona.GENERAL,
                confidence = 0.55f, // Deliberately capped — this is a heuristic fallback, not the trained agent.
                isOfflineFallback = true,
            )
        }

        private companion object {
            /** (language, verdict) -> (warning, guideline) */
            val CANNED_COPY: Map<Pair<String, RagVerdict>, Pair<String, String>> =
                mapOf(
                    ("en" to RagVerdict.PHISHING) to (
                        "This message is impersonating Bank/YONO to steal your OTP, MPIN, or card details." to
                            "Do not click any link or share OTP/MPIN. Delete the message and verify only via the official YONO app or bankname.co.in."
                    ),
                    ("en" to RagVerdict.SCAM) to (
                        "This message shows signs of a scam (urgency, reward, or unverified link)." to
                            "Do not click the link or respond. Verify directly with the Bank through the official app or branch before acting."
                    ),
                    ("hi" to RagVerdict.PHISHING) to (
                        "यह संदेश आपका OTP, MPIN या कार्ड विवरण चुराने के लिए बैंक/YONO होने का नाटक कर रहा है।" to
                            "किसी भी लिंक पर क्लिक न करें और OTP/MPIN साझा न करें। संदेश हटाएं और केवल आधिकारिक YONO ऐप या bankname.co.in से पुष्टि करें।"
                    ),
                    ("hi" to RagVerdict.SCAM) to (
                        "इस संदेश में धोखाधड़ी के लक्षण हैं (जल्दबाज़ी, इनाम, या असत्यापित लिंक)।" to
                            "लिंक पर क्लिक न करें। कार्रवाई करने से पहले आधिकारिक ऐप या शाखा से सीधे पुष्टि करें।"
                    ),
                    ("mr" to RagVerdict.PHISHING) to (
                        "हा मेसेज तुमचा OTP, MPIN किंवा कार्ड तपशील चोरण्यासाठी बँक/YONO असल्याचे भासवत आहे." to
                            "कोणत्याही लिंकवर क्लिक करू नका किंवा OTP/MPIN शेअर करू नका. मेसेज डिलीट करा आणि फक्त अधिकृत YONO अ‍ॅप किंवा bankname.co.in वरून खात्री करा."
                    ),
                    ("mr" to RagVerdict.SCAM) to (
                        "या मेसेजमध्ये फसवणुकीची चिन्हे आहेत (घाई, बक्षीस किंवा अपडेट न केलेली लिंक)." to
                            "लिंकवर क्लिक करू नका. कृती करण्यापूर्वी अधिकृत अ‍ॅप किंवा शाखेतून थेट खात्री करा."
                    ),
                    ("ta" to RagVerdict.PHISHING) to (
                        "இந்த செய்தி உங்கள் OTP, MPIN அல்லது கார்டு விவரங்களை திருட வங்கி/YONO போல் நடிக்கிறது." to
                            "எந்த இணைப்பையும் கிளிக் செய்ய வேண்டாம், OTP/MPIN பகிர வேண்டாம். செய்தியை நீக்கவும், அதிகாரப்பூர்வ YONO ஆப் அல்லது bankname.co.in மூலம் மட்டும் உறுதி செய்யவும்."
                    ),
                    ("ta" to RagVerdict.SCAM) to (
                        "இந்த செய்தியில் மோசடி அறிகுறிகள் உள்ளன (அவசரம், பரிசு, அல்லது சரிபார்க்கப்படாத இணைப்பு)." to
                            "இணைப்பைக் கிளிக் செய்ய வேண்டாம். நடவடிக்கை எடுப்பதற்கு முன் அதிகாரப்பூர்வ ஆப் அல்லது கிளையில் நேரடியாக உறுதி செய்யவும்."
                    ),
                )
        }
    }
