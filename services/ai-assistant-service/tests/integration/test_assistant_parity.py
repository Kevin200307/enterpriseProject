import os
from collections.abc import AsyncIterator, Sequence

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import BaseMessage, HumanMessage

from app.config import Settings
from app.llm.gemini import GeminiModelProvider
from app.main import create_app


def test_public_health_and_protected_assistant_contract() -> None:
    with TestClient(create_app(Settings())) as client:
        health = client.get("/actuator/health")
        stream = client.post("/api/v1/assistant/chat/stream", json={"message": "hello"})
        conversation = client.get(
            "/api/v1/assistant/conversations/00000000-0000-0000-0000-000000000001"
        )

    assert health.status_code == 200
    assert health.json() == {"status": "UP"}
    assert stream.status_code == 401
    assert conversation.status_code == 401
    assert stream.headers["www-authenticate"] == "Bearer"


class FakeGemini:
    def stream_text(
        self,
        messages: Sequence[BaseMessage],
        tools: Sequence[dict[str, object]] = (),
    ) -> AsyncIterator[str]:
        del messages, tools

        async def generate() -> AsyncIterator[str]:
            yield "deterministic"

        return generate()


async def test_fake_gemini_requires_no_external_api() -> None:
    provider = FakeGemini()
    output = [item async for item in provider.stream_text([HumanMessage("hello")])]
    assert output == ["deterministic"]


@pytest.mark.live_gemini
@pytest.mark.skipif(not os.getenv("GOOGLE_API_KEY"), reason="GOOGLE_API_KEY is not configured")
async def test_optional_live_gemini_smoke() -> None:
    provider = GeminiModelProvider(Settings(google_api_key=os.environ["GOOGLE_API_KEY"]))
    chunks = [chunk async for chunk in provider.stream_text([HumanMessage("Reply only: OK")])]
    assert "OK" in "".join(chunks).upper()
