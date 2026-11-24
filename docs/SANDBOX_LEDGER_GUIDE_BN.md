# BilPay Sandbox Wallet — ব্যবহার ও নিরাপত্তা

## অবস্থা
এটি শুধু non-redeemable পরীক্ষামূলক ledger। এখানে কোনো বাস্তব টাকা জমা, কাটা, উত্তোলন বা provider settlement হয় না। Production wallet এবং live transaction বন্ধই থাকবে।

## Feature flag
- `BILPAY_SANDBOX_LEDGER_ENABLED=false` (default)
- Staging-এ আলাদা environment-এ পরীক্ষা করার সময়ই true করা যাবে।
- `BILPAY_LIVE_LEDGER_ENABLED=false` অপরিবর্তিত রাখতে হবে।
- Sandbox flag production-এ চালু করা যাবে না; staging database ও production database আলাদা হতে হবে।

## পরীক্ষামূলক API
- `GET /api/v1/sandbox/status` — sandbox flag এবং real-money safety status।
- `GET /api/v1/sandbox/wallet` — লগইন করা ব্যবহারকারীর sandbox balance।
- `POST /api/v1/admin/sandbox/grant` — allowlisted admin সর্বোচ্চ ৳10,000 পর্যন্ত non-redeemable test credit দিতে পারবেন; audit log তৈরি হয়।
- `POST /api/v1/sandbox/transfers/internal` — BilPay user-to-user transfer; fee ৳0; sender debit ও receiver credit সমান।
- `GET /api/v1/sandbox/transactions` — sandbox transfer history।
- Transfer request-এ unique `Idempotency-Key` header বাধ্যতামূলক; একই key ও একই payload retry করলে একই transaction ফেরত আসে।

## সীমাবদ্ধতা
- Sandbox credit কেবল পরীক্ষার জন্য; এর কোনো cash value নেই এবং redeem/withdraw করা যায় না।
- Sandbox data live wallet, live transaction history বা admin live transaction totals-এ দেখানো হয় না।
- bKash/Nagad/Rocket/Upay, NPSB, bank transfer, bill pay বা recharge সংযুক্ত নয়।
- Real wallet চালু করার অনুমতি/পদ্ধতি এই feature দেয় না।
- Deployment-এর আগে CI tests, separate staging DB, secrets review এবং environment isolation যাচাই করতে হবে।
