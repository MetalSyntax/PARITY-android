package com.metalsyntax.paritykt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.asImageBitmap
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

/**
 * Categoría como campo desplegable (la tira horizontal casi no se leía con tantas): muestra la elegida y abre una lista
 * con buscador (sin importar tildes ni mayúsculas). Planificado y Sobres.
 */
@Composable
fun CategoryPicker(label: String, cats: List<Category>, selected: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val cur = cats.firstOrNull { it.id == selected } ?: category(selected)
    Row(Modifier.fillMaxWidth().tap(onClick = { open = true }).clip(RoundedCornerShape(16.dp)).background(Bg).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Bubble(icon(cur.icon), catColor(cur), 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(ES), style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
            Text(cur.name, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ExpandMore, null, tint = Muted)
    }
    if (open) {
        var q by remember { mutableStateOf("") }
        fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
        val found = cats.filter { norm(it.name).contains(norm(q.trim())) }
        TitledSheet(t("selectCategory"), onDismiss = { open = false }, scroll = false) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text(t("kt_search_category")) }, singleLine = true,
                shape = RoundedCornerShape(16.dp), colors = fieldColors(), leadingIcon = { Icon(Icons.Outlined.Search, null) })
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(found, key = { it.id }) { c ->
                    ListRow(c.name, leading = { Bubble(icon(c.icon), catColor(c), 36.dp) }, color = if (c.id == selected) catColor(c).copy(alpha = 0.18f) else Bg,
                        trailingSlot = { if (c.id == selected) Icon(Icons.Outlined.CheckCircle, null, tint = Brand) }) { onPick(c.id); open = false }
                }
                if (found.isEmpty()) item { Text(t("kt_no_results"), Modifier.padding(16.dp), color = Muted) }
            }
        }
    }
}

/**
 * Vista detallada al tocar una transacción (TransactionDetailModal.tsx de la web): categoría y fecha, monto con signo,
 * equivalente en la moneda de visualización, nota, tipo, comisión, billetera y, si trae, la factura; Editar / Listo.
 */
@Composable
fun TxDetail(tx0: Transaction, onEdit: (Transaction) -> Unit, onClose: () -> Unit) {
    val d = Store.data
    val tx = d.transactions.firstOrNull { it.id == tx0.id } ?: tx0 // la versión al día (tras editar o sincronizar)
    val transfer = tx.type == TxType.TRANSFER
    val c = category(tx.category)
    val receipt = remember(tx.id, tx.updatedAt) {
        tx.extra.optString("receipt").takeIf { it.isNotBlank() }?.let { s ->
            runCatching { android.util.Base64.decode(s.substringAfter("base64,"), android.util.Base64.DEFAULT) }.getOrNull()
                ?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    }
    var tab by remember { mutableStateOf(0) }
    val date = runCatching { LocalDate.parse(tx.date.take(10)).format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES)) }.getOrDefault(tx.date)
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScrollable(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Bubble(icon(if (transfer) "swap" else c.icon), if (transfer) Color(0xFF818CF8) else catColor(c), 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (transfer) "${d.account(tx.accountId)?.name ?: "?"} → ${d.account(tx.toAccountId)?.name ?: "?"}" else c.name,
                        style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(date, style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                RoundIcon(Icons.Outlined.Close, t("close"), onClose, tint = Muted, size = 40.dp, bg = Bg)
            }
            if (receipt != null) Segmented(listOf(0 to t("summary"), 1 to t("receipt")), tab, icons = { if (it == 0) Icons.Outlined.Info else Icons.Outlined.Receipt }) { tab = it }
            if (tab == 1 && receipt != null) {
                androidx.compose.foundation.Image(receipt, t("receipt"), Modifier.fillMaxWidth().heightIn(max = 480.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            } else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Caps(t("amount"))
                    Spacer(Modifier.height(6.dp))
                    val sign = when (tx.type) { TxType.INCOME -> "+"; TxType.EXPENSE -> "-"; else -> "" }
                    AutoText(sign + money(tx.amount, tx.originalCurrency), MaterialTheme.typography.displaySmall, if (tx.type == TxType.INCOME) Income else Ink)
                    val disp = Prefs.display
                    if (disp != tx.originalCurrency && !(disp.usdLike && tx.originalCurrency.usdLike))
                        Text("≈ ${money(inDisplay(tx.normalizedAmountUSD, disp), disp)}", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                HorizontalDivider(color = Glass)
                if (tx.note.isNotBlank()) Column {
                    Caps(t("notePlaceholder").replace("...", "").replace("…", ""))
                    Spacer(Modifier.height(4.dp))
                    Text(tx.note, style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
                DetailLine(t("type")) { Pill(typeLabel(tx.type), when (tx.type) { TxType.INCOME -> Income; TxType.EXPENSE -> Expense; else -> RateEur }) }
                if (!transfer) DetailLine(t("wallet")) { Text(d.account(tx.accountId)?.name ?: "—", style = MaterialTheme.typography.titleSmall, color = Ink) }
                tx.fee?.takeIf { it > 0 }?.let { f -> DetailLine(t("commissions")) { Text(money(f, tx.originalCurrency), style = MaterialTheme.typography.titleSmall, color = Expense) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("edit"), Icons.Outlined.Edit, modifier = Modifier.weight(1f).height(52.dp), bg = Bg) { onEdit(tx) }
                BrandButton(t("done"), Modifier.weight(1f).height(52.dp), onClick = onClose)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: @Composable () -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Caps(label, Modifier.weight(1f)); value()
}
