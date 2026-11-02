package com.metalsyntax.paritykt.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metalsyntax.paritykt.data.*
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ------------------------------------------------------------------------------------------------ formato

val ES get() = I18n.locale
private val nf get() = NumberFormat.getNumberInstance(I18n.locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }

fun symbol(c: Currency) = when (c) { Currency.USD -> "$"; Currency.EUR -> "€"; Currency.VES -> "Bs."; Currency.USDT -> "₮" }
fun money(v: Double, c: Currency = Currency.USD) = (if (v < 0) "-" else "") + "${symbol(c)} ${nf.format(kotlin.math.abs(v))}"
fun num(v: Double): String = nf.format(v)
fun parseAmount(s: String): Double? = s.trim().replace(" ", "").let { t ->
    // acepta 1.234,56 y 1234.56
    if (t.contains(',')) t.replace(".", "").replace(',', '.').toDoubleOrNull() else t.toDoubleOrNull()
}

/** USD normalizado → moneda de visualización (botón $ / Bs / € de la web). */
fun inDisplay(usd: Double, c: Currency = Prefs.display): Double = Store.data.rates.let { r ->
    when (c) { Currency.VES -> usd * r.usd; Currency.EUR -> if (r.eur > 0) usd * r.usd / r.eur else usd; else -> usd }
}
const val HIDDEN = "••••••"
/** Monto principal en la moneda de visualización, oculto si el usuario tapó el saldo (ojo). */
fun shown(usd: Double, c: Currency = Prefs.display) = if (Prefs.balanceVisible) money(inDisplay(usd, c), c) else HIDDEN
/** Monto secundario «≈»: Bs si se ve en $/€, $ si se ve en Bs. */
fun secondary(usd: Double) = if (!Prefs.balanceVisible) HIDDEN else if (Prefs.display == Currency.VES) money(usd) else money(inDisplay(usd, Currency.VES), Currency.VES)

fun dayLabel(iso: String): String = runCatching {
    val d = LocalDate.parse(iso.take(10))
    val today = LocalDate.now()
    when (d) {
        today -> t("today")
        today.minusDays(1) -> t("yesterday")
        today.plusDays(1) -> t("kt_tomorrow")
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "d MMM yyyy", ES)).replaceFirstChar { it.uppercase() }
    }
}.getOrDefault(iso.take(10))

fun ago(iso: String?): String = runCatching {
    val m = (System.currentTimeMillis() - Instant.parse(iso).toEpochMilli()) / 60_000
    when { m < 1 -> t("kt_now"); m < 60 -> t("kt_min_ago", m); m < 1440 -> t("kt_h_ago", m / 60); else -> t("kt_d_ago", m / 1440) }
}.getOrDefault("—")

fun initials(name: String) = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "P" }

// ------------------------------------------------------------------------------------------------ íconos

fun icon(name: String): ImageVector = when (name) {
    "food" -> Icons.Outlined.ShoppingCart; "coffee" -> Icons.Outlined.Restaurant; "car" -> Icons.Outlined.DirectionsCar
    "home" -> Icons.Outlined.Home; "bolt" -> Icons.Outlined.Bolt; "wifi" -> Icons.Outlined.Language; "health" -> Icons.Outlined.MonitorHeart
    "bag" -> Icons.Outlined.ShoppingBag; "movie" -> Icons.Outlined.SportsEsports; "school" -> Icons.Outlined.School; "tv" -> Icons.Outlined.Tv
    "card" -> Icons.Outlined.Handshake; "shield" -> Icons.Outlined.Umbrella; "pets" -> Icons.Outlined.Pets; "kids" -> Icons.Outlined.ChildCare
    "plane" -> Icons.Outlined.Flight; "gift" -> Icons.Outlined.CardGiftcard; "spa" -> Icons.Outlined.Person; "build" -> Icons.Outlined.Work
    "receipt" -> Icons.Outlined.Description; "percent" -> Icons.Outlined.Percent; "savings" -> Icons.Outlined.Savings
    "money" -> Icons.Outlined.AttachMoney; "work" -> Icons.Outlined.Work; "laptop" -> Icons.Outlined.Language; "store" -> Icons.Outlined.Work
    "trending" -> Icons.Outlined.TrendingUp; "swap" -> Icons.Outlined.SwapHoriz; "wallet" -> Icons.Outlined.AccountBalanceWallet
    "bank" -> Icons.Outlined.AccountBalance; "cash" -> Icons.Outlined.Payments; "flag" -> Icons.Outlined.Flag
    "beach" -> Icons.Outlined.BeachAccess; "phone" -> Icons.Outlined.Smartphone; "more" -> Icons.Outlined.MoreHoriz
    else -> Icons.Outlined.Category
}

