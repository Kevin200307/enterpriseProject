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


@lru_cache
def get_settings() -> Settings:
    return Settings()
