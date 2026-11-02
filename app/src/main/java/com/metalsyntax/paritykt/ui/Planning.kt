package com.metalsyntax.paritykt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metalsyntax.paritykt.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ------------------------------------------------------------------------------------------------ Planificado

@Composable
fun ScheduledScreen(snack: (String) -> Unit) {
    val d = Store.data
    var edit by remember { mutableStateOf<ScheduledPayment?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = d.scheduled.sortedBy { it.date }
    val monthTotal = list.filter { it.date.startsWith(YearMonth.now().toString()) && it.type == TxType.EXPENSE }.sumOf { Store.usd(it.amount, it.currency) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            item { Text("Pagos e ingresos que se repiten. Los automáticos se registran solos al vencer.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (list.isEmpty()) item { Empty(Icons.Outlined.EventRepeat, "Nada planificado", "Agrega alquiler, servicios o tu sueldo para verlos venir.", "Agregar") { creating = true } }
            else item { Section("Por pagar este mes: ${money(monthTotal)}") }
            items(list, key = { it.id }) { s ->
                val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(s.date)) }.getOrDefault(0)
                val due = when { days < 0 -> "Vencido hace ${-days} d"; days == 0L -> "Vence hoy"; days == 1L -> "Mañana"; else -> "En $days días" }
                Row2(icon(category(s.category).icon), s.name, "$due · ${s.frequency.label}" + if (s.autoPost) " · automático" else "",
                    money(s.amount, s.currency), if (days < 0) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
                    onClick = { edit = s }, extra = {
                    TextButton(onClick = {
                        if (Store.postScheduled(s, s.accountId ?: d.accounts.firstOrNull()?.id)) snack("Registrado: ${s.name}") else snack("Crea una billetera primero")
                    }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (s.type == TxType.INCOME) "Cobrar" else "Pagar") }
                })
            }
        }
        ExtendedFloatingActionButton(onClick = { creating = true }, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Planificar") })
    }
    if (creating || edit != null) ScheduledEditor(edit) { edit = null; creating = false }
}

@Composable
private fun ScheduledEditor(initial: ScheduledPayment?, onDone: () -> Unit) {
    val d = Store.data
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(initial?.amount?.let(::num) ?: "") }
    var accountId by remember { mutableStateOf(initial?.accountId ?: d.accounts.firstOrNull()?.id) }
    var currency by remember { mutableStateOf(initial?.currency ?: Currency.USD) }
    var freq by remember { mutableStateOf(initial?.frequency ?: Frequency.MONTHLY) }
    var cat by remember { mutableStateOf(initial?.category ?: "bills") }
    var date by remember { mutableStateOf(initial?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()) }
    var auto by remember { mutableStateOf(initial?.autoPost ?: false) }
    val value = parseAmount(amount)
    val acc = d.account(accountId)
    FormSheet(if (initial == null) "Planificar" else "Editar planificado", onDone, saveEnabled = name.isNotBlank() && value != null && value > 0, onSave = {
        Store.saveScheduled((initial ?: ScheduledPayment(name = "", amount = 0.0, currency = Currency.USD, date = "", frequency = freq)).copy(
            name = name.trim(), type = type, amount = value!!, currency = acc?.currency ?: currency, frequency = freq, category = cat,
            date = date.toString(), accountId = acc?.id, autoPost = auto && acc != null))
        onDone()
    }, onDelete = initial?.let { { Store.deleteScheduled(it.id); onDone() } }) {
        Segmented(listOf(TxType.EXPENSE to "Pago", TxType.INCOME to "Ingreso"), type) { type = it; cat = if (it == TxType.INCOME) "income" else "bills" }
        Field(name, { name = it }, "Nombre (ej. Alquiler)")
        Field(amount, { amount = it }, "Monto", number = true, suffix = symbol(acc?.currency ?: currency))
        if (d.accounts.isEmpty()) Picker("Moneda", Currency.entries.map { it to it.name }, currency) { currency = it }
        else Picker("Billetera", d.accounts.map { it.id to "${it.name} (${it.currency})" }, accountId) { accountId = it }
        Picker("Frecuencia", Frequency.entries.map { it to it.label }, freq) { freq = it }
        Picker("Categoría", CATEGORIES.filter { it.id != "transfer" && it.income == (type == TxType.INCOME) }.map { it.id to it.name }, cat) { cat = it }
        DateField("Próxima fecha", date) { date = it }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Registrar automáticamente")
                Text("Al vencer se crea el movimiento en la billetera", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = auto, onCheckedChange = { auto = it }, enabled = acc != null)
        }
    }
}

// ------------------------------------------------------------------------------------------------ Presupuesto y metas

@Composable
fun BudgetGoalsScreen() {
    var tab by remember { mutableIntStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Presupuesto") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Metas") })
        }
        if (tab == 0) Budgets() else Goals()
    }
}

