import os
from contextlib import asynccontextmanager
from datetime import datetime, timezone, timedelta
from decimal import Decimal
from typing import Literal, Optional
from uuid import uuid4

import jwt
from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, ConfigDict, Field
from pwdlib import PasswordHash
from sqlalchemy import create_engine, select, String, Integer, Numeric, DateTime, ForeignKey
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
    balance: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=Decimal("50000.00"))
    currency: Mapped[str] = mapped_column(String(3), default="BDT")
    mode: Mapped[str] = mapped_column(String(20), default="DEMO_ONLY")
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

@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(bind=engine)
    yield

app = FastAPI(title="BilPay API", description="Shared API foundation for BilPay Android, web and admin clients.", version="0.3.0", lifespan=lifespan)
origins = os.getenv("BILPAY_ALLOWED_ORIGINS", "https://bilpay-official.onrender.com,https://bilpay-official-site.onrender.com,https://bilpay.com.bd,https://www.bilpay.com.bd")
app.add_middleware(CORSMiddleware, allow_origins=[x.strip() for x in origins.split(",") if x.strip()], allow_credentials=False, allow_methods=["GET","POST"], allow_headers=["Content-Type","Authorization"])
TEST_MODE_ENABLED = os.getenv("BILPAY_TEST_MODE", "false").strip().lower() in {"1","true","yes","on"}

SERVICES = [
 {"id":"electricity","name":"বিদ্যুৎ বিল","category":"bill","enabled":False},
 {"id":"water","name":"পানি বিল","category":"bill","enabled":False},
 {"id":"gas","name":"গ্যাস বিল","category":"bill","enabled":False},
 {"id":"internet","name":"ইন্টারনেট বিল","category":"bill","enabled":False},
 {"id":"mobile_recharge","name":"মোবাইল রিচার্জ","category":"bill","enabled":False},
 {"id":"education","name":"শিক্ষা ফি","category":"bill","enabled":False},
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
class TestPaymentRequest(BaseModel):
    service_id: str = Field(min_length=2, max_length=40)
    provider_id: str = Field(min_length=2, max_length=30)
    amount: Decimal = Field(gt=0, le=Decimal("500000"), max_digits=12, decimal_places=2)
    outcome: Literal["success","failed"] = "success"
    currency: Literal["BDT"] = "BDT"
class PaymentIntentRequest(BaseModel):
    service_id: str = Field(min_length=2, max_length=40)
    provider_id: str = Field(min_length=2, max_length=30)
    amount: Decimal = Field(gt=0, le=Decimal("500000"), max_digits=12, decimal_places=2)
    customer_reference: str = Field(min_length=3, max_length=80)
    customer_phone: str = Field(pattern=r"^01[3-9][0-9]{8}$")
    currency: Literal["BDT"] = "BDT"

@app.get("/")
def root():
    return {"name":"BilPay API","version":app.version,"status":"ready"}
@app.get("/health")
def health():
    return {"ok":True,"service":"bilpay-api"}
@app.get("/api/v1/status")
def status():
    return {"name":"BilPay","api_version":app.version,"status":"operational","test_mode_enabled":TEST_MODE_ENABLED,"live_transactions_enabled":False,"database_enabled":True,"provider_integrations":{p["id"]:p["status"] for p in PROVIDERS},"message":"বাস্তব লেনদেন চালুর আগে অনুমোদিত পার্টনার API সংযোগ প্রয়োজন।"}
@app.get("/api/v1/services")
def services():
    return {"items":SERVICES}
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

@app.get("/api/v1/wallet")
def get_wallet(user:User=Depends(current_user),db:Session=Depends(get_db)):
    wallet=db.scalar(select(Wallet).where(Wallet.user_id==user.id))
    if wallet is None:
        wallet=Wallet(user_id=user.id,balance=Decimal("50000.00"),currency="BDT",mode="DEMO_ONLY")
        db.add(wallet)
        try:
            db.commit()
            db.refresh(wallet)
        except Exception:
            db.rollback()
            wallet=db.scalar(select(Wallet).where(Wallet.user_id==user.id))
            if wallet is None:
                raise HTTPException(status_code=503,detail="ডেমো ওয়ালেট তৈরি করা যায়নি।")
    return {"wallet_id":wallet.id,"balance":str(wallet.balance),"currency":wallet.currency,"mode":wallet.mode,"real_money":False,"message":"এটি সার্ভারে সংরক্ষিত পরীক্ষামূলক ব্যালেন্স; বাস্তব অর্থ নয়।"}

@app.get("/api/v1/transactions")
def history(user:User=Depends(current_user),db:Session=Depends(get_db),limit:int=20):
    limit=max(1,min(limit,100))
    rows=db.scalars(select(Transaction).where(Transaction.user_id==user.id).order_by(Transaction.created_at.desc()).limit(limit)).all()
    return {"items":[{"transaction_id":r.transaction_id,"service_id":r.service_id,"provider_id":r.provider_id,"amount":str(r.amount),"currency":r.currency,"status":r.status,"mode":r.mode,"created_at":r.created_at.isoformat()} for r in rows]}

@app.post("/api/v1/payments/initiate")
def initiate_payment(payload:PaymentIntentRequest):
    if not any(x["id"]==payload.service_id for x in SERVICES): raise HTTPException(status_code=404,detail="অজানা সেবা")
    provider=next((x for x in PROVIDERS if x["id"]==payload.provider_id),None)
    if provider is None: raise HTTPException(status_code=404,detail="অজানা পেমেন্ট প্রোভাইডার")
    raise HTTPException(status_code=503,detail={"code":"PROVIDER_API_NOT_CONFIGURED","message":"এই প্রোভাইডারের অনুমোদিত API এখনো সংযুক্ত হয়নি। কোনো টাকা কাটা হয়নি।","provider":provider["id"]})

@app.post("/api/v1/test-payments/simulate")
def simulate(payload:TestPaymentRequest,user:User=Depends(current_user),db:Session=Depends(get_db)):
    if not TEST_MODE_ENABLED: raise HTTPException(status_code=404,detail="টেস্ট পেমেন্ট মোড বর্তমানে বন্ধ আছে।")
    service=next((x for x in SERVICES if x["id"]==payload.service_id),None)
    provider=next((x for x in PROVIDERS if x["id"]==payload.provider_id),None)
    if service is None: raise HTTPException(status_code=404,detail="অজানা সেবা")
    if provider is None: raise HTTPException(status_code=404,detail="অজানা পেমেন্ট প্রোভাইডার")
    txn_id=f"BILPAY-TEST-{uuid4().hex[:12].upper()}"
    row=Transaction(transaction_id=txn_id,user_id=user.id,service_id=service["id"],provider_id=provider["id"],amount=payload.amount,currency=payload.currency,status=payload.outcome,mode="TEST_ONLY")
    db.add(row); db.commit()
    return {"mode":"TEST_ONLY","transaction_id":txn_id,"status":row.status,"service_id":service["id"],"provider_id":provider["id"],"amount":str(payload.amount),"currency":payload.currency,"real_money_used":False,"wallet_balance_changed":False,"message":"এটি পরীক্ষামূলক রেকর্ড; কোনো বাস্তব টাকা লেনদেন হয়নি।"}
