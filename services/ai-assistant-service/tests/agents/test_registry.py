import pytest

from app.agents.registry import agent_for, tools_for_agent
from app.security.principal import AssistantPrincipal


def principal(*roles: str) -> AssistantPrincipal:
    return AssistantPrincipal("user", frozenset(roles), "fixture")


@pytest.mark.parametrize(
    ("agent", "role"),
    [
        ("customer", "CUSTOMER"),
        ("merchant", "MERCHANT"),
        ("courier", "COURIER"),
        ("dispatcher", "DISPATCHER"),
        ("admin", "ADMIN"),
    ],
)
def test_role_agent_requires_its_verified_role(agent: str, role: str) -> None:
    assert agent_for(agent, principal(role)) is not None
    assert agent_for(agent, principal("CUSTOMER" if role != "CUSTOMER" else "COURIER")) is None


def test_agent_receives_only_relevant_tools() -> None:
    customer = principal("CUSTOMER")
    customer_agent = agent_for("customer", customer)
    tracking_agent = agent_for("tracking", customer)
    assert customer_agent is not None
    assert tracking_agent is not None
    assert [tool.name for tool in tools_for_agent(customer_agent, customer)] == [
        "trackParcel",
        "estimateFee",
        "listMyParcels",
    ]
    assert [tool.name for tool in tools_for_agent(tracking_agent, customer)] == ["trackParcel"]


def test_admin_tools_are_never_exposed_to_non_admin() -> None:
    assert agent_for("admin", principal("CUSTOMER")) is None