@Composable
private fun Budgets() {
    val d = Store.data
    var edit by remember { mutableStateOf<Budget?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = d.budgets.filter { it.month == null || it.month == YearMonth.now().toString() }
    val limit = list.sumOf { it.limit }
    val spent = list.sumOf { d.spentUsd(it.categoryId) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            if (list.isEmpty()) item { Empty(Icons.Outlined.PieChart, "Sin presupuesto", "Pon un límite mensual por categoría y mira cuánto te queda.", "Crear presupuesto") { creating = true } }
            else item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Te queda este mes", style = MaterialTheme.typography.labelLarge)
                        Text(money(limit - spent), style = MaterialTheme.typography.headlineMedium,
                            color = if (spent > limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Progress("Gastado", spent, limit, "${money(spent)} de ${money(limit)}")
                    }
                }
                Section("Por categoría")
            }
            items(list.sortedByDescending { d.spentUsd(it.categoryId) / it.limit }, key = { it.categoryId }) { b ->
                val s = d.spentUsd(b.categoryId)
                Surface(onClick = { edit = b }, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Bubble(icon(category(b.categoryId).icon)); Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) { Progress(category(b.categoryId).name, s, b.limit, "${money(s)} / ${money(b.limit)}") }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { creating = true }, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Límite") })
    }
    if (creating || edit != null) {
        val initial = edit
        var cat by remember(initial) { mutableStateOf(initial?.categoryId ?: (CATEGORIES.firstOrNull { it.id !in list.map { b -> b.categoryId } && !it.income && it.id != "transfer" } ?: CATEGORIES.first()).id) }
        var amount by remember(initial) { mutableStateOf(initial?.limit?.let(::num) ?: "") }
        val v = parseAmount(amount)
        FormSheet(if (initial == null) "Nuevo límite" else "Editar límite", { edit = null; creating = false }, saveEnabled = v != null && v > 0, onSave = {
            if (initial != null && initial.categoryId != cat) Store.deleteBudget(initial.categoryId)
            Store.saveBudget(Budget(cat, v!!)); edit = null; creating = false
        }, onDelete = initial?.let { { Store.deleteBudget(it.categoryId); edit = null } }) {
            Picker("Categoría", CATEGORIES.filter { !it.income && it.id != "transfer" }.map { it.id to it.name }, cat) { cat = it }
            Field(amount, { amount = it }, "Límite mensual", number = true, suffix = "USD")
        }
    }
}

@Composable
private fun Goals() {
    val d = Store.data
    var edit by remember { mutableStateOf<Goal?>(null) }
    var creating by remember { mutableStateOf(false) }
    var contribute by remember { mutableStateOf<Goal?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (d.goals.isEmpty()) item { Empty(Icons.Outlined.Flag, "Sin metas", "Ahorra para algo concreto y mira cuánto falta.", "Crear meta") { creating = true } }
            items(d.goals.sortedBy { it.completed }, key = { it.id }) { g ->
                OutlinedCard(onClick = { edit = g }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Bubble(if (g.completed) Icons.Outlined.TaskAlt else Icons.Outlined.Flag); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name, style = MaterialTheme.typography.titleMedium)
                                Text("Para el ${dayLabel(g.deadline)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!g.completed) FilledTonalButton(onClick = { contribute = g }) { Text("Aportar") }
                        }
                        Progress(if (g.completed) "¡Lograda!" else "Faltan ${money((g.targetAmount - g.savedAmount).coerceAtLeast(0.0))}",
                            g.savedAmount, g.targetAmount, "${money(g.savedAmount)} de ${money(g.targetAmount)}")
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { creating = true }, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Meta") })
    }
    if (creating || edit != null) {
        val initial = edit
        var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
        var target by remember(initial) { mutableStateOf(initial?.targetAmount?.let(::num) ?: "") }
        var saved by remember(initial) { mutableStateOf(initial?.savedAmount?.let(::num) ?: "0") }
        var date by remember(initial) { mutableStateOf(initial?.deadline?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now().plusMonths(6)) }
        val t = parseAmount(target)
        FormSheet(if (initial == null) "Nueva meta" else "Editar meta", { edit = null; creating = false }, saveEnabled = name.isNotBlank() && t != null && t > 0, onSave = {
            Store.saveGoal((initial ?: Goal(name = "", targetAmount = 0.0, deadline = "")).copy(name = name.trim(), targetAmount = t!!,
                savedAmount = parseAmount(saved) ?: 0.0, deadline = date.toString())); edit = null; creating = false
        }, onDelete = initial?.let { { Store.deleteGoal(it.id); edit = null } }) {
            Field(name, { name = it }, "Nombre (ej. Fondo de emergencia)")
            Field(target, { target = it }, "Objetivo", number = true, suffix = "USD")
            Field(saved, { saved = it }, "Ahorrado hasta hoy", number = true, suffix = "USD")
            DateField("Fecha límite", date) { date = it }
        }
    }
    contribute?.let { g ->
        var amount by remember(g) { mutableStateOf("") }
        val v = parseAmount(amount)
        AlertDialog(onDismissRequest = { contribute = null }, title = { Text("Aportar a ${g.name}") },
            text = { Field(amount, { amount = it }, "Monto", number = true, suffix = "USD") },
            confirmButton = { TextButton(enabled = v != null && v > 0, onClick = { Store.saveGoal(g.copy(savedAmount = g.savedAmount + v!!)); contribute = null }) { Text("Aportar") } },
            dismissButton = { TextButton(onClick = { contribute = null }) { Text("Cancelar") } })
    }
}
