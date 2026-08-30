"""
Synthetic Data Generator for SMS Sideloading Spam Detection.

Generates high-quality multilingual synthetic training examples covering
all known sideloading vectors and smishing patterns. Includes both
spam (sideloading) and ham (legitimate) examples in 12+ Indian languages
plus code-mixed Hinglish.

Design: Generate diverse, realistic examples that cover every attack vector
identified in the reference framework. Templates use variable substitution
to create natural variation.
"""

import os
import csv
import json
import random
import string
from typing import List, Dict, Tuple, Optional
from dataclasses import dataclass


# ────────────────────────────────────────────────────────────────────
# Template Variables
# ────────────────────────────────────────────────────────────────────

BANKS = [
    "SBI", "HDFC", "ICICI", "Axis", "Kotak", "PNB", "BOB", "Canara",
    "Union Bank", "IndusInd", "Yes Bank", "Federal Bank", "IDBI",
]

UPI_APPS = ["Google Pay", "PhonePe", "Paytm", "BHIM", "Amazon Pay", "Cred"]
TELECOM = ["Jio", "Airtel", "Vi", "BSNL"]
ECOMMERCE = ["Flipkart", "Amazon", "Myntra", "Meesho", "Swiggy", "Zomato"]
GOVT_SERVICES = ["UIDAI", "Income Tax Dept", "EPFO", "IRCTC", "mParivahan", "DigiLocker"]

FAKE_URLS = [
    "bit.ly/3xK2mN", "tinyurl.com/yvb89kw", "shorturl.at/acEG7",
    "cutt.ly/2wR4kLm", "rb.gy/x7n3p", "is.gd/k8mPq2",
]

SUSPICIOUS_URLS = [
    "sbi-update-kyc.top", "hdfc-secure-login.icu", "icici-verify.xyz",
    "paytm-cashback-claim.cc", "gpay-reward.buzz", "aadhaar-update.site",
    "epfo-claim-status.online", "irctc-booking.club", "pan-verification.live",
    "e-challan-pay.icu", "mparivahan-dl.xyz", "jio-recharge-offer.top",
    "phonepe-cashback.store", "axis-netbanking.fun",
]

LEGITIMATE_URLS = [
    "sbi.co.in", "hdfcbank.com", "icicibank.com", "axisbank.com",
    "paytm.com", "phonepe.com", "play.google.com", "irctc.co.in",
]

# Official brand-owned short-domain redirectors (NOT generic shorteners like
# bit.ly/cutt.ly — these belong to and only ever point back to the brand's
# own delivery-tracking flow). Used only in ham delivery-notification
# templates so the model learns these specific domains are not risk signals,
# without weakening the generic-shortener-is-suspicious heuristic.
LEGITIMATE_BRAND_SHORT_URLS = [
    "fkrt.it/t8xB", "fkrt.it/q2mN9", "amzn.to/3xPq7Lk", "myntr.it/s/8k2p",
]

NAMES = [
    "Rahul", "Priya", "Amit", "Neha", "Suresh", "Anita", "Vijay",
    "Deepa", "Rajesh", "Sunita", "Arjun", "Pooja", "Kiran", "Meena",
]

AMOUNTS = ["₹500", "₹1,000", "₹2,500", "₹5,000", "₹10,000", "₹50,000", "₹1,00,000"]
OTP_CODES = ["4829", "7156", "3094", "8621", "5473", "9208"]


# ────────────────────────────────────────────────────────────────────
# Spam Templates (Sideloading Attack Vectors)
# ────────────────────────────────────────────────────────────────────

