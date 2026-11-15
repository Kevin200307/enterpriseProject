from typing import Protocol
from uuid import UUID

from app.models.conversation import ConversationMessage


class ConversationRepository(Protocol):
    async def create(self, owner_id: str) -> UUID: ...

    async def append(
        self, conversation_id: UUID, owner_id: str, message: ConversationMessage
    ) -> tuple[ConversationMessage, ...] | None: ...

    async def messages(
        self, conversation_id: UUID, owner_id: str
    ) -> tuple[ConversationMessage, ...] | None: ...

    async def delete(self, conversation_id: UUID, owner_id: str) -> bool: ...
