"""
Inference — Stage 2: Fake App Sideloading & Smishing Classifier
===============================================================
Problem: "Fake mobile apps circulated via SMS/WhatsApp, leading to
          phishing and financial fraud."

HARD RULES (always SPAM, no ML probability needed):
  Rule 1  has_apk_link              – direct .apk/.xapk in message text
  Rule 2  has_qr_apk_payload        – QR code decoded payload → APK
  Rule 3  has_callback_apk_prompt   – "call us → officer installs app"
  Rule 4  has_qr_upi_collect        – QR UPI collect/mandate (money-out)
  Rule 5  rule_confidence == 1.0    – heuristic engine is fully certain

For all other messages: Indic-BERT model probability vs optimal threshold
(saved by train.py as optimal_threshold.json).

When is_spam=True, builds structured llm_payload for Stage 3 (llm_analysis.py).
"""

import os, json, torch, yaml
from typing import Dict, Any, Optional
from transformers import AutoTokenizer

import sys
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from src.model import SideloadingDetector, SideloadingDetectorConfig
from src.feature_extractor import (
    load_feature_extractor, ExtractionResult, URLFeatureEnricher, pick_primary_url,
)
from src.url_intelligence import URLIntelligence


def _load_config(path: str) -> dict:
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


def apply_hard_rules(e: ExtractionResult):
    """Return (is_spam, reason) based purely on deterministic signals."""
    if e.has_apk_link:
        return True, "HARD_RULE_1:direct_apk_link"
    if e.has_qr_apk_payload:
        return True, "HARD_RULE_2:qr_apk_payload"
    if e.has_callback_apk_prompt:
        return True, "HARD_RULE_3:callback_apk_install_prompt"
    if e.has_qr_upi_collect:
        return True, "HARD_RULE_4:qr_upi_collect_scam"
    if e.rule_confidence >= 1.0:
        return True, "HARD_RULE_5:heuristic_max_confidence"
    # Rule 6: Cross-language KYC/authority + callback fraud = certain SPAM
    # (e.g., PNB KYC expired in Hindi + "call representative" in English)
    if e.has_callback_fraud and e.authority_impersonation_score >= 0.75:
        return True, "HARD_RULE_6:authority_callback_fraud"
    return False, ""


def build_llm_payload(text, metadata, e: ExtractionResult,
                      ml_prob, triggered_by, hard_rule_reason, threshold) -> dict:
    """Build structured context dict for Stage 3 LLM analysis."""
    return {
        "message":             text,
        "sender":              metadata.get("sender", "unknown"),
        "triggered_by":        triggered_by,
        "hard_rule_reason":    hard_rule_reason,
        "ml_spam_probability": round(ml_prob, 4),
        "rule_confidence":     round(e.rule_confidence, 4),
        "threshold_used":      round(threshold, 4),
        "detected_vectors":    e.sideloading_vectors,
        "impersonated_brands": e.matched_brands,
        "extracted_urls":      e.extracted_urls,
        "matched_keywords":    e.matched_keywords,
        "url_signals": {
            "has_url":               e.has_url,
            "has_shortened_url":     e.has_shortened_url,
            "has_apk_link":          e.has_apk_link,
            "has_suspicious_domain": e.has_suspicious_domain,
            "has_deep_link":         e.has_deep_link,
            "has_low_rep_tld":       e.has_low_reputation_tld,
            "url_count":             e.url_count,
        },
        "qr_signals": {
            "has_qr_reference":   e.has_qr_reference,
            "qr_payload_type":    e.qr_payload_type,
            "has_qr_apk_payload": e.has_qr_apk_payload,
            "has_qr_upi_collect": e.has_qr_upi_collect,
        },
        "callback_signals": {
            "has_callback_fraud":      e.has_callback_fraud,
            "has_callback_apk_prompt": e.has_callback_apk_prompt,
            "callback_number_count":   e.callback_number_count,
        },
        "media_signals": {
            "has_image_attachment": e.has_image_attachment,
            "has_video_attachment": e.has_video_attachment,
            "media_ignored":        e.media_ignored,
            "note": ("Image/video attachment present but not analyzed — this "
                     "classifier is text-only; the verdict above is based "
                     "solely on the message text (and, if applicable, "
                     "already-decoded QR text).") if e.media_ignored else None,
        },
        "content_signals": {
            "urgency_score":           round(e.urgency_score, 4),
            "financial_lure_score":    round(e.financial_lure_score, 4),
            "authority_impersonation": round(e.authority_impersonation_score, 4),
            "financial_term_count":    e.financial_term_count,
            "action_verb_count":       e.action_verb_count,
        },
        "campaign_signals": {
            "wedding_apk":          e.has_wedding_invitation_pattern,
            "loan_app":             e.has_loan_app_pattern,
            "fake_delivery":        e.has_delivery_notification_pattern,
            "govt_challan":         e.has_govt_transport_pattern,
        },
    }


