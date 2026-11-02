package com.metalsyntax.paritykt.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.metalsyntax.paritykt.R
import com.metalsyntax.paritykt.data.*
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Kit de componentes de la app. Regla: las vistas componen estas piezas (y sus variantes por parámetro) en vez de
 * repetir Row/Box con estilos. Sin bordes: la separación es por superficie (Bg → Surface1 → Surface2).
 */

// ================================================================================================ formato

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

fun shortDate(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("MMM d, yyyy", ES)) }.getOrDefault(iso)

fun ago(iso: String?): String = runCatching {
    val m = (System.currentTimeMillis() - Instant.parse(iso).toEpochMilli()) / 60_000
    when { m < 1 -> t("kt_now"); m < 60 -> t("kt_min_ago", m); m < 1440 -> t("kt_h_ago", m / 60); else -> t("kt_d_ago", m / 1440) }
}.getOrDefault("—")

fun initials(name: String) = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "P" }

// ================================================================================================ íconos

fun icon(name: String): ImageVector = when (name) {
    "food" -> Icons.Outlined.ShoppingCart; "coffee" -> Icons.Outlined.Restaurant; "car" -> Icons.Outlined.DirectionsCar
    "home" -> Icons.Outlined.Home; "bolt" -> Icons.Outlined.Bolt; "wifi" -> Icons.Outlined.Language; "health" -> Icons.Outlined.MonitorHeart
    "bag" -> Icons.Outlined.ShoppingBag; "movie" -> Icons.Outlined.SportsEsports; "school" -> Icons.Outlined.School; "tv" -> Icons.Outlined.Tv
    "card" -> Icons.Outlined.Handshake; "shield" -> Icons.Outlined.Umbrella; "pets" -> Icons.Outlined.Pets; "kids" -> Icons.Outlined.ChildCare
    "plane" -> Icons.Outlined.Flight; "gift" -> Icons.Outlined.CardGiftcard; "spa" -> Icons.Outlined.Person; "build" -> Icons.Outlined.Work
    "receipt" -> Icons.Outlined.Description; "percent" -> Icons.Outlined.Percent; "savings" -> Icons.Outlined.Savings
    "money" -> Icons.Outlined.AttachMoney; "work" -> Icons.Outlined.Work; "laptop" -> Icons.Outlined.Language; "store" -> Icons.Outlined.Work
    "trending" -> Icons.Outlined.TrendingUp; "swap" -> Icons.Outlined.SwapHoriz; "flag" -> Icons.Outlined.Flag
    "beach" -> Icons.Outlined.BeachAccess; "phone" -> Icons.Outlined.Smartphone; "more" -> Icons.Outlined.MoreHoriz
    else -> Icons.Outlined.Category
}

fun catColor(c: Category) = Color(c.color)

/** Íconos de billetera de la web (utils/iconUtils.tsx ACCOUNT_ICONS), en el mismo orden. */
val ACCOUNT_ICON_KEYS = listOf("wallet", "bank", "card", "visa", "mastercard", "cash", "crypto", "paypal", "mobile", "savings", "binance", "zelle",
    "venmo", "mercadopago", "cashapp", "applepay", "googlepay", "stripe")

/** Logos de marca (Simple Icons, CC0) en res/drawable/brand_*.xml. */
private val BRAND = mapOf("visa" to R.drawable.brand_visa, "mastercard" to R.drawable.brand_mastercard, "crypto" to R.drawable.brand_bitcoin,
    "paypal" to R.drawable.brand_paypal, "binance" to R.drawable.brand_binance, "zelle" to R.drawable.brand_zelle, "venmo" to R.drawable.brand_venmo,
    "mercadopago" to R.drawable.brand_mercadopago, "cashapp" to R.drawable.brand_cashapp, "applepay" to R.drawable.brand_applepay,
    "googlepay" to R.drawable.brand_googlepay, "stripe" to R.drawable.brand_stripe)

