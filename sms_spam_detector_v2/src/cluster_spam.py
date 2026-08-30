"""
Spam Message Clustering
=======================
Problem: "Fake mobile apps circulated via SMS/WhatsApp → phishing and financial fraud."

Clusters SPAM-ONLY messages into meaningful fraud categories aligned to the problem.
HAM messages are never included.

Fixes vs previous version:
  1. Auto-installs sentence-transformers via pip if missing (no silent failure).
  2. Falls back cleanly to TF-IDF char n-grams if install fails.
  3. Unique label assignment via greedy score matrix — no more "direct_apk_2", "direct_apk_3".
  4. Taxonomy is problem-statement aligned: 15 distinct fraud category labels.

Usage:
    python src/cluster_spam.py \
        --input   data/raw/predictions_synthetic_fake_app_dataset_v2.csv \
        --output  data/clusters/ \
        --label_col true_label \
        --n_clusters 12

    # Auto-select k:
    python src/cluster_spam.py --input ... --output ... --auto_k

    # Force TF-IDF (no downloads):
    python src/cluster_spam.py --input ... --output ... --tfidf
"""

import argparse, os, re, sys, json, subprocess, warnings
from collections import defaultdict
from typing import List, Dict, Optional

import numpy as np
import pandas as pd
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import normalize

warnings.filterwarnings("ignore")


# ── Taxonomy ──────────────────────────────────────────────────────────────────

TAXONOMY = [
    {"label": "direct_apk_download",
     "weight": 2.5,
     "keywords": [".apk",".xapk","apk file","apk link","install app","install kare",
                  "install karo","download app","डाउनलोड","इंस्टॉल","ডাউনলোড","ইনস্টল"]},
    {"label": "qr_code_sideload_scam",
     "weight": 2.5,
     "keywords": ["scan qr","qr code","scan this","scan to download","scan to install",
                  "qr scan","स्कैन","স্ক্যান","barcode","qr कोड","scan karo"]},
    {"label": "banking_kyc_phishing",
     "weight": 2.0,
     "keywords": ["sbi","hdfc","icici","rbl","axis","kotak","bank","kyc","otp",
                  "account block","netbanking","net banking","mobile banking",
                  "बैंक","खाता","ओटीपी","ব্যাঙ্ক","অ্যাকাউন্ট","ওটিপি"]},
    {"label": "upi_payment_fraud",
     "weight": 2.0,
     "keywords": ["upi","phonepe","gpay","google pay","paytm","bhim","collect request",
                  "scan to receive","upi pin","payment failed","cashback","refund",
                  "यूपीआई","पेटीएम","ফোনপে"]},
    {"label": "govt_challan_apk",
     "weight": 2.5,
     "keywords": ["challan","e-challan","ई-चालान","চালান","traffic fine",
                  "traffic violation","rto","mparivahan","m parivahan","parivahan",
                  "driving licence","driving license","rtochalan","e challan","toll"]},
    {"label": "delivery_parcel_fraud",
     "weight": 2.0,
     "keywords": ["parcel","delivery","courier","package","shipment","india post",
                  "delhivery","track","reschedule","failed delivery","missed delivery",
                  "डिलीवरी","पार्सल","কুরিয়ার","ডেলিভারি"]},
    {"label": "loan_app_fraud",
     "weight": 2.0,
     "keywords": ["loan","instant loan","quick loan","easy loan","personal loan",
                  "loan approved","pre-approved","emi","credit limit",
                  "ऋण","लोन","तुरंत लोन","ঋণ","লোন","borrow","lending"]},
    {"label": "electricity_utility_fraud",
     "weight": 2.0,
     "keywords": ["electricity","electric","bijli","power","bses","mseb","bescom",
                  "bill due","bill pending","connection disconnect","meter reading",
                  "bijlee","बिजली","बिजली बिल","utility","gas connection"]},
    {"label": "aadhaar_pan_kyc_fraud",
     "weight": 2.0,
     "keywords": ["aadhaar","aadhar","pan card","pan number","income tax",
                  "uidai","epfo","pf","provident fund","e-kyc","video kyc",
                  "आधार","पैन","आयकर","আধার","প্যান কার্ড"]},
    {"label": "lottery_reward_cashback_scam",
     "weight": 2.0,
     "keywords": ["lottery","winner","won","prize","reward","congratulations",
                  "lucky draw","selected","gift","free","claim now","voucher",
                  "जीता","बधाई","इनाम","জিতেছেন","পুরস্কার","cashback won"]},
    {"label": "callback_vishing_fraud",
     "weight": 2.5,
     "keywords": ["call now","call immediately","call us","call back","1800",
                  "toll free","helpline","customer care","our officer","our executive",
                  "bank officer","कॉल करें","हेल्पलाइन","আমাদের কল","dial"]},
    {"label": "investment_trading_fraud",
     "weight": 2.0,
     "keywords": ["invest","investment","trading","stock","share market",
                  "mutual fund","crypto","bitcoin","profit","guaranteed return",
                  "roi","निवेश","शेयर मार्केट","ট্রেডিং"]},
    {"label": "telecom_recharge_fraud",
     "weight": 1.8,
     "keywords": ["recharge","data pack","talktime","jio","airtel","vi",
                  "vodafone","bsnl","free recharge","data offer","plan expired",
                  "रिचार्ज","রিচার্জ","validity","sim blocked"]},
    {"label": "job_recruitment_fraud",
     "weight": 1.8,
     "keywords": ["job","hiring","recruitment","vacancy","apply now","salary",
                  "work from home","part time","earn money","job offer","interview",
                  "नौकरी","वेतन","কাজ","চাকরি","remote work","freelance"]},
    {"label": "other_spam",
     "weight": 0.5,
     "keywords": []},
]

