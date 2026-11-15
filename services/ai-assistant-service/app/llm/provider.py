from collections.abc import AsyncIterator, Sequence
from typing import Protocol

from langchain_core.messages import BaseMessage


class ModelUnavailable(RuntimeError):
    pass


class ModelProvider(Protocol):
    def stream_text(
        self,
        messages: Sequence[BaseMessage],
        tools: Sequence[dict[str, object]] = (),
    ) -> AsyncIterator[str]: ...
