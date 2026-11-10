package com.metalsyntax.paritykt.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metalsyntax.paritykt.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/** Navegación compartida por todas las vistas. */
class Nav(val go: (Route) -> Unit, val back: () -> Unit, val newTx: (TxType) -> Unit, val openTx: (Transaction) -> Unit,
          val snack: (String) -> Unit, val settings: () -> Unit, val tutorial: (Tutorial) -> Unit)

fun routeIcon(r: Route): ImageVector = when (r) {
    Route.HOME -> Icons.Outlined.Home; Route.TX -> Icons.Outlined.ReceiptLong; Route.ANALYSIS -> Icons.Outlined.Insights
    Route.BUDGET -> Icons.Outlined.PieChart; Route.GOALS -> Icons.Outlined.EmojiEvents; Route.WALLETS -> Icons.Outlined.AccountBalanceWallet
    Route.PLAN -> Icons.Outlined.CalendarMonth; Route.SHOPPING -> Icons.Outlined.ShoppingCart; Route.PROFILE -> Icons.Outlined.Person
}

/** Color de cada acción rápida (ALL_QUICK_ACTIONS de Dashboard.tsx). */
fun routeColor(r: Route): Color = when (r) {
    Route.TX -> Color(0xFF34D399); Route.BUDGET -> Color(0xFF60A5FA); Route.PLAN -> Color(0xFFFB923C); Route.ANALYSIS -> Color(0xFFC084FC)
    Route.WALLETS -> Color(0xFF818CF8); Route.GOALS -> Color(0xFFEAB308); Route.SHOPPING -> Color(0xFFF59E0B); Route.PROFILE -> Color(0xFFA1A1AA)
    Route.HOME -> Brand
}

fun widgetIcon(w: Widget): ImageVector = when (w) {
    Widget.QUICK -> Icons.Outlined.Apps; Widget.BALANCE -> Icons.Outlined.ShowChart; Widget.CONVERTER -> Icons.Outlined.SwapHoriz
    Widget.WALLETS -> Icons.Outlined.AccountBalanceWallet; Widget.EXPENSES -> Icons.Outlined.DonutLarge; Widget.INCOME_EXPENSE -> Icons.Outlined.BarChart
    Widget.UPCOMING -> Icons.Outlined.CalendarMonth; Widget.BUDGET -> Icons.Outlined.PieChart; Widget.GOALS -> Icons.Outlined.EmojiEvents
    Widget.RECENT -> Icons.Outlined.ReceiptLong
}

val GOAL = Color(0xFFEAB308)
fun pct(v: Double, total: Double) = if (total > 0) "${"%.0f".format(v / total * 100)}%" else "—"

// ================================================================================================ Inicio

