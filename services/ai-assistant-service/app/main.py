from uuid import uuid4

from fastapi import FastAPI
from fastapi import Request as FastApiRequest
from starlette.middleware.base import RequestResponseEndpoint
from starlette.responses import Response

from app.api.chat import router as chat_router
from app.api.conversations import router as conversations_router
from app.api.health import router as health_router
from app.clients.parcel import ParcelClient
from app.clients.pricing import PricingClient
from app.config import Settings, get_settings
from app.graph.runner import AssistantRunner
from app.infrastructure.logging import configure_logging
from app.infrastructure.redis import AllowAllRateLimiter, RedisRateLimiter, create_redis
from app.infrastructure.telemetry import AssistantMetrics
from app.llm.gemini import create_model_provider
from app.persistence.database import create_database
from app.persistence.memory_conversation_repository import InMemoryConversationRepository
from app.persistence.sql_conversation_repository import SqlConversationRepository
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
    configure_logging(resolved.log_level)
    application.state.metrics = AssistantMetrics()
    application.state.token_validator = JwtTokenValidator(
        resolved.identity_jwks_uri,
        resolved.identity_issuer_uri,
        resolved.jwt_audience,
    )
    if resolved.assistant_database_url is None:
        application.state.conversations = InMemoryConversationRepository()
    else:
        engine, sessions = create_database(resolved.assistant_database_url.get_secret_value())
        application.state.database_engine = engine
        application.state.conversations = SqlConversationRepository(
            sessions, resolved.conversation_retention_days
        )
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

    @application.middleware("http")
    async def request_context(
        request: FastApiRequest, call_next: RequestResponseEndpoint
    ) -> Response:
        request_id = request.headers.get("X-Request-Id") or str(uuid4())
        response = await call_next(request)
        response.headers["X-Request-Id"] = request_id
        await application.state.metrics.increment(
            f"http_{request.method.lower()}_{response.status_code}"
        )
        return response

    return application


app = create_app()
