"""Live accuracy evaluation: samples N labeled messages (label 0 = legit, label 1 = spam) from
the real CSVs, sends each through the actual running FastAPI /v1/scan/message endpoint, and
scores the returned verdict against the known ground-truth label.

Verdict -> binary mapping: BLOCK or QUARANTINE = "flagged as fraud" (1), ALLOW = "not fraud" (0).
QUARANTINE counts as a positive prediction because it's still a fraud warning shown to the user --
the metric we actually care about is "did we warn the user," not the three-way verdict split.

Usage: start the API first (uvicorn api.main:app), then:
    python scripts/evaluate_rag.py --n 40
"""

import argparse
import random
import time

import httpx
import pandas as pd

API_URL = "http://127.0.0.1:8000/v1/scan/message"


def sample_labeled_messages(n_per_class: int, seed: int = 7) -> pd.DataFrame:
    df1 = pd.read_csv("synthetic_data/train.csv")
    df2 = pd.read_csv("synthetic_data/synthetic_dataset.csv")
    df = pd.concat([df1, df2], ignore_index=True)

    rng = random.Random(seed)
    spam = df[df["label"] == 1].sample(n=n_per_class, random_state=seed)
    legit = df[df["label"] == 0].sample(n=n_per_class, random_state=seed)
    combined = pd.concat([spam, legit], ignore_index=True)
    return combined.sample(frac=1, random_state=seed).reset_index(drop=True)  # shuffle order


def scan_one(message_id: str, text: str, language: str) -> dict:
    resp = httpx.post(
        API_URL,
        json={"message_id": message_id, "original_message": text, "language": language},
        timeout=40,
    )
    resp.raise_for_status()
    return resp.json()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--n", type=int, default=20, help="messages per class (spam/legit)")
    args = parser.parse_args()

    df = sample_labeled_messages(args.n)
    print(f"Evaluating {len(df)} messages ({args.n} spam + {args.n} legit)...\n")

    tp = fp = tn = fn = 0
    latencies = []
    errors = 0

    for i, row in df.iterrows():
        language = row.get("language", "en") if pd.notna(row.get("language")) else "en"
        try:
            start = time.monotonic()
            result = scan_one(f"eval-{i}", row["text"], language)
            elapsed_ms = (time.monotonic() - start) * 1000
            latencies.append(elapsed_ms)

            predicted_fraud = result["report"]["verdict"] in ("BLOCK", "QUARANTINE")
            actual_fraud = row["label"] == 1

            if predicted_fraud and actual_fraud:
                tp += 1
            elif predicted_fraud and not actual_fraud:
                fp += 1
            elif not predicted_fraud and not actual_fraud:
                tn += 1
            else:
                fn += 1

            status = "OK" if predicted_fraud == actual_fraud else "WRONG"
            print(f"[{i+1}/{len(df)}] {status} true={row['label']} verdict={result['report']['verdict']} ({elapsed_ms:.0f}ms)")
        except Exception as e:
            errors += 1
            print(f"[{i+1}/{len(df)}] ERROR: {e}")

    total_scored = tp + fp + tn + fn
    print("\n" + "=" * 50)
    print(f"Scored: {total_scored}/{len(df)} ({errors} errors)")
    print(f"TP={tp}  FP={fp}  TN={tn}  FN={fn}")

    if total_scored:
        accuracy = (tp + tn) / total_scored
        precision = tp / (tp + fp) if (tp + fp) else float("nan")
        recall = tp / (tp + fn) if (tp + fn) else float("nan")
        f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else float("nan")
        print(f"Accuracy:  {accuracy:.3f}")
        print(f"Precision: {precision:.3f}  (of messages flagged fraud, how many actually were)")
        print(f"Recall:    {recall:.3f}  (of actual fraud messages, how many we caught)")
        print(f"F1:        {f1:.3f}")

    if latencies:
        latencies.sort()
        p50 = latencies[len(latencies) // 2]
        p95 = latencies[int(len(latencies) * 0.95)]
        print(f"\nLatency: mean={sum(latencies)/len(latencies):.0f}ms  p50={p50:.0f}ms  p95={p95:.0f}ms")


if __name__ == "__main__":
    main()