/** Íconos de billetera de la web (utils/iconUtils.tsx ACCOUNT_ICONS); las marcas usan su equivalente genérico. */
val ACCOUNT_ICON_KEYS = listOf("wallet", "bank", "card", "visa", "cash", "crypto", "paypal", "mobile", "savings", "binance", "zelle")

fun accountIcon(key: String): ImageVector = when (key) {
    "bank" -> Icons.Outlined.AccountBalance; "card", "visa", "mastercard" -> Icons.Outlined.CreditCard; "cash" -> Icons.Outlined.Payments
    "crypto", "binance" -> Icons.Outlined.CurrencyBitcoin; "mobile" -> Icons.Outlined.Smartphone; "savings" -> Icons.Outlined.Savings
    "paypal", "zelle", "venmo", "cashapp", "mercadopago", "applepay", "googlepay", "amazonpay", "stripe" -> Icons.Outlined.Contactless
    else -> Icons.Outlined.AccountBalanceWallet
}

// ------------------------------------------------------------------------------------------------ interacción

/** active:scale-95 de la web: el elemento se encoge con un resorte al tocarlo. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tap(onLongClick: (() -> Unit)? = null, onClick: (() -> Unit)?): Modifier = if (onClick == null && onLongClick == null) this else composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "tap")
    graphicsLayer { scaleX = s; scaleY = s }.combinedClickable(interactionSource = src, indication = rippleInd(), onClick = { onClick?.invoke() }, onLongClick = onLongClick)
}

@Composable
private fun rippleInd() = androidx.compose.material.ripple.rememberRipple(color = Ink)

// ------------------------------------------------------------------------------------------------ bloques

/** Tarjeta de la web: bg-theme-surface + border-theme-soft + rounded-2xl. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, radius: Dp = 24.dp, color: Color = Surface1,
              content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(modifier.tap(onClick = onClick).clip(shape).background(color).border(1.dp, GlassBorder, shape), content = content)
}

/** Rótulo chico en mayúsculas con tracking amplio (text-[10px] font-black uppercase tracking-widest opacity-60). */
@Composable
fun Caps(text: String, modifier: Modifier = Modifier, color: Color = Muted.copy(alpha = 0.75f)) =
    Text(text.uppercase(ES), modifier, style = MaterialTheme.typography.labelSmall, color = color, letterSpacing = 2.sp, maxLines = 1,
        overflow = TextOverflow.Ellipsis)

/** Encabezado de cada vista de la web: botón redondo de volver, título grande y subtítulo; acciones a la derecha. */
@Composable
fun ViewHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            RoundIcon(Icons.Outlined.ArrowBack, t("back"), onBack)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Botón circular con borde (volver, ojo, cerrar). */
@Composable
fun RoundIcon(icon: ImageVector, desc: String?, onClick: () -> Unit, tint: Color = Ink, size: Dp = 44.dp, bg: Color = Surface1) {
    Box(Modifier.size(size).tap(onClick = onClick).clip(CircleShape).background(bg).border(1.dp, GlassBorder, CircleShape),
        contentAlignment = Alignment.Center) { Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.45f)) }
}

/** Botón principal de la web: bg-theme-brand, blanco, font-black, rounded-2xl. */
@Composable
fun BrandButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, color: Color = Brand, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.heightIn(min = 48.dp).tap(onClick = if (enabled) onClick else null).clip(shape).background(if (enabled) color else color.copy(alpha = 0.35f))
        .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botón chico de contorno (acciones secundarias de los encabezados). */
