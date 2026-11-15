from typing import Any
from urllib.parse import urlparse

import httpx


class DownstreamError(RuntimeError):
    def __init__(self, service: str, status_code: int | None = None) -> None:
        super().__init__(f"{service} is temporarily unavailable")
        self.service = service
        self.status_code = status_code


class DownstreamNotFound(DownstreamError):
    pass


class DownstreamClient:
    def __init__(
        self,
        service: str,
        base_url: str,
        timeout_seconds: float,
        *,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        parsed = urlparse(base_url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError(f"Invalid configured URL for {service}")
        self._service = service
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=httpx.Timeout(timeout_seconds),
            transport=transport,
        )

    async def close(self) -> None:
        await self._client.aclose()

    async def request_json(
        self,
        method: str,
        path: str,
        bearer_token: str,
        *,
        request_id: str | None = None,
        extra_headers: dict[str, str] | None = None,
        json_body: object | None = None,
        params: dict[str, str | int | float | bool | None] | None = None,
    ) -> Any:
        if not path.startswith("/") or path.startswith("//"):
            raise ValueError("Downstream path must be an absolute service path")
        headers = {"Authorization": f"Bearer {bearer_token}"}
        if extra_headers:
            headers.update(extra_headers)
        if request_id:
            headers["X-Request-Id"] = request_id
        try:
            response = await self._client.request(
                method,
                path,
                headers=headers,
                json=json_body,
                params=params,
            )
            if response.status_code == 404:
                raise DownstreamNotFound(self._service, 404)
            response.raise_for_status()
            return response.json()
        except DownstreamError:
            raise
        except (httpx.HTTPError, ValueError) as error:
            status_code = (
                error.response.status_code if isinstance(error, httpx.HTTPStatusError) else None
            )
            raise DownstreamError(self._service, status_code) from error
