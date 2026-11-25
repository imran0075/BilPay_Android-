import json
import os
import uuid
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from urllib.parse import urlencode, parse_qs
from urllib.request import Request as URLRequest, urlopen
from urllib.error import URLError, HTTPError

from fastapi import Depends, HTTPException, Request
from pydantic import BaseModel, Field
from sqlalchemy import String, Integer, Numeric, DateTime, ForeignKey, select
from sqlalchemy.orm import Mapped, mapped_column, Session

from .main import Base, app, current_user, get_db, User


class SSLCOMMERZPayment(Base):
    __tablename__ = "sslcommerz_payments"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    tran_id: Mapped[str] = mapped_column(String(40), unique=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"), index=True)
    service_name: Mapped[str] = mapped_column(String(80))
    customer_reference: Mapped[str] = mapped_column(String(80), default="")
    amount: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    currency: Mapped[str] = mapped_column(String(3), default="BDT")
    environment: Mapped[str] = mapped_column(String(12), default="sandbox")
    status: Mapped[str] = mapped_column(String(20), default="PENDING")
    session_key: Mapped[str] = mapped_column(String(80), default="")
    val_id: Mapped[str] = mapped_column(String(80), default="")
    bank_tran_id: Mapped[str] = mapped_column(String(100), default="")
    risk_level: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc))


class SSLCOMMERZSessionRequest(BaseModel):
    service_name: str = Field(min_length=2, max_length=80)
    amount: Decimal = Field(ge=Decimal("10.00"), le=Decimal("500000.00"), max_digits=12, decimal_places=2)
    customer_reference: str = Field(default="", max_length=80)
    customer_email: str = Field(min_length=5, max_length=254)


def _config():
    mode = os.getenv("SSLCOMMERZ_ENV", "sandbox").strip().lower()
    if mode not in {"sandbox", "live"}:
        raise HTTPException(status_code=503, detail="SSLCOMMERZ_ENV must be sandbox or live")
    if mode == "live":
        store_id = os.getenv("SSLCOMMERZ_STORE_ID", "").strip()
        store_password = os.getenv("SSLCOMMERZ_STORE_PASSWORD", "").strip()
        base = "https://securepay.sslcommerz.com"
    else:
        store_id = os.getenv("SSLCOMMERZ_SANDBOX_STORE_ID", "").strip()
        store_password = os.getenv("SSLCOMMERZ_SANDBOX_STORE_PASSWORD", "").strip()
        base = "https://sandbox.sslcommerz.com"
    if not store_id or not store_password:
        raise HTTPException(status_code=503, detail={
            "code": "SSLCOMMERZ_CREDENTIALS_MISSING",
            "message": "এই পরিবেশের Store ID ও Store Password Render Environment Variables-এ সেট করা হয়নি।"
        })
    return mode, store_id, store_password, base


def _post_form(url: str, params: dict):
    body = urlencode(params).encode("utf-8")
    req = URLRequest(url, data=body, headers={"Content-Type": "application/x-www-form-urlencoded"}, method="POST")
    try:
        with urlopen(req, timeout=25) as response:
            return json.loads(response.read().decode("utf-8"))
    except (URLError, HTTPError, TimeoutError, ValueError) as exc:
        raise HTTPException(status_code=502, detail="SSLCOMMERZ-এর সঙ্গে সংযোগ করা যায়নি। পরে আবার চেষ্টা করুন।") from exc


def _get_json(url: str, params: dict):
    req = URLRequest(url + "?" + urlencode(params), headers={"Accept": "application/json"})
    try:
        with urlopen(req, timeout=25) as response:
            return json.loads(response.read().decode("utf-8"))
    except (URLError, HTTPError, TimeoutError, ValueError) as exc:
        raise HTTPException(status_code=502, detail="SSLCOMMERZ পেমেন্ট যাচাই করা যায়নি।") from exc


