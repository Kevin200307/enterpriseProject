from fastapi import FastAPI

from app.api.chat import router as chat_router
from app.api.conversations import router as conversations_router
from app.api.health import router as health_router
from app.clients.parcel import ParcelClient
from app.clients.pricing import PricingClient
from app.config import Settings, get_settings
from app.graph.runner import AssistantRunner
from app.infrastructure.redis import AllowAllRateLimiter, RedisRateLimiter, create_redis
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
    parcel_client = ParcelClient(
        resolved.parcel_service_url,
        resolved.downstream_timeout_seconds,
    )
    pricing_client = PricingClient(
        resolved.pricing_service_url,
        resolved.downstream_timeout_seconds,
    )
    application.state.parcel_client = parcel_client
    application.state.pricing_client = pricing_client
    application.state.assistant_runner = AssistantRunner(parcel_client, pricing_client)
    if resolved.redis_url is None:
        application.state.rate_limiter = AllowAllRateLimiter()
    else:
        redis = create_redis(resolved.redis_url.get_secret_value())
        application.state.redis = redis
        application.state.rate_limiter = RedisRateLimiter(
            redis,
            resolved.assistant_requests_per_hour,
        )
    application.include_router(health_router)
    application.include_router(chat_router)
    application.include_router(conversations_router)
    return application


app = create_app()
