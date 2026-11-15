from typing import Literal

from fastapi import APIRouter, Request
from pydantic import BaseModel

router = APIRouter(tags=["health"])


class HealthResponse(BaseModel):
    status: Literal["UP"] = "UP"


class ReadinessResponse(BaseModel):
    status: Literal["UP", "OUT_OF_SERVICE"]
    components: dict[str, str]


@router.get("/actuator/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse()


@router.get("/actuator/health/liveness", response_model=HealthResponse)
async def liveness() -> HealthResponse:
    return HealthResponse()


@router.get("/actuator/health/readiness", response_model=ReadinessResponse)
async def readiness(request: Request) -> ReadinessResponse:
    settings = request.app.state.settings
    components = {
        "assistant": "enabled" if settings.assistant_enabled else "disabled",
        "runtime": "ready",
    }
    return ReadinessResponse(status="UP", components=components)


@router.get("/actuator/metrics")
async def metrics(request: Request) -> dict[str, int]:
    result: dict[str, int] = await request.app.state.metrics.snapshot()
    return result
