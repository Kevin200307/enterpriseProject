from dataclasses import dataclass
from typing import Literal

from pydantic import BaseModel

from app.security.principal import AssistantPrincipal
from app.tools.models import EmptyArguments, EstimateArguments, TrackingArguments


@dataclass(frozen=True, slots=True)
class ToolSpec:
    name: str
    description: str
    arguments: type[BaseModel]
    risk: Literal["READ", "WRITE", "HIGH_IMPACT"] = "READ"


TRACK_PARCEL = ToolSpec(
    "trackParcel",
    "Look up a parcel's privacy-safe tracking status by tracking number.",
    TrackingArguments,
)
ESTIMATE_FEE = ToolSpec(
    "estimateFee",
    "Get a live delivery estimate for two cities, size, weight, and express choice.",
    EstimateArguments,
)
LIST_MY_PARCELS = ToolSpec(
    "listMyParcels",
    "List the authenticated customer's parcels without street addresses or contact details.",
    EmptyArguments,
)
MERCHANT_OVERVIEW = ToolSpec(
    "merchantOverview", "View the authenticated merchant status.", EmptyArguments
)
MERCHANT_ORDERS = ToolSpec("merchantOrders", "List authenticated merchant orders.", EmptyArguments)
WEBHOOK_FAILURES = ToolSpec(
    "webhookFailures", "List failed merchant webhook deliveries.", EmptyArguments
)


def allowed_tools(principal: AssistantPrincipal) -> tuple[ToolSpec, ...]:
    tools = [TRACK_PARCEL, ESTIMATE_FEE]
    if principal.has_role("CUSTOMER"):
        tools.append(LIST_MY_PARCELS)
    if principal.has_role("MERCHANT"):
        tools.extend((MERCHANT_OVERVIEW, MERCHANT_ORDERS, WEBHOOK_FAILURES))
    return tuple(tools)
