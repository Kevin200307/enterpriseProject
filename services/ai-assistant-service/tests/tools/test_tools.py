from decimal import Decimal
from typing import Any

import pytest
from pydantic import ValidationError

from app.security.principal import AssistantPrincipal
from app.tools.customer import list_my_parcels
from app.tools.models import EmptyArguments, EstimateArguments, TrackingArguments
from app.tools.pricing import estimate_fee
from app.tools.registry import allowed_tools
from app.tools.tracking import track_parcel


class FakeParcelClient:
    async def track(self, tracking_number: str, token: str) -> dict[str, Any]:
        return {
            "trackingNumber": tracking_number,
            "status": "IN_TRANSIT",
            "recipientPhone": "must-not-leak",
        }

    async def list_mine(self, token: str, *, page: int = 0, size: int = 10) -> dict[str, Any]:
        return {
            "content": [
                {
                    "trackingNumber": "SD-12AB34CD",
                    "status": "CREATED",
                    "pickup": {"city": "Colombo", "street": "private"},
                    "dropoff": {"city": "Kandy", "phone": "private"},
                }
            ],
            "totalElements": 1,
            "totalPages": 1,
        }


class FakePricingClient:
    async def estimate(self, request: dict[str, object], token: str) -> dict[str, Any]:
        assert request["weightKg"] == "2.5"
        return {"total": 750, "currency": "LKR", "internalRuleId": "private"}


@pytest.mark.asyncio
async def test_tracking_result_is_privacy_reduced() -> None:
    result = await track_parcel(
        TrackingArguments(trackingNumber="SD-12AB34CD"),
        token="token",  # noqa: S106
        parcel_client=FakeParcelClient(),  # type: ignore[arg-type]
    )
    assert result == {"trackingNumber": "SD-12AB34CD", "status": "IN_TRANSIT"}


@pytest.mark.asyncio
async def test_estimate_result_is_minimized() -> None:
    result = await estimate_fee(
        EstimateArguments(
            fromCity=" Colombo ",
            toCity="Kandy",
            size="SMALL",
            weightKg=Decimal("2.5"),
            express=False,
        ),
        token="token",  # noqa: S106
        pricing_client=FakePricingClient(),  # type: ignore[arg-type]
    )
    assert result == {"total": 750, "currency": "LKR"}


@pytest.mark.asyncio
async def test_customer_list_excludes_addresses_and_contacts() -> None:
    result = await list_my_parcels(
        EmptyArguments(),
        token="token",  # noqa: S106
        parcel_client=FakeParcelClient(),  # type: ignore[arg-type]
    )
    parcel = result["parcels"][0]
    assert parcel["originCity"] == "Colombo"
    assert "street" not in str(parcel)
    assert "phone" not in str(parcel)


def test_tool_registry_is_role_scoped() -> None:
    customer = AssistantPrincipal("1", frozenset({"CUSTOMER"}), "token")
    courier = AssistantPrincipal("2", frozenset({"COURIER"}), "token")
    assert [tool.name for tool in allowed_tools(customer)] == [
        "trackParcel",
        "estimateFee",
        "listMyParcels",
    ]
    assert [tool.name for tool in allowed_tools(courier)] == ["trackParcel", "estimateFee"]


def test_tool_arguments_are_strict() -> None:
    with pytest.raises(ValidationError):
        TrackingArguments(trackingNumber="invalid")
    with pytest.raises(ValidationError):
        EstimateArguments(
            fromCity="A",
            toCity="B",
            size="SMALL",
            weightKg=Decimal("0"),
            express=False,
            ignored="not-allowed",  # type: ignore[call-arg]
        )
