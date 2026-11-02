package com.metalsyntax.paritykt.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.metalsyntax.paritykt.data.*
import com.metalsyntax.paritykt.sync.Sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------------------------------------ Billeteras

@Composable
fun WalletsScreen(snack: (String) -> Unit) {
    val d = Store.data
    var edit by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            item {
                Text("Total en todas tus billeteras", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(d.totalUsd()), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
            if (d.accounts.isEmpty()) item { Empty(Icons.Outlined.AccountBalanceWallet, "Sin billeteras", "Efectivo, banco, Zelle, USDT… cada una con su moneda.", "Crear billetera") { creating = true } }
            else item { Section("Mis billeteras") }
            items(d.accounts.sortedByDescending { d.rates.toUsd(it.balance, it.currency) }, key = { it.id }) { a ->
                val usd = d.rates.toUsd(a.balance, a.currency)
                Row2(icon(a.icon), a.name, a.currency.name, money(a.balance, a.currency),
                    if (a.balance < 0) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified, onClick = { edit = a },
                    extra = if (a.currency != Currency.USD && a.currency != Currency.USDT) { { Text("≈ ${money(usd)}", style = MaterialTheme.typography.bodySmall) } } else null)
            }
        }
        ExtendedFloatingActionButton(onClick = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Billetera") })
    }
    if (creating || edit != null) {
        val initial = edit
        var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
        var cur by remember(initial) { mutableStateOf(initial?.currency ?: Currency.USD) }
        var balance by remember(initial) { mutableStateOf(initial?.balance?.let(::num) ?: "0") }
        var ic by remember(initial) { mutableStateOf(initial?.icon ?: "wallet") }
        val b = parseAmount(balance)
        FormSheet(if (initial == null) "Nueva billetera" else "Editar billetera", { edit = null; creating = false }, saveEnabled = name.isNotBlank() && b != null, onSave = {
            Store.saveAccount((initial ?: Account(name = "", currency = cur, balance = 0.0)).copy(name = name.trim(), currency = cur, balance = b!!, icon = ic))
            edit = null; creating = false
        }, onDelete = initial?.let { { if (Store.deleteAccount(it.id)) edit = null else snack("Tiene movimientos: bórralos o muévelos antes") } }) {
            Field(name, { name = it }, "Nombre (ej. Banesco, Efectivo)")
            Segmented(Currency.entries.map { it to it.name }, cur) { cur = it }
            Field(balance, { balance = it }, if (initial == null) "Saldo inicial" else "Saldo (ajuste)", number = true, suffix = symbol(cur))
            Segmented(listOf("wallet" to "Billetera", "bank" to "Banco", "cash" to "Efectivo", "savings" to "Ahorro"), ic) { ic = it }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Lista de compras

@Composable
fun ShoppingScreen() {
    val d = Store.data
    var openId by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    val open = d.lists.firstOrNull { it.id == openId }
    if (open != null) return ShoppingDetail(open) { openId = null }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            if (d.lists.isEmpty()) item { Empty(Icons.Outlined.ShoppingCart, "Sin listas", "Arma la lista del mercado con cantidades y precios estimados.", "Nueva lista") { naming = true } }
            items(d.lists.sortedByDescending { it.updatedAt }, key = { it.id }) { l ->
                val done = l.items.count { it.completed }
                Row2(Icons.Outlined.ShoppingCart, l.name, "$done de ${l.items.size} listos", estimate(l)?.let { "≈ ${money(it)}" }, onClick = { openId = l.id })
            }
        }
        ExtendedFloatingActionButton(onClick = { naming = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Lista") })
    }
    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { naming = false }, title = { Text("Nueva lista") }, text = { Field(name, { name = it }, "Nombre (ej. Mercado)") },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { val l = ShoppingList(name = name.trim()); Store.saveList(l); naming = false; openId = l.id }) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancelar") } })
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
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // la flecha de volver ya está en la barra superior
                Text(l.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, "Borrar lista") }
            }
            estimate(l)?.let { Text("Total estimado ≈ ${money(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp)) }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(name, { name = it }, "Producto", modifier = Modifier.weight(1f))
                Field(qty, { qty = it }, "Cant.", modifier = Modifier.width(72.dp))
                Field(price, { price = it }, "$", number = true, modifier = Modifier.width(80.dp))
                FilledIconButton(onClick = {
                    save(l.items + ShoppingItem(name = name.trim(), quantity = qty.ifBlank { "1" }, price = parseAmount(price), currency = Currency.USD))
                    name = ""; qty = "1"; price = ""
                }, enabled = name.isNotBlank()) { Icon(Icons.Outlined.Add, "Agregar") }
            }
            if (l.items.any { it.completed }) TextButton(onClick = { save(l.items.filterNot { it.completed }) }) { Text("Quitar los ya comprados") }
        }
        items(l.items.sortedBy { it.completed }, key = { it.id }) { i ->
            ListItem(
                leadingContent = { Checkbox(i.completed, { c -> save(l.items.map { if (it.id == i.id) it.copy(completed = c) else it }) }) },
                headlineContent = { Text(i.name, textDecoration = if (i.completed) TextDecoration.LineThrough else null) },
                supportingContent = { Text("× ${i.quantity}" + (i.price?.let { "  ·  ${money(it, i.currency ?: Currency.USD)} c/u" } ?: "")) },
                trailingContent = { IconButton(onClick = { save(l.items.filter { it.id != i.id }) }) { Icon(Icons.Outlined.Close, "Quitar") } },
            )
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("¿Borrar «${l.name}»?") },
        confirmButton = { TextButton(onClick = { Store.deleteList(l.id); confirm = false; back() }) { Text("Borrar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}

// ------------------------------------------------------------------------------------------------ Perfil

@Composable
fun ProfileScreen(snack: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = Store.data
    var name by remember { mutableStateOf(d.profile.name) }
    var manual by remember { mutableStateOf(d.rates.manual) }
    var rate by remember { mutableStateOf(if (d.rates.usd > 0) num(d.rates.usd) else "") }
    var eur by remember { mutableStateOf(if (d.rates.eur > 0) num(d.rates.eur) else "") }
    var importAsk by remember { mutableStateOf<Pair<AppData, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) } // refresca "última copia"

    fun bg(label: String, work: () -> String) {
        busy = true
        scope.launch { val r = withContext(Dispatchers.IO) { runCatching(work).getOrElse { "$label: ${it.message}" } }; busy = false; tick++; snack(r) }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { bg("No se pudo exportar") { Sync.export(ctx, it); "Respaldo guardado" } }
    }
    // leer e interpretar fuera del hilo principal: los respaldos de la web pueden traer recibos en base64 (varios MB)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Sync.read(ctx, uri) } }
            busy = false
            r.onSuccess { importAsk = it }.onFailure { snack("Ese archivo no es un respaldo de Parity") }
        }
    }
    fun applyImport(merge: Boolean, data: AppData) {
        importAsk = null; busy = true
        scope.launch {
            withContext(Dispatchers.Default) { if (merge) Store.merge(data) else Store.replaceAll(data) }
            busy = false; snack(if (merge) "Datos mezclados" else "Datos reemplazados")
        }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { bg("No se pudo usar la carpeta") { Sync.setFolder(ctx, it); "Copia automática activada" } }
    }
    var pendingDrive by remember { mutableStateOf(false) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val token = Sync.tokenFrom(ctx as Activity, r.data)
        if (token != null && pendingDrive) bg("Drive") { Sync.driveSync(ctx, token) } else snack("Drive: permiso no concedido")
        pendingDrive = false
    }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Section("Tu perfil")
            Field(name, { name = it }, "Tu nombre")
            TextButton(onClick = { Store.saveProfile(d.profile.copy(name = name.trim())); snack("Guardado") }, enabled = name != d.profile.name) { Text("Guardar nombre") }

            Section("Tasa de cambio")
            Text(if (d.rates.usd > 0) "Oficial BCV: ${num(d.rates.usd)} Bs/$" + (if (d.rates.eur > 0) " · ${num(d.rates.eur)} Bs/€" else "") +
                if (d.rates.manual) " (manual)" else " · ${ago(d.rates.updatedAt)}" else "Sin tasa todavía")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Usar tasa manual", Modifier.weight(1f)); Switch(manual, { manual = it; if (!it) Store.setRates(d.rates.copy(manual = false)) })
            }
            if (manual) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(rate, { rate = it }, "Bs por $", number = true, modifier = Modifier.weight(1f))
                Field(eur, { eur = it }, "Bs por €", number = true, modifier = Modifier.weight(1f))
            }
            Row {
                if (manual) Button(onClick = { parseAmount(rate)?.let { Store.setRates(Rates(it, parseAmount(eur) ?: 0.0, nowIso(), true)); snack("Tasa manual guardada") } },
                    enabled = parseAmount(rate) != null) { Text("Guardar tasa") }
                else FilledTonalButton(onClick = { bg("Sin conexión") { val r = Store.refreshRates(); "BCV: ${num(r.usd)} Bs/$" } }, enabled = !busy) {
                    Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Actualizar del BCV")
                }
            }

            Section("Respaldo y sincronización")
            Row2(Icons.Outlined.Upload, "Exportar respaldo", "Elige Drive, Descargas u otra app en el selector", onClick = { exporter.launch(Sync.backupName()) })
            Row2(Icons.Outlined.Download, "Importar respaldo", "De esta app o de Parity web (parity_backup / parity_sync)", onClick = { importer.launch(arrayOf("application/json", "*/*")) })
            val f = remember(tick) { Sync.folder(ctx) }
            Row2(Icons.Outlined.DriveFolderUpload, if (f == null) "Copia automática en una carpeta" else "Copia automática activa",
                if (f == null) "Elige una carpeta de Drive, Dropbox o Syncthing: se actualiza sola" else "Última: ${ago(Sync.lastAuto(ctx))} · toca para cambiar",
                onLongClick = if (f != null) { { Sync.setFolder(ctx, null); tick++; snack("Copia automática desactivada") } } else null,
                onClick = { folder.launch(null) })
            Row2(Icons.Outlined.CloudSync, "Sincronizar con Google Drive",
                remember(tick) { Sync.lastDrive(ctx) }?.let { "Última: ${ago(it)} · mismo formato que Parity web" } ?: "Mezcla con los parity_sync de Parity web",
                onClick = {
                    pendingDrive = true
                    Sync.authorize(ctx as Activity, onToken = { t -> pendingDrive = false; bg("Drive") { Sync.driveSync(ctx, t) } },
                        onResolution = { pi -> consent.launch(IntentSenderRequest.Builder(pi.intentSender).build()) },
                        onError = { e -> pendingDrive = false; snack("Drive no disponible: $e") })
                })
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))

            Section("Privacidad y legal")
            Text("Tus datos viven solo en este teléfono, en un archivo de la app. No hay cuentas, publicidad ni rastreo. " +
                "Solo salen si tú exportas, eliges una carpeta o sincronizas con tu propio Google Drive. " +
                "La tasa es la oficial del Banco Central de Venezuela, obtenida de ve.dolarapi.com; es referencial y no constituye asesoría financiera.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Parity MVP 0.1 · Íconos Material Symbols (Apache 2.0)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }

    importAsk?.let { (data, minimal) ->
        AlertDialog(onDismissRequest = { importAsk = null }, title = { Text("Importar respaldo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Billeteras" to data.accounts.size, "Movimientos" to data.transactions.size, "Planificados" to data.scheduled.size,
                        "Presupuestos" to data.budgets.size, "Metas" to data.goals.size, "Listas de compras" to data.lists.size).forEach { (k, n) ->
                        Row { Text(k, Modifier.weight(1f)); Text(if (n > 0) "$n" else "no trae", color = if (n > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline) }
                    }
                    if (data.scheduled.isEmpty() && data.goals.isEmpty() && data.budgets.isEmpty() && data.transactions.isNotEmpty()) Text(
                        "Este archivo es el «Exportar JSON» de la web, que solo guarda billeteras y movimientos. Para traer planificados, metas y " +
                            "presupuestos usa el respaldo de Google Drive de la web (parity_backup_ en tu Drive).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    Text(if (minimal) "Es una sincronización: se mezclará con lo que tienes." else "¿Reemplazar todo o mezclar con lo que tienes?",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { applyImport(true, data) }) { Text("Mezclar") } },
            dismissButton = { if (!minimal) TextButton(onClick = { applyImport(false, data) }) { Text("Reemplazar todo") } })
    }
}
