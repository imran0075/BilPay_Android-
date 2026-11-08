#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ALIAS="bilpayrelease"
KEYSTORE="$HOME/bilpay-release.jks"
B64FILE="$HOME/bilpay-release.jks.base64"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool পাওয়া যায়নি। আগে চালান: pkg update && pkg install openjdk-17"
  exit 1
fi

if [[ -e "$KEYSTORE" || -e "$B64FILE" ]]; then
  echo "আগের bilpay-release.jks বা base64 ফাইল পাওয়া গেছে। নিরাপত্তার জন্য overwrite করা হচ্ছে না।"
  echo "আগে ব্যাকআপ/নাম পরিবর্তন করুন, তারপর আবার চালান।"
  exit 1
fi

echo "BilPay স্থায়ী Release Key তৈরি হবে। পাসওয়ার্ড নিরাপদে লিখে রাখুন।"
read -rsp "নতুন keystore password: " STORE_PASS; echo
read -rsp "আবার লিখুন: " STORE_CONFIRM; echo
if [[ "$STORE_PASS" != "$STORE_CONFIRM" || ${#STORE_PASS} -lt 8 ]]; then
  echo "পাসওয়ার্ড মিলেনি অথবা ৮ অক্ষরের কম।"
  exit 1
fi

keytool -genkeypair -v \
  -keystore "$KEYSTORE" -storetype JKS \
  -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
  -alias "$ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
  -dname "CN=BilPay Release, OU=BilPay, O=BilPay Technologies, L=Raipura, ST=Narsingdi, C=BD"

keytool -list -v -keystore "$KEYSTORE" -storetype JKS -storepass "$STORE_PASS" -alias "$ALIAS" >/dev/null
base64 -w 0 "$KEYSTORE" > "$B64FILE"
chmod 600 "$KEYSTORE" "$B64FILE"

echo
echo "Release Key তৈরি ও যাচাই হয়েছে।"
echo "Keystore: $KEYSTORE"
echo "Base64:   $B64FILE"
echo "Alias:    $ALIAS"
echo
echo "এখন GitHub Repository > Settings > Secrets and variables > Actions-এ এই ৪টি Repository Secret যোগ করুন:"
echo "BILPAY_KEYSTORE_BASE64 = $B64FILE ফাইলের সম্পূর্ণ কনটেন্ট"
echo "BILPAY_STORE_PASSWORD  = আপনি যে পাসওয়ার্ড দিয়েছেন"
echo "BILPAY_KEY_ALIAS       = $ALIAS"
echo "BILPAY_KEY_PASSWORD    = একই পাসওয়ার্ড"
echo
echo "Base64 clipboard-এ কপি করার চেষ্টা করছি..."
if command -v termux-clipboard-set >/dev/null 2>&1; then
  termux-clipboard-set < "$B64FILE"
  echo "Base64 clipboard-এ কপি হয়েছে।"
else
  echo "কপি করতে চালান: termux-clipboard-set < $B64FILE"
fi
echo "গুরুত্বপূর্ণ: দুই ফাইল নিরাপদে অন্য জায়গায় ব্যাকআপ রাখুন। এগুলো কাউকে পাঠাবেন না।"
