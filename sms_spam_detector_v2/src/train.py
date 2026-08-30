"""
Training Script — Fake App Sideloading Detector
================================================
Changes vs original:
  - aux_feature_dim now reads from config (29 not 25)
  - Versioned output dir: final_model_YYYYMMDD_HHMMSS/
  - Optimal threshold (99% recall) computed and saved post-training
  - Dynamic padding collator (unchanged from original)
"""

import os, json, torch, torch.nn as nn, numpy as np, yaml
from datetime import datetime
from typing import Optional
from torch.utils.data import Dataset
from transformers import AutoTokenizer, Trainer, TrainingArguments, EarlyStoppingCallback
from sklearn.metrics import (accuracy_score, f1_score, precision_score,
                              recall_score, roc_auc_score, precision_recall_curve)
from datasets import load_from_disk

import sys
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from src.model import SideloadingDetector, SideloadingDetectorConfig
from src.feature_extractor import load_feature_extractor, URLFeatureEnricher, URLFeatureCache


def _load_config(p):
    with open(p, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


def make_versioned_dir(project_root: str) -> str:
    ts  = datetime.now().strftime("%Y%m%d_%H%M%S")
    out = os.path.join(project_root, f"final_model_{ts}")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "run_info.json"), "w") as f:
        json.dump({"created_at": ts, "model": "ai4bharat/indic-bert"}, f, indent=2)
    print(f"\n[Train] Versioned output dir: {out}\n")
    return out


class SMSDataset(Dataset):
    def __init__(self, hf_dataset, tokenizer, feature_extractor, max_length,
                 url_cache: Optional[URLFeatureCache] = None):
        self.ds = hf_dataset
        self.tok = tokenizer
        self.fe  = feature_extractor
        self.ml  = max_length
        self.url_cache = url_cache

    def __len__(self): return len(self.ds)

    def _parse_metadata(self, item):
        meta = {}
        if 'metadata' in item and item['metadata']:
            try:
                meta = json.loads(item['metadata'])
            except (json.JSONDecodeError, TypeError):
                pass
        return meta

    def __getitem__(self, idx):
        item = self.ds[idx]
        text, label = item['text'], item['label']
        meta = self._parse_metadata(item)
        enc = self.tok(text, max_length=self.ml, padding=False,
                       truncation=True, return_tensors='pt')
        extraction = self.fe.extract(text, meta)
        if self.url_cache:
            intel = self.url_cache.get_for_urls(extraction.extracted_urls)
            URLFeatureEnricher.enrich(extraction, intel)
        aux = extraction.to_feature_vector()
        return {
            'input_ids':         enc['input_ids'].squeeze(0),
            'attention_mask':    enc['attention_mask'].squeeze(0),
            'auxiliary_features': torch.tensor(aux, dtype=torch.float32),
            'labels':            torch.tensor(label, dtype=torch.long),
        }


class SMSDataCollator:
    def __init__(self, tokenizer): self.tok = tokenizer
    def __call__(self, features):
        padded = self.tok.pad(
            [{"input_ids": f['input_ids'], "attention_mask": f['attention_mask']}
             for f in features],
            padding=True, return_tensors="pt")
        return {
            'input_ids':         padded['input_ids'],
            'attention_mask':    padded['attention_mask'],
            'auxiliary_features': torch.stack([f['auxiliary_features'] for f in features]),
            'labels':            torch.stack([f['labels'] for f in features]),
        }


class ContiguousTrainer(Trainer):
    def _save(self, output_dir=None, state_dict=None):
        if state_dict is None:
            state_dict = self.model.state_dict()
        state_dict = {k: v.contiguous() if isinstance(v, torch.Tensor) else v
                      for k, v in state_dict.items()}
        super()._save(output_dir, state_dict=state_dict)


