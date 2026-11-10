import os
from decimal import Decimal
from typing import Literal

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

app = FastAPI(
    title="BilPay API",
    description="Shared API foundation for BilPay Android, web and admin clients.",
    version="0.1.0",
)

default_origins = (
    "https://bilpay-official.onrender.com,"
    "https://bilpay-official-site.onrender.com,"
    "https://bilpay.com.bd,"
    "https://www.bilpay.com.bd"
)
allowed_origins = [
    origin.strip()
    for origin in os.getenv("BILPAY_ALLOWED_ORIGINS", default_origins).split(",")
    if origin.strip()
]
if allowed_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=allowed_origins,
        allow_credentials=False,
        allow_methods=["GET", "POST"],
        allow_headers=["Content-Type", "Authorization"],
    )

SERVICES = [
    {"id": "electricity", "name": "বিদ্যুৎ বিল", "category": "bill", "enabled": False},
    {"id": "water", "name": "পানি বিল", "category": "bill", "enabled": False},
    {"id": "gas", "name": "গ্যাস বিল", "category": "bill", "enabled": False},
    {"id": "internet", "name": "ইন্টারনেট বিল", "category": "bill", "enabled": False},
    {"id": "mobile_recharge", "name": "মোবাইল রিচার্জ", "category": "bill", "enabled": False},
    {"id": "education", "name": "শিক্ষা ফি", "category": "bill", "enabled": False},
    {"id": "government", "name": "সরকারি ফি", "category": "bill", "enabled": False},
]

PROVIDERS = [
    {"id": "bkash", "name": "bKash", "status": "awaiting_merchant_api"},
    {"id": "nagad", "name": "Nagad", "status": "awaiting_merchant_api"},
    {"id": "rocket", "name": "Rocket", "status": "awaiting_merchant_api"},
    {"id": "upay", "name": "Upay", "status": "awaiting_merchant_api"},
]


class PaymentIntentRequest(BaseModel):
    service_id: str = Field(min_length=2, max_length=40)
    provider_id: str = Field(min_length=2, max_length=30)
    amount: Decimal = Field(gt=0, le=Decimal("500000"), max_digits=12, decimal_places=2)
    customer_reference: str = Field(min_length=3, max_length=80)
    customer_phone: str = Field(pattern=r"^01[3-9][0-9]{8}$")
    currency: Literal["BDT"] = "BDT"


@app.get("/")
def root():
    return {"name": "BilPay API", "version": "0.1.0", "status": "ready"}


@app.get("/health")
def health():
    return {"ok": True, "service": "bilpay-api"}


@app.get("/api/v1/status")
def api_status():
    return {
        "name": "BilPay",
        "api_version": app.version,
        "status": "operational",
        "live_transactions_enabled": False,
        "provider_integrations": {
            provider["id"]: provider["status"] for provider in PROVIDERS
        },
        "message": "বাস্তব লেনদেন চালুর আগে অনুমোদিত পার্টনার API সংযোগ প্রয়োজন।",
    }


@app.get("/api/v1/services")
def services():
    return {"items": SERVICES}


@app.get("/api/v1/providers")
def providers():
    return {
        "items": PROVIDERS,
        "live_transactions_enabled": False,
        "message": "প্রতিটি সেবার অনুমোদিত API সংযোগ সম্পন্ন হলে চালু হবে।",
    }


@app.post("/api/v1/payments/initiate")
def initiate_payment(payload: PaymentIntentRequest):
    service = next((x for x in SERVICES if x["id"] == payload.service_id), None)
    provider = next((x for x in PROVIDERS if x["id"] == payload.provider_id), None)
    if service is None:
        raise HTTPException(status_code=404, detail="অজানা সেবা")
    if provider is None:
        raise HTTPException(status_code=404, detail="অজানা পেমেন্ট প্রোভাইডার")
    # Intentionally fail closed: no payment is accepted or simulated until
    # the selected provider's official merchant API contract is configured.
    raise HTTPException(
        status_code=503,
        detail={
            "code": "PROVIDER_API_NOT_CONFIGURED",
            "message": "এই প্রোভাইডারের অনুমোদিত API এখনো সংযুক্ত হয়নি। কোনো টাকা কাটা হয়নি।",
            "provider": provider["id"],
        },
    )
