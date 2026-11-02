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

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", ES)
private val shortFmt = DateTimeFormatter.ofPattern("MMM", ES)

/** Análisis como la web: flujo neto, ingresos vs gastos (6 meses), top gastos, ingresos por fuente, fugas y próximos pagos. */
@Composable
fun AnalysisScreen(go: (String) -> Unit) {
    val d = Store.data
    var month by remember { mutableStateOf(YearMonth.now()) }
    val a = d.analysis(month)
    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Mes anterior") }
                Text(month.format(monthFmt).replaceFirstChar { it.uppercase() }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) { Icon(Icons.Outlined.ChevronRight, "Mes siguiente") }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Caps("Flujo neto")
                    Text((if (a.net >= 0) "+" else "−") + money(kotlin.math.abs(a.net)), style = MaterialTheme.typography.displaySmall,
                        color = if (a.net >= 0) Color.White else Expense)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Column(Modifier.weight(1f)) { Caps("Ingresos"); Text(money(a.income), color = Income, fontWeight = FontWeight.Black) }
                        Column(Modifier.weight(1f)) { Caps("Gastos"); Text(money(a.expense), color = Expense, fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
        item {
            Section("Ingresos vs gastos")
            GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { IncomeExpenseBars(d.monthlyTotals(month)) } }
        }
        item {
            Section("Dónde se va tu dinero")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (a.topExpenses.isEmpty()) Text("Sin gastos este mes.", Modifier.padding(vertical = 8.dp), color = Muted)
                    a.topExpenses.forEach { (c, v) -> Progress(c.name, v, a.expense, "${money(v)} · ${pct(v, a.expense)}") }
                }
            }
        }
        if (a.incomeByCategory.isNotEmpty()) item {
            Section("Fuentes de ingreso")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    a.incomeByCategory.forEach { (c, v) -> Progress(c.name, v, a.income, "${money(v)} · ${pct(v, a.income)}") }
                }
            }
        }
        item { Section("Detector de fugas") }
        if (a.leaks.isEmpty()) item {
            Row2(Icons.Outlined.Verified, "Sin fugas detectadas", "Tus gastos se ven sanos este mes.", tint = Income)
        } else items(a.leaks) { l ->
            Row2(if (l.impulse) Icons.Outlined.WaterDrop else Icons.Outlined.Warning, "${if (l.impulse) "Fuga hormiga" else "Gasto excesivo"} · ${l.category}",
                l.description, money(l.amount), Expense, tint = if (l.impulse) Color(0xFFF59E0B) else Expense)
        }
        item { Section("Próximos pagos", "Ver todo") { go("plan") } }
        val subs = d.scheduled.sortedBy { it.date }
        if (subs.isEmpty()) item { Text("No hay pagos planificados.", Modifier.padding(4.dp), color = Muted) }
        items(subs.take(5), key = { it.id }) { s ->
            val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(s.date)) }.getOrDefault(0L)
            Row2(icon(category(s.category).icon), s.name, when { days < 0 -> "Hace ${-days} días"; days == 0L -> "Hoy"; else -> "En $days días" },
                money(s.amount, s.currency), if (s.type == TxType.INCOME) Income else Expense,
                tint = when { days < 0 -> Expense; days <= 7 -> Color(0xFFF59E0B); else -> Muted }, onClick = { go("plan") })
        }
    }
}

private fun pct(v: Double, total: Double) = if (total > 0) "${"%.0f".format(v / total * 100)} %" else "—"

@Composable
private fun IncomeExpenseBars(data: List<Triple<YearMonth, Double, Double>>) {
    val max = data.maxOf { maxOf(it.second, it.third) }.takeIf { it > 0 } ?: 1.0
    val desc = data.joinToString("; ") { "${it.first.format(shortFmt)}: ingresos ${money(it.second)}, gastos ${money(it.third)}" }
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
        Legend("Ingresos", Income); Legend("Gastos", Expense)
    }
}

@Composable
private fun Legend(label: String, c: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Surface(Modifier.size(8.dp), shape = CircleShape, color = c) {}
    Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
}
