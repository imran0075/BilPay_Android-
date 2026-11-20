import os
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone, timedelta
from decimal import Decimal
from typing import Literal, Optional

import jwt
from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, ConfigDict, Field
from pwdlib import PasswordHash
from sqlalchemy import create_engine, select, String, Integer, Numeric, DateTime, ForeignKey, func, Boolean
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship, sessionmaker, Session

db_url = os.getenv("DATABASE_URL", "").strip()
if not db_url:
    raise RuntimeError("DATABASE_URL is required")
if db_url.startswith("postgres://"):
    db_url = db_url.replace("postgres://", "postgresql+psycopg://", 1)
elif db_url.startswith("postgresql://"):
    db_url = db_url.replace("postgresql://", "postgresql+psycopg://", 1)
engine = create_engine(db_url, pool_pre_ping=True, pool_recycle=300)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)

class Base(DeclarativeBase):
    pass

class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    phone: Mapped[str] = mapped_column(String(11), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    full_name: Mapped[str] = mapped_column(String(80), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))
    transactions: Mapped[list["Transaction"]] = relationship(back_populates="user", cascade="all, delete-orphan")

class Wallet(Base):
    __tablename__ = "wallets"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), unique=True, index=True)
    balance: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=Decimal("0.00"))
    currency: Mapped[str] = mapped_column(String(3), default="BDT")
    mode: Mapped[str] = mapped_column(String(20), default="DISABLED")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc))

class Transaction(Base):
    __tablename__ = "transactions"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    transaction_id: Mapped[str] = mapped_column(String(40), unique=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    service_id: Mapped[str] = mapped_column(String(40))
    provider_id: Mapped[str] = mapped_column(String(30))
    amount: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    currency: Mapped[str] = mapped_column(String(3), default="BDT")
    status: Mapped[str] = mapped_column(String(12))
    mode: Mapped[str] = mapped_column(String(20), default="TEST_ONLY")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True)
    user: Mapped[User] = relationship(back_populates="transactions")


class SandboxWallet(Base):
    __tablename__ = "sandbox_wallets"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), unique=True, index=True)
    balance: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=Decimal("0.00"))
    currency: Mapped[str] = mapped_column(String(3), default="BDT")
    mode: Mapped[str] = mapped_column(String(20), default="SANDBOX")
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc))

class SandboxLedgerEntry(Base):
    __tablename__ = "sandbox_ledger_entries"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    entry_id: Mapped[str] = mapped_column(String(40), unique=True, index=True)
    transaction_id: Mapped[str] = mapped_column(String(40), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    amount: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    entry_type: Mapped[str] = mapped_column(String(20))
    mode: Mapped[str] = mapped_column(String(20), default="SANDBOX")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True)

class SandboxTransfer(Base):
    __tablename__ = "sandbox_transfers"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    transaction_id: Mapped[str] = mapped_column(String(40), unique=True, index=True)
    idempotency_key: Mapped[str] = mapped_column(String(100), unique=True, index=True)
    sender_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    recipient_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    amount: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    fee: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=Decimal("0.00"))
    status: Mapped[str] = mapped_column(String(20), default="POSTED")
    mode: Mapped[str] = mapped_column(String(20), default="SANDBOX")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True)

class AdminAuditLog(Base):
    __tablename__ = "admin_audit_logs"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    admin_user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    action: Mapped[str] = mapped_column(String(60))
    target: Mapped[str] = mapped_column(String(100), default="")
    details: Mapped[str] = mapped_column(String(500), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True)

class ServiceConfig(Base):
    __tablename__ = "service_configs"
    service_id: Mapped[str] = mapped_column(String(40), primary_key=True)
    visible: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc))

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

password_hasher = PasswordHash.recommended()
def jwt_secret():
    secret = os.getenv("BILPAY_JWT_SECRET", "")
    if len(secret) < 32:
        raise RuntimeError("BILPAY_JWT_SECRET must be at least 32 characters")
    return secret

def issue_token(user_id: int):
    now = datetime.now(timezone.utc)
    return jwt.encode({"sub": str(user_id), "iat": now, "exp": now + timedelta(days=7)}, jwt_secret(), algorithm="HS256")

