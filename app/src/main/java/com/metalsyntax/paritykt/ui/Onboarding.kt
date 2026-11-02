package com.metalsyntax.paritykt.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metalsyntax.paritykt.data.*
import com.metalsyntax.paritykt.sync.Sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Step(val icon: ImageVector, val color: Color, val title: String, val desc: String)

/**
 * Onboarding de la web (views/Onboarding.tsx) con los pasos que aplican: nombre, idioma, privacidad y moneda, menú rápido,
 * apariencia, PIN (opcional) y «Todo listo» (empezar de cero o importar un respaldo). Sin los de gamificación (XP, retos).
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(Store.data.profile.name) }
    var pinSheet by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val steps = listOf(
        Step(Icons.Outlined.TrackChanges, Color(0xFF6366F1), t("onboarding_title1"), t("onboarding_name_desc")),
        Step(Icons.Outlined.Language, Color(0xFF34D399), t("onboarding_language_title"), t("onboarding_language_desc")),
        Step(Icons.Outlined.Shield, Color(0xFFFACC15), t("onboarding_privacy_title"), t("onboarding_privacy_desc")),
        Step(Icons.Outlined.Dashboard, Color(0xFF60A5FA), t("onboarding_menu_title"), t("navbarFavoritesDesc")),
        Step(Icons.Outlined.Palette, Color(0xFFC084FC), t("appearance"), t("kt_ob_theme_desc")),
        Step(Icons.Outlined.Lock, Color(0xFFFB923C), t("security"), t("kt_ob_pin_desc")),
        Step(Icons.Outlined.RocketLaunch, Color(0xFF6366F1), t("onboarding_finish_title"), t("onboarding_finish_desc")),
    )
    val s = steps[step]
    val glow by animateColorAsState(s.color.copy(alpha = 0.14f), tween(500), label = "glow")
    val last = step == steps.lastIndex
    fun next() { if (step == 0) Store.saveProfile(Store.data.profile.copy(name = name.trim())); if (!last) step++ }
    fun finish() { Prefs.finishOnboarding(); onFinish() }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Sync.read(ctx, uri).first } }
            r.onSuccess { d -> withContext(Dispatchers.Default) { Store.merge(d) }; finish() }
            importing = false
        }
    }
    androidx.activity.compose.BackHandler(step > 0) { step-- }

    Box(Modifier.fillMaxSize().background(Bg).background(Brush.radialGradient(listOf(glow, Color.Transparent), radius = 1400f)).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(48.dp))
            AnimatedContent(step, Modifier.weight(1f), label = "ob", transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(250)) + slideInHorizontally(tween(300)) { it / 5 * dir }) togetherWith (fadeOut(tween(150)) + slideOutHorizontally { -it / 5 * dir })
            }) { i ->
                val st = steps[i]
                Column(Modifier.fillMaxWidth().verticalScrollable(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Surface1), contentAlignment = Alignment.Center) {
                        Icon(st.icon, null, tint = st.color, modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(st.title, style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(st.desc, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(28.dp))
                    when (i) {
                        0 -> Field(name, { name = it }, t("onboarding_name_placeholder"), container = Surface1)
                        1 -> Segmented(listOf("es" to "Español", "en" to "English", "pt" to "Português"), I18n.lang, container = Surface1) {
                            Store.saveProfile(Store.data.profile.copy(language = it))
                        }
                        2 -> {
                            GlassCard(Modifier.fillMaxWidth(), radius = 18.dp) {
                                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    SettingRow(t("onboarding_hide_balances"), icon = Icons.Outlined.VisibilityOff) {
                                        Toggle(!Prefs.balanceVisible) { Prefs.setBalanceVisible(!it) }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Segmented(listOf(Currency.USD to "USD ($)", Currency.EUR to "EUR (€)", Currency.VES to "VES (Bs)"), Prefs.display, container = Surface1) {
                                Prefs.setDisplay(it)
                            }
                        }
                        3 -> OptionGrid(Route.entries.filter { it != Route.HOME }, { it in Prefs.favorites }, columns = 2, container = Surface1,
                            enabled = { it in Prefs.favorites || Prefs.favorites.size < 3 },
                            onPick = { r -> Prefs.saveFavorites(if (r in Prefs.favorites) Prefs.favorites - r else Prefs.favorites + r) }) { r, on ->
                            Icon(routeIcon(r), null, tint = if (on) Color.White else Muted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(t(r.key).uppercase(ES), style = MaterialTheme.typography.labelSmall, color = if (on) Color.White else Muted)
                        }
                        4 -> ThemePicker(Surface1)
                        5 -> {
                            if (Pin.enabled) Pill(t("kt_pin_ready"), Income)
                            else BrandButton(t("kt_create_pin"), Modifier.fillMaxWidth(), Icons.Outlined.Lock) { pinSheet = true }
                            Spacer(Modifier.height(10.dp))
                            Text(t("kt_ob_pin_note"), style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center)
                        }
                        else -> {
                            GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, color = Income.copy(alpha = 0.1f)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CheckCircle, null, tint = Income); Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(t("onboarding_profile_ready").uppercase(ES), style = MaterialTheme.typography.labelMedium, color = Ink)
                                        Text(t("onboarding_profile_ready_desc"), style = MaterialTheme.typography.bodySmall, color = Muted)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            GhostButton(t("kt_import_backup"), Icons.Outlined.Download, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                importer.launch(arrayOf("application/json", "*/*"))
                            }
                            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = Brand, trackColor = Glass)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
                steps.indices.forEach { i ->
                    val w by androidx.compose.animation.core.animateDpAsState(if (i == step) 24.dp else 6.dp, label = "dot")
                    Box(Modifier.padding(horizontal = 3.dp).size(w, 6.dp).clip(CircleShape).background(if (i == step) Brand else Surface2))
                }
            }
            BrandButton(if (last) t("startFresh").uppercase(ES) else t("next").uppercase(ES), Modifier.fillMaxWidth().heightIn(min = 56.dp),
                if (last) null else Icons.Outlined.ArrowForward, enabled = step != 0 || name.isNotBlank()) { if (last) finish() else next() }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pinSheet) PinSetupSheet { pinSheet = false }
}

/** Modo (Claro / Oscuro / Negro) y color de acento: lo usan el onboarding y Ajustes. */
@Composable
fun ThemePicker(container: androidx.compose.ui.graphics.Color = Bg) {
    OptionGrid(Mode.entries, { it == Prefs.mode }, columns = 3, container = container, onPick = Prefs::applyMode) { m, on ->
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(m.bg)).padding(6.dp).clip(CircleShape).background(Color(m.surface)))
        Spacer(Modifier.height(6.dp))
        Text(t(m.key), style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else Ink)
    }
    Spacer(Modifier.height(14.dp))
    Caps(t("kt_accent"))
    Spacer(Modifier.height(6.dp))
    Swatches(ACCENTS, Prefs.accent, Prefs::applyAccent)
}