SPAM_TEMPLATES = {
    # ── Vector 1: Direct APK Download ──
    "direct_apk": {
        "en": [
            "URGENT: Your {bank} mobile banking app needs critical security update. Download now: https://{suspicious_url}/app.apk",
            "⚠️ {bank} Alert: Install the new security app to protect your account. Download: {fake_url}/secure.apk",
            "New {bank} app is mandatory for all customers. Install immediately: https://{suspicious_url}/banking.apk or your account will be blocked.",
            "Dear Customer, download the updated {upi_app} app for better security: {fake_url}/update.apk",
            "Your {telecom} SIM will be blocked. Download KYC verification app: https://{suspicious_url}/kyc.apk",
        ],
        "hi": [
            "⚠️ {bank} सूचना: आपके खाते की सुरक्षा के लिए नया ऐप डाउनलोड करें: https://{suspicious_url}/app.apk",
            "प्रिय ग्राहक, {bank} का नया मोबाइल बैंकिंग ऐप डाउनलोड करें: {fake_url}/banking.apk अन्यथा आपका खाता बंद हो जाएगा",
            "तुरंत {bank} सुरक्षा ऐप इंस्टॉल करें। आपका OTP चोरी हो सकता है। डाउनलोड: https://{suspicious_url}/secure.apk",
            "{telecom} KYC अपडेट जरूरी है। ऐप डाउनलोड करें: {fake_url}/kyc.apk वरना SIM बंद हो जाएगी",
            "आपके {bank} खाते में संदिग्ध गतिविधि पाई गई। सुरक्षा ऐप डाउनलोड करें: {fake_url}/alert.apk",
        ],
        "bn": [
            "⚠️ {bank} থেকে জরুরি বার্তা: নতুন নিরাপত্তা অ্যাপ ডাউনলোড করুন: https://{suspicious_url}/app.apk",
            "প্রিয় গ্রাহক, {bank} অ্যাপটি আপডেট করুন নতুবা আপনার অ্যাকাউন্ট বন্ধ হয়ে যাবে। ডাউনলোড: {fake_url}/update.apk",
            "{bank} KYC আপডেট বাধ্যতামূলক। এখনই ডাউনলোড করুন: {fake_url}/kyc.apk",
        ],
        "ta": [
            "⚠️ {bank} எச்சரிக்கை: புதிய பாதுகாப்பு ஆப்பைப் பதிவிறக்கம் செய்யவும்: https://{suspicious_url}/app.apk",
            "உங்கள் {bank} கணக்கு ஆபத்தில் உள்ளது. பாதுகாப்பு ஆப் நிறுவுங்கள்: {fake_url}/secure.apk",
        ],
        "te": [
            "⚠️ {bank} హెచ్చరిక: కొత్త భద్రత యాప్ డౌన్‌లోడ్ చేయండి: https://{suspicious_url}/app.apk",
            "మీ {bank} ఖాతా నిలిపివేయబడుతుంది. యాప్ ఇన్‌స్టాల్ చేయండి: {fake_url}/banking.apk",
        ],
        "hi_latn": [
            "Bhai tera {bank} account suspend ho jayega, jaldi ye app install kar: {fake_url}/app.apk",
            "{bank} ka naya security app download karo turant: https://{suspicious_url}/secure.apk warna account block",
            "Urgent! {bank} mobile banking app update karo: {fake_url}/update.apk KYC mandatory hai",
        ],
    },

    # ── Vector 2: Shortened URL with Urgency ──
    "shortened_url_urgency": {
        "en": [
            "ALERT: Your {bank} account has been compromised! Verify immediately: {fake_url}",
            "Your {bank} KYC expires today. Update now or account will be blocked: {fake_url}",
            "⚠️ IMPORTANT: {amount} debited from your {bank} account. If not you, report: {fake_url}",
            "Your {upi_app} account is under review. Complete verification: {fake_url} within 24 hours.",
            "NOTICE: Your {telecom} SIM will be deactivated in 24hrs due to pending KYC. Verify: {fake_url}",
            "Unauthorized login detected on your {bank} account. Secure it now: {fake_url}",
        ],
        "hi": [
            "⚠️ आपके {bank} खाते से {amount} डेबिट हुए हैं। अगर आपने नहीं किया तो तुरंत रिपोर्ट करें: {fake_url}",
            "चेतावनी: आपका {bank} KYC आज expire हो रहा है। अभी अपडेट करें: {fake_url} वरना खाता बंद",
            "{bank} अलर्ट: आपके खाते में संदिग्ध लेनदेन। तुरंत वेरीफाई करें: {fake_url}",
            "आपका {telecom} सिम 24 घंटे में बंद हो जाएगा। KYC पूरा करें: {fake_url}",
        ],
        "bn": [
            "সতর্কতা: আপনার {bank} অ্যাকাউন্ট থেকে {amount} ডেবিট হয়েছে। রিপোর্ট করুন: {fake_url}",
            "{bank} KYC মেয়াদ শেষ হচ্ছে। এখনই আপডেট করুন: {fake_url}",
        ],
        "ta": [
            "எச்சரிக்கை: உங்கள் {bank} கணக்கிலிருந்து {amount} டெபிட் ஆகியுள்ளது. புகாரளிக்கவும்: {fake_url}",
        ],
        "te": [
            "హెచ్చరిక: మీ {bank} ఖాతా నుండి {amount} డెబిట్ అయింది. నివేదించండి: {fake_url}",
        ],
        "hi_latn": [
            "Alert! Tera {bank} account se {amount} debit hua hai. Agar tune nahi kiya to report kar: {fake_url}",
            "{bank} KYC aaj expire ho raha hai. Jaldi update kar: {fake_url} warna account band",
        ],
    },

    # ── Vector 3: QR Code Sideloading ──
    "qr_code": {
        "en": [
            "Scan this QR code to download the new {bank} security app: {fake_url}",
            "Complete your {bank} KYC by scanning this QR code: {fake_url} - mandatory for all customers",
            "Scan QR to receive your {upi_app} cashback of {amount}: {fake_url}",
            "To verify your {telecom} SIM, scan this QR code and install the verification app: {fake_url}",
        ],
        "hi": [
            "इस QR कोड को स्कैन करके {bank} का नया सुरक्षा ऐप डाउनलोड करें: {fake_url}",
            "{bank} KYC पूरा करने के लिए यह QR कोड स्कैन करें: {fake_url}",
            "QR कोड स्कैन करें और {upi_app} कैशबैक {amount} प्राप्त करें: {fake_url}",
        ],
        "bn": [
            "এই QR কোড স্ক্যান করে {bank} নিরাপত্তা অ্যাপ ডাউনলোড করুন: {fake_url}",
        ],
        "ta": [
            "இந்த QR குறியீட்டை ஸ்கேன் செய்து {bank} பாதுகாப்பு ஆப் பதிவிறக்கம் செய்யவும்: {fake_url}",
        ],
    },

    # ── Vector 4: Wedding Invitation APK (SpyMax campaign) ──
    "wedding_invitation": {
        "en": [
            "You're invited! 💒 Open the wedding invitation card: {fake_url}/invitation.apk",
            "Wedding Invitation: {name} & {name2}'s wedding on 15th March. View card: {fake_url}/card.apk",
            "Hi! Please join us for the wedding ceremony. Download invitation: {fake_url}/wedding.apk",
        ],
        "hi": [
            "शादी का निमंत्रण 💒 कृपया शादी कार्ड देखें: {fake_url}/invitation.apk",
            "{name} और {name2} की शादी में आप सादर आमंत्रित हैं। कार्ड डाउनलोड करें: {fake_url}/card.apk",
            "शादी का कार्ड खोलें: {fake_url}/wedding.apk आप जरूर आइएगा 🙏",
        ],
        "bn": [
            "বিবাহের নিমন্ত্রণ 💒 অনুগ্রহ করে কার্ড দেখুন: {fake_url}/invitation.apk",
            "{name} এবং {name2} এর বিয়ের অনুষ্ঠানে আপনাকে আমন্ত্রণ। কার্ড ডাউনলোড: {fake_url}/card.apk",
        ],
        "ta": [
            "திருமண அழைப்பு 💒 அழைப்பிதழ் பதிவிறக்கம்: {fake_url}/invitation.apk",
        ],
        "te": [
            "పెళ్ళి ఆహ్వానం 💒 కార్డ్ డౌన్‌లోడ్ చేయండి: {fake_url}/invitation.apk",
        ],
        "hi_latn": [
            "Shaadi ka card download karo: {fake_url}/invitation.apk Zaroor aana! 💒",
            "Bhai {name} ki shaadi hai. Invitation card dekh: {fake_url}/wedding.apk",
        ],
    },

    # ── Vector 5: Government/Transport Impersonation (GhostBat) ──
    "govt_impersonation": {
        "en": [
            "e-Challan Notice: You have an unpaid traffic fine of {amount}. Pay now to avoid license suspension: {fake_url}",
            "UIDAI Alert: Your Aadhaar card needs immediate update. Complete here: {fake_url}",
            "Income Tax Dept: Your PAN is linked to suspicious activity. Verify: {fake_url}",
            "EPFO Notice: Claim your PF amount of {amount}. Download form: {fake_url}/epfo-claim.apk",
            "mParivahan: Your driving license has expired. Renew online: {fake_url}",
        ],
        "hi": [
            "ई-चालान नोटिस: {amount} का अवैतनिक ट्रैफिक जुर्माना। अभी भुगतान करें: {fake_url}",
            "UIDAI सूचना: आपका आधार कार्ड तुरंत अपडेट करें: {fake_url}",
            "आयकर विभाग: आपके PAN में संदिग्ध गतिविधि। वेरीफाई करें: {fake_url}",
            "EPFO: आपका PF {amount} क्लेम करें। फॉर्म डाउनलोड: {fake_url}/epfo.apk",
            "mParivahan: ड्राइविंग लाइसेंस expired। ऑनलाइन रिन्यू करें: {fake_url}",
        ],
        "bn": [
            "ই-চালান নোটিস: {amount} ট্রাফিক জরিমানা বকেয়া। এখনই পে করুন: {fake_url}",
            "UIDAI: আপনার আধার কার্ড আপডেট করুন: {fake_url}",
        ],
        "ta": [
            "மின்-சாலான் அறிவிப்பு: {amount} போக்குவரத்து அபராதம். இப்போதே செலுத்துங்கள்: {fake_url}",
        ],
        "hi_latn": [
            "E-challan notice: {amount} ka traffic fine pending hai. Pay karo: {fake_url}",
            "Aadhaar update karna zaroori hai. Link: {fake_url}",
        ],
    },

    # ── Vector 6: Predatory Loan App (SpyLoan campaign) ──
    "loan_app": {
        "en": [
            "🎉 Instant Personal Loan up to {amount}! No documents needed. Download app: {fake_url}",
            "Get {amount} loan in just 5 minutes! Zero interest for first month. Apply: {fake_url}",
            "Pre-approved loan of {amount} for your {bank} account. Claim now: {fake_url}/loan-app.apk",
            "Low EMI loans available! Download our trusted app: {fake_url} - instant approval guaranteed",
        ],
        "hi": [
            "🎉 तुरंत पर्सनल लोन {amount} तक! कोई दस्तावेज नहीं। ऐप डाउनलोड करें: {fake_url}",
            "5 मिनट में {amount} लोन! पहले महीने जीरो ब्याज। अप्लाई करें: {fake_url}",
            "आपके {bank} खाते के लिए {amount} का प्री-अप्रूव्ड लोन। अभी क्लेम करें: {fake_url}/loan.apk",
        ],
        "bn": [
            "🎉 তাৎক্ষণিক ব্যক্তিগত ঋণ {amount} পর্যন্ত! কোনও নথি লাগবে না। অ্যাপ ডাউনলোড: {fake_url}",
        ],
        "hi_latn": [
            "Turant loan {amount} tak! Koi document nahi chahiye. App download karo: {fake_url}",
            "Sirf 5 minute mein {amount} loan approved! Download: {fake_url}/loan-app.apk",
        ],
    },

    # ── Vector 7: Fake Delivery Notification ──
    "delivery_notification": {
        "en": [
            "Your package delivery failed. Reschedule here: {fake_url}",
            "{ecommerce} Order Update: Your package is on hold due to address issue. Verify: {fake_url}",
            "Delivery attempt failed. Track your parcel and reschedule: {fake_url}",
            "Courier partner unable to deliver. Confirm your address: {fake_url} or order will be returned.",
        ],
        "hi": [
            "आपकी डिलीवरी विफल हो गई। पुनर्निर्धारित करें: {fake_url}",
            "{ecommerce} ऑर्डर अपडेट: पता समस्या के कारण पार्सल रुका। वेरीफाई करें: {fake_url}",
            "कूरियर पार्टनर डिलीवर नहीं कर सका। पता कन्फर्म करें: {fake_url}",
        ],
        "hi_latn": [
            "Delivery fail ho gayi. Reschedule karo: {fake_url}",
            "{ecommerce} order ruka hai address issue ki wajah se. Verify karo: {fake_url}",
        ],
    },

    # ── Vector 8: Financial Reward / Prize Scam ──
    "financial_reward": {
        "en": [
            "🎉 Congratulations! You've won {amount} in the {bank} Lucky Draw! Claim: {fake_url}",
            "Your {upi_app} cashback of {amount} is ready! Claim before it expires: {fake_url}",
            "You have been selected for a special {bank} reward of {amount}. Claim now: {fake_url}",
            "Tax refund of {amount} approved for your account. Process here: {fake_url}",
        ],
        "hi": [
            "🎉 बधाई हो! आपने {bank} लकी ड्रॉ में {amount} जीते! क्लेम करें: {fake_url}",
            "आपका {upi_app} कैशबैक {amount} तैयार है! एक्सपायर होने से पहले क्लेम करें: {fake_url}",
            "{bank} से विशेष इनाम {amount}। अभी क्लेम करें: {fake_url}",
        ],
        "bn": [
            "🎉 অভিনন্দন! আপনি {bank} লাকি ড্র-তে {amount} জিতেছেন! দাবি করুন: {fake_url}",
        ],
        "hi_latn": [
            "Congratulations! {bank} lucky draw mein {amount} jeete ho! Claim karo: {fake_url}",
            "Tera {upi_app} cashback {amount} ready hai. Jaldi claim kar: {fake_url}",
        ],
    },

    # ── Vector 9: Wrong Number / Affinity Scam ──
    "affinity_scam": {
        "en": [
            "Sorry, wrong number 😊 But you seem really nice. Want to be friends? Check my profile: {fake_url}",
            "Hi grandpa, I've been in a car accident and need bail money urgently. Please send {amount} via: {fake_url}",
            "This is your boss. I need you to buy gift cards worth {amount} immediately. Instructions: {fake_url}",
        ],
        "hi": [
            "माफ करना, गलत नंबर 😊 लेकिन आप अच्छे लग रहे हैं। दोस्ती करेंगे? प्रोफाइल देखें: {fake_url}",
            "दादाजी, मेरा एक्सीडेंट हो गया है। तुरंत {amount} भेजो: {fake_url}",
        ],
        "hi_latn": [
            "Sorry galat number 😊 But tum ache lag rahe ho. Profile dekho: {fake_url}",
            "Dada ji mera accident ho gaya hai. Jaldi {amount} bhejo: {fake_url}",
        ],
    },

    # ── Vector 10: Electricity/Utility Disconnect Scam ──
    "utility_scam": {
        "en": [
            "⚡ ALERT: Your electricity connection will be disconnected today due to pending bill of {amount}. Pay now: {fake_url}",
            "Gas connection disconnection notice. Outstanding: {amount}. Pay immediately: {fake_url}",
            "Your {telecom} postpaid bill of {amount} is overdue. Pay to avoid service interruption: {fake_url}",
        ],
        "hi": [
            "⚡ सूचना: बिजली कनेक्शन आज काटा जाएगा। बकाया: {amount}। अभी भुगतान करें: {fake_url}",
            "गैस कनेक्शन कटौती नोटिस। बकाया: {amount}। तुरंत भुगतान करें: {fake_url}",
            "आपका {telecom} बिल {amount} बकाया है। सेवा बंद होने से पहले भुगतान करें: {fake_url}",
        ],
        "bn": [
            "⚡ সতর্কতা: বিদ্যুৎ সংযোগ আজ বিচ্ছিন্ন হবে। বকেয়া: {amount}। এখনই পে করুন: {fake_url}",
        ],
        "hi_latn": [
            "Bijli connection aaj kat jayega! Bill {amount} pending hai. Pay karo: {fake_url}",
        ],
    },

    # ── Vector 11: Callback-only Vishing Fraud (NO phone number literal, NO
    #    APK, often NO url at all — a fake bank/transaction alert that pushes
    #    the victim to "call us back" / "our representative" so a human closes
    #    the scam over the phone). Covers all 13 config languages + code-mix. ──
    "callback_only_fraud": {
        "en": [
            "{amount} was debited from your {bank} account. If this was not done by you, secure your account now. Kindly respond immediately, otherwise your account will be blocked. We tried to reach you regarding your pending issue. Call us back on the number below and our representative will guide you through the verification process.",
            "Dear Customer, unusual activity detected on your {bank} account. Please act within 24 hours or your account will be suspended. Contact our helpline and our representative will assist with verification.",
            "Your {bank} debit card has been temporarily blocked due to a security review. Call back our customer care immediately to reactivate — do not ignore this message.",
        ],
        "hi": [
            "आपके {bank} खाते से {amount} डेबिट हुए। अगर यह आपने नहीं किया तो तुरंत अपना खाता सुरक्षित करें। कृपया 24 घंटे के भीतर कार्रवाई करें, नहीं तो खाता बंद हो जाएगा। हमारे प्रतिनिधि को कॉल करें और वेरिफिकेशन प्रक्रिया पूरी करें।",
            "प्रिय ग्राहक, आपके {bank} खाते में असामान्य गतिविधि पाई गई है। तुरंत हमारे हेल्पलाइन पर कॉल करें अन्यथा खाता ब्लॉक हो जाएगा।",
        ],
        "bn": [
            "আপনার {bank} অ্যাকাউন্ট থেকে {amount} ডেবিট হয়েছে। যদি এটি আপনি না করে থাকেন, এখনই আপনার অ্যাকাউন্ট সুরক্ষিত করুন। অনুগ্রহ করে আজই ব্যবস্থা নিন, নতুবা আপনার অ্যাকাউন্ট বন্ধ হয়ে যাবে। আমাদের প্রতিনিধিকে কল করুন যাচাইকরণের জন্য।",
        ],
        "pa": [
            "ਤੁਹਾਡੇ {bank} ਖਾਤੇ ਵਿੱਚੋਂ {amount} ਡੈਬਿਟ ਹੋਏ ਹਨ। ਜੇ ਇਹ ਤੁਸੀਂ ਨਹੀਂ ਕੀਤਾ, ਤਾਂ ਹੁਣੇ ਆਪਣਾ ਖਾਤਾ ਸੁਰੱਖਿਅਤ ਕਰੋ। ਕਿਰਪਾ ਕਰਕੇ ਅੱਜ ਹੀ ਕਾਰਵਾਈ ਕਰੋ, ਨਹੀਂ ਤਾਂ ਤੁਹਾਡਾ ਖਾਤਾ ਬਲਾਕ ਹੋ ਜਾਵੇਗਾ। ਸਾਡੇ ਨੁਮਾਇੰਦੇ ਨੂੰ ਕਾਲ ਕਰੋ ਅਤੇ ਤਸਦੀਕ ਪ੍ਰਕਿਰਿਆ ਪੂਰੀ ਕਰੋ।",
        ],
        "mr": [
            "तुमच्या {bank} खात्यातून {amount} डेबिट झाले आहेत. जर हे तुम्ही केले नसेल, तर लगेच तुमचे खाते सुरक्षित करा. कृपया २४ तासांच्या आत कारवाई करा, अन्यथा तुमचे खाते बंद होईल. आमच्या प्रतिनिधीला कॉल करा आणि पडताळणी प्रक्रिया पूर्ण करा.",
        ],
        "ur": [
            "آپ کے {bank} اکاؤنٹ سے {amount} ڈیبٹ ہوئے ہیں۔ اگر یہ آپ نے نہیں کیا تو ابھی اپنا اکاؤنٹ محفوظ بنائیں۔ براہ کرم فوری طور پر اقدام کریں، ورنہ آپ کا اکاؤنٹ بند ہو جائے گا۔ ہمارے نمائندے کو کال کریں اور تصدیقی عمل مکمل کریں۔",
        ],
        "gu": [
            "તમારા {bank} ખાતામાંથી {amount} ડેબિટ થયા છે. જો આ તમે નથી કર્યું, તો હમણાં જ તમારું ખાતું સુરક્ષિત કરો. કૃપા કરી 24 કલાકમાં પગલાં લો, નહીં તો ખાતું બ્લોક થઈ જશે. અમારા પ્રતિનિધિને કૉલ કરો.",
        ],
        "ta": [
            "உங்கள் {bank} கணக்கிலிருந்து {amount} டெபிட் ஆனது. இது நீங்கள் செய்யவில்லை என்றால், உடனே உங்கள் கணக்கை பாதுகாக்கவும். எங்கள் பிரதிநிதியை அழைத்து சரிபார்ப்பு செயல்முறையை முடிக்கவும், இல்லையெனில் கணக்கு முடக்கப்படும்.",
        ],
        "te": [
            "మీ {bank} ఖాతా నుండి {amount} డెబిట్ అయింది. ఇది మీరు చేయకపోతే, వెంటనే మీ ఖాతాను సురక్షితం చేసుకోండి. మా ప్రతినిధికి కాల్ చేసి ధృవీకరణ పూర్తి చేయండి, లేకపోతే ఖాతా బ్లాక్ అవుతుంది.",
        ],
        "kn": [
            "ನಿಮ್ಮ {bank} ಖಾತೆಯಿಂದ {amount} ಡೆಬಿಟ್ ಆಗಿದೆ. ಇದನ್ನು ನೀವು ಮಾಡದಿದ್ದರೆ, ತಕ್ಷಣ ನಿಮ್ಮ ಖಾತೆಯನ್ನು ಸುರಕ್ಷಿತಗೊಳಿಸಿ. ನಮ್ಮ ಪ್ರತಿನಿಧಿಗೆ ಕರೆ ಮಾಡಿ, ಇಲ್ಲದಿದ್ದರೆ ಖಾತೆ ನಿರ್ಬಂಧಿಸಲಾಗುತ್ತದೆ.",
        ],
        "ml": [
            "നിങ്ങളുടെ {bank} അക്കൗണ്ടിൽ നിന്ന് {amount} ഡെബിറ്റ് ചെയ്തു. ഇത് നിങ്ങൾ ചെയ്തതല്ലെങ്കിൽ, ഉടൻ അക്കൗണ്ട് സുരക്ഷിതമാക്കുക. ഞങ്ങളുടെ പ്രതിനിധിയെ വിളിക്കുക, അല്ലെങ്കിൽ അക്കൗണ്ട് ബ്ലോക്ക് ചെയ്യപ്പെടും.",
        ],
        "or": [
            "ଆପଣଙ୍କ {bank} ଖାତାରୁ {amount} ଡେବିଟ ହୋଇଛି। ଏହା ଆପଣ କରିନଥିଲେ, ତୁରନ୍ତ ଆପଣଙ୍କ ଖାତାକୁ ସୁରକ୍ଷିତ କରନ୍ତୁ। ଆମ ପ୍ରତିନିଧିଙ୍କୁ କଲ କରନ୍ତୁ, ନଚେତ ଖାତା ବ୍ଲକ ହେବ।",
        ],
        "hi_latn": [
            "Tere {bank} account se {amount} debit hua hai. Agar tune nahi kiya to abhi apna account secure kar. 24 ghante mein action lo warna account block ho jayega. Hamare representative ko call karo verification ke liye.",
        ],
        # Explicit code-mixed (two Indic scripts + English in the SAME message —
        # e.g. a Punjabi urgency line embedded in an otherwise-English bank alert).
        "mixed_script": [
            "{amount} was debited from your {bank} account. If this was not done by you, secure your account now.\nਕਿਰਪਾ ਕਰਕੇ ਅੱਜ ਹੀ ਕਾਰਵਾਈ ਕਰੋ, ਨਹੀਂ ਤਾਂ ਤੁਹਾਡਾ ਖਾਤਾ ਬਲਾਕ ਹੋ ਜਾਵੇਗਾ।\nWe tried to reach you regarding your pending issue. Call us back on the number below and our representative will guide you through the verification process.",
            "প্রিয় গ্রাহক,\nYour {bank} account will be suspended due to unusual login activity.\nঅনুগ্রহ করে অবিলম্বে ব্যবস্থা নিন, নতুবা আপনার অ্যাকাউন্ট বন্ধ হয়ে যাবে।\nCall our representative back to complete verification.",
            "ਪਿਆਰੇ ਗਾਹਕ,\nCongratulations! You are eligible for a pre-approved offer of {amount} from {bank}.\nਕਿਰਪਾ ਕਰਕੇ 24 ਘੰਟਿਆਂ ਦੇ ਅੰਦਰ ਕਾਰਵਾਈ ਕਰੋ, ਨਹੀਂ ਤਾਂ ਪੇਸ਼ਕਸ਼ ਖਤਮ ਹੋ ਜਾਵੇਗੀ। ਸਾਡੇ ਨੁਮਾਇੰਦੇ ਨੂੰ ਕਾਲ ਕਰੋ।",
        ],
    },

    # ── Vector 12: Bare shortened-URL spam — NO apk keyword, NO reward
    #    superlative, just a generic shortener tacked onto a plausible-sounding
    #    transactional/prize message. This is the pattern a lexical-only URL
    #    classifier misses (the shortener itself carries all the risk). ──
    "shortened_url_bare": {
        "en": [
            "{bank} Free Msg: Your bill is paid. Thanks, here's a little gift for you: {fake_url}",
            "Hello {name}, your FEDEX package with tracking code GB-{order_id} is waiting for you to set delivery preferences: {fake_url}",
            "Congratulations! You have won a cash prize of {amount}. Claim now at {fake_url} before it expires.",
        ],
        "hi_latn": [
            "Badhai ho! Aapne {amount} ka prize jeeta hai. Abhi claim kare {fake_url} par warna offer khatam ho jayega.",
        ],
    },
}


