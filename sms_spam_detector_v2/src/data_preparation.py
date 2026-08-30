"""
Data Preparation Pipeline for SMS Sideloading Detection.

Responsible for:
1. Loading raw dataset files (e.g., from HuggingFace, Kaggle, synthetic).
2. Merging them into a unified schema.
3. Preprocessing text (cleaning, normalization).
4. Stratified splitting into train/val/test sets.
5. Saving processed artifacts for training.
"""

import os
import pandas as pd
from sklearn.model_selection import train_test_split
from datasets import Dataset, DatasetDict, load_dataset
import yaml
import sys

# Import feature extractor for relabeling
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from src.feature_extractor import load_feature_extractor

def load_config(config_path: str) -> dict:
    with open(config_path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)

def clean_text(text: str) -> str:
    """Basic text cleaning while preserving important features like URLs."""
    if not isinstance(text, str):
        return ""
    text = text.replace('\n', ' ').replace('\r', '')
    text = ' '.join(text.split())
    return text


_LABEL_MAP = {'spam': 1, 'ham': 0, 'not_spam': 0, '1': 1, '0': 0}


def load_raw_csv_datasets(config: dict, raw_dir: str, seed: int, heldout_frac: float = 0.15):
    """
    Ingest hand-curated / externally-generated CSVs from data/raw/*.csv
    (already correctly labeled, unlike the noisy relabeled HF public
    datasets) directly into the training corpus.

    Each file is split stratified-by-label into a train_pool (goes into the
    merged training corpus below) and a heldout slice, which is written back
    out as "<name>_heldout.csv" next to the source file. That heldout slice
    is what evaluate.py should be pointed at afterwards — evaluating on rows
    the model actually trained on would be data leakage, not a real test.

    "<name>_heldout.csv" files already present are skipped as a source (so
    re-running this script doesn't fold the held-out test set back into
    training on a subsequent run).
    """
    train_pool_dfs = []
    if not os.path.isdir(raw_dir):
        return train_pool_dfs

    for fname in sorted(os.listdir(raw_dir)):
        if not fname.endswith('.csv') or fname.endswith('_heldout.csv'):
            continue
        fpath = os.path.join(raw_dir, fname)
        print(f"Loading raw dataset {fpath} ...")
        try:
            raw_df = pd.read_csv(fpath)
        except Exception as e:
            print(f"  WARNING: could not read {fpath}: {e}")
            continue
        raw_df.columns = [c.strip().lower() for c in raw_df.columns]
        label_col = 'label' if 'label' in raw_df.columns else (
            'true_label' if 'true_label' in raw_df.columns else None)
        if 'text' not in raw_df.columns or label_col is None:
            print(f"  WARNING: {fpath} missing text/label columns, skipping")
            continue

        raw_df = raw_df[['text', label_col]].rename(columns={label_col: 'label'}).dropna()
        raw_df['text'] = raw_df['text'].apply(clean_text)
        if raw_df['label'].dtype == object:
            raw_df['label'] = raw_df['label'].astype(str).str.strip().str.lower().map(_LABEL_MAP)
        raw_df = raw_df.dropna(subset=['label'])
        raw_df['label'] = raw_df['label'].astype(int)
        raw_df['language'] = 'unknown'
        raw_df['category'] = 'raw_dataset'
        raw_df['metadata'] = '{}'
        raw_df['source'] = fname

        try:
            train_pool, heldout = train_test_split(
                raw_df, test_size=heldout_frac,
                stratify=raw_df['label'], random_state=seed)
        except ValueError:
            train_pool, heldout = train_test_split(
                raw_df, test_size=heldout_frac, random_state=seed)

        heldout_path = os.path.join(raw_dir, f"{os.path.splitext(fname)[0]}_heldout.csv")
        heldout_out = heldout[['text', 'label']].copy()
        heldout_out['label'] = heldout_out['label'].map({1: 'spam', 0: 'ham'})
        heldout_out.to_csv(heldout_path, index=False)
        print(f"  {fname}: {len(train_pool)} → train pool, {len(heldout)} → held-out "
              f"({heldout_path})")

        train_pool_dfs.append(train_pool[['text', 'label', 'language', 'category',
                                          'metadata', 'source']])

    return train_pool_dfs


