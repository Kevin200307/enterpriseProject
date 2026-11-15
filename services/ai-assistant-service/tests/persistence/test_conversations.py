from app.models.conversation import ConversationMessage
from app.persistence.database import create_database
from app.persistence.models import Base
from app.persistence.sql_conversation_repository import SqlConversationRepository


async def test_sql_repository_persists_and_enforces_ownership(tmp_path: object) -> None:
    engine, sessions = create_database("sqlite+aiosqlite:///:memory:")
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    repository = SqlConversationRepository(sessions)
    conversation_id = await repository.create("owner")
    await repository.append(
        conversation_id,
        "owner",
        ConversationMessage(role="user", content="hello"),
    )

    assert await repository.messages(conversation_id, "other") is None
    messages = await repository.messages(conversation_id, "owner")
    assert messages is not None and messages[0].content == "hello"
    assert not await repository.delete(conversation_id, "other")
    assert await repository.delete(conversation_id, "owner")
    await engine.dispose()