def current_user(authorization: Optional[str] = Header(default=None), db: Session = Depends(get_db)):
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="লগইন করুন।")
    try:
        data = jwt.decode(authorization.split(" ", 1)[1].strip(), jwt_secret(), algorithms=["HS256"])
        user = db.get(User, int(data["sub"]))
    except (jwt.InvalidTokenError, ValueError, KeyError, RuntimeError):
        raise HTTPException(status_code=401, detail="লগইন সেশন সঠিক নয় বা মেয়াদ শেষ হয়েছে।")
    if user is None:
        raise HTTPException(status_code=401, detail="অ্যাকাউন্ট পাওয়া যায়নি।")
    return user

def admin_user(user: User = Depends(current_user)):
    allowed = {x.strip() for x in os.getenv("BILPAY_ADMIN_PHONES", "").split(",") if x.strip()}
    if not allowed:
        raise HTTPException(status_code=503, detail="অ্যাডমিন অ্যাক্সেস এখনো নিরাপদভাবে কনফিগার করা হয়নি।")
    if user.phone not in allowed:
        raise HTTPException(status_code=403, detail="এই অংশ ব্যবহারের অনুমতি নেই।")
    return user

def record_admin_action(db: Session, admin: User, action: str, target: str = "", details: str = ""):
    db.add(AdminAuditLog(
        admin_user_id=admin.id,
        action=action[:60],
        target=target[:100],
        details=details[:500]
    ))
    db.commit()

@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(bind=engine)
    with SessionLocal() as db:
        # Retire all legacy demo balances. They were never real customer funds.
        db.query(Wallet).filter(Wallet.mode != "LIVE").update(
            {Wallet.balance: Decimal("0.00"), Wallet.mode: "DISABLED"},
            synchronize_session=False,
        )
        for item in SERVICES:
            if db.get(ServiceConfig, item["id"]) is None:
                db.add(ServiceConfig(service_id=item["id"], visible=True))
        db.commit()
    yield

app = FastAPI(title="BilPay API", description="Shared API foundation for BilPay Android, web and admin clients.", version="0.3.0", lifespan=lifespan)
origins = os.getenv("BILPAY_ALLOWED_ORIGINS", "https://bilpay-official.onrender.com,https://bilpay-official-site.onrender.com,https://bilpay.com.bd,https://www.bilpay.com.bd")
app.add_middleware(CORSMiddleware, allow_origins=[x.strip() for x in origins.split(",") if x.strip()], allow_credentials=False, allow_methods=["GET","POST","PUT"], allow_headers=["Content-Type","Authorization"])

SERVICES = [
 {"id":"electricity","name":"বিদ্যুৎ বিল","category":"bill","enabled":False},
 {"id":"water","name":"পানি বিল","category":"bill","enabled":False},
 {"id":"gas","name":"গ্যাস বিল","category":"bill","enabled":False},
 {"id":"internet","name":"ইন্টারনেট বিল","category":"bill","enabled":False},
 {"id":"mobile_recharge","name":"মোবাইল রিচার্জ","category":"recharge","enabled":False},
 {"id":"tv","name":"টিভি / ডিটিএইচ বিল","category":"bill","enabled":False},
 {"id":"telephone","name":"টেলিফোন বিল","category":"bill","enabled":False},
 {"id":"education","name":"শিক্ষা ফি","category":"bill","enabled":False},
 {"id":"hospital","name":"হাসপাতাল বিল","category":"bill","enabled":False},
 {"id":"government","name":"সরকারি ফি","category":"bill","enabled":False},
]
PROVIDERS = [{"id":x,"name":n,"status":"awaiting_merchant_api"} for x,n in [("bkash","bKash"),("nagad","Nagad"),("rocket","Rocket"),("upay","Upay")]]

class RegisterRequest(BaseModel):
    phone: str = Field(pattern=r"^01[3-9][0-9]{8}$")
    password: str = Field(min_length=10, max_length=128)
    full_name: str = Field(default="", max_length=80)
