package com.metalsyntax.paritykt.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
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
          val snack: (String) -> Unit, val settings: () -> Unit) {
}

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

// ------------------------------------------------------------------------------------------------ Inicio

@Composable
fun HomeScreen(nav: Nav) {
    val d = Store.data
    val month = d.monthTx()
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item { HomeHeader(d, nav) }
        item { QuickActions(nav) }
        item { BalanceCard(d) }
        item { Spacer(Modifier.height(16.dp)); Converter(d.rates) }
        if (d.accounts.isEmpty()) item {
            Empty(Icons.Outlined.AccountBalanceWallet, t("kt_start_wallet"), t("kt_start_wallet_desc"), t("kt_create_wallet")) { nav.go(Route.WALLETS) }
        } else item {
            Section(t("wallet"), t("slide_hint")) { nav.go(Route.WALLETS) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                d.accounts.sortedByDescending { d.rates.toUsd(it.balance, it.currency) }.forEach { a -> WalletCard(a) { nav.go(Route.WALLETS) } }
            }
        }
        val expenses = month.filter { it.type == TxType.EXPENSE }
        if (expenses.isNotEmpty()) item { ExpenseStructure(expenses) { nav.go(Route.ANALYSIS) } }
        val upcoming = d.scheduled.filter { runCatching { !LocalDate.parse(it.date).isAfter(LocalDate.now().plusDays(7)) }.getOrDefault(false) }.sortedBy { it.date }
        if (upcoming.isNotEmpty()) {
            item { Section(t("kt_next_7_days"), t("kt_see_all")) { nav.go(Route.PLAN) } }
            items(upcoming.take(3), key = { "s" + it.id }) { s ->
                val c = category(s.category)
                Row2(icon(c.icon), s.name, dayLabel(s.date) + " · " + s.frequency.label, money(s.amount, s.currency),
                    if (s.type == TxType.INCOME) Income else Expense, tint = catColor(c), onClick = { nav.go(Route.PLAN) })
            }
        }
        val budgets = d.budgets.filter { it.month == null || it.month == YearMonth.now().toString() }
        if (budgets.isNotEmpty()) item {
            Section(t("budget"), t("kt_see_all")) { nav.go(Route.BUDGET) }
            GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Route.BUDGET) }) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    budgets.sortedByDescending { d.spentUsd(it.categoryId) / it.limit }.take(3).forEach { b ->
                        val spent = d.spentUsd(b.categoryId)
                        Progress(category(b.categoryId).name, spent, b.limit, "${money(spent)} / ${money(b.limit)}", catColor(category(b.categoryId)))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("recentTransactions").uppercase(ES), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Muted, letterSpacing = 1.sp)
                GhostButton(t("filter"), Icons.Outlined.Tune) { nav.go(Route.TX) }
            }
        }
        if (d.transactions.isEmpty()) item { Text(t("noTransactions"), Modifier.padding(8.dp), color = Muted) }
        val recent = d.transactions.sortedByDescending { it.date }.take(6)
        recent.groupBy { it.date.take(10) }.forEach { (day, txs) ->
            item(key = "h$day") { Text(dayLabel(day), Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = Muted) }
            items(txs, key = { it.id }) { TxRow(it, nav.openTx) }
        }
    }
}

@Composable
private fun HomeHeader(d: AppData, nav: Nav) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).tap(onClick = { nav.go(Route.PROFILE) }).clip(CircleShape).background(Surface1).border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center) { Text(initials(d.profile.name), color = Ink, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("welcome_back").uppercase(ES), style = MaterialTheme.typography.labelSmall, color = Muted.copy(alpha = 0.75f), letterSpacing = 1.5.sp, maxLines = 2)
            Text(d.profile.name.ifBlank { "Parity" }.substringBefore(' '), style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // tasas siempre visibles, como el recuadro de la web
        Column(Modifier.tap(onClick = nav.settings).clip(RoundedCornerShape(16.dp)).background(Surface1).border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
            if (d.rates.usd > 0) {
                Text("${t("usd_rate")}: ${num(d.rates.usd)} Bs", color = RateUsd, style = MaterialTheme.typography.labelMedium)
                if (d.rates.eur > 0) Text("${t("eur_rate")}: ${num(d.rates.eur)} Bs", color = RateEur, style = MaterialTheme.typography.labelMedium)
            } else Text(t("kt_no_rate"), color = Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(Icons.Outlined.Tune, t("btn_settings"), onClick = nav.settings)
        Chip(Icons.Outlined.Refresh, t("btn_update"), spinning = busy) {
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { Store.refreshRates() } }
                busy = false
                nav.snack(r.fold({ "BCV: ${num(it.usd)} Bs/$" }, { t("kt_offline") }))
            }
        }
    }
}

