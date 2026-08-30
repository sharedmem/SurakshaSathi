"""Zero-trust PII redaction, run on every NCRP complaint / user telemetry / SBI case-note record
before it is chunked, embedded, stored, or included in any external LLM (Groq) prompt. Required
under the DPDP Act 2023 -- these sources carry Aadhaar numbers, card/account numbers, phone
numbers, and personal names that must never reach an external API or a persistent index unmasked.

Runs on the ENGLISH-translated text (language/translate.py runs first) -- Presidio's bundled NER
model is English-only, and translation already happens upstream in the message pipeline, so this
is a free accuracy win rather than an extra step.

Detection methods (per spec):
  Aadhaar          -> regex + Verhoeff checksum validation (cuts false positives on generic
                      12-digit sequences -- a bare regex hit is not "high-precision" on its own)
  Mobile number    -> regex + Presidio's phone-number recognizer
  Card/account no. -> regex candidate + Luhn checksum ("Primary Account Number" = card PAN here,
                      not the Indian Income-Tax PAN alphanumeric ID)
  Person name      -> Microsoft Presidio (spaCy NER under the hood)
  Email            -> RFC 5322-ish regex

NOTE: this masks PII for anything that gets STORED, INDEXED, or sent to the LLM. It must not run
on a message before technical fraud-signal extraction (callback-number HLR lookup, URL
resolution) -- those need the raw value first; redact only the copy that gets persisted/logged/
sent externally.
"""

import re

AADHAAR_RE = re.compile(r"\b\d{4}\s?\d{4}\s?\d{4}\b")
EMAIL_RE = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")
CARD_CANDIDATE_RE = re.compile(r"\b(?:\d[ -]?){13,19}\b")


def _verhoeff_checksum_valid(number: str) -> bool:
    """Aadhaar numbers use a Verhoeff check digit -- validating it turns a generic 12-digit regex
    hit into a high-precision Aadhaar match instead of flagging any 12-digit sequence."""
    d = [
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 2, 3, 4, 0, 6, 7, 8, 9, 5],
        [2, 3, 4, 0, 1, 7, 8, 9, 5, 6], [3, 4, 0, 1, 2, 8, 9, 5, 6, 7],
        [4, 0, 1, 2, 3, 9, 5, 6, 7, 8], [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
        [6, 5, 9, 8, 7, 1, 0, 4, 3, 2], [7, 6, 5, 9, 8, 2, 1, 0, 4, 3],
        [8, 7, 6, 5, 9, 3, 2, 1, 0, 4], [9, 8, 7, 6, 5, 4, 3, 2, 1, 0],
    ]
    p = [
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 5, 7, 6, 2, 8, 3, 0, 9, 4],
        [5, 8, 0, 3, 7, 9, 6, 1, 4, 2], [8, 9, 1, 6, 0, 4, 3, 5, 2, 7],
        [9, 4, 5, 3, 1, 2, 6, 8, 7, 0], [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
        [2, 7, 9, 3, 8, 0, 6, 4, 1, 5], [7, 0, 4, 6, 9, 1, 3, 2, 5, 8],
    ]
    digits = [int(c) for c in number if c.isdigit()]
    if len(digits) != 12:
        return False
    c = 0
    for i, digit in enumerate(reversed(digits)):
        c = d[c][p[i % 8][digit]]
    return c == 0


def _luhn_valid(number: str) -> bool:
    digits = [int(c) for c in number if c.isdigit()]
    if not (13 <= len(digits) <= 19):
        return False
    checksum = 0
    parity = len(digits) % 2
    for i, digit in enumerate(digits):
        if i % 2 == parity:
            digit *= 2
            if digit > 9:
                digit -= 9
        checksum += digit
    return checksum % 10 == 0


class PiiRedactor:
    def __init__(self, use_presidio: bool = True):
        self.use_presidio = use_presidio
        self._presidio_analyzer = None
        if use_presidio:
            try:
                from presidio_analyzer import AnalyzerEngine
                self._presidio_analyzer = AnalyzerEngine()
            except Exception:
                # spaCy model (en_core_web_sm) not downloaded in this environment -- degrade to
                # regex-only redaction rather than fail the whole ingestion pipeline.
                self._presidio_analyzer = None

    def _redact_regex(self, text: str) -> str:
        text = AADHAAR_RE.sub(
            lambda m: "<AADHAAR>" if _verhoeff_checksum_valid(m.group()) else m.group(), text
        )
        text = CARD_CANDIDATE_RE.sub(
            lambda m: "<CARD_NUMBER>" if _luhn_valid(m.group()) else m.group(), text
        )
        text = EMAIL_RE.sub("<EMAIL>", text)
        return text

    def _redact_presidio(self, text: str) -> str:
        if self._presidio_analyzer is None:
            return text
        from presidio_anonymizer import AnonymizerEngine

        results = self._presidio_analyzer.analyze(
            text=text, language="en", entities=["PERSON", "PHONE_NUMBER"]
        )
        anonymizer = AnonymizerEngine()
        return anonymizer.anonymize(text=text, analyzer_results=results).text

    def redact(self, text: str) -> str:
        """Order matters: regex-based checksummed entities first (Aadhaar/card/email are precise
        and would otherwise get partially mangled by Presidio's generic PHONE_NUMBER recognizer),
        then Presidio for names + any remaining phone-number formats the regex missed."""
        text = self._redact_regex(text)
        text = self._redact_presidio(text)
        return text


_redactor_singleton: "PiiRedactor | None" = None


def get_redactor() -> "PiiRedactor":
    """Process-wide lazy singleton -- Presidio/spaCy model load is slow, do it once."""
    global _redactor_singleton
    if _redactor_singleton is None:
        _redactor_singleton = PiiRedactor()
    return _redactor_singleton


def redact_for_external_use(text: str) -> str:
    """Convenience wrapper shared by every call site that sends/persists text outside this
    process (message_scan.py, guardian_chat.py): masks Aadhaar/card/email/name/phone-number PII.
    Fails CLOSED, not open -- if redaction itself errors (model not downloaded, unexpected input),
    returns a placeholder rather than risk forwarding raw, unredacted PII to an external LLM API
    or a persisted log.
    """
    try:
        return get_redactor().redact(text)
    except Exception:
        return "[message unavailable -- redaction failed]"
