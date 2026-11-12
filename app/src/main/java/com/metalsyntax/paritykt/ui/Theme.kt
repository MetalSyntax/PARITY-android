package com.metalsyntax.paritykt.ui

import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metalsyntax.paritykt.data.Currency
import com.metalsyntax.paritykt.data.detectPayCycle

/** Colores resueltos del tema actual (modo + acento). */
data class Palette(val bg: Color, val surface: Color, val card: Color, val ink: Color, val muted: Color, val brand: Color, val dark: Boolean)

/** Modos como en Pulsix: claro, oscuro (zinc) y negro (OLED, el «Original Dark» de la web). */
enum class Mode(val key: String, val bg: Long, val surface: Long, val card: Long, val ink: Long, val muted: Long) {
    LIGHT("kt_mode_light", 0xFFFFFFFF, 0xFFF4F4F5, 0xFFE4E4E7, 0xFF18181B, 0xFF71717A),
    DARK("kt_mode_dark", 0xFF18181B, 0xFF232327, 0xFF2E2E33, 0xFFFFFFFF, 0xFFA1A1AA),
    BLACK("kt_mode_black", 0xFF000000, 0xFF121212, 0xFF1E1E1E, 0xFFFFFFFF, 0xFFA1A1AA),
}

/** Paleta de acento (Tailwind 500/600); el índigo #4f46e5 es el de la web. */
val ACCENTS = listOf(0xFF4F46E5, 0xFF7C3AED, 0xFF9333EA, 0xFFC026D3, 0xFFDB2777, 0xFFE11D48, 0xFFDC2626, 0xFFEA580C, 0xFFD97706,
    0xFFCA8A04, 0xFF65A30D, 0xFF16A34A, 0xFF059669, 0xFF0D9488, 0xFF0891B2, 0xFF0284C7, 0xFF2563EB, 0xFF475569).map { Color(it) }

/** Vistas que pueden ir en la barra inferior (como navbarFavorites de la web; Inicio va siempre). */
enum class Route(val key: String, val webId: String) {
    HOME("dashboard", "DASHBOARD"), TX("transactions", "TRANSACTIONS"), ANALYSIS("analysis", "ANALYSIS"), BUDGET("budget", "BUDGET"),
    GOALS("goals", "GOALS"), WALLETS("wallet", "WALLET"), PLAN("scheduled", "SCHEDULED"), SHOPPING("shoppingList", "SHOPPING_LIST"),
    PROFILE("profile", "PROFILE"), HUB("kt_plan_hub", "PLAN_HUB"), // HUB: centro «Plan» de Parity Modern
}

/** Variante del A/B (flavor): Parity Modern = rediseño de docs/REDISENO_DASHBOARD.md. */
val MODERN = com.metalsyntax.paritykt.BuildConfig.MODERN

/** En Modern el Inicio ya trae saldo, acciones, «Para hoy» y recientes; estos widgets son extras opcionales (apagados por defecto). */
val MODERN_EXTRAS = listOf(Widget.CONVERTER, Widget.WALLETS, Widget.EXPENSES, Widget.INCOME_EXPENSE, Widget.BUDGET, Widget.GOALS)

/** Widgets del Inicio que se pueden ocultar y reordenar (Personalizar Dashboard de la web). */
enum class Widget(val key: String, val on: Boolean = true) {
    QUICK("quickActions"), BALANCE("totalBalance"), CONVERTER("currencyConverter"), WALLETS("wallets"), EXPENSES("kt_expense_structure"),
    INCOME_EXPENSE("incomeVsExpenses", false), UPCOMING("kt_next_7_days"), BUDGET("budget"), GOALS("goals"), RECENT("recentTransactions"),
}

/**
 * Preferencias de interfaz (en la web viven en localStorage, no en el respaldo). Son estado de Compose: cambiarlas recompone.
 */
object Prefs {
    private lateinit var sp: android.content.SharedPreferences
    var mode by mutableStateOf(Mode.BLACK); private set
    var accent by mutableStateOf(ACCENTS[0]); private set
    var favorites by mutableStateOf(listOf(Route.WALLETS, Route.ANALYSIS, Route.PROFILE)); private set
    var balanceVisible by mutableStateOf(true); private set
    var display by mutableStateOf(Currency.USD); private set
    var widgets by mutableStateOf(Widget.entries.toList()); private set       // orden
    var hidden by mutableStateOf(Widget.entries.filter { !it.on || (MODERN && it in MODERN_EXTRAS) }.toSet()); private set
    var quick by mutableStateOf(QUICK_DEFAULT); private set                    // acciones rápidas, en orden
    var hideWelcome by mutableStateOf(false); private set
    var hideName by mutableStateOf(false); private set
    var txLimit by mutableIntStateOf(6); private set
    var dev by mutableStateOf(false); private set
    var onboarded by mutableStateOf(true); private set
    var payCycle by mutableStateOf("AUTO"); private set                       // AUTO o un PayCycle