@Composable
fun GhostButton(text: String, icon: ImageVector? = null, color: Color = Ink, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.heightIn(min = 40.dp).tap(onClick = onClick).clip(shape).background(Surface1).border(1.dp, GlassBorder, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Caps(title, Modifier.weight(1f))
        if (action != null) Text(action.uppercase(ES), Modifier.tap(onClick = onAction).padding(4.dp), style = MaterialTheme.typography.labelSmall,
            color = Brand, letterSpacing = 1.sp)
    }
}

/** Ícono en cuadro de color (bg-x-500/20 text-x-400 de la web). */
@Composable
fun Bubble(icon: ImageVector, tint: Color = Brand, size: Dp = 46.dp) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(Modifier.size(size).clip(shape).background(tint.copy(alpha = 0.15f)).border(1.dp, tint.copy(alpha = 0.2f), shape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

fun catColor(c: Category) = Color(c.color)

/** Fila estándar: ícono, título y subtítulo (se recortan), valor a la derecha (nunca se recorta). */
@Composable
fun Row2(
    icon: ImageVector, title: String, subtitle: String? = null, trailing: String? = null, trailingColor: Color = Ink,
    tint: Color = Brand, onLongClick: (() -> Unit)? = null,
    extra: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null, // último: una lambda final es siempre el clic
) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).tap(onLongClick, onClick).clip(shape).background(Surface1).border(1.dp, GlassBorder, shape)
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Bubble(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = Ink)
            subtitle?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
        if (trailing != null || extra != null) {
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                trailing?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = trailingColor, maxLines = 1) }
                extra?.invoke()
            }
        }
    }
}

@Composable
fun Empty(icon: ImageVector, title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Surface1).border(1.dp, GlassBorder, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Brand, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); BrandButton(action, icon = Icons.Outlined.Add, onClick = onAction) }
    }
}

/** Barra de progreso que crece al aparecer. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val f by animateFloatAsState(if (shown) fraction.coerceIn(0f, 1f) else 0f, tween(900), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Glass)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
fun Progress(label: String, value: Double, max: Double, caption: String, color: Color? = null) {
    val f = if (max > 0) (value / max).toFloat() else 0f
    val over = f > 1f
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(caption, style = MaterialTheme.typography.labelMedium, color = if (over) Expense else Muted, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Bar(f, if (over) Expense else if (f > 0.8f) Warning else color ?: Brand)
    }
}

/** Interruptor de la web (w-10 h-5 bg-theme-brand). */
@Composable
fun Toggle(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) = Switch(checked, onChange, enabled = enabled,
    colors = SwitchDefaults.colors(checkedTrackColor = Brand, checkedThumbColor = Color.White, uncheckedTrackColor = Glass,
        uncheckedThumbColor = Muted, uncheckedBorderColor = GlassBorder))

/** Fila con texto y descripción a la izquierda y un control a la derecha. */
@Composable
fun SettingRow(title: String, desc: String? = null, titleColor: Color = Ink, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().tap(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            desc?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
        Spacer(Modifier.width(12.dp)); trailing()
    }
}

// ------------------------------------------------------------------------------------------------ formularios

