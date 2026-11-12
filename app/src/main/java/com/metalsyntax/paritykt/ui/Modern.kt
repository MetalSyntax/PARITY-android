package com.metalsyntax.paritykt.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metalsyntax.paritykt.data.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/*
 * Parity Modern (flavor «modern»): rediseño de docs/REDISENO_DASHBOARD.md.
 * Inicio = una pregunta («¿cuánto puedo gastar con tranquilidad?»), 3 acciones, «Para hoy» y 5 recientes.
 * Todo lo demás vive a un toque: desglose, conversor, centro «Plan» y extras opcionales de Personalizar inicio.
 */

// ================================================================================================ Inicio

@Composable
fun ModernHome(nav: Nav) {
    val d = Store.data
    val today = LocalDate.now()
    val safe = d.safeToSpend(today)
    val cards = d.todayCards(today, Metrics.noSpend)
    val streak = d.streak(today, Metrics.noSpend)
    val recent = d.transactions.sortedByDescending { it.date }.take(5)
    var breakdown by remember { mutableStateOf(false) }
    var converter by remember { mutableStateOf(false) }
    val month = d.monthTx()
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item(key = "header") { ModernHeader(d, nav, streak) { converter = true } }
        item(key = "hero") { Hero(d, safe) { breakdown = true } }
        item(key = "actions") { CoreActions(nav) }
        item(key = "todayTitle") { Section(t("kt_for_today")) }
        items(cards, key = { "c" + it.kind.name + (it.scheduled?.id ?: it.budget?.key ?: it.goal?.id ?: "") }) { TodayRow(it, nav) }
        item(key = "recentTitle") { Section(t("kt_recent"), t("kt_see_all")) { nav.go(Route.TX) } }
        if (recent.isEmpty()) item(key = "first") { FirstRecord(nav) }
        items(recent, key = { it.id }) { SwipeTx(it, nav) }
        // extras que la persona activó en Personalizar inicio (apagados por defecto)
        Prefs.widgets.filter { it in MODERN_EXTRAS && it !in Prefs.hidden }.forEach { homeWidget(it, d, nav, month) }
    }
    if (breakdown) Breakdown(safe, nav) { breakdown = false }
    if (converter) TitledSheet(t("currencyConverter"), onDismiss = { converter = false }) { Spacer(Modifier.height(12.dp)); Converter(d.rates) }
}

@Composable
private fun ModernHeader(d: AppData, nav: Nav, streak: Int, onConverter: () -> Unit) {
    val refresh = rememberRateRefresh(nav)
    val rot = remember { Animatable(0f) }
    LaunchedEffect(refresh.busy) { while (refresh.busy) rot.animateTo(rot.value + 360f, tween(700)); rot.snapTo(0f) }
    Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(d.profile.name, 44.dp, Modifier.tap(onClick = { nav.go(Route.PROFILE) }))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("kt_hello", d.profile.name.ifBlank { "Parity" }.substringBefore(' ')), style = MaterialTheme.typography.titleMedium, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (streak >= 2) Text("🔥 " + t("kt_streak", streak), style = MaterialTheme.typography.labelSmall, color = Warning)
            else Text(t("kt_home_sub"), style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
        }
        // tocar = actualizar tasas; mantener = conversor
        Row(Modifier.tap(onLongClick = onConverter, onClick = refresh.run).clip(RoundedCornerShape(14.dp)).background(Surface1)
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                if (d.rates.usd > 0) {
                    RateTicker("USD ${num(d.rates.usd)}", RateUsd)
                    if (d.rates.eur > 0) RateTicker("EUR ${num(d.rates.eur)}", RateEur)
                } else Text(t("kt_no_rate"), color = Muted, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.Refresh, t("btn_update"), tint = Muted.copy(alpha = 0.6f), modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = rot.value })
        }
    }
}