    val theme get() = mode.let { m -> Palette(Color(m.bg), Color(m.surface), Color(m.card), Color(m.ink), Color(m.muted), accent, m != Mode.LIGHT) }

    private inline fun <reified E : Enum<E>> list(k: String, def: List<E>): List<E> =
        sp.getString(k, null)?.split(',')?.mapNotNull { n -> enumValues<E>().firstOrNull { it.name == n } } ?: def

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
        mode = Mode.entries.firstOrNull { it.name == sp.getString("mode", null) } ?: if (sp.getString("theme", "")!!.startsWith("light")) Mode.LIGHT else Mode.BLACK
        accent = Color(sp.getLong("accent", ACCENTS[0].value.toLong()).toULong())
        favorites = list("favorites", favorites)
        balanceVisible = sp.getBoolean("balanceVisible", true)
        display = Currency.entries.firstOrNull { it.name == sp.getString("display", null) } ?: Currency.USD
        // widgets nuevos que no estaban en el orden guardado van al final
        widgets = list("widgets", Widget.entries.toList()).let { it + (Widget.entries - it.toSet()) }
        hidden = list("hidden", hidden.toList()).toSet()
        // antes se podían desmarcar; ahora las casillas solo se cambian: mínimo 4 para que la fila no quede vacía
        quick = list("quick", QUICK_DEFAULT).let { q -> if (q.size >= 4) q else q + (QUICK_DEFAULT - q.toSet()).take(4 - q.size) }
        hideWelcome = sp.getBoolean("hideWelcome", false); hideName = sp.getBoolean("hideName", false)
        txLimit = sp.getInt("txLimit", 6); dev = sp.getBoolean("dev", false)
        payCycle = sp.getString("payCycle", "AUTO")!!
        onboarded = sp.getBoolean("onboarded", sp.contains("theme") || sp.contains("mode")) // instalaciones previas no repiten el onboarding
    }

    private fun put(k: String, v: Any) = sp.edit().apply {
        when (v) { is Boolean -> putBoolean(k, v); is Int -> putInt(k, v); is Long -> putLong(k, v); else -> putString(k, v.toString()) }
    }.apply()

    fun applyMode(m: Mode) { mode = m; put("mode", m.name) }
    fun applyAccent(c: Color) { accent = c; put("accent", c.value.toLong()) }
    fun saveFavorites(r: List<Route>) { favorites = r.take(3); put("favorites", favorites.joinToString(",") { it.name }) }
    fun toggleBalance() { balanceVisible = !balanceVisible; put("balanceVisible", balanceVisible) }
    @JvmName("applyBalanceVisible") fun setBalanceVisible(v: Boolean) { balanceVisible = v; put("balanceVisible", v) }
    @JvmName("applyDisplay") fun setDisplay(c: Currency) { display = c; put("display", c.name) }
    /** USD → VES → EUR → USD, como el botón de moneda del balance de la web. */
    fun cycleDisplay() = setDisplay(when (display) { Currency.USD -> Currency.VES; Currency.VES -> Currency.EUR; else -> Currency.USD })
    fun saveWidgets(order: List<Widget>) { widgets = order; put("widgets", order.joinToString(",") { it.name }) }
    fun toggleWidget(w: Widget) { hidden = if (w in hidden) hidden - w else hidden + w; put("hidden", hidden.joinToString(",") { it.name }) }
    fun saveQuick(r: List<Route>) { quick = r; put("quick", r.joinToString(",") { it.name }) }
    @JvmName("applyHideWelcome") fun setHideWelcome(v: Boolean) { hideWelcome = v; put("hideWelcome", v) }
    @JvmName("applyHideName") fun setHideName(v: Boolean) { hideName = v; put("hideName", v) }
    @JvmName("applyTxLimit") fun setTxLimit(v: Int) { txLimit = v.coerceIn(3, 20); put("txLimit", txLimit) }
    @JvmName("applyDev") fun setDev(v: Boolean) { dev = v; put("dev", v) }
    @JvmName("applyPayCycle") fun setPayCycle(v: String) { payCycle = v; put("payCycle", v) }
    fun finishOnboarding() { onboarded = true; put("onboarded", true) }
}

val QUICK_DEFAULT = listOf(Route.TX, Route.BUDGET, Route.PLAN, Route.ANALYSIS, Route.WALLETS, Route.GOALS, Route.SHOPPING, Route.PROFILE)

// tokens (var(--…) de la web); leen el tema actual
val Bg get() = Prefs.theme.bg            // --bg-primary
val Surface1 get() = Prefs.theme.surface // --bg-secondary (bg-theme-surface)
val Surface2 get() = Prefs.theme.card    // --bg-tertiary
val Ink get() = Prefs.theme.ink          // --text-primary
val Muted get() = Prefs.theme.muted      // --text-secondary
val Brand get() = Prefs.theme.brand      // --primary
val Glass get() = Ink.copy(alpha = 0.05f)        // bg-theme-soft
val Income = Color(0xFF10B981)  // text-emerald-500 (CurrencyAmount)
val Expense = Color(0xFFEF4444) // text-red-500
val Warning = Color(0xFFF59E0B)
val RateUsd = Color(0xFF34D399) // text-emerald-400 (tasa USD del encabezado)
val RateEur = Color(0xFF60A5FA) // text-blue-400

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(w: Int) = androidx.compose.ui.text.font.Font(com.metalsyntax.paritykt.R.font.inter, FontWeight(w),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w)))