def prepare_dataset(config_path: str):
    """Load, merge, and split the dataset."""
    config = load_config(config_path)
    
    synthetic_path = os.path.join(config['paths']['synthetic_data_dir'], 'synthetic_dataset.csv')
    processed_dir = config['paths']['processed_data_dir']
    os.makedirs(processed_dir, exist_ok=True)
    
    # In a full implementation, we would load Kaggle/HF datasets here.
    # For now, we rely on the high-quality synthetic dataset we generated,
    # which provides a solid foundation for all specific sideloading vectors.
    print(f"Loading synthetic data from {synthetic_path}...")
    df = pd.read_csv(synthetic_path)
    
    df['text'] = df['text'].apply(clean_text)
    df = df.dropna(subset=['text', 'label'])
    
    # --- Load Public Datasets & Relabel ---
    print("Loading public datasets from HuggingFace...")
    public_dfs = []
    
    # Initialize feature extractor for relabeling
    feature_extractor = load_feature_extractor(config_path)
    
    # List of datasets to attempt loading
    hf_datasets_to_load = [
        {'id': 'sms_spam', 'text_col': 'sms', 'label_col': 'label'},
        {'id': 'CloveAI/india-spam-sms', 'text_col': 'text', 'label_col': 'label'},
        {'id': 'ashqal/spam-text-classification', 'text_col': 'text', 'label_col': 'label'}
    ]
    
    for ds_info in hf_datasets_to_load:
        ds_id = ds_info['id']
        try:
            print(f"Trying to load {ds_id}...")
            hf_ds = load_dataset(ds_id, split='train')
            hf_df = hf_ds.to_pandas()
            
            # Map columns
            if ds_info['text_col'] in hf_df.columns:
                hf_df = hf_df.rename(columns={ds_info['text_col']: 'text'})
            if ds_info['label_col'] in hf_df.columns:
                hf_df = hf_df.rename(columns={ds_info['label_col']: 'label'})
                
            hf_df['language'] = 'en' # Assuming English for base datasets unless detected
            hf_df['category'] = 'public_dataset'
            hf_df['source'] = ds_id
            hf_df['metadata'] = '{}'
            
            required_cols = ['text', 'label', 'language', 'category', 'metadata', 'source']
            for col in required_cols:
                if col not in hf_df.columns:
                    hf_df[col] = ''
            
            hf_df = hf_df[required_cols]
            hf_df['text'] = hf_df['text'].apply(clean_text)
            
            # Normalize labels if they are string ("spam", "ham")
            if hf_df['label'].dtype == object:
                hf_df['label'] = hf_df['label'].astype(str).str.lower().map({'spam': 1, 'ham': 0, '1': 1, '0': 0})
            
            hf_df = hf_df.dropna(subset=['label', 'text'])
            hf_df['label'] = hf_df['label'].astype(int)
            
            # --- CRITICAL: Relabeling for Sideloading Specificity ---
            # Public datasets label generic marketing/promo as "spam=1".
            # Our model must treat generic spam as "ham=0" and ONLY sideloading vectors as "spam=1".
            print(f"Relabeling {ds_id} to filter only sideloading vectors...")
            
            def relabel_sideloading(row):
                if row['label'] == 0:
                    return 0 # Already benign
                
                # If public dataset claims it's spam, verify if it's a sideloading vector
                ext_result = feature_extractor.extract(row['text'])
                
                # If it has an APK, deep link, or is flagged by our heuristics as sideloading
                if len(ext_result.sideloading_vectors) > 0 or ext_result.has_url:
                    # We keep it as 1 if it has a URL (potential phishing/sideloading)
                    return 1
                else:
                    # It's just generic text spam (e.g., "Buy this loan!"), no link/vector.
                    # For our specific problem statement, this is NOT a threat. Relabel to 0.
                    return 0
                    
            hf_df['label'] = hf_df.apply(relabel_sideloading, axis=1)
            
            public_dfs.append(hf_df)
            print(f"Successfully loaded and relabeled {len(hf_df)} samples from {ds_id}.")
        except Exception as e:
            print(f"Warning: Could not load public dataset {ds_id}. Error: {e}")
        
    # --- Raw curated CSVs (data/raw/*.csv) — already correctly labeled,
    #     domain-specific fake-app-sideloading data. Held out slice is
    #     written to data/raw/<name>_heldout.csv for a leakage-free final eval.
    raw_dfs = load_raw_csv_datasets(
        config, config['paths']['raw_data_dir'], seed=config['training']['seed'])

    # --- Merge Datasets ---
    all_extra = public_dfs + raw_dfs
    if all_extra:
        print("Merging synthetic, raw, and public datasets...")
        df = pd.concat([df] + all_extra, ignore_index=True)
        # Drop duplicates based on text to avoid overlapping exact same spam messages
        df = df.drop_duplicates(subset=['text'], keep='first')
        print(f"Total merged dataset size: {len(df)} samples")
    
    # Stratified split based on label and language (if available)
    try:
        df['stratify_col'] = df['label'].astype(str) + "_" + df['language'].astype(str)
        train_val, test = train_test_split(
            df, 
            test_size=config['data']['test_size'], 
            stratify=df['stratify_col'],
            random_state=config['training']['seed']
        )
        train, val = train_test_split(
            train_val,
            test_size=config['data']['val_size'] / (1.0 - config['data']['test_size']),
            stratify=train_val['stratify_col'],
            random_state=config['training']['seed']
        )
    except Exception as e:
        print(f"Stratified split failed ({e}), falling back to standard split.")
        train_val, test = train_test_split(
            df, test_size=config['data']['test_size'], random_state=config['training']['seed']
        )
        train, val = train_test_split(
            train_val, test_size=config['data']['val_size'] / (1.0 - config['data']['test_size']), random_state=config['training']['seed']
        )
        
    print(f"Dataset split complete:")
    print(f"Train: {len(train)}")
    print(f"Val:   {len(val)}")
    print(f"Test:  {len(test)}")
    
    # Save processed CSVs
    train.to_csv(os.path.join(processed_dir, 'train.csv'), index=False)
    val.to_csv(os.path.join(processed_dir, 'val.csv'), index=False)
    test.to_csv(os.path.join(processed_dir, 'test.csv'), index=False)
    
    # Convert to HuggingFace DatasetDict
    dataset = DatasetDict({
        'train': Dataset.from_pandas(train),
        'validation': Dataset.from_pandas(val),
        'test': Dataset.from_pandas(test)
    })
    
    dataset.save_to_disk(os.path.join(processed_dir, 'hf_dataset'))
    print("Dataset saved successfully.")

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', type=str, default='/scratch/m25cse012/sms_spam_detector/configs/config.yaml')
    args = parser.parse_args()
    prepare_dataset(args.config)
