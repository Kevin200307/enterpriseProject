import re

from app.graph.routes import ROLE_BY_AGENT, Intent
from app.graph.state import AgentName, AssistantState

TRACKING_PATTERN = re.compile(r"\bSD-[0-9A-Z]{8}\b", re.IGNORECASE)


def input_guard(state: AssistantState) -> dict[str, object]:
    message = state.get("message", "").strip()
    if not message or len(message) > 2000:
        return {
            "selected_agent": "denied",
            "error_code": "INVALID_MESSAGE",
            "response": "Please enter a message between 1 and 2000 characters.",
        }
    return {"message": message, "workflow_status": "ROUTING"}


def intent_router(state: AssistantState) -> dict[str, object]:
    if state.get("selected_agent") == "denied":
        return {}
    message = state.get("message", "").lower()
    if TRACKING_PATTERN.search(state.get("message", "")) or "track" in message:
        return {"intent": Intent.TRACK_PARCEL, "selected_agent": "tracking"}
    if any(term in message for term in ("price", "fee", "estimate", "cost")):
        return {"intent": Intent.ESTIMATE_FEE, "selected_agent": "customer"}
    if "my deliveries" in message or "my parcels" in message:
        return {"intent": Intent.LIST_MY_DELIVERIES, "selected_agent": "customer"}
    if any(term in message for term in ("approve merchant", "change role", "pricing rule")):
        return {"intent": Intent.ADMIN_REQUEST, "selected_agent": "admin"}
    if any(term in message for term in ("assign courier", "unassigned", "workload")):
        return {"intent": Intent.DISPATCH_REQUEST, "selected_agent": "dispatcher"}
    if any(term in message for term in ("today's stops", "my assignments", "mark delivered")):
        return {"intent": Intent.COURIER_REQUEST, "selected_agent": "courier"}
    if any(term in message for term in ("api key", "webhook", "merchant order")):
        return {"intent": Intent.MERCHANT_REQUEST, "selected_agent": "merchant"}
    return {"intent": Intent.SUPPORT_REQUEST, "selected_agent": "support"}


def authorization_guard(state: AssistantState) -> dict[str, object]:
    selected = state.get("selected_agent", "denied")
    required_role = ROLE_BY_AGENT.get(selected)
    roles = set(state.get("roles", ()))
    if required_role is not None and required_role not in roles:
        return {
            "selected_agent": "denied",
            "error_code": "FORBIDDEN",
            "response": "You don't have permission to perform that action.",
        }
    return {"workflow_status": "AUTHORIZED"}


def selected_agent(state: AssistantState) -> AgentName:
    return state.get("selected_agent", "denied")


def specialist_placeholder(state: AssistantState) -> dict[str, object]:
    agent = state.get("selected_agent", "support")
    if agent == "denied":
        return {"workflow_status": "DENIED"}
    return {
        "workflow_status": "READY",
        "response": f"The {agent} agent is ready to handle this request.",
    }
