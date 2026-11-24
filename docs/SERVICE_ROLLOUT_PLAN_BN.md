# BilPay সেবা চালুর ধাপসমূহ

এই নথি BilPay Android, ওয়েব ও API-এর একই rollout checklist। বর্তমান backend branch: `feat/sandbox-internal-wallet`।

## বর্তমানে চালু (পরীক্ষামূলক)

- অ্যাকাউন্ট নিবন্ধন, লগইন, প্রোফাইল ও পাসওয়ার্ড পরিবর্তন।
- Sandbox Wallet, admin-only test credit, internal Send Money এবং transaction history।
- Admin overview, user list, service visibility ও audit log।
- Sandbox লেনদেনের ক্ষেত্রে `real_money=false` এবং `redeemable=false` বাধ্যতামূলক।

## পরবর্তী বাস্তবায়নের ক্রম

1. **Service catalogue:** বিদ্যুৎ, পানি, গ্যাস, ইন্টারনেট, টিভি, টেলিফোন, শিক্ষা, হাসপাতাল, সরকারি ফি ও মোবাইল রিচার্জের তালিকা/ফর্ম।
2. **Mobile recharge adapter:** অপারেটর/aggregator-এর merchant approval, API spec, sandbox credentials, callback/webhook এবং live credentials।
3. **Bill payment adapters:** প্রতিটি biller বা অনুমোদিত aggregator-এর পৃথক API, bill inquiry, amount validation, payment, status inquiry ও reversal/refund flow।
4. **Cash In / Cash Out:** অনুমোদিত bank/PSP/MFS partner, customer KYC, transaction limits, fees, signed callbacks এবং reconciliation।
5. **BilPay-to-BilPay / Fund Transfer:** production ledger, double-entry posting, idempotency, limits, fraud monitoring, dispute handling ও reconciliation।
6. **NPSB / bank / MFS interoperability:** সংশ্লিষ্ট প্রতিষ্ঠানের লিখিত চুক্তি, certification ও production credentials পাওয়ার পর।
7. **Production release:** security review, penetration test, webhook signature tests, duplicate/replay tests, failed/timeout settlement tests, backup/restore drill এবং controlled pilot।

## Fail-closed নীতি

- কোনো provider credential না থাকলে payment initiation অবশ্যই unavailable থাকবে; UI-তে "শিগগির আসছে/সংযোগ অপেক্ষমাণ" দেখাতে হবে।
- কোনো mock/sandbox response-কে সফল বাস্তব পেমেন্ট, customer balance বা withdrawable fund হিসেবে দেখানো যাবে না।
- API timeout হলে অনুমান করে success দেখানো যাবে না; provider status inquiry/reconciliation না হওয়া পর্যন্ত pending থাকবে।
- Live ledger, Cash In/Out, recharge, bill pay ও external transfer আলাদা feature flags-এ বন্ধ থাকবে যতক্ষণ না সংশ্লিষ্ট partner integration ও compliance approval যাচাই হয়।
- API secret, signing key, OTP, password বা database URL কখনো source code, APK বা GitHub-এ রাখা যাবে না; server-side secret store-এ রাখতে হবে।

## বর্তমান API অবস্থা

- `GET /api/v1/services`: service catalogue; `enabled=false` মানে বাস্তব payment route চালু নয়।
- `GET /api/v1/providers`: bKash, Nagad, Rocket ও Upay এখন `awaiting_merchant_api`।
- `POST /api/v1/payments/initiate`: provider API না থাকলে HTTP 503 `PROVIDER_API_NOT_CONFIGURED` ফেরত দেয়; কোনো টাকা কাটা হয় না।
- `GET /api/v1/sandbox/status`, `/api/v1/sandbox/wallet`, `/api/v1/sandbox/transactions` এবং `POST /api/v1/sandbox/transfers/internal`: পরীক্ষামূলক flow।

প্রতিটি partner-এর official API specification ও credentials হাতে এলে সংশ্লিষ্ট adapter আলাদাভাবে implement, test এবং deploy করতে হবে।