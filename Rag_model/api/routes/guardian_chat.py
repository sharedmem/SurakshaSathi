"""POST /v1/scan/message/{message_id}/guardian_chat -- Layer C "Guardian AI" of the Android
client's adaptive friction engine (see SurakshaSathi's feature/messagefriction). This is reached
only after the user has already cleared the Intent Confirmation (Layer A) and Micro-Education
(Layer B) friction screens and explicitly opted into talking to the Guardian instead of (or before)
the Safe Simulation preview -- it is the one part of the whole friction engine with a genuine
incremental Groq cost per use, so it is kept bounded on two independent axes: opt-in (never
triggered automatically) and hard-capped at MAX_TURNS exchanges per conversation.

Stateless by design: the client resends the already-fetched ThreatReport as `report_context` on
every turn (it already has it from the original `/v1/scan/message` call) instead of this route
depending on a server-side session store or a Postgres lookup by message_id -- one less moving
part, and consistent with the rest of this backend's fire-and-forget persistence model.
"""

import asyncio

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from common.schemas import ThreatReport
from llm.groq_client import generate_guardian_reply
from privacy.pii_redaction import redact_for_external_use

router = APIRouter()

MAX_TURNS = 6


class ChatTurn(BaseModel):
    role: str  # "user" | "assistant"
    content: str


class GuardianChatRequest(BaseModel):
    turn_index: int = Field(ge=0)  # 0-based index of THIS turn -- client's own running counter
    user_message: str
    report_context: ThreatReport
    # Prior turns in this conversation, oldest first -- NOT persisted server-side, the client
    # carries its own transcript so this route can stay stateless.
    history: list[ChatTurn] = Field(default_factory=list)


class GuardianChatResponse(BaseModel):
    reply: str
    turns_remaining: int


@router.post("/v1/scan/message/{message_id}/guardian_chat", response_model=GuardianChatResponse)
async def guardian_chat(message_id: str, req: GuardianChatRequest) -> GuardianChatResponse:
    if req.turn_index >= MAX_TURNS:
        raise HTTPException(
            status_code=429,
            detail="Conversation limit reached for this message. Use 'Report to Cybercrime' if you're still unsure.",
        )

    # Same privacy discipline as the original scan: nothing the user types reaches the LLM
    # unredacted (Aadhaar/card/email/name/phone masked first).
    user_message_redacted = await asyncio.to_thread(redact_for_external_use, req.user_message)

    report = req.report_context
    system_context = (
        f"MESSAGE VERDICT: {report.verdict.value} ({report.threat_type or 'unspecified'})\n"
        f"RISK SCORE: {report.risk_score:.0f}/100\n"
        f"WHY IT WAS FLAGGED: {report.plain_language_explanation}\n"
        f"SUSPICIOUS SIGNALS: {', '.join(report.suspicious_signals) or 'none listed'}\n"
        f"FRAUD PATTERN: {report.pattern_matched or 'unspecified'}"
    )

    # Cap history sent back to the model too -- MAX_TURNS exchanges is at most MAX_TURNS*2
    # messages; a malformed/oversized client-supplied history must not blow up the prompt.
    history_messages = [{"role": t.role, "content": t.content} for t in req.history[-(MAX_TURNS * 2) :]]
    turns = history_messages + [{"role": "user", "content": user_message_redacted}]

    reply = await asyncio.to_thread(generate_guardian_reply, system_context, turns)

    return GuardianChatResponse(reply=reply, turns_remaining=max(0, MAX_TURNS - req.turn_index - 1))