class LoginRequest(BaseModel):
    phone: str = Field(pattern=r"^01[3-9][0-9]{8}$")
    password: str = Field(min_length=1, max_length=128)
class UserOut(BaseModel):
    id: int
    phone: str
    full_name: str
    model_config = ConfigDict(from_attributes=True)
class AuthOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut
class ProfileUpdateRequest(BaseModel):
    full_name: str = Field(min_length=1, max_length=80)
class PasswordChangeRequest(BaseModel):
    current_password: str = Field(min_length=1, max_length=128)
    new_password: str = Field(min_length=10, max_length=128)
class PaymentIntentRequest(BaseModel):
    service_id: str = Field(min_length=2, max_length=40)
    provider_id: str = Field(min_length=2, max_length=30)
    amount: Decimal = Field(gt=0, le=Decimal("500000"), max_digits=12, decimal_places=2)
    customer_reference: str = Field(min_length=3, max_length=80)
    customer_phone: str = Field(pattern=r"^01[3-9][0-9]{8}$")
    currency: Literal["BDT"] = "BDT"

class SandboxGrantRequest(BaseModel):
    user_phone: str = Field(pattern=r"^01[3-9][0-9]{8}$")
    amount: Decimal = Field(gt=0, le=Decimal("10000"), max_digits=7, decimal_places=2)
class SandboxTransferRequest(BaseModel):
    recipient_phone: str = Field(pattern=r"^01[3-9][0-9]{8}$")
    amount: Decimal = Field(gt=0, le=Decimal("10000"), max_digits=7, decimal_places=2)

def sandbox_enabled():
    if os.getenv("BILPAY_SANDBOX_LEDGER_ENABLED", "false").strip().lower() not in {"1", "true", "yes", "on"}:
        raise HTTPException(status_code=503, detail={"code":"SANDBOX_DISABLED","message":"Sandbox wallet এখনো চালু করা হয়নি।"})

class ServiceVisibilityRequest(BaseModel):
    visible: bool

@app.get("/")
def root():
    return {"name":"BilPay API","version":app.version,"status":"ready","account_registration_enabled":True,"wallet_enabled":False,"live_transactions_enabled":False}
@app.get("/health")
def health():
    return {"ok":True,"service":"bilpay-api"}
@app.get("/api/v1/status")
def status():
    return {"name":"BilPay","api_version":app.version,"status":"operational","account_registration_enabled":True,"wallet_enabled":False,"live_transactions_enabled":False,"database_enabled":True,"provider_integrations":{p["id"]:p["status"] for p in PROVIDERS},"message":"অ্যাকাউন্ট সেবা চালু আছে। বাস্তব ওয়ালেট ও পেমেন্ট চালুর আগে অনুমোদিত লেজার এবং পার্টনার API সংযোগ প্রয়োজন।"}
@app.get("/api/v1/services")
def services(db: Session = Depends(get_db)):
    configs = {x.service_id: x.visible for x in db.scalars(select(ServiceConfig)).all()}
    items = [{**item, "visible": configs.get(item["id"], True), "enabled": False} for item in SERVICES]
    return {"items": items, "live_transactions_enabled": False, "message": "সেবা তালিকায় দেখা গেলেও বাস্তব পেমেন্ট এখনো বন্ধ।"}
@app.get("/api/v1/providers")
def providers():
    return {"items":PROVIDERS,"live_transactions_enabled":False,"message":"প্রতিটি সেবার অনুমোদিত API সংযোগ সম্পন্ন হলে চালু হবে।"}

@app.post("/api/v1/auth/register", response_model=AuthOut)
def register(payload:RegisterRequest, db:Session=Depends(get_db)):
    if db.scalar(select(User).where(User.phone==payload.phone)):
        raise HTTPException(status_code=409,detail="এই মোবাইল নম্বর দিয়ে আগে অ্যাকাউন্ট খোলা হয়েছে।")
    user=User(phone=payload.phone,password_hash=password_hasher.hash(payload.password),full_name=payload.full_name.strip())
    db.add(user); db.commit(); db.refresh(user)
    return AuthOut(access_token=issue_token(user.id),user=user)

