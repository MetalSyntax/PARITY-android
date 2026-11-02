package com.metalsyntax.paritykt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metalsyntax.paritykt.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Fila de transacción de la web (TransactionItem.tsx): monto en la moneda de visualización y «≈» en la otra. */
@Composable
fun TxRow(tx: Transaction, onOpen: (Transaction) -> Unit) {
    val d = Store.data
    val c = category(tx.category)
    val transfer = tx.type == TxType.TRANSFER
    val color = when (tx.type) { TxType.EXPENSE -> Expense; TxType.INCOME -> Income; else -> Ink }
    val sign = when (tx.type) { TxType.EXPENSE -> "-"; TxType.INCOME -> "+"; else -> "" }
    val title = if (transfer) "${d.account(tx.accountId)?.name ?: "?"} → ${d.account(tx.toAccountId)?.name ?: "?"}" else tx.note.ifBlank { c.name }
    val date = runCatching { LocalDate.parse(tx.date.take(10)).format(DateTimeFormatter.ofPattern("d/M/yyyy")) }.getOrDefault("")
    ListRow(title, subtitle = "${tx.originalCurrency} • ${(d.account(tx.accountId)?.name ?: "").uppercase(ES)} • $date" +
        if ((tx.fee ?: 0.0) > 0) "\n${t("commissions")}: ${num(tx.fee!!)} ${symbol(tx.originalCurrency)}" else "",
        icon = icon(if (transfer) "swap" else c.icon), tint = if (transfer) Color(0xFF818CF8) else catColor(c),
        trailing = if (Prefs.balanceVisible) sign + money(inDisplay(tx.normalizedAmountUSD), Prefs.display) else HIDDEN, trailingColor = color,
        trailingSub = "≈ ${secondary(tx.normalizedAmountUSD)}") { onOpen(tx) }
}

private val ALL = "ALL"

@Composable
fun TransactionsScreen(nav: Nav) {
    val d = Store.data
    var period by remember { mutableStateOf(ALL) } // ALL o YYYY-MM
    var type by remember { mutableStateOf<TxType?>(null) }
    var wallet by remember { mutableStateOf(ALL) }
    var cat by remember { mutableStateOf(ALL) }
    var query by remember { mutableStateOf("") }
    val months = remember(d.transactions) { d.transactions.map { it.date.take(7) }.distinct().sortedDescending() }
    val list = d.transactions.filter {
        (period == ALL || it.date.startsWith(period)) && (type == null || it.type == type) && (wallet == ALL || it.accountId == wallet || it.toAccountId == wallet) &&
            (cat == ALL || it.category == cat) &&
            (query.isBlank() || it.note.contains(query, true) || category(it.category).name.contains(query, true) || d.account(it.accountId)?.name?.contains(query, true) == true)
    }.sortedByDescending { it.date }
    val active = listOf(query.isNotBlank(), type != null, wallet != ALL, period != ALL, cat != ALL).count { it }
    val fmt = DateTimeFormatter.ofPattern("MMMM yyyy", ES)

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically) {
                DisplayCurrencyButton()
                PeriodMenu(period, months, fmt) { period = it }
            }
            ViewHeader(t("transactions"), "${t("fullHistory")} • ${list.size}", nav.back)
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SearchField(query, { query = it }, t("searchTransactions"))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterButton(d.account(wallet)?.name ?: t("wallet"), Icons.Outlined.AccountBalanceWallet, wallet != ALL,
                        listOf(ALL to t("allWallets")) + d.accounts.map { it.id to it.name }, wallet, Modifier.weight(1f)) { wallet = it }
                    FilterButton(type?.let { typeLabel(it) } ?: t("type"), Icons.Outlined.SwapHoriz, type != null,
                        listOf(null to t("all"), TxType.INCOME to typeLabel(TxType.INCOME), TxType.EXPENSE to typeLabel(TxType.EXPENSE),
                            TxType.TRANSFER to typeLabel(TxType.TRANSFER)), type, Modifier.weight(1f)) { type = it }
                    FilterButton(if (cat == ALL) t("category") else category(cat).name, Icons.Outlined.PieChart, cat != ALL,
                        listOf(ALL to t("allCategories")) + CATEGORIES.map { it.id to it.name }, cat, Modifier.weight(1f)) { cat = it }
                }
                if (active > 0) LinkText("${t("clearFilters")} ($active)", modifier = Modifier.padding(top = 6.dp)) {
                    query = ""; type = null; wallet = ALL; period = ALL; cat = ALL
                }
            }
        }
        if (list.isEmpty()) item { Empty(Icons.Outlined.ReceiptLong, t("noTransactionsFound"), t("kt_try_other_filters")) }
        list.groupBy { it.date.take(10) }.forEach { (day, txs) ->
            item(key = "h$day") {
                Text(dayLabel(day).uppercase(ES), Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall,
                    color = Muted, letterSpacing = 2.sp)
            }
            items(txs, key = { it.id }) { Box(Modifier.padding(horizontal = 16.dp)) { TxRow(it, nav.openTx) } }
        }
    }
}

fun typeLabel(x: TxType) = when (x) { TxType.INCOME -> t("income"); TxType.EXPENSE -> t("expenses"); TxType.TRANSFER -> t("transfer") }

