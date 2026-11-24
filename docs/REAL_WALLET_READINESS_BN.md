# BPay বাস্তব ওয়ালেট প্রস্তুতি পরিকল্পনা

এই নথি প্রস্তুতির checklist; এটি কোনো লাইসেন্স, চুক্তি, API অনুমোদন বা live payment activation নয়।

## 1. প্রস্তাবিত চার্জ নীতি

| সেবা | BPay গ্রাহক ফি | শর্ত |
|---|---:|---|
| BPay → BPay Send Money | ৳0 | অনুমোদিত wallet/ledger, KYC ও risk limits |
| NPSB-তে PSP হিসেবে BPay → অন্য অংশগ্রহণকারী | সর্বোচ্চ 0.20% (VAT-সহ) | কেবল প্রযোজ্য NPSB PSP transfer; BPay-এর PSP status, participant route ও লিখিত tariff নিশ্চিত হলে |
| bKash → BPay Add Money | এখনো নির্ধারিত নয় | bKash/partner-এর লিখিত funding permission ও tariff |
| BPay → bKash | এখনো নির্ধারিত নয় | অনুমোদিত payout বা NPSB route; route অনুযায়ী fee-cap |
| Cash Out | এখনো নির্ধারিত নয় | অনুমোদিত channel, agent/partner agreement ও প্রকাশিত tariff |
| BPay → Bank | গ্রাহকের জন্য ৳0 (লক্ষ্য) | partner cost, settlement fee ও subsidy budget নিশ্চিত |

**NPSB 0.20% সব ধরনের লেনদেনের সাধারণ ফি নয়।** এটি কেবল Bangladesh Bank-এর নির্ধারিত শ্রেণিতে BPay যদি PSP হিসেবে NPSB interoperable transfer করে, সেই নির্দিষ্ট রুটের সর্বোচ্চ (VAT-সহ) চার্জ। bKash Add Money, bKash payout, Cash Out বা অন্য payment gateway-এর চার্জ আলাদা চুক্তি/রুট/নিয়মে নির্ধারিত হবে। এসব ক্ষেত্রে এখনই 0.20% বা ৳3/৳1,000 নিশ্চিত করা হচ্ছে না। Internal BPay-to-BPay Send Money গ্রাহকের জন্য সম্পূর্ণ ফ্রি থাকবে—প্রেরক ৳500 পাঠালে প্রাপক ৳500 পাবেন; কোনো শতাংশ বা নির্দিষ্ট ট্রান্সফার ফি নয়। কোনো সেবাকে unlimited বলা যাবে না; প্রযোজ্য account-type, KYC, risk ও regulatory limits থাকবে।

Bangladesh Bank-এর PSD Circular No. 12 অনুযায়ী NPSB interoperable fund transfer-এ সর্বোচ্চ (VAT-সহ) Bank 0.15%, PSP 0.20%, MFS 0.85%; sender pays, receiver pays না এবং account-type limits বহাল। BPay-এর regulatory classification ও actual rail লিখিতভাবে নিশ্চিত না করে কোনো NPSB tariff production-এ প্রয়োগ করা যাবে না।

## 2. Regulatory / commercial blocking gates

- [ ] **প্রথমে operating model বাছাই:** BPay নিজে লাইসেন্স নেবে, নাকি licensed PSP/bank-এর অধীনে অনুমোদিত technology/distribution partner হবে—Bangladesh Bank ও আইনজীবীর লিখিত মতামত নিতে হবে।
- [ ] **নিজস্ব PSP licence-এর eligibility যাচাই:** Bangladesh Bank-এর প্রকাশিত PSP approval procedure-এ applicant-কে Companies Act অনুযায়ী incorporated company হতে হবে; প্রকাশিত guideline-এ ন্যূনতম paid-up capital ৳20 কোটি, সংশ্লিষ্ট key personnel-এর কমপক্ষে ৩ বছরের অভিজ্ঞতা এবং বাংলাদেশে technology infrastructure উল্লেখ আছে। ২০২৪ সালের Payment and Settlement Systems Act ও পরবর্তী নির্দেশনার আলোকে এই শর্তের বর্তমান প্রয়োগ Bangladesh Bank-এর কাছ থেকে লিখিতভাবে পুনরায় নিশ্চিত করতে হবে। বর্তমান sole-proprietor trade licence-কে PSP licence-এর বিকল্প ধরা যাবে না।
- [ ] **Partner-first বিকল্প:** licensed PSP/bank-এর সঙ্গে MoU/contract, customer-fund custody and settlement structure, responsibility matrix, service scope, fee sharing, dispute/reversal, data processing ও audit rights লিখিতভাবে স্থির করা।
- [ ] Sponsor/settlement bank এবং licensed PSP partner-এর চুক্তি।
- [ ] bKash merchant/payment gateway এবং আলাদা payout/disbursement access-এর লিখিত অনুমোদন। সাধারণ merchant checkout approval wallet funding/payout permission নয়।
- [ ] NPSB participant/partner route, supported destinations, fees, limits, settlement and dispute rules লিখিতভাবে নিশ্চিত।
- [ ] KYC/AML/CFT, sanctions/PEP screening, suspicious activity escalation/reporting procedures।
- [ ] Customer terms, privacy notice, fee disclosure, refund/reversal, complaints and service-level policy legal review।
- [ ] Security assessment, incident response, business continuity and disaster recovery sign-off।

Payment and Settlement Systems Act, 2024-এর অধীনে বাংলাদেশ ব্যাংকের লাইসেন্স ছাড়া payment service বা payment system পরিচালনা করা যাবে না। PSP-এর অনুমোদিত scope-এর মধ্যে e-money/payment instrument থাকলে তবেই তা দেওয়া যাবে; published BB PSP procedure-এ Trust Cum Settlement Account-এর কথাও আছে। Customer funds operating account-এ রাখা যাবে না; regulator/partner-approved trust/settlement arrangement লাগবে।

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
- POST /api/v1/transfers/internal — BPay-to-BPay; idempotency key; fee = 0.
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