/** Héroe: un solo número de decisión. Tocar abre el desglose (saldo, reservado, billeteras). */
@Composable
private fun Hero(d: AppData, s: SafeToSpend, onBreakdown: () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(s.available) { anim.snapTo(0f); anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val low = s.available < 0
    val hist = remember(d.transactions, s.balance) { balanceHistory(d) }
    GlassCard(Modifier.fillMaxWidth(), onClick = onBreakdown) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caps(t("kt_safe_to_spend"), Modifier.weight(1f))
                DisplayCurrencyButton(36.dp)
                Spacer(Modifier.width(8.dp))
                RoundIcon(if (Prefs.balanceVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, t("kt_toggle_balance"), Prefs::toggleBalance,
                    tint = Muted, size = 36.dp, bg = Bg)
            }
            Spacer(Modifier.height(10.dp))
            AutoText(shown(s.available * anim.value), MaterialTheme.typography.displaySmall, if (low) Warning else Ink)
            Text("≈ ${secondary(s.available)}", style = MaterialTheme.typography.labelMedium, color = Muted)
            if (low) Text(t("kt_safe_low"), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = Warning)
            if (hist.size > 1 && d.transactions.isNotEmpty()) {
                val up = hist.last() >= hist.first()
                Canvas(Modifier.fillMaxWidth().height(36.dp).padding(top = 12.dp)) {
                    val min = hist.min(); val span = (hist.max() - min).takeIf { it > 0 } ?: 1.0
                    val path = Path()
                    hist.forEachIndexed { i, v ->
                        val x = size.width * i / (hist.size - 1) * anim.value; val y = size.height * (1 - ((v - min) / span).toFloat())
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, (if (up) Income else Color(0xFFF43F5E)).copy(alpha = 0.6f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${t("kt_balance")} ${shown(s.balance)} · ${t("kt_reserved")} ${shown(s.reserved)}", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.Info, t("kt_breakdown"), tint = Muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Las 3 acciones núcleo (en vez de 8 accesos rápidos). */
@Composable
private fun CoreActions(nav: Nav) = Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    ActionTile(Icons.Outlined.Add, t("expense"), true, Modifier.weight(1f)) { nav.newTx(TxType.EXPENSE) }
    ActionTile(Icons.Outlined.PhotoCamera, t("kt_receipt_short"), false, Modifier.weight(1f), nav.scan)
    ActionTile(Icons.Outlined.SwapHoriz, t("kt_transfer_short"), false, Modifier.weight(1f)) { nav.newTx(TxType.TRANSFER) }
}

@Composable
private fun ActionTile(ic: ImageVector, label: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(78.dp).tap(onClick = onClick).clip(RoundedCornerShape(20.dp)).background(if (filled) Brand else Surface1).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(ic, null, tint = if (filled) Color.White else Brand, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (filled) Color.White else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Una tarjeta de «Para hoy»: punto de color, frase en tono amable y, si aplica, una acción. */
@Composable
private fun TodayRow(c: TodayCard, nav: Nav) {
    val haptic = LocalHapticFeedback.current
    val today = LocalDate.now()
    var dot = Brand; var title = ""; var sub: String? = null; var action: String? = null; var onAction: () -> Unit = {}
    when (c.kind) {
        CardKind.DUE -> {
            val s = c.scheduled!!; val dd = c.date!!
            val whenTxt = when { dd.isBefore(today) -> t("kt_overdue"); dd == today -> t("today").lowercase(); else -> t("kt_tomorrow").lowercase() }
            dot = if (dd.isBefore(today)) Expense else Warning
            title = t("kt_due_title", s.name, whenTxt); sub = money(s.amount, s.currency)
            action = if (s.type == TxType.INCOME) t("kt_collect") else t("kt_register")
            onAction = { if (Store.postScheduled(s)) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nav.snack(t("kt_payment_registered")) } else nav.go(Route.PLAN) }
        }
        CardKind.BUDGET -> {
            val b = c.budget!!; val cat = category(b.categoryId)
            if (c.value >= 0) { dot = Warning; title = t("kt_budget_left", shown(c.value), cat.name); sub = t("kt_budget_left_sub") }
            else { dot = Expense; title = t("kt_budget_over", cat.name, shown(-c.value)); sub = t("kt_budget_adjust") }
            action = t("kt_see"); onAction = { nav.go(Route.BUDGET) }
        }
        CardKind.GOAL -> {
            val g = c.goal!!
            dot = GOAL; title = t("kt_goal_close", g.name, "%.0f%%".format(c.value * 100)); sub = t("kt_goal_close_sub", money(g.targetAmount - g.savedAmount))
            action = t("kt_contribute"); onAction = { nav.go(Route.GOALS) }
        }
        CardKind.NO_SPEND -> {
            dot = Income; title = t("kt_no_spend_q"); sub = t("kt_no_spend_sub"); action = t("kt_mark")
            onAction = { Metrics.markNoSpend(today.toString()); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); nav.snack(t("kt_no_spend_done")) }
        }
        CardKind.WEEK -> {
            dot = Income; title = t("kt_week_spent", shown(c.value))
            sub = c.change?.let { ch -> val p = "%.0f%%".format(abs(ch) * 100); if (ch <= 0) t("kt_week_less", p) else t("kt_week_more", p) } ?: t("kt_all_good")
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Surface1).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2) }
        }
        action?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, Modifier.tap(onClick = onAction).clip(RoundedCornerShape(12.dp)).background(dot.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = dot)
        }
    }
}

@Composable
private fun FirstRecord(nav: Nav) = Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Surface1).padding(18.dp),
    horizontalAlignment = Alignment.CenterHorizontally) {
    Text(t("kt_first_record"), style = MaterialTheme.typography.bodyMedium, color = Ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    BrandButton(t("kt_start"), icon = Icons.Outlined.Add) { nav.newTx(TxType.EXPENSE) }
}

@Composable
private fun SumLine(label: String, value: String, color: Color) = Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Ink)
    Text(value, style = MaterialTheme.typography.titleMedium, color = color)
}

