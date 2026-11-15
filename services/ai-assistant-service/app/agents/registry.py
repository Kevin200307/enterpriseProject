from app.agents.base import AgentSpec
from app.security.principal import AssistantPrincipal
from app.tools.registry import ToolSpec, allowed_tools

AGENTS = {
    "support": AgentSpec(
        "support",
        "Answer only questions about SwiftDrop delivery policies and navigation. "
        "Never invent live data.",
        frozenset(),
    ),
    "tracking": AgentSpec(
        "tracking",
        "Help users track SwiftDrop parcels. Use the tracking tool for every live status claim.",
        frozenset({"trackParcel"}),
    ),
    "customer": AgentSpec(
        "customer",
        "Help the authenticated customer with their own deliveries and live price estimates.",
        frozenset({"trackParcel", "estimateFee", "listMyParcels"}),
        "CUSTOMER",
    ),
    "merchant": AgentSpec(
        "merchant",
        "Help the authenticated merchant with merchant-owned SwiftDrop resources.",
        frozenset(),
        "MERCHANT",
    ),
    "courier": AgentSpec(
        "courier",
        "Help the authenticated courier with parcels assigned to that courier.",
        frozenset(),
        "COURIER",
    ),
    "dispatcher": AgentSpec(
        "dispatcher",
        "Help dispatchers assign work without bypassing dispatch-service rules.",
        frozenset(),
        "DISPATCHER",
    ),
    "admin": AgentSpec(
        "admin",
        "Perform only explicitly requested, authorized, confirmed, and audited admin operations.",
        frozenset(),
        "ADMIN",
    ),
}


def agent_for(name: str, principal: AssistantPrincipal) -> AgentSpec | None:
    spec = AGENTS.get(name)
    if spec is None or (spec.required_role and not principal.has_role(spec.required_role)):
        return None
    return spec


def tools_for_agent(spec: AgentSpec, principal: AssistantPrincipal) -> tuple[ToolSpec, ...]:
    return tuple(
        tool for tool in allowed_tools(principal) if tool.name in spec.allowed_tool_names
    )