/** Hoja inferior común para crear/editar: título, campos, Guardar y (si se edita) Eliminar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean = true, onDelete: (() -> Unit)? = null,
              content: @Composable ColumnScope.() -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding().verticalScrollable(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.weight(1f))
                if (onDelete != null) RoundIcon(Icons.Outlined.Delete, t("delete"), { confirm = true }, tint = Expense, size = 40.dp)
            }
            content()
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButtonWide(t("cancel"), Modifier.weight(1f), onDismiss)
                BrandButton(t("save"), Modifier.weight(1f), Icons.Outlined.Check, saveEnabled, onClick = onSave)
            }
        }
    }
    if (confirm) Confirm(t("kt_delete_q"), t("kt_cannot_undo"), t("delete"), danger = true, onDismiss = { confirm = false }) { confirm = false; onDelete?.invoke() }
}

@Composable
private fun GhostButtonWide(text: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.heightIn(min = 48.dp).tap(onClick = onClick).clip(shape).background(Glass).border(1.dp, GlassBorder, shape), contentAlignment = Alignment.Center) {
        Text(text, color = Ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Modifier.verticalScrollable() = this.verticalScroll(androidx.compose.foundation.rememberScrollState())

/** ModalBottomSheet con el estilo de los modales de la web (bg-theme-surface, rounded-t-[2rem]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface1,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), dragHandle = {
            Box(Modifier.padding(vertical = 12.dp).size(40.dp, 4.dp).clip(CircleShape).background(GlassBorder))
        }, content = content)

@Composable
fun Confirm(title: String, text: String, action: String, danger: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, containerColor = Surface1, shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = Ink, style = MaterialTheme.typography.titleLarge) }, text = { Text(text, color = Muted) },
        confirmButton = { BrandButton(action, color = if (danger) Expense else Brand, onClick = onConfirm) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel"), color = Muted) } })

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Bg, unfocusedContainerColor = Bg, disabledContainerColor = Bg,
    focusedBorderColor = Brand, unfocusedBorderColor = GlassBorder, focusedLabelColor = Brand, unfocusedLabelColor = Muted,
    focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Brand, focusedLeadingIconColor = Muted, unfocusedLeadingIconColor = Muted,
    focusedTrailingIconColor = Muted, unfocusedTrailingIconColor = Muted, focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted,
    focusedSuffixColor = Muted, unfocusedSuffixColor = Muted)

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, number: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(), suffix: String? = null) =
    OutlinedTextField(value, onChange, modifier, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
        suffix = suffix?.let { { Text(it) } }, shape = RoundedCornerShape(16.dp), colors = fieldColors(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default)

/** Desplegable de opciones (clave → texto). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Picker(label: String, options: List<Pair<T, String>>, selected: T?, modifier: Modifier = Modifier.fillMaxWidth(), onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(options.firstOrNull { it.first == selected }?.second ?: "", {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, shape = RoundedCornerShape(16.dp),
            colors = fieldColors(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        ExposedDropdownMenu(open, { open = false }, Modifier.background(Surface2)) {
            options.forEach { (k, t) -> DropdownMenuItem(text = { Text(t, color = if (k == selected) Brand else Ink, fontWeight = FontWeight.Bold) },
                onClick = { onSelect(k); open = false }) }
        }
    }
}

/** Selector de pastillas (los toggles de tipo de la web): fondo oscuro, opción activa en color de marca. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, colorOf: (T) -> Color = { Brand }, onSelect: (T) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Bg).border(1.dp, GlassBorder, shape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (k, label) ->
            val on = k == selected
            val bg by animateColorAsState(if (on) colorOf(k) else Color.Transparent, tween(200), label = "seg")
            Box(Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(bg).tap(onClick = { onSelect(k) }).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center) {
                Text(label, color = if (on) Color.White else Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Campo de fecha (YYYY-MM-DD) con el selector de Material 3, con aspecto de campo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tap(onClick = { open = true }).clip(shape).background(Bg).border(1.dp, GlassBorder, shape)
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Event, null, tint = Brand, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(date.format(DateTimeFormatter.ofPattern("EEE d MMMM yyyy", ES)).replaceFirstChar { it.uppercase() }, color = Ink,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = {
                st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()) }; open = false
            }) { Text(t("done"), color = Brand) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(t("cancel"), color = Muted) } }) { DatePicker(st) }
    }
}

/** Menú desplegable anclado a un botón de filtro (popups de filtros de la web). */
@Composable
fun <T> FilterButton(label: String, icon: ImageVector, active: Boolean, options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier,
                     onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier) {
        Column(Modifier.fillMaxWidth().tap(onClick = { open = true }).clip(shape).background(if (active) Brand.copy(alpha = 0.15f) else Surface1)
            .border(1.dp, if (active) Brand.copy(alpha = 0.5f) else GlassBorder, shape).padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (active) Brand else Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label.uppercase(ES), style = MaterialTheme.typography.labelSmall, color = if (active) Brand else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(open, { open = false }, Modifier.background(Surface2).heightIn(max = 360.dp)) {
            options.forEach { (k, t) -> DropdownMenuItem(text = { Text(t, color = if (k == selected) Brand else Muted, fontWeight = FontWeight.Black) },
                onClick = { onSelect(k); open = false }) }
        }
    }
}