FALLBACK_LABEL = "other_spam"


# ── Scoring & label assignment ─────────────────────────────────────────────────

def _score(text: str) -> Dict[str, float]:
    tl = text.lower()
    scores = {}
    for entry in TAXONOMY:
        hits = sum(1 for kw in entry["keywords"] if kw.lower() in tl)
        scores[entry["label"]] = entry["weight"] * hits
    return scores


def assign_unique_labels(df: pd.DataFrame, cluster_col: str, text_col: str) -> pd.DataFrame:
    """
    Greedy best-match: each cluster gets exactly one label from the taxonomy.
    No two clusters share the same primary label.
    """
    cids   = sorted(df[cluster_col].unique())
    labels = [e["label"] for e in TAXONOMY]

    # Build mean-score matrix
    matrix: Dict[int, Dict[str, float]] = {}
    for cid in cids:
        texts  = df[df[cluster_col] == cid][text_col].astype(str).tolist()
        totals = defaultdict(float)
        for t in texts:
            for lbl, sc in _score(t).items():
                totals[lbl] += sc
        n = max(len(texts), 1)
        matrix[cid] = {lbl: totals[lbl] / n for lbl in labels}

    # Greedy assignment
    rem_cids   = list(cids)
    rem_labels = [l for l in labels if l != FALLBACK_LABEL]
    assigned: Dict[int, str] = {}

    while rem_cids and rem_labels:
        best_sc, best_cid, best_lbl = -1.0, None, None
        for cid in rem_cids:
            for lbl in rem_labels:
                sc = matrix[cid].get(lbl, 0.0)
                if sc > best_sc:
                    best_sc, best_cid, best_lbl = sc, cid, lbl
        if best_cid is None or best_sc == 0:
            break
        assigned[best_cid] = best_lbl
        rem_cids.remove(best_cid)
        rem_labels.remove(best_lbl)

    for i, cid in enumerate(rem_cids):
        assigned[cid] = FALLBACK_LABEL if i == 0 else f"{FALLBACK_LABEL}_{i+1}"

    df = df.copy()
    df["cluster_label"] = df[cluster_col].map(assigned)
    return df


# ── Embeddings ────────────────────────────────────────────────────────────────

def _ensure_sentence_transformers() -> bool:
    try:
        import sentence_transformers  # noqa
        return True
    except ImportError:
        print("[Cluster] Installing sentence-transformers …")
        try:
            subprocess.check_call([sys.executable, "-m", "pip", "install",
                                   "sentence-transformers", "--quiet"])
            import sentence_transformers  # noqa
            print("[Cluster] sentence-transformers installed.")
            return True
        except Exception as e:
            print(f"[Cluster] Install failed ({e}). Using TF-IDF fallback.")
            return False


def embed_sentence_transformers(texts: List[str]) -> np.ndarray:
    from sentence_transformers import SentenceTransformer
    model_name = "paraphrase-multilingual-MiniLM-L12-v2"
    print(f"[Cluster] Loading {model_name} …")
    m = SentenceTransformer(model_name)
    return m.encode(texts, batch_size=64, show_progress_bar=True,
                    normalize_embeddings=True)


def embed_tfidf(texts: List[str]) -> np.ndarray:
    print("[Cluster] Using TF-IDF char n-grams (multilingual, no download needed).")
    vec = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4),
                          max_features=10000, sublinear_tf=True)
    return normalize(vec.fit_transform(texts).toarray(), norm="l2")


def get_embeddings(texts: List[str], force_tfidf: bool = False) -> np.ndarray:
    if force_tfidf:
        return embed_tfidf(texts)
    return embed_sentence_transformers(texts) if _ensure_sentence_transformers() else embed_tfidf(texts)


