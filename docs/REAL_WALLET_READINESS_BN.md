# BilPay বাস্তব ওয়ালেট প্রস্তুতি পরিকল্পনা

এই নথি প্রস্তুতির checklist; এটি কোনো লাইসেন্স, চুক্তি, API অনুমোদন বা live payment activation নয়।

## 1. প্রস্তাবিত চার্জ নীতি

| সেবা | BilPay গ্রাহক ফি | শর্ত |
|---|---:|---|
| BilPay → BilPay Send Money | ৳0 | অনুমোদিত wallet/ledger ও risk limits |
| bKash → BilPay Add Money | ৳3 / ৳1,000 (প্রস্তাব) | bKash-এর লিখিত অনুমোদন ও fee-cap যাচাই |
| BilPay → bKash | ৳3 / ৳1,000 (প্রস্তাব) | approved payout/interoperability route |
| Cash Out | ৳3 / ৳1,000 (প্রস্তাব) | approved agent/bank/PSP channel |
| BilPay → Bank | গ্রাহকের জন্য ৳0 (লক্ষ্য) | partner cost ও subsidy budget নিশ্চিত |

৳3/৳1,000 = 0.30%। Bangladesh Bank-এর PSD Circular No. 12 অনুযায়ী NPSB interoperable fund transfer-এ সর্বোচ্চ (VAT-সহ) Bank 0.15%, PSP 0.20%, MFS 0.85%; sender pays, receiver pays না এবং account-type limits বহাল। তাই BilPay-এর regulatory classification ও rail নিশ্চিত না করে 0.30% fee production-এ প্রয়োগ করা যাবে না। কোনো সেবাকে unlimited বলা যাবে না।

## 2. Regulatory / commercial blocking gates

- [ ] Bangladesh Bank-এর সঙ্গে লিখিতভাবে নির্ধারণ: BilPay নিজে PSP/e-money issuer হবে, নাকি licensed PSP/bank-এর technology/distribution partner হবে।
- [ ] BilPay-এর বর্তমান sole-proprietor trade licence দিয়ে model অনুমোদনযোগ্য কি না যাচাই; PSP licence-এর জন্য company incorporation ও eligibility/document requirements লাগতে পারে।
- [ ] Sponsor/settlement bank এবং licensed PSP partner-এর চুক্তি।
- [ ] bKash merchant/payment gateway এবং আলাদা payout/disbursement access-এর লিখিত অনুমোদন। সাধারণ merchant checkout approval wallet funding/payout permission নয়।
- [ ] NPSB participant/partner route, supported destinations, fees, limits, settlement and dispute rules লিখিতভাবে নিশ্চিত।
- [ ] KYC/AML/CFT, sanctions/PEP screening, suspicious activity escalation/reporting procedures।
- [ ] Customer terms, privacy notice, fee disclosure, refund/reversal, complaints and service-level policy legal review।
- [ ] Security assessment, incident response, business continuity and disaster recovery sign-off।

Bangladesh Bank-এর MFS Regulations 2022 bank/FI/Government-led model এবং PSP licensing-এর কথা বলে। Bangladesh Bank guidance অনুযায়ী PSP wallet/e-money service দিতে পারে এবং trust-cum-settlement arrangement লাগে। লাইসেন্স ছাড়া payment service বা payment system পরিচালনা করা যাবে না।

## 3. Ledger architecture

- Double-entry immutable journal; প্রতিটি posting-এ মোট debit = মোট credit।
- BDT Decimal arithmetic; floating-point নয়।
- Wallet balance posted journal entries থেকে derived/verified; client-supplied balance কখনো গ্রহণ নয়।
- Unique transaction reference + idempotency key; retry হলে দ্বিতীয়বার debit/credit নয়।
- Atomic DB transaction/row locking; concurrent transfer-এ overdraft প্রতিরোধ।
- Pending → processing → posted / failed / reversed lifecycle; verified partner result ছাড়া external payment posted নয়।
- Provider webhook signature verification, replay protection, amount/currency/reference matching।
- Partner settlement report বনাম ledger reconciliation; unmatched queue ও maker-checker resolution।
- Refund/reversal হবে compensating journal entry দিয়ে; পুরনো journal edit/delete নয়।
- Versioned fee rules; internal Send Money fee = 0; confirmation-এ amount, fee, total debit ও recipient credit স্পষ্ট।

## 4. Proposed API (approval না পাওয়া পর্যন্ত disabled)

- GET /api/v1/wallet — verified available/ledger balance.
- POST /api/v1/transfers/internal — BilPay-to-BilPay; idempotency key; fee = 0.
- POST /api/v1/funding/intents — Add Money intent; verified provider success ছাড়া credit নয়।
- POST /api/v1/payouts — approved MFS/bank withdrawal.
- GET /api/v1/transactions/{id} — owner-only status.
- POST /api/v1/webhooks/{provider} — authenticated, replay-safe callback.
- Admin ledger/reconciliation views; sensitive actions require maker-checker and immutable audit.

## 5. Security / operations

- [ ] Transaction PIN/step-up authentication, device/session controls, rate/velocity limits.
- [ ] OTP only through approved channel; never log OTP, PIN, passwords or secrets.
- [ ] Secrets only in Render protected environment; never commit to Git or Android APK.
- [ ] TLS, least-privilege DB/service accounts, encrypted backups and restore test.
- [ ] Admin role separation, maker-checker, immutable audit.
- [ ] Customer notification for every state change; receipt with transaction ID.
- [ ] Support, complaint/dispute SLA and incident escalation plan.
- [ ] Duplicate callback, timeout, reversal, outage, concurrency and reconciliation tests.

## 6. Existing system guardrails

- Backend currently keeps wallet/live transactions disabled and returns 503 for wallet access until a real ledger is approved/configured.
- Keep BILPAY_LIVE_LEDGER_ENABLED=false until all blocking gates and production tests are signed off.
- No demo balances, fake successful transactions, manual unbacked credits, or production use of test credentials.
- Customer funds must not be held in the operating account; use only regulator/partner-approved trust/settlement structure.

## 7. Go-live acceptance

1. Written regulatory/legal approval and signed bank/PSP/provider agreements.
2. Production credentials issued by each partner and stored server-side.
3. Ledger invariants, concurrency and idempotency tests pass.
4. Sandbox/UAT, reconciliation, refunds/reversals and dispute tests pass.
5. Security and operations review signed off.
6. Controlled pilot within approved limits, with daily monitoring.
7. Formal go-live sign-off by business, compliance, finance and technology owners.

**This document does not authorize live-money activation.**
