"""Asynchronous Decision Fusion Core: the 4-case matrix arbitrating Lane A (identity/cloud) vs.
Lane B (structural AI) verdicts. Pure decision logic, no external calls -- runs once both lanes
report (or immediately, if Lane A alone already says malicious).
"""

import asyncio
from dataclasses import dataclass


@dataclass
class FusionVerdict:
    action: str  # "BLOCK" | "ALLOW" | "QUARANTINE"
    reason: str


async def fuse(lane_a_task: asyncio.Task, lane_b_task: asyncio.Task) -> FusionVerdict:
    """Case 1 (Lane A malicious) short-circuits: don't wait for Lane B, cancel it to free the
    worker. Cases 2-4 need both results."""
    lane_a_verdict = await lane_a_task

    if lane_a_verdict == "malicious":
        lane_b_task.cancel()
        return FusionVerdict("BLOCK", "Lane A: known-malicious signature/cloud reputation hit")

    lane_b_result = await lane_b_task
    lane_b_verdict = lane_b_result.verdict if hasattr(lane_b_result, "verdict") else lane_b_result

    if lane_a_verdict == "benign" and lane_b_verdict in ("benign", "unknown"):
        return FusionVerdict("ALLOW", "Both lanes consistent with a safe profile")

    if lane_a_verdict == "unknown" and lane_b_verdict == "malicious":
        return FusionVerdict(
            "BLOCK", "Zero-day: unrecognized certificate, but structural AI found malicious behavior"
        )

    if lane_a_verdict == "benign" and lane_b_verdict == "malicious":
        return FusionVerdict(
            "QUARANTINE",
            "Signature matches a trusted developer, but structural analysis found malicious "
            "code -- possible certificate hijack or Janus-style exploit (CVE-2017-13156)",
        )

    # any other combination (e.g. both unknown) -- fail toward caution, not silent allow
    return FusionVerdict("QUARANTINE", f"Inconclusive: Lane A={lane_a_verdict}, Lane B={lane_b_verdict}")
