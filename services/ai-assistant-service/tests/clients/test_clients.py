import httpx
import pytest

from app.clients.base import DownstreamClient, DownstreamError, DownstreamNotFound
from app.clients.parcel import ParcelClient


@pytest.mark.asyncio
async def test_forwards_authorization_and_request_id() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.headers["Authorization"] == "Bearer caller-token"
        assert request.headers["X-Request-Id"] == "request-1"
        return httpx.Response(200, json={"status": "CREATED"})

    client = DownstreamClient(
        "test-service",
        "https://service.test",
        1,
        transport=httpx.MockTransport(handler),
    )
    try:
        result = await client.request_json(
            "GET", "/resource", "caller-token", request_id="request-1"
        )
    finally:
        await client.close()

    assert result == {"status": "CREATED"}


@pytest.mark.asyncio
async def test_parcel_client_uses_fixed_service_path() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/v1/parcels/by-tracking/SD-12AB34CD"
        return httpx.Response(200, json={"trackingNumber": "SD-12AB34CD"})

    client = ParcelClient("https://parcel.test", 1, transport=httpx.MockTransport(handler))
    try:
        result = await client.track("SD-12AB34CD", "token")
    finally:
        await client.close()
    assert result["trackingNumber"] == "SD-12AB34CD"


@pytest.mark.asyncio
async def test_maps_not_found_and_transport_errors() -> None:
    not_found = DownstreamClient(
        "test-service",
        "https://service.test",
        1,
        transport=httpx.MockTransport(lambda _request: httpx.Response(404)),
    )

    async def fail(_request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectTimeout("timeout")

    unavailable = DownstreamClient(
        "test-service",
        "https://service.test",
        1,
        transport=httpx.MockTransport(fail),
    )
    try:
        with pytest.raises(DownstreamNotFound):
            await not_found.request_json("GET", "/missing", "token")
        with pytest.raises(DownstreamError) as captured:
            await unavailable.request_json("GET", "/resource", "secret-token")
    finally:
        await not_found.close()
        await unavailable.close()
    assert "secret-token" not in str(captured.value)


def test_rejects_untrusted_base_urls_and_paths() -> None:
    with pytest.raises(ValueError):
        DownstreamClient("bad", "file:///etc/passwd", 1)