# ────────────────────────────────────────────────────────────────────
# Ham (Legitimate) Templates
# ────────────────────────────────────────────────────────────────────

HAM_TEMPLATES = {
    "legitimate_bank_otp": {
        "en": [
            "{otp} is your OTP for {bank} transaction of {amount}. Valid for 5 minutes. Do not share with anyone. - {bank}",
            "Your {bank} account ending XX4523 has been credited with {amount}. Available balance: {amount}.",
            "Dear Customer, your {bank} fixed deposit of {amount} has been renewed. Thank you for banking with us.",
            "Transaction alert: {amount} debited from A/C XX7891 at Amazon. Avl Bal: {amount}. Not you? Call 1800XXXXXXX",
        ],
        "hi": [
            "{otp} आपका {bank} लेनदेन का OTP है। {amount} के लिए। 5 मिनट के लिए वैध। किसी से शेयर न करें।",
            "आपके {bank} खाते XX4523 में {amount} क्रेडिट हुए हैं। उपलब्ध शेष: {amount}।",
        ],
    },
    "legitimate_delivery": {
        "en": [
            "Your {ecommerce} order #ORD-{order_id} has been shipped. Track: {legit_url}",
            "{ecommerce}: Your package is out for delivery today. Enjoy your purchase!",
            "Your {ecommerce} order has been delivered. Rate your experience on the app.",
            "Your Flipkart order #OD{order_id} shipped. Delivery by tomorrow. Track: {brand_short_url}",
            "Your Amazon order #{order_id} is out for delivery. Track: {brand_short_url}",
            "Myntra: Your order has shipped and will arrive soon. Track: {brand_short_url}",
            "Hello {name}, your {ecommerce} order with tracking code OD-{order_id} is out for delivery today. Track: {brand_short_url}",
        ],
        "hi": [
            "आपका {ecommerce} ऑर्डर #ORD-{order_id} शिप हो गया है। ट्रैक करें: {legit_url}",
            "{ecommerce}: आपका पार्सल आज डिलीवर होगा। धन्यवाद!",
            "आपका Flipkart ऑर्डर #OD{order_id} शिप हो गया है। ट्रैक करें: {brand_short_url}",
        ],
    },
    "legitimate_govt": {
        "en": [
            "Your Aadhaar has been successfully linked to your PAN. Reference: LINK-{order_id}. - UIDAI",
            "IRCTC: Your PNR {order_id} is confirmed. Train: Rajdhani Exp, Date: 15-Mar-2026. Happy journey!",
            "Your driving license renewal application has been received. Track at parivahan.gov.in/DL-{order_id}",
        ],
        "hi": [
            "आपका आधार आपके PAN से सफलतापूर्वक लिंक हो गया है। संदर्भ: LINK-{order_id}। - UIDAI",
        ],
    },
    "legitimate_promo": {
        "en": [
            "{ecommerce} Sale! Up to 70% off on electronics. Shop now on the {ecommerce} app. T&C apply. Reply STOP to opt out.",
            "{telecom}: Your recharge of {amount} was successful. Validity: 28 days. Data: 1.5GB/day.",
            "Enjoy 20% off on your next {ecommerce} order! Use code SAVE20. Valid till 31-Mar. Reply STOP to unsubscribe.",
        ],
        "hi": [
            "{ecommerce} सेल! इलेक्ट्रॉनिक्स पर 70% तक की छूट। {ecommerce} ऐप पर शॉप करें। STOP लिखकर भेजें ऑप्ट-आउट के लिए।",
            "{telecom}: आपका {amount} का रिचार्ज सफल। वैधता: 28 दिन। STOP भेजें अनसब्सक्राइब करने के लिए।",
        ],
    },
    "personal_messages": {
        "en": [
            "Hey, are you coming to the party tonight? Let me know!",
            "Happy Birthday! 🎂 Wishing you a wonderful year ahead!",
            "Meeting rescheduled to 3 PM. See you in conference room B.",
            "Can you pick up milk on the way home? Thanks!",
            "Great presentation today! The client loved it.",
        ],
        "hi": [
            "क्या तुम आज रात पार्टी में आ रहे हो? बताना!",
            "जन्मदिन मुबारक! 🎂 ढेर सारी शुभकामनाएं!",
            "मीटिंग 3 बजे हो गई है। कॉन्फ्रेंस रूम B में मिलते हैं।",
            "घर आते वक्त दूध ले आना। धन्यवाद!",
        ],
        "hi_latn": [
            "Kya tum aaj party mein aa rahe ho? Batao!",
            "Happy Birthday bhai! 🎂 Bahut bahut badhai!",
            "Meeting 3 baje shift ho gayi hai. Room B mein milte hain.",
        ],
        "bn": [
            "তুমি কি আজ রাতে পার্টিতে আসছো? জানাও!",
            "শুভ জন্মদিন! 🎂 অনেক শুভেচ্ছা!",
        ],
        "ta": [
            "இன்று இரவு பார்ட்டிக்கு வருகிறாயா? சொல்!",
            "பிறந்தநாள் வாழ்த்துக்கள்! 🎂",
        ],
    },

    # ── Hard negatives: degenerate word repetition (typos, glitchy keyboard
    #    input, rambling voice-to-text) — must NEVER be flagged as spam even
    #    though heavy repetition can look "unusual" to a naive classifier. ──
    "repeated_word_noise": {
        "mr": [
            "मी देर आहे, तर तुम्हाला उद्या रात्री कॉल करा, मी आणि तू मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी.",
            "अरे यार मी मी मी मी मी मी मी मी मी मी मी मी काय बोलू तेच कळत नाही आहे आज.",
        ],
        "hi": [
            "अरे यार मुझे मुझे मुझे मुझे मुझे मुझे मुझे मुझे मुझे मुझे नहीं पता क्या बोलूं आज।",
            "हां हां हां हां हां हां हां हां हां हां ठीक है बाबा समझ गया।",
        ],
        "en": [
            "lol lol lol lol lol lol lol lol lol lol lol that was so funny",
            "no no no no no no no no no no no no wait I meant something else",
        ],
        "bn": [
            "আমি আমি আমি আমি আমি আমি আমি আমি আমি আমি কি বলব বুঝতে পারছি না।",
        ],
        "hi_latn": [
            "yaar mujhe mujhe mujhe mujhe mujhe mujhe mujhe mujhe kuch samajh nahi aa raha aaj",
        ],
    },

    # ── Benign code-mixed / multi-script conversational messages, so the
    #    model learns that script-mixing alone is not a spam signal. ──
    "mixed_language_conversational": {
        "mixed_script": [
            "ভাই, আজ রাতে dinner কোথায় করবো? তুমি বলো।",
            "ਯਾਰ ਅੱਜ college ਨਹੀਂ ਜਾ ਰਿਹਾ, thoda tired hoon।",
            "आज मीटिंग 4 PM ला आहे, please वेळेवर ये।",
            "Bhai kal exam hai, थोड़ा जल्दी सो जा आज।",
            "সন্ধ্যায় বাজারে যাচ্ছি, তোমার জন্য কিছু আনবো কি?",
        ],
    },
}


