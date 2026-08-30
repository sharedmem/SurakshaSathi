"""Text -> fixed-size vector, then PCA projection down to settings.embedding_dim_reduced.

Primary encoder: `paraphrase-multilingual-MiniLM-L12-v2` (sentence-transformers, 384-dim),
natively multilingual across Hindi/Tamil/Telugu/Bengali/etc. -- no translation step needed before
clustering (translation is still used separately for the LLM prompt). This was blocked earlier
in local dev by 0 bytes free on C:; disk space has since been freed and the model runs fine here.

`TfidfCharEncoder` is kept as a dependency-free fallback (e.g. an offline dev box, or if torch
isn't installed) -- `ReducedEmbedder` picks whichever is available at construction time so the
PCA + DenStream + region-weighting code downstream never has to know which one is active.
"""

import numpy as np
from sklearn.decomposition import PCA
from sklearn.feature_extraction.text import TfidfVectorizer

from config.settings import settings


class MultilingualSentenceEncoder:
    """Primary encoder: real multilingual sentence-transformer, no fitting required (pretrained)."""

    def __init__(self, model_name: str = "paraphrase-multilingual-MiniLM-L12-v2"):
        from sentence_transformers import SentenceTransformer

        self.model = SentenceTransformer(model_name)
        self._fitted = True  # pretrained -- fit() is a no-op, kept for interface parity

    def fit(self, texts: list[str]) -> "MultilingualSentenceEncoder":
        return self

    def transform(self, texts: list[str]) -> np.ndarray:
        return np.asarray(self.model.encode(texts, normalize_embeddings=True))


class TfidfCharEncoder:
    """Dependency-free fallback: character n-gram TF-IDF, script-agnostic (works across
    Devanagari, Tamil, Latin, etc. without per-language tokenization) but has no semantic
    understanding -- purely lexical overlap. Used only if sentence-transformers isn't installed."""

    def __init__(self, max_features: int = 4096):
        self.vectorizer = TfidfVectorizer(
            analyzer="char_wb", ngram_range=(2, 4), max_features=max_features
        )
        self._fitted = False

    def fit(self, texts: list[str]) -> "TfidfCharEncoder":
        self.vectorizer.fit(texts)
        self._fitted = True
        return self

    def transform(self, texts: list[str]) -> np.ndarray:
        if not self._fitted:
            raise RuntimeError("Encoder not fitted. Call fit() on the corpus first.")
        return self.vectorizer.transform(texts).toarray()


def _build_default_encoder():
    try:
        return MultilingualSentenceEncoder()
    except Exception:
        return TfidfCharEncoder()


class ReducedEmbedder:
    """Fits PCA once on the corpus (fixed projection matrix, per spec), then projects any new
    message's raw vector down to `target_dim` dims before DenStream."""

    def __init__(self, target_dim: int | None = None, encoder=None):
        self.target_dim = target_dim or settings.embedding_dim_reduced
        self.encoder = encoder or _build_default_encoder()
        self.pca: PCA | None = None

    def fit(self, corpus_texts: list[str]) -> "ReducedEmbedder":
        self.encoder.fit(corpus_texts)
        raw = self.encoder.transform(corpus_texts)
        n_components = min(self.target_dim, raw.shape[0], raw.shape[1])
        self.pca = PCA(n_components=n_components, random_state=42)
        self.pca.fit(raw)
        return self

    def embed(self, texts: list[str]) -> np.ndarray:
        if self.pca is None:
            raise RuntimeError("Call fit() before embed().")
        raw = self.encoder.transform(texts)
        return self.pca.transform(raw)
