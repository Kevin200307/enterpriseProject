from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict


class ConversationMessage(BaseModel):
    model_config = ConfigDict(frozen=True)

    role: Literal["user", "assistant"]
    content: str


class ConversationView(BaseModel):
    model_config = ConfigDict(frozen=True)

    conversationId: UUID
    messages: tuple[ConversationMessage, ...]