/** Botoncitos del encabezado (Ajustes, Actualizar): ícono y rótulo diminuto. */
@Composable
private fun Chip(ic: ImageVector, label: String, spinning: Boolean = false, onClick: () -> Unit) {
    val rot = remember { Animatable(0f) }
    LaunchedEffect(spinning) { while (spinning) { rot.animateTo(rot.value + 360f, tween(700)) } }
    Column(Modifier.tap(onClick = onClick).clip(RoundedCornerShape(16.dp)).background(Surface1).border(1.dp, GlassBorder.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
        listOf(Route.TX, Route.BUDGET, Route.PLAN, Route.ANALYSIS, Route.WALLETS, Route.GOALS, Route.SHOPPING, Route.PROFILE).forEach { r ->
            val c = routeColor(r)
            Column(Modifier.width(84.dp).tap(onClick = { nav.go(r) }).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(c.copy(alpha = 0.1f)).border(1.dp, c.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center) { Icon(routeIcon(r), null, tint = c, modifier = Modifier.size(28.dp)) }
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
    val total = d.totalUsd()
    val net = d.transactions.groupBy { it.date.take(10) }.mapValues { (_, l) ->
        l.sumOf { when (it.type) { TxType.INCOME -> it.normalizedAmountUSD; TxType.EXPENSE -> -it.normalizedAmountUSD; else -> 0.0 } }
    }
    val out = ArrayList<Double>(days)
    var b = total
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
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(Surface1, Bg))).border(1.dp, GlassBorder, shape)) {
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
                val cur = Prefs.display
                Box(Modifier.size(40.dp).tap(onClick = Prefs::cycleDisplay).clip(RoundedCornerShape(12.dp))
                    .background(if (cur != Currency.USD) Brand else Bg).border(1.dp, GlassBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text(if (cur == Currency.VES) "Bs" else symbol(cur), color = if (cur != Currency.USD) Color.White else Muted, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(10.dp))
                RoundIcon(if (Prefs.balanceVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, t("kt_toggle_balance"), Prefs::toggleBalance,
                    tint = Muted, size = 40.dp, bg = Bg)
            }
            Spacer(Modifier.height(14.dp))
            AutoText(shown(total * anim.value), MaterialTheme.typography.displaySmall, Ink)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("≈ ${secondary(total)}", Modifier.clip(RoundedCornerShape(12.dp)).background(Glass).border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1)
                if (Prefs.balanceVisible && d.transactions.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text((if (up) "↗ " else "↘ ") + "%.1f%%".format(kotlin.math.abs(trend)),
                        Modifier.clip(CircleShape).background(lineColor.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = lineColor)
                }
            }
        }
    }
}

/** Text con reducción automática de tamaño para que el saldo nunca se corte. */
@Composable
fun AutoText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) } // solo se achica: el conteo animado no debe reiniciarlo
    Text(text, modifier, style = style.copy(fontSize = style.fontSize * scale), color = color, maxLines = 1, softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && scale > 0.5f) scale *= 0.9f })
}

// ------------------------------------------------------------------------------------------------ Conversor (calculadora)

