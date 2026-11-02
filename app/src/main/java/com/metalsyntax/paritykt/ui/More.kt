package com.metalsyntax.paritykt.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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

// ------------------------------------------------------------------------------------------------ Billeteras

@Composable
fun WalletsScreen(nav: Nav) {
    val d = Store.data
    var edit by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            ViewHeader(t("wallets"), "${d.accounts.size} ${t("wallets").lowercase(ES)}", nav.back) {
                RoundIcon(Icons.Outlined.Add, t("kt_new_wallet"), { creating = true }, tint = Color.White, bg = Brand)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(22.dp)) {
                    Caps(t("totalBalance"))
                    Spacer(Modifier.height(6.dp))
                    AutoText(shown(d.totalUsd()), MaterialTheme.typography.headlineLarge, Ink)
                    Text("≈ ${secondary(d.totalUsd())}", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
            }
        }
        if (d.accounts.isEmpty()) item {
            Empty(Icons.Outlined.AccountBalanceWallet, t("kt_no_wallets"), t("kt_no_wallets_desc"), t("kt_create_wallet")) { creating = true }
        } else item { Box(Modifier.padding(horizontal = 16.dp)) { Section(t("kt_my_wallets")) } }
        items(d.accounts.sortedByDescending { d.rates.toUsd(it.balance, it.currency) }, key = { it.id }) { a ->
            val usd = d.rates.toUsd(a.balance, a.currency)
            Box(Modifier.padding(horizontal = 16.dp)) {
                Row2(accountIcon(a.icon), a.name, a.currency.name, if (Prefs.balanceVisible) money(a.balance, a.currency) else HIDDEN,
                    if (a.balance < 0) Expense else Ink, onClick = { edit = a },
                    extra = if (a.currency != Currency.USD && a.currency != Currency.USDT) { {
                        Text("≈ ${if (Prefs.balanceVisible) money(usd) else HIDDEN}", style = MaterialTheme.typography.labelSmall, color = Muted)
                    } } else null)
            }
        }
    }
    if (creating || edit != null) {
        val initial = edit
        var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
        var cur by remember(initial) { mutableStateOf(initial?.currency ?: Currency.USD) }
        var balance by remember(initial) { mutableStateOf(initial?.balance?.let(::num) ?: "0") }
        var ic by remember(initial) { mutableStateOf(initial?.icon ?: "wallet") }
        val b = parseAmount(balance)
        FormSheet(if (initial == null) t("kt_new_wallet") else t("kt_edit_wallet"), { edit = null; creating = false }, saveEnabled = name.isNotBlank() && b != null, onSave = {
            Store.saveAccount((initial ?: Account(name = "", currency = cur, balance = 0.0)).copy(name = name.trim(), currency = cur, balance = b!!, icon = ic))
            edit = null; creating = false
        }, onDelete = initial?.let { { if (Store.deleteAccount(it.id)) edit = null else nav.snack(t("kt_wallet_has_tx")) } }) {
            Field(name, { name = it }, t("kt_wallet_name_hint"))
            Caps(t("currency"))
            Segmented(Currency.entries.map { it to it.name }, cur) { cur = it }
            Field(balance, { balance = it }, if (initial == null) t("initialBalance") else t("kt_balance_adjust"), number = true, suffix = symbol(cur))
            Caps(t("kt_icon"))
            (ACCOUNT_ICON_KEYS + listOf(ic).filter { it !in ACCOUNT_ICON_KEYS }).chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        val on = ic == k
                        Box(Modifier.weight(1f).height(48.dp).tap(onClick = { ic = k }).clip(RoundedCornerShape(14.dp)).background(if (on) Brand else Bg)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Icon(accountIcon(k), k, tint = if (on) Color.White else Muted)
                        }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Lista de compras

@Composable
fun ShoppingScreen(nav: Nav) {
    val d = Store.data
    var openId by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    val open = d.lists.firstOrNull { it.id == openId }
    if (open != null) return ShoppingDetail(open) { openId = null }
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            ViewHeader(t("shoppingList"), "${d.lists.size} ${t("kt_lists")}", nav.back) {
                RoundIcon(Icons.Outlined.Add, t("kt_new_list"), { naming = true }, tint = Color.White, bg = Brand)
            }
        }
        if (d.lists.isEmpty()) item { Empty(Icons.Outlined.ShoppingCart, t("kt_no_lists"), t("kt_no_lists_desc"), t("kt_new_list")) { naming = true } }
        items(d.lists.sortedByDescending { it.updatedAt }, key = { it.id }) { l ->
            val done = l.items.count { it.completed }
            Box(Modifier.padding(horizontal = 16.dp)) {
                Row2(Icons.Outlined.ShoppingCart, l.name, t("kt_n_of_m_done", done, l.items.size), estimate(l)?.let { "≈ ${money(it)}" },
                    tint = Color(0xFFF59E0B), onClick = { openId = l.id })
            }
        }
    }
    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { naming = false }, containerColor = Surface1, shape = RoundedCornerShape(28.dp),
            title = { Text(t("kt_new_list"), color = Ink) }, text = { Field(name, { name = it }, t("kt_list_name_hint")) },
            confirmButton = { BrandButton(t("kt_create"), enabled = name.isNotBlank()) { val l = ShoppingList(name = name.trim()); Store.saveList(l); naming = false; openId = l.id } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(t("cancel"), color = Muted) } })
    }
}

