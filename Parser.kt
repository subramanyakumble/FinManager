package com.personal.finmanager

import android.content.Context
import android.provider.Telephony
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class Txn(
    val id: Long,
    val dt: LocalDateTime,
    val amount: Double,
    val isDebit: Boolean,
    val merchant: String,
    val bank: String,
    val account: String,
    val category: String
)

object Categories {
    val all = listOf(
        "Food", "Groceries", "Fuel", "Shopping", "Bills & Recharge", "Travel",
        "Entertainment", "Health", "Education", "Transfers", "Cash", "Income", "Others"
    )

    private val keywords = listOf(
        "Food" to listOf("swiggy", "zomato", "restaurant", "cafe", "pizza", "domino", "mcdonald", "kfc", "burger", "bakery", "food", "hotel", "dhaba", "starbucks", "chai", "sweets"),
        "Groceries" to listOf("bigbasket", "blinkit", "zepto", "instamart", "dmart", "grocery", "kirana", "supermarket", "jiomart", "milk", "vegetable", "provision"),
        "Fuel" to listOf("petrol", "fuel", "hpcl", "bpcl", "iocl", "indian oil", "shell", "pump"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "shopping", "store", "mall", "lifestyle", "croma", "reliance retail"),
        "Bills & Recharge" to listOf("electricity", "recharge", "jio", "airtel", "vodafone", "bsnl", "gas", "broadband", "dth", "tata sky", "bill", "insurance", "torrent", "postpaid", "lic", "water"),
        "Travel" to listOf("uber", "ola", "rapido", "irctc", "redbus", "makemytrip", "goibibo", "indigo", "metro", "fastag", "toll", "railway", "flight"),
        "Entertainment" to listOf("netflix", "hotstar", "spotify", "prime video", "bookmyshow", "youtube", "pvr", "inox", "google play", "apple"),
        "Health" to listOf("pharmacy", "medical", "hospital", "apollo", "pharmeasy", "1mg", "clinic", "diagnostic", "netmeds", "lab"),
        "Education" to listOf("school", "college", "tuition", "udemy", "course", "fees", "university")
    )

    fun guess(merchant: String, body: String, isDebit: Boolean): String {
        if (!isDebit) return "Income"
        val b = body.lowercase()
        if (b.contains("atm") || b.contains("cash withdrawal")) return "Cash"
        val m = merchant.lowercase()
        for ((cat, words) in keywords) if (words.any { m.contains(it) }) return cat
        if (m.contains("@")) return "Transfers"
        return "Others"
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cats", Context.MODE_PRIVATE)

    fun set(ctx: Context, merchant: String, category: String) {
        prefs(ctx).edit().putString("c_" + merchant.lowercase(), category).apply()
    }
}

object Parser {
    private val amountRe = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val debitRe = Regex("""\b(debited|spent|withdrawn|paid|sent|purchased?|deducted|payment of)\b""")
    private val creditRe = Regex("""\b(credited|received|deposited|refunded|refund|cashback|reversed)\b""")
    private val skipRe = Regex("""\b(otp|one time password|verification code|will be debited|is due|due on|minimum amount due|requested|request|offer|pre-approved)\b""")
    private val acctRe = Regex("""(?:a/c|acct?|account|card)[^0-9]{0,15}([0-9]{3,4})\b""", RegexOption.IGNORE_CASE)
    private val merchRes = listOf(
        Regex("""(?:vpa|info:?|towards|trf to|transfer to|upi/p2[am]/\d+/|upi-)\s*([A-Za-z0-9@._ &-]{3,40})""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:at|to|from|by)\s+([A-Za-z0-9@._&][A-Za-z0-9@._& -]{2,35})""", RegexOption.IGNORE_CASE)
    )
    private val cutRe = Regex("""\s+(?:on|ref|upi|via|using|avl|bal|not|if|is|has|a/c|from|dt|date|for|txn|at)\b|\(|\.\s|\.$""", RegexOption.IGNORE_CASE)
    private val badRe = Regex("""^(a/c|acct|account|your|card|bank|you|the|rs|inr|ref|avl|bal|upi|neft|imps)\b|^[0-9x*. ]+$""", RegexOption.IGNORE_CASE)

    private val banks = listOf(
        "HDFC" to "HDFC", "SBI" to "SBI", "ICIC" to "ICICI", "AXIS" to "Axis", "KOTAK" to "Kotak",
        "PNB" to "PNB", "BARODA" to "BoB", "BOB" to "BoB", "PAYTM" to "Paytm", "PHONEPE" to "PhonePe",
        "CANARA" to "Canara", "IDFC" to "IDFC", "YESB" to "Yes", "UNION" to "Union", "INDUS" to "IndusInd"
    )

    private fun bankOf(sender: String): String {
        val s = sender.uppercase()
        for ((k, v) in banks) if (s.contains(k)) return v
        return s.substringAfter('-')
    }

    private fun merchantOf(body: String): String {
        for (re in merchRes) {
            for (m in re.findAll(body)) {
                var s = cutRe.split(m.groupValues[1])[0].trim().trim('.', '-', ' ', ',')
                if (s.length >= 3 && !badRe.containsMatchIn(s)) {
                    if (!s.contains("@")) {
                        s = s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                    }
                    return s
                }
            }
        }
        return "Unknown"
    }

    fun parse(id: Long, sender: String, body: String, date: Long, override: (String) -> String?): Txn? {
        if (sender.isNotEmpty() && (sender[0].isDigit() || sender[0] == '+')) return null
        val lower = body.lowercase()
        if (skipRe.containsMatchIn(lower)) return null
        val amt = amountRe.find(body) ?: return null
        val amount = amt.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val d = debitRe.find(lower)
        val c = creditRe.find(lower)
        if (d == null && c == null) return null
        val isDebit = if (d != null && c != null) d.range.first < c.range.first else d != null
        val merchant = merchantOf(body)
        val account = acctRe.find(body)?.groupValues?.get(1) ?: ""
        val cat = override(merchant) ?: Categories.guess(merchant, body, isDebit)
        val dt = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDateTime()
        return Txn(id, dt, amount, isDebit, merchant, bankOf(sender), account, cat)
    }
}

object SmsReader {
    fun read(ctx: Context): List<Txn> {
        val prefs = ctx.getSharedPreferences("cats", Context.MODE_PRIVATE)
        val since = System.currentTimeMillis() - 400L * 24 * 3600 * 1000
        val out = ArrayList<Txn>()
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf("_id", "address", "body", "date"),
            "date > ?", arrayOf(since.toString()), "date DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val t = Parser.parse(
                    c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getLong(3)
                ) { m -> prefs.getString("c_" + m.lowercase(), null) }
                if (t != null) out.add(t)
            }
        }
        return out
    }
}
