"""Exports the trained SideloadingDetector checkpoint to PyTorch Mobile (.ptl) format.

This exists because the project's original PyTorch->TFLite path (src/quantize.py, using
litert_torch/ai-edge-torch) turned out to be broken: the exported .tflite files score an obvious
phishing message and a benign message almost identically (~0.51 vs ~0.51-0.57), while the same
trained weights run directly in PyTorch correctly separate them (0.85 vs 0.05). The wrapper's
manual forward logic was verified bit-identical to the real model in eager PyTorch (see
docs/TFLITE_EXPORT_BUG.md) -- the bug is in litert_torch's trace/convert step itself. That
toolchain also turned out to have no Windows build at all (litert-converter / ai-edge-tensorflow
ship no Windows wheels) and this machine has no WSL/Docker to fall back to Linux, so fixing the
TFLite path isn't possible from here.

PyTorch Mobile is the alternative: it needs nothing beyond torch itself (already required by
training), works on this platform right now, and traces the REAL forward pass directly (no manual
reimplementation to get subtly wrong).

Same limitation as the TFLite path applies here too, and is worth stating plainly: dynamic INT8
quantization only touches nn.Linear weights, not the 200k-row word-embedding table (the majority
of this model's size) -- so this is not a small model. It is, however, a CORRECT one, which the
checked-in TFLite exports were not.

Usage:
    python scripts/export_pytorch_mobile.py --model_dir final_model_20260706_123010 \
        --output_dir final_model_20260706_123010/_mobile
"""

import argparse
import json
import os
import time

import torch
import torch.nn as nn
from safetensors.torch import load_file
from transformers import AlbertConfig, AlbertModel, AutoTokenizer

SEQ_LEN = 64
AUX_DIM = 39


