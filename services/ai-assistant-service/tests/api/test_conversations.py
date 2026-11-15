from uuid import uuid4

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.models.conversation import ConversationMessage
from app.security.dependencies import require_principal
from app.security.principal import AssistantPrincipal


def client_for(subject: str = "user-1", *, enabled: bool = True) -> TestClient:
    app = create_app(Settings(assistant_enabled=enabled))
    app.dependency_overrides[require_principal] = lambda: AssistantPrincipal(
        subject=subject,
        roles=frozenset({"CUSTOMER"}),
        token="test-token",  # noqa: S106 -- non-secret test fixture
    )
    return TestClient(app)


def test_owner_can_read_and_delete_conversation() -> None:
    with client_for() as client:
        repository = client.app.state.conversations
        conversation_id = client.portal.call(repository.create, "user-1")
        client.portal.call(
            repository.append,
            conversation_id,
            "user-1",
            ConversationMessage(role="user", content="hello"),
        )

        response = client.get(f"/api/v1/assistant/conversations/{conversation_id}")
        deleted = client.delete(f"/api/v1/assistant/conversations/{conversation_id}")
        missing = client.get(f"/api/v1/assistant/conversations/{conversation_id}")

    assert response.status_code == 200
    assert response.json() == {
        "conversationId": str(conversation_id),
        "messages": [{"role": "user", "content": "hello"}],
    }
    assert deleted.status_code == 204
    assert missing.status_code == 404


def test_other_user_cannot_discover_conversation() -> None:
    with client_for(subject="attacker") as client:
        repository = client.app.state.conversations
        conversation_id = client.portal.call(repository.create, "owner")
        response = client.get(f"/api/v1/assistant/conversations/{conversation_id}")
        deleted = client.delete(f"/api/v1/assistant/conversations/{conversation_id}")

    assert response.status_code == 404
    assert deleted.status_code == 404


def test_unknown_conversation_is_not_found() -> None:
    with client_for() as client:
        response = client.get(f"/api/v1/assistant/conversations/{uuid4()}")
    assert response.status_code == 404


def test_disabled_assistant_returns_service_unavailable() -> None:
    with client_for(enabled=False) as client:
        response = client.get(f"/api/v1/assistant/conversations/{uuid4()}")
    assert response.status_code == 503
