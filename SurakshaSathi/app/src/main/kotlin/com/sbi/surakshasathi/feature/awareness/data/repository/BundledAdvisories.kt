package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.feature.awareness.domain.model.Advisory
import com.sbi.surakshasathi.feature.awareness.domain.model.AdvisoryCategory
import com.sbi.surakshasathi.feature.awareness.domain.usecase.GENERAL_PERSONA

/**
 * Bundled offline default advisory set (§7c Phase 7) — ships in the client so the Advisories
 * tab works fully with no backend, same convention as [BundledLessons]. Content is informed by
 * cybercrime.gov.in, I4C's CyberDost advisories, cytrain.ncrb.gov.in, TRAI's advice-to-senders
 * guidance, and Truecaller/PandaSecurity's documented spam-text patterns, cited per article via
 * [Advisory.sourceLabel]/[Advisory.sourceUrl].
 */
object BundledAdvisories {
    val ENGLISH: List<Advisory> =
        listOf(
            Advisory(
                id = "adv_first_hour_after_scam",
                title = "The first hour after you've been scammed",
                body =
                    "Act fast: every minute matters before stolen funds are moved further. First, call 1930 " +
                        "(the national cybercrime helpline) or report at cybercrime.gov.in immediately — both are " +
                        "run by the Indian Cyber Crime Coordination Centre (I4C) and can trigger a bank-side freeze " +
                        "on the receiving account within the \"golden hour.\" Then call your bank's official helpline " +
                        "(the number on your card or passbook, never one from a message) to block your card or UPI. " +
                        "Change your net-banking and UPI PIN, and if a malicious app was installed, uninstall it and " +
                        "run a security scan before logging into anything again. Finally, keep screenshots of the " +
                        "message, call, or app — you'll need them for your complaint.",
                category = AdvisoryCategory.POST_INCIDENT,
                personaTags = listOf(GENERAL_PERSONA),
                language = "en",
                sourceLabel = "cybercrime.gov.in / 1930 National Cybercrime Helpline",
                sourceUrl = "https://cybercrime.gov.in/Webform/CrimeCatDes.aspx",
            ),
            Advisory(
                id = "adv_otp_never_share",
                title = "Never share your OTP — with anyone, for any reason",
                body =
                    "An OTP exists to prove that the person completing a transaction is you — it is not a " +
                        "\"verification code\" a bank employee, delivery agent, or customer care executive ever needs " +
                        "from you over a call or chat. Scammers impersonate Bank officers, claim they need to " +
                        "\"reverse a wrongly credited amount,\" or say your card will be blocked unless you " +
                        "\"confirm\" with the code just sent to you. In every one of these cases, the OTP was " +
                        "actually authorizing a transaction the scammer initiated. If you're ever asked for an OTP " +
                        "by anyone other than the payment screen in front of you, hang up and do not share it.",
                category = AdvisoryCategory.OTP_SAFETY,
                personaTags = listOf(GENERAL_PERSONA),
                language = "en",
                sourceLabel = "CyberDost (I4C) — Ministry of Home Affairs",
                sourceUrl = "https://cybercrime.gov.in/Webform/CrimeCatDes.aspx",
            ),
            Advisory(
                id = "adv_boss_zip_whatsapp_scam",
                title = "The \"Boss\" WhatsApp .zip file scam",
                body =
                    "A new WhatsApp scam flagged by I4C targets working professionals: scammers send a .zip file " +
                        "disguised as an account statement or salary document, using the display picture and name " +
                        "of your actual boss or manager. The moment the file is downloaded and opened, it silently " +
                        "hacks the recipient's WhatsApp. The attacker then uses your boss's real photo and name to " +
                        "message your finance team or colleagues demanding an \"urgent payment.\" Never open a .zip " +
                        "file sent unexpectedly, even from a known contact's photo and name — always confirm by a " +
                        "direct phone call first, since a compromised or spoofed account looks identical to the real one.",
                category = AdvisoryCategory.SOCIAL_ENGINEERING,
                personaTags = listOf("business_owner", "salaried_professional"),
                language = "en",
                sourceLabel = "Indian Cyber Crime Coordination Centre (I4C)",
                sourceUrl = "https://cybercrime.gov.in/Webform/CrimeCatDes.aspx",
            ),
            Advisory(
                id = "adv_gst_refund_sms",
                title = "Fake GST refund and filing messages",
                body =
                    "Business owners are frequently targeted with SMS or email claiming a GST refund is \"pending\" " +
                        "and providing a link to \"claim\" it. The Goods and Services Tax Network (GSTN) never sends " +
                        "refund claim links by SMS — refund status is only ever visible by logging into the official " +
                        "gst.gov.in portal directly. These messages are designed to harvest your GSTIN, PAN, and " +
                        "banking details on a lookalike page. Always type the GST portal address yourself rather " +
                        "than tapping a link, and double-check the domain in your browser bar before entering any " +
                        "business credentials.",
                category = AdvisoryCategory.PHISHING,
                personaTags = listOf("business_owner"),
                language = "en",
                sourceLabel = "Goods and Services Tax Network (GSTN)",
                sourceUrl = "https://cybercrime.gov.in/Webform/CrimeCatDes.aspx",
            ),
            Advisory(
                id = "adv_qr_payment_fraud",
                title = "QR code payment fraud — for shoppers and shopkeepers",
                body =
                    "Scanning a QR code to pay someone should never require entering your UPI PIN to \"receive\" " +
                        "money — that pattern (\"scan this code to get your cashback/refund\") is always a way to " +
                        "trick you into paying the scammer instead. Before confirming any QR payment, check the " +
                        "payee name shown on screen matches who you intend to pay. Shopkeepers should periodically " +
                        "verify their displayed QR sticker hasn't been physically swapped or pasted over by someone " +
                        "posing as a customer or delivery agent, redirecting payments to a different UPI ID entirely.",
                category = AdvisoryCategory.QR_FRAUD,
                personaTags = listOf("business_owner", "homemaker"),
                language = "en",
                sourceLabel = "Truecaller — Scam & Spam Insights",
                sourceUrl = "https://www.truecaller.com/blog/category/all",
            ),
            Advisory(
                id = "adv_scholarship_internship_apk",
                title = "Fake scholarship and internship apps targeting students",
                body =
                    "Scammers advertise \"guaranteed\" internships or scholarships that require installing an " +
                        "unfamiliar app or paying a small \"registration\" or \"processing\" fee upfront. Genuine " +
                        "scholarships and campus placements never ask you to pay to receive money, and never " +
                        "guarantee a spot before you've even applied. Verify any offer independently through the " +
                        "official company careers page or your college placement cell before installing anything " +
                        "or sharing personal documents.",
                category = AdvisoryCategory.APK_SAFETY,
                personaTags = listOf("student"),
                language = "en",
                sourceLabel = "National Scholarship Portal, Ministry of Education",
                sourceUrl = "https://cybercrime.gov.in/Webform/CrimeCatDes.aspx",
            ),
            Advisory(
                id = "adv_fake_campus_wifi",
                title = "Fake campus Wi-Fi and login portals",
                body =
                    "Open Wi-Fi networks named to look like your campus network (\"Campus-Free-WiFi\", " +
                        "\"University_Guest\") are sometimes set up by an attacker on the same premises to intercept " +
                        "traffic or present a fake login page that captures your college credentials. Only connect " +
                        "to Wi-Fi networks your institution has officially published, and be cautious of any Wi-Fi " +
                        "login page that asks for your full email password rather than a simple guest code.",
                category = AdvisoryCategory.DEVICE_SAFETY,
                personaTags = listOf("student"),
                language = "en",
                sourceLabel = "CyberDost (I4C) — Ministry of Home Affairs",
                sourceUrl = "https://cytrain.ncrb.gov.in/",
            ),
            Advisory(
                id = "adv_pension_kyc_phishing",
                title = "Pension and KYC-update phishing targeting senior citizens",
                body =
                    "Messages claiming your pension is \"suspended\" or your bank KYC has \"expired,\" urging you " +
                        "to click a link to fix it immediately, are almost always phishing. Pension life-certificate " +
                        "submission is done only through the official Jeevan Pramaan app or in person at your bank " +
                        "branch — never through a link in an SMS. If you receive such a message, do not click it; " +
                        "instead, call your bank branch directly using the number on your passbook to confirm.",
                category = AdvisoryCategory.PHISHING,
                personaTags = listOf("senior_citizen"),
                language = "en",
                sourceLabel = "Jeevan Pramaan, Ministry of Electronics & IT",
                sourceUrl = "https://cybercrime.gov.in/Webform/CrimeCatDes.aspx",
            ),
            Advisory(
                id = "adv_digital_arrest_scam",
                title = "\"Digital arrest\" and fake police video call scams",
                body =
                    "In this increasingly common scam, callers impersonate police, customs, or CBI officials on a " +
                        "video call, claiming you're under investigation and must stay \"digitally under arrest\" " +
                        "and transfer money to \"prove your innocence\" or avoid arrest. No Indian law enforcement " +
                        "agency conducts arrests or investigations over a phone or video call, and none will ever " +
                        "ask you to transfer money to a personal account. If you receive such a call, hang up " +
                        "immediately and report it at cybercrime.gov.in or by calling 1930.",
                category = AdvisoryCategory.SOCIAL_ENGINEERING,
                personaTags = listOf("senior_citizen"),
                language = "en",
                sourceLabel = "cybercrime.gov.in / 1930 National Cybercrime Helpline",
                sourceUrl = "https://cybercrime.gov.in/Webform/CrimeCatDes.aspx",
            ),
            Advisory(
                id = "adv_courier_customs_fee_scam",
                title = "Courier and customs \"release fee\" scams",
                body =
                    "A message claims your parcel is being held at customs or a courier hub and asks for a small " +
                        "\"release fee\" or \"customs duty\" through a personal payment link. Legitimate couriers " +
                        "never collect customs fees this way — they collect duty at the door on delivery, or through " +
                        "their own official app or website, never through a link texted to you. The small amount is " +
                        "deliberate: it's designed to feel too minor to question while it actually captures your " +
                        "card details for further misuse.",
                category = AdvisoryCategory.PHISHING,
                personaTags = listOf("homemaker"),
                language = "en",
                sourceLabel = "PandaSecurity — Spam Text Message Examples",
                sourceUrl = "https://www.pandasecurity.com/en/mediacenter/spam-text-message-examples/",
            ),
            Advisory(
                id = "adv_task_investment_scam",
                title = "Task-based and \"guaranteed return\" investment scams",
                body =
                    "Groups on WhatsApp or Telegram promising \"guaranteed\" returns of 2-3x within days, or paying " +
                        "you small amounts for simple \"tasks\" like liking videos before asking for a larger " +
                        "\"investment,\" are running a trust-building scam. Real investments never guarantee returns, " +
                        "and legitimate platforms are registered with SEBI. Once a larger sum is invested, the " +
                        "group typically disappears entirely. Be especially wary of any \"opportunity\" that starts " +
                        "by paying you first — it exists purely to make the eventual larger ask feel safe.",
                category = AdvisoryCategory.SOCIAL_ENGINEERING,
                personaTags = listOf("salaried_professional"),
                language = "en",
                sourceLabel = "cybercrime.gov.in / 1930 National Cybercrime Helpline",
                sourceUrl = "https://cybercrime.gov.in/Webform/CrimeCatDes.aspx",
            ),
            Advisory(
                id = "adv_fake_yono_apk",
                title = "Fake YONO Bank and banking app APKs",
                body =
                    "Fraudsters distribute APK files disguised as YONO Bank or other banking apps through WhatsApp " +
                        "forwards, SMS links, or fake app stores, often promising faster loans or special rewards. " +
                        "These fake apps can capture your login credentials, OTPs, and even record your screen. " +
                        "Only ever install YONO Bank from the Google Play Store or Apple App Store, or the official " +
                        "bankname.co.in website — never from a link shared in a message, however convincing the sender " +
                        "appears.",
                category = AdvisoryCategory.APK_SAFETY,
                personaTags = listOf("salaried_professional", GENERAL_PERSONA),
                language = "en",
                sourceLabel = "The Bank — Official Advisory",
                sourceUrl = "https://cybercrime.gov.in/Webform/CrimeCatDes.aspx",
            ),
            Advisory(
                id = "adv_dlt_sender_id_spoofing",
                title = "How to spot a spoofed SMS sender ID",
                body =
                    "TRAI requires all commercial SMS senders to register under the Distributed Ledger Technology " +
                        "(DLT) system, giving genuine transactional messages a consistent short header (like " +
                        "\"BANKINB\" or \"AD-BANKYON\") rather than a random 10-digit mobile number. A message claiming " +
                        "to be from your bank but arriving from what looks like an ordinary phone number, or with " +
                        "an unfamiliar/misspelled header, is a strong sign of spoofing. Genuine banks also never ask " +
                        "you to reply to an SMS with personal or banking details.",
                category = AdvisoryCategory.PHISHING,
                personaTags = listOf(GENERAL_PERSONA),
                language = "en",
                sourceLabel = "TRAI — Advice to SMS/Voice Senders",
                sourceUrl = "https://trai.gov.in/advice-to-senders",
            ),
            Advisory(
                id = "adv_ncrb_cytrain_portal",
                title = "Free cybercrime awareness training from NCRB",
                body =
                    "The National Crime Records Bureau runs CyTrain, a free online training portal covering " +
                        "cybercrime awareness, investigation basics, and digital hygiene for citizens and law " +
                        "enforcement alike. If you want to go deeper than this app's games and advisories — for " +
                        "yourself, your family, or your workplace — CyTrain's modules are a good next step and are " +
                        "entirely free to access.",
                category = AdvisoryCategory.POST_INCIDENT,
                personaTags = listOf(GENERAL_PERSONA),
                language = "en",
                sourceLabel = "CyTrain — National Crime Records Bureau (NCRB)",
                sourceUrl = "https://cytrain.ncrb.gov.in/",
            ),
            Advisory(
                id = "adv_device_hardening_basics",
                title = "Basic settings that harden any phone",
                body =
                    "A handful of settings meaningfully cut your risk on any smartphone: a screen lock plus " +
                        "biometric unlock, installing apps only from the Play Store, keeping Google Play Protect and " +
                        "automatic security updates turned on, and disabling \"install unknown apps\" for every app " +
                        "unless you specifically need it for a moment. Add a SIM PIN so a lost phone's SIM can't be " +
                        "reused to intercept your OTPs, and turn on \"Find My Device\" so a lost or stolen phone can " +
                        "be locked or wiped remotely. None of these take more than a few minutes to set up once.",
                category = AdvisoryCategory.DEVICE_SAFETY,
                personaTags = listOf(GENERAL_PERSONA),
                language = "en",
                sourceLabel = "CyberDost (I4C) — Ministry of Home Affairs",
                sourceUrl = "https://cybercrime.gov.in/Webform/CrimeCatDes.aspx",
            ),
        )
}