/** Color original de cada marca; en modo oscuro/negro la variante clara para que se lea sobre fondo oscuro. */
fun accountColor(key: String): Color {
    val dark = Prefs.theme.dark
    return when (key) {
        "visa" -> Color(if (dark) 0xFF5B7BFF else 0xFF1A1F71); "paypal" -> Color(if (dark) 0xFF009CDE else 0xFF003087)
        "mastercard" -> Color(0xFFEB001B); "crypto" -> Color(0xFFF7931A); "binance" -> Color(if (dark) 0xFFF0B90B else 0xFFC99400)
        "zelle" -> Color(if (dark) 0xFFA272F0 else 0xFF6D1ED4); "venmo" -> Color(if (dark) 0xFF3D95CE else 0xFF008CFF)
        "mercadopago" -> Color(0xFF00B1EA); "cashapp" -> Color(if (dark) 0xFF00D632 else 0xFF00A63A); "googlepay" -> Color(0xFF4285F4)
        "stripe" -> Color(if (dark) 0xFF7A73FF else 0xFF635BFF); "applepay" -> Ink; "cash" -> Income
        else -> Brand
    }
}

private fun genericAccountIcon(key: String): ImageVector = when (key) {
    "bank" -> Icons.Outlined.AccountBalance; "card" -> Icons.Outlined.CreditCard; "cash" -> Icons.Outlined.Payments
    "mobile" -> Icons.Outlined.Smartphone; "savings" -> Icons.Outlined.Savings; else -> Icons.Outlined.AccountBalanceWallet
}

/** Ícono de billetera: logo de marca si existe, si no el genérico. */
@Composable
fun AccountGlyph(key: String, size: Dp = 22.dp, tint: Color = accountColor(key)) {
    val res = BRAND[key]
    if (res != null) Icon(painterResource(res), key, tint = tint, modifier = Modifier.size(size))
    else Icon(genericAccountIcon(key), key, tint = tint, modifier = Modifier.size(size))
}

/** Cuadro con el ícono de la billetera en su color de marca. */
@Composable
fun AccountTile(key: String, size: Dp = 46.dp) {
    val c = accountColor(key)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        AccountGlyph(key, size * 0.5f, c)
    }
}

// ================================================================================================ interacción

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

/**
 * Reordenar arrastrando en un LazyColumn (los DnD de la web). Los ítems usan [reorderItem] y el asa [DragHandle];
 * [onMove] recibe las claves (origen, destino) y la vista mueve su lista (ver [move]).
 */
class ReorderState(private val list: LazyListState) {
    var onMove: (Any, Any) -> Unit = { _, _ -> }
    var dragging by mutableStateOf<Any?>(null); private set
    var offset by mutableFloatStateOf(0f); private set
    fun start(key: Any) { dragging = key; offset = 0f }
    fun end() { dragging = null; offset = 0f }
    fun drag(dy: Float, keys: Set<Any>) {
        offset += dy
        val info = list.layoutInfo.visibleItemsInfo
        val cur = info.firstOrNull { it.key == dragging } ?: return
        val mid = cur.offset + offset + cur.size / 2f
        val target = info.firstOrNull { it.key != dragging && it.key in keys && mid >= it.offset && mid <= it.offset + it.size } ?: return
        onMove(cur.key, target.key)
        offset += cur.offset - target.offset // el ítem salta a la posición del destino: compensar para que siga bajo el dedo
    }
}

@Composable
fun rememberReorder(list: LazyListState, onMove: (Any, Any) -> Unit) = remember(list) { ReorderState(list) }.also { it.onMove = onMove }

fun Modifier.reorderItem(st: ReorderState, key: Any) = composed {
    val on = st.dragging == key
    val lift by animateFloatAsState(if (on) 1.03f else 1f, label = "lift")
    zIndex(if (on) 1f else 0f).graphicsLayer { translationY = if (on) st.offset else 0f; scaleX = lift; scaleY = lift; shadowElevation = if (on) 24f else 0f }
}

/** Asa de arrastre (⋮⋮): mantener presionado y mover. [keys] = claves que admiten intercambio. */
@Composable
fun DragHandle(st: ReorderState, key: Any, keys: Set<Any>) {
    Icon(Icons.Outlined.DragIndicator, t("kt_reorder"), tint = Muted.copy(alpha = 0.6f), modifier = Modifier.size(32.dp).pointerInput(key, keys) {
        detectDragGesturesAfterLongPress(onDragStart = { st.start(key) }, onDragEnd = st::end, onDragCancel = st::end) { c, d -> c.consume(); st.drag(d.y, keys) }
    }.padding(4.dp))
}