@Composable
fun HomeScreen(nav: Nav) {
    val d = Store.data
    var customize by remember { mutableStateOf(false) }
    var tutorials by remember { mutableStateOf(false) }
    val month = d.monthTx()
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item { HomeHeader(d, nav, onWidgets = { customize = true }, onTutorials = { tutorials = true }) }
        Prefs.widgets.filter { it !in Prefs.hidden }.forEach { w ->
            when (w) {
                Widget.QUICK -> item(key = w) { QuickActions(nav) }
                Widget.BALANCE -> item(key = w) { Spacer(Modifier.height(8.dp)); BalanceCard(d) }
                Widget.CONVERTER -> item(key = w) { Spacer(Modifier.height(16.dp)); Converter(d.rates) }
                Widget.WALLETS -> item(key = w) {
                    if (d.accounts.isEmpty()) Empty(Icons.Outlined.AccountBalanceWallet, t("kt_start_wallet"), t("kt_start_wallet_desc"), t("kt_create_wallet")) { nav.go(Route.WALLETS) }
                    else {
                        Section(t("wallet"), t("slide_hint")) { nav.go(Route.WALLETS) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            d.accounts.forEach { a -> WalletMini(a) { nav.go(Route.WALLETS) } }
                        }
                    }
                }
                Widget.EXPENSES -> month.filter { it.type == TxType.EXPENSE }.takeIf { it.isNotEmpty() }?.let { ex -> item(key = w) { ExpenseStructure(ex) { nav.go(Route.ANALYSIS) } } }
                Widget.INCOME_EXPENSE -> item(key = w) {
                    Section(t("incomeVsExpenses"))
                    GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Route.ANALYSIS) }) { Column(Modifier.padding(16.dp)) { IncomeExpenseBars(d.monthlyTotals(YearMonth.now())) } }
                }
                Widget.UPCOMING -> {
                    val up = d.scheduled.filter { runCatching { !LocalDate.parse(it.date).isAfter(LocalDate.now().plusDays(7)) }.getOrDefault(false) }.sortedBy { it.date }
                    if (up.isNotEmpty()) {
                        item(key = w) { Section(t("kt_next_7_days"), t("kt_see_all")) { nav.go(Route.PLAN) } }
                        items(up.take(3), key = { "s" + it.id }) { s -> ScheduledRow(s, nav) }
                    }
                }
                Widget.BUDGET -> d.activeBudgets().takeIf { it.isNotEmpty() }?.let { budgets ->
                    item(key = w) {
                        Section(t("budget"), t("kt_see_all")) { nav.go(Route.BUDGET) }
                        GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Route.BUDGET) }) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                                budgets.sortedByDescending { d.spentUsd(it.categoryId) / it.limit }.take(3).forEach { b ->
                                    val spent = d.spentUsd(b.categoryId); val c = category(b.categoryId)
                                    Progress(c.name, spent, b.limit, "${money(spent)} / ${money(b.limit)}", catColor(c))
                                }
                            }
                        }
                    }
                }
                Widget.GOALS -> d.goals.filter { !it.completed }.takeIf { it.isNotEmpty() }?.let { goals ->
                    item(key = w) {
                        Section(t("goals"), t("kt_see_all"), Icons.Outlined.EmojiEvents, GOAL) { nav.go(Route.GOALS) }
                        GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Route.GOALS) }) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                                goals.take(3).forEach { g -> Progress(g.name, g.savedAmount, g.targetAmount, "${money(g.targetAmount)} · ${pct(g.savedAmount, g.targetAmount)}", GOAL) }
                            }
                        }
                    }
                }
                Widget.RECENT -> {
                    item(key = w) {
                        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("recentTransactions").uppercase(ES), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Muted, letterSpacing = 1.sp)
                            GhostButton(t("filter"), Icons.Outlined.Tune) { nav.go(Route.TX) }
                        }
                        if (d.transactions.isEmpty()) EmptyNote(t("noTransactions"))
                    }
                    d.transactions.sortedByDescending { it.date }.take(Prefs.txLimit).groupBy { it.date.take(10) }.forEach { (day, txs) ->
                        item(key = "h$day") { Text(dayLabel(day), Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = Muted) }
                        items(txs, key = { it.id }) { TxRow(it, nav.openTx) }
                    }
                }
            }
        }
    }
    if (customize) CustomizerSheet { customize = false }
    if (tutorials) TutorialsSheet({ tutorials = false; nav.tutorial(it) }) { tutorials = false }
}