def _validate_and_update(tran_id: str, val_id: str, db: Session):
    order = db.scalar(select(SSLCOMMERZPayment).where(SSLCOMMERZPayment.tran_id == tran_id))
    if order is None:
        raise HTTPException(status_code=404, detail="পেমেন্ট অর্ডার পাওয়া যায়নি।")
    if order.status in {"PAID", "REVIEW"}:
        return order
    mode, store_id, store_password, base = _config()
    if mode != order.environment:
        raise HTTPException(status_code=409, detail="পেমেন্টের পরিবেশ ও সার্ভারের বর্তমান পরিবেশ মিলছে না।")
    result = _get_json(base + "/validator/api/validationserverAPI.php", {
        "val_id": val_id,
        "store_id": store_id,
        "store_passwd": store_password,
        "format": "json"
    })
    status = str(result.get("status", "")).upper()
    try:
        verified_amount = Decimal(str(result.get("amount", "0"))).quantize(Decimal("0.01"))
    except InvalidOperation:
        verified_amount = Decimal("0.00")
    if (status not in {"VALID", "VALIDATED"}
        or str(result.get("tran_id", "")) != order.tran_id
        or verified_amount != Decimal(order.amount).quantize(Decimal("0.01"))
        or str(result.get("currency", "")).upper() != "BDT"
        or str(result.get("store_id", store_id)) != store_id):
        order.status = "VERIFY_FAILED"
        order.updated_at = datetime.now(timezone.utc)
        db.commit()
        raise HTTPException(status_code=400, detail="পেমেন্টের তথ্য সার্ভারে সংরক্ষিত অর্ডারের সঙ্গে মেলেনি।")
    risk = int(result.get("risk_level", 0) or 0)
    order.val_id = val_id
    order.bank_tran_id = str(result.get("bank_tran_id", ""))[:100]
    order.risk_level = risk
    order.status = "REVIEW" if risk == 1 else "PAID"
    order.updated_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(order)
    return order


async def _callback_data(request: Request):
    if request.method == "GET":
        return dict(request.query_params)
    raw = (await request.body()).decode("utf-8", errors="replace")
    parsed = parse_qs(raw, keep_blank_values=True)
    return {key: values[-1] for key, values in parsed.items()}


@app.post("/api/v1/sslcommerz/session")
def create_sslcommerz_session(payload: SSLCOMMERZSessionRequest, request: Request,
                              user: User = Depends(current_user), db: Session = Depends(get_db)):
    mode, store_id, store_password, base = _config()
    merchant_email = os.getenv("BILPAY_MERCHANT_EMAIL", "").strip()
    if not merchant_email:
        raise HTTPException(status_code=503, detail="BILPAY_MERCHANT_EMAIL Render Environment Variables-এ সেট করতে হবে।")
    tran_id = "BP-" + uuid.uuid4().hex[:24].upper()
    base_url = str(request.base_url).rstrip("/")
    order = SSLCOMMERZPayment(
        tran_id=tran_id, user_id=user.id, service_name=payload.service_name.strip(),
        customer_reference=payload.customer_reference.strip(), amount=payload.amount,
        currency="BDT", environment=mode, status="INITIATING"
    )
    db.add(order)
    db.commit()
    params = {
        "store_id": store_id, "store_passwd": store_password,
        "total_amount": str(payload.amount), "currency": "BDT", "tran_id": tran_id,
        "success_url": base_url + "/api/v1/sslcommerz/success",
        "fail_url": base_url + "/api/v1/sslcommerz/fail",
        "cancel_url": base_url + "/api/v1/sslcommerz/cancel",
        "ipn_url": base_url + "/api/v1/sslcommerz/ipn",
        "product_name": payload.service_name.strip(), "product_category": "digital service",
        "product_profile": "general",
        "cus_name": (user.full_name or "BilPay Customer")[:50],
        "cus_email": payload.customer_email,
        "cus_add1": "Bangladesh", "cus_city": "Narsingdi",
        "cus_country": "Bangladesh", "cus_phone": user.phone,
        "shipping_method": "NO",
        "num_of_item": "1",
        "value_a": str(user.id),
        "value_b": tran_id
    }
    try:
        result = _post_form(base + "/gwprocess/v4/api.php", params)
    except HTTPException:
        order.status = "INITIATION_FAILED"
        db.commit()
        raise
    if str(result.get("status", "")).upper() != "SUCCESS" or not result.get("GatewayPageURL"):
        order.status = "INITIATION_FAILED"
        db.commit()
        raise HTTPException(status_code=502, detail={"code": "SESSION_CREATE_FAILED", "message": "SSLCOMMERZ পেমেন্ট সেশন তৈরি করতে পারেনি।"})
    order.session_key = str(result.get("sessionkey", ""))[:80]
    order.status = "PENDING"
    db.commit()
    return {"tran_id": tran_id, "gateway_url": result["GatewayPageURL"], "status": "PENDING",
            "environment": mode, "currency": "BDT", "amount": str(payload.amount)}


