"""
TFLite Inference — mobile-parity smoke-test path
====================================================
Runs the exported model_fp32.tflite / model_int8.tflite (produced by
src/quantize.py) through the SAME LiteRT runtime an Android app would use
(ai_edge_litert.interpreter.Interpreter — the standalone LiteRT package,
not full TensorFlow), so you can sanity-check the converted model's
predictions match the PyTorch model before shipping it to a device.

This mirrors SMSAnalyzer's decision flow (hard rules -> threshold ->
rule_confidence fallback -> anti-FP guard) but swaps the neural forward
pass for the TFLite interpreter. Hard rules / heuristics never touch the
model at all, so they're identical regardless of which backend runs the
ML branch.

Usage:
    python src/tflite_inference.py \
        --config configs/config.yaml \
        --model final_model_YYYYMMDD_HHMMSS \
        --tflite final_model_YYYYMMDD_HHMMSS_quantized/model_fp32.tflite \
        --text "Namaste, aapka RBL Bank account block ho gaya. Install karo: rbl-protect-acc.icu/RBL_Protect.apk"
"""

import argparse
import json
import os
import sys
from typing import Any, Dict, Optional

import numpy as np
import yaml

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from src.feature_extractor import (
    load_feature_extractor, ExtractionResult, URLFeatureEnricher, pick_primary_url,
)
from src.url_intelligence import URLIntelligence
from src.inference import apply_hard_rules, build_llm_payload


def _load_config(path: str) -> dict:
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


class TFLiteSMSAnalyzer:
    """Same decision flow as SMSAnalyzer, but the ML branch runs on a .tflite model."""

    def __init__(self, config_path: str, model_path: str, tflite_path: str,
                 threshold: Optional[float] = None):
        from transformers import AutoTokenizer
        from ai_edge_litert.interpreter import Interpreter

        self.config = _load_config(config_path)

        opt = os.path.join(model_path, "optimal_threshold.json")
        if threshold is not None:
            self.threshold = threshold
        elif os.path.exists(opt):
            self.threshold = json.load(open(opt))["optimal_threshold"]
        else:
            self.threshold = 0.5

        self.feature_extractor = load_feature_extractor(config_path)
        self.url_intelligence = URLIntelligence(run_stage3=True)
        self.tokenizer = AutoTokenizer.from_pretrained(model_path)

        self.interpreter = Interpreter(model_path=tflite_path)
        self.interpreter.allocate_tensors()
        self._input_details = self.interpreter.get_input_details()
        self._output_details = self.interpreter.get_output_details()
        # litert_torch preserves forward(input_ids, attention_mask, auxiliary_features)
        # argument order in the exported signature's input list.
        self._seq_len = self._input_details[0]["shape"][1]

    def _run_model(self, text: str, e: ExtractionResult) -> float:
        enc = self.tokenizer(text, max_length=self._seq_len, padding="max_length",
                              truncation=True, return_tensors="np")
        input_ids = enc["input_ids"].astype(np.int64)
        attention_mask = enc["attention_mask"].astype(np.int64)
        aux = np.array([e.to_feature_vector()], dtype=np.float32)

        tensors = [input_ids, attention_mask, aux]
        for detail, tensor in zip(self._input_details, tensors):
            self.interpreter.set_tensor(detail["index"], tensor)
        self.interpreter.invoke()
        logits = self.interpreter.get_tensor(self._output_details[0]["index"])[0]
        probs = np.exp(logits - logits.max())
        probs = probs / probs.sum()
        return float(probs[1])

    def analyze(self, text: str, metadata: Optional[Dict[str, str]] = None) -> Dict[str, Any]:
        metadata = metadata or {}
        e = self.feature_extractor.extract(text, metadata)

        if e.extracted_urls:
            primary_url = pick_primary_url(e.extracted_urls)
            if primary_url:
                intel = self.url_intelligence.analyze(primary_url)
                URLFeatureEnricher.enrich(e, intel)
                self.feature_extractor.refresh_after_url_enrichment(e)

        hard_spam, hard_reason = apply_hard_rules(e)
        if hard_spam:
            payload = build_llm_payload(text, metadata, e, 1.0, "hard_rule", hard_reason, self.threshold)
            return self._result(True, 1.0, "hard_rule", hard_reason, e, payload)

        spam_prob = self._run_model(text, e)

        if spam_prob >= self.threshold:
            if e.is_likely_benign and spam_prob < 0.85:
                triggered, is_spam = "ham", False
            else:
                triggered, is_spam = "ml_model", True
        elif e.rule_based_spam:
            triggered, is_spam = "rule_confidence", True
        else:
            triggered, is_spam = "ham", False

        llm_payload = None
        if is_spam:
            llm_payload = build_llm_payload(text, metadata, e, spam_prob, triggered, None, self.threshold)
        return self._result(is_spam, spam_prob, triggered, None, e, llm_payload)

    def _result(self, is_spam, spam_prob, triggered, hard_reason, e: ExtractionResult, llm_payload) -> dict:
        r = {
            "is_spam": is_spam,
            "spam_probability": round(spam_prob, 4),
            "threshold_used": round(self.threshold, 4),
            "triggered_by": triggered,
            "hard_rule_reason": hard_reason,
            "rule_confidence": round(e.rule_confidence, 4),
            "sideloading_vectors": e.sideloading_vectors,
            "extracted_urls": e.extracted_urls,
            "detected_brands": e.matched_brands,
            "qr_payload_type": e.qr_payload_type,
            "callback_signals": {
                "has_callback_fraud": e.has_callback_fraud,
                "has_callback_apk_prompt": e.has_callback_apk_prompt,
                "callback_number_count": e.callback_number_count,
            },
            "media_ignored": e.media_ignored,
        }
        if llm_payload is not None:
            r["llm_payload"] = llm_payload
        return r


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="Run inference through the exported TFLite model")
    ap.add_argument("--config", default="configs/config.yaml")
    ap.add_argument("--model", required=True, help="Trained model dir (for tokenizer + threshold)")
    ap.add_argument("--tflite", required=True, help="Path to model_fp32.tflite or model_int8.tflite")
    ap.add_argument("--text", required=True)
    ap.add_argument("--sender", default=None)
    ap.add_argument("--qr", default=None)
    args = ap.parse_args()

    analyzer = TFLiteSMSAnalyzer(args.config, args.model, args.tflite)
    meta = {}
    if args.sender: meta["sender"] = args.sender
    if args.qr: meta["qr_decoded_text"] = args.qr
    print(json.dumps(analyzer.analyze(args.text, meta), indent=2, ensure_ascii=False))
