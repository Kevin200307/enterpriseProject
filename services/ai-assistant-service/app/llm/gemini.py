from collections.abc import AsyncIterator, Sequence

from langchain_core.messages import BaseMessage
from langchain_google_genai import ChatGoogleGenerativeAI

from app.config import Settings
from app.llm.provider import ModelProvider, ModelUnavailable


class DisabledModelProvider:
    async def stream_text(
        self,
        messages: Sequence[BaseMessage],
        tools: Sequence[dict[str, object]] = (),
    ) -> AsyncIterator[str]:
        del messages, tools
        raise ModelUnavailable("The assistant model is not configured")
        yield  # pragma: no cover -- keeps this an async generator


class GeminiModelProvider:
    def __init__(self, settings: Settings) -> None:
        if settings.google_api_key is None:
            raise ValueError("GOOGLE_API_KEY is required when the assistant is enabled")
        self._model = ChatGoogleGenerativeAI(
            model=settings.gemini_model,
            google_api_key=settings.google_api_key,
            temperature=settings.gemini_temperature,
            max_output_tokens=settings.gemini_max_output_tokens,
            timeout=settings.gemini_timeout_seconds,
            max_retries=settings.gemini_max_retries,
        )

    async def stream_text(
        self,
        messages: Sequence[BaseMessage],
        tools: Sequence[dict[str, object]] = (),
    ) -> AsyncIterator[str]:
        model = self._model.bind_tools(list(tools)) if tools else self._model
        try:
            async for chunk in model.astream(list(messages)):
                content = chunk.content
                if isinstance(content, str) and content:
                    yield content
        except Exception as error:
            raise ModelUnavailable("The assistant model is temporarily unavailable") from error


def create_model_provider(settings: Settings) -> ModelProvider:
    if not settings.assistant_enabled or settings.google_api_key is None:
        return DisabledModelProvider()
    return GeminiModelProvider(settings)
