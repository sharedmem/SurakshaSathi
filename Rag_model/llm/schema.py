"""The strict JSON schema passed to Groq's structured-output mode. Mirrors
common.schemas.ThreatReport field-for-field -- keep them in sync; this dict is what actually gets
sent over the wire, the pydantic model is what the rest of the codebase imports/type-checks
against.
"""

THREAT_REPORT_SCHEMA = {
    "name": "phishing_threat_report",
    "strict": True,
    "schema": {
        "type": "object",
        "properties": {
            "verdict": {"type": "string", "enum": ["BLOCK", "QUARANTINE", "ALLOW"]},
            "threat_type": {"type": "string"},
            "risk_score": {"type": "number", "minimum": 0, "maximum": 100},
            "suspicious_signals": {"type": "array", "items": {"type": "string"}},
            "technical_evidence": {
                "type": "object",
                "properties": {
                    "resolved_destination": {"type": ["string", "null"]},
                    "domain_creation_age": {"type": ["string", "null"]},
                    "quishing_anomaly_detected": {"type": "boolean"},
                    "callback_number_verified": {"type": "boolean"},
                    "domain_age_days": {"type": ["integer", "null"]},
                    "is_pwa": {"type": "boolean"},
                    "pwa_name_spoofing_suspected": {"type": "boolean"},
                },
                "required": [
                    "resolved_destination",
                    "domain_creation_age",
                    "quishing_anomaly_detected",
                    "callback_number_verified",
                    "domain_age_days",
                    "is_pwa",
                    "pwa_name_spoofing_suspected",
                ],
                "additionalProperties": False,
            },
            "plain_language_explanation": {"type": "string"},
            "recommended_action": {"type": "string"},
            "pattern_matched": {"type": "string"},
            "micro_lesson": {"type": "string"},
        },
        "required": [
            "verdict",
            "threat_type",
            "risk_score",
            "suspicious_signals",
            "technical_evidence",
            "plain_language_explanation",
            "recommended_action",
            "pattern_matched",
            "micro_lesson",
        ],
        "additionalProperties": False,
    },
}
