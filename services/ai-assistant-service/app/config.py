from functools import lru_cache

from pydantic import Field, SecretStr
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
    downstream_timeout_seconds: float = Field(default=5.0, gt=0, le=30)
    google_api_key: SecretStr | None = None
    gemini_model: str = "gemini-2.5-flash-lite"
    gemini_temperature: float = Field(default=0.1, ge=0, le=1)
    gemini_max_output_tokens: int = Field(default=500, ge=64, le=2048)
    gemini_timeout_seconds: float = Field(default=30, gt=0, le=120)
    gemini_max_retries: int = Field(default=1, ge=0, le=3)
    assistant_database_url: SecretStr | None = None
    conversation_retention_days: int = Field(default=30, ge=1, le=365)


@lru_cache
def get_settings() -> Settings:
    return Settings()
