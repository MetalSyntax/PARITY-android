package com.metalsyntax.paritykt.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metalsyntax.paritykt.Reminders
import com.metalsyntax.paritykt.data.*
import com.metalsyntax.paritykt.sync.Sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.YearMonth

// ================================================================================================ Billetera & Ingresos

/** Billetera & Ingresos de la web (WalletView.tsx): pestañas Ingresos y Billetera. */
@Composable
fun WalletsScreen(nav: Nav) {
    val d = Store.data
    var tab by remember { mutableIntStateOf(1) }
    var edit by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val keys: Set<Any> = d.accounts.map { it.id }.toSet()
    val st = rememberReorder(list) { a, b -> Store.reorderAccounts(Store.data.accounts.move(a, b) { it.id }) }
    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            ViewHeader(t("kt_wallet_income"), t("kt_wallet_income_sub"), nav.back) {
                DisplayCurrencyButton(); if (tab == 1) AddButton(t("kt_new_wallet")) { creating = true }
            }
            Box(Modifier.padding(horizontal = 16.dp)) {
                Segmented(listOf(0 to t("incomeView"), 1 to t("wallet")), tab, icons = { if (it == 0) Icons.Outlined.TrendingUp else Icons.Outlined.AccountBalanceWallet },
                    container = Surface1) { tab = it }
            }
        }
        if (tab == 0) incomeTab(d, nav)
        else {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    StatCard(t("totalBalance"), shown(d.totalUsd()), Modifier.fillMaxWidth().padding(top = 14.dp), sub = "≈ ${secondary(d.totalUsd())}")
                }
                Box(Modifier.padding(horizontal = 16.dp)) { Section("${t("yourWallets")} (${d.accounts.size})", icon = Icons.Outlined.AccountBalanceWallet) }
            }
            if (d.accounts.isEmpty()) item { Empty(Icons.Outlined.AccountBalanceWallet, t("kt_no_wallets"), t("kt_no_wallets_desc"), t("kt_create_wallet")) { creating = true } }
            items(d.accounts, key = { it.id }) { a ->
                WalletCard(a, Modifier.padding(horizontal = 16.dp).reorderItem(st, a.id), handle = { DragHandle(st, a.id, keys) }) { edit = a }
            }
        }
    }
    if (creating || edit != null) WalletEditor(edit, nav) { edit = null; creating = false }
}

/** Tarjeta de billetera de la web: franja de acento, logo, nombre, cliente, «Balance disponible», moneda y saldo. */
@Composable
private fun WalletCard(a: Account, modifier: Modifier, handle: @Composable () -> Unit, onEdit: () -> Unit) {
    val c = accountColor(a.icon)
    GlassCard(modifier.fillMaxWidth().padding(vertical = 6.dp), onClick = onEdit) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(c))
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountTile(a.icon, 52.dp); Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        a.payrollClient?.let { Text(it.uppercase(ES), style = MaterialTheme.typography.labelSmall, color = Income, letterSpacing = 1.5.sp, maxLines = 1) }
                    }
                    handle()
                }
                Spacer(Modifier.height(18.dp))
                Caps(t("availableBalance"))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (a.currency == Currency.VES) "Bs" else a.currency.name, Ink)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        AutoText(if (Prefs.balanceVisible) money(a.balance, a.currency) else HIDDEN, MaterialTheme.typography.headlineSmall, if (a.balance < 0) Expense else Ink)
                        if (a.currency != Currency.USD && a.currency != Currency.USDT) Text("≈ ${if (Prefs.balanceVisible) money(Store.usd(a.balance, a.currency)) else HIDDEN}",
                            style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
            }
        }
    }
}

