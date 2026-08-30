"""Loads the two provided CSVs (synthetic_data/train.csv, synthetic_data/synthetic_dataset.csv),
keeps only spam-labeled rows (label==1) -- Blue DB per spec only ever holds messages the on-device
model has already flagged as spam -- and fills in the metadata columns the raw CSVs don't have:
message_id, channel, region, persona, message_type, sender, timestamp, content_type, needs_translation,
user_name, user_phone.

`message_type` is derived from the actual message text (clustering/message_type.py) rather than
the raw CSVs' `category` column, which for ~83% of rows just says "public_dataset"/"raw_dataset"
(the source file, not a fraud type). `persona` simulates the user-declared persona a real app
install would carry on UserMap -- sampled independent of region/message_type since we have no real
ground truth linking persona to fraud targeting (inventing that correlation would misrepresent
synthetic data as an observed pattern).

`user_name`/`user_phone` simulate the recipient who reported/received this message on their
device -- entirely synthetic identities (see `_synth_recipient`), never real PII, generated so
the demo pipeline has someone concrete to alert once a cluster crosses its threshold (see
db/seed/seed_blue_db.py::seed_users, which derives UserMap rows from these two columns). This is
separate from `sender`, which is the fraud message's spoofed originator, not the recipient.

Existing `metadata` JSON (present on some synthetic_dataset.csv rows, e.g. {"sender": ...}) is
preserved and takes priority over synthesized values.

Output: data/processed/blue_db_messages_enriched.csv -- one row per MessageRecord field, ready
for ingestion/run_clustering.py and then db/seed/seed_blue_db.py once MONGODB_URI is available.
"""

import ast
import json
import random
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pandas as pd

from clustering.message_type import classify_message_type
from clustering.persona import persona_names_and_weights
from clustering.region_weighting import region_names_and_weights

RAG_MODEL_ROOT = Path(__file__).resolve().parent.parent
SYNTHETIC_DIR = RAG_MODEL_ROOT / "synthetic_data"
OUTPUT_DIR = RAG_MODEL_ROOT / "data" / "processed"

CHANNELS = ["sms", "whatsapp", "telegram"]
CHANNEL_WEIGHTS = [0.55, 0.35, 0.10]  # SMS still dominant vector for these fraud categories in India

CATEGORY_TO_CONTENT_TYPE = {
    "direct_apk": "apk",
    "qr_code": "qr",
    "callback_only_fraud": "callback",
    "shortened_url_urgency": "url",
    "shortened_url_bare": "url",
}

random.seed(42)


def _parse_metadata(raw: str) -> dict:
    if not isinstance(raw, str) or not raw.strip():
        return {}
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        try:
            return ast.literal_eval(raw)
        except (ValueError, SyntaxError):
            return {}


# Common Indian first/last names spanning multiple regions/scripts-of-origin (transliterated),
# used only to generate synthetic recipient identities -- never sourced from or matched against
# any real person. Deliberately not persona/region-correlated for the same reason `persona` above
# is sampled independently: inventing a name<->region<->persona correlation would misrepresent
# synthetic data as an observed demographic pattern.
_FIRST_NAMES = [
    "Aarav", "Vivaan", "Aditya", "Vihaan", "Arjun", "Sai", "Reyansh", "Krishna",
    "Ishaan", "Rohan", "Ananya", "Diya", "Saanvi", "Aadhya", "Kiara", "Myra",
    "Priya", "Anjali", "Neha", "Pooja", "Rahul", "Amit", "Suresh", "Vikram",
    "Lakshmi", "Meera", "Sunita", "Geeta", "Rajesh", "Manoj", "Deepak", "Sanjay",
]
_LAST_NAMES = [
    "Sharma", "Verma", "Gupta", "Kumar", "Singh", "Patel", "Reddy", "Nair",
    "Iyer", "Rao", "Mehta", "Joshi", "Desai", "Pillai", "Chatterjee", "Banerjee",
    "Mukherjee", "Das", "Gowda", "Naidu", "Shetty", "Kulkarni", "Bose", "Menon",
]


