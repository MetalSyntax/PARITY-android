package com.metalsyntax.paritykt.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.metalsyntax.paritykt.data.t

/**
 * Tutoriales (TutorialSystem.tsx de la web), solo los que aplican a esta versión. Cada paso lleva a su vista y muestra
 * una tarjeta guía. Textos: kt_tut_<id> / kt_tut_<id>_d / kt_tut_<id>_<n> / kt_tut_<id>_<n>_d.
 * ponytail: tarjeta guía sin foco sobre el control exacto (la web resalta el elemento con un selector CSS).
 */
data class Tutorial(val id: String, val icon: ImageVector, val color: Color, val steps: List<Route?>)

val TUTORIALS = listOf(
    Tutorial("expense", Icons.Outlined.ReceiptLong, Color(0xFFF87171), listOf(Route.HOME, null, null)),
    Tutorial("income", Icons.Outlined.TrendingUp, Color(0xFF34D399), listOf(Route.HOME, null, null)),
    Tutorial("transfer", Icons.Outlined.SwapHoriz, Color(0xFF60A5FA), listOf(Route.HOME, null)),
    Tutorial("wallet", Icons.Outlined.AccountBalance, Color(0xFF818CF8), listOf(Route.WALLETS, Route.WALLETS, Route.WALLETS)),
    Tutorial("budget", Icons.Outlined.PieChart, Color(0xFFFB923C), listOf(Route.BUDGET, Route.BUDGET, Route.GOALS)),
    Tutorial("report", Icons.Outlined.BarChart, Color(0xFF38BDF8), listOf(Route.TX, Route.TX, Route.ANALYSIS)),
    Tutorial("recurring", Icons.Outlined.CalendarMonth, Color(0xFFA78BFA), listOf(Route.PLAN, Route.PLAN, Route.PLAN)),
    Tutorial("shopping", Icons.Outlined.ShoppingCart, Color(0xFFF59E0B), listOf(Route.SHOPPING, Route.SHOPPING)),
    Tutorial("customize", Icons.Outlined.Dashboard, Color(0xFF2DD4BF), listOf(Route.HOME, Route.PROFILE)),
)

/** Lista de tutoriales (botón Tutoriales del Inicio). */
@Composable
fun TutorialsSheet(onPick: (Tutorial) -> Unit, onDismiss: () -> Unit) =
    TitledSheet(t("btn_tutorials"), t("kt_tut_count", TUTORIALS.size), onDismiss) {
        Spacer(Modifier.height(12.dp))
        TUTORIALS.forEach { tu ->
            ListRow(t("kt_tut_${tu.id}"), subtitle = t("kt_tut_${tu.id}_d"), icon = tu.icon, tint = tu.color, color = Bg,
                trailingSlot = { Text(t("kt_steps", tu.steps.size), style = MaterialTheme.typography.labelSmall, color = Muted) }) { onPick(tu) }
        }
    }

/** Tarjeta guía flotante sobre la barra inferior. */
@Composable
fun TutorialCoach(tu: Tutorial, step: Int, onNext: () -> Unit, onClose: () -> Unit) {
    val n = step + 1
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 104.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedContent(step, transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith fadeOut() }, label = "coach") { s ->
            Column(Modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Surface2).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Bubble(tu.icon, tu.color, 36.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Caps(t("kt_step_of", s + 1, tu.steps.size), color = tu.color)
                        Text(t("kt_tut_${tu.id}_${s + 1}"), style = MaterialTheme.typography.titleMedium, color = Ink)
                    }
                    RoundIcon(Icons.Outlined.Close, t("close"), onClose, tint = Muted, size = 36.dp, bg = Bg)
                }
                Spacer(Modifier.height(8.dp))
                Text(t("kt_tut_${tu.id}_${s + 1}_d"), style = MaterialTheme.typography.bodyMedium, color = Muted)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(tu.steps.size) { i ->
                        Box(Modifier.padding(end = 6.dp).size(if (i == s) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == s) tu.color else Glass))
                    }
                    Spacer(Modifier.weight(1f))
                    BrandButton(if (n >= tu.steps.size) t("done") else t("next"), color = tu.color, onClick = onNext)
                }
            }
        }
    }
}
