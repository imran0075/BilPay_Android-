package com.bilpay.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.text.method.PasswordTransformationMethod
import org.json.JSONObject
import android.os.Bundle
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.zxing.integration.android.IntentIntegrator
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private var homeBalance: String? = null
    private var homeBalanceVisible = false
    private var homeBalanceLoading = false
    private var homeBalanceHideRunnable: Runnable? = null
    private var homeBalanceOwnerToken: String? = null
    private var homeBalanceText: TextView? = null
    private val green = Color.rgb(0, 112, 78)
    private val dark = Color.rgb(20, 39, 34)
    private val muted = Color.rgb(104, 122, 115)
    private val white = Color.WHITE
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply { text = value; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD) }

    private fun action(label: String) {
        val lower = label.lowercase()
        when {
            lower == "সেবা" || lower == "আরও সেবা" -> showServices()
            lower == "প্রোফাইল" -> showProfile()
            lower == "পরীক্ষামূলক ওয়ালেট" -> showSandboxWallet()
            lower == "সাপোর্ট" || lower == "সহায়তা" -> showSupport()
            lower == "হোম" -> showInfo("BPay", "আপনি BPay-এর হোম স্ক্রিনে আছেন।")
            lower == "হিসাব" || lower == "লেনদেন" -> showHistory()
            lower.contains("বিদ্যুৎ") || lower.contains("পানি") || lower.contains("গ্যাস") ||
                lower.contains("ইন্টারনেট") || lower.contains("টিভি") || lower.contains("টেলিফোন") ||
                lower.contains("শিক্ষা") || lower.contains("হাসপাতাল") || lower.contains("সরকারি") ||
                lower.contains("অন্যান্য") || lower.contains("রিচার্জ") -> showBillForm(label)
            lower.contains("সেন্ড মানি") -> openSandboxTransferForm(false)
            lower.contains("ফান্ড ট্রান্সফার") -> openSandboxTransferForm(true)
            lower == "qr" || lower.contains("qr") -> startQrScanner()
            lower.contains("অ্যাড মানি") || lower.contains("ক্যাশ") ||
                lower.contains("bkash") || lower.contains("nagad") || lower.contains("rocket") || lower.contains("upay") ->
                showInfo("সেবা চালুর অপেক্ষায়", "Cash In, Cash Out ও বাহ্যিক ট্রান্সফার এখনো চালু নয়। অনুমোদিত আর্থিক অংশীদার API ও চুক্তি যুক্ত হলে বাস্তব সেবা চালু হবে।")
            else -> showInfo("সেবা প্রস্তুত হচ্ছে", "এই সুবিধাটি বাস্তবভাবে চালুর জন্য প্রয়োজনীয় সার্ভার বা সেবাদাতা সংযোগ এখনো সম্পন্ন হয়নি।")
        }
    }


    private fun startQrScanner() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.CAMERA), 4102
            )
            return
        }
        launchQrCameraScanner()
    }

    private fun launchQrCameraScanner() {
        try {
            IntentIntegrator(this).apply {
                setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                setPrompt("BilPay QR কোডটি ক্যামেরার সামনে ধরুন")
                setBeepEnabled(false)
                setOrientationLocked(true)
                setBarcodeImageEnabled(false)
            }.initiateScan()
        } catch (e: Exception) {
            showInfo("QR স্ক্যানার চালু হয়নি", "ক্যামেরার অনুমতি দিন এবং আবার QR অপশনে চাপ দিন।")
        }
    }

    @Deprecated("Deprecated in Android, retained for runtime camera permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4102) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launchQrCameraScanner()
            } else {
                showInfo("ক্যামেরার অনুমতি প্রয়োজন", "QR স্ক্যান করতে BilPay-কে ক্যামেরা ব্যবহারের অনুমতি দিন।")
            }
        }
    }

    @Deprecated("Deprecated in Android, retained for the ZXing scanner result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents.isNullOrBlank()) {
                showInfo("QR স্ক্যান বাতিল", "আপনি স্ক্যান বন্ধ করেছেন। চাইলে আবার QR বাটনে চাপ দিন।")
            } else {
                val payload = result.contents.trim()
                android.app.AlertDialog.Builder(this)
                    .setTitle("QR কোড পাওয়া গেছে")
                    .setMessage(payload + "\\n\\nনিরাপত্তা: QR-এর তথ্য যাচাই না করে কাউকে টাকা পাঠাবেন না। এই স্ক্যানটি এখন শুধু QR তথ্য পড়বে; বাস্তব পেমেন্টের জন্য অনুমোদিত মার্চেন্ট/পেমেন্ট API সংযোগ প্রয়োজন।")
                    .setPositiveButton("ঠিক আছে", null)
                    .setNeutralButton("কপি করুন") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("BilPay QR", payload))
                        showInfo("কপি হয়েছে", "QR-এর তথ্য ক্লিপবোর্ডে কপি করা হয়েছে।")
                    }
                    .show()
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showInfo(title: String, message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("বুঝেছি", null)
            .show()
    }

    private fun showServices() {
        val progress = android.app.ProgressDialog(this).apply {
            setMessage("সার্ভিস তালিকা আনা হচ্ছে…")
            setCancelable(false)
            show()
        }
        Thread {
            val items = mutableListOf<String>()
            var error = ""
            try {
                val connection = (URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/services").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (code !in 200..299) {
                    error = "সার্ভার উত্তর দিয়েছে (HTTP $code)।"
                } else {
                    val catalogue = JSONObject(body).optJSONArray("items")
                    if (catalogue == null) {
                        error = "সার্ভিস তালিকার তথ্য সঠিক নয়।"
                    } else {
                        for (index in 0 until catalogue.length()) {
                            val item = catalogue.optJSONObject(index) ?: continue
                            if (item.optBoolean("visible", true)) {
                                val name = item.optString("name").trim()
                                if (name.isNotEmpty()) items.add(name)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                error = "সার্ভিস তালিকা আনা যায়নি: " + (e.localizedMessage ?: "সংযোগ সমস্যা")
            }
            runOnUiThread {
                progress.dismiss()
                if (error.isNotEmpty()) {
                    showInfo("সার্ভিস লোড হয়নি", error + "\nইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।")
                    return@runOnUiThread
                }
                // Only show services explicitly made visible in the server catalogue.
                // Do not append hard-coded wallet/transfer providers: visibility must be
                // controlled centrally and unavailable financial actions must not look live.
                if (items.isEmpty()) {
                    showInfo("সার্ভিস তালিকা", "অ্যাডমিন বর্তমানে কোনো সেবা প্রদর্শনের জন্য চালু রাখেননি।")
                    return@runOnUiThread
                }
                val labels = items.distinct().toTypedArray()
                android.app.AlertDialog.Builder(this)
                    .setTitle("BPay • সকল সেবা")
                    .setItems(labels) { _, which -> action(labels[which]) }
                    .setNegativeButton("বন্ধ", null)
                    .show()
            }
        }.start()
    }

    private fun securePrefs() = EncryptedSharedPreferences.create(
        this,
        "bilpay_secure",
        MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun showProfile() {
        val prefs = securePrefs()
        val token = prefs.getString("access_token", null)
        if (token.isNullOrBlank()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("BPay অ্যাকাউন্ট")
                .setItems(arrayOf("লগইন", "নতুন অ্যাকাউন্ট তৈরি")) { _, which -> showAuthForm(which == 1) }
                .setNegativeButton("বন্ধ", null).show()
            return
        }
        val progress = android.app.ProgressDialog(this).apply {
            setMessage("সার্ভার থেকে প্রোফাইল আনা হচ্ছে…")
            setCancelable(false)
            show()
        }
        Thread {
            var title = "BPay অ্যাকাউন্ট"
            var message = "প্রোফাইল তথ্য পাওয়া যায়নি।"
            var profileName = "নাম দেওয়া হয়নি"
            var profilePhone = ""
            var sessionExpired = false
            try {
                val connection = (URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/auth/me").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (code in 200..299) {
                    val user = JSONObject(body)
                    profileName = user.optString("full_name").ifBlank { "নাম দেওয়া হয়নি" }
                    profilePhone = user.optString("phone")
                    message = "নাম: $profileName\\nমোবাইল: $profilePhone\\nঅ্যাকাউন্ট ID: " + user.optInt("id") + "\\n\\nতথ্য সরাসরি BPay সার্ভার থেকে এসেছে।\\nওয়ালেট ও আর্থিক লেনদেনের সুবিধা বাস্তব লেজার ও অনুমোদিত অংশীদার API যুক্ত হওয়ার পর চালু হবে."
                } else if (code == 401) {
                    sessionExpired = true
                    title = "আবার লগইন করুন"
                    message = "আপনার লগইন সেশনের মেয়াদ শেষ হয়েছে। আবার লগইন করুন।"
                    prefs.edit().clear().apply()
                } else {
                    message = "সার্ভার উত্তর দিয়েছে (HTTP $code)। কিছুক্ষণ পর আবার চেষ্টা করুন।"
                }
            } catch (e: Exception) {
                message = "সার্ভারের সঙ্গে যোগাযোগ করা যায়নি: " + (e.localizedMessage ?: "অজানা সমস্যা")
            }
            runOnUiThread {
                progress.dismiss()
                if (sessionExpired) {
                    resetHomeBalance()
                    showInfo(title, message)
                    showAuthForm(false)
                } else {
                    android.app.AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(message)
                        .setNeutralButton("নিরাপত্তা ও প্রোফাইল") { _, _ -> showAccountSecurity(token, profileName, profilePhone) }
                        .setNegativeButton("বন্ধ", null)
                        .setPositiveButton("লগআউট") { _, _ ->
                            prefs.edit().clear().apply()
                            resetHomeBalance()
                            recreate()
                            showInfo("লগআউট হয়েছে", "এই ফোন থেকে আপনার BPay অ্যাকাউন্ট বিচ্ছিন্ন করা হয়েছে।")
                        }.show()
                }
            }
        }.start()
    }

    private fun showAccountSecurity(token: String, currentName: String, phone: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("প্রোফাইল ও নিরাপত্তা")
            .setItems(arrayOf("নাম পরিবর্তন", "পাসওয়ার্ড পরিবর্তন")) { _, which ->
                if (which == 0) showEditProfile(token, currentName)
                else showChangePassword(token)
            }
            .setNegativeButton("বন্ধ", null)
            .show()
    }

    private fun showEditProfile(token: String, currentName: String) {
        val field = EditText(this).apply {
            hint = "আপনার পূর্ণ নাম"
            setText(currentName.takeUnless { it == "নাম দেওয়া হয়নি" } ?: "")
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("নাম পরিবর্তন")
            .setView(wrap)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("সংরক্ষণ", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = field.text.toString().trim()
                        if (name.isBlank() || name.length > 80) {
                            showInfo("তথ্য যাচাই", "১ থেকে ৮০ অক্ষরের মধ্যে আপনার নাম লিখুন।")
                            return@setOnClickListener
                        }
                        sendAccountRequest(token, "/api/v1/auth/profile", "PUT",
                            JSONObject().put("full_name", name), "প্রোফাইলের নাম পরিবর্তন হয়েছে।")
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun showChangePassword(token: String) {
        val current = EditText(this).apply {
            hint = "বর্তমান পাসওয়ার্ড"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        val next = EditText(this).apply {
            hint = "নতুন পাসওয়ার্ড (কমপক্ষে ১০ অক্ষর)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        val confirm = EditText(this).apply {
            hint = "নতুন পাসওয়ার্ড আবার লিখুন"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(current); addView(next); addView(confirm)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("পাসওয়ার্ড পরিবর্তন")
            .setMessage("নতুন পাসওয়ার্ড কমপক্ষে ১০ অক্ষরের হতে হবে।")
            .setView(wrap)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("পরিবর্তন", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val oldPass = current.text.toString()
                        val newPass = next.text.toString()
                        if (oldPass.isBlank() || newPass.length < 10 || newPass != confirm.text.toString()) {
                            showInfo("তথ্য যাচাই", "বর্তমান পাসওয়ার্ড দিন, নতুন পাসওয়ার্ড কমপক্ষে ১০ অক্ষরের রাখুন এবং দুইবার একইভাবে লিখুন।")
                            return@setOnClickListener
                        }
                        sendAccountRequest(token, "/api/v1/auth/change-password", "POST",
                            JSONObject().put("current_password", oldPass).put("new_password", newPass),
                            "পাসওয়ার্ড পরিবর্তন হয়েছে। নিরাপত্তার জন্য আবার লগইন করুন।", clearSession = true)
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun sendAccountRequest(token: String, path: String, method: String, payload: JSONObject, successMessage: String, clearSession: Boolean = false) {
        val progress = android.app.ProgressDialog(this).apply {
            setMessage("তথ্য নিরাপদে সংরক্ষণ হচ্ছে…")
            setCancelable(false)
            show()
        }
        Thread {
            var message = "সার্ভারে সংযোগ করা যায়নি।"
            var success = false
            try {
                val connection = (URL(BuildConfig.API_BASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (code in 200..299) {
                    success = true
                    message = successMessage
                } else {
                    val detail = JSONObject(body).opt("detail")
                    message = if (detail is String) detail else "অনুরোধ সম্পন্ন হয়নি (HTTP $code)।"
                }
            } catch (e: Exception) {
                message = "সমস্যা হয়েছে: " + (e.localizedMessage ?: "অজানা ত্রুটি")
            }
            runOnUiThread {
                progress.dismiss()
                if (success && clearSession) {
                    securePrefs().edit().clear().apply()
                    resetHomeBalance()
                    recreate()
                    showInfo("সফল", message)
                    showAuthForm(false)
                } else {
                    if (success && path == "/api/v1/auth/profile") recreate()
                    showInfo(if (success) "সফল" else "সমস্যা", message)
                }
            }
        }.start()
    }

    private fun normalizeBdPhone(raw: String): String {
        val ascii = buildString {
            raw.forEach { ch ->
                when {
                    ch in '\u09E6'..'\u09EF' -> append(('0'.code + (ch.code - '\u09E6'.code)).toChar())
                    ch in '\u0660'..'\u0669' -> append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                    ch.isDigit() -> append(ch)
                }
            }
        }
        return when {
            ascii.startsWith("00880") && ascii.length == 15 -> "0" + ascii.substring(5)
            ascii.startsWith("880") && ascii.length == 13 -> "0" + ascii.substring(3)
            ascii.length == 10 && ascii.startsWith("1") -> "0" + ascii
            else -> ascii
        }
    }

    private fun showAuthForm(register: Boolean) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val fullName = EditText(this).apply { hint = "আপনার নাম (ঐচ্ছিক)" }
        val phone = EditText(this).apply { hint = "মোবাইল নম্বর (01XXXXXXXXX)"; inputType = android.text.InputType.TYPE_CLASS_PHONE }
        val password = EditText(this).apply {
            hint = if (register) "পাসওয়ার্ড (কমপক্ষে ১০ অক্ষর)" else "পাসওয়ার্ড"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        if (register) form.addView(fullName)
        form.addView(phone); form.addView(password)
        android.app.AlertDialog.Builder(this)
            .setTitle(if (register) "নতুন BPay অ্যাকাউন্ট" else "BPay লগইন")
            .setMessage("নিজের মোবাইল নম্বর ব্যবহার করুন। পাসওয়ার্ড কাউকে দেবেন না।")
            .setView(form)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton(if (register) "অ্যাকাউন্ট তৈরি" else "লগইন") { _, _ ->
                val mobile = normalizeBdPhone(phone.text.toString())
                val pass = password.text.toString()
                val nameValue = fullName.text.toString().trim()
                val invalidPhone = !mobile.matches(Regex("^01[3-9][0-9]{8}$"))
                val shortRegistrationPassword = register && pass.length < 10
                if (invalidPhone || shortRegistrationPassword || pass.isBlank()) {
                    val validationMessage = when {
                        invalidPhone -> "সঠিক বাংলাদেশি ১১ সংখ্যার মোবাইল নম্বর দিন। উদাহরণ: 01XXXXXXXXX অথবা +8801XXXXXXXXX"
                        shortRegistrationPassword -> "নতুন অ্যাকাউন্টের পাসওয়ার্ড কমপক্ষে ১০ অক্ষরের হতে হবে।"
                        else -> "পাসওয়ার্ড লিখুন।"
                    }
                    showInfo("তথ্য যাচাই করুন", validationMessage)
                } else {
                    val progress = android.app.ProgressDialog(this).apply { setMessage("অ্যাকাউন্ট যাচাই হচ্ছে…"); setCancelable(false); show() }
                    Thread {
                        var message = "সার্ভারে সংযোগ করা যায়নি।"
                        var ok = false
                        try {
                            val endpoint = if (register) "/api/v1/auth/register" else "/api/v1/auth/login"
                            val connection = (URL(BuildConfig.API_BASE_URL.trimEnd('/') + endpoint).openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"; connectTimeout = 90000; readTimeout = 90000
                                doOutput = true; setRequestProperty("Content-Type", "application/json")
                            }
                            val body = JSONObject().apply {
                                put("phone", mobile); put("password", pass)
                                if (register) put("full_name", nameValue)
                            }.toString()
                            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                            val code = connection.responseCode
                            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                            connection.disconnect()
                            val json = JSONObject(response)
                            if (code in 200..299 && json.has("access_token")) {
                                securePrefs().edit().putString("access_token", json.getString("access_token"))
                                    .putString("account_phone", mobile).apply()
                                ok = true
                                message = "আপনার অ্যাকাউন্ট সফলভাবে " + if (register) "তৈরি হয়েছে।" else "লগইন হয়েছে।"
                            } else {
                                val detail = json.opt("detail")
                                message = if (detail is String) detail else "অনুরোধটি সম্পন্ন হয়নি (HTTP $code)।"
                            }
                        } catch (e: Exception) { message = "সমস্যা হয়েছে: " + (e.localizedMessage ?: "অজানা ত্রুটি") }
                        runOnUiThread {
                            progress.dismiss()
                            if (ok) {
                                resetHomeBalance()
                                // Rebuild the home header with the newly authenticated account token.
                                recreate()
                            }
                            showInfo(if (ok) "সফল" else "অ্যাকাউন্ট সমস্যা", message)
                        }
                    }.start()
                }
            }.show()
    }

    private fun showSupport() {
        showInfo(
            "BPay সাপোর্ট",
            "ইমেইল: imran16102001bd@gmail.com\nমোবাইল: +8801977714371\n\nPIN, OTP বা পাসওয়ার্ড কাউকে জানাবেন না।"
        )
    }

    private fun showBillForm(service: String) {
        showInfo("সেবা এখনো চালু নয়", "$service-এর অনুমোদিত বিলার API ও পেমেন্ট যাচাই সংযোগ সম্পন্ন হলে এই সেবা চালু হবে। এখন কোনো বিল জমা বা টাকা কাটা হবে না।")
    }

    private fun sandboxRequest(method: String, path: String, token: String, payload: JSONObject? = null, idempotencyKey: String? = null): Pair<Int, String> {
        val connection = (URL(BuildConfig.API_BASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (!idempotencyKey.isNullOrBlank()) setRequestProperty("Idempotency-Key", idempotencyKey)
        }
        if (payload != null) connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return code to body
    }

    private fun showSandboxWallet() {
        val token = securePrefs().getString("access_token", null)
        if (token.isNullOrBlank()) {
            showInfo("লগইন প্রয়োজন", "Sandbox Wallet ব্যবহার করতে আগে BPay অ্যাকাউন্টে লগইন করুন।")
            showAuthForm(false)
            return
        }

        // Keep the wallet interaction in one clear, branded dialog while the server is checked.
        val loadingPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            addView(ProgressBar(this@MainActivity).apply { isIndeterminate = true })
            addView(text("নিরাপদে Sandbox Wallet যাচাই হচ্ছে", 14f, dark, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(6))
            })
            addView(text("সার্ভারের নিশ্চিত ব্যালেন্স না পাওয়া পর্যন্ত কোনো অঙ্ক দেখানো হবে না।", 11f, muted).apply {
                gravity = Gravity.CENTER
            })
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("BPay Sandbox Wallet")
            .setView(loadingPanel)
            .setPositiveButton("আবার চেষ্টা", null)
            .setNegativeButton("বন্ধ", null)
            .create()
        dialog.setCancelable(false)
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
        }
        dialog.show()

        Thread {
            var balance = ""
            var isAdmin = false
            var error = ""
            try {
                val (statusCode, statusBody) = sandboxRequest("GET", "/api/v1/sandbox/status", token)
                if (statusCode !in 200..299) error = sandboxHttpMessage(statusCode, "Sandbox সার্ভার যাচাই")
                else if (!JSONObject(statusBody).optBoolean("sandbox_enabled", false)) {
                    error = "Sandbox Wallet বর্তমানে চালু নেই। এটি বাস্তব ওয়ালেট নয়।"
                } else {
                    val (profileCode, profileBody) = sandboxRequest("GET", "/api/v1/auth/me", token)
                    if (profileCode !in 200..299) error = sandboxHttpMessage(profileCode, "অ্যাকাউন্ট যাচাই")
                    else {
                        isAdmin = JSONObject(profileBody).optBoolean("is_admin", false)
                        val (walletCode, walletBody) = sandboxRequest("GET", "/api/v1/sandbox/wallet", token)
                        if (walletCode in 200..299) {
                            val wallet = JSONObject(walletBody)
                            balance = wallet.optString("balance", "")
                            if (balance.isBlank()) error = "সার্ভার ব্যালেন্সের তথ্য দেয়নি। আবার চেষ্টা করুন।"
                        } else error = sandboxHttpMessage(walletCode, "Sandbox ব্যালেন্স আনা")
                    }
                }
            } catch (e: Exception) {
                error = "সার্ভারে সংযোগ করা যায়নি। ইন্টারনেট পরীক্ষা করে আবার চেষ্টা করুন।"
            }
            runOnUiThread {
                if (!dialog.isShowing) return@runOnUiThread
                if (error.isNotBlank()) {
                    loadingPanel.removeAllViews()
                    loadingPanel.setPadding(dp(20), dp(18), dp(20), dp(12))
                    loadingPanel.addView(text("⚠  ব্যালেন্স যাচাই করা যায়নি", 15f, dark, true))
                    loadingPanel.addView(text(error, 12f, muted).apply { setPadding(0, dp(8), 0, dp(8)) })
                    loadingPanel.addView(text("কোনো ব্যালেন্স অনুমান করে দেখানো হয়নি।", 10f, muted))
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).visibility = View.VISIBLE
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        dialog.dismiss()
                        showSandboxWallet()
                    }
                } else {
                    dialog.dismiss()
                    renderSandboxWallet(token, balance, isAdmin)
                }
            }
        }.start()
    }

    private fun sandboxHttpMessage(code: Int, action: String): String = when (code) {
        401 -> "লগইন সেশনের মেয়াদ শেষ হয়েছে। আবার লগইন করুন।"
        403 -> "এই অ্যাকাউন্টের Sandbox Wallet ব্যবহারের অনুমতি নেই।"
        404 -> "Sandbox Wallet সেবা পাওয়া যায়নি। পরে আবার চেষ্টা করুন।"
        in 500..599 -> "BPay সার্ভারে সাময়িক সমস্যা হচ্ছে। কিছুক্ষণ পর আবার চেষ্টা করুন।"
        else -> "$action সম্পন্ন হয়নি (HTTP $code)। আবার চেষ্টা করুন।"
    }

    private fun renderSandboxWallet(token: String, balance: String, isAdmin: Boolean) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val balanceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(48, 61, 133), Color.rgb(34, 43, 105))
            ).apply { cornerRadius = dp(18).toFloat() }
        }
        balanceCard.addView(text("পরীক্ষামূলক ব্যালেন্স", 12f, Color.rgb(220, 226, 255), true))
        balanceCard.addView(text("৳ $balance", 29f, Color.WHITE, true).apply {
            setPadding(0, dp(7), 0, dp(2))
        })
        balanceCard.addView(text("সার্ভার থেকে যাচাই করা • Sandbox", 10f, Color.rgb(220, 226, 255)))
        panel.addView(balanceCard)
        panel.addView(text("এটি কাল্পনিক পরীক্ষামূলক অর্থ। বাস্তবে জমা, উত্তোলন বা ব্যবহার করা যাবে না।", 11f, muted).apply {
            setPadding(0, dp(12), 0, dp(2))
        })
        panel.addView(text(if (isAdmin) "Admin অনুমতি: সক্রিয় ✓" else "Admin অনুমতি: সক্রিয় নয়", 12f, muted, true).apply {
            setPadding(0, dp(8), 0, 0)
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, 0) }
        val send = Button(this).apply { text = "Sandbox Send Money"; setOnClickListener { showSandboxTransfer(token) } }
        val history = Button(this).apply { text = "লেনদেনের ইতিহাস"; setOnClickListener { showSandboxHistory(token) } }
        actions.addView(send, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        actions.addView(history, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(actions)
        val refresh = Button(this).apply {
            text = "↻  ব্যালেন্স আবার যাচাই"
            setOnClickListener { showSandboxWallet() }
        }
        panel.addView(refresh, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(8) })
        if (isAdmin) {
            val adminGrant = Button(this).apply {
                text = "Admin: Sandbox ব্যালেন্স যোগ"
                setOnClickListener { requestSandboxAdminGrant(token) }
            }
            panel.addView(adminGrant, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
            panel.addView(text("একবারে সর্বোচ্চ ৳১০,০০,০০০ • শুধু নিজের Admin Sandbox Wallet", 10f, muted).apply { setPadding(0, dp(5), 0, 0) })
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("BPay Sandbox Wallet")
            .setView(panel)
            .setPositiveButton("বন্ধ", null)
            .show()
    }

    private fun requestSandboxAdminGrant(token: String) {
        val amountInput = EditText(this).apply {
            hint = "পরিমাণ (সর্বোচ্চ ১০০০০০০)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("1000000")
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(text("আপনার নিজের Admin Sandbox Wallet-এ কত টাকা যোগ করবেন?", 13f, dark, true))
            addView(amountInput, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(text("সর্বোচ্চ ৳১০,০০,০০০। এটি কাল্পনিক Sandbox ব্যালেন্স; বাস্তব টাকা নয়।", 10f, muted).apply { setPadding(0, dp(8), 0, 0) })
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Admin Sandbox ব্যালেন্স")
            .setView(form)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("ক্রেডিট করুন", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = amountInput.text.toString().trim().toBigDecimalOrNull()
                if (amount == null || amount <= java.math.BigDecimal.ZERO ||
                    amount.scale() > 2 || amount > java.math.BigDecimal("1000000")) {
                    showInfo("পরিমাণ সঠিক নয়", "৳০.০১ থেকে ৳১০,০০,০০০-এর মধ্যে সর্বোচ্চ দুই দশমিকসহ পরিমাণ লিখুন।")
                    return@setOnClickListener
                }
                android.app.AlertDialog.Builder(this)
                    .setTitle("ক্রেডিট নিশ্চিত করুন")
                    .setMessage("আপনার নিজের Admin Sandbox Wallet-এ ৳" + amount.toPlainString() + " কাল্পনিক টেস্ট ব্যালেন্স যোগ করবেন?")
                    .setNegativeButton("না", null)
                    .setPositiveButton("হ্যাঁ, যোগ করুন") { _, _ ->
                        dialog.dismiss()
                        val progress = android.app.ProgressDialog(this).apply { setMessage("Admin অনুমতি যাচাই ও ক্রেডিট হচ্ছে…"); setCancelable(false); show() }
                        Thread {
                            var message = "অনুরোধ সম্পন্ন হয়নি।"
                            var success = false
                            try {
                                val (code, body) = sandboxRequest(
                                    "POST", "/api/v1/admin/sandbox/grant", token,
                                    JSONObject().put("amount", amount.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString())
                                )
                                val response = JSONObject(body)
                                if (code in 200..299) {
                                    success = true
                                    message = "আপনার নিজের Admin Sandbox Wallet-এ ৳" + response.optString("amount", amount.toPlainString()) +
                                        " যোগ হয়েছে। Transaction ID: " + response.optString("transaction_id", "সার্ভারে সংরক্ষিত") +
                                        "\\nএটি বাস্তব টাকা নয় এবং উত্তোলনযোগ্য নয়।"
                                } else if (code == 403) {
                                    message = "এই অ্যাকাউন্টে Admin অনুমতি পাওয়া যায়নি।"
                                } else {
                                    val detail = response.opt("detail")
                                    message = if (detail is String) detail else "সার্ভার উত্তর দিয়েছে (HTTP $code)।"
                                }
                            } catch (e: Exception) {
                                message = "সার্ভারে সংযোগ করা যায়নি: " + (e.localizedMessage ?: "অজানা সমস্যা")
                            }
                            runOnUiThread {
                                progress.dismiss()
                                showInfo(if (success) "Sandbox ব্যালেন্স যোগ হয়েছে" else "অনুরোধ সম্পন্ন হয়নি", message)
                                if (success) {
                            // A transfer changes the server-side balance; discard any cached home amount.
                            resetHomeBalance()
                            toggleHomeBalance()
                            showSandboxWallet()
                        }
                            }
                        }.start()
                    }.show()
            }
        }
        dialog.show()
    }

    private fun openSandboxTransferForm(isFundTransfer: Boolean) {
        if (!BuildConfig.API_BASE_URL.contains("sandbox", ignoreCase = true)) {
            showInfo(
                if (isFundTransfer) "Fund Transfer এখনো চালু নয়" else "Send Money এখনো চালু নয়",
                "বাস্তব আর্থিক লেনদেন চালুর জন্য অনুমোদিত আর্থিক অংশীদার, লাইভ লেজার ও প্রয়োজনীয় নিয়ন্ত্রক অনুমোদন এখনো যুক্ত হয়নি।"
            )
            return
        }
        val token = securePrefs().getString("access_token", null)
        if (token.isNullOrBlank()) {
            showInfo("লগইন প্রয়োজন", "Send Money বা Fund Transfer ব্যবহার করতে আগে আপনার BPay Sandbox অ্যাকাউন্টে লগইন করুন।")
            showAuthForm(false)
            return
        }
        showSandboxTransfer(token, isFundTransfer)
    }

    private fun showSandboxTransfer(token: String, isFundTransfer: Boolean = false) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val phone = EditText(this).apply { hint = "প্রাপকের BPay নম্বর"; inputType = android.text.InputType.TYPE_CLASS_PHONE }
        val amount = EditText(this).apply { hint = "পরিমাণ (৳)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        form.addView(phone); form.addView(amount)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(if (isFundTransfer) "Sandbox Fund Transfer" else "Sandbox Send Money")
            .setMessage(if (isFundTransfer) "BPay অ্যাকাউন্টে ফান্ড ট্রান্সফার • ফি ৳০ • শুধু পরীক্ষামূলক ব্যালেন্স।" else "BPay নম্বরে Send Money • ফি ৳০ • শুধু পরীক্ষামূলক ব্যালেন্স স্থানান্তর হবে।")
            .setView(form)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton(if (isFundTransfer) "ফান্ড ট্রান্সফার করুন" else "টাকা পাঠান", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val recipient = normalizeBdPhone(phone.text.toString())
                val value = amount.text.toString().trim()
                val decimal = value.toBigDecimalOrNull()
                if (!recipient.matches(Regex("^01[3-9][0-9]{8}$")) || decimal == null || decimal <= java.math.BigDecimal.ZERO || decimal.scale() > 2 || decimal > java.math.BigDecimal("10000")) {
                    showInfo("তথ্য যাচাই", "সঠিক ১১ সংখ্যার BPay নম্বর এবং ৳০.০১ থেকে ৳১০,০০০ পর্যন্ত পরিমাণ লিখুন।")
                    return@setOnClickListener
                }
                dialog.dismiss()
                val progress = android.app.ProgressDialog(this).apply { setMessage("পরীক্ষামূলক ট্রান্সফার হচ্ছে…"); setCancelable(false); show() }
                Thread {
                    var message = "অনুরোধ সম্পন্ন হয়নি।"
                    var success = false
                    try {
                        val payload = JSONObject().put("recipient_phone", recipient).put("amount", decimal.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString())
                        val (code, body) = sandboxRequest("POST", "/api/v1/sandbox/transfers/internal", token, payload, (if (isFundTransfer) "FUND_TRANSFER-" else "SEND_MONEY-") + java.util.UUID.randomUUID().toString())
                        val json = JSONObject(body)
                        if (code in 200..299) {
                            success = true
                            message = (if (isFundTransfer) "পরীক্ষামূলক Fund Transfer সফল।\nপ্রাপক পেয়েছেন: ৳" else "পরীক্ষামূলক Send Money সফল।\nপ্রাপক পেয়েছেন: ৳") + json.optString("recipient_credit") + "\nফি: ৳0.00\nবাস্তব টাকা স্থানান্তর হয়নি।"
                        } else {
                            val detail = json.opt("detail")
                            message = if (detail is String) detail else if (detail is JSONObject) detail.optString("message", "HTTP $code") else "অনুরোধ সম্পন্ন হয়নি (HTTP $code)।"
                        }
                    } catch (e: Exception) { message = "সমস্যা হয়েছে: " + (e.localizedMessage ?: "অজানা ত্রুটি") }
                    runOnUiThread {
                        progress.dismiss()
                        showInfo(if (success) "Sandbox ট্রান্সফার সফল" else "ট্রান্সফার হয়নি", message)
                        if (success) showSandboxWallet()
                    }
                }.start()
            }
        }
        dialog.show()
    }

    private fun inboxDateTime(raw: String): String {
        return try {
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = parser.parse(raw.take(19)) ?: return raw
            val formatter = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale("bn", "BD"))
            formatter.timeZone = java.util.TimeZone.getDefault()
            formatter.format(date)
        } catch (_: Exception) { raw.ifBlank { "সময় পাওয়া যায়নি" } }
    }

    private fun showInbox() {
        if (!BuildConfig.API_BASE_URL.contains("sandbox", ignoreCase = true)) {
            showInfo("ইনবক্স", "প্রকৃত লেনদেনের ইনবক্স লাইভ লেজার ও অনুমোদিত পেমেন্ট সংযোগ চালু হলে দেখাবে।")
            return
        }
        val token = securePrefs().getString("access_token", null)
        if (token.isNullOrBlank()) {
            showInfo("ইনবক্স দেখতে লগইন করুন", "লেনদেনের বিবরণ দেখতে আগে BPay অ্যাকাউন্টে লগইন করুন।")
            showAuthForm(false)
            return
        }
        loadInbox(token)
    }

    private fun loadInbox(token: String) {
        val progress = android.app.ProgressDialog(this).apply {
            setMessage("ইনবক্সের লেনদেন আনা হচ্ছে…")
            setCancelable(false)
            show()
        }
        Thread {
            var items = org.json.JSONArray()
            var error = ""
            try {
                val (code, body) = sandboxRequest("GET", "/api/v1/sandbox/transactions?limit=100", token)
                if (code in 200..299) items = JSONObject(body).optJSONArray("items") ?: org.json.JSONArray()
                else error = "লেনদেনের তথ্য পাওয়া যায়নি (HTTP " + code + ")।"
            } catch (e: Exception) {
                error = "সার্ভারে সংযোগ করা যায়নি: " + (e.localizedMessage ?: "অজানা সমস্যা")
            }
            runOnUiThread {
                progress.dismiss()
                if (error.isNotBlank()) showInfo("ইনবক্স লোড হয়নি", error)
                else renderInbox(items, token)
            }
        }.start()
    }

    private fun renderInbox(items: org.json.JSONArray, token: String) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }
        if (items.length() == 0) {
            panel.addView(text("এখনো কোনো লেনদেন নেই", 14f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, dp(28))
            })
        } else {
            val groups = listOf("SEND_MONEY" to "সেন্ড মানি", "FUND_TRANSFER" to "ফান্ড ট্রান্সফার", "OTHER" to "অন্যান্য")
            for ((kind, heading) in groups) {
                val matching = (0 until items.length()).mapNotNull { index ->
                    items.optJSONObject(index)?.takeIf { row ->
                        val actual = row.optString("transaction_type", "SEND_MONEY")
                        if (kind == "OTHER") actual != "SEND_MONEY" && actual != "FUND_TRANSFER" else actual == kind
                    }
                }
                if (matching.isEmpty()) continue
                panel.addView(text(heading, 14f, dark, true).apply { setPadding(0, dp(12), 0, dp(8)) })
                for (row in matching) {
                    val outgoing = row.optString("direction") == "OUT"
                    val phone = if (outgoing) row.optString("recipient_phone") else row.optString("sender_phone")
                    val phoneLabel = if (outgoing) "প্রাপকের নম্বর" else "প্রেরকের নম্বর"
                    val statusRaw = row.optString("status").uppercase()
                    val status = when (statusRaw) {
                        "POSTED", "SUCCESS", "COMPLETED" -> "সফল"
                        "PENDING" -> "অপেক্ষমাণ"
                        "FAILED" -> "ব্যর্থ"
                        else -> row.optString("status").ifBlank { "অজানা" }
                    }
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        background = GradientDrawable().apply {
                            setColor(Color.WHITE)
                            cornerRadius = dp(14).toFloat()
                            setStroke(dp(1), Color.rgb(226, 231, 242))
                        }
                        elevation = dp(1).toFloat()
                    }
                    val top = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    top.addView(text(if (outgoing) "টাকা পাঠানো হয়েছে" else "টাকা এসেছে", 12f, dark, true))
                    top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                    top.addView(text(status, 10f, green, true))
                    card.addView(top)
                    card.addView(text("৳ " + row.optString("amount", "—"), 21f, dark, true).apply {
                        setPadding(0, dp(7), 0, dp(7))
                    })
                    card.addView(text(phoneLabel + ": " + phone.ifBlank { "তথ্য পাওয়া যায়নি" }, 11f, muted))
                    card.addView(text("ট্রানজেকশন আইডি: " + row.optString("transaction_id", "—"), 10f, muted).apply {
                        setPadding(0, dp(7), 0, dp(3))
                    })
                    card.addView(text("তারিখ ও সময়: " + inboxDateTime(row.optString("created_at")), 10f, muted))
                    panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
                }
            }
        }
        val scroll = ScrollView(this).apply { addView(panel); isFillViewport = true }
        android.app.AlertDialog.Builder(this)
            .setTitle("BPay ইনবক্স • লেনদেন")
            .setView(scroll)
            .setPositiveButton("রিফ্রেশ") { _, _ -> loadInbox(token) }
            .setNegativeButton("বন্ধ", null)
            .show()
    }

    private fun showSandboxHistory(token: String) {
        loadInbox(token)
    }

    private fun showHistory() {
        showInbox()
    }

    private fun cancelHomeBalanceAutoHide() {
        homeBalanceHideRunnable?.let { homeBalanceText?.removeCallbacks(it) }
        homeBalanceHideRunnable = null
    }

    private fun scheduleHomeBalanceAutoHide(token: String, valueView: TextView) {
        cancelHomeBalanceAutoHide()
        val hideTask = Runnable {
            if (homeBalanceOwnerToken == token && securePrefs().getString("access_token", null) == token) {
                homeBalanceVisible = false
                valueView.text = "৳  • • • • • •"
            }
            homeBalanceHideRunnable = null
        }
        homeBalanceHideRunnable = hideTask
        valueView.postDelayed(hideTask, 5_000L)
    }

    private fun resetHomeBalance() {
        cancelHomeBalanceAutoHide()
        homeBalance = null
        homeBalanceOwnerToken = null
        homeBalanceVisible = false
        homeBalanceText?.text = "৳  • • • • • •"
    }

    private fun toggleHomeBalance() {
        val valueView = homeBalanceText ?: return
        if (homeBalanceLoading) return
        val token = securePrefs().getString("access_token", null)
        if (token.isNullOrBlank()) {
            resetHomeBalance()
            showInfo("লগইন প্রয়োজন", "ব্যালেন্স দেখতে আগে BPay অ্যাকাউন্টে লগইন করুন।")
            showAuthForm(false)
            return
        }
        // A cached amount belongs to exactly one login token, never to the next account.
        if (homeBalance != null && homeBalanceOwnerToken != token) resetHomeBalance()
        val cached = homeBalance
        if (cached != null) {
            homeBalanceVisible = !homeBalanceVisible
            if (homeBalanceVisible) {
                valueView.text = "৳ $cached"
                scheduleHomeBalanceAutoHide(token, valueView)
            } else {
                cancelHomeBalanceAutoHide()
                valueView.text = "৳  • • • • • •"
            }
            return
        }
        homeBalanceLoading = true
        valueView.text = "যাচাই হচ্ছে…"
        Thread {
            var fetchedBalance = ""
            var error = ""
            try {
                val (statusCode, statusBody) = sandboxRequest("GET", "/api/v1/sandbox/status", token)
                if (statusCode !in 200..299) error = sandboxHttpMessage(statusCode, "Sandbox সার্ভার যাচাই")
                else if (!JSONObject(statusBody).optBoolean("sandbox_enabled", false)) {
                    error = "Sandbox Wallet বর্তমানে চালু নেই।"
                } else {
                    val (profileCode, profileBody) = sandboxRequest("GET", "/api/v1/auth/me", token)
                    if (profileCode !in 200..299) error = sandboxHttpMessage(profileCode, "অ্যাকাউন্ট যাচাই")
                    else {
                        val (walletCode, walletBody) = sandboxRequest("GET", "/api/v1/sandbox/wallet", token)
                        if (walletCode in 200..299) {
                            fetchedBalance = JSONObject(walletBody).optString("balance", "")
                            if (fetchedBalance.isBlank()) error = "সার্ভার ব্যালেন্সের তথ্য দেয়নি।"
                        } else error = sandboxHttpMessage(walletCode, "Sandbox ব্যালেন্স আনা")
                    }
                }
            } catch (_: Exception) {
                error = "সার্ভারে সংযোগ করা যায়নি। ইন্টারনেট পরীক্ষা করে আবার চেষ্টা করুন।"
            }
            runOnUiThread {
                homeBalanceLoading = false
                // Ignore a late response if the user switched accounts while the request was running.
                if (securePrefs().getString("access_token", null) != token) {
                    valueView.text = "৳  • • • • • •"
                    return@runOnUiThread
                }
                if (error.isNotBlank()) {
                    valueView.text = "৳  • • • • • •"
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } else {
                    homeBalance = fetchedBalance
                    homeBalanceOwnerToken = token
                    homeBalanceVisible = true
                    valueView.text = "৳ $fetchedBalance"
                    scheduleHomeBalanceAutoHide(token, valueView)
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pageBg = Color.WHITE
        val red = Color.rgb(205, 0, 18)
        val navy = Color.rgb(190, 0, 16)
        val navy2 = Color.rgb(235, 0, 20)
        val gold = Color.rgb(224, 174, 61)
        val ink = Color.rgb(30, 35, 54)
        val soft = Color.rgb(119, 126, 148)
        window.statusBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.navigationBarColor = Color.WHITE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = false }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(22))
        }

        // BPay header: account identity on the left; notification and brand/menu on the right.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        val accountIdentity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val accountAvatar = TextView(this).apply {
            text = "U"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(red)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(255, 241, 242))
                setStroke(dp(1), red)
            }
            isClickable = true
            isFocusable = true
            contentDescription = "অ্যাকাউন্ট"
            setOnClickListener { showProfile() }
        }
        accountIdentity.addView(accountAvatar, LinearLayout.LayoutParams(dp(42), dp(42)))
        val accountName = text("ইউজার", 13f, ink, true).apply {
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), 0, dp(4), 0)
            isClickable = true
            setOnClickListener { showProfile() }
        }
        accountIdentity.addView(accountName, LinearLayout.LayoutParams(0, dp(42), 1f))

        val headerSpacer = View(this)
        val rightActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val notificationButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_popup_reminder)
            setColorFilter(red)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(238, 220, 220))
            }
            isClickable = true
            isFocusable = true
            contentDescription = "নোটিফিকেশন"
            setOnClickListener { showInfo("নোটিফিকেশন", "নতুন নোটিফিকেশন আপাতত নেই।") }
        }
        rightActions.addView(notificationButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            marginEnd = dp(10)
        })

        // Keep the user avatar, notification bell, and B logo circles equal-sized and compact.
        val brandStack = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
        val brandMark = TextView(this).apply {
            text = "B"
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(red)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(235, 235, 235))
            }
            elevation = dp(2).toFloat()
        }
        brandStack.addView(brandMark, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP or Gravity.START))
        val menuButton = TextView(this).apply {
            text = "☰"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            // Menu glyph stays white with no red circle or colored background.
            background = null
            elevation = 0f
            isClickable = true
            isFocusable = true
            contentDescription = "মেনু"
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("BPay মেনু")
                    .setItems(arrayOf("প্রোফাইল", "ইনবক্স • লেনদেন", "সকল সেবা", "সাপোর্ট")) { _, which ->
                        when (which) {
                            0 -> showProfile()
                            1 -> showInbox()
                            2 -> showServices()
                            else -> showSupport()
                        }
                    }
                    .setNegativeButton("বন্ধ", null)
                    .show()
            }
        }
        brandStack.addView(menuButton, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.BOTTOM or Gravity.END))
        // All three main circles share the same 42dp row and vertical center.
        rightActions.addView(brandStack, LinearLayout.LayoutParams(dp(42), dp(42)))

        header.addView(accountIdentity, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(headerSpacer, LinearLayout.LayoutParams(dp(4), 1))
        header.addView(rightActions, LinearLayout.LayoutParams(-2, dp(42)))
        page.addView(header)

        // Load the active account name for this exact login token.
        val identityPrefs = securePrefs()
        val identityToken = identityPrefs.getString("access_token", null)
        if (!identityToken.isNullOrBlank()) {
            val cachedToken = identityPrefs.getString("account_name_token", null)
            val cachedName = identityPrefs.getString("account_full_name", null).orEmpty()
            if (cachedToken == identityToken && cachedName.isNotBlank()) {
                accountAvatar.text = cachedName.trim().first().uppercaseChar().toString()
                accountAvatar.contentDescription = "অ্যাকাউন্ট: $cachedName"
                accountName.text = cachedName
            }
            Thread {
                try {
                    val connection = (URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/auth/me").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 15000
                        setRequestProperty("Authorization", "Bearer $identityToken")
                        setRequestProperty("Accept", "application/json")
                    }
                    val code = connection.responseCode
                    val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    connection.disconnect()
                    if (code == 401) {
                        identityPrefs.edit().remove("account_full_name").remove("account_name_token").apply()
                    } else if (code in 200..299) {
                        val response = JSONObject(body)
                        val user = response.optJSONObject("user")
                            ?: response.optJSONObject("data")
                            ?: response.optJSONObject("profile")
                            ?: response
                        val fullName = user.optString("full_name")
                            .ifBlank { user.optString("display_name") }
                            .ifBlank { user.optString("name") }
                            .trim()
                        if (fullName.isNotBlank()) {
                            identityPrefs.edit()
                                .putString("account_full_name", fullName)
                                .putString("account_name_token", identityToken)
                                .apply()
                            runOnUiThread {
                                if (securePrefs().getString("access_token", null) == identityToken) {
                                    accountAvatar.text = fullName.first().uppercaseChar().toString()
                                    accountAvatar.contentDescription = "অ্যাকাউন্ট: $fullName"
                                    accountName.text = fullName
                                }
                            }
                        }
                    }
                } catch (_: Exception) { }
            }.start()
        } else {
            accountAvatar.text = "U"
            accountAvatar.contentDescription = "অ্যাকাউন্ট"
            accountName.text = "ইউজার"
        }

        // Balance card: title and masked amount stay on the left; reveal control stays on the right.
        val balance = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(245, 35, 45), Color.rgb(210, 0, 18), Color.rgb(175, 0, 15))
            ).apply { cornerRadius = dp(20).toFloat() }
            elevation = dp(4).toFloat()
        }

        val balanceDetails = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        balanceDetails.addView(text("Total Balance", 13f, Color.WHITE, true))
        val balanceAmountText = text("৳  • • • • • •", 23f, Color.WHITE, true).apply {
            gravity = Gravity.START
            setPadding(0, dp(4), 0, 0)
        }
        homeBalanceText = balanceAmountText
        balanceDetails.addView(balanceAmountText, LinearLayout.LayoutParams(-1, dp(36)))
        balance.addView(balanceDetails, LinearLayout.LayoutParams(0, -2, 1f))

        val balanceHint = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(7), dp(9), dp(7))
            background = shape(Color.WHITE, 16)
            isClickable = true
            isFocusable = true
            contentDescription = "Tap for Balance"
            setOnClickListener { toggleHomeBalance() }
        }
        balanceHint.addView(text("Tap for Balance", 12f, red, true))
        val eyeIcon = FrameLayout(this).apply {
            contentDescription = "ব্যালেন্স লুকানো"
            addView(text("◉", 15f, red, true).apply {
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER))
            addView(View(this@MainActivity).apply {
                setBackgroundColor(red)
            }, FrameLayout.LayoutParams(dp(16), dp(1)).apply {
                gravity = Gravity.CENTER
            })
        }
        eyeIcon.setPadding(dp(4), 0, 0, 0)
        balanceHint.addView(eyeIcon, LinearLayout.LayoutParams(dp(23), dp(20)))
        balance.addView(balanceHint)

        page.addView(balance, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        // 4-column quick service grid, following the supplied reference layout.
        val services = listOf(
            "👛" to "সেন্ড মানি", "📱" to "মোবাইল রিচার্জ", "🛍" to "পেমেন্ট", "⇄" to "ফান্ড ট্রান্সফার",
            "➕" to "অ্যাড মানি", "🧾" to "বিল পে", "📘" to "শিক্ষা ফি", "🏛" to "সরকারি ফি",
            "👥" to "সেবা", "◷" to "হিসাব", "🤝" to "সহায়তা", "▦" to "আরও সেবা"
        )
        for (offset in services.indices step 4) {
            val serviceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in offset until minOf(offset + 4, services.size)) {
                val (symbol, label) = services[i]
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(3), dp(10), dp(3), dp(9))
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(18).toFloat()
                        setStroke(dp(1), Color.rgb(238, 220, 220))
                    }
                    elevation = dp(2).toFloat()
                    setOnClickListener {
                        when (label) {
                            "পেমেন্ট", "বিল পে" -> action("বিদ্যুৎ")
                            "ফান্ড ট্রান্সফার" -> action("ফান্ড ট্রান্সফার")
                            "সেন্ড মানি" -> action("সেন্ড মানি")
                            "অ্যাড মানি" -> action("অ্যাড মানি")
                            "মোবাইল রিচার্জ" -> action("মোবাইল রিচার্জ")
                            "শিক্ষা ফি" -> action("শিক্ষা ফি")
                            "সরকারি ফি" -> action("সরকারি ফি")
                            else -> action(label)
                        }
                    }
                }
                val iconBox = TextView(this).apply {
                    text = symbol
                    textSize = 25f
                    gravity = Gravity.CENTER
                    background = shape(Color.WHITE, 18)
                }
                tile.addView(iconBox, LinearLayout.LayoutParams(dp(62), dp(62)))
                tile.addView(text(label, 9.5f, ink, true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(0, dp(7), 0, 0)
                }, LinearLayout.LayoutParams(-1, dp(34)))
                serviceRow.addView(tile, LinearLayout.LayoutParams(0, dp(112), 1f))
            }
            page.addView(serviceRow)
        }

        // Promotional banner: real BPay campaign artwork with a woman holding the phone.
        val promo = ImageView(this).apply {
            setImageBitmap(BitmapFactory.decodeResource(resources, R.drawable.bpay_woman_phone))
            scaleType = ImageView.ScaleType.FIT_XY
            contentDescription = "একজন নারী হাতে BPay মোবাইল ধরে আছেন"
            isClickable = true
            setOnClickListener { showInfo("BPay", "এক অ্যাপে প্রয়োজনীয় সব সেবা।") }
        }
        page.addView(promo, LinearLayout.LayoutParams(-1, dp(112)).apply {
            topMargin = dp(10); bottomMargin = dp(14)
        })

        val rewards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("％" to "Promo Code" to "অফার চালু হলে এখানে দেখাবে",
               "♛" to "Reward Point" to "রিওয়ার্ড প্রোগ্রাম আসছে").forEach { entry ->
            val (pair, subtitle) = entry
            val (symbol, title) = pair
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(13), dp(10), dp(13))
                background = shape(Color.WHITE, 18)
                elevation = dp(1).toFloat()
                addView(text(symbol, 22f, red, true))
                addView(text(title, 13f, ink, true).apply { setPadding(0, dp(6), 0, dp(2)) })
                addView(text(subtitle, 9f, soft))
            }
            rewards.addView(card, LinearLayout.LayoutParams(0, dp(112), 1f).apply {
                marginEnd = dp(7)
            })
        }
        page.addView(rewards)

        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(7), dp(5), dp(7))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.WHITE, Color.WHITE)
            ).apply {
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
                setStroke(dp(1), Color.rgb(238, 225, 225))
            }
            elevation = dp(10).toFloat()
        }
        listOf("⌂" to "হোম", "♧" to "অফার", "▦" to "QR", "⌕" to "সার্চ", "☷" to "আরও").forEachIndexed { index, pair ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener {
                    when (pair.second) {
                        "হোম" -> scroll.smoothScrollTo(0, 0)
                        "অফার" -> showInfo("BPay Offers", "অফার সুবিধা চালুর প্রস্তুতি চলছে।")
                        "QR" -> startQrScanner()
                        "সার্চ" -> showServices()
                        else -> android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("আরও সেবা")
                            .setItems(arrayOf("ইনবক্স • লেনদেন", "সকল সেবা", "প্রোফাইল", "সাপোর্ট")) { _, which ->
                                when (which) {
                                    0 -> showInbox()
                                    1 -> showServices()
                                    2 -> showProfile()
                                    else -> showSupport()
                                }
                            }
                            .setNegativeButton("বন্ধ", null)
                            .show()
                    }
                }
            }
            item.addView(text(pair.first, 22f, if (index == 0) red else ink, true).apply { gravity = Gravity.CENTER })
            item.addView(text(pair.second, 9f, if (index == 0) red else ink, index == 0).apply { gravity = Gravity.CENTER })
            nav.addView(item, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun section(parent: LinearLayout, title: String, subtitle: String) {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(25), 0, dp(12)) }
        wrap.addView(text(title, 18f, dark, true))
        wrap.addView(text(subtitle, 11f, muted).apply { setPadding(0, dp(3), 0, 0) })
        parent.addView(wrap)
    }
    private fun grid(parent: LinearLayout, items: List<String>) {
        for (start in items.indices step 3) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in start until minOf(start + 3, items.size)) {
                val item = items[i]
                val parts = item.split("\n", limit = 2)
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(12), dp(5), dp(10))
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(17).toFloat()
                        setStroke(dp(1), Color.rgb(232, 239, 235))
                    }
                    elevation = dp(2).toFloat()
                    addView(TextView(this@MainActivity).apply {
                        text = parts[0]
                        textSize = 23f
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-1, dp(34)))
                    addView(TextView(this@MainActivity).apply {
                        text = parts.getOrElse(1) { item }
                        textSize = 10.5f
                        setTextColor(dark)
                        gravity = Gravity.CENTER
                        maxLines = 2
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, dp(5), 0, 0)
                    }, LinearLayout.LayoutParams(-1, -2))
                    setOnClickListener { action(parts.getOrElse(1) { item }) }
                }
                row.addView(tile, LinearLayout.LayoutParams(0, dp(88), 1f).apply {
                    marginEnd = dp(8)
                    bottomMargin = dp(9)
                })
            }
            while (row.childCount < 3) row.addView(View(this), LinearLayout.LayoutParams(0, dp(88), 1f).apply { marginEnd = dp(8) })
            parent.addView(row)
        }
    }
}