/** Mueve el elemento con clave [from] a la posición de [to]. */
fun <T> List<T>.move(from: Any, to: Any, key: (T) -> Any): List<T> {
    val a = indexOfFirst { key(it) == from }; val b = indexOfFirst { key(it) == to }
    if (a < 0 || b < 0) return this
    return toMutableList().apply { add(b, removeAt(a)) }
}

// ================================================================================================ superficies y texto

/** Panel base: bg-theme-surface + rounded-2xl, sin borde. Variantes por [color] y [radius]. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, radius: Dp = 24.dp, color: Color = Surface1,
              content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.tap(onClick = onClick).clip(RoundedCornerShape(radius)).background(color), content = content)
}

/** Rótulo chico en mayúsculas con tracking amplio. */
@Composable
fun Caps(text: String, modifier: Modifier = Modifier, color: Color = Muted.copy(alpha = 0.75f), maxLines: Int = 1) =
    Text(text.uppercase(ES), modifier, style = MaterialTheme.typography.labelSmall, color = color, letterSpacing = 2.sp, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis)

/** Texto de una línea que reduce su tamaño para no cortarse (saldos grandes). Solo se achica. */
@Composable
fun AutoText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    Text(text, modifier, style = style.copy(fontSize = style.fontSize * scale), color = color, maxLines = 1, softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && scale > 0.5f) scale *= 0.9f })
}

/** Etiqueta en cápsula de color (estado, moneda, días). */
@Composable
fun Pill(text: String, color: Color = Muted, filled: Boolean = true) =
    Text(text, Modifier.clip(CircleShape).background(if (filled) color.copy(alpha = 0.14f) else Color.Transparent).padding(horizontal = 9.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)

/** Encabezado de cada vista: volver, título, subtítulo, acciones. */
@Composable
fun ViewHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) { RoundIcon(Icons.Outlined.ArrowBack, t("back"), onBack); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            AutoText(title, MaterialTheme.typography.headlineSmall, Ink)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun Section(title: String, action: String? = null, icon: ImageVector? = null, iconTint: Color = Brand, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = iconTint, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp)) }
        Caps(title, Modifier.weight(1f))
        if (action != null) Text(action.uppercase(ES), Modifier.tap(onClick = onAction).padding(4.dp), style = MaterialTheme.typography.labelSmall,
            color = Brand, letterSpacing = 1.sp)
    }
}

// ================================================================================================ botones

