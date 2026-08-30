"""POST /v1/scan/message -- Output 1: the endpoint the Android app calls once its on-device
classifier flags an SMS/WhatsApp/Telegram message as spam. Runs L1 triage, the URL/QR/callback/
file L2 lane relevant to what the message (and any attachment) contains, a low-latency web-search
corroboration in parallel, retrieves guideline docs, and returns a ThreatReport for immediate
display to the user.

Request is multipart/form-data, not JSON: the message fields ride as Form fields, and an optional
single `attachment` (QR/image/video/APK-or-other-file) rides alongside them in the same request so
the whole report is generated from one round trip. `attachment_type` tells this handler which L2
lane to route the attachment through -- content bytes are never written to Blue DB or disk beyond
a scratch temp file for APK static analysis (cleaned up before the handler returns); only derived
analysis results (decoded QR payload, APK verdict, file metadata) reach the LLM prompt / Postgres.
Image and video attachments have no content-level analyzer wired up (no CV/video model exists
yet) -- they're accepted without error and noted to the LLM as unanalyzed, not silently dropped.

Storing the message + assigning it to a cluster (Blue DB write, DenStream routing) happens here
too, but AFTER the response is prepared -- a slow Postgres write must never add to the user-facing
latency budget captured in the response.
"""

import asyncio
import json
import time
from dataclasses import dataclass

from fastapi import APIRouter, File, Form, UploadFile
from pydantic import BaseModel

from common.schemas import ContentType, ThreatReport
from config.settings import settings
from llm.groq_client import generate_threat_report
from llm.prompt_templates import build_per_message_prompt
from privacy.pii_redaction import redact_for_external_use
from url_qr_callback_engine.l1_triage import triage

router = APIRouter()

# What the Android client can put in `attachment_type`. "qr" and "file" get real structural
# analysis and are forwarded to the RAG prompt; "image"/"video" are accepted but not analyzed.
ANALYZED_ATTACHMENT_TYPES = {"qr", "file"}
UNANALYZED_ATTACHMENT_TYPES = {"image", "video"}


@dataclass
class MessageScanRequest:
    message_id: str
    original_message: str
    language: str | None = None          # None -> detect_language() runs
    ml_model_metadata: dict | None = None  # from the Android on-device classifier, online-only
    has_image_attachment: bool = False
    callback_number: str | None = None
    # Onboarding persona the Android client already collects (clustering/persona.py's fixed
    # category set) but previously never sent here -- lets the LLM tailor tone (see
    # llm.prompt_templates._PERSONA_TONE_HINTS) for e.g. senior_citizen without a second call.
    user_persona: str | None = None
    # Phone number of the registered user reporting this message (api/routes/user_registration.py)
    # -- the recipient, NOT the fraud message's spoofed sender. Threaded through to persistence
    # only; not used in the LLM prompt.
    user_phone: str | None = None


class MessageScanResponse(BaseModel):
    report: ThreatReport
    latency_ms: int


async def _live_web_corroboration(query: str) -> str | None:
    """Budget-bounded: returns None on timeout rather than blocking the response. Uses Tavily
    (see docs/CREDENTIALS.md for why it was chosen over Google Custom Search for this budget)."""
    import httpx

    async def _search():
        async with httpx.AsyncClient(timeout=settings.web_search_timeout_ms / 1000) as client:
            resp = await client.post(
                "https://api.tavily.com/search",
                json={"api_key": settings.tavily_api_key, "query": query, "max_results": 3},
            )
            resp.raise_for_status()
            results = resp.json().get("results", [])
            return "\n".join(f"- {r['title']}: {r['content'][:200]}" for r in results[:3])

    try:
        return await asyncio.wait_for(_search(), timeout=settings.web_search_timeout_ms / 1000)
    except Exception:
        return None  # timeout or API failure -- corroboration is supplementary, not blocking


async def _analyze_url_safe(url: str) -> dict:
    from url_qr_callback_engine.url_pipeline import analyze_url

    try:
        result = await asyncio.to_thread(analyze_url, url)
        return result.__dict__
    except Exception as e:
        # A single L2 lane failing must not take down the whole scan -- the LLM still gets
        # everything else, plus an explicit note that this signal is missing.
        return {"error": f"analysis failed: {e}"}


