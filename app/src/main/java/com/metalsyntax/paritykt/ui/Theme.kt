package com.metalsyntax.paritykt.ui

import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metalsyntax.paritykt.data.Currency

/** Temas de apps/web/themes.json (mismos nombres y colores). */
data class Palette(val id: String, val label: String, val bg: Color, val surface: Color, val card: Color, val ink: Color, val muted: Color,
                   val brand: Color, val brandHover: Color) {
    val dark get() = bg.luminanceSimple() < 0.5f
}

private fun Color.luminanceSimple() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
private val DARK = arrayOf(Color(0xFF000000), Color(0xFF121212), Color(0xFF1E1E1E), Color.White, Color(0xFFA1A1AA))
private val LIGHT = arrayOf(Color.White, Color(0xFFF4F4F5), Color(0xFFE4E4E7), Color(0xFF18181B), Color(0xFF71717A))
private fun p(id: String, label: String, base: Array<Color>, brand: Long, hover: Long) =
    Palette(id, label, base[0], base[1], base[2], base[3], base[4], Color(brand), Color(hover))

val THEMES = listOf(
    p("original", "Original Dark", DARK, 0xFF4F46E5, 0xFF4338CA), p("midnight_ocean", "Midnight Ocean", DARK, 0xFF0EA5E9, 0xFF0284C7),
    p("forest", "Forest", DARK, 0xFF22C55E, 0xFF16A34A), p("light", "Light", LIGHT, 0xFF4F46E5, 0xFF4338CA),
    p("light_blue", "Light Blue", LIGHT, 0xFF0EA5E9, 0xFF0284C7), p("light_green", "Light Green", LIGHT, 0xFF22C55E, 0xFF16A34A),
)

/** Vistas que pueden ir en la barra inferior (como navbarFavorites de la web; Inicio va siempre). */
enum class Route(val key: String, val webId: String) {
    HOME("dashboard", "DASHBOARD"), TX("transactions", "TRANSACTIONS"), ANALYSIS("analysis", "ANALYSIS"), BUDGET("budget", "BUDGET"),
    GOALS("goals", "GOALS"), WALLETS("wallet", "WALLET"), PLAN("scheduled", "SCHEDULED"), SHOPPING("shoppingList", "SHOPPING_LIST"),
    PROFILE("profile", "PROFILE"),
}

/**
 * Preferencias de interfaz (en la web viven en localStorage, no en el respaldo): tema, barra inferior, ocultar saldo,
 * moneda de visualización. Son estado de Compose: cambiarlas recompone.
 */
object Prefs {
    private lateinit var sp: android.content.SharedPreferences
    var theme by mutableStateOf(THEMES[0]); private set
    var favorites by mutableStateOf(listOf(Route.WALLETS, Route.ANALYSIS, Route.PROFILE)); private set
    var balanceVisible by mutableStateOf(true); private set
    var display by mutableStateOf(Currency.USD); private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
        theme = THEMES.firstOrNull { it.id == sp.getString("theme", null) } ?: THEMES[0]
        sp.getString("favorites", null)?.let { s -> favorites = s.split(',').mapNotNull { n -> Route.entries.firstOrNull { it.name == n } } }
        balanceVisible = sp.getBoolean("balanceVisible", true)
        display = Currency.entries.firstOrNull { it.name == sp.getString("display", null) } ?: Currency.USD
    }

    fun applyTheme(p: Palette) { theme = p; sp.edit().putString("theme", p.id).apply() }
    fun saveFavorites(r: List<Route>) { favorites = r.take(3); sp.edit().putString("favorites", favorites.joinToString(",") { it.name }).apply() }
    fun toggleBalance() { balanceVisible = !balanceVisible; sp.edit().putBoolean("balanceVisible", balanceVisible).apply() }
    /** USD → VES → EUR → USD, como el botón de moneda del balance de la web. */
    fun cycleDisplay() {
        display = when (display) { Currency.USD -> Currency.VES; Currency.VES -> Currency.EUR; else -> Currency.USD }
        sp.edit().putString("display", display.name).apply()
    }
}

// tokens (var(--…) de la web); leen el tema actual
val Bg get() = Prefs.theme.bg            // --bg-primary
val Surface1 get() = Prefs.theme.surface // --bg-secondary (bg-theme-surface)
val Surface2 get() = Prefs.theme.card    // --bg-tertiary
val Ink get() = Prefs.theme.ink          // --text-primary
val Muted get() = Prefs.theme.muted      // --text-secondary
val Brand get() = Prefs.theme.brand      // --primary
val Glass get() = Ink.copy(alpha = 0.05f)        // bg-theme-soft
val GlassBorder get() = Ink.copy(alpha = 0.10f)  // border-theme-soft
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