@Composable
private fun HomeHeader(d: AppData, nav: Nav, onWidgets: () -> Unit, onTutorials: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var parallel by remember { mutableStateOf(false) }
    val drive = rememberDriveSync(nav)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).tap(onClick = { nav.go(Route.PROFILE) }).clip(CircleShape).background(Surface1), contentAlignment = Alignment.Center) {
            Text(initials(d.profile.name), color = Ink, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (Prefs.dev) Pill(t("devModeActive"), Warning)
            if (!Prefs.hideWelcome) Text(t("welcome_back").uppercase(ES), style = MaterialTheme.typography.labelSmall, color = Muted.copy(alpha = 0.75f), letterSpacing = 1.5.sp, maxLines = 2)
            if (!Prefs.hideName) Text(d.profile.name.ifBlank { "Parity" }.substringBefore(' '), style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // tasas siempre visibles; en modo Dev, al tocar, muestra la paralela
        Box {
            Column(Modifier.tap(onClick = { if (Prefs.dev) parallel = true else nav.settings() }).clip(RoundedCornerShape(16.dp)).background(Surface1)
                .padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                if (d.rates.usd > 0) {
                    Text("USD: ${num(d.rates.usd)} Bs", color = RateUsd, style = MaterialTheme.typography.labelMedium)
                    if (d.rates.eur > 0) Text("EUR: ${num(d.rates.eur)} Bs", color = RateEur, style = MaterialTheme.typography.labelMedium)
                } else Text(t("kt_no_rate"), color = Muted, style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(parallel, { parallel = false }, Modifier.background(Surface2)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Caps(t("kt_parallel_rates"), color = Brand)
                    Spacer(Modifier.height(8.dp))
                    RateLine("USD ${t("parallel")}", d.rates.usdPar, RateUsd); RateLine("EUR ${t("parallel")}", d.rates.eurPar, RateEur)
                    RateLine("USD BCV", d.rates.bcvUsd, Muted); RateLine("EUR BCV", d.rates.bcvEur, Muted)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (Prefs.dev) Chip(Icons.Outlined.CloudSync, t("btn_sync"), onClick = drive)
        Chip(Icons.Outlined.Widgets, t("btn_widgets"), onClick = onWidgets)
        Chip(Icons.Outlined.Refresh, t("btn_update"), spinning = busy) {
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { Store.refreshRates() } }
                busy = false
                nav.snack(r.fold({ "BCV: ${num(it.bcvUsd)} Bs/$" }, { t("kt_offline") }))
            }
        }
        Chip(Icons.Outlined.Tune, t("btn_settings"), onClick = nav.settings)
        Chip(Icons.Outlined.School, t("btn_tutorials"), onClick = onTutorials)
    }
}

@Composable
private fun RateLine(label: String, v: Double, c: Color) = Row(Modifier.width(200.dp).padding(vertical = 2.dp)) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Muted)
    Text(if (v > 0) "${num(v)} Bs" else "—", style = MaterialTheme.typography.labelMedium, color = c)
}

/** Botoncitos del encabezado (Widgets, Actualizar, Ajustes, Tutoriales): ícono y rótulo diminuto. */
@Composable
private fun Chip(ic: ImageVector, label: String, spinning: Boolean = false, onClick: () -> Unit) {
    val rot = remember { Animatable(0f) }
    LaunchedEffect(spinning) { while (spinning) { rot.animateTo(rot.value + 360f, tween(700)) } }
    Column(Modifier.tap(onClick = onClick).clip(RoundedCornerShape(16.dp)).background(Surface1).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(ic, null, tint = Muted, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rot.value })
        Text(label.uppercase(ES), style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Muted)
    }
}

