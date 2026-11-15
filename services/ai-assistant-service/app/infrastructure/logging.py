import logging
import re

SENSITIVE_PATTERNS = (
    re.compile(r"Bearer\s+[A-Za-z0-9._~+/-]+", re.IGNORECASE),
    re.compile(r"(?i)(api[_-]?key|password|token)=([^\s&,]+)"),
    re.compile(r"\+?[0-9][0-9 -]{8,18}"),
)


def redact(value: object) -> str:
    text = str(value)
    text = SENSITIVE_PATTERNS[0].sub("Bearer [REDACTED]", text)
    text = SENSITIVE_PATTERNS[1].sub(r"\1=[REDACTED]", text)
    return SENSITIVE_PATTERNS[2].sub("[REDACTED_PHONE]", text)


class RedactingFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.msg = redact(record.getMessage())
        record.args = ()
        return True


def configure_logging(level: str) -> None:
    handler = logging.StreamHandler()
    handler.addFilter(RedactingFilter())
    logging.basicConfig(level=level, handlers=[handler], force=True)
