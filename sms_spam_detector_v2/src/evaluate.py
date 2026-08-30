"""
Evaluation Script for SMS Sideloading Detection Model.

Provides comprehensive metrics across languages and sideloading vectors.
Supports:
  - Internal HF test split (default)
  - Custom user-provided CSV test file (--custom_test path/to/file.csv)
  - INT8 Dynamic quantization benchmark (--quantize flag)

Outputs per run:
  - predictions_<tag>.csv  : raw text, true label, predicted label, spam probability
  - metrics_<tag>.json     : full classification report + key metrics
"""

import os
import json
import time
import torch
import numpy as np
import pandas as pd
from transformers import AutoTokenizer
from torch.utils.data import DataLoader, Dataset
from sklearn.metrics import (classification_report, confusion_matrix,
                             roc_auc_score, precision_recall_curve,
                             accuracy_score, f1_score, precision_score, recall_score)
import yaml
from datasets import load_from_disk

# Import our custom components
import sys
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from src.model import SideloadingDetector, SideloadingDetectorConfig
from src.feature_extractor import load_feature_extractor, URLFeatureEnricher, URLFeatureCache
from src.train import SMSDataset, SMSDataCollator, _collect_text_meta_pairs
from src.inference import apply_hard_rules

def load_config(config_path: str) -> dict:
    with open(config_path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


class CustomCSVDataset(Dataset):
    """
    Dataset for a user-supplied CSV file.
    Only 'text' and 'label' columns are used. All other columns are ignored.
    """
    def __init__(self, csv_path: str, tokenizer, feature_extractor, max_length: int,
                 url_cache: URLFeatureCache = None):
        df = pd.read_csv(csv_path)
        # Normalize column names to lowercase stripped
        df.columns = [c.strip().lower() for c in df.columns]
        if 'text' not in df.columns or 'label' not in df.columns:
            raise ValueError(f"CSV must contain 'text' and 'label' columns. Found: {list(df.columns)}")
        df = df[['text', 'label']].dropna()
        # Normalize label to int (handles 'spam'/'ham' or 0/1)
        if df['label'].dtype == object:
            df['label'] = df['label'].str.strip().str.lower().map({'spam': 1, 'ham': 0, '1': 1, '0': 0,'not_spam':0})
        df['label'] = df['label'].astype(int)
        self.df = df.reset_index(drop=True)
        self.tokenizer = tokenizer
        self.feature_extractor = feature_extractor
        self.max_length = max_length
        self.url_cache = url_cache

    def __len__(self):
        return len(self.df)

    def __getitem__(self, idx):
        text = str(self.df.loc[idx, 'text'])
        label = int(self.df.loc[idx, 'label'])

        encoding = self.tokenizer(
            text,
            max_length=self.max_length,
            padding=False,
            truncation=True,
            return_tensors='pt'
        )
        extraction = self.feature_extractor.extract(text)
        if self.url_cache:
            intel = self.url_cache.get_for_urls(extraction.extracted_urls)
            URLFeatureEnricher.enrich(extraction, intel)
        aux_features = extraction.to_feature_vector()

        return {
            'input_ids': encoding['input_ids'].squeeze(0),
            'attention_mask': encoding['attention_mask'].squeeze(0),
            'auxiliary_features': torch.tensor(aux_features, dtype=torch.float32),
            'labels': torch.tensor(label, dtype=torch.long)
        }


def load_model(config, model_path, quantize=False, device=None):
    """Load SideloadingDetector and optionally apply INT8 dynamic quantization."""
    if device is None:
        device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

    model_config = SideloadingDetectorConfig(
        model_name=config['model']['name'],
        num_labels=config['model']['num_labels'],
        aux_feature_dim=config['model']['auxiliary_feature_dim'],
        fusion_dim=config['model']['fusion_hidden_dim'],
        dropout=0.0  # No dropout during inference/evaluation
    )
    model = SideloadingDetector(model_config)

    # Load weights - try safetensors first, then pytorch_model.bin
    safetensors_path = os.path.join(model_path, 'model.safetensors')
    bin_path = os.path.join(model_path, 'pytorch_model.bin')

    if os.path.exists(safetensors_path):
        from safetensors.torch import load_file
        state_dict = load_file(safetensors_path, device='cpu')
        model.load_state_dict(state_dict, strict=False)
        print(f"Loaded weights from {safetensors_path}")
    elif os.path.exists(bin_path):
        model.load_state_dict(torch.load(bin_path, map_location='cpu'))
        print(f"Loaded weights from {bin_path}")
    else:
        print(f"WARNING: No weights found at {model_path}. Using untrained weights.")

    if quantize:
        print("Applying INT8 Dynamic Quantization to linear layers...")
        model = torch.quantization.quantize_dynamic(
            model, {torch.nn.Linear}, dtype=torch.qint8
        )
        print("Quantization applied.")

    model.to(device)
    model.eval()
    return model, device


def run_evaluation(model, dataloader, device,
                   feature_extractor=None, optimal_threshold=0.5, texts=None):
    """
    Run inference on a dataloader.
    Applies:
      - optimal_threshold (from optimal_threshold.json) instead of raw argmax 0.5
      - Hard rules (APK, QR-APK, callback-APK, UPI, rule_confidence=1.0,
        authority+callback fraud) to override predictions deterministically
    This mirrors the exact decision flow of SMSAnalyzer.analyze().
    """
    all_preds = []
    all_labels = []
    all_probs = []
    latencies = []
    sample_idx = 0

    with torch.no_grad():
        for batch in dataloader:
            input_ids = batch['input_ids'].to(device)
            attention_mask = batch['attention_mask'].to(device)
            aux_features = batch['auxiliary_features'].to(device)
            labels = batch['labels'].numpy()

            t0 = time.perf_counter()
            outputs = model(input_ids, attention_mask, aux_features)
            t1 = time.perf_counter()

            batch_size = input_ids.size(0)
            latencies.append((t1 - t0) * 1000 / batch_size)

            logits = outputs['logits'].cpu()
            probs = torch.softmax(logits, dim=-1).numpy()
            ml_probs = probs[:, 1]

            for i, (ml_prob, label) in enumerate(zip(ml_probs, labels)):
                # Step 1: Apply optimal threshold (not hardcoded 0.5)
                pred = 1 if ml_prob >= optimal_threshold else 0

                # Step 2: Apply hard rules + rule_confidence fallback.
                # This MUST mirror SMSAnalyzer.analyze() exactly:
                #   hard_rule wins -> else ml threshold -> else rule_confidence fallback.
                # (Previously this only applied hard rules, silently dropping the
                #  rule_confidence fallback branch that inference.py relies on for
                #  non-APK/QR fraud such as callback-only scams and bare shortened
                #  URLs — which made offline eval look far worse than production.)
                if feature_extractor is not None and texts is not None:
                    t_idx = sample_idx + i
                    if t_idx < len(texts):
                        text = str(texts[t_idx])
                        e = feature_extractor.extract(text)
                        hard_spam, _ = apply_hard_rules(e)
                        if hard_spam:
                            pred = 1  # Hard rule always wins
                        elif pred == 1 and e.is_likely_benign and ml_prob < 0.85:
                            pred = 0  # anti-FP guard (matches inference.py)
                        elif pred == 0 and e.rule_based_spam:
                            pred = 1  # rule_confidence fallback (matches inference.py)

                all_preds.append(pred)
                all_labels.append(label)
                all_probs.append(float(ml_prob))

            sample_idx += batch_size

    avg_latency_ms = np.mean(latencies)
    return all_preds, all_labels, all_probs, avg_latency_ms


def print_and_save_results(all_labels, all_preds, all_probs, avg_latency_ms,
                           title="EVALUATION RESULTS", output_dir=None, tag="eval", texts=None):
    """Print comprehensive evaluation metrics and save to CSV + JSON."""
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}")
    report_dict = classification_report(
        all_labels, all_preds,
        target_names=['Ham', 'Spam (Sideloading)'],
        output_dict=True
    )
    print(classification_report(all_labels, all_preds, target_names=['Ham', 'Spam (Sideloading)']))

    try:
        auc = roc_auc_score(all_labels, all_probs)
        print(f"ROC-AUC Score: {auc:.4f}")
    except ValueError:
        auc = None
        print("ROC-AUC: N/A (only one class present)")

    cm = confusion_matrix(all_labels, all_preds)
    tn, fp, fn, tp = cm[0][0], cm[0][1], cm[1][0], cm[1][1]
    print("\nConfusion Matrix:")
    print(f"  True Negative  (Ham correctly ID'd) : {tn}")
    print(f"  False Positive (Ham flagged as Spam): {fp}")
    print(f"  False Negative (Spam missed)        : {fn}  <-- WANT NEAR ZERO")
    print(f"  True Positive  (Spam correctly ID'd): {tp}")

    print(f"\nAvg Inference Latency : {avg_latency_ms:.2f} ms/sample")

    # Threshold optimization for max recall
    optimal_threshold = None
    precision_at_threshold = None
    precisions, recalls, thresholds = precision_recall_curve(all_labels, all_probs)
    target_recall = 0.99
    idx = np.where(recalls >= target_recall)[0]
    if len(idx) > 0:
        optimal_idx = idx[-1]
        if optimal_idx < len(thresholds):
            optimal_threshold = float(thresholds[optimal_idx])
            precision_at_threshold = float(precisions[optimal_idx])
            print(f"\nThreshold for {target_recall*100:.0f}% recall: {optimal_threshold:.4f}")
            print(f"Precision at that threshold         : {precision_at_threshold:.4f}")

    # ---- Save CSV of predictions ----
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)
        label_names = ['Ham', 'Spam']
        df_out = pd.DataFrame({
            'text':             texts if texts is not None else [""] * len(all_labels),
            'true_label':       [label_names[int(l)] for l in all_labels],
            'predicted_label':  [label_names[int(p)] for p in all_preds],
            'spam_probability': [float(p) for p in all_probs],
            'correct':          [l == p for l, p in zip(all_labels, all_preds)]
        })
        csv_path = os.path.join(output_dir, f'predictions_{tag}.csv')
        df_out.to_csv(csv_path, index=False)
        print(f"\n  Saved predictions CSV : {csv_path}")

        # ---- Save metrics JSON ----
        metrics = {
            'title':                    title,
            'accuracy':                 report_dict['accuracy'],
            'macro_f1':                 report_dict['macro avg']['f1-score'],
            'macro_precision':          report_dict['macro avg']['precision'],
            'macro_recall':             report_dict['macro avg']['recall'],
            'weighted_f1':              report_dict['weighted avg']['f1-score'],
            'roc_auc':                  auc,
            'avg_latency_ms':           avg_latency_ms,
            'confusion_matrix':         {'TN': int(tn), 'FP': int(fp), 'FN': int(fn), 'TP': int(tp)},
            'optimal_threshold_99pct':  optimal_threshold,
            'precision_at_threshold':   precision_at_threshold,
            'per_class':                report_dict,
        }
        json_path = os.path.join(output_dir, f'metrics_{tag}.json')
        with open(json_path, 'w') as jf:
            json.dump(metrics, jf, indent=2)
        print(f"  Saved metrics JSON    : {json_path}")


