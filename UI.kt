package com.personal.finmanager

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private val Red = Color(0xFFC62828)
private val Green = Color(0xFF2E7D32)
private val Track = Color(0xFFE0E0E0)
private val Accent = Color(0xFF1B5E20)

fun inr(d: Double): String =
    "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(d.roundToLong())

private fun List<Txn>.spent() = filter { it.isDebit }.sumOf { it.amount }
private fun List<Txn>.income() = filter { !it.isDebit }.sumOf { it.amount }

@Composable
fun App() {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var txns by remember { mutableStateOf<List<Txn>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(granted, refresh) {
        if (granted) txns = withContext(Dispatchers.IO) { SmsReader.read(ctx) }
    }
    val list = txns
    if (!granted) {
        PermissionScreen { launcher.launch(Manifest.permission.READ_SMS) }
    } else if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        Main(list, onRecat = { m, c -> Categories.set(ctx, m, c); refresh++ }, onRefresh = { refresh++ })
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Fin Manager", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Accent)
        Spacer(Modifier.height(12.dp))
        Text("Reads your bank and UPI SMS on this phone to build spending reports. Nothing leaves your device.")
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("Allow SMS access") }
        Spacer(Modifier.height(20.dp))
        Text(
            "If Android says the permission is blocked: Settings → Apps → Fin Manager → ⋮ (top right) → Allow restricted settings, then Permissions → SMS → Allow. Reopen the app afterwards.",
            fontSize = 12.sp
        )
    }
}

