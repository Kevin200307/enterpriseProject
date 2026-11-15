from datetime import timedelta
from uuid import uuid4

import pytest

from app.security.principal import AssistantPrincipal
from app.workflows.confirmation import ConfirmationError, ConfirmationService
from app.workflows.operations import InMemoryOperationStore, OperationStatus


def principal(subject: str = "owner", *roles: str) -> AssistantPrincipal:
    return AssistantPrincipal(subject, frozenset(roles or ("CUSTOMER",)), "fixture")


async def prepared(store: InMemoryOperationStore, *, ttl: timedelta = timedelta(minutes=10)):
    return await store.prepare(
        uuid4(),
        "owner",
        "CREATE_DELIVERY",
        {"size": "SMALL"},
        "CUSTOMER",
        ttl=ttl,
    )


async def test_confirmation_uses_server_stored_payload_and_single_execution_claim() -> None:
    store = InMemoryOperationStore()
    service = ConfirmationService(store)
    operation = await prepared(store)

    confirmed = await service.confirm(operation.id, principal())
    claimed = await service.claim_for_execution(operation.id, principal())

    assert confirmed.request_payload == {"size": "SMALL"}
    assert claimed.status == OperationStatus.EXECUTING
    with pytest.raises(ConfirmationError):
        await service.claim_for_execution(operation.id, principal())


async def test_cross_user_and_wrong_role_are_denied() -> None:
    store = InMemoryOperationStore()
    service = ConfirmationService(store)
    operation = await prepared(store)
    with pytest.raises(ConfirmationError):
        await service.confirm(operation.id, principal("attacker", "CUSTOMER"))
    with pytest.raises(ConfirmationError):
        await service.confirm(operation.id, principal("owner", "COURIER"))


async def test_expired_confirmation_is_persisted_as_expired() -> None:
    store = InMemoryOperationStore()
    service = ConfirmationService(store)
    operation = await prepared(store, ttl=timedelta(seconds=-1))
    with pytest.raises(ConfirmationError):
        await service.confirm(operation.id, principal())
    expired = await store.get(operation.id)
    assert expired is not None and expired.status == OperationStatus.EXPIRED


async def test_rejection_prevents_later_confirmation() -> None:
    store = InMemoryOperationStore()
    service = ConfirmationService(store)
    operation = await prepared(store)
    rejected = await service.reject(operation.id, principal())
    assert rejected.status == OperationStatus.REJECTED
    with pytest.raises(ConfirmationError):
        await service.confirm(operation.id, principal())
