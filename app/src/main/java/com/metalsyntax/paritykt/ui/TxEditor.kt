package com.metalsyntax.paritykt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import com.metalsyntax.paritykt.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

private enum class Focus { AMOUNT, FEE, PERCENT }

/**
 * Agregar / editar transacción a pantalla completa, como AddTransaction.tsx de la web: tipo, monto grande con teclado propio
 * (y calculadora), moneda, comisión fija y %, nota, billetera / categoría / fecha y Guardar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxEditor(initial: Transaction?, type0: TxType, onDone: () -> Unit) {
    val d = Store.data
    if (d.accounts.isEmpty()) {
        Confirm(t("kt_first_wallet"), t("kt_first_wallet_desc"), t("done"), onDismiss = onDone, onConfirm = onDone)
        return
    }
    var type by remember { mutableStateOf(initial?.type ?: type0) }
    var accountId by remember { mutableStateOf(initial?.accountId ?: d.accounts.first().id) }
    var toId by remember { mutableStateOf(initial?.toAccountId ?: d.accounts.getOrNull(1)?.id) }
    var currency by remember { mutableStateOf(initial?.originalCurrency ?: d.account(accountId)!!.currency) }
    var amount by remember { mutableStateOf(initial?.amount?.let { trimNum(it) } ?: "") }
    var fee by remember { mutableStateOf(initial?.fee?.takeIf { it > 0 }?.let { trimNum(it) } ?: "") }
    var percent by remember { mutableStateOf("") }
    var focus by remember { mutableStateOf(Focus.AMOUNT) }
    var calc by remember { mutableStateOf(false) }
    // total y comisiones: oculto hasta tocar su tarjeta (abierto si la transacción ya trae comisión)
    var showFees by remember { mutableStateOf((initial?.fee ?: 0.0) > 0) }
    var cat by remember { mutableStateOf(initial?.category ?: if (type0 == TxType.INCOME) "income" else "food") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableStateOf(initial?.date?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now()) }
    var pick by remember { mutableStateOf<String?>(null) } // "wallet" | "to" | "category" | "date"
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // factura digitalizada: imagen (`receipt`, data URL como la web) + datos leídos por el OCR (`invoiceData`)
    var receipt by remember { mutableStateOf(initial?.extra?.optString("receipt")?.takeIf { it.isNotBlank() }) }
    var invoice by remember { mutableStateOf(InvoiceData.from(initial?.extra?.optJSONObject("invoiceData"))) }
    var review by remember { mutableStateOf<ScannedReceipt?>(null) }
    var attachMenu by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val actions = rememberReceiptActions(onDictated = { s -> note = (note.trim() + " " + s).trim() }) { r ->
        if (r == null) toast(ctx, t("kt_scan_failed")) else review = r
    }

    val value = if (Calc.isExpression(amount)) Calc.eval(amount) else parseAmount(amount)
    val feeTotal = (parseAmount(fee) ?: 0.0) + (value ?: 0.0) * (parseAmount(percent) ?: 0.0) / 100
    val hasFee = type != TxType.INCOME
    val ok = value != null && value > 0 && (type != TxType.TRANSFER || (toId != null && toId != accountId))
    val typeColor = when (type) { TxType.EXPENSE -> Expense; TxType.INCOME -> Income; else -> Brand }
    val acc = d.account(accountId)!!

    fun key(k: String) {
        fun edit(s: String) = when (k) { "⌫" -> s.dropLast(1); "." -> if (s.takeLastWhile { it.isDigit() || it == '.' }.contains('.')) s else (if (s.isEmpty()) "0." else "$s.")
            else -> if (k in "+−×÷" && (s.isEmpty() || s.last() in "+−×÷.")) s else s + k }
        when (focus) { Focus.AMOUNT -> amount = edit(amount); Focus.FEE -> fee = edit(fee); Focus.PERCENT -> percent = edit(percent) }
    }
    fun save() {
        val v = value ?: return
        val time = if (initial != null && initial.date.take(10) == date.toString()) initial.date else LocalDateTime.of(date, java.time.LocalTime.now().withNano(0)).toString()
        Store.saveTx((initial ?: Transaction(amount = 0.0, originalCurrency = currency, exchangeRate = 0.0, normalizedAmountUSD = 0.0, type = type,
            category = cat, accountId = accountId, date = time)).copy(
            amount = v, originalCurrency = currency, type = type, accountId = accountId, date = time, note = note.trim(),
            category = if (type == TxType.TRANSFER) "transfer" else cat, toAccountId = if (type == TxType.TRANSFER) toId else null,
            fee = if (hasFee && feeTotal > 0) feeTotal else null,
            extra = JSONObject().also { r -> initial?.extra?.let { e -> e.keys().forEach { k -> r.put(k, e.get(k)) } } }.apply {
                if (receipt != null) put("receipt", receipt) else remove("receipt")
                invoice?.let { put("invoiceData", it.toJson()) } ?: remove("invoiceData")
            }))
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        onDone()
    }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // el teclado de Android se superpone (no redimensiona ni empuja la pantalla)
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect { (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) }
        Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(horizontal = 16.dp)) {
            // encabezado
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Outlined.ArrowBack, t("back"), onDone)
                Spacer(Modifier.width(14.dp))
                Text(if (initial == null) t("add_transaction") else t("kt_edit_tx"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = Ink, maxLines = 1)
                if (initial != null) RoundIcon(Icons.Outlined.Delete, t("delete"), { confirmDelete = true }, tint = Expense, bg = Expense.copy(alpha = 0.12f))
            }
            // tipo: Gasto (punto rojo) / Ingresos / Transferencias
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1).padding(4.dp)) {
                listOf(TxType.EXPENSE to t("expense"), TxType.INCOME to t("income"), TxType.TRANSFER to t("transfer")).forEach { (k, label) ->
                    val on = k == type
                    val bg by animateColorAsState(if (on) Bg else Color.Transparent, label = "type")
                    Row(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(14.dp)).background(bg).tap(onClick = {
                        type = k; if (k == TxType.INCOME && !category(cat).income) cat = "income"; if (k == TxType.EXPENSE && category(cat).income) cat = "food"
                    }), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        if (on) { Box(Modifier.size(6.dp).clip(CircleShape).background(when (k) { TxType.EXPENSE -> Expense; TxType.INCOME -> Income; else -> Brand })); Spacer(Modifier.width(6.dp)) }
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Ink else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val sc = androidx.compose.foundation.rememberScrollState()
            Box(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(sc)) {
                // monto grande
                Column(Modifier.fillMaxWidth().padding(top = 14.dp).tap(onClick = { focus = Focus.AMOUNT }), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (currency == Currency.VES) "Bs" else symbol(currency), style = MaterialTheme.typography.headlineMedium, color = Muted)
                        Spacer(Modifier.width(14.dp))
                        AutoText(amount.ifEmpty { "0" }, MaterialTheme.typography.displayMedium, if (focus == Focus.AMOUNT) typeColor else Ink)
                    }
                    val usd = value?.let { Store.usd(it, currency) } ?: 0.0
                    Text(if (Calc.isExpression(amount)) "= ${value?.let(::num) ?: "…"}" else if (currency == Currency.VES) "≈ ${money(usd)}" else "≈ ${money(inDisplay(usd, Currency.VES), Currency.VES)}",
                        style = MaterialTheme.typography.labelMedium, color = Muted)
                }
                // moneda de la transacción
                Row(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp).clip(CircleShape).background(Surface1).padding(4.dp)) {
                    Currency.entries.forEach { c ->
                        val on = c == currency
                        Text(c.name, Modifier.clip(CircleShape).background(if (on) Brand else Color.Transparent).tap(onClick = { currency = c }).padding(horizontal = 14.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else Muted)
                    }
                }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), placeholder = { Text(t("notePlaceholder")) }, singleLine = true,
                    shape = RoundedCornerShape(18.dp), colors = fieldColors(Surface1), leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                    trailingIcon = {
                        Row {
                            IconButton(actions.dictate) { Icon(Icons.Outlined.Mic, t("kt_dictate"), tint = Brand) }
                            Box {
                                IconButton({ attachMenu = true }) { Icon(Icons.Outlined.PhotoCamera, t("kt_scan_receipt"), tint = Brand) }
                                DropdownMenu(attachMenu, { attachMenu = false }, Modifier.background(Surface2)) {
                                    DropdownMenuItem({ Text(t("kt_take_photo"), color = Ink) }, { attachMenu = false; actions.camera() },
                                        leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null, tint = Brand) })
                                    DropdownMenuItem({ Text(t("kt_from_gallery"), color = Ink) }, { attachMenu = false; actions.gallery() },
                                        leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null, tint = Brand) })
                                }
                            }
                        }
                    })
                AnimatedVisibility(actions.scanning) { Box(Modifier.padding(top = 10.dp)) { ScanningBanner() } }
                Spacer(Modifier.height(10.dp))
                // deslizador horizontal como la web: billetera (o desde ⇄ hasta) / categoría / fecha / comisiones, con el nombre completo
                Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    PickChip(if (type == TxType.TRANSFER) t("from") else t("wallet"), acc.name, { AccountTile(acc.icon, 40.dp) }) { pick = "wallet" }
                    if (type == TxType.TRANSFER) {
                        val to = d.account(toId)
                        RoundIcon(Icons.Outlined.SwapHoriz, t("kt_swap"), {
                            toId?.let { t0 -> val from = accountId; accountId = t0; toId = from; d.account(t0)?.let { currency = it.currency } }
                        }, tint = Muted, size = 36.dp)
                        PickChip(t("to"), to?.name ?: "—", { AccountTile(to?.icon ?: "wallet", 40.dp) }) { pick = "to" }
                    } else {
                        val c = category(cat)
                        PickChip(t("category"), c.name, { Bubble(icon(c.icon), catColor(c), 40.dp) }) { pick = "category" }
                    }
                    PickChip(t("date"), if (date == LocalDate.now()) t("today") else date.format(DateTimeFormatter.ofPattern("d MMM", ES)),
                        { Bubble(Icons.Outlined.CalendarMonth, Brand, 40.dp) }) { pick = "date" }
                    if (receipt != null) {
                        val thumb = remember(receipt) { ReceiptOcr.decode(receipt) }
                        PickChip(t("receipt"), invoice?.let { t("kt_items_found", it.items.size) } ?: t("kt_attached"), {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Bg), contentAlignment = Alignment.Center) {
                                thumb?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                                    ?: Icon(Icons.Outlined.Receipt, null, tint = Brand)
                            }
                        }) { review = ScannedReceipt(receipt!!, invoice ?: InvoiceData()) }
                    }
                    if (hasFee) PickChip(t("commissions"), if (feeTotal > 0) "${num(feeTotal)} ${currency.name}" else "—",
                        { Bubble(if (showFees) Icons.Outlined.ExpandLess else Icons.Outlined.Percent, Warning, 40.dp) }, selected = showFees) {
                        showFees = !showFees; if (!showFees) focus = Focus.AMOUNT
                    }
                }
                // total y comisiones (fijo / %): solo si se abrió con su tarjeta
                AnimatedVisibility(hasFee && showFees) {
                    Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Caps(t("total"))
                            Text("${num((value ?: 0.0) + feeTotal)} ${currency.name}", style = MaterialTheme.typography.titleSmall, color = Brand)
                        }
                        Column(Modifier.weight(1.4f)) {
                            Caps(t("commissions"))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FeeBox(t("fixed"), fee, focus == Focus.FEE, Modifier.weight(1f)) { focus = Focus.FEE }
                                FeeBox("%", percent, focus == Focus.PERCENT, Modifier.weight(1f)) { focus = Focus.PERCENT }
                            }
                        }
                    }
                }
            }
            // aviso de que hay más campos abajo (nota, billetera, categoría, fecha) cuando el teclado los tapa
            androidx.compose.animation.AnimatedVisibility(sc.canScrollForward, Modifier.align(Alignment.BottomCenter),
                enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                Box(Modifier.fillMaxWidth().height(56.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Bg))),
                    contentAlignment = Alignment.BottomCenter) {
                    val bounce by androidx.compose.animation.core.rememberInfiniteTransition(label = "hint").animateFloat(0f, 5f,
                        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "y")
                    Row(Modifier.offset(y = (-bounce).dp).clip(CircleShape).background(Surface2).tap(onClick = { scope.launch { sc.animateScrollTo(sc.maxValue) } })
                        .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Brand, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(t("kt_scroll_more"), style = MaterialTheme.typography.labelSmall, color = Ink)
                    }
                }
            }
            }
            // calculadora (operaciones) y teclado numérico
            AnimatedVisibility(calc) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("+", "−", "×", "÷").forEach { op -> Key(op, Modifier.weight(1f), small = true, color = Brand) { key(op) } }
                    Key("=", Modifier.weight(1f), small = true, color = Brand) { value?.let { amount = trimNum(it) } }
                }
            }
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { k -> Key(k, Modifier.weight(1f)) { key(k) } } }
                }
            }
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).tap(onClick = { calc = !calc }).clip(RoundedCornerShape(18.dp)).background(if (calc) Brand else Surface1), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Calculate, t("kt_calculator"), tint = if (calc) Color.White else Muted)
                }
                BrandButton(t("save").uppercase(ES), Modifier.weight(1f).height(56.dp), Icons.Outlined.Check, ok) { save() } // no ::save: Compose memoriza la referencia sin claves y guardaba el primer monto tecleado
            }
        }
        // dentro del Dialog: si se crean fuera, Android las dibuja detrás de esta ventana
        when (pick) {
            "wallet", "to" -> TitledSheet(t("selectWallet"), onDismiss = { pick = null }) {
                Spacer(Modifier.height(10.dp))
                d.accounts.filter { pick == "wallet" || it.id != accountId }.forEach { a ->
                    ListRow(a.name, subtitle = money(a.balance, a.currency), leading = { AccountTile(a.icon) }, color = Bg,
                        trailingSlot = { if ((if (pick == "wallet") accountId else toId) == a.id) Icon(Icons.Outlined.CheckCircle, null, tint = Brand) }) {
                        if (pick == "wallet") { accountId = a.id; currency = a.currency; if (toId == a.id) toId = d.accounts.firstOrNull { it.id != a.id }?.id } else toId = a.id
                        pick = null
                    }
                }
            }
            "category" -> TitledSheet(t("selectCategory"), onDismiss = { pick = null }, scroll = false) {
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CATEGORIES.filter { it.id != "transfer" && it.income == (type == TxType.INCOME) }, key = { it.id }) { c ->
                        val on = c.id == cat
                        Column(Modifier.tap(onClick = { cat = c.id; pick = null }).clip(RoundedCornerShape(16.dp)).background(if (on) catColor(c).copy(alpha = 0.22f) else Bg)
                            .padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon(c.icon), null, tint = catColor(c), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(c.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp), color = if (on) Ink else Muted,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            "date" -> {
                val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
                DatePickerDialog(onDismissRequest = { pick = null },
                    confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }; pick = null }) { Text(t("done"), color = Brand) } },
                    dismissButton = { TextButton(onClick = { pick = null }) { Text(t("cancel"), color = Muted) } }) { DatePicker(st) }
            }
        }
        review?.let { sc ->
            ReceiptReview(sc, currency, onRemove = if (sc.dataUrl == receipt) { { receipt = null; invoice = null; review = null } } else null, onUse = { inv ->
                receipt = sc.dataUrl; invoice = inv; review = null
                inv.currency?.let { currency = it }
                inv.total?.takeIf { it > 0 }?.let { amount = trimNum(Math.round(it * 100) / 100.0); focus = Focus.AMOUNT }
                if (note.isBlank()) inv.merchant?.let { note = it }
                inv.invoiceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { !it.isAfter(LocalDate.now()) }?.let { date = it }
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            }) { review = null }
        }
        if (confirmDelete && initial != null) Confirm(t("kt_delete_q"), t("kt_cannot_undo"), t("delete"), danger = true, onDismiss = { confirmDelete = false }) {
            Store.deleteTx(initial.id); confirmDelete = false; onDone()
        }
    }

}

/** Número sin ceros sobrantes para editar en el teclado (12.5, no 12,50). */
private fun trimNum(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
private fun Key(k: String, modifier: Modifier, small: Boolean = false, color: Color = Ink, onClick: () -> Unit) {
    Box(modifier.height(if (small) 44.dp else 58.dp).tap(onClick = onClick).clip(RoundedCornerShape(18.dp)).background(Surface1), contentAlignment = Alignment.Center) {
        if (k == "⌫") Icon(Icons.Outlined.Backspace, t("kt_erase"), tint = Ink)
        else Text(k, style = if (small) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, color = color)
    }
}

@Composable
private fun FeeBox(label: String, value: String, focused: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.tap(onClick = onClick).clip(RoundedCornerShape(12.dp)).background(if (focused) Brand.copy(alpha = 0.18f) else Bg).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label.uppercase(ES), style = MaterialTheme.typography.labelSmall, color = Muted)
        Text(value.ifEmpty { "0" }, style = MaterialTheme.typography.titleSmall, color = if (focused) Brand else Ink, maxLines = 1)
    }
}

@Composable
private fun PickChip(label: String, value: String, leading: @Composable () -> Unit, selected: Boolean = false, onClick: () -> Unit) {
    // tarjeta del deslizador: mínimo 150 dp como la web (min-w-[140px]) y hasta 220 dp para que el nombre se lea
    Row(Modifier.height(64.dp).widthIn(min = 150.dp, max = 220.dp).tap(onClick = onClick).clip(RoundedCornerShape(18.dp))
        .background(if (selected) Warning.copy(alpha = 0.15f) else Surface1).padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        leading(); Spacer(Modifier.width(10.dp))
        Column {
            Text(label.uppercase(ES), style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