/** Botón circular (volver, ojo, cerrar). */
@Composable
fun RoundIcon(icon: ImageVector, desc: String?, onClick: () -> Unit, tint: Color = Ink, size: Dp = 44.dp, bg: Color = Surface1) {
    Box(Modifier.size(size).tap(onClick = onClick).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

/** Acción principal de una vista (el + de color de marca de la web). */
@Composable
fun AddButton(desc: String, onClick: () -> Unit) = RoundIcon(Icons.Outlined.Add, desc, onClick, tint = Color.White, bg = Brand)

/** Botón principal: bg-theme-brand, blanco, font-black, rounded-2xl. */
@Composable
fun BrandButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, color: Color = Brand, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 48.dp).tap(onClick = if (enabled) onClick else null).clip(RoundedCornerShape(16.dp))
        .background(if (enabled) color else color.copy(alpha = 0.35f)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botón secundario sobre una superficie. */
@Composable
fun GhostButton(text: String, icon: ImageVector? = null, color: Color = Ink, modifier: Modifier = Modifier, bg: Color = Surface1, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 40.dp).tap(onClick = onClick).clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Texto-enlace en mayúsculas (Limpiar filtros, Quitar comprados). */
@Composable
fun LinkText(text: String, color: Color = Brand, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Text(text.uppercase(ES), modifier.tap(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp), style = MaterialTheme.typography.labelSmall,
        color = color, letterSpacing = 1.sp)

// ================================================================================================ filas

/** Ícono en cuadro de color (bg-x-500/20 text-x-400 de la web). [badge] = mini botón + (el de Planificado). */
@Composable
fun Bubble(icon: ImageVector, tint: Color = Brand, size: Dp = 46.dp, badge: (() -> Unit)? = null) {
    Box {
        Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.48f))
        }
        if (badge != null) Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(22.dp).tap(onClick = badge).clip(CircleShape).background(Income),
            contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

/**
 * Fila de lista estándar. Variantes por parámetros: ícono o [leading] propio, subtítulo, [pill], monto con secundario,
 * contenido a la derecha ([trailingSlot]) y asa de arrastre ([handle]).
 */
@Composable
fun ListRow(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null, icon: ImageVector? = null, tint: Color = Brand,
    leading: (@Composable () -> Unit)? = null, pill: (@Composable () -> Unit)? = null,
    trailing: String? = null, trailingColor: Color = Ink, trailingSub: String? = null,
    trailingSlot: (@Composable () -> Unit)? = null, handle: (@Composable () -> Unit)? = null,
    titleColor: Color = Ink, strike: Boolean = false, color: Color = Surface1,
    onLongClick: (() -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp).tap(onLongClick, onClick).clip(RoundedCornerShape(18.dp)).background(color)
        .padding(start = if (handle != null) 4.dp else 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        handle?.let { it(); Spacer(Modifier.width(4.dp)) }
        if (leading != null) leading() else if (icon != null) Bubble(icon, tint)
        if (leading != null || icon != null) Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = titleColor,
                textDecoration = if (strike) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
            subtitle?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Muted) }
            pill?.let { Spacer(Modifier.height(6.dp)); it() }
        }
        if (trailing != null || trailingSlot != null) {
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                trailing?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = trailingColor, maxLines = 1) }
                trailingSub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Muted.copy(alpha = 0.7f), maxLines = 1) }
                trailingSlot?.invoke()
            }
        }
    }
}

/** Atajo de [ListRow]: fila con ícono y valor. */
@Composable
fun Row2(icon: ImageVector, title: String, subtitle: String? = null, trailing: String? = null, trailingColor: Color = Ink, tint: Color = Brand,
         onLongClick: (() -> Unit)? = null, extra: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null) =
    ListRow(title, subtitle = subtitle, icon = icon, tint = tint, trailing = trailing, trailingColor = trailingColor, trailingSlot = extra,
        onLongClick = onLongClick, onClick = onClick)

/** Tarjeta de cifra: rótulo, valor grande (autoajustable) y línea secundaria; [content] agrega lo propio. */
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Ink, sub: String? = null, subColor: Color = Muted,
             content: @Composable ColumnScope.() -> Unit = {}) {
    GlassCard(modifier) {
        Column(Modifier.padding(20.dp)) {
            Caps(label, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            AutoText(value, MaterialTheme.typography.headlineMedium, valueColor)
            sub?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = subColor) }
            content()
        }
    }
}

/** Tarjeta de progreso (sobres de presupuesto, metas): ícono, título, estado, monto «de» total y barra. */
@Composable
fun ProgressCard(title: String, icon: ImageVector, tint: Color, value: Double, max: Double, status: String, statusColor: Color,
                 amount: String, of: String, modifier: Modifier = Modifier, amountSub: String? = null, barColor: Color = statusColor,
                 footer: (@Composable RowScope.() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    GlassCard(modifier.fillMaxWidth().padding(vertical = 5.dp), onClick = onClick) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Bubble(icon, tint, 48.dp); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(status, style = MaterialTheme.typography.labelMedium, color = statusColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(amount, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1)
                    amountSub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Muted.copy(alpha = 0.7f)) }
                    Text(of, style = MaterialTheme.typography.labelMedium, color = Muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            Bar(if (max > 0) (value / max).toFloat() else 0f, barColor)
            footer?.let { Spacer(Modifier.height(12.dp)); Row(verticalAlignment = Alignment.CenterVertically, content = it) }
        }
    }
}

@Composable
fun Empty(icon: ImageVector, title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Surface1), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Brand, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); BrandButton(action, icon = Icons.Outlined.Add, onClick = onAction) }
    }
}

