"""
Feature Extractor — Stage 1 of the Fake App Sideloading Detection Pipeline
===========================================================================
Problem: "Fake mobile apps circulated via SMS/WhatsApp, leading to phishing
          and financial fraud."

Produces a 39-dimensional feature vector:
  Dims  0-28  Text heuristics (APK link, QR, callback, urgency, etc.)
  Dims 29-38  URL Intelligence features (Stage 2 offline + Stage 3 live HTTP)

URL Intelligence dims (10):
  [29]  url_stage2_risk_score       - lexical+TLD+brand impersonation [0,1]
  [30]  url_has_apk_extension       - .apk/.xapk in URL path
  [31]  url_is_low_rep_tld          - .icu/.xyz/.top etc.
  [32]  url_has_brand_impersonation - Levenshtein/Jaro-Winkler hit
  [33]  url_in_blocklist            - domain in local malicious domain list
  [34]  url_stage3_risk_score       - live HTTP analysis score [0,1]
  [35]  url_is_apk_content_type     - HTTP Content-Type = APK
  [36]  url_has_redirect_chain      - redirect count > 1
  [37]  url_has_phishing_form       - OTP/PAN/Aadhaar fields in HTML
  [38]  url_has_apk_href            - href=".apk" found in fetched HTML

Strategy:
  Training:  URLFeatureCache pre-fetches all URL features in parallel
             before the GPU trainer starts. No network I/O during training.
  Inference: Stage 2 (offline, ~0ms) always runs.
             Stage 3 (live HTTP, <=5s timeout) runs only when
             Stage 2 risk >= 0.5 and a URL exists in the message.
"""

import re
import math
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field, asdict
from typing import Dict, List, Optional, Any, Tuple

_APK_URL_RE = re.compile(r'\.(apk|xapk|apks|apkm|aab)\b', re.IGNORECASE)


@dataclass
class ExtractionResult:
    # URL signals (text-level)
    has_url: bool = False
    has_shortened_url: bool = False
    has_apk_link: bool = False
    has_suspicious_domain: bool = False
    has_deep_link: bool = False
    has_low_reputation_tld: bool = False
    url_count: int = 0
    # QR code signals
    has_qr_reference: bool = False
    qr_payload_type: str = "none"
    has_qr_apk_payload: bool = False
    has_qr_upi_collect: bool = False
    # Callback/vishing signals
    has_callback_fraud: bool = False
    callback_number_count: int = 0
    has_callback_apk_prompt: bool = False
    # Content signals
    urgency_score: float = 0.0
    financial_lure_score: float = 0.0
    authority_impersonation_score: float = 0.0
    action_verb_count: int = 0
    financial_term_count: int = 0
    message_length: int = 0
    # Formatting anomalies
    has_generic_salutation: bool = False
    excessive_caps_ratio: float = 0.0
    excessive_punctuation: bool = False
    has_spelling_obfuscation: bool = False
    # Sender signals
    sender_is_shortcode: bool = False
    sender_is_standard_number: bool = False
    sender_is_international: bool = False
    sender_is_email_gateway: bool = False
    sender_has_opt_out: bool = False
    sender_is_alphanumeric: bool = False
    # Psychological vectors
    transactional_update_score: float = 0.0
    affinity_romance_score: float = 0.0
    reward_lottery_score: float = 0.0
    # Campaign patterns
    has_wedding_invitation_pattern: bool = False
    has_loan_app_pattern: bool = False
    has_delivery_notification_pattern: bool = False
    has_govt_transport_pattern: bool = False
    # Language
    detected_language: str = "unknown"
    # Media attachments (image/video) — this classifier is TEXT-ONLY. Image/
    # video bytes are never fetched, decoded, or otherwise analyzed here (QR
    # codes are the one exception, and only via the already-decoded
    # `qr_decoded_text` metadata string, never raw image bytes). These fields
    # exist purely so callers can see that a media attachment was present and
    # was deliberately ignored, not silently dropped. They are NOT part of
    # to_feature_vector() and never influence rule_confidence / the model.
    has_image_attachment: bool = False
    has_video_attachment: bool = False
    media_ignored: bool = False
    # Explainability
    extracted_urls: List[str] = field(default_factory=list)
    matched_brands: List[str] = field(default_factory=list)
    matched_keywords: Dict[str, List[str]] = field(default_factory=dict)
    sideloading_vectors: List[str] = field(default_factory=list)
    # Heuristic verdict
    rule_confidence: float = 0.0
    rule_based_spam: bool = False
    is_likely_benign: bool = False   # Anti-FP guard: True when msg is conversational with no attack surface
    # URL Intelligence dims 29-38 (filled by URLFeatureEnricher)
    url_stage2_risk_score: float = 0.0
    url_has_apk_extension: bool = False
    url_is_low_rep_tld: bool = False
    url_has_brand_impersonation: bool = False
    url_in_blocklist: bool = False
    url_stage3_risk_score: float = 0.0
    url_is_apk_content_type: bool = False
    url_has_redirect_chain: bool = False
    url_has_phishing_form: bool = False
    url_has_apk_href: bool = False

    def to_feature_vector(self) -> List[float]:
        return [
            # Text heuristics 0-24
            float(self.has_url),
            float(self.has_shortened_url),
            float(self.has_apk_link),
            float(self.has_suspicious_domain),
            float(self.has_deep_link),
            float(self.has_low_reputation_tld),
            float(self.url_count),
            float(self.has_qr_reference),
            self.urgency_score,
            float(self.action_verb_count),
            float(self.financial_term_count),
            float(self.message_length) / 500.0,
            float(self.has_generic_salutation),
            self.excessive_caps_ratio,
            float(self.excessive_punctuation),
            float(self.has_spelling_obfuscation),
            float(self.sender_is_shortcode),
            float(self.sender_is_standard_number),
            float(self.sender_is_international),
            float(self.sender_is_email_gateway),
            self.financial_lure_score,
            self.transactional_update_score,
            self.authority_impersonation_score,
            self.affinity_romance_score,
            self.rule_confidence,
            # QR + callback 25-28
            float(self.has_qr_apk_payload),
            float(self.has_qr_upi_collect),
            float(self.has_callback_fraud),
            float(self.has_callback_apk_prompt),
            # URL Intelligence 29-38
            self.url_stage2_risk_score,
            float(self.url_has_apk_extension),
            float(self.url_is_low_rep_tld),
            float(self.url_has_brand_impersonation),
            float(self.url_in_blocklist),
            self.url_stage3_risk_score,
            float(self.url_is_apk_content_type),
            float(self.url_has_redirect_chain),
            float(self.url_has_phishing_form),
            float(self.url_has_apk_href),
        ]

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


