from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictArguments(BaseModel):
    model_config = ConfigDict(extra="forbid")


class TrackingArguments(StrictArguments):
    trackingNumber: str = Field(pattern=r"^SD-[0-9A-Z]{8}$")


class EstimateArguments(StrictArguments):
    fromCity: str = Field(min_length=1, max_length=120)
    toCity: str = Field(min_length=1, max_length=120)
    size: Literal["SMALL", "MEDIUM", "LARGE"]
    weightKg: Decimal = Field(gt=0)
    express: bool


class EmptyArguments(StrictArguments):
    pass