@app.api_route("/api/v1/sslcommerz/success", methods=["GET", "POST"])
async def sslcommerz_success(request: Request, db: Session = Depends(get_db)):
    data = await _callback_data(request)
    tran_id, val_id = str(data.get("tran_id", "")), str(data.get("val_id", ""))
    if not tran_id or not val_id:
        raise HTTPException(status_code=400, detail="পেমেন্ট যাচাইয়ের তথ্য পাওয়া যায়নি।")
    order = _validate_and_update(tran_id, val_id, db)
    return {"tran_id": order.tran_id, "status": order.status, "message": "পেমেন্ট যাচাই সম্পন্ন।"}


@app.api_route("/api/v1/sslcommerz/ipn", methods=["GET", "POST"])
async def sslcommerz_ipn(request: Request, db: Session = Depends(get_db)):
    data = await _callback_data(request)
    tran_id, val_id = str(data.get("tran_id", "")), str(data.get("val_id", ""))
    if not tran_id or not val_id:
        return {"ok": False, "message": "Missing transaction validation fields"}
    order = _validate_and_update(tran_id, val_id, db)
    return {"ok": True, "tran_id": order.tran_id, "status": order.status}


@app.api_route("/api/v1/sslcommerz/fail", methods=["GET", "POST"])
async def sslcommerz_fail(request: Request, db: Session = Depends(get_db)):
    data = await _callback_data(request)
    tran_id = str(data.get("tran_id", ""))
    order = db.scalar(select(SSLCOMMERZPayment).where(SSLCOMMERZPayment.tran_id == tran_id))
    if order is not None and order.status not in {"PAID", "REVIEW"}:
        order.status = "FAILED"
        db.commit()
    return {"tran_id": tran_id, "status": order.status if order else "UNKNOWN"}


@app.api_route("/api/v1/sslcommerz/cancel", methods=["GET", "POST"])
async def sslcommerz_cancel(request: Request, db: Session = Depends(get_db)):
    data = await _callback_data(request)
    tran_id = str(data.get("tran_id", ""))
    order = db.scalar(select(SSLCOMMERZPayment).where(SSLCOMMERZPayment.tran_id == tran_id))
    if order is not None and order.status not in {"PAID", "REVIEW"}:
        order.status = "CANCELLED"
        db.commit()
    return {"tran_id": tran_id, "status": order.status if order else "UNKNOWN"}


@app.get("/api/v1/sslcommerz/payments/{tran_id}")
def get_sslcommerz_payment(tran_id: str, user: User = Depends(current_user), db: Session = Depends(get_db)):
    order = db.scalar(select(SSLCOMMERZPayment).where(
        SSLCOMMERZPayment.tran_id == tran_id, SSLCOMMERZPayment.user_id == user.id
    ))
    if order is None:
        raise HTTPException(status_code=404, detail="পেমেন্ট পাওয়া যায়নি।")
    return {"tran_id": order.tran_id, "service_name": order.service_name,
            "amount": str(order.amount), "currency": order.currency,
            "status": order.status, "environment": order.environment,
            "risk_level": order.risk_level, "created_at": order.created_at.isoformat()}