class SMSAnalyzer:
    """
    Stage 2 classifier. Decision flow:
      1. FeatureExtractor → text heuristics (dims 0-28)
      2. URLIntelligence → URLFeatureEnricher fills dims 29-38
      3. Hard rules → if fires, SPAM immediately (no ML needed)
      4. Indic-BERT → spam_probability
      5. optimal_threshold comparison → SPAM or HAM
      6. Fallback: rule_confidence >= 0.50 → SPAM
      7. If SPAM: build llm_payload for Stage 3
    """

    def __init__(self, config_path: str, model_path: str,
                 threshold: Optional[float] = None, quantize: bool = False):
        self.config = _load_config(config_path)

        # Load threshold saved by train.py
        opt = os.path.join(model_path, "optimal_threshold.json")
        if threshold is not None:
            self.threshold = threshold
        elif os.path.exists(opt):
            self.threshold = json.load(open(opt))["optimal_threshold"]
            print(f"[SMSAnalyzer] threshold={self.threshold:.4f} (from training)")
        else:
            self.threshold = 0.5
            print("[SMSAnalyzer] using default threshold 0.5")

        self.feature_extractor = load_feature_extractor(config_path)
        self.url_intelligence = URLIntelligence(run_stage3=True)
        self.tokenizer = AutoTokenizer.from_pretrained(model_path)

        aux_dim = len(ExtractionResult().to_feature_vector())  # 39
        cfg = SideloadingDetectorConfig(
            model_name      = self.config['model']['name'],
            num_labels      = self.config['model']['num_labels'],
            aux_feature_dim = aux_dim,
            fusion_dim      = self.config['model']['fusion_hidden_dim'],
            dropout         = 0.0,
        )
        self.model = SideloadingDetector(cfg)

        sf  = os.path.join(model_path, "model.safetensors")
        bin_ = os.path.join(model_path, "pytorch_model.bin")
        if os.path.exists(sf):
            from safetensors.torch import load_file
            self.model.load_state_dict(load_file(sf, device="cpu"), strict=False)
            print(f"[SMSAnalyzer] weights loaded from {sf}")
        elif os.path.exists(bin_):
            self.model.load_state_dict(torch.load(bin_, map_location="cpu"))
            print(f"[SMSAnalyzer] weights loaded from {bin_}")
        else:
            print(f"[SMSAnalyzer] WARNING: no weights at {model_path}")

        if quantize:
            self.model = torch.quantization.quantize_dynamic(
                self.model, {torch.nn.Linear}, dtype=torch.qint8)

        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model.to(self.device).eval()

    def analyze(self, text: str, metadata: Optional[Dict[str, str]] = None) -> Dict[str, Any]:
        """
        Classify one SMS/WhatsApp message.

        Args:
            text:     Raw message text (any Indic language or English).
            metadata: Optional dict with keys:
                        "sender"          – sender ID / number
                        "qr_decoded_text" – decoded QR payload from ML Kit

        Returns dict:
            is_spam, spam_probability, threshold_used, triggered_by,
            hard_rule_reason, rule_confidence, sideloading_vectors,
            extracted_urls, detected_brands, qr_payload_type,
            callback_signals, llm_payload (only when is_spam=True),
            raw_features (full ExtractionResult dict for debugging)
        """
        metadata = metadata or {}
        e: ExtractionResult = self.feature_extractor.extract(text, metadata)

        if e.extracted_urls:
            primary_url = pick_primary_url(e.extracted_urls)
            if primary_url:
                intel = self.url_intelligence.analyze(primary_url)
                URLFeatureEnricher.enrich(e, intel)
                self.feature_extractor.refresh_after_url_enrichment(e)

        # Hard rules — no ML needed (re-evaluated after URL enrichment)
        hard_spam, hard_reason = apply_hard_rules(e)
        if hard_spam:
            payload = build_llm_payload(text, metadata, e, 1.0,
                                        "hard_rule", hard_reason, self.threshold)
            return self._result(True, 1.0, "hard_rule", hard_reason, e, payload)

        # ML inference
        spam_prob = self._run_model(text, e)

        if spam_prob >= self.threshold:
            if e.is_likely_benign and spam_prob < 0.85:
                # Anti-FP guard: message has zero attack surface (no URL/APK/QR/
                # callback) and is either predominantly Arabic/Urdu script,
                # very short with no spam signals, or degenerate word repetition.
                # A borderline ML call on such text is more likely script/noise
                # confusion than real spam, so require near-certainty to override.
                triggered, is_spam = "ham", False
            else:
                triggered, is_spam = "ml_model", True
        elif e.rule_based_spam:
            triggered, is_spam = "rule_confidence", True
        else:
            triggered, is_spam = "ham", False

        llm_payload = None
        if is_spam:
            llm_payload = build_llm_payload(text, metadata, e, spam_prob,
                                            triggered, None, self.threshold)
        return self._result(is_spam, spam_prob, triggered, None, e, llm_payload)

    def _run_model(self, text: str, e: ExtractionResult) -> float:
        enc = self.tokenizer(text, max_length=self.config['model']['max_seq_length'],
                             padding=True, truncation=True, return_tensors="pt")
        aux = torch.tensor([e.to_feature_vector()], dtype=torch.float32).to(self.device)
        with torch.no_grad():
            out   = self.model(enc['input_ids'].to(self.device),
                               enc['attention_mask'].to(self.device), aux)
            probs = torch.softmax(out['logits'], dim=-1)[0].cpu().numpy()
        return float(probs[1])

    def _result(self, is_spam, spam_prob, triggered, hard_reason,
                e: ExtractionResult, llm_payload) -> dict:
        r = {
            "is_spam":             is_spam,
            "spam_probability":    round(spam_prob, 4),
            "threshold_used":      round(self.threshold, 4),
            "triggered_by":        triggered,
            "hard_rule_reason":    hard_reason,
            "rule_confidence":     round(e.rule_confidence, 4),
            "sideloading_vectors": e.sideloading_vectors,
            "extracted_urls":      e.extracted_urls,
            "detected_brands":     e.matched_brands,
            "qr_payload_type":     e.qr_payload_type,
            "callback_signals": {
                "has_callback_fraud":      e.has_callback_fraud,
                "has_callback_apk_prompt": e.has_callback_apk_prompt,
                "callback_number_count":   e.callback_number_count,
            },
            "media_ignored": e.media_ignored,
            "raw_features": e.to_dict(),
        }
        if llm_payload is not None:
            r["llm_payload"] = llm_payload
        return r
