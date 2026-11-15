import asyncio
from collections import Counter


class AssistantMetrics:
    def __init__(self) -> None:
        self._counters: Counter[str] = Counter()
        self._lock = asyncio.Lock()

    async def increment(self, name: str) -> None:
        async with self._lock:
            self._counters[name] += 1

    async def snapshot(self) -> dict[str, int]:
        async with self._lock:
            return dict(self._counters)
