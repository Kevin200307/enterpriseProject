import json
import re
from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Literal

from app.clients.parcel import ParcelClient
from app.clients.pricing import PricingClient
from app.graph.builder import build_assistant_graph
from app.security.principal import AssistantPrincipal
from app.tools.customer import list_my_parcels
from app.tools.models import EmptyArguments, TrackingArguments
from app.tools.tracking import track_parcel

TRACKING_PATTERN = re.compile(r"\bSD-[0-9A-Z]{8}\b", re.IGNORECASE)


@dataclass(frozen=True, slots=True)
class AssistantEvent:
    event: Literal["token", "tool", "error"]
    data: dict[str, object]


class AssistantRunner:
    def __init__(self, parcel_client: ParcelClient, pricing_client: PricingClient) -> None:
        self._graph = build_assistant_graph()
        self._parcel_client = parcel_client
        self._pricing_client = pricing_client

    async def run(
        self, message: str, principal: AssistantPrincipal
    ) -> AsyncIterator[AssistantEvent]:
        state = await self._graph.ainvoke(
            {
                "message": message,
                "roles": tuple(sorted(principal.roles)),
                "user_id": principal.subject,
            }
        )
        if state.get("selected_agent") == "denied":
            yield AssistantEvent("token", {"text": state.get("response", "Request denied.")})
            return

        intent = state.get("intent")
        if intent == "TRACK_PARCEL":
            match = TRACKING_PATTERN.search(message)
            if match is None:
                yield AssistantEvent(
                    "token",
                    {"text": "Please provide a tracking number like SD-12AB34CD."},
                )
                return
            arguments = TrackingArguments(trackingNumber=match.group(0).upper())
            yield AssistantEvent(
                "tool",
                {"name": "trackParcel", "arguments": arguments.model_dump()},
            )
            result = await track_parcel(
                arguments,
                token=principal.token,
                parcel_client=self._parcel_client,
            )
            yield AssistantEvent("token", {"text": _readable_result(result)})
            return

        if intent == "LIST_MY_DELIVERIES":
            yield AssistantEvent("tool", {"name": "listMyParcels", "arguments": {}})
            result = await list_my_parcels(
                EmptyArguments(),
                token=principal.token,
                parcel_client=self._parcel_client,
            )
            yield AssistantEvent("token", {"text": _readable_result(result)})
            return

        if intent == "ESTIMATE_FEE":
            yield AssistantEvent(
                "token",
                {
                    "text": (
                        "Please provide the origin city, destination city, package size "
                        "(SMALL, MEDIUM, or LARGE), weight in kg, and whether it is express."
                    )
                },
            )
            return

        yield AssistantEvent(
            "token",
            {"text": "I can help with SwiftDrop tracking, delivery estimates, and your parcels."},
        )


def _readable_result(result: dict[str, object]) -> str:
    if "error" in result:
        return str(result["error"])
    return json.dumps(result, ensure_ascii=False, separators=(",", ":"))
