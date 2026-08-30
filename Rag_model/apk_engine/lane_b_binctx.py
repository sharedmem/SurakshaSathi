"""Lane B: slow (3-25s) structural deep-learning analysis (BinCTX-style). Three sub-analyses:
bytecode-as-image via DenseNet, manifest/permission/string extraction via androguard, and an
ICCG SDK profiler.

STATUS: the DenseNet classifier needs a labeled malware corpus (CICMalDroid2020 / Drebin are the
public bootstrap datasets recommended in docs/CREDENTIALS.md) that hasn't been trained yet in
this environment. `manifest_permission_scan` below is real (androguard does the actual APK
parsing, no trained model needed) and can run today; `bytecode_image_verdict` is a documented
stub until the DenseNet model is trained -- see scripts/train_apk_densenet.py.
"""

from dataclasses import dataclass

MALICIOUS_PERMISSION_TRIAD = {
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.READ_SMS",
}


@dataclass
class LaneBResult:
    verdict: str  # "malicious" | "benign" | "unknown"
    triad_permissions_found: set[str]
    suspicious_strings: list[str]
    bytecode_model_available: bool


def manifest_permission_scan(apk_path: str) -> tuple[set[str], list[str]]:
    """Real, no trained model required: parses AndroidManifest.xml for the malicious permission
    triad and pulls hardcoded string constants (candidate C2 IPs / phishing URLs) via androguard."""
    from androguard.misc import AnalyzeAPK

    apk, _, _ = AnalyzeAPK(apk_path)
    granted = set(apk.get_permissions())
    triad_found = granted & MALICIOUS_PERMISSION_TRIAD

    suspicious_strings = [
        s for s in apk.get_strings()
        if isinstance(s, str) and ("http://" in s or "https://" in s or _looks_like_ip(s))
    ]
    return triad_found, suspicious_strings[:50]  # cap -- this is a signal list, not a full dump


def _looks_like_ip(s: str) -> bool:
    parts = s.strip().split(".")
    return len(parts) == 4 and all(p.isdigit() and 0 <= int(p) <= 255 for p in parts)


def bytecode_image_verdict(apk_path: str) -> str:
    """STUB: maps the .dex bytecode to an RGB image matrix and classifies with a trained
    DenseNet. Returns "unknown" until scripts/train_apk_densenet.py has produced a model
    artifact (GCS-backed, loaded via Vertex AI Endpoint in production)."""
    return "unknown"


def lane_b_analyze(apk_path: str) -> LaneBResult:
    triad_found, suspicious_strings = manifest_permission_scan(apk_path)
    bytecode_verdict = bytecode_image_verdict(apk_path)

    if triad_found == MALICIOUS_PERMISSION_TRIAD or suspicious_strings:
        structural_verdict = "malicious" if triad_found == MALICIOUS_PERMISSION_TRIAD else "unknown"
    else:
        structural_verdict = "benign"

    # bytecode model, once trained, should dominate this decision -- for now it can only abstain
    final_verdict = bytecode_verdict if bytecode_verdict != "unknown" else structural_verdict

    return LaneBResult(
        verdict=final_verdict,
        triad_permissions_found=triad_found,
        suspicious_strings=suspicious_strings,
        bytecode_model_available=False,
    )