/** Pestaña Ingresos: fuentes activas, ingreso del mes (realizado / pendiente), neto e historial. */
private fun androidx.compose.foundation.lazy.LazyListScope.incomeTab(d: AppData, nav: Nav) {
    val month = d.monthTx()
    val incomes = month.filter { it.type == TxType.INCOME }
    val total = incomes.sumOf { it.normalizedAmountUSD }
    val expense = month.filter { it.type == TxType.EXPENSE }.sumOf { it.normalizedAmountUSD }
    val pending = d.scheduled.filter { it.type == TxType.INCOME && it.date.startsWith(YearMonth.now().toString()) }.sumOf { Store.usd(it.amount, it.currency) }
    val sources = incomes.groupBy { it.category }.map { (id, l) -> category(id) to l.sumOf { it.normalizedAmountUSD } }.sortedByDescending { it.second }
    item {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Section("${t("activeSources")} (${sources.size})", icon = Icons.Outlined.Layers)
            if (sources.isEmpty()) EmptyNote(t("kt_no_income_month"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                sources.forEach { (c, v) ->
                    GlassCard(Modifier.width(150.dp), radius = 20.dp) {
                        Column(Modifier.padding(16.dp)) {
                            Row { Icon(icon(c.icon), null, tint = catColor(c)); Spacer(Modifier.weight(1f)); Pill(pct(v, total)) }
                            Spacer(Modifier.height(14.dp))
                            Text(shown(v), style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1)
                            Text("≈ ${secondary(v)}", style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
                            Caps(c.name, color = Muted)
                        }
                    }
                }
            }
            StatCard(t("kt_month_income"), shown(total), Modifier.fillMaxWidth().padding(top = 16.dp), valueColor = Income, sub = "≈ ${secondary(total)}") {
                Spacer(Modifier.height(12.dp))
                Row { Text(t("kt_income_received"), Modifier.weight(1f), color = Muted); Text(shown(total), color = Ink, style = MaterialTheme.typography.titleSmall) }
                Row { Text(t("kt_income_pending"), Modifier.weight(1f), color = Muted); Text(shown(pending), color = Warning, style = MaterialTheme.typography.titleSmall) }
            }
            val net = total - expense
            StatCard(t("monthlyNetFlow"), shown(net), Modifier.fillMaxWidth().padding(top = 12.dp), valueColor = if (net >= 0) Income else Expense, sub = "≈ ${secondary(net)}") {
                Spacer(Modifier.height(10.dp)); Pill(if (net >= 0) t("kt_positive_flow") else t("kt_negative_flow"), if (net >= 0) Income else Expense)
            }
            Section(t("kt_income_history"), icon = Icons.Outlined.TrendingUp)
            if (incomes.isEmpty()) EmptyNote(t("kt_no_income_month"))
        }
    }
    items(incomes.sortedByDescending { it.date }, key = { "i" + it.id }) { Box(Modifier.padding(horizontal = 16.dp)) { TxRow(it, nav.openTx) } }
}

@Composable
private fun WalletEditor(initial: Account?, nav: Nav, onDone: () -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var cur by remember(initial) { mutableStateOf(initial?.currency ?: Currency.USD) }
    var balance by remember(initial) { mutableStateOf(initial?.balance?.let(::num) ?: "0") }
    var ic by remember(initial) { mutableStateOf(initial?.icon ?: "wallet") }
    var client by remember(initial) { mutableStateOf(initial?.payrollClient ?: "") }
    val b = parseAmount(balance)
    FormSheet(if (initial == null) t("kt_new_wallet") else t("editWallet"), onDone, saveEnabled = name.isNotBlank() && b != null, onSave = {
        Store.saveAccount((initial ?: Account(name = "", currency = cur, balance = 0.0)).copy(name = name.trim(), currency = cur, balance = b!!, icon = ic,
            payrollClient = client.trim().ifEmpty { null }))
        onDone()
    }, onDelete = initial?.let { { if (Store.deleteAccount(it.id)) onDone() else nav.snack(t("kt_wallet_has_tx")) } }) {
        Field(name, { name = it }, t("kt_wallet_name_hint"))
        Caps(t("currency"))
        Segmented(Currency.entries.map { it to it.name }, cur) { cur = it }
        Field(balance, { balance = it }, if (initial == null) t("initialBalance") else t("kt_balance_adjust"), number = true, suffix = symbol(cur))
        Caps(t("kt_icon"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (ACCOUNT_ICON_KEYS + listOf(ic).filter { it !in ACCOUNT_ICON_KEYS }).forEach { k ->
                val on = ic == k
                Box(Modifier.size(56.dp).tap(onClick = { ic = k }).clip(RoundedCornerShape(16.dp)).background(if (on) Brand else Bg), contentAlignment = Alignment.Center) {
                    AccountGlyph(k, 24.dp, if (on) Color.White else accountColor(k))
                }
            }
        }
        Field(client, { client = it }, "${t("payrollClient")} (${t("kt_optional")})")
    }
}

// ================================================================================================ Lista de compras

@Composable
fun ShoppingScreen(nav: Nav) {
    val d = Store.data
    var openId by remember { mutableStateOf(d.lists.firstOrNull()?.id) }
    var naming by remember { mutableStateOf<ShoppingList?>(null) }
    var editItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    val l = d.lists.firstOrNull { it.id == openId } ?: d.lists.firstOrNull()
    val list = rememberLazyListState()
    val keys: Set<Any> = l?.items?.map { it.id }?.toSet() ?: emptySet()
    val st = rememberReorder(list) { a, b -> Store.data.lists.firstOrNull { it.id == l?.id }?.let { cur -> Store.saveList(cur.copy(items = cur.items.move(a, b) { it.id })) } }
    fun save(items: List<ShoppingItem>) = l?.let { Store.saveList(it.copy(items = items)) }
    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            ViewHeader(l?.name ?: t("shoppingList"), l?.let { "${it.items.size} ${t("kt_products")}" + (estimate(it)?.let { e -> " · ≈ ${money(e)}" } ?: "") }, nav.back) {
                DisplayCurrencyButton()
                if (l != null) RoundIcon(Icons.Outlined.Delete, t("delete"), { confirm = true }, tint = Expense)
            }
            // listas como pastillas (+ nueva, ✎ renombrar), como la web
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Outlined.Add, t("kt_new_list"), { naming = ShoppingList(name = "") }, size = 40.dp)
                d.lists.forEach { x ->
                    val on = x.id == l?.id
                    Row(Modifier.tap(onClick = { openId = x.id }).clip(CircleShape).background(if (on) Brand else Surface1).padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(x.name, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else Ink)
                        Spacer(Modifier.width(6.dp))
                        Text("${x.items.size}", style = MaterialTheme.typography.labelMedium, color = if (on) Color.White.copy(alpha = 0.8f) else Muted)
                    }
                }
                if (l != null) RoundIcon(Icons.Outlined.Edit, t("edit"), { naming = l }, tint = Muted, size = 40.dp, bg = Bg)
            }
        }
        if (l == null) item { Empty(Icons.Outlined.ShoppingCart, t("kt_no_lists"), t("kt_no_lists_desc"), t("kt_new_list")) { naming = ShoppingList(name = "") } }
        else {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(name, { name = it }, t("kt_product"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Field(qty, { qty = it }, t("kt_qty"), modifier = Modifier.weight(1f))
                            Field(price, { price = it }, t("kt_price"), number = true, modifier = Modifier.weight(1.3f), suffix = "$")
                            RoundIcon(Icons.Outlined.Add, t("add"), {
                                if (name.isNotBlank()) {
                                    save(l.items + ShoppingItem(name = name.trim(), quantity = qty.ifBlank { "1" }, price = parseAmount(price), currency = Currency.USD))
                                    name = ""; qty = "1"; price = ""
                                }
                            }, tint = Color.White, size = 52.dp, bg = if (name.isNotBlank()) Brand else Brand.copy(alpha = 0.35f))
                        }
                    }
                }
                if (l.items.any { it.completed }) LinkText(t("kt_remove_bought"), modifier = Modifier.padding(start = 16.dp)) { save(l.items.filterNot { it.completed }) }
            }
            if (l.items.isEmpty()) item { Box(Modifier.padding(16.dp)) { EmptyNote(t("kt_list_empty")) } }
            items(l.items, key = { it.id }) { i ->
                val unit = i.price?.let { Store.usd(it, i.currency ?: Currency.USD) }
                val q = i.quantity.replace(',', '.').toDoubleOrNull() ?: 1.0
                ListRow(i.name, Modifier.padding(horizontal = 16.dp).reorderItem(st, i.id), strike = i.completed, titleColor = if (i.completed) Muted else Ink,
                    color = if (i.completed) Surface1.copy(alpha = 0.5f) else Surface1, handle = { DragHandle(st, i.id, keys) },
                    leading = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (i.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (i.completed) Income else Muted,
                                modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(10.dp)); Bubble(Icons.Outlined.ShoppingCart, Color(0xFFF59E0B), 38.dp)
                        }
                    },
                    trailingSlot = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Pill("× ${i.quantity}")
                                unit?.let { Text(money(it * q), style = MaterialTheme.typography.labelLarge, color = Income); Text(money(inDisplay(it * q, Currency.VES), Currency.VES),
                                    style = MaterialTheme.typography.labelSmall, color = Muted) }
                            }
                            Spacer(Modifier.width(8.dp))
                            // editar y eliminar, como los botones de cada producto en la web
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundIcon(Icons.Outlined.Edit, t("edit"), { editItem = i }, tint = Brand, size = 32.dp, bg = Bg)
                                RoundIcon(Icons.Outlined.Delete, t("delete"), { save(l.items.filter { it.id != i.id }) }, tint = Expense, size = 32.dp, bg = Bg)
                            }
                        }
                    }) { save(l.items.map { if (it.id == i.id) it.copy(completed = !i.completed) else it }) }
            }
            if (l.items.isNotEmpty()) item { Text(t("kt_shopping_hint"), Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodySmall,
                color = Muted, textAlign = TextAlign.Center) }
        }
    }
    editItem?.let { i ->
        var n by remember(i) { mutableStateOf(i.name) }
        var q by remember(i) { mutableStateOf(i.quantity) }
        var p by remember(i) { mutableStateOf(i.price?.let(::num) ?: "") }
        FormSheet(t("edit"), { editItem = null }, saveEnabled = n.isNotBlank(), onSave = {
            save(l!!.items.map { if (it.id == i.id) it.copy(name = n.trim(), quantity = q.ifBlank { "1" }, price = parseAmount(p)) else it }); editItem = null
        }, onDelete = { save(l!!.items.filter { it.id != i.id }); editItem = null }) {
            Field(n, { n = it }, t("kt_product"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(q, { q = it }, t("kt_qty"), modifier = Modifier.weight(1f))
                Field(p, { p = it }, t("kt_price"), number = true, modifier = Modifier.weight(1.3f), suffix = "$")
            }
        }
    }
    naming?.let { x ->
        var n by remember(x) { mutableStateOf(x.name) }
        Confirm(if (x.name.isEmpty()) t("kt_new_list") else t("edit"), "", if (x.name.isEmpty()) t("kt_create") else t("save"), enabled = n.isNotBlank(),
            onDismiss = { naming = null }, content = { Field(n, { n = it }, t("kt_list_name_hint")) }) {
            val saved = x.copy(name = n.trim()); Store.saveList(saved); naming = null; openId = saved.id
        }
    }
    if (confirm && l != null) Confirm(t("kt_delete_list_q", l.name), t("kt_cannot_undo"), t("delete"), danger = true, onDismiss = { confirm = false }) {
        Store.deleteList(l.id); confirm = false; openId = null
    }
}