@app.post("/api/v1/auth/login", response_model=AuthOut)
def login(payload:LoginRequest, db:Session=Depends(get_db)):
    user=db.scalar(select(User).where(User.phone==payload.phone))
    if user is None or not password_hasher.verify(payload.password,user.password_hash):
        raise HTTPException(status_code=401,detail="মোবাইল নম্বর অথবা পাসওয়ার্ড সঠিক নয়।")
    return AuthOut(access_token=issue_token(user.id),user=user)

@app.get("/api/v1/auth/me",response_model=UserOut)
def me(user:User=Depends(current_user)):
    return user

@app.put("/api/v1/auth/profile", response_model=UserOut)
def update_profile(payload: ProfileUpdateRequest, user: User = Depends(current_user), db: Session = Depends(get_db)):
    name = payload.full_name.strip()
    if not name:
        raise HTTPException(status_code=422, detail="নাম খালি রাখা যাবে না।")
    user.full_name = name
    db.commit()
    db.refresh(user)
    return user

@app.post("/api/v1/auth/change-password")
def change_password(payload: PasswordChangeRequest, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if not password_hasher.verify(payload.current_password, user.password_hash):
        raise HTTPException(status_code=400, detail="বর্তমান পাসওয়ার্ড সঠিক নয়।")
    if password_hasher.verify(payload.new_password, user.password_hash):
        raise HTTPException(status_code=400, detail="নতুন পাসওয়ার্ডটি বর্তমান পাসওয়ার্ডের থেকে আলাদা হতে হবে।")
    user.password_hash = password_hasher.hash(payload.new_password)
    db.commit()
    return {"ok": True, "message": "পাসওয়ার্ড পরিবর্তন হয়েছে। নিরাপত্তার জন্য আবার লগইন করুন।"}


@app.get("/api/v1/sandbox/status")
def sandbox_status():
    enabled = os.getenv("BILPAY_SANDBOX_LEDGER_ENABLED", "false").strip().lower() in {"1", "true", "yes", "on"}
    return {"sandbox_enabled": enabled, "live_wallet_enabled": False, "live_transactions_enabled": False,
            "real_money": False, "redeemable": False, "message": "Sandbox test balance বাস্তব টাকা নয়; উত্তোলন বা redeem করা যাবে না।"}

@app.get("/api/v1/sandbox/wallet")
def sandbox_wallet(user: User = Depends(current_user), db: Session = Depends(get_db)):
    sandbox_enabled()
    wallet = db.scalar(select(SandboxWallet).where(SandboxWallet.user_id == user.id, SandboxWallet.mode == "SANDBOX"))
    balance = wallet.balance if wallet else Decimal("0.00")
    return {"balance": str(balance), "currency":"BDT", "mode":"SANDBOX", "real_money":False, "redeemable":False}

@app.post("/api/v1/admin/sandbox/grant")
def sandbox_grant(payload: SandboxGrantRequest, admin: User = Depends(admin_user), db: Session = Depends(get_db)):
    sandbox_enabled()
    target = db.scalar(select(User).where(User.phone == payload.user_phone))
    if target is None:
        raise HTTPException(status_code=404, detail="পরীক্ষার অ্যাকাউন্ট পাওয়া যায়নি।")
    db.commit()
    with db.begin():
        wallet = db.scalar(select(SandboxWallet).where(SandboxWallet.user_id == target.id).with_for_update())
        if wallet is None:
            wallet = SandboxWallet(user_id=target.id, balance=Decimal("0.00"), mode="SANDBOX")
            db.add(wallet)
            db.flush()
        txid = "SBX-" + uuid.uuid4().hex[:24].upper()
        wallet.balance = Decimal(wallet.balance) + payload.amount
        db.add_all([
            SandboxLedgerEntry(entry_id=uuid.uuid4().hex, transaction_id=txid, user_id=target.id,
                               amount=payload.amount, entry_type="TEST_CREDIT", mode="SANDBOX"),
            SandboxLedgerEntry(entry_id=uuid.uuid4().hex, transaction_id=txid, user_id=admin.id,
                               amount=-payload.amount, entry_type="TEST_FAUCET_DEBIT", mode="SANDBOX")
        ])
        db.add(AdminAuditLog(admin_user_id=admin.id, action="sandbox_test_credit", target=target.phone,
                             details=f"{payload.amount} BDT non-redeemable sandbox credit"))
    return {"transaction_id":txid, "amount":str(payload.amount), "mode":"SANDBOX",
            "real_money":False, "redeemable":False, "message":"শুধু পরীক্ষার জন্য কাল্পনিক ব্যালেন্স যোগ হয়েছে; বাস্তব টাকা নয়।"}

@app.post("/api/v1/sandbox/transfers/internal")
def sandbox_internal_transfer(payload: SandboxTransferRequest, idempotency_key: str = Header(..., alias="Idempotency-Key", min_length=8, max_length=100),
                              user: User = Depends(current_user), db: Session = Depends(get_db)):
    sandbox_enabled()
    recipient = db.scalar(select(User).where(User.phone == payload.recipient_phone))
    if recipient is None:
        raise HTTPException(status_code=404, detail="প্রাপকের BilPay অ্যাকাউন্ট পাওয়া যায়নি।")
    if recipient.id == user.id:
        raise HTTPException(status_code=400, detail="নিজের অ্যাকাউন্টে Send Money করা যাবে না।")
    existing = db.scalar(select(SandboxTransfer).where(SandboxTransfer.idempotency_key == idempotency_key))
    if existing:
        if existing.sender_id != user.id or existing.recipient_id != recipient.id or existing.amount != payload.amount:
            raise HTTPException(status_code=409, detail="এই Idempotency-Key আগে অন্য অনুরোধে ব্যবহৃত হয়েছে।")
        return {"transaction_id":existing.transaction_id, "amount":str(existing.amount), "fee":"0.00",
                "recipient_credit":str(existing.amount), "status":existing.status, "mode":"SANDBOX",
                "real_money":False, "redeemable":False}
    db.commit()
    with db.begin():
        locked_wallets = db.scalars(select(SandboxWallet).where(
            SandboxWallet.user_id.in_(sorted([user.id, recipient.id])),
            SandboxWallet.mode == "SANDBOX"
        ).order_by(SandboxWallet.user_id).with_for_update()).all()
        wallet_map = {wallet.user_id: wallet for wallet in locked_wallets}
        sender_wallet = wallet_map.get(user.id)
        recipient_wallet = wallet_map.get(recipient.id)
        if sender_wallet is None or Decimal(sender_wallet.balance) < payload.amount:
            raise HTTPException(status_code=400, detail="Sandbox ব্যালেন্স পর্যাপ্ত নয়।")
        if recipient_wallet is None:
            recipient_wallet = SandboxWallet(user_id=recipient.id, balance=Decimal("0.00"), mode="SANDBOX")
            db.add(recipient_wallet)
            db.flush()
        txid = "SBX-" + uuid.uuid4().hex[:24].upper()
        sender_wallet.balance = Decimal(sender_wallet.balance) - payload.amount
        recipient_wallet.balance = Decimal(recipient_wallet.balance) + payload.amount
        db.add(SandboxTransfer(transaction_id=txid, idempotency_key=idempotency_key, sender_id=user.id,
                               recipient_id=recipient.id, amount=payload.amount, fee=Decimal("0.00"),
                               status="POSTED", mode="SANDBOX"))
        db.add_all([
            SandboxLedgerEntry(entry_id=uuid.uuid4().hex, transaction_id=txid, user_id=user.id,
                               amount=-payload.amount, entry_type="TRANSFER_DEBIT", mode="SANDBOX"),
            SandboxLedgerEntry(entry_id=uuid.uuid4().hex, transaction_id=txid, user_id=recipient.id,
                               amount=payload.amount, entry_type="TRANSFER_CREDIT", mode="SANDBOX")
        ])
    return {"transaction_id":txid, "amount":str(payload.amount), "fee":"0.00",
            "recipient_credit":str(payload.amount), "status":"POSTED", "mode":"SANDBOX",
            "real_money":False, "redeemable":False, "message":"পরীক্ষামূলক Send Money সফল; কোনো বাস্তব টাকা স্থানান্তর হয়নি।"}

@app.get("/api/v1/sandbox/transactions")
def sandbox_history(user: User = Depends(current_user), db: Session = Depends(get_db), limit: int = 20):
    sandbox_enabled()
    limit = max(1, min(limit, 100))
    rows = db.scalars(select(SandboxTransfer).where(
        (SandboxTransfer.sender_id == user.id) | (SandboxTransfer.recipient_id == user.id)
    ).order_by(SandboxTransfer.created_at.desc()).limit(limit)).all()
    return {"items":[{"transaction_id":r.transaction_id, "amount":str(r.amount), "fee":"0.00",
                      "direction":"OUT" if r.sender_id == user.id else "IN", "status":r.status,
                      "mode":"SANDBOX", "real_money":False} for r in rows]}


@app.get("/api/v1/wallet")
def get_wallet(user:User=Depends(current_user),db:Session=Depends(get_db)):
    # No wallet is exposed unless a future approved real ledger is explicitly enabled.
    if os.getenv("BILPAY_LIVE_LEDGER_ENABLED", "false").strip().lower() not in {"1","true","yes","on"}:
        raise HTTPException(status_code=503, detail={
            "code":"LIVE_LEDGER_NOT_CONFIGURED",
            "message":"বাস্তব ওয়ালেট এখনো চালু হয়নি। কোনো ডেমো বা কাল্পনিক ব্যালেন্স দেখানো হবে না।"
        })
    wallet=db.scalar(select(Wallet).where(Wallet.user_id==user.id, Wallet.mode=="LIVE"))
    if wallet is None:
        raise HTTPException(status_code=404, detail="এই অ্যাকাউন্টের বাস্তব ওয়ালেট এখনো তৈরি হয়নি।")
    return {"wallet_id":wallet.id,"balance":str(wallet.balance),"currency":wallet.currency,"mode":"LIVE","real_money":True}

@app.get("/api/v1/transactions")
def history(user:User=Depends(current_user),db:Session=Depends(get_db),limit:int=20):
    limit=max(1,min(limit,100))
    rows=db.scalars(select(Transaction).where(Transaction.user_id==user.id, Transaction.mode=="LIVE").order_by(Transaction.created_at.desc()).limit(limit)).all()
    return {"items":[{"transaction_id":r.transaction_id,"service_id":r.service_id,"provider_id":r.provider_id,"amount":str(r.amount),"currency":r.currency,"status":r.status,"mode":r.mode,"created_at":r.created_at.isoformat()} for r in rows]}

@app.get("/api/v1/admin/services")
def admin_services(admin: User = Depends(admin_user), db: Session = Depends(get_db)):
    configs = {x.service_id: x.visible for x in db.scalars(select(ServiceConfig)).all()}
    record_admin_action(db, admin, "view_services", "service_catalogue", "Viewed service visibility settings")
    return {"items": [{**item, "visible": configs.get(item["id"], True), "enabled": False} for item in SERVICES],
            "live_transactions_enabled": False,
            "message": "অ্যাডমিন কেবল তালিকায় সেবা দেখানো বা লুকানোর নিয়ন্ত্রণ পান; বাস্তব পেমেন্ট চালু করতে পারেন না।"}

@app.put("/api/v1/admin/services/{service_id}")
def update_service_visibility(service_id: str, payload: ServiceVisibilityRequest,
                              admin: User = Depends(admin_user), db: Session = Depends(get_db)):
    item = next((x for x in SERVICES if x["id"] == service_id), None)
    if item is None:
        raise HTTPException(status_code=404, detail="সেবা পাওয়া যায়নি।")
    config = db.get(ServiceConfig, service_id)
    if config is None:
        config = ServiceConfig(service_id=service_id, visible=payload.visible)
        db.add(config)
    else:
        config.visible = payload.visible
    db.commit()
    record_admin_action(db, admin, "update_service_visibility", service_id,
                        "visible=" + str(payload.visible).lower())
    return {"service_id": service_id, "visible": config.visible, "enabled": False,
            "live_transactions_enabled": False,
            "message": "সেবার তালিকায় প্রদর্শনের অবস্থা আপডেট হয়েছে; বাস্তব পেমেন্ট বন্ধ আছে।"}

@app.get("/api/v1/admin/overview")
def admin_overview(admin: User = Depends(admin_user), db: Session = Depends(get_db)):
    total_users = db.scalar(select(func.count()).select_from(User)) or 0
    live_rows = select(Transaction).where(Transaction.mode == "LIVE")
    total_transactions = db.scalar(select(func.count()).select_from(live_rows.subquery())) or 0
    successful_transactions = db.scalar(select(func.count()).select_from(Transaction).where(Transaction.mode == "LIVE", Transaction.status == "success")) or 0
    failed_transactions = db.scalar(select(func.count()).select_from(Transaction).where(Transaction.mode == "LIVE", Transaction.status == "failed")) or 0
    record_admin_action(db, admin, "view_overview", "dashboard", "Read dashboard totals")
    return {
        "mode": "ADMIN_READ_ONLY",
        "total_users": total_users,
        "total_transactions": total_transactions,
        "successful_transactions": successful_transactions,
        "failed_transactions": failed_transactions,
        "live_transactions_enabled": False,
        "message": "এটি প্রশাসনিক পর্যবেক্ষণ; এখান থেকে বাস্তব অর্থ লেনদেন করা যায় না।"
    }

@app.get("/api/v1/admin/users")
def admin_users(admin: User = Depends(admin_user), db: Session = Depends(get_db), limit: int = 50):
    limit = max(1, min(limit, 100))
    rows = db.scalars(select(User).order_by(User.created_at.desc()).limit(limit)).all()
    record_admin_action(db, admin, "view_users", "users", f"Viewed up to {limit} users")
    return {"items": [{"id": u.id, "phone": u.phone, "full_name": u.full_name, "created_at": u.created_at.isoformat()} for u in rows]}

@app.get("/api/v1/admin/transactions")
def admin_transactions(admin: User = Depends(admin_user), db: Session = Depends(get_db), limit: int = 50):
    limit = max(1, min(limit, 100))
    rows = db.execute(select(Transaction, User.phone).join(User, Transaction.user_id == User.id).where(Transaction.mode == "LIVE").order_by(Transaction.created_at.desc()).limit(limit)).all()
    record_admin_action(db, admin, "view_transactions", "transactions", f"Viewed up to {limit} transactions")
    return {"items": [{"transaction_id": t.transaction_id, "user_phone": phone, "service_id": t.service_id, "provider_id": t.provider_id, "amount": str(t.amount), "currency": t.currency, "status": t.status, "mode": t.mode, "created_at": t.created_at.isoformat()} for t, phone in rows]}

@app.get("/api/v1/admin/audit")
def admin_audit(admin: User = Depends(admin_user), db: Session = Depends(get_db), limit: int = 50):
    limit = max(1, min(limit, 100))
    record_admin_action(db, admin, "view_audit_log", "audit", f"Viewed up to {limit} audit entries")
    rows = db.scalars(select(AdminAuditLog).order_by(AdminAuditLog.created_at.desc()).limit(limit)).all()
    return {"items": [{"id": x.id, "admin_user_id": x.admin_user_id, "action": x.action, "target": x.target, "details": x.details, "created_at": x.created_at.isoformat()} for x in rows]}

@app.post("/api/v1/payments/initiate")
def initiate_payment(payload:PaymentIntentRequest,user:User=Depends(current_user)):
    if not any(x["id"]==payload.service_id for x in SERVICES): raise HTTPException(status_code=404,detail="অজানা সেবা")
    provider=next((x for x in PROVIDERS if x["id"]==payload.provider_id),None)
    if provider is None: raise HTTPException(status_code=404,detail="অজানা পেমেন্ট প্রোভাইডার")
    raise HTTPException(status_code=503,detail={"code":"PROVIDER_API_NOT_CONFIGURED","message":"এই প্রোভাইডারের অনুমোদিত API এখনো সংযুক্ত হয়নি। কোনো টাকা কাটা হয়নি।","provider":provider["id"]})
