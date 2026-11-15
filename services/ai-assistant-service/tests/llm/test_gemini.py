from collections.abc import AsyncIterator, Sequence

import pytest
from langchain_core.messages import BaseMessage, HumanMessage

from app.config import Settings
from app.llm.gemini import DisabledModelProvider, create_model_provider
from app.llm.provider import ModelUnavailable


class FakeModelProvider:
    async def stream_text(
        self,
        messages: Sequence[BaseMessage],
        tools: Sequence[dict[str, object]] = (),
    ) -> AsyncIterator[str]:
        assert messages[-1].content == "hello"
        assert not tools
        yield "safe"
        yield " response"


@pytest.mark.asyncio
async def test_fake_model_stream_is_deterministic() -> None:
    chunks = [chunk async for chunk in FakeModelProvider().stream_text([HumanMessage("hello")])]
    assert chunks == ["safe", " response"]


@pytest.mark.asyncio
async def test_missing_key_produces_disabled_provider() -> None:
    provider = create_model_provider(Settings(google_api_key=None))
    assert isinstance(provider, DisabledModelProvider)
    with pytest.raises(ModelUnavailable):
        _ = [chunk async for chunk in provider.stream_text([HumanMessage("hello")])]


def test_settings_do_not_reveal_api_key() -> None:
    settings = Settings(google_api_key="not-a-real-secret")
    assert "not-a-real-secret" not in repr(settings)