# ── Clustering ────────────────────────────────────────────────────────────────

def find_best_k(embeddings: np.ndarray, k_min: int = 6, k_max: int = 14) -> int:
    n   = embeddings.shape[0]
    idx = np.random.choice(n, min(n, 3000), replace=False)
    X   = embeddings[idx]
    best_k, best_sc = k_min, -1.0
    print(f"[Cluster] Silhouette sweep k={k_min}…{k_max}")
    for k in range(k_min, min(k_max + 1, n)):
        km  = KMeans(n_clusters=k, random_state=42, n_init=5, max_iter=200)
        lbs = km.fit_predict(X)
        sc  = silhouette_score(X, lbs, sample_size=min(2000, len(X)))
        print(f"    k={k:2d}  sil={sc:.4f}")
        if sc > best_sc:
            best_sc, best_k = sc, k
    print(f"[Cluster] Best k={best_k}")
    return best_k


# ── Main ──────────────────────────────────────────────────────────────────────

def run_clustering(input_csv: str, output_dir: str, label_col: str = "true_label",
                   text_col: str = "text", n_clusters: Optional[int] = None,
                   auto_k: bool = False, force_tfidf: bool = False):

    os.makedirs(output_dir, exist_ok=True)
    print(f"[Cluster] Reading {input_csv} …")
    df = pd.read_csv(input_csv)
    df.columns = [c.strip() for c in df.columns]

    if label_col not in df.columns:
        for alt in ["label", "true_label", "Label", "spam"]:
            if alt in df.columns:
                label_col = alt; break
        else:
            raise ValueError(f"Label column not found. Columns: {df.columns.tolist()}")

    if text_col not in df.columns:
        raise ValueError(f"Text column '{text_col}' not found. Columns: {df.columns.tolist()}")

    mask   = df[label_col].astype(str).str.strip().str.lower().isin({"spam","1","true"})
    df_sp  = df[mask].reset_index(drop=True)
    print(f"[Cluster] Total={len(df)}  SPAM={len(df_sp)}  HAM(excluded)={(~mask).sum()}")

    if len(df_sp) < 15:
        raise ValueError(f"Too few spam messages ({len(df_sp)}) to cluster.")

    texts = df_sp[text_col].astype(str).tolist()
    emb   = get_embeddings(texts, force_tfidf=force_tfidf)

    k = (find_best_k(emb, k_min=min(6, len(df_sp)-1),
                          k_max=min(len(TAXONOMY)-1, len(df_sp)-1))
         if (auto_k or n_clusters is None)
         else min(n_clusters, len(df_sp)-1))
    print(f"[Cluster] KMeans k={k} on {len(texts)} messages …")

    df_sp = df_sp.copy()
    df_sp["cluster_id"] = KMeans(n_clusters=k, random_state=42, n_init=10,
                                  max_iter=300).fit_predict(emb)
    df_sp = assign_unique_labels(df_sp, "cluster_id", text_col)

    summary = []
    print(f"\n[Cluster] Saving CSVs to {output_dir}/")
    for lbl in sorted(df_sp["cluster_label"].unique()):
        sub  = df_sp[df_sp["cluster_label"] == lbl]
        path = os.path.join(output_dir, f"cluster_{lbl}.csv")
        sub.to_csv(path, index=False)
        sample = sub[text_col].iloc[0][:100] if len(sub) else ""
        summary.append({"cluster_label": lbl, "count": len(sub),
                         "file": os.path.basename(path), "sample": sample})
        print(f"  {lbl:42s}  {len(sub):5d} msgs")

    df_sp.to_csv(os.path.join(output_dir, "all_spam_clustered.csv"), index=False)
    with open(os.path.join(output_dir, "cluster_summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print(f"\n{'─'*60}")
    print(f"  {'FRAUD CATEGORY':42}  {'COUNT':>5}")
    print(f"{'─'*60}")
    for row in sorted(summary, key=lambda r: -r["count"]):
        print(f"  {row['cluster_label']:42}  {row['count']:>5}")
    print(f"{'─'*60}")
    print(f"  {'TOTAL SPAM':42}  {len(df_sp):>5}")
    print(f"{'─'*60}\n")
    return df_sp


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="Cluster spam messages by fraud type")
    ap.add_argument("--input",      required=True)
    ap.add_argument("--output",     default="data/clusters")
    ap.add_argument("--label_col",  default="true_label")
    ap.add_argument("--text_col",   default="text")
    ap.add_argument("--n_clusters", type=int, default=None)
    ap.add_argument("--auto_k",     action="store_true")
    ap.add_argument("--tfidf",      action="store_true")
    args = ap.parse_args()
    run_clustering(args.input, args.output, args.label_col,
                   args.text_col, args.n_clusters, args.auto_k, args.tfidf)