async def _analyze_callback_safe(sender_id: str, callback_number: str, message_text: str) -> dict:
    from url_qr_callback_engine.callback_pipeline import analyze_callback

    try:
        result = await asyncio.to_thread(analyze_callback, sender_id, callback_number, message_text)
        return result.__dict__
    except Exception as e:
        return {"error": f"analysis failed: {e}"}


async def _analyze_qr_safe(image_bytes: bytes) -> dict:
    """L2 Path B. Decodes in-memory (numpy array), never written to disk or Blue DB -- only the
    decoded payload + structural signal reach the LLM prompt."""
    try:
        import cv2
        import numpy as np

        from url_qr_callback_engine.qr_pipeline import analyze_qr_image

        arr = np.frombuffer(image_bytes, dtype=np.uint8)
        image = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if image is None:
            return {"error": "could not decode attachment as an image"}
        result = await asyncio.to_thread(analyze_qr_image, image)
        return {
            "decoded_payload": result.decoded_payload,
            "xgboost_quishing_score": result.xgboost_quishing_score,
        }
    except Exception as e:
        return {"error": f"qr analysis failed: {e}"}


async def _analyze_apk_bytes_safe(content: bytes, package_name: str, cert_sha256: str) -> dict:
    """Reuses the same Lane A (identity/cloud) + Lane B (structural) + fusion pipeline as the
    dedicated /v1/scan/apk endpoint, but inline as part of a message scan -- so an APK forwarded
    alongside a suspicious message gets folded into the same report instead of a second round
    trip. Written to a scratch temp file only because androguard needs a filesystem path; deleted
    before this returns, never persisted."""
    import hashlib
    import tempfile
    from pathlib import Path

    from apk_engine.fusion_core import fuse
    from apk_engine.lane_a_signature import lane_a_verdict
    from apk_engine.lane_b_binctx import lane_b_analyze

    try:
        apk_file_sha256 = hashlib.sha256(content).hexdigest()
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as tmp:
            tmp.write(content)
            tmp_path = tmp.name
        try:
            loop = asyncio.get_event_loop()
            lane_a_task = loop.run_in_executor(None, lane_a_verdict, package_name, cert_sha256, apk_file_sha256)
            lane_b_task = loop.run_in_executor(None, lane_b_analyze, tmp_path)
            fusion_result = await fuse(lane_a_task, lane_b_task)
            lane_b_result = lane_b_task.result() if lane_b_task.done() else None
            return {
                "verdict": fusion_result.action,
                "reason": fusion_result.reason,
                "lane_a_verdict": lane_a_task.result(),
                "triad_permissions": list(lane_b_result.triad_permissions_found) if lane_b_result else [],
            }
        finally:
            Path(tmp_path).unlink(missing_ok=True)
    except Exception as e:
        return {"error": f"apk analysis failed: {e}"}


def _is_apk(filename: str | None, content_type_header: str | None) -> bool:
    return bool(
        (filename and filename.lower().endswith(".apk"))
        or content_type_header == "application/vnd.android.package-archive"
    )


async def _analyze_file_attachment(
    content: bytes, filename: str | None, content_type_header: str | None, package_name: str, cert_sha256: str
) -> tuple[dict, bool]:
    """`attachment_type == "file"`: routes to the APK lanes if it looks like an APK, otherwise
    there's no dedicated structural analyzer for arbitrary file types yet -- returns metadata only
    so the LLM at least knows a file was attached, rather than the attachment vanishing silently.
    Returns (analysis_dict, is_apk)."""
    if _is_apk(filename, content_type_header):
        return await _analyze_apk_bytes_safe(content, package_name, cert_sha256), True
    return (
        {
            "filename": filename,
            "content_type": content_type_header,
            "size_bytes": len(content),
            "note": "generic file attachment -- no dedicated structural analyzer wired for this file type yet",
        },
        False,
    )


def _note_unanalyzed_media(attachment_type: str, filename: str | None, content_type_header: str | None, size: int) -> dict:
    return {
        "attachment_type": attachment_type,
        "filename": filename,
        "content_type": content_type_header,
        "size_bytes": size,
        "note": f"{attachment_type} attachment received but not content-analyzed (no CV/video "
        "model wired up) -- not stored, flagged here only for transparency",
    }


