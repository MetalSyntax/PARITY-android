package com.metalsyntax.paritykt.ui

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metalsyntax.paritykt.data.Currency
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ------------------------------------------------------------------------------------------------ tema

/** Tokens de Parity web (apps/web/tailwind.config.js + themes.json "Original Dark"): negro OLED, vidrio, índigo. */
val Bg = Color(0xFF050505)
val Surface1 = Color(0xFF121212)
val Surface2 = Color(0xFF1E1E1E)
val Glass = Color(0x0DFFFFFF)       // blanco 5 %
val GlassBorder = Color(0x1AFFFFFF) // blanco 10 %
val Indigo = Color(0xFF6366F1)
val Income = Color(0xFF34D399)      // emerald-400
val Expense = Color(0xFFFB7185)     // rose-400
val Muted = Color(0xFFA1A1AA)       // zinc-400

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(w: Int) = androidx.compose.ui.text.font.Font(com.metalsyntax.paritykt.R.font.inter, FontWeight(w),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w)))

val Inter = androidx.compose.ui.text.font.FontFamily(listOf(400, 500, 600, 700, 800, 900).map(::inter))

@Composable
fun ParityTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Indigo, onPrimary = Color.White, primaryContainer = Color(0xFF2E2A7A), onPrimaryContainer = Color(0xFFE0E0FF),
        secondary = Color(0xFF818CF8), secondaryContainer = Color(0xFF26245A), onSecondaryContainer = Color(0xFFE0E0FF),
        tertiary = Income, error = Expense, background = Bg, onBackground = Color.White, surface = Bg, onSurface = Color.White,
        surfaceVariant = Surface2, onSurfaceVariant = Muted, outline = Color(0xFF52525B), outlineVariant = GlassBorder,
        surfaceContainer = Surface1, surfaceContainerHigh = Surface2, surfaceContainerLow = Color(0xFF0C0C0C), surfaceContainerHighest = Color(0xFF262626),
        surfaceContainerLowest = Bg,
    )
    val t = Typography()
    fun androidx.compose.ui.text.TextStyle.f(w: FontWeight? = null) = copy(fontFamily = Inter, fontWeight = w ?: fontWeight)
    val type = Typography(
        displayLarge = t.displayLarge.f(FontWeight.Black), displayMedium = t.displayMedium.f(FontWeight.Black),
        displaySmall = t.displaySmall.f(FontWeight.Black).copy(letterSpacing = (-1).sp),
        headlineLarge = t.headlineLarge.f(FontWeight.Black), headlineMedium = t.headlineMedium.f(FontWeight.Black).copy(letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.f(FontWeight.ExtraBold), titleLarge = t.titleLarge.f(FontWeight.ExtraBold),
        titleMedium = t.titleMedium.f(FontWeight.Bold), titleSmall = t.titleSmall.f(FontWeight.Bold),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(FontWeight.Bold), labelMedium = t.labelMedium.f(FontWeight.Bold), labelSmall = t.labelSmall.f(FontWeight.SemiBold).copy(fontSize = 10.5.sp, letterSpacing = 0.sp),
    )
    val shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
}

/** Tarjeta de vidrio de la web: blanco 5 % con borde blanco 10 %, esquinas grandes. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    val border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
    if (onClick != null) Surface(onClick = onClick, modifier = modifier, shape = shape, color = Glass, border = border) { Column(content = content) }
    else Surface(modifier = modifier, shape = shape, color = Glass, border = border) { Column(content = content) }
}

/** Rótulo chico en mayúsculas, como los encabezados de la web. */
@Composable
fun Caps(text: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(text.uppercase(ES), modifier, style = MaterialTheme.typography.labelSmall, color = color, letterSpacing = 1.2.sp)

// ------------------------------------------------------------------------------------------------ formato

val ES: Locale = Locale("es", "VE")
private val nf = NumberFormat.getNumberInstance(ES).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }

fun symbol(c: Currency) = when (c) { Currency.USD -> "$"; Currency.EUR -> "€"; Currency.VES -> "Bs."; Currency.USDT -> "₮" }
fun money(v: Double, c: Currency = Currency.USD) = "${symbol(c)} ${nf.format(v)}"
fun num(v: Double): String = nf.format(v)
fun parseAmount(s: String): Double? = s.trim().replace(" ", "").let { t ->
    // acepta 1.234,56 y 1234.56
    if (t.contains(',')) t.replace(".", "").replace(',', '.').toDoubleOrNull() else t.toDoubleOrNull()
}

fun dayLabel(iso: String): String = runCatching {
    val d = LocalDate.parse(iso.take(10))
    val today = LocalDate.now()
    when (d) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        today.plusDays(1) -> "Mañana"
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "d MMM yyyy", ES)).replaceFirstChar { it.uppercase() }
    }
}.getOrDefault(iso.take(10))

fun ago(iso: String?): String = runCatching {
    val m = (System.currentTimeMillis() - Instant.parse(iso).toEpochMilli()) / 60_000
    when { m < 1 -> "ahora"; m < 60 -> "hace $m min"; m < 1440 -> "hace ${m / 60} h"; else -> "hace ${m / 1440} d" }
}.getOrDefault("—")

