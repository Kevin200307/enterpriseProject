import json
import re
from collections.abc import AsyncIterator
from uuid import UUID, uuid4

from fastapi.testclient import TestClient

from app.config import Settings
from app.graph.runner import AssistantEvent
from app.main import create_app
from app.security.dependencies import require_principal
from app.security.principal import AssistantPrincipal


def make_client(*, enabled: bool = True) -> TestClient:
    app = create_app(Settings(assistant_enabled=enabled))
    app.dependency_overrides[require_principal] = lambda: AssistantPrincipal(
        subject="user-1",
        roles=frozenset({"CUSTOMER"}),
        token="fixture",  # noqa: S106
    )
    app.state.assistant_runner = FakeRunner()
    return TestClient(app)


class FakeRunner:
    async def run(
        self, message: str, principal: AssistantPrincipal
    ) -> AsyncIterator[AssistantEvent]:
        del principal
        yield AssistantEvent("token", {"text": f"Reply to {message}"})


def parse_events(body: str) -> list[tuple[str, dict[str, object]]]:
    events: list[tuple[str, dict[str, object]]] = []
    for block in re.split(r"\r?\n\r?\n", body.strip()):
        lines = block.splitlines()
        name = next(line[7:] for line in lines if line.startswith("event: "))
        data = next(line[6:] for line in lines if line.startswith("data: "))
        events.append((name, json.loads(data)))
    return events


def test_stream_emits_compatible_events_and_persists_history() -> None:
    with make_client() as client:
        response = client.post("/api/v1/assistant/chat/stream", json={"message": " Hello "})
        events = parse_events(response.text)
        conversation_id = UUID(str(events[-1][1]["conversationId"]))
        history = client.get(f"/api/v1/assistant/conversations/{conversation_id}")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    assert response.headers["cache-control"] == "no-store"
    assert [name for name, _ in events] == ["token", "done"]
    assert history.json()["messages"][0] == {"role": "user", "content": "Hello"}
    assert history.json()["messages"][1] == {"role": "assistant", "content": "Reply to Hello"}


def test_missing_owned_conversation_returns_sse_error() -> None:
    with make_client() as client:
        response = client.post(
            "/api/v1/assistant/chat/stream",
            json={"message": "hello", "conversationId": str(uuid4())},
        )
    assert parse_events(response.text) == [("error", {"message": "Conversation not found."})]


def test_invalid_messages_are_rejected_before_streaming() -> None:
    with make_client() as client:
        blank = client.post("/api/v1/assistant/chat/stream", json={"message": "   "})
        long = client.post("/api/v1/assistant/chat/stream", json={"message": "x" * 2001})
    assert blank.status_code == 422
    assert long.status_code == 422


def test_disabled_assistant_returns_503() -> None:
    with make_client(enabled=False) as client:
        response = client.post("/api/v1/assistant/chat/stream", json={"message": "hello"})
    assert response.status_code == 503