private fun estimate(l: ShoppingList): Double? = l.items.mapNotNull { i ->
    i.price?.let { p -> Store.usd(p * (i.quantity.replace(',', '.').toDoubleOrNull() ?: 1.0), i.currency ?: Currency.USD) }
}.takeIf { it.isNotEmpty() }?.sum()

@Composable
private fun ShoppingDetail(l: ShoppingList, back: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = back)
    fun save(items: List<ShoppingItem>) = Store.saveList(l.copy(items = items))
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            ViewHeader(l.name, estimate(l)?.let { "${t("kt_estimated_total")} ≈ ${money(it)}" }, back) {
                RoundIcon(Icons.Outlined.Delete, t("delete"), { confirm = true }, tint = Expense)
            }
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
            if (l.items.any { it.completed }) Text(t("kt_remove_bought").uppercase(ES), Modifier.padding(start = 20.dp, top = 12.dp)
                .tap(onClick = { save(l.items.filterNot { it.completed }) }), style = MaterialTheme.typography.labelSmall, color = Brand, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
        }
        items(l.items.sortedBy { it.completed }, key = { it.id }) { i ->
            val shape = RoundedCornerShape(16.dp)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(shape).background(Surface1).border(1.dp, GlassBorder, shape)
                .tap(onClick = { save(l.items.map { if (it.id == i.id) it.copy(completed = !i.completed) else it }) }).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(i.completed, { c -> save(l.items.map { if (it.id == i.id) it.copy(completed = c) else it }) },
                    colors = CheckboxDefaults.colors(checkedColor = Brand, uncheckedColor = Muted))
                Column(Modifier.weight(1f)) {
                    Text(i.name, style = MaterialTheme.typography.titleSmall, color = if (i.completed) Muted else Ink,
                        textDecoration = if (i.completed) TextDecoration.LineThrough else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("× ${i.quantity}" + (i.price?.let { "  ·  ${money(it, i.currency ?: Currency.USD)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                IconButton(onClick = { save(l.items.filter { it.id != i.id }) }) { Icon(Icons.Outlined.Close, t("delete"), tint = Muted) }
            }
        }
    }
    if (confirm) Confirm(t("kt_delete_list_q", l.name), t("kt_cannot_undo"), t("delete"), danger = true, onDismiss = { confirm = false }) {
        Store.deleteList(l.id); confirm = false; back()
    }
}

// ------------------------------------------------------------------------------------------------ Perfil

/** Tarjeta de sección del perfil (bg-theme-surface rounded-2xl p-6, rótulo con ícono). */
@Composable
private fun ProfileCard(label: String, ic: ImageVector, tint: Color = Brand, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, null, tint = tint, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp))
                Caps(label, color = if (tint == Expense) Expense else Muted.copy(alpha = 0.75f))
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

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
    var pendingDrive by remember { mutableStateOf(false) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val token = Sync.tokenFrom(ctx as Activity, r.data)
        if (token != null && pendingDrive) bg("Drive") { Sync.driveSync(ctx, token) } else nav.snack(t("kt_drive_denied"))
        pendingDrive = false
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        setExtra("notificationsEnabled", ok); Reminders.schedule(ctx)
        if (!ok) nav.snack(t("kt_notif_denied"))
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            ViewHeader(t("profile"), t("profileSubtitle"), nav.back) {
                BrandButton(t("saveProfile"), icon = Icons.Outlined.Save, enabled = name.trim() != d.profile.name && name.isNotBlank()) {
                    Store.saveProfile(d.profile.copy(name = name.trim())); nav.snack(t("kt_saved"))
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)).background(Bg).border(3.dp, Ink, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                    Text(initials(name.ifBlank { d.profile.name }), style = MaterialTheme.typography.displaySmall, color = Ink)
                }
            }
        }
        item {
            ProfileCard(t("username"), Icons.Outlined.Person) {
                Field(name, { name = it }, t("name"))
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, null, tint = Brand, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp)); Caps(t("language"))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to ("🇺🇸" to "ENGLISH"), "es" to ("🇻🇪" to "ESPAÑOL"), "pt" to ("🇧🇷" to "PORTUGUÊS")).forEach { (code, v) ->
                        val on = I18n.lang == code
                        Column(Modifier.weight(1f).tap(onClick = { Store.saveProfile(d.profile.copy(language = code)) }).clip(RoundedCornerShape(16.dp))
                            .background(if (on) Brand else Bg).border(1.dp, GlassBorder, RoundedCornerShape(16.dp)).padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v.first, fontSize = 22.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(v.second, style = MaterialTheme.typography.labelSmall, color = if (on) Color.White else Ink, maxLines = 1)
                        }
                    }
                }
            }
        }
        item {
            ProfileCard(t("navbarFavorites"), Icons.Outlined.Dashboard) {
                Text("${Prefs.favorites.size} / 3", style = MaterialTheme.typography.labelMedium, color = Brand)
                Spacer(Modifier.height(8.dp))
                Route.entries.filter { it != Route.HOME }.chunked(3).forEach { row ->
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { r ->
                            val on = r in Prefs.favorites
                            val full = !on && Prefs.favorites.size >= 3
                            val c = routeColor(r)
                            Column(Modifier.weight(1f).tap(onClick = if (full) null else { {
                                Prefs.saveFavorites(if (on) Prefs.favorites - r else Prefs.favorites + r)
                            } }).clip(RoundedCornerShape(16.dp)).background(if (on) c.copy(alpha = 0.12f) else Bg)
                                .border(if (on) 2.dp else 1.dp, if (on) c.copy(alpha = 0.6f) else GlassBorder, RoundedCornerShape(16.dp))
                                .padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(routeIcon(r), null, tint = if (on) c else Muted.copy(alpha = if (full) 0.3f else 0.7f), modifier = Modifier.size(22.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(t(r.key), style = MaterialTheme.typography.labelSmall, color = if (on) Ink else Muted.copy(alpha = if (full) 0.4f else 0.8f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("ⓘ ${t("navbarFavoritesDesc")}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        item {
            ProfileCard(t("notifications"), Icons.Outlined.Notifications) {
                val on = Reminders.enabled()
                SettingRow(t("enableNotifications"), t("kt_notif_desc")) {
                    Toggle(on) { v ->
                        if (v && Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        else { setExtra("notificationsEnabled", v); Reminders.schedule(ctx) }
                    }
                }
                AnimatedVisibility(on) {
                    SettingRow(t("notificationLeadTime")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundIcon(Icons.Outlined.Remove, "-", { setExtra("notificationLeadTime", (Reminders.leadDays() - 1).coerceAtLeast(0)) }, size = 36.dp, bg = Bg)
                            Text("${Reminders.leadDays()}", Modifier.width(40.dp), style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center)
                            RoundIcon(Icons.Outlined.Add, "+", { setExtra("notificationLeadTime", (Reminders.leadDays() + 1).coerceAtMost(30)) }, size = 36.dp, bg = Bg)
                        }
                    }
                }
            }
        }
        item {
            ProfileCard(t("kt_backup_sync"), Icons.Outlined.CloudSync) {
                SettingRow(t("kt_export_backup"), t("kt_export_backup_desc"), onClick = { exporter.launch(Sync.backupName()) }) { Chevron() }
                SettingRow(t("kt_import_backup"), t("kt_import_backup_desc"), onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Chevron() }
                val f = remember(tick) { Sync.folder(ctx) }
                SettingRow(if (f == null) t("kt_auto_folder") else t("kt_auto_folder_on"),
                    if (f == null) t("kt_auto_folder_desc") else t("kt_auto_folder_last", ago(Sync.lastAuto(ctx))), onClick = { folder.launch(null) }) {
                    if (f != null) Toggle(true) { Sync.setFolder(ctx, null); tick++; nav.snack(t("kt_auto_off")) } else Chevron()
                }
                SettingRow(t("kt_drive_sync"), remember(tick) { Sync.lastDrive(ctx) }?.let { t("kt_drive_last", ago(it)) } ?: t("kt_drive_desc"), onClick = {
                    pendingDrive = true
                    Sync.authorize(ctx as Activity, onToken = { tk -> pendingDrive = false; bg("Drive") { Sync.driveSync(ctx, tk) } },
                        onResolution = { pi -> consent.launch(IntentSenderRequest.Builder(pi.intentSender).build()) },
                        onError = { e -> pendingDrive = false; nav.snack(t("kt_drive_unavailable", e)) })
                }) { Chevron() }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Brand, trackColor = Glass)
            }
        }
        item {
            ProfileCard(t("legalNotices"), Icons.Outlined.Balance) {
                LegalRow(Icons.Outlined.Description, Brand, t("termsAndConditions"), t("termsSubtitle")) { legal = "terms" }
                LegalRow(Icons.Outlined.VerifiedUser, Income, t("privacyPolicy"), t("privacySubtitle")) { legal = "privacy" }
            }
        }
        item {
            ProfileCard("Danger Zone", Icons.Outlined.Delete, tint = Expense) {
                LegalRow(Icons.Outlined.Delete, Expense, t("resetAppData"), t("resetAppDataDesc"), titleColor = Expense) { reset = true }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Brand)); Spacer(Modifier.width(8.dp))
                    Text("PARITY", style = MaterialTheme.typography.labelMedium, color = Ink, letterSpacing = 3.sp)
                }
                Text("MVP 0.1 · Kotlin", style = MaterialTheme.typography.labelSmall, color = Muted)
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
        AlertDialog(onDismissRequest = { importAsk = null }, containerColor = Surface1, shape = RoundedCornerShape(28.dp),
            title = { Text(t("kt_import_backup"), color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(t("wallets") to data.accounts.size, t("transactions") to data.transactions.size, t("scheduled") to data.scheduled.size,
                        t("budget") to data.budgets.size, t("goals") to data.goals.size, t("shoppingList") to data.lists.size).forEach { (k, n) ->
                        Row { Text(k, Modifier.weight(1f), color = Ink); Text(if (n > 0) "$n" else t("kt_not_included"), color = if (n > 0) Ink else Muted) }
                    }
                    if (data.scheduled.isEmpty() && data.goals.isEmpty() && data.budgets.isEmpty() && data.transactions.isNotEmpty())
                        Text(t("kt_web_export_note"), style = MaterialTheme.typography.bodySmall, color = Income)
                    Text(if (minimal) t("kt_sync_merge_note") else t("kt_replace_or_merge"), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            },
            confirmButton = { BrandButton(t("kt_merge")) { applyImport(true, data) } },
            dismissButton = { if (!minimal) TextButton(onClick = { applyImport(false, data) }) { Text(t("kt_replace_all"), color = Expense) } })
    }
}

@Composable
private fun Chevron() = Icon(Icons.Outlined.ChevronRight, null, tint = Muted)

@Composable
private fun LegalRow(ic: ImageVector, tint: Color, title: String, desc: String, titleColor: Color = Ink, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().tap(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Bubble(ic, tint, 40.dp); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Chevron()
    }
}

/** Términos y Privacidad con el texto de la web (TermsModal.tsx y PrivacyModal.tsx → assets/legal.json). */
@Composable
private fun LegalSheet(kind: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val doc = remember(kind, I18n.lang) {
        JSONObject(ctx.assets.open("legal.json").bufferedReader().use { it.readText() }).getJSONObject(kind).let { it.optJSONObject(I18n.lang) ?: it.getJSONObject("en") }
    }
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScrollable()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(doc.getString("title"), style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Outlined.Close, t("close"), onDismiss, tint = Muted, size = 40.dp)
            }
            Text(doc.optString("lastUpdated"), style = MaterialTheme.typography.labelSmall, color = Muted)
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
}
