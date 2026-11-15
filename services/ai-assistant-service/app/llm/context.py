from collections.abc import Sequence

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage

from app.models.conversation import ConversationMessage


def build_context(
    system_prompt: str,
    history: Sequence[ConversationMessage],
    current_message: str,
    *,
    summary: str | None = None,
    max_recent_messages: int = 6,
    max_summary_characters: int = 2000,
) -> list[BaseMessage]:
    messages: list[BaseMessage] = [SystemMessage(system_prompt)]
    if summary:
        bounded = summary[:max_summary_characters]
        messages.append(SystemMessage(f"Earlier conversation summary: {bounded}"))
    for item in history[-max_recent_messages:]:
        messages.append(
            HumanMessage(item.content) if item.role == "user" else AIMessage(item.content)
        )
    messages.append(HumanMessage(current_message))
    return messages
