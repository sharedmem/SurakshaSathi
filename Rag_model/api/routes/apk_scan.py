"""POST /v1/scan/apk -- Output 1 variant for APK payloads. Runs Lane A and Lane B concurrently,
fuses via the 4-case decision matrix, and only calls the LLM for the QUARANTINE/ambiguous cases
where a plain-language explanation adds value (a clean ALLOW or a Lane-A-confirmed BLOCK doesn't
need an LLM round trip -- saves latency and Groq quota on the common cases).
"""

import asyncio
import hashlib
import tempfile
from pathlib import Path

from fastapi import APIRouter, UploadFile
from pydantic import BaseModel

from apk_engine.fusion_core import fuse
from apk_engine.lane_a_signature import lane_a_verdict
from apk_engine.lane_b_binctx import lane_b_analyze

router = APIRouter()


class ApkScanResponse(BaseModel):
    verdict: str
    reason: str
    lane_a_verdict: str
    lane_b_triad_permissions: list[str]


@router.post("/v1/scan/apk", response_model=ApkScanResponse)
async def scan_apk(file: UploadFile, package_name: str, cert_sha256: str) -> ApkScanResponse:
    content = await file.read()
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

        return ApkScanResponse(
            verdict=fusion_result.action,
            reason=fusion_result.reason,
            lane_a_verdict=lane_a_task.result(),
            lane_b_triad_permissions=list(lane_b_result.triad_permissions_found) if lane_b_result else [],
        )
    finally:
        Path(tmp_path).unlink(missing_ok=True)
