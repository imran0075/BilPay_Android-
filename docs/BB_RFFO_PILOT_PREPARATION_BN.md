# BPay Technology — Bangladesh Bank RFFO Pilot প্রস্তুতি

**উদ্দেশ্য:** BPay-এর proposed fintech model সম্পর্কে Bangladesh Bank-এর Regulatory FinTech Facilitation Office (RFFO)-এর লিখিত guidance চাওয়া এবং উপযুক্ত হলে supervised pilot-এর যোগ্যতা/প্রক্রিয়া জানা। এটি PSP licence application নয়, কোনো অনুমোদনের দাবি নয়, এবং কোনো live-money pilot চালুর অনুমতিও নয়।

## 1. প্রাথমিক যোগাযোগ

Bangladesh Bank-এর Payment Systems পৃষ্ঠায় RFFO-কে fintech pilot ও regulatory assistance-এর যোগাযোগপথ হিসেবে উল্লেখ করা হয়েছে।

- Email: rffo.psd@bb.org.bd
- Department: Payment Systems Department, Bangladesh Bank
- Official information: https://www.bb.org.bd/en/index.php/financialsystems/paysystems

**প্রস্তাবিত প্রথম পদক্ষেপ:** প্রথমে লিখিতভাবে জেনে নিতে হবে, একক-মালিকানার ট্রেড লাইসেন্সধারী BPay Technology কোন কাঠামোয় RFFO-তে fintech proposal দিতে পারবে, এবং customer funds না ধরে licensed PSP/bank-এর অধীনে technology/distribution partner হিসেবে pilot করা যায় কি না।

## 2. BPay-এর প্রস্তাবিত মডেল (আলোচনার জন্য)

- Brand: BilPay
- Business/trade name: BPay Technology (BPay Technology)
- Current legal form: sole proprietorship (এটি PSP licence eligibility পূরণ করে—এমন দাবি করা হচ্ছে না)
- Product: Android app + web service + admin console
- Proposed services: customer onboarding, bill-payment/recharge service discovery, partner-authorized payment initiation, transaction status and receipts
- Future wallet proposal: stored-value wallet, internal BilPay-to-BilPay transfer free to customer, and external transfers through approved bank/PSP/NPSB rails
- Proposed internal transfer fee: ৳0; sender's debit equals recipient's credit
- NPSB fee policy target: PSP category ceiling 0.20% (VAT-inclusive) only where that exact NPSB PSP route and classification apply; not a universal fee for Add Money, cash-out or all payouts
- Current safety status: no live wallet balance, no real-money transfer, no cash-in/out or provider payout is enabled; provider APIs/production credentials are not connected

## 3. RFFO-কে যে প্রশ্নগুলো করতে হবে

1. BPay Technology-এর current sole-proprietor form-এ RFFO fintech pilot proposal জমা দেওয়ার সুযোগ আছে কি?
2. Customer fund custody না করে licensed PSP/bank-এর অধীনে technology service provider বা distribution partner হিসেবে pilot করা যায় কি?
3. যদি incorporated company, minimum paid-up capital, board/key-person criteria বা অন্য eligibility লাগে, বর্তমান applicable requirements কী?
4. Proposed stored-value wallet ও internal P2P feature কি PSP licence scope-এর মধ্যে পড়ে, নাকি অন্য কোনো permitted structure আছে?
5. bKash/Nagad/Rocket/Upay Add Money ও payout integration-এর জন্য পৃথক provider approval/contract কীভাবে নিতে হবে?
6. NPSB-তে PSP partner model-এ BilPay-এর role, participant sponsorship, settlement, fee disclosure ও limits কীভাবে নির্ধারিত হবে?
7. Pilot-এর জন্য কোন application form, technical documents, security assessment, customer-protection controls ও reporting লাগবে?

## 4. প্রাথমিক তথ্য-ঘর (এখনো পূরণ/যাচাই বাকি)

- Legal/trade name and trade licence copy
- Trade licence number, issuing authority, validity
- e-TIN and business bank account documents (if applicable)
- Proprietor identity and business contact details
- Product overview, screenshots and app version
- Proposed service scope and excluded services
- Customer journey and transaction flow diagrams
- Fund-flow diagram identifying the licensed bank/PSP and settlement account owner
- Clear statement of who legally holds customer funds
- Proposed KYC/AML/CFT, fraud monitoring and complaint handling
- Data protection, cybersecurity, access control and incident response
- Business model, fee disclosure and partner revenue share
- 3–5 year operating and financial projections (assumptions clearly stated; do not invent numbers)
- Business continuity, disaster recovery and reconciliation plan
- Partner letters/MoUs, if any (none should be claimed until signed)

## 5. Technical readiness evidence

- Backend status endpoint confirms wallet/live transactions are disabled.
- Wallet endpoint returns a safe unavailable response instead of a demo balance.
- Simulated payment endpoint has been removed.
- Provider integrations remain unavailable until written provider approval and production credentials.
- Planned ledger design: double-entry immutable journal, idempotency, atomic posting, reconciliation, reversal entries and maker-checker controls.
- Before any pilot: independent security review, sandbox/UAT evidence, operational runbooks, backup/restore test and written go-live approval.

## 6. Draft email to RFFO

**Subject:** Request for regulatory guidance on BPay fintech proposal and supervised pilot eligibility

Dear Sir/Madam,

We are developing BilPay under the business name BPay Technology in Bangladesh. BilPay is planned as a digital financial-services application with an Android app, web interface and administrative console.

We would like to understand the appropriate regulatory structure before offering any payment or stored-value service. In particular, we seek guidance on whether BilPay may participate in an RFFO-supported fintech pilot as a technology/distribution partner under a Bangladesh Bank-licensed bank or PSP, without independently holding customer funds or operating an unlicensed payment service.

Our proposed long-term roadmap includes partner-authorized bill payments and mobile recharge, a stored-value wallet, free customer-to-customer transfers within BilPay, and external transfers through approved bank/PSP/NPSB rails. These services are not currently live: wallet balances, cash-in/out, real-money transfers and provider payouts remain disabled.

Could you please advise:
1. Whether our current sole-proprietorship business structure may submit an RFFO proposal;
2. The applicable licensing/partner requirements for the proposed model;
3. The required documents, application format and process for a supervised pilot; and
4. The appropriate contact point for a preliminary consultation.

We can provide our business documents, product overview, system architecture, transaction/fund-flow diagrams, risk controls and implementation plan as requested.

Thank you for your guidance.

Sincerely,

BPay Technology
Bangladesh

---

**Important:** This draft has not been sent. Do not attach passwords, OTPs, API keys, signing keys, customer personal data or production secrets. Send only verified business documents through an official, secure channel after checking the recipient and attachment requirements.

## 7. Official references

- Bangladesh Bank — Payment Systems / RFFO information: https://www.bb.org.bd/en/index.php/financialsystems/paysystems
- Bangladesh Bank — Approval Procedure of PSO/PSP (published guideline): https://www.bb.org.bd/aboutus/regulationguideline/psd/pso_psp_03022019.pdf
- Bangladesh Bank circular index (verify current circulars before applying): https://www.bb.org.bd/en/index.php/mediaroom/circular
