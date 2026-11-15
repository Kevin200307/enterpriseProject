from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class DeliveryAddress(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contactName: str = Field(min_length=1, max_length=120)
    phone: str = Field(pattern=r"^\+?[0-9][0-9 -]{6,19}$")
    line1: str = Field(min_length=1, max_length=200)
    line2: str | None = Field(default=None, max_length=200)
    city: str = Field(min_length=1, max_length=120)


class DeliveryDraft(BaseModel):
    model_config = ConfigDict(extra="forbid")

    pickup: DeliveryAddress | None = None
    dropoff: DeliveryAddress | None = None
    size: Literal["SMALL", "MEDIUM", "LARGE"] | None = None
    weightKg: Decimal | None = Field(default=None, gt=0)
    express: bool | None = None
    notes: str | None = Field(default=None, max_length=500)

    def missing_fields(self) -> tuple[str, ...]:
        required = ("pickup", "dropoff", "size", "weightKg", "express")
        return tuple(name for name in required if getattr(self, name) is None)

    def pricing_request(self) -> dict[str, object]:
        if self.pickup is None or self.dropoff is None or self.missing_fields():
            raise ValueError("Delivery draft is incomplete")
        return {
            "fromCity": self.pickup.city,
            "toCity": self.dropoff.city,
            "size": self.size,
            "weightKg": str(self.weightKg),
            "express": self.express,
        }
