from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph

from app.graph.nodes import (
    authorization_guard,
    input_guard,
    intent_router,
    selected_agent,
    specialist_placeholder,
)
from app.graph.state import AssistantState

AGENTS = ("support", "tracking", "customer", "merchant", "courier", "dispatcher", "admin", "denied")


def build_assistant_graph(
    checkpointer: BaseCheckpointSaver[Any] | None = None,
) -> CompiledStateGraph[
    AssistantState, None, AssistantState, AssistantState
]:
    graph = StateGraph(
        AssistantState,
        input_schema=AssistantState,
        output_schema=AssistantState,
    )
    graph.add_node("input_guard", input_guard)
    graph.add_node("intent_router", intent_router)
    graph.add_node("authorization", authorization_guard)
    for agent in AGENTS:
        graph.add_node(agent, specialist_placeholder)

    graph.add_edge(START, "input_guard")
    graph.add_edge("input_guard", "intent_router")
    graph.add_edge("intent_router", "authorization")
    graph.add_conditional_edges(
        "authorization",
        selected_agent,
        {agent: agent for agent in AGENTS},
    )
    for agent in AGENTS:
        graph.add_edge(agent, END)
    return graph.compile(checkpointer=checkpointer)


def graph_mermaid() -> str:
    return build_assistant_graph().get_graph().draw_mermaid()
