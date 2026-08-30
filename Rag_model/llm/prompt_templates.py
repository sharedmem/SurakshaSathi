"""Two prompt shapes for the same Groq model + schema: Output 1 (per-message, single user) and
Output 2 (alerting, multi-user/regional). Both render into the same THREAT_REPORT_SCHEMA -- the
difference is what context gets assembled into the prompt, not the output contract.
"""

SYSTEM_PROMPT = """You are SurakshaSathi's fraud-analysis engine, protecting Indian bank
customers from SMS/WhatsApp/Telegram phishing, fake banking APKs, quishing (malicious QR codes),
and vishing (fraudulent callback numbers). You are given: the suspect message (translated to
English), structural detection results from specialized analysis engines (APK/URL/QR/callback),
retrieved fraud-education reference documents, and optionally live web-search corroboration.

Rules:
- Ground every claim in the technical evidence provided. Do not invent domain ages, permissions,
  or verdicts not present in the input.
- If evidence is contradictory (e.g. a verified signature but malicious structural code), say so
  explicitly in suspicious_signals and recommend QUARANTINE, not a confident ALLOW/BLOCK.
- plain_language_explanation must be understandable by a non-technical bank customer.
- recommended_action must be concrete and actionable (what to do right now), not generic advice.
- pattern_matched: a short label (under 10 words) naming the specific fraud pattern this message
  matches (e.g. "fake KYC-block urgency + non-bank link"), drawn from the retrieved fraud-education
  reference. Empty string only if genuinely nothing matches (verdict ALLOW).
- micro_lesson: a real 1-1.5 paragraph explanation for the "adaptive friction" education screen the
  user sees before they're allowed to proceed past this warning. It must cover, as connected prose
  (not a bulleted list): (1) which specific fraud pattern this is and why it works psychologically
  on people, (2) one concrete, verifiable fact from the retrieved reference docs that debunks the
  message's claim, (3) what the user should do instead right now. Ground it in the retrieved
  fraud-education reference the same way as everything else -- do not invent facts. If a USER
  PROFILE section is present below, write plain_language_explanation, recommended_action, and
  micro_lesson in language suited to that profile (e.g. senior_citizen/homemaker: short sentences,
  no jargon, explicit step-by-step; student/salaried_professional: can be more concise/direct).
"""

_PERSONA_TONE_HINTS: dict[str, str] = {
    "senior_citizen": "an older adult who may be less familiar with smartphones -- use short "
    "sentences, avoid technical jargon, spell out each step explicitly, and be extra reassuring "
    "rather than alarming.",
    "homemaker": "someone managing household finances who may not be a frequent digital-banking "
    "user -- use short sentences, avoid technical jargon, spell out each step explicitly.",
    "student": "a student who is digitally comfortable -- concise, direct language is fine.",
    "salaried_professional": "a working professional who is digitally comfortable -- concise, "
    "direct language is fine.",
    "business_owner": "a business owner handling frequent transactions -- concise, direct "
    "language is fine, but flag anything that could affect business payments clearly.",
    "general": "a general audience -- clear, simple language, avoid jargon.",
}


def build_per_message_prompt(
    query_message_english: str,
    detection_results: dict,
    retrieved_docs: list[str],
    web_corroboration: str | None,
    ml_model_metadata: dict | None,
    user_persona: str | None = None,
) -> str:
    """Output 1: single-user warning. `web_corroboration` may be None if the live search timed
    out (per the <1.5s budget) -- the prompt must still work without it. `user_persona` is the
    Android client's already-collected onboarding persona (see clustering/persona.py for the fixed
    category set) -- previously captured but never actually sent to this endpoint, so every user
    got the same generic tone regardless of profile."""
    sections = [f"SUSPECT MESSAGE (English):\n{query_message_english}"]

    if ml_model_metadata:
        sections.append(f"ON-DEVICE ML CLASSIFIER METADATA:\n{ml_model_metadata}")

    sections.append(f"STRUCTURAL DETECTION RESULTS:\n{detection_results}")

    if retrieved_docs:
        joined_docs = "\n---\n".join(retrieved_docs)
        sections.append(f"RETRIEVED FRAUD-EDUCATION REFERENCE:\n{joined_docs}")

    if web_corroboration:
        sections.append(f"LIVE WEB CORROBORATION (best-effort, may be partial):\n{web_corroboration}")
    else:
        sections.append("LIVE WEB CORROBORATION: not available (timed out or no relevant hit).")

    tone_hint = _PERSONA_TONE_HINTS.get(user_persona or "")
    if tone_hint:
        sections.append(f"USER PROFILE:\nWriting for {tone_hint}")

    return "\n\n".join(sections)


def build_alerting_prompt(
    macro_cluster_sample_message: str,
    fraud_type: str,
    region: str,
    message_count_last_24h: int,
    retrieved_docs: list[str],
) -> str:
    """Output 2: regional/multi-user alert, generated once a macro-cluster crosses its
    threshold in the daily 7am job."""
    sections = [
        f"EMERGING FRAUD CAMPAIGN DETECTED\nRegion: {region}\nFraud type: {fraud_type}\n"
        f"Messages matching this pattern in the last 24h: {message_count_last_24h}",
        f"REPRESENTATIVE SAMPLE MESSAGE:\n{macro_cluster_sample_message}",
    ]
    if retrieved_docs:
        joined_docs = "\n---\n".join(retrieved_docs)
        sections.append(f"RETRIEVED FRAUD-EDUCATION REFERENCE:\n{joined_docs}")
    sections.append(
        "Generate a regional alert (not addressed to one individual) warning users in this "
        "region about this specific, currently-active campaign."
    )
    return "\n\n".join(sections)
