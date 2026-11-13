package com.bilpay.app

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
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
    private val demoTransactions = mutableListOf<String>()

    private var pendingApkDownload: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

    private fun checkForUpdate() {
        Thread {
            try {
                val connection = (URL("https://raw.githubusercontent.com/imran0075/BilPay_Android-/main/version.json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val info = JSONObject(body)
                val latestCode = info.optInt("versionCode", 1)
                val latestName = info.optString("versionName", "")
                val apkUrl = info.optString("apkUrl", "")
                if (latestCode > BuildConfig.VERSION_CODE && apkUrl.startsWith("https://")) {
                    runOnUiThread {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("নতুন BilPay আপডেট")
                            .setMessage("নতুন সংস্করণ $latestName পাওয়া গেছে।")
                            .setNegativeButton("পরে", null)
                            .setPositiveButton("আপডেট করুন") { _, _ -> downloadUpdate(apkUrl) }
                            .show()
                    }
                }
            } catch (_: Exception) { }
        }.start()
    }

    private fun downloadUpdate(apkUrl: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("BilPay আপডেট")
                .setDescription("নতুন সংস্করণ ডাউনলোড হচ্ছে")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "BilPay-update.apk")
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            pendingApkDownload = manager.enqueue(request)
            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != pendingApkDownload) return
                    try {
                        val cursor = manager.query(DownloadManager.Query().setFilterById(pendingApkDownload))
                        val success = cursor.use { it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL }
                        if (success) {
                            val apk = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "BilPay-update.apk")
                            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", apk)
                            startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } else Toast.makeText(this@MainActivity, "ডাউনলোড সম্পন্ন হয়নি।", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "আপডেট ইনস্টল চালু করা যায়নি।", Toast.LENGTH_LONG).show()
                    } finally { unregisterDownloadReceiver() }
                }
            }
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(downloadReceiver, filter)
            Toast.makeText(this, "আপডেট ডাউনলোড শুরু হয়েছে", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "আপডেট ডাউনলোড শুরু করা যায়নি", Toast.LENGTH_LONG).show()
        }
    }

    private fun unregisterDownloadReceiver() {
        downloadReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        downloadReceiver = null
    }

    override fun onDestroy() {
        unregisterDownloadReceiver()
        super.onDestroy()
    }

    private fun action(label: String) {
        val lower = label.lowercase()
        if (lower == "সেবা" || lower == "আরও সেবা") {
            showServices()
            return
        }
        if (lower == "প্রোফাইল") {
            showProfile()
            return
        }
        if (lower == "সাপোর্ট" || lower == "সহায়তা") {
            showSupport()
            return
        }
        if (lower == "হোম") {
            showInfo("BilPay Home", "আপনি BilPay-এর হোম স্ক্রিনে আছেন। সব সুবিধা আপাতত ডেমো মোডে ব্যবহার করতে পারবেন।")
            return
        }
        if (lower.contains("অ্যাড মানি") || lower.contains("সেন্ড মানি") ||
            lower.contains("ক্যাশ") || lower.contains("ট্রান্সফার") || lower.contains("qr") ||
            lower.contains("বিদ্যুৎ") || lower.contains("পানি") || lower.contains("গ্যাস") ||
            lower.contains("ইন্টারনেট") || lower.contains("রিচার্জ") || lower.contains("bKash".lowercase()) ||
            lower.contains("nagad") || lower.contains("rocket") || lower.contains("upay") ||
            lower.contains("হিসাব") || lower.contains("লেনদেন") || lower.contains("প্রোফাইল") ||
            lower.contains("সেবা") || lower.contains("নোটিফিকেশন")) {
            openDemoFlow(label)
            return
        }
        val message = when {
            label.contains("হিসাব") || label.contains("লেনদেন") ->
                "লেনদেনের ইতিহাস চালুর জন্য নিরাপদ অ্যাকাউন্ট ও সার্ভার-সাইড লেজার তৈরি করা হচ্ছে।"
            label.contains("বিদ্যুৎ") || label.contains("পানি") || label.contains("গ্যাস") ||
            label.contains("ইন্টারনেট") || label.contains("টিভি") || label.contains("টেলিফোন") ||
            label.contains("শিক্ষা") || label.contains("হাসপাতাল") || label.contains("সরকারি") ||
            label.contains("অন্যান্য") || label.contains("রিচার্জ") ->
                "BilPay অ্যাপের সার্ভার API এবং এই বিলারের পেমেন্ট API আলাদা। এই বিলারের অফিসিয়াল API/চুক্তি এখনো যুক্ত হয়নি, তাই আপাতত বিল জমা বা টাকা কাটা যাবে না।"
            label.contains("bKash") || label.contains("Nagad") || label.contains("Rocket") ||
            label.contains("Upay") || label.contains("মানি") || label.contains("ট্রান্সফার") ||
            label.contains("QR") || label.contains("অ্যাড") ->
                "BilPay সার্ভার চালু থাকলেও bKash/Nagad/Rocket/Upay-এর লেনদেন API আলাদাভাবে অনুমোদিত merchant চুক্তি ও credentials দিয়ে যুক্ত করতে হয়। সেগুলো এখনো যুক্ত হয়নি; তাই টাকা পাঠানো বা গ্রহণ বন্ধ আছে।"
            label.contains("প্রোফাইল") ->
                "নিরাপদ লগইন ও পরিচয় যাচাই (KYC) মডিউল পরবর্তী ধাপে যুক্ত করা হবে।"
            else -> "এই সুবিধাটি এখনো চালু করা হয়নি। সার্ভার সংযোগ আর বাস্তব সেবাদাতার API সংযোগ এক বিষয় নয়। প্রয়োজনীয় API ও অনুমোদন যুক্ত হলে এটি চালু হবে।"
        }
        showInfo("BilPay • $label", message)
    }

    private fun showInfo(title: String, message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("বুঝেছি", null)
            .show()
    }

    private fun showServices() {
        val items = arrayOf(
            "বিদ্যুৎ বিল", "পানি বিল", "গ্যাস বিল", "ইন্টারনেট বিল",
            "মোবাইল রিচার্জ", "টিভি / DTH", "টেলিফোন বিল", "শিক্ষা ফি",
            "হাসপাতাল বিল", "সরকারি ফি", "bKash", "Nagad", "Rocket", "Upay",
            "QR পে", "ব্যাংক ট্রান্সফার"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("BilPay • সকল সেবা")
            .setItems(items) { _, which -> action(items[which]) }
            .setNegativeButton("বন্ধ", null)
            .show()
    }

    private fun showProfile() {
        val prefs = getSharedPreferences("bilpay_demo", MODE_PRIVATE)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply {
            hint = "আপনার নাম"
            setText(prefs.getString("profile_name", ""))
        }
        val phone = EditText(this).apply {
            hint = "মোবাইল নম্বর"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("profile_phone", ""))
        }
        form.addView(name)
        form.addView(phone)
        android.app.AlertDialog.Builder(this)
            .setTitle("ডেমো প্রোফাইল")
            .setMessage("তথ্যগুলো শুধু এই ফোনে সংরক্ষিত হবে। এটি নিরাপদ লগইন বা KYC নয়।")
            .setView(form)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("সংরক্ষণ") { _, _ ->
                prefs.edit()
                    .putString("profile_name", name.text.toString().trim())
                    .putString("profile_phone", phone.text.toString().trim())
                    .apply()
                showInfo("প্রোফাইল সংরক্ষিত", "তোমার ডেমো প্রোফাইল এই ডিভাইসে সংরক্ষণ করা হয়েছে।")
            }
            .show()
    }

    private fun showSupport() {
        showInfo(
            "BilPay সাপোর্ট",
            "ইমেইল: imran16102001bd@gmail.com\nমোবাইল: +8801977714371\n\nPIN, OTP বা পাসওয়ার্ড কাউকে জানাবেন না।"
        )
    }

    private fun showBillForm(service: String) {
        showInfo("সেবা এখনো চালু নয়", "$service-এর অনুমোদিত বিলার API ও পেমেন্ট যাচাই সংযোগ সম্পন্ন হলে এই সেবা চালু হবে। এখন কোনো বিল জমা বা টাকা কাটা হবে না।")
    }

    private fun showHistory() {
        val prefs = getSharedPreferences("bilpay_demo", MODE_PRIVATE)
        val entries = prefs.getString("history", "")?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        val body = if (entries.isEmpty()) "এখনো কোনো ডেমো লেনদেন নেই।" else entries.reversed().joinToString("\n")
        showInfo("ডেমো লেনদেনের ইতিহাস", body + "\n\nএগুলো পরীক্ষামূলক রেকর্ড—বাস্তব টাকা লেনদেন নয়।")
    }

    private fun openDemoFlow(label: String) {
        val prefs = getSharedPreferences("bilpay_demo", MODE_PRIVATE)
        val current = prefs.getLong("balance", 50000L)
        val title = when {
            label.contains("অ্যাড") || label.contains("Cash In", true) -> "ডেমো অ্যাড মানি"
            label.contains("সেন্ড") || label.contains("bKash", true) || label.contains("Nagad", true) || label.contains("Rocket", true) || label.contains("Upay", true) -> "ডেমো টাকা পাঠান"
            label.contains("ক্যাশ") -> "ডেমো ক্যাশ আউট"
            label.contains("ট্রান্সফার") -> "ডেমো ব্যাংক ট্রান্সফার"
            label.contains("QR", true) -> "ডেমো QR পেমেন্ট"
            label.contains("রিচার্জ") -> "ডেমো মোবাইল রিচার্জ"
            label.contains("বিদ্যুৎ") || label.contains("পানি") || label.contains("গ্যাস") || label.contains("ইন্টারনেট") || label.contains("বিল") -> "ডেমো বিল পেমেন্ট • $label"
            label.contains("হিসাব") || label.contains("লেনদেন") -> { showHistory(); return }
            label.contains("প্রোফাইল") -> { showInfo("BilPay প্রোফাইল", "অ্যাকাউন্ট: ডেমো ব্যবহারকারী\nKYC: এখনো যাচাই হয়নি\nনিরাপত্তা: PIN/OTP মডিউল প্রস্তুতির পর্যায়ে"); return }
            label.contains("নোটিফিকেশন") -> { showInfo("নোটিফিকেশন", "নতুন কোনো নোটিফিকেশন নেই।"); return }
            else -> { showInfo(label, "এই সেবার ডেমো স্ক্রিন প্রস্তুত হচ্ছে।"); return }
        }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val target = EditText(this).apply {
            hint = if (title.contains("বিল")) "বিলার / কাস্টমার নম্বর" else "মোবাইল / অ্যাকাউন্ট নম্বর"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val amount = EditText(this).apply {
            hint = "টাকার পরিমাণ (৳)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        form.addView(target)
        form.addView(amount)
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("ডেমো ব্যালেন্স: ৳$current\nশুধু পরীক্ষার জন্য—বাস্তব টাকা কাটবে না বা পাঠানো হবে না।")
            .setView(form)
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("ডেমো নিশ্চিত করুন") { _, _ ->
                val value = amount.text.toString().toLongOrNull()
                if (value == null || value <= 0L) {
                    showInfo("সঠিক পরিমাণ দিন", "টাকার পরিমাণ ১ বা তার বেশি হতে হবে।")
                } else {
                    val isAdd = title.contains("অ্যাড মানি")
                    if (!isAdd && value > current) {
                        showInfo("পর্যাপ্ত ব্যালেন্স নেই", "ডেমো ওয়ালেটে পর্যাপ্ত টাকা নেই।")
                    } else {
                        val updated = if (isAdd) current + value else current - value
                        val recipient = target.text.toString().ifBlank { "ডেমো" }
                        val record = "$title • ৳$value • $recipient"
                        val old = prefs.getString("history", "") ?: ""
                        prefs.edit().putLong("balance", updated).putString("history", (old + "\n" + record).trim()).apply()
                        showInfo("ডেমো সম্পন্ন", "পরীক্ষামূলক রেকর্ড সংরক্ষিত হয়েছে।\nনতুন ডেমো ব্যালেন্স: ৳$updated\n\nবাস্তব লেনদেন হয়নি।")
                    }
                }
            }.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val canvas = Color.rgb(246, 249, 247)
        val ink = Color.rgb(18, 37, 33)
        val soft = Color.rgb(111, 128, 120)
        val accent = Color.rgb(0, 132, 91)
        window.statusBarColor = canvas
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(canvas)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(26))
        }

        // Compact BilPay brand header aligned to the start side
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val mark = ImageView(this).apply {
            setImageResource(R.drawable.bilpay_gold_b_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "BilPay gold B logo"
        }
        brand.addView(mark, LinearLayout.LayoutParams(dp(38), dp(38)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        brandText.addView(text("BilPay Premium", 19f, ink, true))
        brandText.addView(text("ONE APP. ALL SERVICES.", 8f, soft, true).apply { setPadding(0, dp(2), 0, 0) })
        brand.addView(brandText, LinearLayout.LayoutParams(-2, -2))
        page.addView(brand, LinearLayout.LayoutParams(-1, dp(48)))

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(25), 0, dp(13))
        }
        welcome.addView(text("স্বাগতম 👋", 13f, soft, true))
        welcome.addView(text("আপনার সব পেমেন্ট, এক অ্যাপে", 20f, ink, true).apply { setPadding(0, dp(4), 0, 0) })
        page.addView(welcome)

        // Premium wallet card
        val balance = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(21), dp(20), dp(21), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(0, 143, 101), Color.rgb(0, 79, 72))).apply { cornerRadius = dp(25).toFloat() }
            elevation = dp(6).toFloat()
        }
        val balanceTop = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        balanceTop.addView(text("WALLET BALANCE", 10f, Color.rgb(207, 239, 225), true), LinearLayout.LayoutParams(0, -2, 1f))
        balanceTop.addView(text("●  DEMO WALLET", 9f, Color.rgb(219, 247, 235), true))
        balance.addView(balanceTop)
        val demoBalance = getSharedPreferences("bilpay_demo", MODE_PRIVATE).getLong("balance", 50000L)
        balance.addView(text("৳$demoBalance", 31f, Color.WHITE, true).apply {
            setPadding(0, dp(10), 0, dp(3))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        balance.addView(text("পরীক্ষামূলক ব্যালেন্স • বাস্তব অর্থ নয়", 10f, Color.rgb(211, 240, 228)))
        val quickTitle = text("দ্রুত কাজ", 12f, ink, true).apply { setPadding(0, dp(22), 0, dp(10)) }
        page.addView(balance, LinearLayout.LayoutParams(-1, -2))
        page.addView(quickTitle)

        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("＋" to "অ্যাড মানি", "↗" to "সেন্ড মানি", "⇄" to "ট্রান্সফার", "▦" to "QR পে").forEach { pair ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(10), dp(3), dp(10))
                background = shape(Color.WHITE, 17)
                elevation = dp(2).toFloat()
                setOnClickListener { action(pair.second) }
            }
            cell.addView(TextView(this).apply {
                text = pair.first
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(accent)
                typeface = Typeface.DEFAULT_BOLD
            })
            cell.addView(text(pair.second, 10f, ink, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
            quick.addView(cell, LinearLayout.LayoutParams(0, dp(78), 1f).apply { marginEnd = dp(8) })
        }
        page.addView(quick)

        section(page, "বিল পেমেন্ট", "প্রয়োজনীয় সব বিল সহজে পরিশোধ করুন")
        grid(page, listOf("⚡\nবিদ্যুৎ","💧\nপানি","🔥\nগ্যাস","🌐\nইন্টারনেট","📱\nমোবাইল রিচার্জ","📺\nটিভি / DTH","☎\nটেলিফোন","🎓\nশিক্ষা ফি","🏥\nহাসপাতাল","🏛\nসরকারি ফি","🧾\nঅন্যান্য বিল","＋\nআরও সেবা"))

        section(page, "মোবাইল ফিন্যান্স", "এক জায়গায় জনপ্রিয় মোবাইল ব্যাংকিং")
        val providers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("bKash","Nagad","Rocket","Upay").forEachIndexed { i, name ->
            val colors = intArrayOf(Color.rgb(220,35,105), Color.rgb(239,105,25), Color.rgb(110,42,150), Color.rgb(0,145,105))
            val tile = TextView(this).apply {
                text = name
                textSize = 12f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setTextColor(colors[i])
                gravity = Gravity.CENTER
                background = shape(Color.WHITE, 14)
                elevation = dp(2).toFloat()
                setOnClickListener { action(name) }
            }
            providers.addView(tile, LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(7) })
        }
        page.addView(providers)

        val safety = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = shape(Color.rgb(231, 243, 237), 17)
        }
        safety.addView(text("🔒", 21f, accent))
        val safetyCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            addView(text("নিরাপত্তা সবার আগে", 12f, ink, true))
            addView(text("অনুমোদিত API যুক্ত না হওয়া পর্যন্ত বাস্তব লেনদেন বন্ধ।", 10f, soft))
        }
        safety.addView(safetyCopy)
        page.addView(safety, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
        apiStatus = text("ডেমো মোড সক্রিয় • বাস্তব পেমেন্ট বন্ধ", 10f, soft).apply { setPadding(dp(3), dp(12), dp(3), 0) }
        page.addView(apiStatus)

        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
        }
        listOf("⌂" to "হোম", "▣" to "সেবা", "◷" to "হিসাব", "●" to "প্রোফাইল").forEachIndexed { index, pair ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener { action(pair.second) }
            }
            item.addView(TextView(this).apply {
                text = pair.first
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (index == 0) accent else soft)
            })
            item.addView(text(pair.second, 10f, if (index == 0) accent else soft, index == 0).apply { gravity = Gravity.CENTER })
            nav.addView(item, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(nav)
        setContentView(root)
        checkForUpdate()
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