async def _localize_report(report: ThreatReport, language: str) -> ThreatReport:
    """Translates the LLM's (always-English) explanation/action text into the user's selected
    language via a second Google Translate pass -- see language/translate.translate_from_english
    for why this isn't a prompt instruction to the LLM instead. Never fails the request: on
    translation failure (no API key, quota, network) the English text is kept and `language` stays
    "en", same graceful-degradation shape as the rest of this handler."""
    if language == "en":
        return report

    from language.translate import translate_from_english

    try:
        explanation, action, micro_lesson = await asyncio.gather(
            asyncio.to_thread(translate_from_english, report.plain_language_explanation, language),
            asyncio.to_thread(translate_from_english, report.recommended_action, language),
            # micro_lesson is the adaptive-friction Layer B education text -- it needs the same
            # localization pass as the other two user-facing fields, or non-English users would
            # see a full paragraph of English dropped into an otherwise-localized screen.
            asyncio.to_thread(translate_from_english, report.micro_lesson, language)
            if report.micro_lesson
            else asyncio.sleep(0, result=""),
        )
        return report.model_copy(
            update={
                "plain_language_explanation": explanation,
                "recommended_action": action,
                "micro_lesson": micro_lesson or report.micro_lesson,
                "language": language,
            }
        )
    except Exception as e:
        print(f"_localize_report failed for language={language!r}, keeping English text: {e}")
        return report


