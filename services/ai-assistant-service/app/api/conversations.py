from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status

from app.models.conversation import ConversationView
from app.persistence.conversation_repository import ConversationRepository
from app.security.dependencies import require_principal
from app.security.principal import AssistantPrincipal

router = APIRouter(prefix="/api/v1/assistant", tags=["assistant"])


def get_conversations(request: Request) -> ConversationRepository:
    repository: ConversationRepository = request.app.state.conversations
    return repository


def require_enabled(request: Request) -> None:
    if not request.app.state.settings.assistant_enabled:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Assistant is disabled",
        )


@router.get("/conversations/{conversation_id}", response_model=ConversationView)
async def get_conversation(
    conversation_id: UUID,
    principal: Annotated[AssistantPrincipal, Depends(require_principal)],
    conversations: Annotated[ConversationRepository, Depends(get_conversations)],
    _: Annotated[None, Depends(require_enabled)],
) -> ConversationView:
    messages = await conversations.messages(conversation_id, principal.subject)
    if messages is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Conversation not found")
    return ConversationView(conversationId=conversation_id, messages=messages)


@router.delete("/conversations/{conversation_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_conversation(
    conversation_id: UUID,
    principal: Annotated[AssistantPrincipal, Depends(require_principal)],
    conversations: Annotated[ConversationRepository, Depends(get_conversations)],
    _: Annotated[None, Depends(require_enabled)],
) -> Response:
    if not await conversations.delete(conversation_id, principal.subject):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Conversation not found")
    return Response(status_code=status.HTTP_204_NO_CONTENT)