class URLFeatureEnricher:
    """
    Writes URL Intelligence results (from url_intelligence.URLIntelligence)
    into dims 29-38 of an ExtractionResult in-place.

    Called at training time from the pre-fetched cache.
    Called at inference time live after the ML model runs.
    """

    @staticmethod
    def enrich(result: ExtractionResult, url_intel: Optional[Dict]) -> None:
        if not url_intel:
            return
        s2 = url_intel.get("stage2", {})
        s3 = url_intel.get("stage3", {})

        # Stage 2 (always available - offline)
        result.url_stage2_risk_score       = float(s2.get("stage2_risk_score", 0.0))
        result.url_has_apk_extension       = bool(s2.get("has_apk_extension", False))
        result.url_is_low_rep_tld          = bool(s2.get("is_low_rep_tld", False))
        result.url_has_brand_impersonation = bool(s2.get("impersonation_hits", []))
        result.url_in_blocklist            = bool(s2.get("in_blocklist", False))

        # Stage 3 (live HTTP - 0 when not run)
        result.url_stage3_risk_score   = float(s3.get("stage3_risk_score", 0.0))
        result.url_is_apk_content_type = bool(s3.get("is_apk_content_type", False))
        result.url_has_redirect_chain  = bool(s3.get("redirect_count", 0) > 1)
        result.url_has_phishing_form   = bool(s3.get("html_has_phishing_form", False))
        result.url_has_apk_href        = bool(s3.get("html_apk_hrefs", []))

        # Propagate confirmed signals back to text-level flags so hard rules fire
        if result.url_has_apk_extension or result.url_is_apk_content_type:
            result.has_apk_link = True
        if result.url_has_brand_impersonation:
            result.has_suspicious_domain = True
        if result.url_is_low_rep_tld:
            result.has_low_reputation_tld = True
        if result.url_in_blocklist:
            result.rule_confidence = 1.0
            result.rule_based_spam = True
        # Raise rule_confidence if URL intelligence confirms high risk
        url_max_risk = max(result.url_stage2_risk_score, result.url_stage3_risk_score)
        if url_max_risk > result.rule_confidence:
            result.rule_confidence = min(1.0, result.rule_confidence + url_max_risk * 0.3)
            if result.rule_confidence >= 0.50:
                result.rule_based_spam = True


def pick_primary_url(
    urls: List[str],
    url_cache: Optional[Dict[str, Dict]] = None,
) -> Optional[str]:
    """Pick the single URL whose intelligence features should enrich the message."""
    if not urls:
        return None

    if url_cache:
        best_url = None
        best_risk = -1.0
        for url in urls:
            intel = url_cache.get(url)
            if not intel:
                continue
            risk = float(intel.get("stage2", {}).get("stage2_risk_score", 0.0))
            if risk > best_risk:
                best_risk = risk
                best_url = url
        if best_url:
            return best_url

    apk_urls = [u for u in urls if _APK_URL_RE.search(u)]
    if apk_urls:
        with_scheme = [u for u in apk_urls if u.lower().startswith(('http://', 'https://'))]
        return min(with_scheme or apk_urls, key=len)

    with_scheme = [u for u in urls if u.lower().startswith(('http://', 'https://', 'www.'))]
    if with_scheme:
        return min(with_scheme, key=len)
    return min(urls, key=len)


class URLFeatureCache:
    """
    Pre-fetches URLIntelligence results for all unique URLs in a corpus.
    Used during training/evaluation so batches never block on network I/O.
    """

    def __init__(self, cache: Dict[str, Dict]):
        self._cache = cache

    def get(self, url: str) -> Optional[Dict]:
        return self._cache.get(url)

    def get_for_urls(self, urls: List[str]) -> Optional[Dict]:
        primary = pick_primary_url(urls, self._cache)
        return self._cache.get(primary) if primary else None

    @classmethod
    def build(
        cls,
        pairs: List[Tuple[str, Optional[Dict[str, str]]]],
        feature_extractor: "FeatureExtractor",
        run_stage3: bool = False,
        max_workers: int = 16,
    ) -> "URLFeatureCache":
        from src.url_intelligence import URLIntelligence

        unique_urls: set = set()
        for text, meta in pairs:
            result = feature_extractor.extract(text, meta or {})
            unique_urls.update(result.extracted_urls)

        if not unique_urls:
            return cls({})

        intel = URLIntelligence(run_stage3=run_stage3)
        cache: Dict[str, Dict] = {}

        def _analyze(url: str) -> Tuple[str, Dict]:
            return url, intel.analyze(url)

        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {executor.submit(_analyze, url): url for url in unique_urls}
            for future in as_completed(futures):
                url = futures[future]
                try:
                    analyzed_url, result = future.result()
                    cache[analyzed_url] = result
                except Exception as exc:
                    cache[url] = {
                        "error": str(exc),
                        "stage2": {"stage2_risk_score": 0.0},
                        "stage3": {"stage3_risk_score": 0.0},
                    }

        return cls(cache)


