from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


def test_spring_compatible_health_response() -> None:
    with TestClient(create_app(Settings())) as client:
        response = client.get("/actuator/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_readiness_does_not_leak_configuration() -> None:
    with TestClient(create_app(Settings(assistant_enabled=False))) as client:
        response = client.get("/actuator/health/readiness")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "components": {"assistant": "disabled", "runtime": "ready"},
    }
    assert "key" not in response.text.lower()


def test_metrics_are_aggregate_and_request_ids_are_returned() -> None:
    with TestClient(create_app(Settings())) as client:
        response = client.get("/actuator/health", headers={"X-Request-Id": "request-123"})
        metrics = client.get("/actuator/metrics")
    assert response.headers["X-Request-Id"] == "request-123"
    assert metrics.json()["http_get_200"] >= 1
    assert "content" not in metrics.text.lower()
