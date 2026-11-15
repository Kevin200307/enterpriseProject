import logging
from uuid import UUID

from app.clients.base import DownstreamError
from app.clients.identity import IdentityClient
from app.clients.merchant import MerchantClient
from app.clients.notification import NotificationClient
from app.security.principal import AssistantPrincipal
from app.workflows.confirmation import ConfirmationService
from app.workflows.operations import InMemoryOperationStore

logger = logging.getLogger(__name__)
ALLOWED_USER_STATUSES = {"ACTIVE", "SUSPENDED"}
ALLOWED_ROLES = {"CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN"}
ALLOWED_MERCHANT_DECISIONS = {"APPROVE", "REJECT", "SUSPEND"}


class AdminWriteTools:
    def __init__(
        self,
        identity: IdentityClient,
        merchants: MerchantClient,
        notifications: NotificationClient,
        operations: InMemoryOperationStore,
        confirmations: ConfirmationService,
    ) -> None:
        self._identity = identity
        self._merchants = merchants
        self._notifications = notifications
        self._operations = operations
        self._confirmations = confirmations

    @staticmethod
    def _allowed(principal: AssistantPrincipal) -> bool:
        return principal.has_role("ADMIN")

    async def prepare_user_status(
        self, conversation_id: UUID, principal: AssistantPrincipal, user_id: str, status: str
    ) -> dict[str, object]:
        status = status.upper()
        if status not in ALLOWED_USER_STATUSES:
            return {"error": "User status must be ACTIVE or SUSPENDED."}
        return await self._prepare(
            conversation_id,
            principal,
            "ADMIN_USER_STATUS",
            {"userId": user_id, "status": status},
            f"Set user {user_id} status to {status}",
        )

    async def prepare_user_roles(
        self,
        conversation_id: UUID,
        principal: AssistantPrincipal,
        user_id: str,
        roles: list[str],
    ) -> dict[str, object]:
        normalized = sorted({role.upper() for role in roles})
        if not normalized or not set(normalized).issubset(ALLOWED_ROLES):
            return {"error": "One or more requested roles are invalid."}
        return await self._prepare(
            conversation_id,
            principal,
            "ADMIN_USER_ROLES",
            {"userId": user_id, "roles": normalized},
            f"Replace user {user_id} roles with {', '.join(normalized)}",
        )

    async def prepare_merchant_decision(
        self,
        conversation_id: UUID,
        principal: AssistantPrincipal,
        merchant_id: str,
        decision: str,
        reason: str | None = None,
    ) -> dict[str, object]:
        decision = decision.upper()
        if decision not in ALLOWED_MERCHANT_DECISIONS:
            return {"error": "Merchant decision must be APPROVE, REJECT, or SUSPEND."}
        if decision in {"REJECT", "SUSPEND"} and not (reason and reason.strip()):
            return {"error": "A reason is required for rejection or suspension."}
        return await self._prepare(
            conversation_id,
            principal,
            "ADMIN_MERCHANT_DECISION",
            {"merchantId": merchant_id, "decision": decision, "reason": reason},
            f"{decision.title()} merchant {merchant_id}",
        )

    async def prepare_notification_resend(
        self,
        conversation_id: UUID,
        principal: AssistantPrincipal,
        notification_id: str,
    ) -> dict[str, object]:
        return await self._prepare(
            conversation_id,
            principal,
            "ADMIN_NOTIFICATION_RESEND",
            {"notificationId": notification_id},
            f"Resend notification {notification_id}",
        )

    async def execute(self, operation_id: UUID, principal: AssistantPrincipal) -> dict[str, object]:
        operation = await self._confirmations.claim_for_execution(operation_id, principal)
        payload = operation.request_payload
        try:
            if operation.operation_type == "ADMIN_USER_STATUS":
                value = await self._identity.update_status(
                    str(payload["userId"]), str(payload["status"]), principal.token
                )
            elif operation.operation_type == "ADMIN_USER_ROLES":
                roles = payload.get("roles")
                if not isinstance(roles, list) or not all(isinstance(role, str) for role in roles):
                    return {"error": "The role update request is invalid."}
                value = await self._identity.update_roles(
                    str(payload["userId"]), roles, principal.token
                )
            elif operation.operation_type == "ADMIN_MERCHANT_DECISION":
                reason = payload.get("reason")
                value = await self._merchants.decide(
                    str(payload["merchantId"]),
                    str(payload["decision"]),
                    principal.token,
                    reason if isinstance(reason, str) else None,
                )
            elif operation.operation_type == "ADMIN_NOTIFICATION_RESEND":
                value = await self._notifications.resend(
                    str(payload["notificationId"]), principal.token
                )
            else:
                return {"error": "The confirmed operation is not an administrator action."}
        except (DownstreamError, KeyError):
            logger.warning(
                "admin_operation_failed operation_id=%s actor=%s type=%s",
                operation.id,
                principal.subject,
                operation.operation_type,
            )
            return {"error": "The administrator operation was rejected or unavailable."}
        logger.info(
            "admin_operation_executed operation_id=%s actor=%s type=%s",
            operation.id,
            principal.subject,
            operation.operation_type,
        )
        return value if isinstance(value, dict) else {"error": "Invalid operation response."}

    async def _prepare(
        self,
        conversation_id: UUID,
        principal: AssistantPrincipal,
        operation_type: str,
        payload: dict[str, object],
        summary: str,
    ) -> dict[str, object]:
        if not self._allowed(principal):
            return {"error": "Administrator permission is required."}
        operation = await self._operations.prepare(
            conversation_id, principal.subject, operation_type, payload, "ADMIN"
        )
        logger.info(
            "admin_operation_prepared operation_id=%s actor=%s type=%s",
            operation.id,
            principal.subject,
            operation_type,
        )
        return {
            "operationId": str(operation.id),
            "confirmationRequired": True,
            "expiresAt": operation.expires_at.isoformat(),
            "summary": summary,
        }
