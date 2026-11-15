from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any
from uuid import UUID

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver


def checkpoint_config(conversation_id: UUID) -> dict[str, dict[str, str]]:
    return {"configurable": {"thread_id": str(conversation_id)}}


@asynccontextmanager
async def postgres_checkpointer(
    database_url: str,
) -> AsyncIterator[BaseCheckpointSaver[Any]]:
    """Open and initialize the production LangGraph checkpointer."""
    async with AsyncPostgresSaver.from_conn_string(database_url) as saver:
        await saver.setup()
        yield saver
