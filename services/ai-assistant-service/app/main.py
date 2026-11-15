from fastapi import FastAPI

from app.api.chat import router as chat_router
from app.api.conversations import router as conversations_router
from app.api.health import router as health_router
from app.config import Settings, get_settings
from app.llm.gemini import create_model_provider
from app.persistence.memory_conversation_repository import InMemoryConversationRepository
from app.security.jwt import JwtTokenValidator


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved = settings or get_settings()
    application = FastAPI(
        title=resolved.app_name,
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    application.state.settings = resolved
    application.state.token_validator = JwtTokenValidator(
        resolved.identity_jwks_uri,
        resolved.identity_issuer_uri,
        resolved.jwt_audience,
    )
    application.state.conversations = InMemoryConversationRepository()
    application.state.model_provider = create_model_provider(resolved)
    application.include_router(health_router)
    application.include_router(chat_router)
    application.include_router(conversations_router)
    return application


app = create_app()