/** Conversor de moneda de la web (CurrencyConverter.tsx): vía bolívares, con las tasas BCV. */
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
                    Text(t("kt_bcv_rate").uppercase(ES), style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Text("↗ LIVE", Modifier.clip(CircleShape).background(Income.copy(alpha = 0.12f)).border(1.dp, Income.copy(alpha = 0.3f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = RateUsd)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caps(t("amountToConvert"), Modifier.weight(1f), color = Ink)
                CurrencyPill(from) { from = it }
            }
            androidx.compose.foundation.text.BasicTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = Ink),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Brand),
                decorationBox = { inner -> Box { if (amount.isEmpty()) Text(if (from == Currency.VES) num(a) else "1",
                    style = MaterialTheme.typography.headlineMedium, color = Muted.copy(alpha = 0.5f)); inner() } })
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RoundIcon(Icons.Outlined.SwapVert, t("kt_swap"), { val f = from; from = to; to = f }, tint = Muted, size = 40.dp, bg = Bg)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caps(t("convertedAmount"), Modifier.weight(1f), color = Brand)
                CurrencyPill(to) { to = it }
            }
            Text(if (r.usd > 0) num(result) else "—", style = MaterialTheme.typography.headlineMedium, color = Brand,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun CurrencyPill(c: Currency, onPick: (Currency) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.tap(onClick = { open = true }).clip(CircleShape).background(Bg).border(1.dp, GlassBorder, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (c == Currency.VES) "Bs" else symbol(c), color = if (c == Currency.VES) RateEur else RateUsd,
                style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp)); Text(c.name, color = Ink, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(open, { open = false }, Modifier.background(Surface2)) {
            Currency.entries.forEach { o -> DropdownMenuItem(text = { Text(o.name, color = if (o == c) Brand else Ink, fontWeight = FontWeight.Black) },
                onClick = { onPick(o); open = false }) }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Billeteras y estructura de gastos

@Composable
fun WalletCard(a: Account, onClick: () -> Unit) {
    GlassCard(Modifier.width(150.dp), onClick = onClick, radius = 20.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(accountIcon(a.icon), null, tint = Ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.weight(1f))
                Text(a.currency.name, Modifier.clip(RoundedCornerShape(8.dp)).background(Glass).padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Spacer(Modifier.height(14.dp))
            Text(if (Prefs.balanceVisible) num(a.balance) else HIDDEN, style = MaterialTheme.typography.titleMedium,
                color = if (a.balance < 0) Expense else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        drawArc(catColor(c), startA, (a - 1.5f).coerceAtLeast(0.5f), false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st),
                            style = Stroke(st))
                        startA += a
                    }
                }
                Caps(t("kt_top_expense"), color = Muted.copy(alpha = 0.5f))
            }
            cats.take(4).forEach { (c, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Glass).border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Bubble(icon(c.icon), catColor(c), 40.dp); Spacer(Modifier.width(12.dp))
                    Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(shown(v), style = MaterialTheme.typography.labelLarge, color = Ink)
                        Text("%.1f%%".format(v / total * 100), style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Ajustes (popup)

/** Ajustes de la web (SettingsModal): apariencia y tasa de cambio. */
@Composable
fun SettingsSheet(nav: Nav, onDismiss: () -> Unit) {
    val d = Store.data
    val scope = rememberCoroutineScope()
    var manual by remember { mutableStateOf(d.rates.manual) }
    var usd by remember { mutableStateOf(if (d.rates.usd > 0) num(d.rates.usd) else "") }
    var eur by remember { mutableStateOf(if (d.rates.eur > 0) num(d.rates.eur) else "") }
    var busy by remember { mutableStateOf(false) }
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding().verticalScrollable()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("settings"), style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Outlined.Close, t("close"), onDismiss, tint = Muted, size = 40.dp)
            }
            Section(t("appearance"))
            THEMES.chunked(2).forEach { row ->
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { p ->
                        val on = Prefs.theme.id == p.id
                        Box(Modifier.weight(1f).height(44.dp).tap(onClick = { Prefs.applyTheme(p) }).clip(RoundedCornerShape(14.dp))
                            .background(if (on) Brand else Bg).border(1.dp, GlassBorder, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(p.brand)); Spacer(Modifier.width(8.dp))
                                Text(p.label, color = if (on) Color.White else Ink, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            Section(t("exchangeRate"))
            Text(t("kt_rate_source_desc"), style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(12.dp))
            RateOption(Icons.Outlined.Public, t("officialRate"), !manual, Income) { manual = false }
            if (d.rates.usd > 0) Row(Modifier.padding(start = 76.dp, bottom = 8.dp)) {
                Text("USD: ", color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(num(d.rates.usd), color = RateUsd, style = MaterialTheme.typography.labelMedium)
                Text("   EUR: ", color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(num(d.rates.eur), color = RateEur, style = MaterialTheme.typography.labelMedium)
                Text("  · ${ago(d.rates.updatedAt)}", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            RateOption(Icons.Outlined.Lock, t("manualRate"), manual, Color(0xFFC084FC)) { manual = true }
            if (manual) {
                Spacer(Modifier.height(8.dp))
                Stepper("Bs / USD", usd) { usd = it }
                Spacer(Modifier.height(8.dp))
                Stepper("Bs / EUR", eur) { eur = it }
            }
            Spacer(Modifier.height(20.dp))
            BrandButton(t("update"), Modifier.fillMaxWidth(), Icons.Outlined.Save, enabled = !busy && (!manual || parseAmount(usd) != null)) {
                if (manual) {
                    Store.setRates(Rates(parseAmount(usd)!!, parseAmount(eur) ?: 0.0, nowIso(), true)); nav.snack(t("kt_rate_saved")); onDismiss()
                } else {
                    busy = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { Store.setRates(Store.data.rates.copy(manual = false)); Store.refreshRates() } }
                        busy = false; nav.snack(r.fold({ "BCV: ${num(it.usd)} Bs/$" }, { t("kt_offline") }))
                    }
                }
            }
        }
    }
}

@Composable
private fun RateOption(ic: ImageVector, title: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).tap(onClick = onClick).clip(shape).background(Bg)
        .border(if (selected) 2.dp else 1.dp, if (selected) Brand else GlassBorder, shape).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Bubble(ic, tint, 48.dp); Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = Brand)
    }
}

/** Tasa manual con botones − / + (TASA ACTUAL de la web). */
@Composable
private fun Stepper(label: String, value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    fun step(dv: Double) { onChange(num(((parseAmount(value) ?: 0.0) + dv).coerceAtLeast(0.0))) }
    Row(Modifier.fillMaxWidth().clip(shape).background(Bg).border(1.dp, GlassBorder, shape).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
