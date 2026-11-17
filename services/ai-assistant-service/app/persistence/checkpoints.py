from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any
from uuid import UUID

from langchain_core.runnables import RunnableConfig
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver


def checkpoint_config(conversation_id: UUID) -> RunnableConfig:
    return {"configurable": {"thread_id": str(conversation_id)}}


@asynccontextmanager
async def postgres_checkpointer(
    database_url: str, *, setup: bool = True,
) -> AsyncIterator[BaseCheckpointSaver[Any]]:
    """Open the checkpointer; Kubernetes delegates schema setup to its migration Job."""
    async with AsyncPostgresSaver.from_conn_string(database_url) as saver:
        if setup:
            await saver.setup()
        yield saver