// ------------------------------------------------------------------------------------------------ íconos

fun icon(name: String): ImageVector = when (name) {
    "food" -> Icons.Outlined.ShoppingCart; "coffee" -> Icons.Outlined.LocalCafe; "car" -> Icons.Outlined.DirectionsCar
    "home" -> Icons.Outlined.Home; "bolt" -> Icons.Outlined.Bolt; "wifi" -> Icons.Outlined.Wifi; "health" -> Icons.Outlined.LocalHospital
    "bag" -> Icons.Outlined.ShoppingBag; "movie" -> Icons.Outlined.Movie; "school" -> Icons.Outlined.School; "tv" -> Icons.Outlined.Tv
    "card" -> Icons.Outlined.CreditCard; "shield" -> Icons.Outlined.Shield; "pets" -> Icons.Outlined.Pets; "kids" -> Icons.Outlined.ChildCare
    "plane" -> Icons.Outlined.Flight; "gift" -> Icons.Outlined.CardGiftcard; "spa" -> Icons.Outlined.Spa; "build" -> Icons.Outlined.Build
    "receipt" -> Icons.Outlined.Receipt; "percent" -> Icons.Outlined.Percent; "savings" -> Icons.Outlined.Savings
    "money" -> Icons.Outlined.Payments; "work" -> Icons.Outlined.Work; "laptop" -> Icons.Outlined.Laptop; "store" -> Icons.Outlined.Store
    "trending" -> Icons.Outlined.TrendingUp; "swap" -> Icons.Outlined.SwapHoriz; "wallet" -> Icons.Outlined.AccountBalanceWallet
    "bank" -> Icons.Outlined.AccountBalance; "cash" -> Icons.Outlined.Money; "flag" -> Icons.Outlined.Flag
    else -> Icons.Outlined.Category
}

// ------------------------------------------------------------------------------------------------ bloques

@Composable
fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Caps(title, Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(action, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
fun Bubble(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Fila estándar de lista: ícono en burbuja, título, subtítulo y valor a la derecha. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Row2(
    icon: ImageVector, title: String, subtitle: String? = null, trailing: String? = null, trailingColor: Color = Color.Unspecified,
    tint: Color = MaterialTheme.colorScheme.primary, onLongClick: (() -> Unit)? = null,
    extra: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null, // último: una lambda final es siempre el clic
) {
    ListItem(
        modifier = Modifier.padding(vertical = 3.dp).clip(MaterialTheme.shapes.medium).background(Glass)
            .border(1.dp, GlassBorder, MaterialTheme.shapes.medium).combinedClickable(enabled = onClick != null || onLongClick != null,
            onClick = { onClick?.invoke() }, onLongClick = onLongClick),
        leadingContent = { Bubble(icon, tint) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        supportingContent = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = if (trailing != null || extra != null) { {
            Column(horizontalAlignment = Alignment.End) {
                trailing?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = trailingColor, fontWeight = FontWeight.SemiBold) }
                extra?.invoke()
            }
        } } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun Empty(icon: ImageVector, title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(16.dp)); FilledTonalButton(onClick = onAction) { Text(action) } }
    }
}

@Composable
fun Progress(label: String, value: Double, max: Double, caption: String) {
    val f = if (max > 0) (value / max).toFloat() else 0f
    val over = f > 1f
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Text(caption, style = MaterialTheme.typography.bodyMedium,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { f.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            color = if (over) Expense else if (f > 0.8f) Color(0xFFF59E0B) else Indigo, trackColor = Surface2)
    }
}

// ------------------------------------------------------------------------------------------------ formularios

/** Hoja inferior común para crear/editar: título, campos, Guardar y (si se edita) Eliminar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean = true, onDelete: (() -> Unit)? = null,
              content: @Composable ColumnScope.() -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) OutlinedButton(onClick = { confirm = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text("Eliminar")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(onClick = onSave, enabled = saveEnabled) { Text("Guardar") }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("¿Eliminar?") }, text = { Text("No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete?.invoke() }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, number: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(), suffix: String? = null) =
    OutlinedTextField(value, onChange, modifier, label = { Text(label) }, singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default)

/** Desplegable de opciones (clave → texto). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Picker(label: String, options: List<Pair<T, String>>, selected: T?, modifier: Modifier = Modifier.fillMaxWidth(), onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(options.firstOrNull { it.first == selected }?.second ?: "", {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) })
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { (k, t) -> DropdownMenuItem(text = { Text(t) }, onClick = { onSelect(k); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (k, t) ->
            SegmentedButton(selected = k == selected, onClick = { onSelect(k) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(t) }
        }
    }
}

/** Campo de fecha (YYYY-MM-DD) con el selector de Material 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Event, null); Spacer(Modifier.width(8.dp))
        Text("$label: ${date.format(DateTimeFormatter.ofPattern("EEE d 'de' MMMM yyyy", ES))}")
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = {
                st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()) }; open = false
            }) { Text("Listo") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }) { DatePicker(st) }
    }
}