class FocalLossTrainer(ContiguousTrainer):
    def __init__(self, alpha=0.75, gamma=2.0, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.alpha, self.gamma = alpha, gamma

    def compute_loss(self, model, inputs, return_outputs=False, **kwargs):
        labels  = inputs.pop("labels")
        outputs = model(**inputs)
        logits  = outputs.get("logits")
        ce      = nn.CrossEntropyLoss(reduction='none')(logits, labels)
        pt      = torch.exp(-ce)
        loss    = (self.alpha * (1 - pt) ** self.gamma * ce).mean()
        return (loss, outputs) if return_outputs else loss


def compute_metrics(eval_pred):
    logits, labels = eval_pred
    preds = np.argmax(logits, axis=-1)
    probs = torch.softmax(torch.tensor(logits), dim=-1).numpy()[:, 1]
    m = {
        'accuracy':  accuracy_score(labels, preds),
        'f1':        f1_score(labels, preds, zero_division=0),
        'precision': precision_score(labels, preds, zero_division=0),
        'recall':    recall_score(labels, preds, zero_division=0),
    }
    try: m['auc_roc'] = roc_auc_score(labels, probs)
    except ValueError: pass
    return m


def compute_optimal_threshold(model, test_ds, collator, config, out_dir, device):
    from torch.utils.data import DataLoader
    model.eval()
    loader = DataLoader(test_ds, batch_size=config['training']['batch_size'],
                        collate_fn=collator)
    all_probs, all_labels = [], []
    with torch.no_grad():
        for batch in loader:
            ids  = batch['input_ids'].to(device)
            mask = batch['attention_mask'].to(device)
            aux  = batch['auxiliary_features'].to(device)
            labs = batch['labels'].numpy()
            out  = model(ids, mask, aux)
            p    = torch.softmax(out['logits'], dim=-1).cpu().numpy()
            all_probs.extend(p[:, 1])
            all_labels.extend(labs)

    target_recall = config.get('evaluation', {}).get('target_recall', 0.99)
    precisions, recalls, thresholds = precision_recall_curve(
        np.array(all_labels), np.array(all_probs))

    opt_thresh = 0.5
    prec_at    = None
    idx = np.where(recalls[:-1] >= target_recall)[0]
    if len(idx):
        i           = idx[-1]
        opt_thresh  = float(thresholds[i])
        prec_at     = float(precisions[i])

    result = {
        "optimal_threshold":      opt_thresh,
        "target_recall_pct":      target_recall * 100,
        "precision_at_threshold": prec_at,
        "note": "Use this threshold in SMSAnalyzer instead of 0.5.",
    }
    path = os.path.join(out_dir, "optimal_threshold.json")
    with open(path, 'w') as f:
        json.dump(result, f, indent=2)
    print(f"[Train] Optimal threshold ({target_recall*100:.0f}% recall): "
          f"{opt_thresh:.4f}  →  {path}")
    return opt_thresh


def _collect_text_meta_pairs(hf_ds) -> list:
    pairs = []
    for split in ('train', 'validation', 'test'):
        for item in hf_ds[split]:
            meta = {}
            if item.get('metadata'):
                try:
                    meta = json.loads(item['metadata'])
                except (json.JSONDecodeError, TypeError):
                    pass
            pairs.append((item['text'], meta))
    return pairs


def train(config_path: str):
    config = _load_config(config_path)

    print("Loading tokenizer and data …")
    tokenizer = AutoTokenizer.from_pretrained(config['model']['name'])
    hf_ds     = load_from_disk(os.path.join(config['paths']['processed_data_dir'], 'hf_dataset'))
    fe        = load_feature_extractor(config_path)

    print("Pre-fetching URL intelligence cache (Stage 2 only) …")
    url_pairs = _collect_text_meta_pairs(hf_ds)
    url_cache = URLFeatureCache.build(url_pairs, fe, run_stage3=False)
    print(f"[Train] Cached URL intelligence for {len(url_cache._cache)} unique URLs")

    collator    = SMSDataCollator(tokenizer)
    max_len     = config['model']['max_seq_length']
    train_ds    = SMSDataset(hf_ds['train'],      tokenizer, fe, max_len, url_cache)
    val_ds      = SMSDataset(hf_ds['validation'], tokenizer, fe, max_len, url_cache)
    test_ds     = SMSDataset(hf_ds['test'],        tokenizer, fe, max_len, url_cache)

    # aux_feature_dim must match to_feature_vector() length (39)
    aux_dim = len(fe.extract("test").to_feature_vector())
    print(f"[Train] aux_feature_dim = {aux_dim}")

    model_cfg = SideloadingDetectorConfig(
        model_name      = config['model']['name'],
        num_labels      = config['model']['num_labels'],
        aux_feature_dim = aux_dim,
        fusion_dim      = config['model']['fusion_hidden_dim'],
        dropout         = config['model']['dropout'],
    )
    model = SideloadingDetector(model_cfg)

    versioned_dir  = make_versioned_dir(config['paths']['project_root'])
    checkpoint_dir = config['paths']['checkpoint_dir']

    total_steps  = (len(train_ds) // config['training']['batch_size']) * config['training']['epochs']
    warmup_steps = int(total_steps * config['training']['warmup_ratio'])

    args = TrainingArguments(
        output_dir                  = checkpoint_dir,
        num_train_epochs            = config['training']['epochs'],
        per_device_train_batch_size = config['training']['batch_size'],
        per_device_eval_batch_size  = config['training']['batch_size'],
        learning_rate               = config['training']['learning_rate'],
        weight_decay                = config['training']['weight_decay'],
        warmup_steps                = warmup_steps,
        fp16                        = config['training']['fp16'],
        gradient_accumulation_steps = config['training']['gradient_accumulation_steps'],
        eval_strategy               = "steps",
        eval_steps                  = config['training']['eval_steps'],
        save_strategy               = "steps",
        save_steps                  = config['training']['eval_steps'],
        save_total_limit            = config['training']['save_total_limit'],
        logging_steps               = config['training']['logging_steps'],
        load_best_model_at_end      = True,
        metric_for_best_model       = "recall",
        seed                        = config['training']['seed'],
    )

    if config['loss']['type'] == 'focal':
        trainer = FocalLossTrainer(
            alpha=config['loss']['alpha'], gamma=config['loss']['gamma'],
            model=model, args=args, train_dataset=train_ds,
            eval_dataset=val_ds, compute_metrics=compute_metrics,
            data_collator=collator,
            callbacks=[EarlyStoppingCallback(
                early_stopping_patience=config['training']['early_stopping_patience'])])
    else:
        trainer = ContiguousTrainer(
            model=model, args=args, train_dataset=train_ds,
            eval_dataset=val_ds, compute_metrics=compute_metrics,
            data_collator=collator,
            callbacks=[EarlyStoppingCallback(
                early_stopping_patience=config['training']['early_stopping_patience'])])

    print("Training …")
    trainer.train()
    print("Test evaluation …")
    print(trainer.evaluate(test_ds))

    print(f"Saving model to {versioned_dir} …")
    trainer.save_model(versioned_dir)
    tokenizer.save_pretrained(versioned_dir)

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    model.to(device)
    compute_optimal_threshold(model, test_ds, collator, config, versioned_dir, device)
    print(f"\n[Train] Done. Model at: {versioned_dir}")


if __name__ == "__main__":
    import argparse
    p = argparse.ArgumentParser()
    p.add_argument('--config', default='configs/config.yaml')
    train(p.parse_args().config)