/** Desglose del héroe: de dónde sale el «Disponible para gastar». */
@Composable
private fun Breakdown(s: SafeToSpend, nav: Nav, onDismiss: () -> Unit) {
    val d = Store.data
    TitledSheet(t("kt_safe_to_spend"), t("kt_safe_formula"), onDismiss) {
        Spacer(Modifier.height(12.dp))
        SumLine(t("kt_balance"), shown(s.balance), Ink)
        d.accounts.forEach { a ->
            ListRow(a.name, subtitle = a.currency.name, leading = { AccountTile(a.icon, 40.dp) },
                trailing = if (Prefs.balanceVisible) money(a.balance, a.currency) else HIDDEN, color = Bg)
        }
        Spacer(Modifier.height(10.dp))
        SumLine(t("kt_reserved"), "− " + shown(s.reserved), Warning)
        s.upcoming.forEach { (p, date) ->
            ListRow(p.name, subtitle = shortDate(date.toString()), icon = Icons.Outlined.CalendarMonth, tint = Warning, trailing = money(p.amount, p.currency), color = Bg)
        }
        s.goals.forEach { (g, m) ->
            ListRow(g.name, subtitle = t("kt_goal_monthly"), icon = Icons.Outlined.EmojiEvents, tint = GOAL, trailing = shown(m), color = Bg)
        }
        if (s.upcoming.isEmpty() && s.goals.isEmpty()) Text(t("kt_nothing_reserved"), style = MaterialTheme.typography.bodySmall, color = Muted)
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Glass)
        SumLine(t("kt_safe_to_spend"), shown(s.available), if (s.available < 0) Warning else Brand)
        Spacer(Modifier.height(14.dp))
        GhostButton(t("analysis"), Icons.Outlined.Insights, modifier = Modifier.fillMaxWidth().height(52.dp), bg = Bg) { onDismiss(); nav.go(Route.ANALYSIS) }
    }
}

// ================================================================================================ transacciones deslizables

/** Copia de una transacción para hoy (sin factura ni tasas guardadas: se toman las del momento). */
fun duplicateTx(tx: Transaction): Transaction = tx.copy(id = newId(), date = LocalDateTime.now().withNano(0).toString(), scheduledId = null, updatedAt = nowIso(),
    extra = JSONObject().also { r -> tx.extra.keys().forEach { k -> if (k !in setOf("receipt", "invoiceData", "euroRate", "isAutoPosted")) r.put(k, tx.extra.get(k)) } })