@Composable
private fun QuickActions(nav: Nav) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp, start = 4.dp, end = 4.dp)) {
        Caps(t("quickActions"), Modifier.weight(1f))
        Text(t("slide_hint").uppercase(ES), style = MaterialTheme.typography.labelSmall, color = Brand.copy(alpha = 0.6f))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Prefs.quick.forEach { r ->
            val c = routeColor(r)
            Column(Modifier.width(84.dp).tap(onClick = { nav.go(r) }).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(c.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(routeIcon(r), null, tint = c, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(t(r.key).uppercase(ES), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp), color = Muted,
                    textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** Balance de los últimos 30 días hacia atrás desde el total actual (para la línea de tendencia). */
private fun balanceHistory(d: AppData, days: Int = 30): List<Double> {
    val net = d.transactions.groupBy { it.date.take(10) }.mapValues { (_, l) ->
        l.sumOf { when (it.type) { TxType.INCOME -> it.normalizedAmountUSD; TxType.EXPENSE -> -it.normalizedAmountUSD; else -> 0.0 } }
    }
    val out = ArrayList<Double>(days)
    var b = d.totalUsd()
    for (i in 0 until days) { out.add(b); b -= net[LocalDate.now().minusDays(i.toLong()).toString()] ?: 0.0 }
    return out.reversed()
}

@Composable
private fun BalanceCard(d: AppData) {
    val total = d.totalUsd()
    val hist = remember(d.transactions, total) { balanceHistory(d) }
    val start = hist.first()
    val trend = if (start != 0.0) (total - start) / kotlin.math.abs(start) * 100 else 0.0
    val up = trend >= 0
    val lineColor = if (up) Income else Color(0xFFF43F5E)
    // conteo animado del saldo y trazo de la línea (useSpring + pathLength de la web)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(total) { anim.snapTo(0f); anim.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Surface1, Bg)))) {
        Canvas(Modifier.matchParentSize().padding(top = 70.dp)) {
            val min = hist.min(); val max = hist.max(); val span = (max - min).takeIf { it > 0 } ?: 1.0
            val path = Path()
            val n = ((hist.size - 1) * anim.value).toInt().coerceAtLeast(1)
            for (i in 0..n) {
                val x = size.width * i / (hist.size - 1); val y = size.height * (1 - ((hist[i] - min) / span).toFloat() * 0.8f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor.copy(alpha = 0.35f), style = Stroke(5f, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caps(t("totalBalance"), Modifier.weight(1f))
                DisplayCurrencyButton(40.dp)
                Spacer(Modifier.width(10.dp))
                RoundIcon(if (Prefs.balanceVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, t("kt_toggle_balance"), Prefs::toggleBalance,
                    tint = Muted, size = 40.dp, bg = Bg)
            }
            Spacer(Modifier.height(14.dp))
            AutoText(shown(total * anim.value), MaterialTheme.typography.displaySmall, Ink)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("≈ ${secondary(total)}", Modifier.clip(RoundedCornerShape(12.dp)).background(Glass).padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1)
                if (Prefs.balanceVisible && d.transactions.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Pill((if (up) "↗ " else "↘ ") + "%.1f%%".format(kotlin.math.abs(trend)), lineColor)
                }
            }
        }
    }
}

// ================================================================================================ Conversor (calculadora)

/** Conversor de moneda de la web (CurrencyConverter.tsx): vía bolívares, con la tasa en uso. */
@Composable
private fun Converter(r: Rates) {
    var amount by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(Currency.USD) }
    var to by remember { mutableStateOf(Currency.VES) }
    fun ves(c: Currency) = when (c) { Currency.VES -> 1.0; Currency.EUR -> r.eur.takeIf { it > 0 } ?: r.usd; else -> r.usd }
    // vacío: 1 unidad de divisa o la tasa equivalente en Bs, como la web
    val a = parseAmount(amount) ?: if (from == Currency.VES) ves(to) else 1.0
    val result = if (r.usd > 0) a * ves(from) / ves(to) else 0.0
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SwapHoriz, null, tint = Brand, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("currencyConverter").uppercase(ES), style = MaterialTheme.typography.labelMedium, color = Ink)
                    Text((if (r.parallel) t("parallel") else t("kt_bcv_rate")).uppercase(ES), style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Pill("↗ LIVE", RateUsd)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Caps(t("amountToConvert"), Modifier.weight(1f), color = Ink); CurrencyPill(from) { from = it } }
            androidx.compose.foundation.text.BasicTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true, textStyle = MaterialTheme.typography.headlineMedium.copy(color = Ink),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Brand),
                decorationBox = { inner -> Box { if (amount.isEmpty()) Text(if (from == Currency.VES) num(a) else "1",
                    style = MaterialTheme.typography.headlineMedium, color = Muted.copy(alpha = 0.5f)); inner() } })
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RoundIcon(Icons.Outlined.SwapVert, t("kt_swap"), { val f = from; from = to; to = f }, tint = Muted, size = 40.dp, bg = Bg)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Caps(t("convertedAmount"), Modifier.weight(1f), color = Brand); CurrencyPill(to) { to = it } }
            Text(if (r.usd > 0) num(result) else "—", style = MaterialTheme.typography.headlineMedium, color = Brand, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ================================================================================================ piezas del Inicio

/** Billetera compacta del carrusel: logo en su color, moneda, saldo y nombre. */
@Composable
fun WalletMini(a: Account, onClick: () -> Unit) {
    GlassCard(Modifier.width(150.dp), onClick = onClick, radius = 20.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountGlyph(a.icon, 24.dp); Spacer(Modifier.weight(1f)); Pill(a.currency.name)
            }
            Spacer(Modifier.height(14.dp))
            Text(if (Prefs.balanceVisible) num(a.balance) else HIDDEN, style = MaterialTheme.typography.titleMedium, color = if (a.balance < 0) Expense else Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.name, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ExpenseStructure(expenses: List<Transaction>, onOpen: () -> Unit) {
    val total = expenses.sumOf { it.normalizedAmountUSD }
    val cats = expenses.groupBy { it.category }.map { (id, l) -> category(id) to l.sumOf { it.normalizedAmountUSD } }.sortedByDescending { it.second }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(total) { sweep.snapTo(0f); sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Section(t("kt_expense_structure"))
    GlassCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(shown(total), style = MaterialTheme.typography.headlineSmall, color = Ink)
                Spacer(Modifier.width(8.dp)); Caps(t("totalExpenses"), Modifier.padding(bottom = 4.dp))
            }
            Text("≈ ${secondary(total)}", style = MaterialTheme.typography.labelMedium, color = Muted)
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(170.dp)) {
                    var startA = -90f
                    val st = 26.dp.toPx()
                    cats.forEach { (c, v) ->
                        val a = (v / total).toFloat() * 360f * sweep.value
                        drawArc(catColor(c), startA, (a - 1.5f).coerceAtLeast(0.5f), false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st))
                        startA += a
                    }
                }
                Caps(t("kt_top_expense"), color = Muted.copy(alpha = 0.5f))
            }
            cats.take(4).forEach { (c, v) ->
                ListRow(c.name, icon = icon(c.icon), tint = catColor(c), trailing = shown(v), trailingSub = "%.1f%%".format(v / total * 100), color = Bg)
            }
        }
    }
}

