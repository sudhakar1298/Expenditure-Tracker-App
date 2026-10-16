from pydantic import BaseModel, Field


class DevicePairRequest(BaseModel):
    device_id: str = Field(min_length=16, max_length=128)
    public_key_der: str = Field(min_length=80, max_length=2048)
    pairing_code: str = Field(min_length=24, max_length=256)
    label: str = Field(default="Expense tracker phone", min_length=1, max_length=80)


class DevicePairResponse(BaseModel):
    paired: bool
    device_id: str
    label: str