/** Fila con deslizar: a la izquierda borra (con Deshacer), a la derecha duplica para hoy (con Deshacer). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeTx(tx: Transaction, nav: Nav) {
    val haptic = LocalHapticFeedback.current
    var last by remember { mutableLongStateOf(0L) } // evita que un mismo gesto dispare dos veces
    val st = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        val now = System.currentTimeMillis()
        if (now - last < 700) return@rememberSwipeToDismissBoxState v == SwipeToDismissBoxValue.EndToStart
        when (v) {
            SwipeToDismissBoxValue.EndToStart -> {
                last = now; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Store.deleteTx(tx.id); nav.undo(t("kt_tx_deleted")) { Store.restoreTx(tx) }; true
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                last = now; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val copy = duplicateTx(tx); Store.saveTx(copy); nav.undo(t("kt_tx_duplicated")) { Store.deleteTx(copy.id) }; false
            }
            else -> false
        }
    })
    SwipeToDismissBox(st, backgroundContent = {
        val dir = st.dismissDirection
        val del = dir == SwipeToDismissBoxValue.EndToStart
        val c = if (del) Expense else Brand
        // el ícono crece al acercarse al umbral
        val sc by animateFloatAsState(if (st.targetValue != SwipeToDismissBoxValue.Settled) 1.25f else 0.85f, label = "swipe")
        Box(Modifier.fillMaxSize().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(if (dir == SwipeToDismissBoxValue.Settled) Color.Transparent else c.copy(alpha = 0.15f))
            .padding(horizontal = 22.dp), contentAlignment = if (del) Alignment.CenterEnd else Alignment.CenterStart) {
            if (dir != SwipeToDismissBoxValue.Settled) Icon(if (del) Icons.Outlined.Delete else Icons.Outlined.ContentCopy, null, tint = c, modifier = Modifier.scale(sc))
        }
    }) { TxRow(tx, nav.openTx) }
}

// ================================================================================================ centro «Plan»

/** Presupuesto, metas, pagos, compras y análisis en un solo lugar, con su resumen; el detalle a un toque. */
@Composable
fun PlanHub(nav: Nav) {
    val d = Store.data
    val bs = d.activeBudgets()
    val limit = bs.sumOf { it.limit }; val spent = bs.sumOf { d.spentUsd(it.categoryId) }
    val goals = d.goals.filter { !it.completed }
    val best = goals.filter { it.targetAmount > 0 }.maxByOrNull { it.savedAmount / it.targetAmount }
    val next = d.scheduled.minByOrNull { it.date }
    val pending = d.lists.sumOf { l -> l.items.count { !it.completed } }
    val monthSpent = d.monthTx().filter { it.type == TxType.EXPENSE }.sumOf { it.normalizedAmountUSD }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item { Box(Modifier.offset(x = (-16).dp).fillParentMaxWidth()) { ViewHeader(t("kt_plan_hub"), t("kt_plan_sub")) } }
        item {
            HubCard(routeIcon(Route.BUDGET), routeColor(Route.BUDGET), t("budget"),
                if (bs.isEmpty()) t("kt_hub_budget_empty") else t("kt_hub_budget", shown(spent), shown(limit)), if (limit > 0) spent / limit else null) { nav.go(Route.BUDGET) }
        }
        item {
            HubCard(routeIcon(Route.GOALS), GOAL, t("goals"),
                best?.let { t("kt_hub_goals", goals.size, it.name, pct(it.savedAmount, it.targetAmount)) } ?: t("kt_hub_goals_empty"),
                best?.let { it.savedAmount / it.targetAmount }) { nav.go(Route.GOALS) }
        }
        item {
            HubCard(routeIcon(Route.PLAN), routeColor(Route.PLAN), t("scheduled"),
                next?.let { t("kt_hub_next", it.name, shortDate(it.date)) } ?: t("kt_hub_plan_empty"), null) { nav.go(Route.PLAN) }
        }
        item {
            HubCard(routeIcon(Route.SHOPPING), routeColor(Route.SHOPPING), t("shoppingList"),
                if (d.lists.isEmpty()) t("kt_hub_shopping_empty") else t("kt_hub_shopping", d.lists.size, pending), null) { nav.go(Route.SHOPPING) }
        }
        item {
            HubCard(routeIcon(Route.ANALYSIS), routeColor(Route.ANALYSIS), t("analysis"), t("kt_hub_analysis", shown(monthSpent)), null) { nav.go(Route.ANALYSIS) }
        }
    }
}

@Composable
private fun HubCard(ic: ImageVector, tint: Color, title: String, sub: String, progress: Double?, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), onClick = onClick) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Bubble(ic, tint, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                progress?.let { Spacer(Modifier.height(8.dp)); Bar(it.toFloat().coerceIn(0f, 1f), tint) }
            }
            Spacer(Modifier.width(8.dp))
            Chevron()
        }
    }
}

// ================================================================================================ celebración

/** Meta recién lograda (Store.onGoalReached la marca); [Confetti] la celebra y la limpia. */
object Celebrate { var goal by mutableStateOf<String?>(null) }

@Composable
fun Confetti() {
    val name = Celebrate.goal ?: return
    val p = remember(name) { Animatable(0f) }
    LaunchedEffect(name) { p.animateTo(1f, tween(2400, easing = LinearEasing)); Celebrate.goal = null }
    val parts = remember(name) { List(80) { Triple(Random.nextFloat(), 0.5f + Random.nextFloat() * 0.7f, Random.nextInt(6)) } }
    val colors = listOf(Brand, Income, GOAL, RateEur, Color(0xFFF472B6), Warning)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            parts.forEachIndexed { i, (x, speed, c) ->
                val y = (p.value * speed * 1.5f - 0.15f - (i % 9) * 0.02f) * size.height
                val xx = x * size.width + sin(p.value * 10f + i) * 18.dp.toPx()
                rotate(p.value * 720f * speed + i * 37f, Offset(xx, y)) { drawRect(colors[c], Offset(xx, y), Size(7.dp.toPx(), 13.dp.toPx())) }
            }
        }
        val a = if (p.value < 0.75f) 1f else (1f - (p.value - 0.75f) / 0.25f)
        val pop = (0.6f + p.value * 4f).coerceAtMost(1f)
        Column(Modifier.align(Alignment.Center).graphicsLayer { alpha = a; scaleX = pop; scaleY = pop }.clip(RoundedCornerShape(28.dp)).background(Surface2)
            .padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", fontSize = 44.sp)
            Text(t("kt_goal_reached"), style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(name, style = MaterialTheme.typography.bodyLarge, color = Muted)
        }
    }
}
