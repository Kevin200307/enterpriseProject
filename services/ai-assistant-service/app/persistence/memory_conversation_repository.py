import asyncio
from dataclasses import dataclass, field
from uuid import UUID, uuid4

from app.models.conversation import ConversationMessage


@dataclass(slots=True)
class _Conversation:
    owner_id: str
    messages: list[ConversationMessage] = field(default_factory=list)


class InMemoryConversationRepository:
    """Development adapter. Production switches to PostgreSQL before Java removal."""

    def __init__(self) -> None:
        self._conversations: dict[UUID, _Conversation] = {}
        self._lock = asyncio.Lock()

    async def create(self, owner_id: str) -> UUID:
        async with self._lock:
            conversation_id = uuid4()
            self._conversations[conversation_id] = _Conversation(owner_id=owner_id)
            return conversation_id

    async def append(
        self, conversation_id: UUID, owner_id: str, message: ConversationMessage
    ) -> tuple[ConversationMessage, ...] | None:
        async with self._lock:
            conversation = self._owned(conversation_id, owner_id)
            if conversation is None:
                return None
            conversation.messages.append(message)
            return tuple(conversation.messages)

    async def messages(
        self, conversation_id: UUID, owner_id: str
    ) -> tuple[ConversationMessage, ...] | None:
        async with self._lock:
            conversation = self._owned(conversation_id, owner_id)
            return None if conversation is None else tuple(conversation.messages)

    async def delete(self, conversation_id: UUID, owner_id: str) -> bool:
        async with self._lock:
            if self._owned(conversation_id, owner_id) is None:
                return False
            del self._conversations[conversation_id]
            return True

    def _owned(self, conversation_id: UUID, owner_id: str) -> _Conversation | None:
        conversation = self._conversations.get(conversation_id)
        if conversation is None or conversation.owner_id != owner_id:
            return None
        return conversation
