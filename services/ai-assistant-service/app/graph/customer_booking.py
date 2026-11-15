from typing import Any

from pydantic import ValidationError

from app.models.delivery import DeliveryDraft


class BookingDraftError(ValueError):
    pass


def update_delivery_draft(
    current: DeliveryDraft | None,
    fields: dict[str, Any],
) -> DeliveryDraft:
    merged = current.model_dump() if current is not None else {}
    for name, value in fields.items():
        if value is not None:
            merged[name] = value
    try:
        return DeliveryDraft.model_validate(merged)
    except ValidationError as error:
        raise BookingDraftError("Some delivery details are invalid") from error


def booking_state(draft: DeliveryDraft) -> dict[str, object]:
    missing = draft.missing_fields()
    return {
        "workflow_status": "READY_FOR_ESTIMATE" if not missing else "COLLECTING_INFORMATION",
        "workflow_slots": draft.model_dump(mode="json"),
        "missing_fields": missing,
    }
