"""Exports the trained checkpoint's SentencePiece Unigram vocabulary into a compact, mobile-
friendly text asset for the Android on-device tokenizer (see
SurakshaSathi feature/messagescan/data/classifier/tokenizer/AlbertUnigramTokenizer.kt), plus the
special-token IDs needed to wrap sequences the same way the training-time HF tokenizer does.

This only reads tokenizer.json (stdlib json only, no torch/transformers needed) -- it is
deliberately independent of quantize.py and safe to run without the heavier ML deps installed.

Usage:
    python scripts/export_mobile_tokenizer_vocab.py --model_dir final_model_20260706_121821 \
        --out_dir ../SurakshaSathi/app/src/main/assets
"""

import argparse
import json
import os


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_dir", required=True)
    parser.add_argument("--out_dir", required=True)
    args = parser.parse_args()

    tokenizer_path = os.path.join(args.model_dir, "tokenizer.json")
    with open(tokenizer_path, encoding="utf-8") as f:
        tok = json.load(f)

    model = tok["model"]
    assert model["type"] == "Unigram", f"expected Unigram model, got {model['type']}"
    vocab = model["vocab"]  # list of [piece, score], index == token id for the base model vocab
    unk_id = model["unk_id"]
    byte_fallback = model.get("byte_fallback", False)
    assert not byte_fallback, "byte_fallback vocabularies need a different mobile tokenizer path"

    added_tokens = {t["content"]: t["id"] for t in tok.get("added_tokens", [])}
    for required in ("<pad>", "<unk>", "[CLS]", "[SEP]"):
        assert required in added_tokens, f"missing expected special token {required!r}"

    os.makedirs(args.out_dir, exist_ok=True)

    vocab_out_path = os.path.join(args.out_dir, "spam_classifier_vocab.txt")
    with open(vocab_out_path, "w", encoding="utf-8", newline="\n") as f:
        for piece, score in vocab:
            # Pieces never contain literal tabs/newlines in a SentencePiece Unigram vocab --
            # tab-separated is safe and far cheaper to parse on-device than JSON for 200k lines.
            f.write(f"{piece}\t{score}\n")

    special_out_path = os.path.join(args.out_dir, "spam_classifier_special_tokens.json")
    with open(special_out_path, "w", encoding="utf-8") as f:
        json.dump(
            {
                "pad_id": added_tokens["<pad>"],
                "unk_id": unk_id,
                "cls_id": added_tokens["[CLS]"],
                "sep_id": added_tokens["[SEP]"],
                "vocab_size": len(vocab),
            },
            f,
            indent=2,
        )

    print(f"Wrote {len(vocab)} pieces to {vocab_out_path}")
    print(f"Wrote special token IDs to {special_out_path}")
    print(f"pad_id={added_tokens['<pad>']} unk_id={unk_id} cls_id={added_tokens['[CLS]']} sep_id={added_tokens['[SEP]']}")


if __name__ == "__main__":
    main()
