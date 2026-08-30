package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.feature.awareness.domain.model.TrafficChannel
import com.sbi.surakshasathi.feature.awareness.domain.model.TrafficItem
import com.sbi.surakshasathi.feature.awareness.domain.usecase.GENERAL_PERSONA

/**
 * Bundled decision-queue pool for Fraud Traffic Control. Sample text patterns informed by
 * Truecaller's scam-text blog, PandaSecurity's spam-text-examples reference, TRAI's
 * advice-to-senders guidance on DLT-registered headers, and I4C's WhatsApp ".zip" APK scam
 * advisory.
 */
object BundledTrafficItems {
    val ENGLISH: List<TrafficItem> =
        listOf(
            TrafficItem(
                id = "otp_genuine",
                channel = TrafficChannel.SMS,
                summary = "BANK: OTP for txn of Rs.2,450 at Amazon is 4821. Valid 10 min. Do not share with anyone. -BANK",
                isGenuine = true,
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "A genuine bank OTP SMS comes from a DLT-registered header, states the exact transaction, and reminds you never to share it — it's informing you, not asking you to act.",
            ),
            TrafficItem(
                id = "kyc_block_scam",
                channel = TrafficChannel.SMS,
                summary = "Dear customer your A/C will be BLOCKED today. Update KYC immediately: http://bank-kyc-verify.tk",
                isGenuine = false,
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "Urgency (\"blocked today\"), a non-bank domain (.tk), and a demand to click a link are the three classic signs of a KYC-update phishing SMS.",
            ),
            TrafficItem(
                id = "gst_refund_scam",
                channel = TrafficChannel.SMS,
                summary = "GST Portal: Your GST refund of Rs.18,500 is pending. Claim now: http://gst-refund-claim.xyz",
                isGenuine = false,
                personaTags = listOf("business_owner"),
                explanation = "The real GST Network never messages a claim link — refunds are only ever tracked by logging into gst.gov.in directly, never via an SMS link.",
            ),
            TrafficItem(
                id = "gst_lookalike_domain",
                channel = TrafficChannel.WEBSITE,
                summary = "A link labeled \"gst.gov.in-refund-status.com\" asks you to log in with your GSTIN and password",
                isGenuine = false,
                personaTags = listOf("business_owner"),
                explanation = "\"gst.gov.in-refund-status.com\" is a different domain entirely (a .com site pretending to be gst.gov.in) — always check what comes right before the first single slash.",
            ),
            TrafficItem(
                id = "otp_reversal_call_scam",
                channel = TrafficChannel.CALL,
                summary = "Caller claims to be from the Bank and asks you to share your OTP to \"reverse a wrongly credited amount\"",
                isGenuine = false,
                personaTags = listOf("senior_citizen"),
                explanation = "No bank employee will ever call and ask for your OTP for any reason, including \"reversing\" a transaction — this is the single most common scam call script.",
            ),
            TrafficItem(
                id = "pension_kyc_scam",
                channel = TrafficChannel.SMS,
                summary = "Your pension has been suspended due to KYC mismatch. Update here: bit.ly/pension-update",
                isGenuine = false,
                personaTags = listOf("senior_citizen"),
                explanation = "Pension KYC is only ever done at your bank branch or the official Jeevan Pramaan app — never through a shortened link in an SMS.",
            ),
            TrafficItem(
                id = "courier_customs_scam",
                channel = TrafficChannel.WEBSITE,
                summary = "\"Your courier delivery failed. Pay Rs.50 customs fee to reschedule\": pay-customs-fee.net",
                isGenuine = false,
                personaTags = listOf("homemaker"),
                explanation = "Legitimate couriers never charge a random small \"customs fee\" through a personal link — this pattern exists purely to capture your card details for a tiny, easy-to-miss charge.",
            ),
            TrafficItem(
                id = "fake_shop_qr_scam",
                channel = TrafficChannel.QR,
                summary = "A shop's \"discount\" QR code shows the payment going to \"Rahul Kumar\" instead of the shop's name",
                isGenuine = false,
                personaTags = listOf("homemaker", "business_owner"),
                explanation = "Always check the payee name shown before confirming a UPI QR payment — a mismatched personal name instead of the business name means the QR sticker has likely been swapped.",
            ),
            TrafficItem(
                id = "boss_zip_apk_scam",
                channel = TrafficChannel.APK,
                summary = "Your \"Boss\" sends a WhatsApp file titled \"Salary_Statement_25-26.zip\" asking you to install it to view",
                isGenuine = false,
                personaTags = listOf("salaried_professional", "business_owner"),
                explanation = "This is the I4C-flagged \"Boss scam\": a .zip disguised as a document is really an APK that hacks WhatsApp once opened, then messages your contacts using your boss's photo and name.",
            ),
            TrafficItem(
                id = "investment_group_scam",
                channel = TrafficChannel.SMS,
                summary = "Guaranteed returns! Join our stock tips group, invest Rs.5,000 today for 3x returns in a week",
                isGenuine = false,
                personaTags = listOf("salaried_professional"),
                explanation = "\"Guaranteed\" high returns in days is impossible in real markets — this is a task-based investment scam designed to build trust with small early payouts before a large final loss.",
            ),
            TrafficItem(
                id = "placement_cell_call_genuine",
                channel = TrafficChannel.CALL,
                summary = "Your college's placement cell calls to confirm your interview time slot for a campus drive",
                isGenuine = true,
                personaTags = listOf("student"),
                explanation = "A known institution calling about a process you already applied for, with no request for money or OTP, is a normal genuine call.",
            ),
            TrafficItem(
                id = "play_store_update_genuine",
                channel = TrafficChannel.APK,
                summary = "The Play Store app itself shows an available update for YONO Bank",
                isGenuine = true,
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "Updates that appear inside the Play Store app are safe — the danger is only ever an install prompt that arrives from outside it (SMS, WhatsApp, email, a website).",
            ),
        )
}
