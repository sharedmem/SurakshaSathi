"""
LLM Analysis — Stage 3: Threat Report Generator
================================================
Problem: "Fake mobile apps circulated via SMS/WhatsApp → phishing and financial fraud."

Called ONLY when SMSAnalyzer returns is_spam=True.
Receives structured llm_payload, returns human-readable threat report.

Supported backends (set LLM_BACKEND env var):
  anthropic  (default) — ANTHROPIC_API_KEY
  openai               — OPENAI_API_KEY
  gemini               — GOOGLE_API_KEY
  ollama               — local Ollama at OLLAMA_URL (default localhost:11434)

Usage:
    from src.llm_analysis import LLMAnalyzer
    llm = LLMAnalyzer()
    report = llm.analyze(result["llm_payload"])

CLI demo:
    python src/llm_analysis.py --demo
    python src/llm_analysis.py --payload_file payload.json
"""

import os, json, re, time
from typing import Dict, Any, Optional


SYSTEM_PROMPT = """You are a cybersecurity expert specialising in smishing (SMS phishing)
and fake mobile app fraud targeting users in India and South Asia.

A machine-learning classifier has already flagged the message as SPAM.
You receive structured metadata extracted by a heuristic engine.

Respond ONLY with a valid JSON object — no preamble, no markdown fences.

Return exactly this structure:
{
  "fraud_category": "<direct_apk_phishing|banking_phishing|qr_upi_collect_scam|callback_vishing|challan_apk_fraud|delivery_fraud|loan_app_fraud|lottery_reward_scam|govt_impersonation|kyc_fraud|investment_fraud|telecom_fraud|romance_scam|utility_disconnection|unknown>",
  "attack_mechanism": "<1-2 sentences: exact step-by-step how this attack works>",
  "impersonated_entity": "<brand/govt body being faked, or none>",
  "data_at_risk": ["<list: UPI PIN, bank credentials, Aadhaar, money, device access, etc>"],
  "urgency_level": "<CRITICAL|HIGH|MEDIUM>",
  "urgency_justification": "<one sentence>",
  "victim_advisory": "<2-3 plain-English sentences: what to do and NOT do>",
  "advisory_hindi": "<same advisory in Hindi Devanagari>",
  "red_flags": ["<3-5 specific red flags present in THIS message>"],
  "confidence": "<HIGH|MEDIUM|LOW>"
}"""


def _build_prompt(payload: Dict[str, Any]) -> str:
    lines = [
        f"MESSAGE:\n{payload.get('message','')}",
        f"\nSENDER: {payload.get('sender','unknown')}",
        f"TRIGGER: {payload.get('triggered_by','')}  ML_prob={payload.get('ml_spam_probability',0):.1%}  Rule_conf={payload.get('rule_confidence',0):.1%}",
    ]
    if payload.get('hard_rule_reason'):
        lines.append(f"HARD RULE: {payload['hard_rule_reason']}")
    v = payload.get('detected_vectors', [])
    if v: lines.append("ATTACK VECTORS:\n" + "\n".join(f"  • {x}" for x in v))
    b = payload.get('impersonated_brands', [])
    if b: lines.append(f"IMPERSONATED BRANDS: {', '.join(b)}")
    u = payload.get('extracted_urls', [])
    if u: lines.append("URLS:\n" + "\n".join(f"  • {x}" for x in u[:5]))
    qr = payload.get('qr_signals', {})
    if any(qr.values()):
        lines.append(f"QR: type={qr.get('qr_payload_type','none')}  apk={qr.get('has_qr_apk_payload')}  upi_collect={qr.get('has_qr_upi_collect')}")
    cb = payload.get('callback_signals', {})
    if cb.get('has_callback_apk_prompt'):
        lines.append("CALLBACK: message asks victim to call; officer will install APK")
    elif cb.get('has_callback_fraud'):
        lines.append(f"CALLBACK: vishing pattern detected ({cb.get('callback_number_count',0)} numbers embedded)")
    c = payload.get('content_signals', {})
    lines.append(f"CONTENT: urgency={c.get('urgency_score',0):.2f}  financial_lure={c.get('financial_lure_score',0):.2f}  authority={c.get('authority_impersonation',0):.2f}")
    camp = payload.get('campaign_signals', {})
    active = [k for k, v in camp.items() if v]
    if active: lines.append(f"CAMPAIGNS: {', '.join(active)}")
    lines.append("\nReturn the JSON threat report.")
    return "\n".join(lines)


# ── LLM backends ──────────────────────────────────────────────────────────────

def _call_anthropic(prompt: str, model: str) -> str:
    import anthropic
    c = anthropic.Anthropic(api_key=os.environ.get("ANTHROPIC_API_KEY"))
    r = c.messages.create(model=model, max_tokens=1024,
                          system=SYSTEM_PROMPT,
                          messages=[{"role":"user","content":prompt}])
    return r.content[0].text


def _call_openai(prompt: str, model: str) -> str:
    from openai import OpenAI
    c = OpenAI(api_key=os.environ.get("OPENAI_API_KEY"))
    r = c.chat.completions.create(
        model=model, max_tokens=1024, temperature=0.1,
        messages=[{"role":"system","content":SYSTEM_PROMPT},
                  {"role":"user","content":prompt}])
    return r.choices[0].message.content


def _call_gemini(prompt: str, model: str) -> str:
    import google.generativeai as genai
    genai.configure(api_key=os.environ.get("GOOGLE_API_KEY"))
    m = genai.GenerativeModel(model_name=model, system_instruction=SYSTEM_PROMPT)
    return m.generate_content(prompt, generation_config={"max_output_tokens":1024}).text