# ────────────────────────────────────────────────────────────────────
# Generator Class
# ────────────────────────────────────────────────────────────────────

class SyntheticDataGenerator:
    """
    Generate diverse, multilingual synthetic training data for
    SMS sideloading spam detection.
    """

    def __init__(self, seed: int = 42, max_per_template: int = 5):
        self.rng = random.Random(seed)
        self.max_per_template = max_per_template

    def _fill_template(self, template: str) -> str:
        """Replace template variables with random realistic values."""
        replacements = {
            '{bank}': self.rng.choice(BANKS),
            '{upi_app}': self.rng.choice(UPI_APPS),
            '{telecom}': self.rng.choice(TELECOM),
            '{ecommerce}': self.rng.choice(ECOMMERCE),
            '{amount}': self.rng.choice(AMOUNTS),
            '{name}': self.rng.choice(NAMES),
            '{name2}': self.rng.choice(NAMES),
            '{fake_url}': self.rng.choice(FAKE_URLS),
            '{suspicious_url}': self.rng.choice(SUSPICIOUS_URLS),
            '{legit_url}': self.rng.choice(LEGITIMATE_URLS),
            '{brand_short_url}': self.rng.choice(LEGITIMATE_BRAND_SHORT_URLS),
            '{otp}': self.rng.choice(OTP_CODES),
            '{order_id}': ''.join(self.rng.choices(string.digits, k=8)),
        }

        result = template
        for key, value in replacements.items():
            result = result.replace(key, value)
        return result

    def _generate_metadata(
        self, is_spam: bool, lang: str
    ) -> Dict[str, str]:
        """Generate realistic sender metadata."""
        if is_spam:
            # Spam senders: varied suspicious patterns
            sender_type = self.rng.choice([
                'standard_number', 'international', 'email_gateway',
                'spoofed_shortcode', 'alphanumeric',
            ])
            if sender_type == 'standard_number':
                sender = '+91' + ''.join(self.rng.choices(string.digits, k=10))
            elif sender_type == 'international':
                prefix = self.rng.choice(['+44', '+1', '+86', '+234', '+63'])
                sender = prefix + ''.join(self.rng.choices(string.digits, k=10))
            elif sender_type == 'email_gateway':
                sender = f"noreply@{self.rng.choice(SUSPICIOUS_URLS)}"
            elif sender_type == 'spoofed_shortcode':
                sender = ''.join(self.rng.choices(string.digits, k=6))
            else:
                brand = self.rng.choice(BANKS).upper().replace(' ', '')[:6]
                sender = f"VM-{brand}"
        else:
            # Ham senders: legitimate patterns
            sender_type = self.rng.choice([
                'shortcode', 'alphanumeric', 'personal',
            ])
            if sender_type == 'shortcode':
                sender = ''.join(self.rng.choices(string.digits, k=6))
            elif sender_type == 'alphanumeric':
                brand = self.rng.choice(BANKS).upper().replace(' ', '')[:6]
                sender = f"AD-{brand}"
            else:
                sender = '+91' + ''.join(self.rng.choices(string.digits, k=10))

        return {
            'sender': sender,
            'language': lang,
        }

    def generate_dataset(self) -> List[Dict]:
        """
        Generate the complete synthetic dataset.

        Returns:
            List of dicts with keys: text, label, language, category,
                                      metadata, source
        """
        dataset = []

        # ── Generate spam samples ──
        for category, lang_templates in SPAM_TEMPLATES.items():
            for lang, templates in lang_templates.items():
                for template in templates:
                    for _ in range(self.max_per_template):
                        text = self._fill_template(template)
                        metadata = self._generate_metadata(True, lang)
                        dataset.append({
                            'text': text,
                            'label': 1,  # spam
                            'language': lang,
                            'category': category,
                            'metadata': json.dumps(metadata),
                            'source': 'synthetic_spam',
                        })

        # ── Generate ham samples ──
        for category, lang_templates in HAM_TEMPLATES.items():
            for lang, templates in lang_templates.items():
                for template in templates:
                    for _ in range(self.max_per_template):
                        text = self._fill_template(template)
                        metadata = self._generate_metadata(False, lang)
                        dataset.append({
                            'text': text,
                            'label': 0,  # ham
                            'language': lang,
                            'category': category,
                            'metadata': json.dumps(metadata),
                            'source': 'synthetic_ham',
                        })

        self.rng.shuffle(dataset)
        return dataset

    def save_dataset(self, output_path: str):
        """Generate and save dataset to CSV."""
        dataset = self.generate_dataset()
        os.makedirs(os.path.dirname(output_path), exist_ok=True)

        with open(output_path, 'w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(
                f,
                fieldnames=['text', 'label', 'language', 'category',
                            'metadata', 'source'],
            )
            writer.writeheader()
            writer.writerows(dataset)

        # Print statistics
        spam_count = sum(1 for d in dataset if d['label'] == 1)
        ham_count = sum(1 for d in dataset if d['label'] == 0)
        langs = set(d['language'] for d in dataset)
        categories = set(d['category'] for d in dataset)

        print(f"\n{'='*60}")
        print(f"Synthetic Dataset Generated: {output_path}")
        print(f"{'='*60}")
        print(f"Total samples:    {len(dataset)}")
        print(f"  Spam:           {spam_count} ({100*spam_count/len(dataset):.1f}%)")
        print(f"  Ham:            {ham_count} ({100*ham_count/len(dataset):.1f}%)")
        print(f"Languages:        {len(langs)} — {sorted(langs)}")
        print(f"Attack vectors:   {len([c for c in categories if c in SPAM_TEMPLATES])}")
        print(f"Ham categories:   {len([c for c in categories if c in HAM_TEMPLATES])}")
        print(f"{'='*60}\n")

        return dataset


# ────────────────────────────────────────────────────────────────────
# CLI Entry Point
# ────────────────────────────────────────────────────────────────────

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(
        description='Generate synthetic SMS sideloading spam dataset'
    )
    parser.add_argument(
        '--output', '-o',
        default='/scratch/m25cse012/sms_spam_detector/data/synthetic/synthetic_dataset.csv',
        help='Output CSV path'
    )
    parser.add_argument('--seed', type=int, default=42)
    parser.add_argument('--max-per-template', type=int, default=5)
    args = parser.parse_args()

    generator = SyntheticDataGenerator(
        seed=args.seed,
        max_per_template=args.max_per_template,
    )
    generator.save_dataset(args.output)
