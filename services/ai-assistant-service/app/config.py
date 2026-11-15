from functools import lru_cache

from pydantic import Field
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


@lru_cache
def get_settings() -> Settings:
    return Settings()
