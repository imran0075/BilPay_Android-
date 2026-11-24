# BPay — BRAC Bank API Integration Preparation (বাংলা)

## অবস্থা
- BRAC Bank-এর সঙ্গে এখন পর্যন্ত প্রাথমিক আলোচনা হয়েছে; আনুষ্ঠানিক চুক্তি, API access বা production approval পাওয়া যায়নি।
- এই নথি প্রস্তুতিমূলক। এটি কোনো ব্যাংকিং সংযোগ সক্রিয় করে না।
- BPay-এর বর্তমান Sandbox balance কাল্পনিক; বাস্তব টাকা নয়।
- Live ledger, provider payment এবং bank transfer fail-closed থাকবে যতক্ষণ না লিখিত অনুমোদন, চুক্তি, API credentials, compliance sign-off ও UAT সম্পন্ন হয়।

## BRAC Bank-এর সংশ্লিষ্ট দলের কাছে অনুরোধ
1. BPay-এর business model অনুযায়ী উপযুক্ত partnership route এবং প্রয়োজনীয় licence/authorization-এর তালিকা।
2. Available API product catalogue: account validation, balance enquiry (যদি অনুমোদিত), fund transfer, payment collection, bill payment, transaction status ও reversal।
3. API documentation, sandbox base URL, test credentials issuance process এবং test data।
4. Authentication model: mTLS, OAuth2/client credentials, signed requests, IP allow-list, key rotation এবং certificate lifecycle।
5. Request/response schemas, error codes, timeout policy, rate limits ও maintenance windows।
6. Idempotency requirements, duplicate transaction handling, status enquiry, reversal/refund এবং dispute process।
7. Settlement model, prefunding/escrow requirements (if applicable), settlement cycles, fees, taxes এবং reconciliation file/report formats।
8. Webhook/callback signing, retry schedule, replay protection ও delivery guarantees।
9. Production onboarding checklist, security assessment, penetration testing, UAT sign-off এবং go-live/change-management process।
10. Support escalation contacts, incident response SLA এবং operational reporting requirements।

## প্রস্তাবিত integration workflow
1. BRAC Bank ও BPay business/compliance teams scope এবং legal eligibility নিশ্চিত করবে।
2. Bank-issued sandbox docs ও credentials নিরাপদ secret manager-এ সংরক্ষণ করা হবে; Git repository, chat বা client app-এ নয়।
3. Backend-এ আলাদা bank adapter তৈরি হবে; Android app কখনো সরাসরি bank API বা secret ব্যবহার করবে না।
4. প্রতিটি payment/transfer request-এ server-side authentication, schema validation, idempotency key, immutable transaction reference এবং audit trail থাকবে।
5. Timeout হলে নতুন করে debit/credit চেষ্টা না করে আগে status enquiry করা হবে।
6. Callback/webhook-এর signature, timestamp, event ID এবং replay protection যাচাই করে event process হবে।
7. Daily settlement ও transaction-level reconciliation, exception queue এবং maker-checker approval থাকবে।
8. Bank-এর লিখিত অনুমোদন, regulatory clearance (যেখানে প্রযোজ্য), security review এবং successful UAT ছাড়া production credentials বা live switch ব্যবহার করা হবে না।

## আবশ্যিক নিরাপত্তা ও হিসাবরক্ষণ নীতি
- Double-entry ledger: প্রতিটি journal-এ debit total = credit total; posted entry edit/delete নয়, correction হবে reversal entry দিয়ে।
- Transaction state machine: CREATED → PENDING → SUCCESS/FAILED/REVERSED; provider confirmation ছাড়া SUCCESS নয়।
- Idempotency: একই client request পুনরায় এলে একই transaction result ফেরত দিতে হবে; দ্বিতীয়বার অর্থ movement নয়।
- Amount handling: BDT-তে decimal/পয়সা-নির্ভুল হিসাব; floating point নয়।
- Secrets: server-side environment/secret manager; log-এ token, password, account credentials বা full sensitive data নয়।
- Admin control: maker-checker, least privilege, MFA, audit log এবং সীমিত operational access।
- Customer protection: consent, KYC/AML/sanctions screening (প্রযোজ্য নিয়ম অনুযায়ী), complaint/dispute ও refund process।
- Observability: correlation ID, redacted logs, alerting, reconciliation exception monitoring ও incident runbook।

## BPay-এর বর্তমান বাস্তবায়ন সীমা
- Account registration/login আছে; production-grade OTP/KYC সম্পন্ন বলে গণ্য নয়।
- Sandbox internal transfer কেবল পরীক্ষার জন্য।
- Live wallet endpoint বন্ধ/ fail-closed।
- Payment initiation provider API configured না থাকলে 503 দিয়ে প্রত্যাখ্যান করে।
- কোনো bKash/Nagad/Rocket/Upay বা BRAC Bank live integration সক্রিয় নয়।

## API contract পাওয়ার পর পরবর্তী engineering deliverables
- Bank API adapter interface এবং environment-separated configuration।
- Request signing/mTLS client এবং certificate rotation procedure।
- Idempotent transaction orchestration + status enquiry/reversal handling।
- Verified webhook endpoint + replay-safe event inbox।
- Double-entry journal schema, immutable postings ও reconciliation reports।
- Contract tests against bank sandbox, failure/timeout/duplicate tests এবং UAT evidence।
- Production deployment checklist with explicit approvals; no automatic live enablement.
