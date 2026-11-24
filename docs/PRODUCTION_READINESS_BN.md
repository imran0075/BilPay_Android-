# BPay Production Readiness — বাস্তব সেবা চালুর পরিকল্পনা

**প্রতিষ্ঠানের নাম:** BPay Technology  
**অ্যাপ:** BPay Android — `com.bilpay.mobile`  
**বর্তমান অবস্থা:** প্রি-লঞ্চ / Sandbox  
**নীতি:** লিখিত অনুমোদন, চুক্তি, UAT ও reconciliation sign-off ছাড়া কোনো Live money movement চালু করা যাবে না।

## 1. বর্তমান যাচাইকৃত অবস্থা

| ক্ষেত্র | অবস্থা |
|---|---|
| Android login / account | বিদ্যমান; production identity verification এখনো অসম্পূর্ণ |
| Sandbox wallet / internal transfer | পরীক্ষামূলক; বাস্তব অর্থ নয় |
| Live wallet | বন্ধ |
| Live payment initiation | provider API না থাকায় fail-closed (503) |
| bKash / Nagad / Rocket / Upay | official production API credentials/merchant agreement সংযুক্ত নয় |
| KYC / AML / sanctions / transaction monitoring | production-ready নয় |
| Customer support, complaints and dispute operations | launch-এর আগে operational policy ও staffing প্রয়োজন |
| Settlement / reconciliation | live partner settlement flow এখনো নেই |

## 2. বাস্তবায়নের ক্রম

### Phase A — Compliance and partner onboarding (external dependency)
- BPay Technology-এর ব্যবসায়িক কাগজপত্র, মালিকানা, trade licence, TIN/BIN (যেখানে প্রযোজ্য), bank account ও business profile প্রস্তুত রাখা।
- বাংলাদেশে প্রযোজ্য regulatory category, licence/approval এবং permissible activities বাংলাদেশ ব্যাংক/যোগ্য আইনজীবীর কাছ থেকে লিখিতভাবে নিশ্চিত করা।
- অনুমোদিত ব্যাংক, PSP/PSO বা payment partner-এর সঙ্গে লিখিত agreement ও merchant onboarding সম্পন্ন করা।
- প্রতিটি partner থেকে official API documentation, sandbox credentials, production credentials, webhook signing specification, settlement schedule, fee schedule, refund/chargeback rules ও support escalation contact সংগ্রহ করা।
- কোনো partner-এর অনুমতি ছাড়া bKash/Nagad/Rocket/Upay-এর নাম ব্যবহার করে live payment চালু বা credentials অনুমান করা যাবে না।

### Phase B — Production ledger
- Sandbox tables থেকে আলাদা LIVE ledger/accounting domain রাখতে হবে।
- Double-entry journal: প্রতিটি posted transaction-এ debit ও credit সমান; journal entry append-only হবে।
- Atomic database transaction, row locking, unique idempotency key, immutable transaction reference এবং duplicate request protection।
- Pending → succeeded/failed/reversed state machine; provider confirmation ছাড়া transaction সফল দেখানো যাবে না।
- Balance হবে ledger entries থেকে নির্ণীত/verified; client request বা admin form দিয়ে সরাসরি balance edit নয়।
- Refund, reversal, fees, partner payable/receivable, settlement batch ও reconciliation report আলাদা ledger accounts-এ হিসাব করতে হবে।
- Daily partner statement reconciliation এবং mismatch alert বাধ্যতামূলক।

### Phase C — Identity, security and operations
- Phone ownership verification via approved OTP/SMS provider; OTP expiry, attempt limits, resend cooldown ও anti-enumeration।
- KYC risk tiers এবং প্রয়োজনীয় পরিচয় যাচাই; applicable AML/CFT screening, limits, suspicious activity escalation এবং record retention policy।
- Login/OTP/payment rate limits, device/session controls, token rotation/revocation, MFA for administrators।
- Admin maker-checker approval for sensitive actions; audit logs tamper-resistant হতে হবে।
- Secrets শুধু Render protected environment/secrets-এ; GitHub, APK, logs বা client app-এ কখনো নয়।
- TLS, least-privilege database credentials, encrypted backups, restore drills, incident response এবং access review।
- Customer support, complaint tracking, refund SLA ও dispute escalation চালু করতে হবে।

### Phase D — Partner adapters and UAT
প্রতিটি অনুমোদিত partner-এর জন্য আলাদা server-side adapter:
1. Create payment / collect request
2. Verify transaction status directly with partner
3. Verify webhook signature and timestamp; reject replay/invalid signatures
4. Idempotent webhook processing and durable event storage
5. Reconciliation against partner settlement statement
6. Refund/reversal only where partner contract and API support it

UAT-এ success, decline, timeout, duplicate callback, delayed callback, reversal, partial outage, amount mismatch, replay attack ও reconciliation mismatch পরীক্ষা করতে হবে। Partner-এর লিখিত UAT acceptance ছাড়া production key activate করা যাবে না।

## 3. Non-negotiable production gates

- [ ] Regulatory/legal review completed in writing
- [ ] Partner agreement and merchant approval completed
- [ ] Official API docs and production credentials received securely
- [ ] Separate production database/credentials and backups configured
- [ ] Double-entry ledger and reconciliation tests pass
- [ ] OTP/KYC/AML controls reviewed and operational
- [ ] Webhook signature/replay/idempotency tests pass
- [ ] Admin MFA and maker-checker enabled
- [ ] Security review and incident response drill completed
- [ ] Partner UAT sign-off and controlled pilot approval received

## 4. Existing API safety rules to preserve

- Keep `BILPAY_LIVE_LEDGER_ENABLED=false` until all production gates above are signed off.
- Keep sandbox and live data, credentials, database access and transaction identifiers separated.
- `/api/v1/payments/initiate` must continue returning a fail-closed error for any provider without a configured, approved adapter.
- A sandbox balance, sandbox transfer, simulated payment, or successful Android test is never evidence of real funds or partner settlement.
- Do not expose provider secrets in the Android application.
- Do not enable withdrawals, cash-out, bank transfer or stored-value functionality until the relevant partner and regulatory permissions explicitly cover them.

## 5. Information required to implement a real provider adapter

For each partner, obtain through its official onboarding channel:
- Legal contracting entity and approved merchant/product scope
- API documentation and sandbox base URL
- Secure credential delivery process and key rotation requirements
- Request/response signing or authentication rules
- Webhook endpoint requirements, signature headers and retry policy
- Transaction query/status API
- Settlement statement format and settlement calendar
- Refund/reversal/chargeback process
- UAT test cases and production go-live approval contact

**Never paste passwords, OTPs, private keys, API secrets or database URLs into chat or GitHub.** Use Render protected environment variables after the partner has issued credentials.

---
এই নথি একটি engineering launch plan; এটি regulatory approval, licence, legal opinion, partner contract বা production certification নয়।
