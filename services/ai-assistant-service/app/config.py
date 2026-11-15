from functools import lru_cache

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration loaded exclusively from environment variables."""

    model_config = SettingsConfigDict(
        env_file=None,
        extra="ignore",
        case_sensitive=False,
    )

    app_name: str = "SwiftDrop AI Assistant"
    assistant_enabled: bool = True
    environment: str = "development"
    log_level: str = "INFO"
    port: int = Field(default=8080, ge=1, le=65535)
    identity_jwks_uri: str = "http://identity-service:8080/.well-known/jwks.json"
    identity_issuer_uri: str = "http://identity-service:8080"
    jwt_audience: str = "swiftdrop"
    parcel_service_url: str = "http://parcel-service:8080"
    pricing_service_url: str = "http://pricing-service:8080"
    merchant_service_url: str = "http://merchant-service:8080"
    dispatch_service_url: str = "http://dispatch-service:8080"
    identity_service_url: str = "http://identity-service:8080"
    notification_service_url: str = "http://notification-service:8086"
    downstream_timeout_seconds: float = Field(default=5.0, gt=0, le=30)
    google_api_key: SecretStr | None = None
    gemini_model: str = "gemini-2.5-flash-lite"
    gemini_temperature: float = Field(default=0.1, ge=0, le=1)
    gemini_max_output_tokens: int = Field(default=500, ge=64, le=2048)
    gemini_timeout_seconds: float = Field(default=30, gt=0, le=120)
    gemini_max_retries: int = Field(default=1, ge=0, le=3)
    assistant_database_url: SecretStr | None = None
    conversation_retention_days: int = Field(default=30, ge=1, le=365)
    checkpoint_database_url: SecretStr | None = None
    redis_url: SecretStr | None = None
    assistant_requests_per_hour: int = Field(default=20, ge=1, le=1000)
    max_model_calls_per_turn: int = Field(default=2, ge=1, le=5)
    max_tool_calls_per_turn: int = Field(default=3, ge=1, le=10)
    max_recent_messages: int = Field(default=6, ge=2, le=20)
    max_summary_characters: int = Field(default=2000, ge=200, le=10000)

    @field_validator(
        "google_api_key",
        "assistant_database_url",
        "checkpoint_database_url",
        "redis_url",
        mode="before",
    )
    @classmethod
    def blank_secrets_are_unset(cls, value: object) -> object:
        return None if value == "" else value


@lru_cache
def get_settings() -> Settings:
    return Settings()