def _synth_recipient() -> tuple[str, str]:
    """Synthesizes a (name, phone) pair for the message's recipient -- entirely synthetic,
    never real PII. See module docstring for why this is separate from `sender`."""
    name = f"{random.choice(_FIRST_NAMES)} {random.choice(_LAST_NAMES)}"
    phone = f"+91{random.randint(6, 9)}{random.randint(10**8, 10**9 - 1)}"
    return name, phone


def _synth_sender(category: str) -> str:
    if category in ("govt_impersonation",):
        return random.choice(["DL-eCHLAN", "AD-CYBRCL", "VM-GOVTIN"])
    if category in ("utility_scam",):
        return random.choice(["VK-ELECBD", "AD-PWRBIL"])
    if category in ("financial_reward", "affinity_scam", "loan_app"):
        return f"+91{random.randint(6, 9)}{random.randint(10**8, 10**9 - 1)}"
    if category in ("callback_only_fraud",):
        return f"+91{random.randint(6, 9)}{random.randint(10**8, 10**9 - 1)}"
    return f"+91{random.randint(6, 9)}{random.randint(10**8, 10**9 - 1)}"


def _random_recent_timestamp(days_back: int = 30) -> datetime:
    now = datetime.now(timezone.utc)
    # bias toward more recent days (skewed, not uniform) so a "trailing 10-15 day" heatmap query
    # has realistic density instead of a flat distribution
    delta_days = random.triangular(0, days_back, 2)
    return now - timedelta(days=delta_days, seconds=random.randint(0, 86399))


def load_and_enrich() -> pd.DataFrame:
    df_train = pd.read_csv(SYNTHETIC_DIR / "train.csv")
    df_synth = pd.read_csv(SYNTHETIC_DIR / "synthetic_dataset.csv")

    # align columns: train.csv has an extra stratify_col we don't need downstream
    df_train = df_train.drop(columns=["stratify_col"], errors="ignore")

    df = pd.concat([df_train, df_synth], ignore_index=True)
    df = df[df["label"] == 1].reset_index(drop=True)  # Blue DB only ever sees spam-flagged messages

    region_names, region_weights = region_names_and_weights()
    persona_names, persona_weights = persona_names_and_weights()

    records = []
    for _, row in df.iterrows():
        existing_meta = _parse_metadata(row.get("metadata", "{}"))
        category = row.get("category", "unknown")
        language = row.get("language", "unknown")
        text = row["text"]

        sender = existing_meta.get("sender") or _synth_sender(category)
        region = random.choices(region_names, weights=region_weights, k=1)[0]
        persona = random.choices(persona_names, weights=persona_weights, k=1)[0]
        channel = random.choices(CHANNELS, weights=CHANNEL_WEIGHTS, k=1)[0]
        content_type = CATEGORY_TO_CONTENT_TYPE.get(category, "text_only")
        message_type = classify_message_type(text)
        user_name, user_phone = _synth_recipient()

        is_english = language == "en"
        records.append(
            {
                "message_id": str(uuid.uuid4()),
                "original_message": text,
                "language": language,
                "message_english": text if is_english else None,
                "needs_translation": not is_english,
                "channel": channel,
                "content_type": content_type,
                "category": category,          # kept for eval/debugging; not part of MessageRecord schema
                "message_type": message_type,  # real clustering signal, derived from text
                "region": region,
                "persona": persona,
                "sender": sender,
                "user_name": user_name,        # synthetic recipient identity -- see module docstring
                "user_phone": user_phone,      # synthetic recipient identity -- see module docstring
                "timestamp": _random_recent_timestamp().isoformat(),
                "source_dataset": row.get("source", "unknown"),
            }
        )

    return pd.DataFrame.from_records(records)


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    enriched = load_and_enrich()
    out_path = OUTPUT_DIR / "blue_db_messages_enriched.csv"
    enriched.to_csv(out_path, index=False)
    print(f"Wrote {len(enriched)} spam-flagged messages with metadata to {out_path}")
    print(enriched["message_type"].value_counts())
    print()
    print(enriched["persona"].value_counts())


if __name__ == "__main__":
    main()
