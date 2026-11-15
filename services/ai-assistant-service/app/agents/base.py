from dataclasses import dataclass

from app.graph.state import AgentName


@dataclass(frozen=True, slots=True)
class AgentSpec:
    name: AgentName
    system_prompt: str
    allowed_tool_names: frozenset[str]
    required_role: str | None = None
