from collections.abc import AsyncIterator
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import StreamingResponse

from app.api.conversations import get_conversations, require_enabled
from app.graph.runner import AssistantRunner
from app.models.chat import ChatRequest
from app.models.conversation import ConversationMessage
from app.persistence.conversation_repository import ConversationRepository
from app.security.dependencies import require_principal
from app.security.principal import AssistantPrincipal
from app.streaming.sse import encode_sse

router = APIRouter(prefix="/api/v1/assistant", tags=["assistant"])


async def _placeholder_stream(
    request: Request,
    body: ChatRequest,
    principal: AssistantPrincipal,
    conversations: ConversationRepository,
    runner: AssistantRunner,
) -> AsyncIterator[bytes]:
    conversation_id: UUID = body.conversationId or await conversations.create(principal.subject)
    history = await conversations.append(
        conversation_id,
        principal.subject,
        ConversationMessage(role="user", content=body.message),
    )
    if history is None:
        yield encode_sse("error", {"message": "Conversation not found."})
        return

    answer: list[str] = []
    async for event in runner.run(body.message, principal):
        if await request.is_disconnected():
            return
        if event.event == "token":
            answer.append(str(event.data.get("text", "")))
        yield encode_sse(event.event, event.data)
    response = "".join(answer)
    await conversations.append(
        conversation_id,
        principal.subject,
        ConversationMessage(role="assistant", content=response),
    )
    yield encode_sse("done", {"conversationId": str(conversation_id)})


@router.post("/chat/stream", response_class=StreamingResponse)
async def stream_chat(
    body: ChatRequest,
    request: Request,
    principal: Annotated[AssistantPrincipal, Depends(require_principal)],
    conversations: Annotated[ConversationRepository, Depends(get_conversations)],
    _: Annotated[None, Depends(require_enabled)],
) -> StreamingResponse:
    if not await request.app.state.rate_limiter.allow(principal.subject):
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Assistant request limit exceeded",
        )
    runner: AssistantRunner = request.app.state.assistant_runner
    return StreamingResponse(
        _placeholder_stream(request, body, principal, conversations, runner),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-store",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