@router.post("/v1/scan/message", response_model=MessageScanResponse)
async def scan_message(
    message_id: str = Form(...),
    original_message: str = Form(...),
    language: str | None = Form(None),
    callback_number: str | None = Form(None),
    ml_model_metadata: str | None = Form(None),  # JSON-encoded dict, since Form fields are strings
    attachment_type: str | None = Form(None),  # "qr" | "image" | "video" | "file"
    package_name: str | None = Form(None),  # only used when attachment_type == "file" and it's an APK
    cert_sha256: str | None = Form(None),
    user_persona: str | None = Form(None),  # Android's onboarding persona -- see MessageScanRequest.user_persona
    user_phone: str | None = Form(None),  # registered reporter's phone -- see MessageScanRequest.user_phone
    attachment: UploadFile | None = File(None),
) -> MessageScanResponse:
    start = time.monotonic()

    from language.detect import detect_language
    from language.translate import translate_to_english

    parsed_metadata = None
    if ml_model_metadata:
        try:
            parsed_metadata = json.loads(ml_model_metadata)
        except json.JSONDecodeError:
            # Malformed metadata from the client must not fail the whole scan -- the classifier
            # metadata is supplementary context for the LLM, not load-bearing.
            print(f"ml_model_metadata was not valid JSON, ignoring: {ml_model_metadata!r}")
    attachment_bytes = await attachment.read() if attachment is not None else None
    has_image_attachment = attachment_type in ("qr", "image") and attachment_bytes is not None

    req = MessageScanRequest(
        message_id=message_id,
        original_message=original_message,
        language=language,
        ml_model_metadata=parsed_metadata,
        has_image_attachment=has_image_attachment,
        callback_number=callback_number,
        user_persona=user_persona,
        user_phone=user_phone,
    )

    # Blocking HTTP calls are pushed to a thread so they don't stall the event loop -- otherwise
    # they'd block every OTHER concurrent request this server is handling, not just this one.
    language = req.language or await asyncio.to_thread(detect_language, req.original_message)
    try:
        message_english = await asyncio.to_thread(translate_to_english, req.original_message, language)
    except Exception as e:
        # Translation API down/misconfigured must not block the scan -- fall back to the
        # original text (the LLM can often still work with it) and let the report note the gap.
        print(f"translate_to_english failed, falling back to original text: {e}")
        message_english = req.original_message

    triage_result = triage(req.original_message, req.has_image_attachment)

    # A phone number doesn't have to arrive via the explicit `callback_number` field -- one
    # regex-extracted straight out of the message text is just as real a vishing signal, so it
    # gets the same Twilio/DLT verification. Explicit `callback_number` (e.g. the number the
    # Android call-screen actually dialed) wins if both are present, since it's ground truth.
    resolved_callback_number = req.callback_number or (
        triage_result.phone_numbers[0] if triage_result.phone_numbers else None
    )

    # Every L2 lane + web corroboration + guideline retrieval genuinely run concurrently now
    # (previously several of these were blocking calls sitting in an `async def`, which stalled
    # the event loop and silently serialized everything behind them -- asyncio.to_thread fixes
    # that; this cut live measured latency from ~9-15s down to ~2-3s on a repeat query).
    tasks: dict[str, asyncio.Task] = {
        "web_corroboration": asyncio.create_task(_live_web_corroboration(message_english)),
        "retrieved_docs": asyncio.create_task(asyncio.to_thread(_retrieve_guideline_docs, message_english)),
    }
    if triage_result.urls and not triage_result.bloom_hit:
        tasks["url_analysis"] = asyncio.create_task(_analyze_url_safe(triage_result.urls[0]))
    if resolved_callback_number:
        tasks["callback_analysis"] = asyncio.create_task(
            _analyze_callback_safe(
                (req.ml_model_metadata or {}).get("sender", ""),
                resolved_callback_number,
                message_english,
            )
        )

    is_apk_attachment = False
    unanalyzed_media: dict | None = None
    if attachment_bytes is not None and attachment_type == "qr":
        tasks["qr_analysis"] = asyncio.create_task(_analyze_qr_safe(attachment_bytes))
    elif attachment_bytes is not None and attachment_type == "file":
        tasks["file_analysis"] = asyncio.create_task(
            _analyze_file_attachment(
                attachment_bytes, attachment.filename, attachment.content_type, package_name or "", cert_sha256 or ""
            )
        )
    elif attachment_bytes is not None and attachment_type in UNANALYZED_ATTACHMENT_TYPES:
        # Cheap metadata only -- no need for a background task for this one.
        unanalyzed_media = _note_unanalyzed_media(
            attachment_type, attachment.filename, attachment.content_type, len(attachment_bytes)
        )

    results = dict(zip(tasks.keys(), await asyncio.gather(*tasks.values())))

    if "file_analysis" in results:
        results["file_analysis"], is_apk_attachment = results["file_analysis"]

    # A QR that decodes to a URL gets chased through the same URL lane a plain-text link would --
    # this only fires when the QR lane actually found a URL, so it doesn't cost anything for QR
    # codes encoding something else (a UPI string, plain text, etc).
    qr_result = results.get("qr_analysis")
    if qr_result and isinstance(qr_result.get("decoded_payload"), str) and qr_result["decoded_payload"].startswith("http"):
        results["url_analysis_from_qr"] = await _analyze_url_safe(qr_result["decoded_payload"])

    detection_results: dict = {
        "urls_found": triage_result.urls,
        "phone_numbers_found": triage_result.phone_numbers,
        "bloom_filter_hit": triage_result.bloom_hit,
    }
    if "url_analysis" in results:
        detection_results["url_analysis"] = results["url_analysis"]
    if "url_analysis_from_qr" in results:
        detection_results["url_analysis_from_qr"] = results["url_analysis_from_qr"]
    if "callback_analysis" in results:
        detection_results["callback_analysis"] = results["callback_analysis"]
    if "qr_analysis" in results:
        detection_results["qr_analysis"] = results["qr_analysis"]
    if "file_analysis" in results:
        detection_results["file_analysis"] = results["file_analysis"]
    if unanalyzed_media:
        detection_results["unanalyzed_media_attachment"] = unanalyzed_media

    retrieved_docs = results["retrieved_docs"]
    web_corroboration = results["web_corroboration"]

    # Redact PII (Aadhaar/card/email/name/phone) from the copy that leaves this process -- to the
    # LLM prompt and to storage. Everything above this line (triage, URL/callback analysis) has
    # already run against the raw text, which is required for those checks to work correctly; per
    # privacy/pii_redaction.py's documented rule, redaction must only touch the externally-sent/
    # persisted copy, never the copy used for technical extraction.
    message_english_for_llm = await asyncio.to_thread(redact_for_external_use, message_english)

    prompt = build_per_message_prompt(
        query_message_english=message_english_for_llm,
        detection_results=detection_results,
        retrieved_docs=retrieved_docs,
        web_corroboration=web_corroboration,
        ml_model_metadata=req.ml_model_metadata,
        user_persona=req.user_persona,
    )
    report = await asyncio.to_thread(_generate_threat_report_cached, prompt, message_english_for_llm)
    report = await _localize_report(report, language)

    content_type = _determine_content_type(triage_result, attachment_type, is_apk_attachment)
    original_message_for_storage = await asyncio.to_thread(redact_for_external_use, req.original_message)
    asyncio.create_task(
        _persist_message_async(req, language, message_english_for_llm, content_type, original_message_for_storage)
    )

    return MessageScanResponse(report=report, latency_ms=int((time.monotonic() - start) * 1000))


