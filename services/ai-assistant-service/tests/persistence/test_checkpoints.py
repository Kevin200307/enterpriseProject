from uuid import uuid4

import pytest
from langgraph.checkpoint.memory import InMemorySaver

from app.graph.builder import build_assistant_graph
from app.persistence.checkpoints import checkpoint_config


@pytest.mark.asyncio
async def test_checkpoint_state_can_resume_with_a_new_graph_instance() -> None:
    saver = InMemorySaver()
    conversation_id = uuid4()
    config = checkpoint_config(conversation_id)

    first_graph = build_assistant_graph(saver)
    first = await first_graph.ainvoke(
        {"message": "Show my deliveries", "roles": ("CUSTOMER",)},
        config,
    )
    assert first["selected_agent"] == "customer"

    restarted_graph = build_assistant_graph(saver)
    state = await restarted_graph.aget_state(config)
    assert state.values["intent"] == "LIST_MY_DELIVERIES"
    assert state.values["workflow_status"] == "READY"


@pytest.mark.asyncio
async def test_checkpoint_threads_are_isolated() -> None:
    saver = InMemorySaver()
    graph = build_assistant_graph(saver)
    first = checkpoint_config(uuid4())
    second = checkpoint_config(uuid4())
    await graph.ainvoke({"message": "Track SD-12AB34CD", "roles": ()}, first)

    assert (await graph.aget_state(first)).values["intent"] == "TRACK_PARCEL"
    assert not (await graph.aget_state(second)).values
