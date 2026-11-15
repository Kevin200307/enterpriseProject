from typing import Any, Literal, TypedDict

AgentName = Literal[
    "support",
    "tracking",
    "customer",
    "merchant",
    "courier",
    "dispatcher",
    "admin",
    "denied",
]


class AssistantState(TypedDict, total=False):
    conversation_id: str
    user_id: str
    roles: tuple[str, ...]
    message: str
    intent: str
    selected_agent: AgentName
    workflow_status: str
    workflow_slots: dict[str, Any]
    missing_fields: tuple[str, ...]
    pending_operation_id: str | None
    response: str
    error_code: str | None