val Inter = androidx.compose.ui.text.font.FontFamily(listOf(400, 500, 600, 700, 800, 900).map(::inter))

@Composable
fun ParityTheme(content: @Composable () -> Unit) {
    val pal = Prefs.theme
    val scheme = (if (pal.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = pal.brand, onPrimary = Color.White, primaryContainer = pal.brand.copy(alpha = 0.2f), onPrimaryContainer = pal.ink,
        secondary = pal.brand, secondaryContainer = pal.brand.copy(alpha = 0.15f), onSecondaryContainer = pal.ink,
        tertiary = Income, error = Expense, background = pal.bg, onBackground = pal.ink, surface = pal.bg, onSurface = pal.ink,
        surfaceVariant = pal.card, onSurfaceVariant = pal.muted, outline = pal.muted.copy(alpha = 0.6f), outlineVariant = pal.ink.copy(alpha = 0.1f),
        surfaceContainer = pal.surface, surfaceContainerHigh = pal.surface, surfaceContainerLow = pal.surface, surfaceContainerHighest = pal.card,
        surfaceContainerLowest = pal.bg, inverseSurface = pal.ink, inverseOnSurface = pal.bg,
    )
    val t = Typography()
    fun androidx.compose.ui.text.TextStyle.f(w: FontWeight? = null) = copy(fontFamily = Inter, fontWeight = w ?: fontWeight)
    val type = Typography(
        displayLarge = t.displayLarge.f(FontWeight.Black), displayMedium = t.displayMedium.f(FontWeight.Black),
        displaySmall = t.displaySmall.f(FontWeight.Black).copy(letterSpacing = (-1.5).sp),
        headlineLarge = t.headlineLarge.f(FontWeight.Black), headlineMedium = t.headlineMedium.f(FontWeight.Black).copy(letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.f(FontWeight.Black), titleLarge = t.titleLarge.f(FontWeight.Black),
        titleMedium = t.titleMedium.f(FontWeight.Black), titleSmall = t.titleSmall.f(FontWeight.Black),
        bodyLarge = t.bodyLarge.f(FontWeight.Medium), bodyMedium = t.bodyMedium.f(FontWeight.Medium), bodySmall = t.bodySmall.f(FontWeight.Medium),
        labelLarge = t.labelLarge.f(FontWeight.Black), labelMedium = t.labelMedium.f(FontWeight.Black),
        labelSmall = t.labelSmall.f(FontWeight.Black).copy(fontSize = 10.sp, letterSpacing = 0.sp),
    )
    val r = androidx.compose.foundation.shape.RoundedCornerShape(16.dp) // rounded-2xl de la web
    val shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), medium = r, large = r,
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
}


/**
 * Métricas locales del A/B (docs/REDISENO_DASHBOARD.md §10), iguales en Legacy y Modern para comparar:
 * tiempo de registro (abrir el editor → guardar), % de transacciones con factura y días con registro. Nunca salen del teléfono.
 */
object Metrics {
    private lateinit var sp: android.content.SharedPreferences
    var noSpend by mutableStateOf(emptySet<String>()); private set   // días marcados «hoy no gasté» (racha)
    var saves by mutableStateOf(emptyList<Long>()); private set        // últimas 50 duraciones en ms
    var receipts by mutableIntStateOf(0); private set
    var total by mutableIntStateOf(0); private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("metrics", Context.MODE_PRIVATE)
        noSpend = sp.getStringSet("noSpend", emptySet())!!.toSet()
        saves = sp.getString("saves", "")!!.split(',').mapNotNull { it.toLongOrNull() }
        receipts = sp.getInt("receipts", 0); total = sp.getInt("total", 0)
    }

    fun recordSave(ms: Long, withReceipt: Boolean) {
        saves = (saves + ms).takeLast(50); total++; if (withReceipt) receipts++
        sp.edit().putString("saves", saves.joinToString(",")).putInt("total", total).putInt("receipts", receipts).apply()
    }

    fun markNoSpend(day: String) { noSpend = (noSpend + day).sorted().takeLast(400).toSet(); sp.edit().putStringSet("noSpend", noSpend).apply() }

    fun medianSeconds(): Double? = saves.sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] / 1000.0 }
}


/** Ritmo de cobro en uso: el elegido en Ajustes o, en «Automático», el detectado. */
fun payCycle(d: com.metalsyntax.paritykt.data.AppData = com.metalsyntax.paritykt.data.Store.data): com.metalsyntax.paritykt.data.PayCycle =
    com.metalsyntax.paritykt.data.PayCycle.entries.firstOrNull { it.name == Prefs.payCycle } ?: d.detectPayCycle()
