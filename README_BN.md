# BilPay Android

BilPay-এর প্রাথমিক Android demo project।

## APK তৈরি
1. GitHub-এর Actions ট্যাব খুলুন।
2. **Build BilPay Android APK** workflow নির্বাচন করুন।
3. Run workflow চাপুন (push হলেই স্বয়ংক্রিয়ভাবেও build হবে)।
4. সফল হলে workflow run-এর Artifacts থেকে **BilPay-Android-APK** ডাউনলোড করুন।
5. ZIP extract করে APK ইনস্টল করুন।

## গুরুত্বপূর্ণ
- বর্তমান সংস্করণটি UI demo; কোনো বাস্তব টাকা জমা, বিল পরিশোধ, Send Money, Cash Out বা ব্যাংকিং লেনদেন করে না।
- ব্যালেন্সটি ডেমো।
- বাস্তব সেবা চালুর জন্য সংশ্লিষ্ট অনুমোদিত আর্থিক/বিল পেমেন্ট পার্টনারের API, চুক্তি, লাইসেন্স/নিয়ন্ত্রক অনুমোদন, নিরাপদ backend, KYC/AML, ledger ও reconciliation ব্যবস্থা প্রয়োজন।
- API secret কখনো Android অ্যাপে বা GitHub source code-এ রাখবেন না; server-side secret storage ব্যবহার করতে হবে।


## BilPay API Backend
- API backend-এর ফাইল `backend/` ফোল্ডারে আছে। Render-এ নতুন Web Service তৈরি করে Root Directory `backend`, Build Command `pip install -r requirements.txt`, Start Command `uvicorn app.main:app --host 0.0.0.0 --port $PORT` দিন। বিস্তারিত `backend/README_BN.md`-এ।
- API সার্ভার deploy হলে GitHub repository Settings → Secrets and variables → Actions → Variables-এ `BILPAY_API_BASE_URL` নামে variable তৈরি করে Render-এর HTTPS URL দিন (শেষে `/` দেবেন না)। এরপর নতুন APK build হলে অ্যাপ `/health` endpoint পরীক্ষা করে API status দেখাবে।
- বর্তমানে API ভিত্তি প্রস্তুত; কোনো live payment provider যুক্ত হয়নি।