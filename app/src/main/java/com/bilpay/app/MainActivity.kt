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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var apiStatus: TextView
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
            lower.contains("সেন্ড মানি") || lower.contains("ট্রান্সফার") -> showSandboxWallet()
            lower.contains("অ্যাড মানি") || lower.contains("ক্যাশ") || lower.contains("qr") ||
                lower.contains("bkash") || lower.contains("nagad") || lower.contains("rocket") || lower.contains("upay") ->
                showInfo("সেবা চালুর অপেক্ষায়", "Cash In, Cash Out ও বাহ্যিক ট্রান্সফার এখনো চালু নয়। অনুমোদিত আর্থিক অংশীদার API ও চুক্তি যুক্ত হলে বাস্তব সেবা চালু হবে।")
            else -> showInfo("সেবা প্রস্তুত হচ্ছে", "এই সুবিধাটি বাস্তবভাবে চালুর জন্য প্রয়োজনীয় সার্ভার বা সেবাদাতা সংযোগ এখনো সম্পন্ন হয়নি।")
        }
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
                    showInfo("সফল", message)
                    showAuthForm(false)
                } else {
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
                                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 15000
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
        val progress = android.app.ProgressDialog(this).apply { setMessage("Sandbox Wallet যাচাই হচ্ছে…"); setCancelable(false); show() }
        Thread {
            var balance = ""
            var isAdmin = false
            var error = ""
            try {
                val (statusCode, statusBody) = sandboxRequest("GET", "/api/v1/sandbox/status", token)
                if (statusCode !in 200..299) error = "Sandbox সার্ভার যাচাই করা যায়নি (HTTP $statusCode)।"
                else if (!JSONObject(statusBody).optBoolean("sandbox_enabled", false)) error = "Sandbox Wallet এখনো Staging সার্ভারে চালু হয়নি। এটি বাস্তব ওয়ালেট নয়; চালু হলে এখানে পরীক্ষামূলক ব্যালেন্স দেখাবে।"
                else {
                    val (profileCode, profileBody) = sandboxRequest("GET", "/api/v1/auth/me", token)
                    if (profileCode !in 200..299) error = "অ্যাকাউন্টের Admin অবস্থা যাচাই করা যায়নি (HTTP $profileCode)।"
                    else {
                        isAdmin = JSONObject(profileBody).optBoolean("is_admin", false)
                        val (walletCode, walletBody) = sandboxRequest("GET", "/api/v1/sandbox/wallet", token)
                        if (walletCode in 200..299) balance = JSONObject(walletBody).optString("balance", "0.00")
                        else error = "Sandbox ব্যালেন্স পাওয়া যায়নি (HTTP $walletCode)।"

                    }
                }
            } catch (e: Exception) { error = "সার্ভারে সংযোগ করা যায়নি: " + (e.localizedMessage ?: "অজানা সমস্যা") }
            runOnUiThread {
                progress.dismiss()
                if (error.isNotBlank()) showInfo("Sandbox Wallet", error)
                else renderSandboxWallet(token, balance, isAdmin)
            }
        }.start()
    }

    private fun renderSandboxWallet(token: String, balance: String, isAdmin: Boolean) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        panel.addView(text("পরীক্ষামূলক ব্যালেন্স", 12f, muted, true))
        panel.addView(text("৳ $balance", 30f, dark, true).apply { setPadding(0, dp(6), 0, dp(4)) })
        panel.addView(text("এটি Sandbox-এর কাল্পনিক অর্থ। বাস্তবে জমা, উত্তোলন বা ব্যবহার করা যাবে না।", 11f, muted))
        panel.addView(text(if (isAdmin) "Admin অনুমতি: সক্রিয় ✓" else "Admin অনুমতি: সক্রিয় নয়", 12f, muted, true).apply { setPadding(0, dp(10), 0, 0) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        val send = Button(this).apply { text = "ফ্রি Send Money"; setOnClickListener { showSandboxTransfer(token) } }
        val history = Button(this).apply { text = "ইতিহাস"; setOnClickListener { showSandboxHistory(token) } }
        actions.addView(send, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        actions.addView(history, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(actions)
        if (isAdmin) {
            val adminGrant = Button(this).apply {
                text = "Admin: Sandbox ব্যালেন্স যোগ"
                setOnClickListener { requestSandboxAdminGrant(token) }
            }
            panel.addView(adminGrant, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
            panel.addView(text("একবারে সর্বোচ্চ ৳১০,০০,০০০ • শুধু নিজের Admin Sandbox Wallet", 10f, muted).apply { setPadding(0, dp(5), 0, 0) })
        }
        android.app.AlertDialog.Builder(this).setTitle("BPay Sandbox Wallet").setView(panel).setPositiveButton("বন্ধ", null).show()
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
                                if (success) showSandboxWallet()
                            }
                        }.start()
                    }.show()
            }
        }
        dialog.show()
    }

    private fun showSandboxTransfer(token: String) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val phone = EditText(this).apply { hint = "প্রাপকের BPay নম্বর"; inputType = android.text.InputType.TYPE_CLASS_PHONE }
        val amount = EditText(this).apply { hint = "পরিমাণ (৳)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        form.addView(phone); form.addView(amount)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Sandbox Send Money")
            .setMessage("ফি ৳০ • শুধু পরীক্ষামূলক ব্যালেন্স স্থানান্তর হবে।")
            .setView(form)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("পাঠান", null)
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
                        val (code, body) = sandboxRequest("POST", "/api/v1/sandbox/transfers/internal", token, payload, java.util.UUID.randomUUID().toString())
                        val json = JSONObject(body)
                        if (code in 200..299) {
                            success = true
                            message = "পরীক্ষামূলক Send Money সফল।\nপ্রাপক পেয়েছেন: ৳" + json.optString("recipient_credit") + "\nফি: ৳0.00\nবাস্তব টাকা স্থানান্তর হয়নি।"
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

    private fun showSandboxHistory(token: String) {
        val progress = android.app.ProgressDialog(this).apply { setMessage("Sandbox ইতিহাস আনা হচ্ছে…"); setCancelable(false); show() }
        Thread {
            var message = ""
            var error = ""
            try {
                val (code, body) = sandboxRequest("GET", "/api/v1/sandbox/transactions", token)
                if (code !in 200..299) error = "ইতিহাস পাওয়া যায়নি (HTTP $code)।"
                else {
                    val items = JSONObject(body).optJSONArray("items")
                    if (items == null || items.length() == 0) message = "এখনো কোনো পরীক্ষামূলক Send Money নেই।"
                    else {
                        val rows = mutableListOf<String>()
                        for (i in 0 until items.length()) {
                            val row = items.optJSONObject(i) ?: continue
                            val direction = if (row.optString("direction") == "OUT") "পাঠিয়েছেন" else "পেয়েছেন"
                            rows.add(direction + " • ৳" + row.optString("amount") + " • ফি ৳0.00 • " + row.optString("status"))
                        }
                        message = rows.joinToString("\n")
                    }
                }
            } catch (e: Exception) { error = "সংযোগ সমস্যা: " + (e.localizedMessage ?: "অজানা ত্রুটি") }
            runOnUiThread { progress.dismiss(); showInfo("Sandbox লেনদেন", if (error.isNotBlank()) error else message) }
        }.start()
    }

    private fun showHistory() {
        // Staging users should see the isolated, non-redeemable Sandbox ledger.
        // Production continues to show the live-ledger readiness notice until approved rails exist.
        if (BuildConfig.API_BASE_URL.contains("sandbox", ignoreCase = true)) {
            val token = securePrefs().getString("access_token", null)
            if (token.isNullOrBlank()) {
                showInfo("লগইন প্রয়োজন", "Sandbox লেনদেনের ইতিহাস দেখতে আগে BPay Staging অ্যাকাউন্টে লগইন করুন।")
                showAuthForm(false)
            } else {
                showSandboxHistory(token)
            }
        } else {
            showInfo("লেনদেনের ইতিহাস", "বাস্তব আর্থিক লেজার এখনো চালু করা হয়নি। পরীক্ষামূলক বা ডেমো লেনদেন এখানে দেখানো হবে না। নিরাপদ সার্ভার-সাইড লেজার প্রস্তুত হলে আপনার প্রকৃত লেনদেনের ইতিহাস এখানে দেখা যাবে।")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pageBg = Color.rgb(247, 248, 252)
        val navy = Color.rgb(34, 43, 105)
        val navy2 = Color.rgb(48, 61, 133)
        val red = Color.rgb(205, 35, 54)
        val gold = Color.rgb(224, 174, 61)
        val ink = Color.rgb(30, 35, 54)
        val soft = Color.rgb(119, 126, 148)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = false }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(22))
        }

        // Premium BPay header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(14))
        }
        val avatar = TextView(this).apply {
            text = "B"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(red)
                setStroke(dp(2), Color.rgb(244, 211, 151))
            }
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(46), dp(46)))
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        identity.addView(text("BPay", 19f, ink, true))
        identity.addView(text("ONE APP. ALL SERVICES.", 8f, soft, true))
        header.addView(identity)
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val bell = TextView(this).apply {
            text = "♧"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(navy)
            background = shape(Color.WHITE, 24)
            setOnClickListener { showInfo("নোটিফিকেশন", "নতুন নোটিফিকেশন আপাতত নেই।") }
        }
        header.addView(bell, LinearLayout.LayoutParams(dp(44), dp(44)))
        page.addView(header)

        // Balance card: never display a fabricated or live-looking amount.
        val balance = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(navy2, navy, Color.rgb(28, 35, 84))
            ).apply { cornerRadius = dp(24).toFloat() }
            elevation = dp(4).toFloat()
            setOnClickListener { showSandboxWallet() }
        }
        val balanceTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        balanceTop.addView(text("Total Balance", 12f, Color.rgb(206, 214, 245), true))
        balanceTop.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        balanceTop.addView(text("পরীক্ষামূলক", 9f, Color.WHITE, true).apply {
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = shape(Color.rgb(92, 101, 163), 20)
        })
        balance.addView(balanceTop)
        val balanceBottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        balanceBottom.addView(text("৳  • • • • • •", 25f, Color.WHITE, true))
        balanceBottom.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        balanceBottom.addView(text("◉  Tap for balance", 11f, Color.WHITE, true).apply {
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = shape(Color.rgb(78, 89, 151), 22)
        })
        balance.addView(balanceBottom)
        balance.addView(text("দেখতে চাপ দিন • Sandbox Wallet • বাস্তব টাকা নয়", 9f, Color.rgb(210, 216, 244)).apply {
            setPadding(0, dp(10), 0, 0)
        })
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
                    setOnClickListener {
                        when (label) {
                            "পেমেন্ট", "বিল পে" -> action("বিদ্যুৎ")
                            "ফান্ড ট্রান্সফার", "সেন্ড মানি" -> action("সেন্ড মানি")
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

        // Promotional banner (informational only; no fake offer is advertised).
        val promo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(17), dp(20), dp(17))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(42, 52, 125), Color.rgb(75, 91, 177), Color.rgb(32, 42, 105))
            ).apply { cornerRadius = dp(20).toFloat() }
            setOnClickListener { showInfo("BPay Offers", "অফার ও ক্যাম্পেইন অনুমোদিতভাবে চালু হলে এখানে দেখানো হবে।") }
        }
        promo.addView(text("BPay • পেমেন্ট এখন আরও সহজ", 18f, Color.WHITE, true))
        promo.addView(text("আপনার প্রয়োজনীয় সেবা—এক জায়গায়", 11f, Color.rgb(225, 229, 255)).apply { setPadding(0, dp(5), 0, 0) })
        page.addView(promo, LinearLayout.LayoutParams(-1, dp(104)).apply {
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
        val note = text("🔒  বাস্তব আর্থিক লেনদেন অনুমোদিত ব্যাংক/API সংযুক্তির পর চালু হবে।", 10f, soft)
        note.setPadding(dp(4), dp(18), dp(4), dp(8))
        page.addView(note)
        apiStatus = text("API: সংযোগ যাচাই হচ্ছে…", 9f, soft).apply { setPadding(dp(4), dp(4), dp(4), 0) }
        page.addView(apiStatus)
        page.addView(text("BPay Android v${BuildConfig.VERSION_NAME}", 9f, soft).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })

        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(7), dp(5), dp(7))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(navy, Color.rgb(39, 48, 117))
            ).apply { cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f) }
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
                        "QR" -> showInfo("QR Payment", "QR পেমেন্ট অনুমোদিত মার্চেন্ট API যুক্ত হলে চালু হবে।")
                        "সার্চ" -> showServices()
                        else -> showServices()
                    }
                }
            }
            item.addView(text(pair.first, 22f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
            item.addView(text(pair.second, 9f, Color.WHITE, index == 0).apply { gravity = Gravity.CENTER })
            nav.addView(item, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(nav)
        setContentView(root)
        checkApiConnection()
    }

    private fun checkApiConnection() {
        val baseUrl = BuildConfig.API_BASE_URL.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            apiStatus.text = "API: এখনো সার্ভার URL সেট করা হয়নি"
            return
        }
        Thread {
            val connected = try {
                val connection = (URL(baseUrl + "/health").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val ok = connection.responseCode == 200
                connection.disconnect()
                ok
            } catch (_: Exception) { false }
            runOnUiThread {
                apiStatus.text = if (connected) "API: সংযুক্ত ✓" else "API: সার্ভারে সংযোগ পাওয়া যায়নি"
                apiStatus.setTextColor(if (connected) green else muted)
            }
        }.start()
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