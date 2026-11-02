package com.metalsyntax.paritykt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metalsyntax.paritykt.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ------------------------------------------------------------------------------------------------ Inicio

@Composable
fun HomeScreen(onNewTx: (TxType) -> Unit, onOpenTx: (Transaction) -> Unit, go: (String) -> Unit) {
    val d = Store.data
    val month = d.monthTx()
    val income = month.filter { it.type == TxType.INCOME }.sumOf { it.normalizedAmountUSD }
    val expense = month.filter { it.type == TxType.EXPENSE }.sumOf { it.normalizedAmountUSD }
    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp)) {
        item {
            // saldo total: tarjeta índigo como el hero de la web
            Surface(shape = MaterialTheme.shapes.extraLarge, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Indigo, androidx.compose.ui.graphics.Color(0xFF4338CA), androidx.compose.ui.graphics.Color(0xFF1E1B4B))))) {
                    Column(Modifier.padding(24.dp)) {
                        Caps("Saldo total", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f))
                        Spacer(Modifier.height(6.dp))
                        Text(money(d.totalUsd()), style = MaterialTheme.typography.displaySmall, color = androidx.compose.ui.graphics.Color.White)
                        if (d.rates.usd > 0) Text("≈ ${money(d.totalUsd() * d.rates.usd, Currency.VES)}", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RatePill(if (d.rates.usd > 0) "USD ${num(d.rates.usd)}" else "Sin tasa") { go("profile") }
                            if (d.rates.eur > 0) RatePill("EUR ${num(d.rates.eur)}") { go("profile") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            // acciones rápidas (columna ícono + texto, como los botones de la web)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Quick(Modifier.weight(1f), "Ingreso", Icons.Outlined.SouthWest, Income) { onNewTx(TxType.INCOME) }
                Quick(Modifier.weight(1f), "Gasto", Icons.Outlined.NorthEast, Expense) { onNewTx(TxType.EXPENSE) }
                Quick(Modifier.weight(1f), "Transferir", Icons.Outlined.SwapHoriz, Indigo, enabled = d.accounts.size > 1) { onNewTx(TxType.TRANSFER) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Modifier.weight(1f), "Ingresos del mes", money(income), Income)
                Stat(Modifier.weight(1f), "Gastos del mes", money(expense), Expense)
            }
        }
        if (d.accounts.isEmpty()) item {
            Empty(Icons.Outlined.AccountBalanceWallet, "Empieza con una billetera", "Crea tu cuenta de efectivo, banco o USDT para registrar movimientos.",
                "Crear billetera") { go("wallets") }
        } else item {
            Section("Billeteras", "Ver todas") { go("wallets") }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                d.accounts.sortedByDescending { d.rates.toUsd(it.balance, it.currency) }.forEach { a ->
                    GlassCard(Modifier.width(140.dp), onClick = { go("wallets") }) {
                        Column(Modifier.padding(14.dp)) {
                            Bubble(icon(a.icon))
                            Spacer(Modifier.height(10.dp))
                            Text(a.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(money(a.balance, a.currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1,
                                color = if (a.balance < 0) Expense else androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }
        }
        val upcoming = d.scheduled.filter { runCatching { !LocalDate.parse(it.date).isAfter(LocalDate.now().plusDays(7)) }.getOrDefault(false) }.sortedBy { it.date }
        if (upcoming.isNotEmpty()) {
            item { Section("Próximos 7 días", "Ver todo") { go("plan") } }
            items(upcoming.take(3)) { s ->
                Row2(icon(category(s.category).icon), s.name, dayLabel(s.date) + " · " + s.frequency.label, money(s.amount, s.currency),
                    if (s.type == TxType.INCOME) Income else Expense, onClick = { go("plan") })
            }
        }
        val budgets = d.budgets.filter { it.month == null || it.month == YearMonth.now().toString() }
        if (budgets.isNotEmpty()) {
            item {
                Section("Presupuesto del mes", "Ver todo") { go("budget") }
                GlassCard(Modifier.fillMaxWidth(), onClick = { go("budget") }) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        budgets.sortedByDescending { d.spentUsd(it.categoryId) / it.limit }.take(3).forEach { b ->
                            val spent = d.spentUsd(b.categoryId)
                            Progress(category(b.categoryId).name, spent, b.limit, "${money(spent)} / ${money(b.limit)}")
                        }
                    }
                }
            }
        }
        if (d.transactions.isNotEmpty()) {
            item { Section("Movimientos recientes", "Ver todo") { go("tx") } }
            items(d.transactions.sortedByDescending { it.date }.take(5), key = { it.id }) { t -> TxRow(t, onOpenTx) }
        }
    }
}

@Composable
private fun RatePill(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = androidx.compose.foundation.shape.CircleShape, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CurrencyExchange, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}

@Composable
private fun Quick(modifier: Modifier, label: String, ic: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color,
                  enabled: Boolean = true, onClick: () -> Unit) {
    GlassCard(modifier, onClick = if (enabled) onClick else null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Bubble(ic, if (enabled) tint else Muted)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) androidx.compose.ui.graphics.Color.White else Muted)
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    GlassCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Caps(label)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = tint, maxLines = 1)
        }
    }
}

