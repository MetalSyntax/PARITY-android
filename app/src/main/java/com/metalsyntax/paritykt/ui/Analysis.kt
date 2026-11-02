package com.metalsyntax.paritykt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metalsyntax.paritykt.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val monthFmt get() = DateTimeFormatter.ofPattern("MMMM yyyy", ES)
private val shortFmt get() = DateTimeFormatter.ofPattern("MMM", ES)

/** Análisis como la web: flujo neto, ingresos vs gastos (6 meses), top gastos, ingresos por fuente, fugas y próximos pagos. */
@Composable
private fun ViewHeaderBox(nav: Nav) = ViewHeader(t("analysis"), t("financialOverview"), nav.back)

@Composable
fun AnalysisScreen(nav: Nav) = Column {
    val d = Store.data
    var month by remember { mutableStateOf(YearMonth.now()) }
    val a = d.analysis(month)
    ViewHeaderBox(nav)
    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, null, tint = Ink) }
                Text(month.format(monthFmt).replaceFirstChar { it.uppercase() }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Ink,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) { Icon(Icons.Outlined.ChevronRight, null, tint = if (month < YearMonth.now()) Ink else Muted.copy(alpha = 0.3f)) }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Caps(t("netCashFlow"))
                    AutoText((if (a.net >= 0) "+" else "") + shown(a.net), MaterialTheme.typography.displaySmall, if (a.net >= 0) Ink else Expense)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Column(Modifier.weight(1f)) { Caps(t("income")); Text(shown(a.income), color = Income, fontWeight = FontWeight.Black) }
                        Column(Modifier.weight(1f)) { Caps(t("expenses")); Text(shown(a.expense), color = Expense, fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
        item {
            Section(t("incomeVsExpenses"))
            GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { IncomeExpenseBars(d.monthlyTotals(month)) } }
        }
        item {
            Section(t("topSpendingCategories"))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (a.topExpenses.isEmpty()) Text(t("noData"), Modifier.padding(vertical = 8.dp), color = Muted)
                    a.topExpenses.forEach { (c, v) -> Progress(c.name, v, a.expense, "${money(v)} · ${pct(v, a.expense)}", catColor(c)) }
                }
            }
        }
        if (a.incomeByCategory.isNotEmpty()) item {
            Section(t("incomeSources"))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    a.incomeByCategory.forEach { (c, v) -> Progress(c.name, v, a.income, "${money(v)} · ${pct(v, a.income)}", catColor(c)) }
                }
            }
        }
        item { Section(t("leakDetector")) }
        if (a.leaks.isEmpty()) item {
            Row2(Icons.Outlined.Verified, t("noLeaksDetected"), t("healthySpendingDesc"), tint = Income)
        } else items(a.leaks) { l ->
            Row2(if (l.impulse) Icons.Outlined.WaterDrop else Icons.Outlined.Warning, "${if (l.impulse) t("minorLeakage") else t("overspending")} · ${l.category.name}",
                if (l.impulse) "${l.count} ${t("smallExpensesWarning")}" else "${"%.0f".format(l.share * 100)}% ${t("ofIncomeGoesHere")}",
                money(l.amount), Expense, tint = if (l.impulse) Warning else Expense)
        }
        item { Section(t("upcomingSubscriptions"), t("kt_see_all")) { nav.go(Route.PLAN) } }
        val subs = d.scheduled.sortedBy { it.date }
        if (subs.isEmpty()) item { Text(t("noSubscriptions"), Modifier.padding(4.dp), color = Muted) }
        items(subs.take(5), key = { it.id }) { s ->
            val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(s.date)) }.getOrDefault(0L)
            Row2(icon(category(s.category).icon), s.name, when { days < 0 -> "${-days} ${t("daysAgo")}"; days == 0L -> t("today"); else -> "$days ${t("daysLeft")}" },
                money(s.amount, s.currency), if (s.type == TxType.INCOME) Income else Expense,
                tint = when { days < 0 -> Expense; days <= 7 -> Warning; else -> Muted }, onClick = { nav.go(Route.PLAN) })
        }
    }
}


@Composable
fun IncomeExpenseBars(data: List<Triple<YearMonth, Double, Double>>) {
    val max = data.maxOf { maxOf(it.second, it.third) }.takeIf { it > 0 } ?: 1.0
    val desc = data.joinToString("; ") { "${it.first.format(shortFmt)}: ${t("income")} ${money(it.second)}, ${t("expenses")} ${money(it.third)}" }
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = desc }) {
        val slot = size.width / data.size
        val bw = slot * 0.28f
        data.forEachIndexed { i, (_, inc, exp) ->
            val x = i * slot + slot / 2
            listOf(inc to Income, exp to Expense).forEachIndexed { j, (v, c) ->
                val h = (v / max).toFloat() * size.height
                drawRoundRect(c, Offset(x - bw + j * bw + (if (j == 0) -2f else 2f), size.height - h), Size(bw, h), CornerRadius(6f, 6f))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        data.forEach { Text(it.first.format(shortFmt), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Legend(t("income"), Income); Legend(t("expenses"), Expense)
    }
}

@Composable
private fun Legend(label: String, c: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Surface(Modifier.size(8.dp), shape = CircleShape, color = c) {}
    Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
}