/** Aviso de sección vacía (recuadro tenue de la web: «No hay ingresos programados»). */
@Composable
fun EmptyNote(text: String) = Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1.copy(alpha = 0.5f)).padding(22.dp),
    style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)

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
    colors = SwitchDefaults.colors(checkedTrackColor = Brand, checkedThumbColor = Color.White, uncheckedTrackColor = Surface2,
        uncheckedThumbColor = Muted, uncheckedBorderColor = Color.Transparent, checkedBorderColor = Color.Transparent))

/** Fila de ajuste: texto y descripción a la izquierda, control a la derecha. */
@Composable
fun SettingRow(title: String, desc: String? = null, titleColor: Color = Ink, icon: ImageVector? = null, tint: Color = Brand,
               onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().tap(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Bubble(it, tint, 40.dp); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            desc?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
        Spacer(Modifier.width(12.dp)); trailing()
    }
}

@Composable
fun Chevron() = Icon(Icons.Outlined.ChevronRight, null, tint = Muted)

/** Grupo de ajustes en un panel con rótulo e ícono (tarjetas del Perfil y Ajustes). */
@Composable
fun SettingsGroup(label: String, icon: ImageVector, tint: Color = Brand, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp))
                Caps(label, color = if (tint == Expense) Expense else Muted.copy(alpha = 0.75f))
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Cuadrícula de opciones seleccionables (idioma, barra inferior, modos, íconos). */
@Composable
fun <T> OptionGrid(options: List<T>, selected: (T) -> Boolean, columns: Int = 3, enabled: (T) -> Boolean = { true }, color: (T) -> Color = { Brand },
                   container: Color = Bg, onPick: (T) -> Unit, content: @Composable ColumnScope.(T, Boolean) -> Unit) {
    options.chunked(columns).forEach { row ->
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { o ->
                val on = selected(o)
                val bg by animateColorAsState(if (on) color(o) else container, tween(200), label = "opt")
                Column(Modifier.weight(1f).tap(onClick = if (enabled(o)) { { onPick(o) } } else null).clip(RoundedCornerShape(16.dp)).background(bg)
                    .padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) { content(o, on) }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Paleta de colores de acento. */
@Composable
fun Swatches(colors: List<Color>, selected: Color, onPick: (Color) -> Unit) {
    colors.chunked(6).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            row.forEach { c ->
                Box(Modifier.size(40.dp).tap(onClick = { onPick(c) }).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                    if (c == selected) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ================================================================================================ hojas y formularios

/** ModalBottomSheet con el estilo de los modales de la web (bg-theme-surface, rounded-t-[2rem]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface1,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), dragHandle = {
            Box(Modifier.padding(vertical = 12.dp).size(40.dp, 4.dp).clip(CircleShape).background(Surface2))
        }, content = content)

/** Hoja con título, subtítulo y cerrar; el contenido desplaza. */
@Composable
fun TitledSheet(title: String, subtitle: String? = null, onDismiss: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) = Sheet(onDismiss) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding().let { if (scroll) it.verticalScrollable() else it }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink)
                subtitle?.let { Caps(it) }
            }
            RoundIcon(Icons.Outlined.Close, t("close"), onDismiss, tint = Muted, size = 40.dp, bg = Bg)
        }
        content()
    }
}

/** Hoja común para crear/editar: título, campos, Guardar y (si se edita) Eliminar. */
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean = true, onDelete: (() -> Unit)? = null,
              content: @Composable ColumnScope.() -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding().verticalScrollable(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.weight(1f))
                if (onDelete != null) RoundIcon(Icons.Outlined.Delete, t("delete"), { confirm = true }, tint = Expense, size = 40.dp, bg = Expense.copy(alpha = 0.12f))
            }
            content()
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("cancel"), modifier = Modifier.weight(1f).heightIn(min = 48.dp), bg = Bg, onClick = onDismiss)
                BrandButton(t("save"), Modifier.weight(1f), Icons.Outlined.Check, saveEnabled, onClick = onSave)
            }
        }
    }
    if (confirm) Confirm(t("kt_delete_q"), t("kt_cannot_undo"), t("delete"), danger = true, onDismiss = { confirm = false }) { confirm = false; onDelete?.invoke() }
}