// ================================================================================================ Personalizar Dashboard

/** Personalizar Dashboard de la web: widgets (mostrar/ocultar y arrastrar para ordenar), acciones rápidas y privacidad. */
@Composable
private fun CustomizerSheet(onDismiss: () -> Unit) {
    val wKeys: Set<Any> = Widget.entries.map { "w" + it.name }.toSet()
    val quickAll = Prefs.quick + (QUICK_DEFAULT - Prefs.quick.toSet())
    // Column y no LazyColumn: la precarga del LazyColumn mientras el arrastre reordena corrompía la composición (crash)
    val st = rememberReorder(null) { from, to ->
        if (from in wKeys) Prefs.saveWidgets(Prefs.widgets.move(from, to) { "w" + it.name })
        else Prefs.saveQuick(Prefs.quick.move(from, to) { "q" + it.name })
    }
    Sheet(onDismiss) {
        Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(t("kt_customize_dashboard"), style = MaterialTheme.typography.headlineSmall, color = Ink); Caps(t("layoutControl")) }
                RoundIcon(Icons.Outlined.Close, t("close"), onDismiss, tint = Muted, size = 40.dp, bg = Bg)
            }
            Section(t("widgets"))
            Prefs.widgets.forEach { w -> key("w" + w.name) {
                ListRow(t(w.key), Modifier.reorderItem(st, "w" + w.name), icon = widgetIcon(w), color = Bg, handle = { DragHandle(st, "w" + w.name, wKeys) },
                    trailingSlot = { Toggle(w !in Prefs.hidden) { Prefs.toggleWidget(w) } })
            } }
            Section(t("quickActions"))
            quickAll.forEach { r -> key("q" + r.name) {
                val on = r in Prefs.quick
                ListRow(t(r.key), Modifier.reorderItem(st, "q" + r.name), icon = routeIcon(r), tint = routeColor(r), color = Bg,
                    handle = if (on) { { DragHandle(st, "q" + r.name, Prefs.quick.map { "q" + it.name }.toSet()) } } else null,
                    trailingSlot = { Toggle(on) { Prefs.saveQuick(if (on) Prefs.quick - r else Prefs.quick + r) } })
            } }
            Section(t("privacy"))
            SettingRow(t("hideWelcome")) { Toggle(Prefs.hideWelcome, onChange = Prefs::setHideWelcome) }
            SettingRow(t("hideName")) { Toggle(Prefs.hideName, onChange = Prefs::setHideName) }
            SettingRow(t("dashboardTxLimit")) { Stepper(Prefs.txLimit, Prefs::setTxLimit) }
        }
    }
}

/** Contador − n + reutilizable (límite de transacciones, aviso previo). */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    RoundIcon(Icons.Outlined.Remove, "-", { onChange(value - 1) }, size = 36.dp, bg = Bg)
    Text("$value", Modifier.width(40.dp), style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center)
    RoundIcon(Icons.Outlined.Add, "+", { onChange(value + 1) }, size = 36.dp, bg = Bg)
}

// ================================================================================================ Ajustes (popup)