def _call_ollama(prompt: str, model: str) -> str:
    import requests
    r = requests.post(
        os.environ.get("OLLAMA_URL","http://localhost:11434") + "/api/generate",
        json={"model":model,"prompt":f"{SYSTEM_PROMPT}\n\nUser: {prompt}\nAssistant:",
              "stream":False,"options":{"temperature":0.1,"num_predict":1024}},
        timeout=120)
    r.raise_for_status()
    return r.json().get("response","")


_FALLBACK = {
    "fraud_category":"unknown","attack_mechanism":"LLM analysis unavailable.",
    "impersonated_entity":"unknown","data_at_risk":[],
    "urgency_level":"HIGH","urgency_justification":"Flagged as spam by ML model.",
    "victim_advisory":"Do NOT click any links, scan QR codes, call any numbers, or install any app. Delete the message immediately.",
    "advisory_hindi":"इस संदेश में कोई लिंक न खोलें, QR कोड स्कैन न करें, कोई नंबर डायल न करें या कोई ऐप इंस्टॉल न करें। संदेश तुरंत डिलीट करें।",
    "red_flags":["Classified as SPAM by AI model."],"confidence":"LOW",
}


def _parse(raw: str) -> dict:
    raw = re.sub(r'```(?:json)?', '', raw).strip().strip('`').strip()
    m = re.search(r'\{.*\}', raw, re.DOTALL)
    if m:
        try: return json.loads(m.group())
        except json.JSONDecodeError: pass
    return _FALLBACK.copy()


class LLMAnalyzer:
    BACKENDS = {
        "anthropic": (_call_anthropic, "claude-sonnet-4-6"),
        "openai":    (_call_openai,    "gpt-4o"),
        "gemini":    (_call_gemini,    "gemini-1.5-flash"),
        "ollama":    (_call_ollama,    "llama3"),
    }

    def __init__(self, backend: Optional[str]=None, model: Optional[str]=None):
        name = (backend or os.environ.get("LLM_BACKEND","anthropic")).lower()
        if name not in self.BACKENDS:
            raise ValueError(f"Unknown backend '{name}'. Choose: {list(self.BACKENDS)}")
        fn, default_model = self.BACKENDS[name]
        self.backend_name = name
        self._fn  = fn
        self.model = model or os.environ.get("LLM_MODEL", default_model)
        print(f"[LLMAnalyzer] backend={self.backend_name}  model={self.model}")

    def analyze(self, payload: Dict[str, Any], retries: int=2) -> Dict[str, Any]:
        prompt = _build_prompt(payload)
        raw, ms = "", 0.0
        for attempt in range(retries+1):
            try:
                t0  = time.perf_counter()
                raw = self._fn(prompt, self.model)
                ms  = (time.perf_counter()-t0)*1000
                break
            except Exception as e:
                print(f"[LLMAnalyzer] attempt {attempt+1} failed: {e}")
                if attempt == retries:
                    r = _FALLBACK.copy()
                    r["meta"] = {"backend":self.backend_name,"error":str(e),"latency_ms":0}
                    return r
                time.sleep(2**attempt)
        r = _parse(raw)
        r["meta"] = {"backend":self.backend_name,"model":self.model,
                     "latency_ms":round(ms,1),"raw_head":raw[:200]}
        return r

    def analyze_batch(self, payloads, delay=0.5):
        results = []
        for i,p in enumerate(payloads):
            print(f"[LLMAnalyzer] {i+1}/{len(payloads)}", end="\r")
            results.append(self.analyze(p))
            if i < len(payloads)-1: time.sleep(delay)
        print()
        return results


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--demo",         action="store_true")
    ap.add_argument("--payload",      type=str, default=None)
    ap.add_argument("--payload_file", type=str, default=None)
    ap.add_argument("--backend",      type=str, default=None)
    ap.add_argument("--model",        type=str, default=None)
    args = ap.parse_args()

    llm = LLMAnalyzer(backend=args.backend, model=args.model)

    if args.demo:
        demo_payload = {
            "message": "Namaste, your RBL Bank account is blocked. Install now: rbl-secure.icu/RBL_Protect.apk",
            "sender": "+919876543210", "triggered_by": "hard_rule",
            "hard_rule_reason": "HARD_RULE_1:direct_apk_link", "ml_spam_probability": 1.0,
            "rule_confidence": 1.0, "threshold_used": 0.15,
            "detected_vectors": ["direct_apk_download","brand_impersonation_domain","low_reputation_tld"],
            "impersonated_brands": ["rbl"], "extracted_urls": ["rbl-secure.icu/RBL_Protect.apk"],
            "matched_keywords": {"urgency":["blocked"], "action":["install"]},
            "url_signals": {"has_url":True,"has_apk_link":True,"has_suspicious_domain":True,
                            "has_low_rep_tld":True,"has_shortened_url":False,"has_deep_link":False,"url_count":1},
            "qr_signals": {"has_qr_reference":False,"qr_payload_type":"none","has_qr_apk_payload":False,"has_qr_upi_collect":False},
            "callback_signals": {"has_callback_fraud":False,"has_callback_apk_prompt":False,"callback_number_count":0},
            "content_signals": {"urgency_score":0.7,"financial_lure_score":0.0,"authority_impersonation":0.7,"financial_term_count":3,"action_verb_count":2},
            "campaign_signals": {"wedding_apk":False,"loan_app":False,"fake_delivery":False,"govt_challan":False},
        }
        print(json.dumps(llm.analyze(demo_payload), indent=2, ensure_ascii=False))
    elif args.payload_file:
        with open(args.payload_file) as f: payload = json.load(f)
        print(json.dumps(llm.analyze(payload), indent=2, ensure_ascii=False))
    elif args.payload:
        print(json.dumps(llm.analyze(json.loads(args.payload)), indent=2, ensure_ascii=False))
    else:
        ap.print_help()
