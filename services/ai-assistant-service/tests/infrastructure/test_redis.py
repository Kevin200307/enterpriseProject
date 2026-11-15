from app.infrastructure.redis import AllowAllRateLimiter, ToolCache


class FakeRedis:
    def __init__(self) -> None:
        self.values: dict[str, str] = {}

    async def get(self, key: str) -> str | None:
        return self.values.get(key)

    async def set(self, key: str, value: str, **kwargs: object) -> None:
        del kwargs
        self.values[key] = value


async def test_allow_all_is_development_fallback() -> None:
    limiter = AllowAllRateLimiter()
    assert await limiter.allow("user")


async def test_private_cache_entries_are_user_scoped() -> None:
    redis = FakeRedis()
    cache = ToolCache(redis)  # type: ignore[arg-type]
    await cache.set("parcels", "recent", {"count": 1}, ttl_seconds=10, user_id="user-1")

    assert await cache.get("parcels", "recent", user_id="user-1") == {"count": 1}
    assert await cache.get("parcels", "recent", user_id="user-2") is None
