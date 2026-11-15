import pytest

from app.llm.budget import BudgetExceeded, TurnBudget
from app.llm.context import build_context
from app.models.conversation import ConversationMessage


def test_turn_budget_stops_model_and_tool_loops() -> None:
    budget = TurnBudget(max_model_calls=1, max_tool_calls=2)
    budget.use_model()
    budget.use_tool()
    budget.use_tool()
    with pytest.raises(BudgetExceeded):
        budget.use_model()
    with pytest.raises(BudgetExceeded):
        budget.use_tool()


def test_context_keeps_only_bounded_recent_history_and_summary() -> None:
    history = [
        ConversationMessage(role="user" if index % 2 == 0 else "assistant", content=str(index))
        for index in range(10)
    ]
    messages = build_context(
        "system",
        history,
        "current",
        summary="x" * 100,
        max_recent_messages=4,
        max_summary_characters=20,
    )
    assert len(messages) == 7
    assert messages[1].content == "Earlier conversation summary: " + "x" * 20
    assert [message.content for message in messages[-5:]] == ["6", "7", "8", "9", "current"]