@Composable
fun Modifier.verticalScrollable() = this.verticalScroll(androidx.compose.foundation.rememberScrollState())

@Composable
fun Confirm(title: String, text: String, action: String, danger: Boolean = false, enabled: Boolean = true, onDismiss: () -> Unit,
            content: (@Composable () -> Unit)? = null, onConfirm: () -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, containerColor = Surface1, shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = Ink, style = MaterialTheme.typography.titleLarge) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { if (text.isNotEmpty()) Text(text, color = Muted); content?.invoke() } },
        confirmButton = { BrandButton(action, color = if (danger) Expense else Brand, enabled = enabled, onClick = onConfirm) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel"), color = Muted) } })

@Composable
fun fieldColors(container: Color = Bg) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = container, unfocusedContainerColor = container, disabledContainerColor = container,
    focusedBorderColor = Brand, unfocusedBorderColor = Color.Transparent, disabledBorderColor = Color.Transparent,
    focusedLabelColor = Brand, unfocusedLabelColor = Muted,
    focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Brand, focusedLeadingIconColor = Muted, unfocusedLeadingIconColor = Muted,
    focusedTrailingIconColor = Muted, unfocusedTrailingIconColor = Muted, focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted,
    focusedSuffixColor = Muted, unfocusedSuffixColor = Muted)

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, number: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(), suffix: String? = null,
          container: Color = Bg) =
    OutlinedTextField(value, onChange, modifier, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
        suffix = suffix?.let { { Text(it) } }, shape = RoundedCornerShape(16.dp), colors = fieldColors(container),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default)

/** Buscador redondeado sobre la superficie. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) =
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(Surface1),
        trailingIcon = if (value.isNotEmpty()) { { IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, t("kt_clear"), tint = Muted) } } } else null)

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

/** Selector de pastillas / pestañas: opción activa en color, con ícono opcional. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, colorOf: (T) -> Color = { Brand }, icons: ((T) -> ImageVector)? = null,
                  container: Color = Bg, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(container).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (k, label) ->
            val on = k == selected
            val bg by animateColorAsState(if (on) colorOf(k) else Color.Transparent, tween(200), label = "seg")
            Row(Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(14.dp)).background(bg).tap(onClick = { onSelect(k) }).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                icons?.let { Icon(it(k), null, tint = if (on) Color.White else Muted, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                Text(label, color = if (on) Color.White else Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Campo de fecha con aspecto de campo y el selector de Material 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tap(onClick = { open = true }).clip(RoundedCornerShape(16.dp)).background(Bg)
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

/** Botón de filtro con menú desplegable (popups de filtros de la web). */
@Composable
fun <T> FilterButton(label: String, icon: ImageVector, active: Boolean, options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier,
                     onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(Modifier.fillMaxWidth().tap(onClick = { open = true }).clip(RoundedCornerShape(16.dp)).background(if (active) Brand.copy(alpha = 0.18f) else Surface1)
            .padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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

/** Selector de moneda en cápsula con menú. */
@Composable
fun CurrencyPill(c: Currency, options: List<Currency> = Currency.entries, onPick: (Currency) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.tap(onClick = { open = true }).clip(CircleShape).background(Bg).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (c == Currency.VES) "Bs" else symbol(c), color = if (c == Currency.VES) RateEur else RateUsd, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp)); Text(c.name, color = Ink, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(open, { open = false }, Modifier.background(Surface2)) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o.name, color = if (o == c) Brand else Ink, fontWeight = FontWeight.Black) },
                onClick = { onPick(o); open = false }) }
        }
    }
}

/** Botón redondo de moneda de visualización ($ / Bs / €), como en la web. */
@Composable
fun DisplayCurrencyButton(size: Dp = 44.dp) {
    val cur = Prefs.display
    Box(Modifier.size(size).tap(onClick = Prefs::cycleDisplay).clip(CircleShape).background(if (cur != Currency.USD) Brand else Surface1),
        contentAlignment = Alignment.Center) {
        Text(if (cur == Currency.VES) "Bs" else symbol(cur), color = if (cur != Currency.USD) Color.White else Ink, style = MaterialTheme.typography.labelLarge)
    }
}
