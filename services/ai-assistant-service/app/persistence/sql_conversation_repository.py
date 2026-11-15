from datetime import UTC, datetime, timedelta
from uuid import UUID

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker
from sqlalchemy.orm import selectinload

from app.models.conversation import ConversationMessage
from app.persistence.models import ConversationRecord, MessageRecord


class SqlConversationRepository:
    def __init__(
        self, sessions: async_sessionmaker[AsyncSession], retention_days: int = 30
    ) -> None:
        self._sessions = sessions
        self._retention = timedelta(days=retention_days)

    async def create(self, owner_id: str) -> UUID:
        now = datetime.now(UTC)
        record = ConversationRecord(
            owner_id=owner_id,
            created_at=now,
            updated_at=now,
            expires_at=now + self._retention,
        )
        async with self._sessions() as session:
            session.add(record)
            await session.commit()
            return record.id

    async def append(
        self, conversation_id: UUID, owner_id: str, message: ConversationMessage
    ) -> tuple[ConversationMessage, ...] | None:
        async with self._sessions() as session:
            record = await self._owned(session, conversation_id, owner_id)
            if record is None:
                return None
            now = datetime.now(UTC)
            session.add(
                MessageRecord(
                    conversation_id=conversation_id,
                    role=message.role,
                    content=message.content,
                    created_at=now,
                )
            )
            record.updated_at = now
            await session.commit()
        return await self.messages(conversation_id, owner_id)

    async def messages(
        self, conversation_id: UUID, owner_id: str
    ) -> tuple[ConversationMessage, ...] | None:
        async with self._sessions() as session:
            query = (
                select(ConversationRecord)
                .where(
                    ConversationRecord.id == conversation_id,
                    ConversationRecord.owner_id == owner_id,
                )
                .options(selectinload(ConversationRecord.messages))
            )
            record = (await session.execute(query)).scalar_one_or_none()
            if record is None:
                return None
            return tuple(
                ConversationMessage(role=message.role, content=message.content)
                for message in record.messages
                if message.role in {"user", "assistant"}
            )

    async def delete(self, conversation_id: UUID, owner_id: str) -> bool:
        async with self._sessions() as session:
            result = await session.execute(
                delete(ConversationRecord).where(
                    ConversationRecord.id == conversation_id,
                    ConversationRecord.owner_id == owner_id,
                )
            )
            await session.commit()
            return bool(result.rowcount)

    @staticmethod
    async def _owned(
        session: AsyncSession, conversation_id: UUID, owner_id: str
    ) -> ConversationRecord | None:
        result = await session.execute(
            select(ConversationRecord).where(
                ConversationRecord.id == conversation_id,
                ConversationRecord.owner_id == owner_id,
            )
        )
        return result.scalar_one_or_none()
