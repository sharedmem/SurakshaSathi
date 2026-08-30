"""Groq API wrapper (Llama-3.3-70B) with JSON structured output. Both Output 1 (per-message) and
Output 2 (alerting) go through this single function -- only the prompt differs.

NOTE: Groq's `json_schema` strict structured-output mode is only supported on a handful of
models (see https://console.groq.com/docs/structured-outputs#supported-models) -- llama-3.3-70b-
versatile is NOT one of them (confirmed by a live 400 from the API: "This model does not support
response format `json_schema`"). Falls back to `json_object` mode (guarantees valid JSON syntax,
not schema conformance) with the schema spelled out in the system prompt, then validates with
pydantic and retries once on a validation failure -- the practical equivalent for models without
native schema enforcement.
"""

import json

from groq import Groq
from pydantic import ValidationError

from common.resilience import with_retry
from common.schemas import ThreatReport
from config.settings import settings
from llm.prompt_templates import SYSTEM_PROMPT
from llm.schema import THREAT_REPORT_SCHEMA

_client: Groq | None = None

SCHEMA_INSTRUCTION = (
    "\n\nRespond with ONLY a JSON object matching exactly this schema (no prose, no markdown "
    f"fences):\n{json.dumps(THREAT_REPORT_SCHEMA['schema'], indent=2)}"
)


def _get_client() -> Groq:
    global _client
    if _client is None:
        if not settings.groq_api_key:
            raise RuntimeError("GROQ_API_KEY not set -- see .env.example")
        _client = Groq(api_key=settings.groq_api_key)
    return _client


@with_retry("groq", exceptions=(Exception,))
def _call_groq(user_prompt: str) -> str:
    client = _get_client()
    completion = client.chat.completions.create(
        model=settings.groq_model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT + SCHEMA_INSTRUCTION},
            {"role": "user", "content": user_prompt},
        ],
        response_format={"type": "json_object"},
        temperature=0.2,
    )
    return completion.choices[0].message.content


def generate_threat_report(user_prompt: str) -> ThreatReport:
    raw_json = _call_groq(user_prompt)
    try:
        return ThreatReport.model_validate(json.loads(raw_json))
    except (json.JSONDecodeError, ValidationError):
        # one retry: the model occasionally drifts from the schema in json_object mode (no
        # native enforcement) -- a second attempt is cheap and usually self-corrects
        raw_json = _call_groq(user_prompt)
        return ThreatReport.model_validate(json.loads(raw_json))


GUARDIAN_SYSTEM_PROMPT = """You are SurakshaSathi's Guardian -- a calm, patient assistant helping
an Indian bank customer who has already been shown a fraud warning about a specific message (the
verdict/evidence for it is given to you below) and is now asking follow-up questions before
deciding what to do. This is a live conversation, not a report -- reply like a helpful person, not
a form.

Rules:
- Never ask the user for their OTP, PIN, password, card number, CVV, or any other credential --
  not even "to verify" who they are. You already have everything you need below.
- Keep replies short: 2-4 sentences, plain language, no jargon.
- You already know why this message was flagged -- don't ask the user to re-explain it, help them
  decide what to do next.
- If the user seems set on proceeding anyway (clicking the link, calling the number, sharing
  info), gently steer them toward the app's "Report to Cybercrime" option instead of trying to
  physically stop them -- you cannot take actions on the user's device, only advise.
- If asked something unrelated to this message/fraud in general, politely redirect back to
  helping with this specific warning.
"""


@with_retry("groq", exceptions=(Exception,))
def generate_guardian_reply(system_context: str, turns: list[dict]) -> str:
    """One conversational (non-structured) Groq call for the adaptive-friction Layer C "Guardian
    AI" chat. Deliberately plain-text, not the strict-schema path `generate_threat_report` uses --
    this is a live back-and-forth, not a verdict to render into fixed UI slots."""
    client = _get_client()
    messages = [{"role": "system", "content": GUARDIAN_SYSTEM_PROMPT + "\n\n" + system_context}] + turns
    completion = client.chat.completions.create(
        model=settings.groq_model,
        messages=messages,
        temperature=0.4,
        max_tokens=300,
    )
    return completion.choices[0].message.content