@Composable
fun Main(txns: List<Txn>, onRecat: (String, String) -> Unit, onRefresh: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val tabs = listOf("📊" to "Overview", "🧾" to "Transactions", "📈" to "Reports")
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = tab == i, onClick = { tab = i },
                    icon = { Text(t.first) }, label = { Text(t.second) }
                )
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding().fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("◀") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row {
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("▶") }
                    TextButton(onClick = onRefresh) { Text("⟳") }
                }
            }
            val inMonth = txns.filter { YearMonth.from(it.dt) == month }
            when (tab) {
                0 -> Overview(inMonth)
                1 -> TxnList(inMonth, onRecat)
                else -> Reports(inMonth, txns, month)
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontSize = 12.sp)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun BarRow(label: String, value: Double, max: Double, color: Color, extra: String = "") {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp)
            Text(inr(value) + extra, fontSize = 14.sp)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(Track, RoundedCornerShape(4.dp))) {
            val frac = if (max <= 0) 0f else (value / max).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun Overview(m: List<Txn>) {
    val spent = m.spent()
    val inc = m.income()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Spent", inr(spent), Red, Modifier.weight(1f))
            StatCard("Income", inr(inc), Green, Modifier.weight(1f))
            StatCard("Net", (if (inc - spent < 0) "-" else "") + inr(kotlin.math.abs(inc - spent)), if (inc >= spent) Green else Red, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text("Where your money went", fontWeight = FontWeight.Bold)
        val cats = m.filter { it.isDebit }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
        if (cats.isEmpty()) Text("No spending found this month.", fontSize = 13.sp)
        cats.forEach { BarRow(it.key, it.value, cats.first().value, Accent) }
        Spacer(Modifier.height(16.dp))
        Text("Latest", fontWeight = FontWeight.Bold)
        m.sortedByDescending { it.dt }.take(5).forEach { TxnRow(it) {} }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun TxnRow(t: Txn, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.merchant, fontWeight = FontWeight.Medium, maxLines = 1)
            val acc = if (t.account.isNotEmpty()) " ••" + t.account else ""
            Text(
                t.category + " · " + t.bank + acc + " · " + t.dt.format(DateTimeFormatter.ofPattern("dd MMM")),
                fontSize = 12.sp
            )
        }
        Text(
            (if (t.isDebit) "-" else "+") + inr(t.amount),
            color = if (t.isDebit) Red else Green, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TxnList(m: List<Txn>, onRecat: (String, String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Txn?>(null) }
    val shown = m.filter {
        q.isBlank() || it.merchant.contains(q, true) || it.category.contains(q, true) || it.bank.contains(q, true)
    }.sortedByDescending { it.dt }
    Column(Modifier.padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            label = { Text("Search merchant, category, bank") }, modifier = Modifier.fillMaxWidth()
        )
        Text("Tap a transaction to change its category (applies to that merchant).", fontSize = 11.sp)
        LazyColumn { items(shown) { t -> TxnRow(t) { editing = t } } }
    }
    editing?.let { t ->
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
            title = { Text(t.merchant) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Categories.all.forEach { c ->
                        TextButton(onClick = { onRecat(t.merchant, c); editing = null }) {
                            Text(if (c == t.category) "✓ $c" else c)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun Reports(m: List<Txn>, all: List<Txn>, month: YearMonth) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        // Daily spend
        Text("Daily spending", fontWeight = FontWeight.Bold)
        val days = month.lengthOfMonth()
        val perDay = DoubleArray(days + 1)
        m.filter { it.isDebit }.forEach { perDay[it.dt.dayOfMonth] += it.amount }
        val maxDay = maxOf(perDay.max(), 1.0)
        Row(Modifier.fillMaxWidth().height(110.dp), verticalAlignment = Alignment.Bottom) {
            for (d in 1..days) {
                Box(
                    Modifier.weight(1f).padding(horizontal = 0.5.dp)
                        .height((perDay[d] / maxDay * 100).toFloat().coerceAtLeast(1f).dp)
                        .background(if (perDay[d] > 0) Red else Track)
                )
            }
        }
        val avg = m.spent() / days
        Text("Average per day: " + inr(avg) + "   ·   Highest day: " + inr(maxDay.takeIf { it > 1.0 } ?: 0.0), fontSize = 12.sp)

        // Six month trend
        Spacer(Modifier.height(20.dp))
        Text("Last 6 months (green = income, red = spent)", fontWeight = FontWeight.Bold)
        val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
        val data = months.map { mo ->
            val l = all.filter { YearMonth.from(it.dt) == mo }
            Triple(mo, l.income(), l.spent())
        }
        val maxM = maxOf(data.maxOf { maxOf(it.second, it.third) }, 1.0)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            data.forEach { (mo, inc, sp) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.height(110.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.width(12.dp).height((inc / maxM * 100).toFloat().coerceAtLeast(1f).dp).background(Green))
                        Spacer(Modifier.width(2.dp))
                        Box(Modifier.width(12.dp).height((sp / maxM * 100).toFloat().coerceAtLeast(1f).dp).background(Red))
                    }
                    Text(mo.format(DateTimeFormatter.ofPattern("MMM")), fontSize = 11.sp)
                }
            }
        }

        // Category split
        Spacer(Modifier.height(20.dp))
        Text("Category share", fontWeight = FontWeight.Bold)
        val total = m.spent()
        val cats = m.filter { it.isDebit }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
        cats.forEach {
            val pct = if (total > 0) (it.value / total * 100).roundToLong() else 0L
            BarRow(it.key, it.value, total, Accent, "  ($pct%)")
        }

        // Top merchants
        Spacer(Modifier.height(20.dp))
        Text("Top merchants", fontWeight = FontWeight.Bold)
        val merch = m.filter { it.isDebit }.groupBy { it.merchant }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }.take(8)
        merch.forEach { BarRow(it.key, it.value, merch.first().value, Red) }

        // Biggest expenses
        Spacer(Modifier.height(20.dp))
        Text("Biggest expenses", fontWeight = FontWeight.Bold)
        m.filter { it.isDebit }.sortedByDescending { it.amount }.take(5).forEach { TxnRow(it) {} }
        Spacer(Modifier.height(24.dp))
    }
}