private fun estimate(l: ShoppingList): Double? = l.items.mapNotNull { i ->
    i.price?.let { p -> Store.usd(p * (i.quantity.replace(',', '.').toDoubleOrNull() ?: 1.0), i.currency ?: Currency.USD) }
}.takeIf { it.isNotEmpty() }?.sum()

// ================================================================================================ Sincronización en la nube (Dev)

/** Google Drive (solo modo Dev, como la web): devuelve la acción que pide permiso y sincroniza. */
@Composable
fun rememberDriveSync(nav: Nav): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf(false) }
    fun run(token: String) = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching { Sync.driveSync(ctx, token) }.getOrElse { "Drive: ${it.message}" } }
        nav.snack(r)
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (pending) Sync.tokenFrom(ctx as Activity, r.data).onSuccess { run(it) }.onFailure { nav.snack("Drive: ${it.message}") }
        pending = false
    }
    return {
        pending = true
        Sync.authorize(ctx as Activity, onToken = { tk -> pending = false; run(tk) },
            onResolution = { pi -> consent.launch(IntentSenderRequest.Builder(pi.intentSender).build()) },
            onError = { e -> pending = false; nav.snack(t("kt_drive_unavailable", e)) })
    }
}

// ================================================================================================ Perfil

@Composable
fun ProfileScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = Store.data
    var name by remember { mutableStateOf(d.profile.name) }
    var importAsk by remember { mutableStateOf<Pair<AppData, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) } // refresca «última copia»
    var legal by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableStateOf(false) }
    var devTaps by remember { mutableIntStateOf(0) }
    val drive = rememberDriveSync(nav)

    fun bg(label: String, work: () -> String) {
        busy = true
        scope.launch { val r = withContext(Dispatchers.IO) { runCatching(work).getOrElse { "$label: ${it.message}" } }; busy = false; tick++; nav.snack(r) }
    }
    fun setExtra(k: String, v: Any) = Store.saveProfile(d.profile.copy(extra = JSONObject(d.profile.extra.toString()).put(k, v)))

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { bg(t("kt_export_failed")) { Sync.export(ctx, it); t("kt_backup_saved") } }
    }
    // leer e interpretar fuera del hilo principal: los respaldos de la web pueden traer recibos en base64 (varios MB)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Sync.read(ctx, uri) } }
            busy = false
            r.onSuccess { importAsk = it }.onFailure { nav.snack(t("kt_not_a_backup")) }
        }
    }
    fun applyImport(merge: Boolean, data: AppData) {
        importAsk = null; busy = true
        scope.launch {
            withContext(Dispatchers.Default) { if (merge) Store.merge(data) else Store.replaceAll(data) }
            busy = false; nav.snack(if (merge) t("kt_merged") else t("kt_replaced"))
        }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { bg(t("kt_folder_failed")) { Sync.setFolder(ctx, it); t("kt_auto_on") } }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        setExtra("notificationsEnabled", ok); Reminders.schedule(ctx)
        if (!ok) nav.snack(t("kt_notif_denied"))
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
        item {
            Box(Modifier.offset(x = (-16).dp).fillParentMaxWidth()) {
                ViewHeader(t("profile"), t("profileSubtitle"), nav.back) {
                    BrandButton(t("saveProfile"), icon = Icons.Outlined.Save, enabled = name.trim() != d.profile.name && name.isNotBlank()) {
                        Store.saveProfile(d.profile.copy(name = name.trim())); nav.snack(t("kt_saved"))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)).background(Brand.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(initials(name.ifBlank { d.profile.name }), style = MaterialTheme.typography.displaySmall, color = Brand)
                }
            }
        }
        item {
            SettingsGroup(t("username"), Icons.Outlined.Person) {
                Field(name, { name = it }, t("name"))
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, null, tint = Brand, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp)); Caps(t("language"))
                }
                Spacer(Modifier.height(10.dp))
                OptionGrid(listOf("en" to ("🇺🇸" to "ENGLISH"), "es" to ("🇻🇪" to "ESPAÑOL"), "pt" to ("🇧🇷" to "PORTUGUÊS")), { I18n.lang == it.first },
                    onPick = { Store.saveProfile(d.profile.copy(language = it.first)) }) { (_, v), on ->
                    Text(v.first, fontSize = 22.sp); Spacer(Modifier.height(4.dp))
                    Text(v.second, style = MaterialTheme.typography.labelSmall, color = if (on) Color.White else Ink, maxLines = 1)
                }
            }
        }
        item {
            SettingsGroup(t("navbarFavorites"), Icons.Outlined.Dashboard) {
                Text("${Prefs.favorites.size} / 3", style = MaterialTheme.typography.labelMedium, color = Brand)
                Spacer(Modifier.height(8.dp))
                OptionGrid(Route.entries.filter { it != Route.HOME }, { it in Prefs.favorites }, color = { routeColor(it).copy(alpha = 0.25f) },
                    enabled = { it in Prefs.favorites || Prefs.favorites.size < 3 },
                    onPick = { r -> Prefs.saveFavorites(if (r in Prefs.favorites) Prefs.favorites - r else Prefs.favorites + r) }) { r, on ->
                    Icon(routeIcon(r), null, tint = if (on) routeColor(r) else Muted.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(t(r.key), style = MaterialTheme.typography.labelSmall, color = if (on) Ink else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(6.dp))
                Text("ⓘ ${t("navbarFavoritesDesc")}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        item {
            SettingsGroup(t("notifications"), Icons.Outlined.Notifications) {
                val on = Reminders.enabled()
                SettingRow(t("enableNotifications"), t("kt_notif_desc")) {
                    Toggle(on) { v ->
                        if (v && Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        else { setExtra("notificationsEnabled", v); Reminders.schedule(ctx) }
                    }
                }
                AnimatedVisibility(on) {
                    SettingRow(t("notificationLeadTime")) { Stepper(Reminders.leadDays()) { setExtra("notificationLeadTime", it.coerceIn(0, 30)) } }
                }
            }
        }
        item {
            SettingsGroup(t("kt_backup_sync"), Icons.Outlined.CloudSync) {
                SettingRow(t("kt_export_backup"), t("kt_export_backup_desc"), icon = Icons.Outlined.Upload, onClick = { exporter.launch(Sync.backupName()) }) { Chevron() }
                SettingRow(t("kt_import_backup"), t("kt_import_backup_desc"), icon = Icons.Outlined.Download, onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Chevron() }
                val f = remember(tick) { Sync.folder(ctx) }
                SettingRow(if (f == null) t("kt_auto_folder") else t("kt_auto_folder_on"),
                    if (f == null) t("kt_auto_folder_desc") else t("kt_auto_folder_last", ago(Sync.lastAuto(ctx))), icon = Icons.Outlined.FolderOpen,
                    onClick = { folder.launch(null) }) {
                    if (f != null) Toggle(true) { Sync.setFolder(ctx, null); tick++; nav.snack(t("kt_auto_off")) } else Chevron()
                }
                if (Prefs.dev) SettingRow(t("kt_drive_sync"), remember(tick) { Sync.lastDrive(ctx) }?.let { t("kt_drive_last", ago(it)) } ?: t("kt_drive_desc"),
                    icon = Icons.Outlined.Cloud, tint = Warning, onClick = drive) { Chevron() }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Brand, trackColor = Glass)
            }
        }
        item {
            SettingsGroup(t("legalNotices"), Icons.Outlined.Balance) {
                SettingRow(t("termsAndConditions"), t("termsSubtitle"), icon = Icons.Outlined.Description, onClick = { legal = "terms" }) { Chevron() }
                SettingRow(t("privacyPolicy"), t("privacySubtitle"), icon = Icons.Outlined.VerifiedUser, tint = Income, onClick = { legal = "privacy" }) { Chevron() }
            }
        }
        item {
            SettingsGroup("Danger Zone", Icons.Outlined.Delete, tint = Expense) {
                SettingRow(t("resetAppData"), t("resetAppDataDesc"), titleColor = Expense, icon = Icons.Outlined.Delete, tint = Expense, onClick = { reset = true }) { Chevron() }
            }
        }
        item {
            // como en Android: 7 toques sobre la versión activan (o desactivan) el modo Dev
            Column(Modifier.fillMaxWidth().padding(top = 24.dp).tap(onClick = {
                devTaps++
                val left = 7 - devTaps
                when {
                    left <= 0 -> { Prefs.setDev(!Prefs.dev); devTaps = 0; nav.snack(if (Prefs.dev) t("kt_dev_on") else t("kt_dev_off")) }
                    left <= 3 -> nav.snack(t("kt_dev_steps", left))
                }
            }).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (Prefs.dev) Warning else Brand)); Spacer(Modifier.width(8.dp))
                    Text("PARITY", style = MaterialTheme.typography.labelMedium, color = Ink, letterSpacing = 3.sp)
                }
                Text("v0.2 · Kotlin" + if (Prefs.dev) " · ${t("devMode")}" else "", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }

    legal?.let { LegalSheet(it) { legal = null } }
    if (reset) Confirm(t("resetAppData"), t("resetAppDataConfirm"), t("delete"), danger = true, onDismiss = { reset = false }) {
        reset = false
        // la copia automática no debe pisar el respaldo de la carpeta con datos vacíos
        Sync.setFolder(ctx, null)
        Store.replaceAll(AppData(profile = UserProfile(language = d.profile.language)))
        Reminders.schedule(ctx)
        name = ""; nav.snack(t("kt_data_deleted")); nav.back()
    }
    importAsk?.let { (data, minimal) ->
        Confirm(t("kt_import_backup"), "", t("kt_merge"), onDismiss = { importAsk = null }, content = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(t("wallets") to data.accounts.size, t("transactions") to data.transactions.size, t("scheduled") to data.scheduled.size,
                    t("budget") to data.budgets.size, t("goals") to data.goals.size, t("shoppingList") to data.lists.size).forEach { (k, n) ->
                    Row { Text(k, Modifier.weight(1f), color = Ink); Text(if (n > 0) "$n" else t("kt_not_included"), color = if (n > 0) Ink else Muted) }
                }
                if (data.scheduled.isEmpty() && data.goals.isEmpty() && data.budgets.isEmpty() && data.transactions.isNotEmpty())
                    Text(t("kt_web_export_note"), style = MaterialTheme.typography.bodySmall, color = Income)
                Text(if (minimal) t("kt_sync_merge_note") else t("kt_replace_or_merge"), style = MaterialTheme.typography.bodySmall, color = Muted)
                if (!minimal) LinkText(t("kt_replace_all"), Expense) { applyImport(false, data) }
            }
        }) { applyImport(true, data) }
    }
}

/** Términos y Privacidad con el texto de la web (TermsModal.tsx y PrivacyModal.tsx → assets/legal.json). */
@Composable
private fun LegalSheet(kind: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val doc = remember(kind, I18n.lang) {
        JSONObject(ctx.assets.open("legal.json").bufferedReader().use { it.readText() }).getJSONObject(kind).let { it.optJSONObject(I18n.lang) ?: it.getJSONObject("en") }
    }
    TitledSheet(doc.getString("title"), doc.optString("lastUpdated"), onDismiss) {
        val secs = doc.getJSONArray("sections")
        for (i in 0 until secs.length()) {
            val s = secs.getJSONObject(i)
            Spacer(Modifier.height(16.dp))
            Text(s.getString("heading"), style = MaterialTheme.typography.titleSmall, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(s.getString("body"), style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}