# Real-world fraud campaigns blast near-identical templated text to thousands of recipients --
# caching the generated report by a hash of the (redacted, English) message text means the 2nd..Nth
# report of the same campaign is a Redis hit instead of another Groq call, which is what actually
# keeps Groq spend flat as message volume grows (rather than growing ~linearly with it). Keyed on
# the post-redaction text specifically, since two messages that differ only in the PII they contain
# (e.g. two different names) should still hit the same cache entry -- the fraud pattern is identical.
_REPORT_CACHE_TTL_SECONDS = 6 * 3600


def _generate_threat_report_cached(prompt: str, cache_key_text: str) -> ThreatReport:
    import hashlib

    from db.redis_client import cache_get, cache_set

    cache_key = "msg_scan_report:" + hashlib.sha256(cache_key_text.encode("utf-8")).hexdigest()
    cached = cache_get(cache_key)
    if cached:
        try:
            return ThreatReport.model_validate(json.loads(cached))
        except Exception:
            pass  # corrupt/incompatible cache entry -- fall through to a fresh generation

    report = generate_threat_report(prompt)
    try:
        cache_set(cache_key, report.model_dump_json(), _REPORT_CACHE_TTL_SECONDS)
    except Exception:
        pass  # caching is an optimization, never load-bearing for the response
    return report


def _determine_content_type(triage_result, attachment_type: str | None, is_apk_attachment: bool) -> ContentType:
    # Priority follows specificity, not arrival order: an APK attachment is the strongest signal
    # about what this message actually is, a QR code is more specific than "contains a URL", etc.
    if is_apk_attachment:
        return ContentType.apk
    if attachment_type == "qr":
        return ContentType.qr
    if triage_result.urls:
        return ContentType.url
    if triage_result.phone_numbers:
        return ContentType.callback
    return ContentType.text_only


def _retrieve_guideline_docs(message_english: str) -> list[str]:
    from db.vector_store import retrieve_guideline_docs

    return retrieve_guideline_docs(message_english, top_k=3)


def _persist_message_blocking(
    req: MessageScanRequest,
    language: str,
    message_english_redacted: str,
    content_type: ContentType,
    original_message_redacted: str,
) -> None:
    from datetime import datetime, timezone

    from db.postgres_client import Message, get_session_factory

    Session = get_session_factory()
    with Session() as session:
        session.merge(
            Message(
                message_id=req.message_id,
                # PII-redacted copies only -- see redact_for_external_use. Raw text was already
                # used upstream for triage/URL/callback extraction; nothing raw reaches storage.
                original_message=original_message_redacted,
                language=language,
                message_english=message_english_redacted,
                needs_translation=(language != "en"),
                channel="sms",
                content_type=content_type.value,
                region=None,
                persona=req.user_persona,
                sender=(req.ml_model_metadata or {}).get("sender"),
                user_phone=req.user_phone,
                cluster_id=None,  # assigned by the DenStream router, not inline in the request path
                timestamp=datetime.now(timezone.utc),
            )
        )
        session.commit()


async def _persist_message_async(
    req: MessageScanRequest,
    language: str,
    message_english_redacted: str,
    content_type: ContentType,
    original_message_redacted: str,
) -> None:
    if not settings.postgres_dsn:
        return  # Cloud SQL not provisioned yet -- nothing to persist to
    try:
        # to_thread, not a direct call -- this coroutine is scheduled fire-and-forget via
        # asyncio.create_task, but a *synchronous* blocking DB write here would still stall the
        # event loop the moment the loop picks this task up, which delayed flushing the client's
        # HTTP response by several seconds in testing (measured latency_ms was fine; wall-clock
        # wasn't, because the response bytes hadn't actually been written to the socket yet).
        await asyncio.to_thread(
            _persist_message_blocking, req, language, message_english_redacted, content_type, original_message_redacted
        )
    except Exception as e:
        print(f"_persist_message_async failed (non-fatal, response already sent): {e}")
