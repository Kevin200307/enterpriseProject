import json
from typing import Any, Protocol, cast

from redis.asyncio import Redis


class RateLimiter(Protocol):
    async def allow(self, user_id: str) -> bool: ...


class AllowAllRateLimiter:
    async def allow(self, user_id: str) -> bool:
        del user_id
        return True


class RedisRateLimiter:
    def __init__(self, redis: Redis, limit: int, window_seconds: int = 3600) -> None:
        self._redis = redis
        self._limit = limit
        self._window = window_seconds

    async def allow(self, user_id: str) -> bool:
        key = f"assistant:rate:{user_id}"
        async with self._redis.pipeline(transaction=True) as pipeline:
            pipeline.incr(key)
            pipeline.expire(key, self._window, nx=True)
            count, _ = await pipeline.execute()
        return int(count) <= self._limit


class ToolCache:
    def __init__(self, redis: Redis) -> None:
        self._redis = redis

    async def get(self, namespace: str, key: str, *, user_id: str | None = None) -> Any | None:
        value = await self._redis.get(self._key(namespace, key, user_id))
        if value is None:
            return None
        return json.loads(value)

    async def set(
        self,
        namespace: str,
        key: str,
        value: object,
        *,
        ttl_seconds: int,
        user_id: str | None = None,
    ) -> None:
        await self._redis.set(
            self._key(namespace, key, user_id),
            json.dumps(value, separators=(",", ":")),
            ex=ttl_seconds,
        )

    @staticmethod
    def _key(namespace: str, key: str, user_id: str | None) -> str:
        scope = user_id or "public"
        return f"assistant:cache:{namespace}:{scope}:{key}"


def create_redis(url: str) -> Redis:
    return cast(Redis, Redis.from_url(url, decode_responses=True))
