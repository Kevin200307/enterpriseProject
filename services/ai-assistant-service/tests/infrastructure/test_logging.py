import logging

from app.infrastructure.logging import RedactingFilter, redact


def test_redacts_tokens_keys_passwords_and_phone_numbers() -> None:
    value = "Bearer abc.def token=secret password=hunter2 api_key=key +94771234567"
    result = redact(value)
    assert "abc.def" not in result
    assert "secret" not in result
    assert "hunter2" not in result
    assert "+94771234567" not in result


def test_logging_filter_redacts_arguments() -> None:
    record = logging.LogRecord("test", logging.INFO, __file__, 1, "value %s", ("token=x",), None)
    assert RedactingFilter().filter(record)
    assert record.getMessage() == "value token=[REDACTED]"