@Composable
fun TxRow(t: Transaction, onOpen: (Transaction) -> Unit) {
    val d = Store.data
    val c = category(t.category)
    val sign = when (t.type) { TxType.EXPENSE -> "−"; TxType.INCOME -> "+"; TxType.TRANSFER -> "" }
    val color = when (t.type) { TxType.EXPENSE -> Expense; TxType.INCOME -> Income; else -> Indigo }
    val sub = if (t.type == TxType.TRANSFER) "${d.account(t.accountId)?.name ?: "?"} → ${d.account(t.toAccountId)?.name ?: "?"}"
    else listOfNotNull(c.name, d.account(t.accountId)?.name).joinToString(" · ")
    Row2(icon(if (t.type == TxType.TRANSFER) "swap" else c.icon), t.note.ifBlank { c.name }, sub, "$sign${money(t.amount, t.originalCurrency)}", color, tint = color,
        onClick = { onOpen(t) },
        extra = if (t.originalCurrency != Currency.USD && t.originalCurrency != Currency.USDT) { { Text("≈ ${money(t.normalizedAmountUSD)}", style = MaterialTheme.typography.bodySmall) } } else null)
}

// ------------------------------------------------------------------------------------------------ Transacciones

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(onOpen: (Transaction) -> Unit) {
    val d = Store.data
    var month by remember { mutableStateOf(YearMonth.now()) }
    var filter by remember { mutableStateOf<TxType?>(null) }
    var query by remember { mutableStateOf("") }
    val list = d.monthTx(month).filter { (filter == null || it.type == filter) &&
        (query.isBlank() || it.note.contains(query, true) || category(it.category).name.contains(query, true)) }.sortedByDescending { it.date }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Mes anterior") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", ES)).replaceFirstChar { it.uppercase() },
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) { Icon(Icons.Outlined.ChevronRight, "Mes siguiente") }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar por nota o categoría") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "Todos", TxType.EXPENSE to "Gastos", TxType.INCOME to "Ingresos", TxType.TRANSFER to "Transferencias").forEach { (k, t) ->
                    FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(t) })
                }
            }
        }
        if (list.isEmpty()) item { Empty(Icons.Outlined.ReceiptLong, "Sin movimientos", "No hay movimientos en este mes con ese filtro.") }
        list.groupBy { it.date.take(10) }.forEach { (day, txs) ->
            item(key = "h$day") {
                val net = txs.sumOf { when (it.type) { TxType.INCOME -> it.normalizedAmountUSD; TxType.EXPENSE -> -it.normalizedAmountUSD; else -> 0.0 } }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp)) {
                    Text(dayLabel(day), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text((if (net >= 0) "+" else "−") + money(kotlin.math.abs(net)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(txs, key = { it.id }) { TxRow(it, onOpen) }
        }
    }
}

/** Crear / editar una transacción. La moneda es la de la cuenta de origen. */
@Composable
fun TxEditor(initial: Transaction?, type0: TxType, onDone: () -> Unit) {
    val d = Store.data
    var type by remember { mutableStateOf(initial?.type ?: type0) }
    var amount by remember { mutableStateOf(initial?.amount?.let(::num) ?: "") }
    var accountId by remember { mutableStateOf(initial?.accountId ?: d.accounts.firstOrNull()?.id) }
    var toId by remember { mutableStateOf(initial?.toAccountId ?: d.accounts.getOrNull(1)?.id) }
    var cat by remember { mutableStateOf(initial?.category ?: if (type0 == TxType.INCOME) "income" else "food") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var fee by remember { mutableStateOf(initial?.fee?.let(::num) ?: "") }
    var date by remember { mutableStateOf(initial?.date?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now()) }
    val acc = d.account(accountId)
    val value = parseAmount(amount)
    val ok = acc != null && value != null && value > 0 && (type != TxType.TRANSFER || (toId != null && toId != accountId))
    if (d.accounts.isEmpty()) {
        AlertDialog(onDismissRequest = onDone, title = { Text("Primero una billetera") }, text = { Text("Crea una billetera para registrar movimientos.") },
            confirmButton = { TextButton(onClick = onDone) { Text("Entendido") } })
        return
    }
    FormSheet(if (initial == null) "Nuevo movimiento" else "Editar movimiento", onDone, saveEnabled = ok, onSave = {
        val time = if (initial != null && initial.date.take(10) == date.toString()) initial.date else LocalDateTime.of(date, java.time.LocalTime.now().withNano(0)).toString()
        Store.saveTx((initial ?: Transaction(amount = 0.0, originalCurrency = acc!!.currency, exchangeRate = 0.0, normalizedAmountUSD = 0.0, type = type,
            category = cat, accountId = acc.id, date = time)).copy(
            amount = value!!, originalCurrency = acc!!.currency, type = type, accountId = acc.id, date = time, note = note.trim(),
            category = if (type == TxType.TRANSFER) "transfer" else cat, toAccountId = if (type == TxType.TRANSFER) toId else null,
            fee = if (type == TxType.TRANSFER) parseAmount(fee) else null))
        onDone()
    }, onDelete = initial?.let { { Store.deleteTx(it.id); onDone() } }) {
        Segmented(listOf(TxType.EXPENSE to "Gasto", TxType.INCOME to "Ingreso", TxType.TRANSFER to "Transferir"), type) {
            type = it; if (it == TxType.INCOME && category(cat).income.not()) cat = "income"; if (it == TxType.EXPENSE && category(cat).income) cat = "food"
        }
        Field(amount, { amount = it }, "Monto", number = true, suffix = acc?.currency?.let(::symbol))
        if (acc != null && acc.currency != Currency.USD && value != null && d.rates.usd > 0) Text("≈ ${money(d.rates.toUsd(value, acc.currency))}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Picker(if (type == TxType.TRANSFER) "Desde" else "Billetera", d.accounts.map { it.id to "${it.name} (${it.currency})" }, accountId) { accountId = it }
        if (type == TxType.TRANSFER) {
            Picker("Hacia", d.accounts.filter { it.id != accountId }.map { it.id to "${it.name} (${it.currency})" }, toId) { toId = it }
            Field(fee, { fee = it }, "Comisión (opcional)", number = true)
        } else Picker("Categoría", CATEGORIES.filter { it.id != "transfer" && it.income == (type == TxType.INCOME) }.map { it.id to it.name }, cat) { cat = it }
        DateField("Fecha", date) { date = it }
        Field(note, { note = it }, "Nota")
    }
}
