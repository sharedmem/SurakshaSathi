package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.feature.awareness.domain.model.ScamBubble
import com.sbi.surakshasathi.feature.awareness.domain.usecase.GENERAL_PERSONA

/** Bundled short-message bubble pool for Bubble Pop Scam — a fast, tap-only recognition drill. */
object BundledBubbleMessages {
    val ENGLISH: List<ScamBubble> =
        listOf(
            ScamBubble(
                id = "b_otp_share_request",
                text = "Share your OTP to claim cashback",
                isScam = true,
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "No genuine offer or refund ever needs your OTP — OTPs only authorize a transaction you are already making yourself.",
            ),
            ScamBubble(
                id = "b_delivery_status",
                text = "Your Amazon order is out for delivery",
                isScam = false,
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "A plain delivery-status notification with no link or payment request is normal.",
            ),
            ScamBubble(
                id = "b_account_blocked",
                text = "A/C blocked! Click to reactivate now: bit.ly/xyz123",
                isScam = true,
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "Urgency plus a shortened, unofficial link is a classic account-block phishing pattern.",
            ),
            ScamBubble(
                id = "b_otp_bank_note",
                text = "OTP is 7391 for your Bank login. Never share this with anyone.",
                isScam = false,
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "A real OTP message states the code and explicitly warns you not to share it — it isn't asking you for anything.",
            ),
            ScamBubble(
                id = "b_gst_refund_bubble",
                text = "GST refund of Rs.18,500 pending — claim before it expires",
                isScam = true,
                personaTags = listOf("business_owner"),
                explanation = "GST refunds are only tracked by logging into the official portal yourself, never through an SMS link with an expiry threat.",
            ),
            ScamBubble(
                id = "b_gst_filing_reminder",
                text = "GSTR-3B filing due date is the 20th of this month",
                isScam = false,
                personaTags = listOf("business_owner"),
                explanation = "A plain due-date reminder with no link or payment demand is normal compliance information.",
            ),
            ScamBubble(
                id = "b_scholarship_fee_bubble",
                text = "Scholarship approved! Pay Rs.499 processing fee to release funds",
                isScam = true,
                personaTags = listOf("student"),
                explanation = "Genuine scholarships never ask you to pay a fee to receive money — that direction of payment is always the scam's tell.",
            ),
            ScamBubble(
                id = "b_exam_admit_card",
                text = "Your admit card is now available on the university portal",
                isScam = false,
                personaTags = listOf("student"),
                explanation = "A routine notice pointing to your own known university portal, with no payment or OTP request, is normal.",
            ),
            ScamBubble(
                id = "b_pension_suspend_bubble",
                text = "Pension suspended — update KYC urgently at this link",
                isScam = true,
                personaTags = listOf("senior_citizen"),
                explanation = "Pension KYC is only done at your bank branch or the official Jeevan Pramaan app, never through an urgent SMS link.",
            ),
            ScamBubble(
                id = "b_pension_credited",
                text = "Your monthly pension of Rs.9,000 has been credited",
                isScam = false,
                personaTags = listOf("senior_citizen"),
                explanation = "A plain credit notification stating an amount, with no link or request, is a normal bank message.",
            ),
            ScamBubble(
                id = "b_courier_fee_bubble",
                text = "Parcel held at customs. Pay Rs.50 to release: pay-now.link",
                isScam = true,
                personaTags = listOf("homemaker"),
                explanation = "A tiny \"release fee\" through an unofficial link exists to capture your card details, not to actually release any parcel.",
            ),
            ScamBubble(
                id = "b_delivery_otp_note",
                text = "Share the OTP with the delivery agent only after you receive your parcel",
                isScam = false,
                personaTags = listOf("homemaker"),
                explanation = "Sharing a delivery OTP directly with the agent at your door, after receiving the parcel, is the normal and safe way couriers confirm delivery.",
            ),
        )
}