class MobileSideloadingDetector(nn.Module):
    """Same architecture as src.model.SideloadingDetector, reconstructed directly from the
    checkpoint's own safetensors tensor shapes (128 embedding_size, 768 hidden_size, 12 layers,
    12 heads, 3072 intermediate, single shared ALBERT layer group) -- this avoids needing network
    access to the gated ai4bharat/indic-bert config on the Hub, which isn't reachable from this
    offline/restricted environment. forward() returns softmax probabilities directly (not raw
    logits) so the Android client doesn't need to reimplement softmax."""

    def __init__(self) -> None:
        super().__init__()
        config = AlbertConfig(
            vocab_size=200_000,
            embedding_size=128,
            hidden_size=768,
            num_hidden_layers=12,
            num_attention_heads=12,
            intermediate_size=3072,
            num_hidden_groups=1,
            inner_group_num=1,
            max_position_embeddings=512,
            type_vocab_size=2,
        )
        self.backbone = AlbertModel(config, add_pooling_layer=True)  # checkpoint has pooler weights (unused by forward)
        self.aux_mlp = nn.Sequential(
            nn.Linear(AUX_DIM, 64), nn.ReLU(), nn.Dropout(0.0), nn.Linear(64, 64), nn.ReLU()
        )
        self.classifier = nn.Sequential(
            nn.Linear(768 + 64, 256), nn.ReLU(), nn.Dropout(0.0), nn.Linear(256, 2)
        )

    def forward(
        self, input_ids: torch.Tensor, attention_mask: torch.Tensor, auxiliary_features: torch.Tensor
    ) -> torch.Tensor:
        cls_embedding = self.backbone(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state[:, 0, :]
        aux_embedding = self.aux_mlp(auxiliary_features)
        combined = torch.cat([cls_embedding, aux_embedding], dim=1)
        logits = self.classifier(combined)
        return torch.softmax(logits, dim=-1)


def _sample_inputs() -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    input_ids = torch.randint(4, 200_000, (1, SEQ_LEN), dtype=torch.long)
    attention_mask = torch.ones((1, SEQ_LEN), dtype=torch.long)
    aux = torch.zeros((1, AUX_DIM), dtype=torch.float32)
    return input_ids, attention_mask, aux


def _check_examples(model: nn.Module, tokenizer, label: str) -> None:
    """Sanity check: an obvious phishing message must score meaningfully higher than a benign
    one. This is the exact regression the checked-in TFLite exports failed."""
    ham = "Hey, are we still meeting for lunch tomorrow at 1pm?"
    phishing = (
        "Dear Customer, your SBI YONO account has been suspended. Download yono-update.apk now "
        "to restore access: http://fake-sbi.xyz/yono.apk"
    )
    with torch.no_grad():
        for name, text in [("ham", ham), ("phishing", phishing)]:
            enc = tokenizer(text, max_length=SEQ_LEN, truncation=True, padding="max_length", return_tensors="pt")
            aux = torch.zeros((1, AUX_DIM), dtype=torch.float32)
            probs = model(enc["input_ids"], enc["attention_mask"], aux)
            print(f"  [{label}] {name}: spam_prob={probs[0, 1].item():.4f}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_dir", required=True)
    parser.add_argument("--output_dir", required=True)
    args = parser.parse_args()
    os.makedirs(args.output_dir, exist_ok=True)

    print("Loading real trained weights...")
    model = MobileSideloadingDetector()
    state_dict = load_file(os.path.join(args.model_dir, "model.safetensors"), device="cpu")
    missing, unexpected = model.load_state_dict(state_dict, strict=False)
    assert not missing and not unexpected, f"weight mismatch: missing={missing} unexpected={unexpected}"
    model.eval()

    tokenizer = AutoTokenizer.from_pretrained(args.model_dir)

    print("\nFP32 eager-mode sanity check (must discriminate ham vs phishing):")
    _check_examples(model, tokenizer, "fp32-eager")

    print("\nApplying INT8 dynamic quantization (nn.Linear layers only -- embedding table stays FP32,")
    print("same limitation as the original TFLite path; see module docstring)...")
    quantized = torch.quantization.quantize_dynamic(model, {nn.Linear}, dtype=torch.qint8)
    quantized.eval()

    print("\nQuantized eager-mode sanity check:")
    _check_examples(quantized, tokenizer, "int8-eager")

    print("\nTracing + optimizing for mobile...")
    example_inputs = _sample_inputs()
    traced = torch.jit.trace(quantized, example_inputs, strict=False)
    from torch.utils.mobile_optimizer import optimize_for_mobile

    optimized = optimize_for_mobile(traced)

    ptl_path = os.path.join(args.output_dir, "spam_classifier_mobile.ptl")
    optimized._save_for_lite_interpreter(ptl_path)
    size_mb = os.path.getsize(ptl_path) / (1024 * 1024)
    print(f"\nSaved {ptl_path}  ({size_mb:.1f} MB)")

    print("\nReloading saved .ptl and re-checking (this is what Android will actually run):")
    from torch.jit.mobile import _load_for_lite_interpreter

    reloaded = _load_for_lite_interpreter(ptl_path)
    with torch.no_grad():
        for name, text in [
            ("ham", "Hey, are we still meeting for lunch tomorrow at 1pm?"),
            (
                "phishing",
                "Dear Customer, your SBI YONO account has been suspended. Download yono-update.apk now "
                "to restore access: http://fake-sbi.xyz/yono.apk",
            ),
        ]:
            enc = tokenizer(text, max_length=SEQ_LEN, truncation=True, padding="max_length", return_tensors="pt")
            aux = torch.zeros((1, AUX_DIM), dtype=torch.float32)
            probs = reloaded(enc["input_ids"], enc["attention_mask"], aux)
            print(f"  [ptl-reloaded] {name}: spam_prob={probs[0, 1].item():.4f}")

    print("\nLatency (CPU, this dev machine -- NOT representative of on-device ARM latency,")
    print("but a same-machine before/after comparison point):")
    input_ids, attention_mask, aux = _sample_inputs()
    n = 20
    with torch.no_grad():
        for _ in range(3):
            reloaded(input_ids, attention_mask, aux)  # warmup
        start = time.perf_counter()
        for _ in range(n):
            reloaded(input_ids, attention_mask, aux)
        elapsed_ms = (time.perf_counter() - start) / n * 1000
    print(f"  mean inference latency over {n} runs: {elapsed_ms:.1f} ms")

    meta = {"ptl_size_mb": size_mb, "latency_ms_dev_machine": elapsed_ms, "seq_len": SEQ_LEN, "aux_dim": AUX_DIM}
    with open(os.path.join(args.output_dir, "export_metadata.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)


if __name__ == "__main__":
    main()