def evaluate(config_path: str, model_path: str, custom_test_csv: str = None, quantize: bool = False):
    config = load_config(config_path)

    output_dir = os.path.join(model_path, 'evaluation_results')
    os.makedirs(output_dir, exist_ok=True)
    print(f"  Evaluation results will be saved to: {output_dir}")

    # Load optimal threshold from training (falls back to 0.5)
    opt_thresh_path = os.path.join(model_path, 'optimal_threshold.json')
    if os.path.exists(opt_thresh_path):
        with open(opt_thresh_path) as f:
            opt_data = json.load(f)
        optimal_threshold = float(opt_data.get('optimal_threshold', 0.5))
        print(f"[Evaluate] Loaded optimal threshold: {optimal_threshold:.4f}  (target recall: {opt_data.get('target_recall_pct', 99)}%)")
    else:
        optimal_threshold = 0.5
        print("[Evaluate] optimal_threshold.json not found — using default 0.5")

    print("Loading tokenizer and feature extractor...")
    tokenizer = AutoTokenizer.from_pretrained(model_path)
    feature_extractor = load_feature_extractor(config_path)

    print("Pre-fetching URL intelligence cache (Stage 2 only) ...")
    hf_dataset = load_from_disk(os.path.join(config['paths']['processed_data_dir'], 'hf_dataset'))
    url_pairs = _collect_text_meta_pairs(hf_dataset)
    if custom_test_csv and os.path.exists(custom_test_csv):
        custom_df = pd.read_csv(custom_test_csv)
        custom_df.columns = [c.strip().lower() for c in custom_df.columns]
        if 'text' in custom_df.columns:
            for text in custom_df['text'].dropna():
                url_pairs.append((str(text), {}))
    url_cache = URLFeatureCache.build(url_pairs, feature_extractor, run_stage3=False)
    print(f"[Evaluate] Cached URL intelligence for {len(url_cache._cache)} unique URLs")

    model, device = load_model(config, model_path, quantize=quantize)

    data_collator = SMSDataCollator(tokenizer)
    batch_size = config['training']['batch_size']

    # ------------------------------------------------------------------ #
    # 1. Internal HF Test Split
    # ------------------------------------------------------------------ #
    print("\nEvaluating on internal HF test split...")
    df_test = hf_dataset['test'].to_pandas()
    texts_internal = df_test['text'].tolist() if 'text' in df_test.columns else None

    test_dataset = SMSDataset(
        hf_dataset['test'], tokenizer, feature_extractor,
        config['model']['max_seq_length'], url_cache,
    )
    internal_loader = DataLoader(test_dataset, batch_size=batch_size, collate_fn=data_collator)
    preds, labels, probs, lat = run_evaluation(
        model, internal_loader, device,
        feature_extractor=feature_extractor,
        optimal_threshold=optimal_threshold,
        texts=texts_internal,
    )

    print_and_save_results(
        labels, preds, probs, lat,
        title="INTERNAL TEST SET",
        output_dir=output_dir,
        tag="internal_test",
        texts=texts_internal
    )

    # Per-language breakdown
    df_test['pred'] = preds
    df_test['prob'] = probs
    print(f"\n{'='*60}")
    print("  PERFORMANCE BY LANGUAGE (Internal Test Set)")
    print(f"{'='*60}")
    lang_rows = []
    for lang in sorted(df_test['language'].unique()):
        lang_df = df_test[df_test['language'] == lang]
        if len(lang_df) > 5:
            y_true = lang_df['label']
            y_pred = lang_df['pred']
            rec  = recall_score(y_true, y_pred, zero_division=0)
            prec = precision_score(y_true, y_pred, zero_division=0)
            f1   = f1_score(y_true, y_pred, zero_division=0)
            acc  = accuracy_score(y_true, y_pred)
            print(f"  {lang:10} | Samples: {len(lang_df):5} | "
                  f"Recall: {rec:.4f} | Precision: {prec:.4f} | F1: {f1:.4f}")
            lang_rows.append({'language': lang, 'samples': len(lang_df),
                              'recall': rec, 'precision': prec, 'f1': f1, 'accuracy': acc})

    if lang_rows:
        lang_csv = os.path.join(output_dir, 'metrics_by_language.csv')
        pd.DataFrame(lang_rows).to_csv(lang_csv, index=False)
        print(f"  Saved language breakdown  : {lang_csv}")

    # ------------------------------------------------------------------ #
    # 2. Custom User CSV Test File (if provided)
    # ------------------------------------------------------------------ #
    if custom_test_csv and os.path.exists(custom_test_csv):
        print(f"\nEvaluating on custom test file: {custom_test_csv}")
        c_df = pd.read_csv(custom_test_csv)
        c_df.columns = [c.strip().lower() for c in c_df.columns]
        c_texts = c_df['text'].tolist() if 'text' in c_df.columns else None

        custom_dataset = CustomCSVDataset(
            custom_test_csv, tokenizer, feature_extractor,
            config['model']['max_seq_length'], url_cache,
        )
        custom_loader = DataLoader(custom_dataset, batch_size=batch_size, collate_fn=data_collator)
        c_preds, c_labels, c_probs, c_lat = run_evaluation(
            model, custom_loader, device,
            feature_extractor=feature_extractor,
            optimal_threshold=optimal_threshold,
            texts=c_texts,
        )
        tag = os.path.splitext(os.path.basename(custom_test_csv))[0]
        print_and_save_results(
            c_labels, c_preds, c_probs, c_lat,
            title="CUSTOM USER TEST SET",
            output_dir=output_dir,
            tag=tag,
            texts=c_texts
        )
    elif custom_test_csv:
        print(f"WARNING: Custom test file not found at {custom_test_csv}")

    # ------------------------------------------------------------------ #
    # 3. Quantization size comparison
    # ------------------------------------------------------------------ #
    if quantize:
        print(f"\n{'='*60}")
        print("  QUANTIZATION SUMMARY")
        print(f"{'='*60}")
        total_params = sum(p.numel() for p in model.parameters())
        safetensors_path = os.path.join(model_path, 'model.safetensors')
        bin_path = os.path.join(model_path, 'pytorch_model.bin')
        original_path = safetensors_path if os.path.exists(safetensors_path) else bin_path
        if os.path.exists(original_path):
            original_mb = os.path.getsize(original_path) / (1024 * 1024)
            print(f"  Total Parameters (post-quant): {total_params:,}")
            print(f"  Original FP32 weight file    : {original_mb:.1f} MB")
        else:
            print(f"  Total Parameters (post-quant): {total_params:,}")
        print("  Use src/quantize.py for measured INT8 (.pt) and TFLite export sizes.")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Evaluate SMS Sideloading Detector")
    parser.add_argument('--config', type=str,
                        default='/scratch/m25cse012/sms_spam_detector/configs/config.yaml')
    parser.add_argument('--model_path', type=str,
                        default='/scratch/m25cse012/sms_spam_detector/final_model')
    parser.add_argument('--custom_test', type=str, default=None,
                        help='Path to a custom CSV test file with text and label columns')
    parser.add_argument('--quantize', action='store_true',
                        help='Apply INT8 dynamic quantization before evaluation')
    args = parser.parse_args()
    evaluate(args.config, args.model_path,
             custom_test_csv=args.custom_test,
             quantize=args.quantize)