class FeatureExtractor:
    """
    Stage 1: text-only heuristic extraction. NO network calls.
    Produces dims 0-28. Dims 29-38 are filled by URLFeatureEnricher.
    """

    _URL_RE = re.compile(
        r'(?:https?://|www\.)[^\s<>"\')\],;]+',
        re.IGNORECASE | re.UNICODE,
    )
    _BARE_DOMAIN_RE = re.compile(
        r'(?<![a-zA-Z0-9@/])[a-zA-Z0-9](?:[a-zA-Z0-9\-]*[a-zA-Z0-9])?'
        r'(?:\.[a-zA-Z]{2,}){1,3}(?:/[^\s<>"\')\],;]*)?',
        re.IGNORECASE,
    )
    _DEEP_LINK_RE = re.compile(
        r'(?:intent://|market://|app://)[^\s]+',
        re.IGNORECASE,
    )
    _FILE_EXT_RE = re.compile(
        r'\.(apk|xapk|apks|apkm|aab|ipa|exe|msi|bat|cmd|bin|dex)\b',
        re.IGNORECASE,
    )
    _LOW_REP_TLDS = {
        '.icu','.top','.xyz','.cc','.tk','.ml','.ga','.cf','.gq',
        '.buzz','.club','.online','.site','.fun','.live','.store',
        '.space','.pw','.work','.click','.link','.monster','.rest',
        '.cam','.bar','.surf','.loan',
    }
    _ZERO_WIDTH_RE = re.compile(r'[\u200b-\u200f\ufeff]')
    _OBFUSCATION_RE = re.compile(
        r'd[0o]wn[1l][0o]ad|[1i]n[s5]t[a@][1l]{1,2}|[a@]cc[0o]unt|'
        r'p[a@][s$]{2}w[0o]rd|[s$]ecur[1i]ty|v[e3]r[1i]f[iy]|'
        r'upd[a@]t[e3]|cl[1i]ck|b[a@]nk', re.IGNORECASE,
    )
    _SALUTATION_RE = re.compile(
        r'(?:dear\s+(?:customer|user|sir|madam|citizen|member|valued)|'
        r'प्रिय\s+(?:ग्राहक|उपयोगकर्ता|सदस्य)|'
        r'প্রিয়\s+(?:গ্রাহক|ব্যবহারকারী)|'
        r'அன்புள்ள\s+(?:வாடிக்கையாளர்|பயனர்))',
        re.IGNORECASE | re.UNICODE,
    )
    _UPI_COLLECT_RE = re.compile(r'upi://(?:collect|pay|mandate)\?|pa=.*?&.*?pn=', re.IGNORECASE)
    _QR_TEXT_RE = re.compile(
        r'\b(?:scan\s+(?:the\s+)?(?:qr|code|barcode)|qr\s*code|'
        r'scan\s+to\s+(?:download|install|pay|verify|get|open)|'
        r'scan\s*karo|scan\s*kare)\b', re.IGNORECASE | re.UNICODE,
    )
    _PHONE_RE = re.compile(
        r'(?<!\d)(?:\+91[-\s]?)?(?:1800[-\s]?\d{3}[-\s]?\d{4}|0?[6-9]\d{9})(?!\d)',
    )
    _CALLBACK_VERB_RE = re.compile(
        r'\b(?:call\s+(?:us|now|immediately|back|our|this|karo|kare)|'
        r'contact\s+(?:us|helpline|support|customer\s*care)|'
        r'helpline|toll\s*free|customer\s*care|'
        r'हमें\s*कॉल|कॉल\s*करें|हेल्पलाइन|'
        r'আমাদের\s*কল|হেল্পলাইন|call\s*karo|call\s*kare)\b',
        re.IGNORECASE | re.UNICODE,
    )
    _CALLBACK_APK_RE = re.compile(
        r'(?:call|contact|helpline|कॉल|কল).*?(?:install|app|officer|executive|इंस्टॉल|ইনস্টল)|'
        r'(?:officer|executive|agent|अधिकारी).*?(?:install|app\s*download|help\s+install)|'
        r'(?:install|app).*?(?:call|contact|helpline)',
        re.IGNORECASE | re.UNICODE | re.DOTALL,
    )

    # ── Enhanced APK detection — any form of APK mention = SPAM ────────
    # Catches bare APK filenames anywhere in message text
    _BARE_APK_FILENAME_RE = re.compile(
        r'\b[\w][\w\-]*\.(?:apk|xapk|apks|apkm|aab)\b',
        re.IGNORECASE,
    )
    # Catches forwarded file blocks in any Indic/foreign language
    _FORWARDED_APK_RE = re.compile(
        r'(?:'
        r'\[(?:Forwarded|अग्रेषित|ফরওয়ার্ড\s*করা\s*হয়েছে|ਅੱਗੇ\s*ਭੇਜਿਆ'
        r'|\u0641\u0631\u0648\u0627\u0631\u0688|\u0641\u0648\u0631\u0648\u0627\u0631\u0688\u0688|Iletildi)\]'
        r'|(?:forwarded|shared)\s+(?:file|attachment|message)'
        r')[\s\S]{0,150}?\.(?:apk|xapk|apks|apkm|aab)',
        re.IGNORECASE | re.UNICODE | re.DOTALL,
    )
    # Catches download/install verb (any language) within 100 chars of .apk extension
    _DOWNLOAD_APK_COMBO_RE = re.compile(
        r'(?:'
        r'(?:download|install|'
        r'\u0921\u093e\u0909\u0928\u0932\u094b\u0921|\u0907\u0902\u0938\u094d\u091f\u0949\u0932|'  # Hindi
        r'\u0921\u093e\u0909\u0928\u0932\u094b\u0921\s*\u0915\u0930\u0947\u0902|\u0907\u0902\u0938\u094d\u091f\u093e\u0932\s*\u0915\u0930\u0947\u0902|'  # Hindi verb forms
        r'\u09a1\u09be\u0989\u09a8\u09b2\u09cb\u09a1|\u0987\u09a8\u09b8\u09cd\u099f\u09b2|'  # Bengali
        r'\u0da9\u0dcf\u0d85\u0db1\u0dca\u0dbd\u0ddd\u0da9\u0dca|'  # mixed
        r'\u0dab\u0dd2\u0dbb\u0dd4\u0dc0\u0dd4|'  # Sinhala
        r'\u062f\u0627\u0624\u0646\u0644\u0648\u062f|\u0627\u0646\u0633\u062a\u0627\u0644|'  # Urdu/Arabic
        r'\u0917\u0921\u093e\u0909\u0928\u0932\u094b\u0921|'  # Hindi variant
        r'install\s*karo|install\s*kare|download\s*karo|download\s*kare|'
        r'app\s+install|install\s+app|app\s+download)'
        r'[\s\S]{0,80}?\.(?:apk|xapk|apks)'
        r'|'
        r'\.(?:apk|xapk|apks)[\s\S]{0,80}?'
        r'(?:download|install|install\s*karo|install\s*kare|app\s+download)'
        r')',
        re.IGNORECASE | re.UNICODE | re.DOTALL,
    )

    # ── KYC / Authority + Callback fraud (cross-language) ───────────────
    _KYC_AUTHORITY_RE = re.compile(
        r'(?:kyc|e-?kyc|ekyc|know\s+your\s+customer'
        r'|\u0915\u0947\u0935\u093e\u0908\u0938\u0940|'  # Hindi: केवाईसी
        r'\u0995\u09c7\u0993\u09df\u09be\u0987\u09b8\u09bf|'  # Bengali: কেওয়াইসি
        r'\u0a15\u0a47\u0a35\u0a3e\u0a08\u0a38\u0a40|'  # Punjabi: ਕੇਵਾਈਸੀ
        r'\u06a9\u06d2\s*\u0648\u0627\u0626\u06cc\s*\u0633\u06cc|'  # Urdu: کے وائی سی
        r'pnb\s*kyc|sbi\s*kyc|hdfc\s*kyc|icici\s*kyc|axis\s*kyc|rbl\s*kyc'
        r'|account\s+(?:kyc|blocked|suspended|verify|verification)'
        r'|aadhaar\s+(?:expired?|update|verify|expire)'
        r'|pan\s+(?:expired?|update|verify|expire)'
        r'|kyc\s+(?:expired?|expire|pending|complete|update|verify))',
        re.IGNORECASE | re.UNICODE,
    )
    _IMPLICIT_CALLBACK_RE = re.compile(
        r'(?:call\s+(?:back|us|our|representative|now|immediately|this\s+number|below\s+number)'
        r'|contact\s+(?:our|us|support|helpline|customer)'
        r'|dial\s+(?:our|this|the|below)'
        r'|reach\s+(?:us|our|support)'
        r'|below\s+(?:number|helpline)|number\s+(?:below|given)'
        r'|hamara\s+representative|hamara\s+number|hamara\s+agent'
        r'|\u0939\u092e\u0947\u0902\s*\u0915\u0949\u0932|\u0915\u0943\u092a\u092f\u093e\s*\u0915\u0949\u0932|\u0939\u0947\u0932\u094d\u092a\u0932\u093e\u0907\u0928'  # Hindi
        r'|\u0906\u092e\u093e\u0926\u0947\u0930\s*\u0995\u09b2|\u0939\u09c7\u09b2\u09cd\u09aa\u09b2\u09be\u0987\u09a8'  # Bengali
        r'|\u0938\u093e\u0921\u093e\u0902\u0020\u0928\u0902\u092c\u0930\u0020\u0935\u0930|\u0939\u0948\u0932\u092a\u0932\u093e\u0907\u0928'  # Punjabi
        r'|\u06c1\u0645\u06cc\u06ba\s*\u06a9\u0627\u0644|\u06c1\u06cc\u0644\u067e\s*\u0644\u0627\u0626\u0646)',  # Urdu
        re.IGNORECASE | re.UNICODE,
    )

    def __init__(self, config: Dict):
        self._shorteners = set(config.get('url_shorteners', []))
        self._brands     = config.get('impersonation_brands', [])
        self._keywords   = config.get('keywords', {})
        self._urgency_pats   = self._compile_kw('urgency')
        self._action_pats    = self._compile_kw('download_action')
        self._financial_pats = self._compile_kw('financial')
        self._qr_pats        = self._compile_kw('qr_code')
        self._govt_pats      = self._compile_kw('government_impersonation')
        self._callback_pats  = self._compile_kw('callback_vishing')
        self._campaign_pats  = self._build_campaigns()
        self._reward_pats    = self._build_reward_pats()
        self._transact_pats  = self._build_transact_pats()
        self._affinity_pats  = self._build_affinity_pats()

    def _compile_kw(self, category: str) -> List[re.Pattern]:
        patterns = []
        for _lang, words in self._keywords.get(category, {}).items():
            for word in words:
                try:
                    if all(ord(c) < 128 for c in word if c.isalpha()):
                        p = re.compile(r'\b' + re.escape(word) + r'\b', re.IGNORECASE | re.UNICODE)
                    else:
                        p = re.compile(re.escape(word), re.IGNORECASE | re.UNICODE)
                    patterns.append(p)
                except re.error:
                    pass
        return patterns

    def _build_reward_pats(self): return [re.compile(p, re.IGNORECASE | re.UNICODE) for p in [
        r'you\s+(?:have\s+)?won', r'congratulations', r'lottery',
        r'claim\s+(?:your\s+)?(?:reward|prize|cashback|refund)',
        r'lucky\s+(?:winner|draw|customer)', r'free\s+gift',
        r'(?:cash|money)\s*back', r'आपने\s+जीता', r'बधाई', r'इनाम',
        r'আপনি\s+জিতেছেন', r'aapne\s+jeeta', r'cashback\s+mil',
    ]]

    def _build_transact_pats(self): return [re.compile(p, re.IGNORECASE | re.UNICODE) for p in [
        r'(?:package|parcel|delivery)\s+(?:pending|failed|missed)',
        r'unpaid\s+(?:toll|fine|bill|invoice|due)',
        r'(?:payment|transaction)\s+(?:failed|declined|rejected)',
        r'(?:electricity|gas|water)\s+(?:bill|connection)\s+(?:due|disconnect)',
        r'delivery\s+(?:ruka|pending|fail)',
    ]]

    def _build_affinity_pats(self): return [re.compile(p, re.IGNORECASE | re.UNICODE) for p in [
        r'sorry\s*,?\s*wrong\s+number.*(?:nice|cute|handsome)',
        r'(?:grandson|grandpa|grandma).*(?:accident|arrested|hospital|jail)',
        r'(?:i\'?m|i\s+am)\s+in\s+(?:trouble|hospital|jail|accident)',
        r'galat\s+number.*(?:nice|accha)',
    ]]

    def _build_campaigns(self):
        return {
            'wedding': [re.compile(p, re.IGNORECASE | re.UNICODE) for p in [
                r'(?:wedding|shaadi|विवाह|शादी|বিবাহ).*(?:invitation|card|invite|निमंत्रण)',
                r'(?:wedding|shaadi).*\.apk',
            ]],
            'loan': [re.compile(p, re.IGNORECASE | re.UNICODE) for p in [
                r'(?:instant|quick|fast|easy)\s+(?:loan|credit|cash)',
                r'(?:loan|ऋण|লোন)\s+(?:approved|sanctioned|ready)',
                r'(?:personal|business)\s+loan.*(?:download|install|app)',
            ]],
            'delivery': [re.compile(p, re.IGNORECASE | re.UNICODE) for p in [
                r'(?:your|the)\s+(?:package|parcel|order|courier).*(?:track|verify|confirm).*(?:link|click)',
                r'(?:missed|failed)\s+delivery.*(?:reschedule|verify)',
            ]],
            'govt_transport': [re.compile(p, re.IGNORECASE | re.UNICODE) for p in [
                r'(?:e-?\s*challan|ई-?\s*चालान|traffic\s+(?:fine|violation|challan))',
                r'(?:mparivahan|m-?\s*parivahan|परिवाहन)',
                r'(?:driving|DL|RC)\s+(?:license|licence).*(?:expired|update|renew)',
            ]],
        }

    # ── New detection helpers ────────────────────────────────────────────

    @staticmethod
    def _detect_ignored_media(metadata: Dict[str, str], r: ExtractionResult) -> None:
        """
        Record (but never process) an image/video attachment.

        Callers may pass any of: metadata["media_type"] in {"image","video"},
        or boolean metadata["has_image"] / metadata["has_video"]. Whichever is
        set, we only flip a flag — no bytes are read, no OCR/frame-extraction
        is attempted. QR codes are unaffected: those arrive pre-decoded as
        metadata["qr_decoded_text"] from the phone's ML Kit scan, which is a
        completely separate path handled in _detect_qr().
        """
        media_type = str(metadata.get("media_type", "")).strip().lower()
        has_image = bool(metadata.get("has_image")) or media_type == "image"
        has_video = bool(metadata.get("has_video")) or media_type == "video"
        r.has_image_attachment = has_image
        r.has_video_attachment = has_video
        r.media_ignored = has_image or has_video

    def _detect_advanced_apk(self, text: str, r: ExtractionResult) -> None:
        """
        Enhanced APK detection: catches APK in any form — forwarded file blocks,
        bare filenames, mixed-language download+APK combos.
        Any APK reference in a message → has_apk_link=True → Hard Rule 1 fires.
        """
        if self._FORWARDED_APK_RE.search(text):
            r.has_apk_link = True
        if self._BARE_APK_FILENAME_RE.search(text):
            r.has_apk_link = True
        if self._DOWNLOAD_APK_COMBO_RE.search(text):
            r.has_apk_link = True

    def _detect_authority_callback_fraud(self, text: str, r: ExtractionResult) -> None:
        """
        Cross-language authority impersonation + callback fraud detection.
        Catches messages like: KYC expired (Urdu) + "call representative" (English).
        When KYC authority + callback together: authority_impersonation_score ≥ 0.85
        → Hard Rule 6 fires in inference.py.
        """
        has_kyc = bool(self._KYC_AUTHORITY_RE.search(text))
        has_implicit_callback = bool(self._IMPLICIT_CALLBACK_RE.search(text))

        if has_kyc:
            # KYC authority mention alone already raises suspicion
            r.authority_impersonation_score = max(r.authority_impersonation_score, 0.70)
        if has_kyc and (has_implicit_callback or r.has_callback_fraud):
            # KYC + callback instruction in any language = clear fraud
            r.has_callback_fraud = True
            r.authority_impersonation_score = max(r.authority_impersonation_score, 0.85)
        if r.authority_impersonation_score > 0.5 and r.has_url and (has_implicit_callback or r.has_callback_fraud):
            # Authority impersonation + URL + callback = fraud regardless of KYC keyword
            r.has_callback_fraud = True
            r.authority_impersonation_score = max(r.authority_impersonation_score, 0.75)

        # Generic financial-alert + callback fraud (no KYC wording, no phone number,
        # no URL needed): "INR X debited/account will be blocked ... call us back /
        # our representative / the number below". Bank fraud alerts commonly give a
        # real toll-free number proactively; phishing vaguely says "call back" /
        # "our representative" / "number below" — that vagueness is what
        # _IMPLICIT_CALLBACK_RE is anchored on, so this stays safe against genuine
        # transactional alerts that list an actual helpline number.
        if (not has_kyc and (has_implicit_callback or r.has_callback_fraud)
                and r.financial_term_count >= 1 and r.urgency_score > 0.15):
            r.has_callback_fraud = True
            r.authority_impersonation_score = max(r.authority_impersonation_score, 0.80)

    def _detect_benign_signals(self, text: str, r: ExtractionResult) -> None:
        """
        Anti-false-positive guard for conversational Urdu/Arabic messages.
        Sets is_likely_benign=True when:
          - No attack surface (URL, APK, QR, deep-link, callback-APK)
          - No strong spam content signals
          - Message is ≥55% Arabic/Urdu script characters
          OR is a short (≤100 chars) message with zero spam signals
        This prevents pure conversational Urdu text from being flagged as spam
        due to script-level model confusion.
        """
        # Never mark benign if any attack surface present
        if (r.has_url or r.has_apk_link or r.has_deep_link
                or r.has_qr_reference or r.has_qr_apk_payload
                or r.has_qr_upi_collect or r.has_callback_apk_prompt):
            return
        # Never mark benign if strong spam signals detected
        if (r.urgency_score > 0.35 or r.financial_lure_score > 0.3
                or r.financial_term_count > 2 or r.action_verb_count > 1
                or r.authority_impersonation_score > 0.35
                or r.has_callback_fraud or r.has_generic_salutation):
            return

        # Guard 1: Predominantly Arabic/Urdu script (U+0600–U+06FF, U+0750–U+077F)
        arabic_urdu = sum(
            1 for c in text
            if '\u0600' <= c <= '\u06FF' or '\u0750' <= c <= '\u077F'
        )
        alpha = sum(1 for c in text if c.isalpha())
        if alpha > 10 and arabic_urdu / alpha >= 0.55:
            r.is_likely_benign = True
            return

        # Guard 2: Very short message with absolutely no spam signals
        if (len(text.strip()) <= 100
                and r.urgency_score == 0.0 and r.financial_term_count == 0
                and r.action_verb_count == 0 and not r.has_url
                and r.authority_impersonation_score == 0.0):
            r.is_likely_benign = True
            return

        # Guard 3: Degenerate token repetition (e.g. "mi mi mi mi mi ...").
        # Rambling/glitchy human text repeats one word many times; this has no
        # relation to spam content and should never contribute to rule_confidence.
        # Language-agnostic (works for any Unicode script since it just tokenizes
        # on whitespace and dedupes case-insensitively).
        words = [w.strip('.,!?;:।॥') for w in text.split()]
        words = [w for w in words if w]
        if len(words) >= 10:
            counts: Dict[str, int] = {}
            for w in words:
                key = w.lower()
                counts[key] = counts.get(key, 0) + 1
            top_count = max(counts.values())
            if top_count >= 6 and (top_count / len(words)) >= 0.3:
                r.is_likely_benign = True

    def extract(self, text: str, metadata: Optional[Dict[str, str]] = None) -> ExtractionResult:
        """
        Extract dims 0-28. Dims 29-38 filled later by URLFeatureEnricher.

        Decision flow:
          1. URL/APK/QR text detection
          2. Enhanced APK detection (any form, any language)
          3. Urgency/financial/authority scoring (all language keyword banks)
          4. Cross-language KYC+callback authority fraud detection
          5. Benign FP guard (Urdu/Arabic conversational messages)
          6. Rule confidence computation
        Supports messages mixing 2-4+ languages: keyword banks for all 13
        Indic languages are checked on every message regardless of script.
        """
        r = ExtractionResult()
        metadata = metadata or {}
        self._detect_ignored_media(metadata, r)
        if not text or not text.strip():
            return r
        r.message_length = len(text)
        clean = self._ZERO_WIDTH_RE.sub('', text)
        if len(clean) != len(text):
            r.has_spelling_obfuscation = True

        self._extract_urls(clean, r)
        self._detect_deep_links(clean, r)
        if self._FILE_EXT_RE.search(clean):          # catches .apk anywhere in text
            r.has_apk_link = True
        self._detect_advanced_apk(clean, r)           # NEW: forwarded, bare filename, cross-lang
        self._check_low_rep_tld(r)
        self._detect_qr(clean, metadata, r)
        self._score_urgency(clean, r)
        self._score_action_verbs(clean, r)
        self._score_financial_terms(clean, r)
        self._detect_govt_impersonation(clean, r)
        self._detect_callback(clean, r)
        self._detect_authority_callback_fraud(clean, r)  # NEW: cross-lang KYC+callback
        self._detect_brand_impersonation(r)
        self._detect_formatting(clean, r)
        if self._OBFUSCATION_RE.search(clean):
            r.has_spelling_obfuscation = True
        self._score_reward_lure(clean, r)
        self._score_transactional(clean, r)
        self._score_affinity(clean, r)
        self._detect_campaigns(clean, r)
        if metadata.get('sender'):
            self._analyze_sender(clean, metadata, r)
        self._detect_benign_signals(clean, r)          # NEW: Urdu/Arabic FP guard (last)
        self._compute_rule_confidence(r)
        self._build_vector_list(r)
        return r

    def refresh_after_url_enrichment(self, result: ExtractionResult) -> None:
        """Recompute heuristic fields after URLFeatureEnricher updates flags."""
        self._compute_rule_confidence(result)
        self._build_vector_list(result)

    def _extract_urls(self, text, r):
        urls  = [self._strip_url(u) for u in self._URL_RE.findall(text)]
        bare  = [self._strip_url(u) for u in self._BARE_DOMAIN_RE.findall(text)]
        all_u = list(dict.fromkeys(urls + bare))
        r.extracted_urls = all_u; r.url_count = len(all_u); r.has_url = bool(all_u)
        for u in all_u:
            ul = u.lower()
            if any(s in ul for s in self._shorteners): r.has_shortened_url = True
            if self._FILE_EXT_RE.search(ul): r.has_apk_link = True

    @staticmethod
    def _strip_url(url: str) -> str:
        return url.rstrip('.,!?;:')

    def _detect_deep_links(self, text, r):
        m = self._DEEP_LINK_RE.findall(text)
        if m: r.has_deep_link = True; r.extracted_urls.extend(m)

    def _check_low_rep_tld(self, r):
        for url in r.extracted_urls:
            try:
                netloc = urllib.parse.urlparse(url if '://' in url else f'http://{url}').netloc.lower()
                domain = netloc.replace('www.','').split(':')[0]
                if any(domain.endswith(t) for t in self._LOW_REP_TLDS):
                    r.has_low_reputation_tld = True; return
            except: continue

    def _detect_qr(self, text, metadata, r):
        if self._QR_TEXT_RE.search(text): r.has_qr_reference = True
        for p in self._qr_pats:
            if p.search(text): r.has_qr_reference = True; break
        qr_text = metadata.get('qr_decoded_text','').strip()
        if not qr_text: return
        r.has_qr_reference = True
        qr_urls = [self._strip_url(u) for u in
                   self._URL_RE.findall(qr_text) + self._BARE_DOMAIN_RE.findall(qr_text)]
        if qr_urls:
            r.extracted_urls.extend(qr_urls)
            r.url_count += len(qr_urls)
            r.has_url = True
        if self._FILE_EXT_RE.search(qr_text):
            r.qr_payload_type = 'apk_url'; r.has_qr_apk_payload = True; r.has_apk_link = True
        elif self._UPI_COLLECT_RE.search(qr_text):
            r.qr_payload_type = 'upi_collect'; r.has_qr_upi_collect = True
        elif qr_urls:
            r.qr_payload_type = 'url'
            for u in qr_urls:
                if self._FILE_EXT_RE.search(u.lower()):
                    r.qr_payload_type = 'apk_url'; r.has_qr_apk_payload = True; r.has_apk_link = True
            self._check_low_rep_tld(r)
        else:
            r.qr_payload_type = 'text'

    def _score_urgency(self, text, r):
        hits = []
        for p in self._urgency_pats: hits.extend(p.findall(text))
        r.matched_keywords['urgency'] = list(set(hits))
        if hits: r.urgency_score = min(1.0, 1.0 - math.exp(-0.5 * len(hits)))

    def _score_action_verbs(self, text, r):
        hits = []
        for p in self._action_pats: hits.extend(p.findall(text))
        r.matched_keywords['action'] = list(set(hits)); r.action_verb_count = len(hits)

    def _score_financial_terms(self, text, r):
        hits = []
        for p in self._financial_pats: hits.extend(p.findall(text))
        r.matched_keywords['financial'] = list(set(hits)); r.financial_term_count = len(hits)

    def _detect_brand_impersonation(self, r):
        for url in r.extracted_urls:
            try:
                netloc = urllib.parse.urlparse(url if '://' in url else f'http://{url}').netloc.lower().replace('www.','').split(':')[0]
                dbase = netloc.split('.')[0]
                for brand in self._brands:
                    official = {f'{brand}.com',f'{brand}.in',f'{brand}.co.in',f'{brand}.org',f'{brand}.gov.in',f'{brand}.net'}
                    if netloc in official: continue
                    if brand in netloc:
                        r.has_suspicious_domain = True; r.matched_brands.append(brand)
                    elif len(brand)>3 and self._levenshtein(brand,dbase)<=2:
                        r.has_suspicious_domain = True; r.matched_brands.append(f'{brand}~{dbase}')
            except: continue
        r.matched_brands = list(dict.fromkeys(r.matched_brands))

    def _detect_govt_impersonation(self, text, r):
        if any(p.search(text) for p in self._govt_pats):
            if r.has_url or r.action_verb_count > 0:
                r.authority_impersonation_score = max(r.authority_impersonation_score, 0.7)

    def _detect_callback(self, text, r):
        phones = self._PHONE_RE.findall(text)
        r.callback_number_count = len(phones)
        has_call_verb = bool(self._CALLBACK_VERB_RE.search(text))
        if not has_call_verb:
            for p in self._callback_pats:
                if p.search(text): has_call_verb = True; break
        if self._CALLBACK_APK_RE.search(text):
            r.has_callback_apk_prompt = True; r.has_callback_fraud = True; return
        if has_call_verb and (phones or r.urgency_score > 0.1 or r.financial_term_count > 0):
            r.has_callback_fraud = True
        if has_call_verb and r.authority_impersonation_score > 0:
            r.has_callback_fraud = True

    def _detect_formatting(self, text, r):
        if self._SALUTATION_RE.search(text): r.has_generic_salutation = True
        alpha = [c for c in text if c.isalpha()]
        if len(alpha) > 10: r.excessive_caps_ratio = sum(1 for c in alpha if c.isupper())/len(alpha)
        if re.search(r'[!?]{3,}|\.{4,}', text): r.excessive_punctuation = True

    def _score_reward_lure(self, text, r):
        count = sum(1 for p in self._reward_pats if p.search(text))
        if count: r.reward_lottery_score = min(1.0, 0.4*count); r.financial_lure_score = max(r.financial_lure_score, r.reward_lottery_score)

    def _score_transactional(self, text, r):
        count = sum(1 for p in self._transact_pats if p.search(text))
        if count: r.transactional_update_score = min(1.0, 0.5*count)

    def _score_affinity(self, text, r):
        count = sum(1 for p in self._affinity_pats if p.search(text))
        if count: r.affinity_romance_score = min(1.0, 0.6*count)

    def _detect_campaigns(self, text, r):
        for name, pats in self._campaign_pats.items():
            if any(p.search(text) for p in pats):
                if name=='wedding': r.has_wedding_invitation_pattern=True
                elif name=='loan': r.has_loan_app_pattern=True
                elif name=='delivery': r.has_delivery_notification_pattern=True
                elif name=='govt_transport': r.has_govt_transport_pattern=True

    def _analyze_sender(self, text, metadata, r):
        s = re.sub(r'[\s\-\(\)]','', metadata.get('sender',''))
        if re.match(r'^\d{5,6}$',s): r.sender_is_shortcode=True
        elif re.match(r'^\+?\d{10,11}$',s): r.sender_is_standard_number=True
        elif re.match(r'^\+?\d{12,15}$',s): r.sender_is_international=True
        elif '@' in s: r.sender_is_email_gateway=True
        elif re.match(r'^[A-Z]{2}-[A-Z0-9]+$',s,re.I): r.sender_is_alphanumeric=True
        r.sender_has_opt_out=bool(re.search(r'reply\s+stop|opt[\s-]*out|unsubscribe',text,re.I))

    def _compute_rule_confidence(self, r):
        # APK/QR/callback-APK → always certain spam
        if r.has_apk_link or r.has_qr_apk_payload or r.has_callback_apk_prompt:
            r.rule_confidence = 1.0; r.rule_based_spam = True; return

        # Anti-FP guard: conversational messages get zero confidence
        if r.is_likely_benign:
            r.rule_confidence = 0.0
            r.rule_based_spam = False
            return

        score = 0.0
        if r.has_deep_link:          score += 0.75
        if r.has_suspicious_domain:  score += 0.35
        if r.has_low_reputation_tld: score += 0.30
        if r.has_shortened_url:      score += 0.25
        if r.has_spelling_obfuscation: score += 0.20
        if r.has_qr_upi_collect:     score += 0.60
        # QR reference is one of the project's core attack vectors even when no
        # URL is present in the SMS text itself — the payload lives inside the
        # QR image (decoded separately by ML Kit / qr_decoded_text metadata),
        # so "scan the attached QR to verify/prevent suspension" text alone is
        # already a strong signal, not just QR-combined-with-a-visible-link.
        if r.has_qr_reference:       score += 0.30
        if r.has_qr_reference and r.has_url: score += 0.10
        if r.has_callback_fraud:     score += 0.45
        score += 0.15 * r.urgency_score
        if r.action_verb_count > 0 and r.has_url: score += 0.20
        if r.has_generic_salutation: score += 0.10
        if r.excessive_caps_ratio > 0.5: score += 0.10
        score += 0.05 * min(r.financial_term_count, 3)
        score += 0.20 * r.financial_lure_score
        if r.transactional_update_score > 0 and r.has_url: score += 0.20 * r.transactional_update_score
        score += 0.25 * r.authority_impersonation_score
        score += 0.20 * r.affinity_romance_score
        if r.sender_is_international: score += 0.25
        if r.sender_is_email_gateway: score += 0.20
        if r.has_wedding_invitation_pattern and r.has_url: score += 0.40
        if r.has_loan_app_pattern and r.has_url: score += 0.30
        if r.has_govt_transport_pattern and r.has_url: score += 0.30
        if r.has_delivery_notification_pattern and r.has_url: score += 0.25
        if r.has_shortened_url and r.urgency_score > 0.2 and r.action_verb_count > 0: score += 0.25
        if r.financial_term_count > 0 and r.action_verb_count > 0 and r.has_url: score += 0.15
        if r.sender_has_opt_out: score -= 0.10
        r.rule_confidence = max(0.0, min(1.0, score))
        r.rule_based_spam = r.rule_confidence >= 0.50

    def _build_vector_list(self, r):
        v = []
        if r.has_apk_link:          v.append('direct_apk_download')
        if r.has_qr_apk_payload:    v.append('qr_code_apk_download')
        if r.has_qr_upi_collect:    v.append('qr_upi_collect_scam')
        if r.has_qr_reference:      v.append('qr_code_sideloading')
        if r.has_callback_fraud:    v.append('callback_vishing_fraud')
        if r.has_callback_apk_prompt: v.append('callback_apk_install_prompt')
        if r.has_shortened_url:     v.append('shortened_url_redirect')
        if r.has_suspicious_domain: v.append('brand_impersonation_domain')
        if r.has_low_reputation_tld: v.append('low_reputation_tld')
        if r.has_deep_link:         v.append('deep_link_intent_uri')
        if r.has_spelling_obfuscation: v.append('text_obfuscation_evasion')
        if r.action_verb_count > 0 and r.has_url: v.append('app_download_prompt')
        if r.urgency_score > 0.3 and r.has_url: v.append('urgency_with_link')
        if r.financial_lure_score > 0: v.append('financial_reward_lure')
        if r.transactional_update_score > 0 and r.has_url: v.append('fake_transactional_update')
        if r.authority_impersonation_score > 0: v.append('authority_impersonation')
        if r.has_wedding_invitation_pattern: v.append('campaign_wedding_invitation_apk')
        if r.has_loan_app_pattern: v.append('campaign_predatory_loan_app')
        if r.has_delivery_notification_pattern: v.append('campaign_fake_delivery')
        if r.has_govt_transport_pattern: v.append('campaign_govt_transport_challan')
        if r.sender_is_international: v.append('international_routing_anomaly')
        if r.sender_is_email_gateway: v.append('email_to_sms_gateway')
        r.sideloading_vectors = v

    @staticmethod
    def _levenshtein(s1, s2):
        if len(s1)<len(s2): return FeatureExtractor._levenshtein(s2,s1)
        if not s2: return len(s1)
        prev = range(len(s2)+1)
        for i,c1 in enumerate(s1):
            curr=[i+1]
            for j,c2 in enumerate(s2):
                curr.append(min(prev[j+1]+1,curr[j]+1,prev[j]+(c1!=c2)))
            prev=curr
        return prev[-1]


def load_feature_extractor(config_path: str) -> FeatureExtractor:
    import yaml
    with open(config_path,'r',encoding='utf-8') as f:
        cfg = yaml.safe_load(f)
    return FeatureExtractor(cfg)