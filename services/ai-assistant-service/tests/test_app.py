from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


def test_application_starts_without_external_connections() -> None:
    application = create_app(Settings())

    with TestClient(application) as client:
        response = client.get("/not-yet-implemented")

    assert response.status_code == 404
    assert application.state.settings.app_name == "SwiftDrop AI Assistant"
