"""Retry + circuit-breaker wrapper for every outbound call (Groq, Pinecone, VirusTotal, Twilio,
Translate, WHOIS, Tavily, ...). Wrap external calls with @with_retry; a source that fails
`failure_threshold` times in a row trips its breaker and short-circuits for `reset_after_s`
instead of continuing to hammer a dead dependency."""

import time
from functools import wraps

from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type


class CircuitOpenError(RuntimeError):
    pass


class CircuitBreaker:
    def __init__(self, failure_threshold: int = 5, reset_after_s: float = 60.0):
        self.failure_threshold = failure_threshold
        self.reset_after_s = reset_after_s
        self._failures = 0
        self._opened_at: float | None = None

    def _is_open(self) -> bool:
        if self._opened_at is None:
            return False
        if time.monotonic() - self._opened_at > self.reset_after_s:
            self._opened_at = None
            self._failures = 0
            return False
        return True

    def before_call(self, source_name: str):
        if self._is_open():
            raise CircuitOpenError(f"{source_name}: circuit open, skipping call")

    def record_success(self):
        self._failures = 0
        self._opened_at = None

    def record_failure(self):
        self._failures += 1
        if self._failures >= self.failure_threshold:
            self._opened_at = time.monotonic()


_BREAKERS: dict[str, CircuitBreaker] = {}


def get_breaker(source_name: str) -> CircuitBreaker:
    if source_name not in _BREAKERS:
        _BREAKERS[source_name] = CircuitBreaker()
    return _BREAKERS[source_name]


def with_retry(source_name: str, exceptions: tuple[type[Exception], ...] = (Exception,), attempts: int = 3):
    """Decorator: retries with exponential backoff, plus a per-source circuit breaker so a
    persistently-dead dependency fails fast instead of blocking the pipeline."""

    def decorator(func):
        breaker = get_breaker(source_name)

        @retry(
            stop=stop_after_attempt(attempts),
            wait=wait_exponential(multiplier=0.2, min=0.2, max=2),
            retry=retry_if_exception_type(exceptions),
            reraise=True,
        )
        def _call_with_retry(*args, **kwargs):
            return func(*args, **kwargs)

        @wraps(func)
        def wrapper(*args, **kwargs):
            breaker.before_call(source_name)
            try:
                result = _call_with_retry(*args, **kwargs)
            except exceptions:
                breaker.record_failure()
                raise
            breaker.record_success()
            return result

        return wrapper

    return decorator