/** Ajustes de la web (SettingsModal): apariencia, tasa de cambio y seguridad. */
@Composable
fun SettingsSheet(nav: Nav, onDismiss: () -> Unit) {
    val d = Store.data
    val scope = rememberCoroutineScope()
    var source by remember { mutableIntStateOf(when { d.rates.manual -> 2; d.rates.parallel -> 1; else -> 0 }) }
    var usd by remember { mutableStateOf(if (d.rates.usd > 0) num(d.rates.usd) else "") }
    var eur by remember { mutableStateOf(if (d.rates.eur > 0) num(d.rates.eur) else "") }
    var busy by remember { mutableStateOf(false) }
    var pinSheet by remember { mutableStateOf(false) }
    TitledSheet(t("settings"), onDismiss = onDismiss) {
        Section(t("appearance"), icon = Icons.Outlined.Palette)
        ThemePicker()
        Section(t("exchangeRate"), icon = Icons.Outlined.TrendingUp)
        Text(t("kt_rate_source_desc"), style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(12.dp))
        RateOption(Icons.Outlined.Public, t("officialRate"), "USD ${num(d.rates.bcvUsd)} · EUR ${num(d.rates.bcvEur)}", source == 0, Income) { source = 0 }
        if (Prefs.dev) RateOption(Icons.Outlined.CurrencyExchange, t("parallel"), "USD ${num(d.rates.usdPar)} · EUR ${num(d.rates.eurPar)}", source == 1, Warning) { source = 1 }
        RateOption(Icons.Outlined.Lock, t("manualRate"), t("kt_manual_rate_desc"), source == 2, Color(0xFFC084FC)) { source = 2 }
        if (source == 2) {
            Spacer(Modifier.height(8.dp)); RateStepper("Bs / USD", usd) { usd = it }
            Spacer(Modifier.height(8.dp)); RateStepper("Bs / EUR", eur) { eur = it }
        }
        Spacer(Modifier.height(14.dp))
        BrandButton(t("update"), Modifier.fillMaxWidth(), Icons.Outlined.Save, enabled = !busy && (source != 2 || parseAmount(usd) != null)) {
            if (source == 2) {
                Store.setRates(d.rates.copy(usd = parseAmount(usd)!!, eur = parseAmount(eur) ?: 0.0, updatedAt = nowIso(), manual = true)); nav.snack(t("kt_rate_saved"))
            } else {
                busy = true
                Store.setRates(d.rates.copy(manual = false, parallel = source == 1))
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Store.refreshRates() } }
                    busy = false; nav.snack(r.fold({ "USD ${num(it.usd)} Bs" }, { t("kt_offline") }))
                }
            }
        }
        Text("${t("kt_updated")} ${ago(d.rates.updatedAt)}", Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.labelSmall,
            color = Muted, textAlign = TextAlign.Center)
        Section(t("security"), icon = Icons.Outlined.Lock)
        GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, color = Bg) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingRow(if (Pin.enabled) t("changePin") else t("kt_create_pin"), if (Pin.enabled) t("kt_pin_on") else t("kt_pin_off"),
                    onClick = { pinSheet = true }) { Chevron() }
                if (Pin.enabled) {
                    SettingRow(t("kt_lock_on_exit"), t("kt_lock_on_exit_desc")) { Toggle(Pin.lockOnExit, onChange = Pin::setLockOnExit) }
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val can = remember { Biometric.available(ctx) }
                    SettingRow(t("biometrics"), if (can) t("biometricsDesc") else t("kt_bio_unavailable"), icon = Icons.Outlined.Fingerprint) {
                        Toggle(Pin.biometric && can, enabled = can) { v ->
                            // confirmar con la huella antes de activarla
                            if (v) Biometric.prompt(ctx as androidx.fragment.app.FragmentActivity) { Pin.setBiometric(true); nav.snack(t("kt_bio_on")) } else Pin.setBiometric(false)
                        }
                    }
                    SettingRow(t("kt_remove_pin"), titleColor = Expense, onClick = { Pin.clear(); nav.snack(t("kt_pin_removed")) }) {}
                }
                SettingRow(t("kt_encrypted"), t("kt_encrypted_desc"), icon = Icons.Outlined.VerifiedUser, tint = Income) {}
            }
        }
    }
    if (pinSheet) PinSetupSheet { ok -> pinSheet = false; if (ok) nav.snack(t("kt_pin_saved")) }
}

@Composable
private fun RateOption(ic: ImageVector, title: String, desc: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    ListRow(title, subtitle = desc, leading = { Bubble(ic, tint, 48.dp) }, color = if (selected) Brand.copy(alpha = 0.14f) else Bg,
        trailingSlot = { if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = Brand) }, onClick = onClick)
}

/** Tasa manual con botones − / + (TASA ACTUAL de la web). */
@Composable
private fun RateStepper(label: String, value: String, onChange: (String) -> Unit) {
    fun step(dv: Double) { onChange(num(((parseAmount(value) ?: 0.0) + dv).coerceAtLeast(0.0))) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Bg).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(Icons.Outlined.Remove, "-", { step(-1.0) }, size = 48.dp)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.text.BasicTextField(value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = Ink, textAlign = TextAlign.Center),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Brand))
            Caps(label)
        }
        RoundIcon(Icons.Outlined.Add, "+", { step(1.0) }, size = 48.dp)
    }
}
