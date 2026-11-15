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
COURIER_ASSIGNMENTS = ToolSpec(
    "courierAssignments", "List assignments belonging to the authenticated courier.", EmptyArguments
)
COURIER_STATUS = ToolSpec(
    "updateCourierParcelStatus",
    "Update an assigned parcel through an allowed courier status transition.",
    EmptyArguments,
    "HIGH_IMPACT",
)
DISPATCH_QUEUE = ToolSpec("dispatchQueue", "View the unassigned parcel queue.", EmptyArguments)
DISPATCH_WORKLOAD = ToolSpec("dispatchWorkload", "View courier workloads.", EmptyArguments)
DISPATCH_SUGGEST = ToolSpec("suggestCourier", "Suggest couriers for a parcel.", EmptyArguments)
DISPATCH_ASSIGN = ToolSpec(
    "assignCourier", "Assign a courier after explicit confirmation.", EmptyArguments, "HIGH_IMPACT"
)
DISPATCH_UNASSIGN = ToolSpec(
    "unassignCourier",
    "Unassign a courier with a reason after explicit confirmation.",
    EmptyArguments,
    "HIGH_IMPACT",
)


def allowed_tools(principal: AssistantPrincipal) -> tuple[ToolSpec, ...]:
    tools = [TRACK_PARCEL, ESTIMATE_FEE]
    if principal.has_role("CUSTOMER"):
        tools.append(LIST_MY_PARCELS)
    if principal.has_role("MERCHANT"):
        tools.extend((MERCHANT_OVERVIEW, MERCHANT_ORDERS, WEBHOOK_FAILURES))
    if principal.has_role("COURIER"):
        tools.extend((COURIER_ASSIGNMENTS, COURIER_STATUS))
    if principal.has_role("DISPATCHER") or principal.has_role("ADMIN"):
        tools.extend(
            (
                DISPATCH_QUEUE,
                DISPATCH_WORKLOAD,
                DISPATCH_SUGGEST,
                DISPATCH_ASSIGN,
                DISPATCH_UNASSIGN,
            )
        )
    return tuple(tools)
