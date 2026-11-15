import pytest

from app.graph.builder import build_assistant_graph, graph_mermaid


@pytest.mark.parametrize(
    ("message", "roles", "expected"),
    [
        ("Track SD-12AB34CD", (), "tracking"),
        ("Estimate the fee", ("CUSTOMER",), "customer"),
        ("Show my deliveries", ("CUSTOMER",), "customer"),
        ("Show webhook failures", ("MERCHANT",), "merchant"),
        ("Show my assignments", ("COURIER",), "courier"),
        ("Show unassigned parcels", ("DISPATCHER",), "dispatcher"),
        ("Approve merchant", ("ADMIN",), "admin"),
        ("How does SwiftDrop work?", (), "support"),
    ],
)
def test_routes_authorized_requests(
    message: str, roles: tuple[str, ...], expected: str
) -> None:
    result = build_assistant_graph().invoke({"message": message, "roles": roles})
    assert result["selected_agent"] == expected
    assert result["workflow_status"] == "READY"


def test_customer_cannot_reach_admin_node() -> None:
    result = build_assistant_graph().invoke(
        {"message": "Approve merchant", "roles": ("CUSTOMER",)}
    )
    assert result["selected_agent"] == "denied"
    assert result["workflow_status"] == "DENIED"
    assert result["error_code"] == "FORBIDDEN"


def test_invalid_input_is_denied_before_specialist_execution() -> None:
    result = build_assistant_graph().invoke({"message": "  ", "roles": ("ADMIN",)})
    assert result["selected_agent"] == "denied"
    assert result["error_code"] == "INVALID_MESSAGE"


def test_graph_can_be_visualized() -> None:
    diagram = graph_mermaid()
    assert "input_guard" in diagram
    assert "admin" in diagram
    assert "denied" in diagram