@Composable
private fun PeriodMenu(period: String, months: List<String>, fmt: DateTimeFormatter, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun label(m: String) = if (m == ALL) t("allPeriods") else runCatching { YearMonth.parse(m).format(fmt).replaceFirstChar { it.uppercase() } }.getOrDefault(m)
    Box {
        Row(Modifier.height(44.dp).tap(onClick = { open = true }).clip(RoundedCornerShape(22.dp)).background(Surface1)
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = Brand, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(label(period), color = Ink, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.ExpandMore, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }, Modifier.background(Surface2).heightIn(max = 380.dp)) {
            (listOf(ALL) + months).forEach { m -> DropdownMenuItem(text = { Text(label(m), color = if (m == period) Brand else Muted, fontWeight = FontWeight.Black) },
                onClick = { onPick(m); open = false }) }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Editor

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
        Confirm(t("kt_first_wallet"), t("kt_first_wallet_desc"), t("done"), onDismiss = onDone, onConfirm = onDone)
        return
    }
    val typeColor = when (type) { TxType.EXPENSE -> Expense; TxType.INCOME -> Income; else -> Brand }
    FormSheet(if (initial == null) t("kt_new_tx") else t("kt_edit_tx"), onDone, saveEnabled = ok, onSave = {
        val time = if (initial != null && initial.date.take(10) == date.toString()) initial.date else LocalDateTime.of(date, java.time.LocalTime.now().withNano(0)).toString()
        Store.saveTx((initial ?: Transaction(amount = 0.0, originalCurrency = acc!!.currency, exchangeRate = 0.0, normalizedAmountUSD = 0.0, type = type,
            category = cat, accountId = acc.id, date = time)).copy(
            amount = value!!, originalCurrency = acc!!.currency, type = type, accountId = acc.id, date = time, note = note.trim(),
            category = if (type == TxType.TRANSFER) "transfer" else cat, toAccountId = if (type == TxType.TRANSFER) toId else null,
            fee = if (type == TxType.TRANSFER) parseAmount(fee) else null))
        onDone()
    }, onDelete = initial?.let { { Store.deleteTx(it.id); onDone() } }) {
        Segmented(listOf(TxType.EXPENSE to t("expense"), TxType.INCOME to t("kt_income_one"), TxType.TRANSFER to t("kt_transfer_short")), type,
            colorOf = { when (it) { TxType.EXPENSE -> Expense; TxType.INCOME -> Income; else -> Brand } }) {
            type = it; if (it == TxType.INCOME && category(cat).income.not()) cat = "income"; if (it == TxType.EXPENSE && category(cat).income) cat = "food"
        }
        // monto grande, como el teclado de AddTransaction de la web
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Caps(t("amount"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(acc?.currency?.let(::symbol) ?: "", style = MaterialTheme.typography.headlineMedium, color = Muted)
                Spacer(Modifier.width(6.dp))
                androidx.compose.foundation.text.BasicTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    Modifier.widthIn(min = 60.dp, max = 260.dp).width(IntrinsicSize.Min), singleLine = true,
                    textStyle = MaterialTheme.typography.displaySmall.copy(color = typeColor, textAlign = TextAlign.Center),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(typeColor),
                    decorationBox = { inner -> Box(contentAlignment = Alignment.Center) {
                        if (amount.isEmpty()) Text("0", style = MaterialTheme.typography.displaySmall, color = Muted.copy(alpha = 0.4f)); inner() } })
            }
            if (acc != null && acc.currency != Currency.USD && value != null && d.rates.usd > 0)
                Text("≈ ${money(d.rates.toUsd(value, acc.currency))}", style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        Picker(if (type == TxType.TRANSFER) t("from") else t("wallet"), d.accounts.map { it.id to "${it.name} (${it.currency})" }, accountId) { accountId = it }
        if (type == TxType.TRANSFER) {
            Picker(t("to"), d.accounts.filter { it.id != accountId }.map { it.id to "${it.name} (${it.currency})" }, toId) { toId = it }
            Field(fee, { fee = it }, t("kt_fee_optional"), number = true)
        } else {
            Caps(t("category"))
            CategoryStrip(CATEGORIES.filter { it.id != "transfer" && it.income == (type == TxType.INCOME) }, cat) { cat = it }
        }
        DateField(t("date"), date) { date = it }
        Field(note, { note = it }, t("note"))
    }
}

/** Categorías como fichas desplazables con su color (grilla de categorías de AddTransaction). */
@Composable
fun CategoryStrip(cats: List<Category>, selected: String, onPick: (String) -> Unit) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = cats.indexOfFirst { it.id == selected }.coerceAtLeast(0))
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cats, key = { it.id }) { c ->
            val on = c.id == selected
            val col = catColor(c)
            Column(Modifier.width(76.dp).tap(onClick = { onPick(c.id) }).clip(RoundedCornerShape(16.dp))
                .background(if (on) col.copy(alpha = 0.22f) else Bg)
                .padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon(c.icon), null, tint = col, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(6.dp))
                Text(c.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp), color = if (on) Ink else Muted,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
