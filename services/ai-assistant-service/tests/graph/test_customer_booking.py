from decimal import Decimal

import pytest

from app.graph.customer_booking import BookingDraftError, booking_state, update_delivery_draft
from app.models.delivery import DeliveryAddress


def address(city: str) -> DeliveryAddress:
    return DeliveryAddress(
        contactName="Test User",
        phone="+94771234567",
        line1="1 Main Street",
        city=city,
        postalCode="10400",
        zone="WESTERN",
    )


def test_incomplete_draft_reports_only_missing_fields() -> None:
    draft = update_delivery_draft(None, {"pickup": address("Moratuwa"), "weightKg": "3"})
    state = booking_state(draft)
    assert state["workflow_status"] == "COLLECTING_INFORMATION"
    assert state["missing_fields"] == ("dropoff", "size", "express")


def test_draft_can_be_completed_across_turns_and_quoted() -> None:
    first = update_delivery_draft(None, {"pickup": address("Moratuwa")})
    complete = update_delivery_draft(
        first,
        {
            "dropoff": address("Colombo"),
            "size": "MEDIUM",
            "weightKg": Decimal("3"),
            "express": True,
        },
    )
    assert booking_state(complete)["workflow_status"] == "READY_FOR_ESTIMATE"
    assert complete.pricing_request() == {
        "fromCity": "Moratuwa",
        "toCity": "Colombo",
        "size": "MEDIUM",
        "weightKg": "3",
        "express": True,
    }


def test_invalid_correction_does_not_produce_a_draft() -> None:
    with pytest.raises(BookingDraftError):
        update_delivery_draft(None, {"size": "HUGE", "weightKg": -1})


def test_incomplete_draft_cannot_be_quoted() -> None:
    draft = update_delivery_draft(None, {"pickup": address("Moratuwa")})
    with pytest.raises(ValueError):
        draft.pricing_request()
