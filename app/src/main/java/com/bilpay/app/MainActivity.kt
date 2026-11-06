package com.bilpay.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
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
        Toast.makeText(this, "$label — সেবাটি শিগগিরই চালু হবে", Toast.LENGTH_SHORT).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = green
        window.navigationBarColor = Color.rgb(248, 250, 249)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(248,250,249)) }
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(24)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val logo = TextView(this).apply {
            text = "B"; textSize = 25f; setTextColor(white); gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
            background = shape(green, 15); layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        header.addView(logo)
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        brand.addView(text("BilPay", 23f, dark, true))
        brand.addView(text("ONE APP • ALL SERVICES", 10f, muted, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply { text = "◉"; textSize = 25f; setTextColor(green); gravity = Gravity.CENTER; setOnClickListener { action("নোটিফিকেশন") } })
        page.addView(header)
        val balance = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(0,135,91), Color.rgb(0,75,65))).apply { cornerRadius = dp(24).toFloat() }
        }
        val balanceTop = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        balanceTop.addView(text("মোট ব্যালেন্স", 14f, Color.rgb(220,245,235)), LinearLayout.LayoutParams(0,-2,1f))
        balanceTop.addView(text("◎  নিরাপদ ওয়ালেট", 11f, Color.WHITE, true))
        balance.addView(balanceTop)
        balance.addView(text("৳ ০.০০", 32f, white, true).apply { setPadding(0,dp(12),0,dp(4)) })
        balance.addView(text("ডেমো ব্যালেন্স • বাস্তব টাকা নয়", 11f, Color.rgb(220,245,235)))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0,dp(18),0,0) }
        listOf("＋\nঅ্যাড মানি", "↗\nসেন্ড মানি", "⇄\nট্রান্সফার", "▦\nQR পে").forEach { item ->
            val v = TextView(this).apply { text = item; textSize = 12f; setTextColor(white); gravity = Gravity.CENTER; setPadding(dp(5),dp(9),dp(5),dp(9)); background = shape(Color.argb(45,255,255,255),12); setOnClickListener { action(item.substringAfter('\n')) } }
            actions.addView(v, LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(6) })
        }
        balance.addView(actions)
        page.addView(balance, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(22) })
        section(page, "সকল বিল পরিশোধ", "সব ধরনের বিল এক জায়গায়")
        grid(page, listOf("⚡\nবিদ্যুৎ","💧\nপানি","🔥\nগ্যাস","🌐\nইন্টারনেট","📱\nমোবাইল রিচার্জ","📺\nটিভি / DTH","☎\nটেলিফোন","🎓\nশিক্ষা ফি","🏥\nহাসপাতাল","🏛\nসরকারি ফি","🧾\nঅন্যান্য বিল","＋\nআরও সেবা"))
        section(page, "মোবাইল ব্যাংকিং", "জনপ্রিয় সেবাগুলো একসাথে")
        val providers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("bKash","Nagad","Rocket","Upay").forEachIndexed { i, name ->
            val c = intArrayOf(Color.rgb(220,35,105),Color.rgb(239,105,25),Color.rgb(110,42,150),Color.rgb(0,145,105))[i]
            val tile = TextView(this).apply { text = name; textSize = 13f; setTypeface(null,Typeface.BOLD); setTextColor(c); gravity = Gravity.CENTER; background = shape(white,14); elevation = dp(2).toFloat(); setOnClickListener { action(name) } }
            providers.addView(tile, LinearLayout.LayoutParams(0,dp(62),1f).apply { marginEnd=dp(7) })
        }
        page.addView(providers)
        val note = TextView(this).apply { text = "🔒  আপনার লেনদেনের নিরাপত্তাই আমাদের অগ্রাধিকার\n\nBilPay এখন ডেমো সংস্করণ। বাস্তব পেমেন্ট চালুর আগে অনুমোদিত পার্টনার API ও নিরাপত্তা যাচাই প্রয়োজন।"; textSize=12f; setTextColor(muted); setPadding(dp(16),dp(16),dp(16),dp(16)); background=shape(Color.rgb(233,244,239),16) }
        page.addView(note, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(22) })
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        val nav = LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(dp(8),dp(10),dp(8),dp(10)); setBackgroundColor(white) }
        listOf("⌂\nহোম","▣\nসেবা","◷\nহিসাব","●\nপ্রোফাইল").forEach { item ->
            val v = TextView(this).apply { text=item; gravity=Gravity.CENTER; textSize=11f; setTextColor(if(item.startsWith("⌂")) green else muted); setOnClickListener { action(item.substringAfter('\n')) } }
            nav.addView(v,LinearLayout.LayoutParams(0,dp(48),1f))
        }
        root.addView(nav)
        setContentView(root)
    }
    private fun section(parent: LinearLayout, title: String, subtitle: String) {
        parent.addView(text(title,18f,dark,true).apply { setPadding(0,dp(24),0,dp(3)) })
        parent.addView(text(subtitle,11f,muted).apply { setPadding(0,0,0,dp(12)) })
    }
    private fun grid(parent: LinearLayout, items: List<String>) {
        for (start in items.indices step 4) {
            val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            for (i in start until minOf(start+4,items.size)) {
                val item=items[i]
                val tile=TextView(this).apply { text=item; textSize=11f; gravity=Gravity.CENTER; setTextColor(dark); setPadding(dp(3),dp(8),dp(3),dp(8)); background=shape(white,14); elevation=dp(1).toFloat(); setOnClickListener { action(item.substringAfter('\n')) } }
                row.addView(tile,LinearLayout.LayoutParams(0,dp(76),1f).apply { marginEnd=dp(7); bottomMargin=dp(8) })
            }
            parent.addView(row)
        }
    }
}
