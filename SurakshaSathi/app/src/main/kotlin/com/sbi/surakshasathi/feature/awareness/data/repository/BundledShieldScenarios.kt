package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.feature.awareness.domain.model.AttackScenario
import com.sbi.surakshasathi.feature.awareness.domain.model.ShieldOption
import com.sbi.surakshasathi.feature.awareness.domain.usecase.GENERAL_PERSONA

/** Bundled attack/shield scenario pool for Cyber Shield Defender, tagged by persona. */
object BundledShieldScenarios {
    val ENGLISH: List<AttackScenario> =
        listOf(
            AttackScenario(
                id = "fake_apk_link",
                attackLabel = "Fake APK Link",
                attackDescription = "A WhatsApp message urges you to install YONO Bank from a shared link",
                shields =
                    listOf(
                        ShieldOption("s1_playstore", "Install from Play Store only"),
                        ShieldOption("s1_tap", "Tap and install quickly"),
                        ShieldOption("s1_forward", "Forward to a friend to check"),
                    ),
                correctShieldId = "s1_playstore",
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "Only ever install banking apps from the Play Store or the bank's official website — a shared link can carry a modified, malicious APK.",
            ),
            AttackScenario(
                id = "otp_request_call",
                attackLabel = "OTP Request Call",
                attackDescription = "A caller claims to be from the Bank and asks for your OTP to \"reverse a wrong transaction\"",
                shields =
                    listOf(
                        ShieldOption("s2_never_share", "Never share your OTP"),
                        ShieldOption("s2_partial", "Share only the first 3 digits"),
                        ShieldOption("s2_later", "Ask them to call back later"),
                    ),
                correctShieldId = "s2_never_share",
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "Bank staff never need your OTP for any reason, including a \"reversal\" — the OTP only ever authorizes a transaction you initiate yourself.",
            ),
            AttackScenario(
                id = "suspicious_qr",
                attackLabel = "Suspicious QR Code",
                attackDescription = "A flyer QR code promises cashback the moment you scan and pay",
                shields =
                    listOf(
                        ShieldOption("s3_verify", "Verify the payee name before paying"),
                        ShieldOption("s3_scan", "Scan and pay immediately"),
                        ShieldOption("s3_share", "Share the QR code with friends"),
                    ),
                correctShieldId = "s3_verify",
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "Scanning a QR code to receive money never requires entering your UPI PIN — if it's asking you to pay to \"receive\" cashback, the payee name will reveal the scam.",
            ),
            AttackScenario(
                id = "free_internship_apk",
                attackLabel = "Free Internship APK",
                attackDescription = "An unknown page offers a guaranteed internship if you install their app",
                shields =
                    listOf(
                        ShieldOption("s4_verify_company", "Verify the company independently"),
                        ShieldOption("s4_install", "Install it to check it out"),
                        ShieldOption("s4_pay", "Pay the small registration fee"),
                    ),
                correctShieldId = "s4_verify_company",
                personaTags = listOf("student"),
                explanation = "Real internships never guarantee a spot before you apply, and never require installing an unknown app — search for the company independently first.",
            ),
            AttackScenario(
                id = "gst_refund_sms",
                attackLabel = "GST Refund SMS",
                attackDescription = "An SMS says your GST refund is pending and provides a claim link",
                shields =
                    listOf(
                        ShieldOption("s5_portal", "Check the official GST portal directly"),
                        ShieldOption("s5_click", "Click the link to claim"),
                        ShieldOption("s5_share_gstin", "Share your GSTIN over SMS"),
                    ),
                correctShieldId = "s5_portal",
                personaTags = listOf("business_owner"),
                explanation = "GST refund status is only ever visible by logging into gst.gov.in yourself — the portal never messages you a claim link.",
            ),
            AttackScenario(
                id = "pension_update_link",
                attackLabel = "Pension Update Link",
                attackDescription = "An SMS says your pension is suspended and asks you to update KYC via a link",
                shields =
                    listOf(
                        ShieldOption("s6_call_bank", "Call your bank branch directly"),
                        ShieldOption("s6_click", "Click the link immediately"),
                        ShieldOption("s6_share_acct", "Share your account number by SMS"),
                    ),
                correctShieldId = "s6_call_bank",
                personaTags = listOf("senior_citizen"),
                explanation = "Pension KYC is done at your bank branch or the Jeevan Pramaan app — a genuine bank never resolves \"suspended\" pension status through an SMS link.",
            ),
            AttackScenario(
                id = "courier_customs_fee",
                attackLabel = "Courier Customs Fee",
                attackDescription = "A message says your parcel is held at customs and asks for a small release fee",
                shields =
                    listOf(
                        ShieldOption("s7_official_app", "Contact the courier through their official app"),
                        ShieldOption("s7_pay_link", "Pay the fee via the link"),
                        ShieldOption("s7_card", "Share your card details to release it"),
                    ),
                correctShieldId = "s7_official_app",
                personaTags = listOf("homemaker"),
                explanation = "A real courier never collects customs fees through a personal payment link — check tracking only through the courier's own official app or website.",
            ),
            AttackScenario(
                id = "boss_whatsapp_zip",
                attackLabel = "Boss WhatsApp Zip File",
                attackDescription = "Your \"manager\" sends a .zip file on WhatsApp asking you to install it to view a salary statement",
                shields =
                    listOf(
                        ShieldOption("s8_confirm_call", "Call your manager to confirm first"),
                        ShieldOption("s8_install", "Install it since it's from your boss"),
                        ShieldOption("s8_forward", "Forward it to your finance team"),
                    ),
                correctShieldId = "s8_confirm_call",
                personaTags = listOf("salaried_professional", "business_owner"),
                explanation = "This is the I4C-flagged \"Boss scam\" — the .zip is really an APK that hijacks WhatsApp. Always confirm unusual file requests with a direct call, never by installing first.",
            ),
            AttackScenario(
                id = "reward_scam_sms",
                attackLabel = "Reward Scam SMS",
                attackDescription = "\"You've won a lucky draw prize — just pay a small fee to claim it\"",
                shields =
                    listOf(
                        ShieldOption("s9_ignore", "Ignore and delete the message"),
                        ShieldOption("s9_pay", "Pay the fee to claim your prize"),
                        ShieldOption("s9_reply", "Reply with your bank details"),
                    ),
                correctShieldId = "s9_ignore",
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "You cannot win a lottery or draw you never entered — any prize that asks you to pay first is the scam itself, not a fee to unlock a real one.",
            ),
            AttackScenario(
                id = "fake_supplier_payment",
                attackLabel = "Fake Supplier Payment Request",
                attackDescription = "An email from a \"known supplier\" asks you to change their bank account for future payments",
                shields =
                    listOf(
                        ShieldOption("s10_call_known", "Call the supplier on a known number to confirm"),
                        ShieldOption("s10_update", "Update the account as requested"),
                        ShieldOption("s10_reply_email", "Reply to confirm over email"),
                    ),
                correctShieldId = "s10_call_known",
                personaTags = listOf("business_owner"),
                explanation = "Business email compromise scams intercept real supplier threads and quietly change bank details — always verify by phone using a number you already had, not one from the email.",
            ),
        )
}
