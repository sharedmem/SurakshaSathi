"""Deliberately deferred, per your call: Truecaller's dataset is crowdsourced (not guaranteed
correct) and its unofficial Python library adds 1-2s latency, which blows the per-message budget.
Interface kept so it can be dropped in later without touching call sites in callback_pipeline.py.
"""

from config.settings import settings


class CallbackReputationProvider:
    def lookup(self, phone_number: str) -> dict | None:
        raise NotImplementedError


class TruecallerProvider(CallbackReputationProvider):
    def lookup(self, phone_number: str) -> dict | None:
        if not settings.enable_truecaller:
            return None
        raise NotImplementedError("Truecaller integration intentionally not wired -- see module docstring")
