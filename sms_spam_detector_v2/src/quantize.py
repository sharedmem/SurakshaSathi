"""
Post-Training Quantization (PTQ) Script.

Loads the trained SideloadingDetector model and exports it in two formats:
  1. PyTorch INT8 Dynamic Quantized (.pt)  - for PyTorch Mobile / local use
  2. TFLite FP32 export (.tflite)             - for Android Runtime via TFLite

Usage:
    python src/quantize.py \
        --config configs/config.yaml \
        --model_path final_model \
        --output_dir final_model_quantized
"""

import os
import sys
import argparse
import torch
import yaml
import numpy as np

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from src.model import SideloadingDetector, SideloadingDetectorConfig


def load_config(config_path: str) -> dict:
    with open(config_path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


def get_model_size_mb(path: str) -> float:
    size_bytes = os.path.getsize(path)
    return size_bytes / (1024 * 1024)


def quantize_and_export(config_path: str, model_path: str, output_dir: str):
    config = load_config(config_path)
    os.makedirs(output_dir, exist_ok=True)

    # ------------------------------------------------------------------ #
    # 1. Load original FP32 model
    # ------------------------------------------------------------------ #
    print("Loading FP32 model...")
    model_config = SideloadingDetectorConfig(
        model_name=config['model']['name'],
        num_labels=config['model']['num_labels'],
        aux_feature_dim=config['model']['auxiliary_feature_dim'],
        fusion_dim=config['model']['fusion_hidden_dim'],
        dropout=0.0
    )
    model = SideloadingDetector(model_config)

    safetensors_path = os.path.join(model_path, 'model.safetensors')
    bin_path         = os.path.join(model_path, 'pytorch_model.bin')

    if os.path.exists(safetensors_path):
        from safetensors.torch import load_file
        state_dict = load_file(safetensors_path, device='cpu')
        model.load_state_dict(state_dict, strict=False)
        original_size_mb = get_model_size_mb(safetensors_path)
        print(f"Loaded from {safetensors_path}  |  Size: {original_size_mb:.1f} MB")
    elif os.path.exists(bin_path):
        model.load_state_dict(torch.load(bin_path, map_location='cpu'))
        original_size_mb = get_model_size_mb(bin_path)
        print(f"Loaded from {bin_path}  |  Size: {original_size_mb:.1f} MB")
    else:
        raise FileNotFoundError(f"No model weights found in {model_path}")

    model.eval()

    # ------------------------------------------------------------------ #
    # 2. INT8 Dynamic Quantization  →  save as .pt
    # ------------------------------------------------------------------ #
    print("\n[1/2] Applying INT8 Dynamic Quantization...")
    quantized_model = torch.quantization.quantize_dynamic(
        model, {torch.nn.Linear}, dtype=torch.qint8
    )

    pt_output_path = os.path.join(output_dir, 'model_int8_quantized.pt')
    torch.save(quantized_model.state_dict(), pt_output_path)
    quantized_size_mb = get_model_size_mb(pt_output_path)

    print(f"  Saved INT8 .pt model   : {pt_output_path}")
    print(f"  Original size          : {original_size_mb:.1f} MB")
    print(f"  Quantized size (.pt)   : {quantized_size_mb:.1f} MB")
    print(f"  Size reduction         : {(1 - quantized_size_mb/original_size_mb)*100:.1f}%")

    # ------------------------------------------------------------------ #
    # 3. TFLite Export  →  save as .tflite (FP32, then attempt INT8)
    #
    # Uses litert_torch (ai-edge-torch's successor) to trace + convert.
    # We bypass backbone.forward() and call sub-modules directly:
    #   embeddings → manual 4D additive mask → encoder → CLS token → head
    # This is architecture-agnostic across BERT/ALBERT-style encoders
    # (ai4bharat/indic-bert is an AlbertModel — AlbertTransformer.forward
    # internally applies its own embedding_hidden_mapping_in projection,
    # so calling embeddings→encoder directly is correct for both).
    # ------------------------------------------------------------------ #
    print("\n[2/3] Exporting to TFLite (FP32)...")

    class _EdgeExportWrapper(torch.nn.Module):
        """litert_torch-traceable wrapper that calls backbone sub-modules directly."""
        def __init__(self, sd_model):
            super().__init__()
            self.embeddings  = sd_model.backbone.embeddings
            self.encoder     = sd_model.backbone.encoder
            self.use_auxiliary = sd_model.use_auxiliary
            if self.use_auxiliary:
                self.aux_mlp = sd_model.aux_mlp
            self.classifier  = sd_model.classifier

        def forward(self, input_ids, attention_mask, auxiliary_features=None):
            hidden_states = self.embeddings(input_ids=input_ids)
            extended_mask = (1.0 - attention_mask[:, None, None, :].to(hidden_states.dtype)) * -10000.0
            encoder_output = self.encoder(
                hidden_states=hidden_states,
                attention_mask=extended_mask,
            )
            cls_embedding = encoder_output.last_hidden_state[:, 0, :]
            if self.use_auxiliary and auxiliary_features is not None:
                aux_embedding = self.aux_mlp(auxiliary_features)
                combined = torch.cat((cls_embedding, aux_embedding), dim=1)
            else:
                combined = cls_embedding
            return self.classifier(combined)  # (batch, num_labels)

    export_model = _EdgeExportWrapper(model)
    export_model.eval()

    seq_len  = 64  # representative SMS length
    aux_dim  = config['model']['auxiliary_feature_dim']

    dummy_input_ids      = torch.zeros((1, seq_len), dtype=torch.long)
    dummy_attention_mask = torch.ones((1, seq_len),  dtype=torch.long)
    dummy_aux_features   = torch.zeros((1, aux_dim), dtype=torch.float32)
    sample_args = (dummy_input_ids, dummy_attention_mask, dummy_aux_features)

    tflite_fp32_path = os.path.join(output_dir, 'model_fp32.tflite')
    tflite_int8_path = os.path.join(output_dir, 'model_int8.tflite')
    tflite_fp32_size_mb = 0.0
    tflite_int8_size_mb = 0.0
    int8_tflite_effective = False

    try:
        import litert_torch
        edge_model = litert_torch.convert(export_model, sample_args)
        edge_model.export(tflite_fp32_path)
        tflite_fp32_size_mb = get_model_size_mb(tflite_fp32_path)
        print(f"  Saved TFLite model (FP32) : {tflite_fp32_path}")
        print(f"  TFLite FP32 size          : {tflite_fp32_size_mb:.1f} MB")
    except ImportError:
        print("  (litert_torch not installed — skipping TFLite export)")
        tflite_fp32_path = "N/A"

    # ------------------------------------------------------------------ #
    # 4. TFLite INT8 (dynamic-range, PT2E quantizer — no calibration data
    #    needed, same weight-only technique as the .pt export above).
    #    NOTE: PT2E's linear-op pattern matching only quantizes nn.Linear
    #    weights; ai4bharat/indic-bert's 200k-vocab embedding table (the
    #    majority of its size) is NOT covered by this pass. If the measured
    #    reduction is small, that's why — the honest fix is a smaller-vocab
    #    backbone or explicit embedding quantization, not a bug to silently
    #    paper over here.
    # ------------------------------------------------------------------ #
    print("\n[3/3] Exporting to TFLite (INT8 dynamic-range)...")
    try:
        import litert_torch
        from litert_torch.quantize.pt2e_quantizer import PT2EQuantizer, get_symmetric_quantization_config
        from litert_torch.quantize.quant_config import QuantConfig

        quantizer = PT2EQuantizer().set_global(
            get_symmetric_quantization_config(is_per_channel=True, is_dynamic=True))
        qconfig = QuantConfig(pt2e_quantizer=quantizer)

        edge_model_int8 = litert_torch.convert(export_model, sample_args, quant_config=qconfig)
        edge_model_int8.export(tflite_int8_path)
        tflite_int8_size_mb = get_model_size_mb(tflite_int8_path)
        reduction_pct = (1 - tflite_int8_size_mb / tflite_fp32_size_mb) * 100 if tflite_fp32_size_mb else 0.0
        int8_tflite_effective = reduction_pct >= 15.0
        print(f"  Saved TFLite model (INT8) : {tflite_int8_path}")
        print(f"  TFLite INT8 size          : {tflite_int8_size_mb:.1f} MB  ({reduction_pct:.1f}% reduction)")
        if not int8_tflite_effective:
            print("  WARNING: reduction <15% — PT2E quantized few/no ops on this "
                  "backbone (the embedding table dominates model size and isn't "
                  "covered by linear-only PT2E quantization). Prefer model_fp32.tflite "
                  "for on-device inference, or model_int8_quantized.pt for PyTorch Mobile.")
    except ImportError:
        print("  (litert_torch quantize extras not installed — skipping INT8 TFLite export)")
        tflite_int8_path = "N/A"
    except Exception as e:
        print(f"  INT8 TFLite export failed ({e}) — falling back to FP32 TFLite only.")
        tflite_int8_path = "N/A"

    # ------------------------------------------------------------------ #
    # 5. Summary (all sizes measured on disk, not hardcoded estimates)
    # ------------------------------------------------------------------ #
    print(f"\n{'='*60}")
    print("  QUANTIZATION EXPORT SUMMARY")
    print(f"{'='*60}")
    print(f"  Original FP32 weight size  : {original_size_mb:.1f} MB")
    print(f"  INT8 quantized (.pt)       : {quantized_size_mb:.1f} MB  ({(1 - quantized_size_mb/original_size_mb)*100:.0f}% reduction)")
    print(f"  TFLite FP32 (.tflite)      : {tflite_fp32_size_mb:.1f} MB")
    if tflite_int8_path != "N/A":
        print(f"  TFLite INT8 (.tflite)      : {tflite_int8_size_mb:.1f} MB"
              f"{'  [effective]' if int8_tflite_effective else '  [limited effect, see warning above]'}")
    print(f"  Output directory           : {output_dir}/")
    print(f"{'='*60}")
    print("  Files saved:")
    print(f"    - {pt_output_path}")
    print(f"    - {tflite_fp32_path}")
    if tflite_int8_path != "N/A":
        print(f"    - {tflite_int8_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Quantize and export SMS Sideloading model")
    parser.add_argument('--config', type=str,
                        default='/scratch/m25cse012/sms_spam_detector/configs/config.yaml')
    parser.add_argument('--model_path', type=str,
                        default='/scratch/m25cse012/sms_spam_detector/final_model')
    parser.add_argument('--output_dir', type=str,
                        default='/scratch/m25cse012/sms_spam_detector/final_model_quantized')
    args = parser.parse_args()
    quantize_and_export(args.config, args.model_path, args.output_dir)
