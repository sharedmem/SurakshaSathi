"""One-off: export config.yaml's keyword/brand data to a JSON asset for the Android
FeatureExtractor.kt port, guaranteeing byte-for-byte fidelity with the Python source
of truth instead of hand-transcribing ~150 multilingual keyword phrases."""
import yaml
import json
import os

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG_PATH = os.path.join(HERE, "configs", "config.yaml")
OUT_PATH = os.path.join(
    "D:\\", "SBI", "SurakshaSathi", "app", "src", "main", "assets", "feature_extractor_keywords.json"
)

with open(CONFIG_PATH, encoding="utf-8") as f:
    cfg = yaml.safe_load(f)

out = {
    "keywords": cfg["keywords"],
    "url_shorteners": cfg["url_shorteners"],
    "impersonation_brands": cfg["impersonation_brands"],
    "suspicious_extensions": cfg["suspicious_extensions"],
}

with open(OUT_PATH, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=None)

print("categories:", list(out["keywords"].keys()))
print("total keyword phrases:", sum(len(words) for cat in out["keywords"].values() for words in cat.values()))
print("shorteners:", len(out["url_shorteners"]))
print("brands:", len(out["impersonation_brands"]))
print("wrote:", OUT_PATH)
