# BilPay API Backend

এটি BilPay Android, ওয়েবসাইট ও অ্যাডমিন প্যানেলের জন্য শেয়ার্ড API-এর প্রাথমিক ভিত্তি।

## লোকালি চালানো
```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## API
- `GET /` — API পরিচিতি
- `GET /health` — health check
- `GET /api/v1/services` — বিল সেবার তালিকা
- `GET /api/v1/providers` — পেমেন্ট প্রোভাইডারের সংযোগ অবস্থা
- `POST /api/v1/payments/initiate` — পেমেন্ট শুরু করার নিরাপদ placeholder

Swagger docs: `/docs`

## Render-এ ডেপ্লয়
GitHub repo `imran0075/BilPay_Android-` থেকে নতুন Web Service তৈরি করুন:
- Root Directory: `backend`
- Runtime: Python 3
- Build Command: `pip install -r requirements.txt`
- Start Command: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`

প্রয়োজনে `BILPAY_ALLOWED_ORIGINS` environment variable-এ অনুমোদিত ওয়েবসাইটের origin কমা দিয়ে দিন।

## নিরাপত্তা ও বাস্তব লেনদেন
এই সংস্করণ কোনো টাকা নেয় না, ব্যালেন্স রাখে না, এবং কোনো লেনদেন সম্পন্ন হয়েছে বলে দাবি করে না। পেমেন্ট endpoint প্রোভাইডার API অনুপস্থিত থাকলে fail-closed ভাবে 503 দেয়। বাস্তব সংযোগের আগে সংশ্লিষ্ট প্রতিষ্ঠানের লিখিত অনুমোদন, merchant account, official API documentation, server-side secret storage, webhook signature verification, idempotency, database ledger, reconciliation, KYC/AML ও প্রয়োজনীয় নিয়ন্ত্রক অনুমোদন লাগবে।

কোনো API secret, password, OTP বা private key GitHub-এ commit করবেন না। এগুলো কেবল server-এর protected environment/secrets-এ রাখতে হবে।
