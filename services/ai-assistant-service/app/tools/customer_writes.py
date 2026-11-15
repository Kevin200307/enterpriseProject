from uuid import UUID

from app.clients.base import DownstreamError
from app.clients.parcel import ParcelClient
from app.clients.pricing import PricingClient
from app.models.delivery import DeliveryDraft
from app.security.principal import AssistantPrincipal
from app.workflows.confirmation import ConfirmationService
from app.workflows.operations import InMemoryOperationStore


class CustomerWriteTools:
    def __init__(
        self,
        parcel_client: ParcelClient,
        pricing_client: PricingClient,
        operations: InMemoryOperationStore,
        confirmations: ConfirmationService,
    ) -> None:
        self._parcels = parcel_client
        self._pricing = pricing_client
        self._operations = operations
        self._confirmations = confirmations

    async def prepare_delivery(
        self,
        conversation_id: UUID,
        principal: AssistantPrincipal,
        draft: DeliveryDraft,
    ) -> dict[str, object]:
        if not principal.has_role("CUSTOMER"):
            return {"error": "You don't have permission to create a delivery."}
        try:
            estimate = await self._pricing.estimate(draft.pricing_request(), principal.token)
        except DownstreamError:
            return {
                "error": "A live price could not be obtained, so the delivery was not prepared."
            }
        payload = draft.model_dump(mode="json", exclude={"notes"}, exclude_none=True)
        operation = await self._operations.prepare(
            conversation_id,
            principal.subject,
            "CREATE_DELIVERY",
            payload,
            "CUSTOMER",
        )
        return {
            "operationId": str(operation.id),
            "confirmationRequired": True,
            "expiresAt": operation.expires_at.isoformat(),
            "estimate": estimate,
            "summary": {
                "fromCity": draft.pickup.city if draft.pickup else None,
                "toCity": draft.dropoff.city if draft.dropoff else None,
                "size": draft.size,
                "weightKg": str(draft.weightKg),
                "express": draft.express,
            },
        }

    async def create_delivery(
        self, operation_id: UUID, principal: AssistantPrincipal
    ) -> dict[str, object]:
        operation = await self._confirmations.claim_for_execution(operation_id, principal)
        if operation.operation_type != "CREATE_DELIVERY":
            return {"error": "The confirmed operation is not a delivery booking."}
        try:
            result = await self._parcels.book(
                operation.request_payload,
                principal.token,
                operation.idempotency_key,
            )
        except DownstreamError:
            return {"error": "Delivery creation is temporarily unavailable."}
        if not isinstance(result, dict):
            return {"error": "Delivery creation returned an invalid response."}
        return {
            key: result.get(key)
            for key in ("id", "trackingNumber", "status", "fee", "currency")
        }
