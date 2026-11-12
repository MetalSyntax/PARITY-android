package com.metalsyntax.paritykt

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metalsyntax.paritykt.data.*
import com.metalsyntax.paritykt.sync.AutoDrive
import com.metalsyntax.paritykt.sync.Sync
import com.metalsyntax.paritykt.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** FragmentActivity (es un ComponentActivity) porque BiometricPrompt lo necesita. */
class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val bgScope = MainScope()
    private var pending: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Metrics.init(this)
        Pin.init(this)
        I18n.load(this)
        // cada cambio reescribe la copia automática (si hay carpeta), agrupando ráfagas de cambios
        Store.init(this) {
            pending?.cancel()
            pending = bgScope.launch(Dispatchers.IO) { delay(3000); Sync.autoBackup(this@MainActivity) }
            AutoDrive.onChange(this, bgScope)
        }
        Reminders.schedule(this)
        AutoDrive.onOpen(this, bgScope)
        bgScope.launch(Dispatchers.IO) { runCatching { Store.refreshRates() } }
        setContent {
            val dark = Prefs.theme.dark
            LaunchedEffect(dark) {
                val bars = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(bars, bars)
            }
            ParityTheme {
                when {
                    Pin.locked -> LockScreen()
                    !Prefs.onboarded -> OnboardingScreen {}
                    // vista inicial por intent (adb: --es route WALLETS), útil para revisar pantallas
                    else -> App(Route.entries.firstOrNull { it.name == intent?.getStringExtra("route") } ?: Route.HOME)
                }
            }
        }
    }

    // «Bloquear con PIN al salir»: al volver de segundo plano se pide el PIN
    override fun onStop() {
        super.onStop()
        if (Pin.enabled && Pin.lockOnExit && !isChangingConfigurations && !Pin.external) Pin.locked = true
        AutoDrive.onBackground(this, bgScope)
    }

    override fun onResume() { super.onResume(); Pin.external = false }

    override fun onDestroy() { super.onDestroy(); pending?.let { if (it.isActive) Sync.autoBackup(this) } }
}

@Composable
private fun App(start: Route) {
    // guardables: si Android cierra la app mientras la cámara está abierta, al volver sigue en el mismo lugar
    var route by rememberSaveable { mutableStateOf(start) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> Store.data.transactions.firstOrNull { it.id == id } }
    var detail by remember { mutableStateOf<Transaction?>(null) } // tocar una transacción abre la vista detallada, no el editor
    var newType by rememberSaveable { mutableStateOf<TxType?>(null) }
    var scanNew by rememberSaveable { mutableStateOf(false) } // Modern: «Factura» abre el editor directo en la cámara
    var settings by remember { mutableStateOf(false) }
    var tutorial by remember { mutableStateOf<Tutorial?>(null) }
    var tStep by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Modern: presupuesto, metas, pagos, compras y análisis viven dentro de «Plan»; atrás vuelve ahí
    fun up() = if (MODERN && route in HUB_CHILDREN) Route.HUB else Route.HOME
    BackHandler(enabled = route != Route.HOME) { route = up() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        if (MODERN) Store.onGoalReached = { g -> Celebrate.goal = g.name; haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }
    }
    val nav = remember {
        Nav(go = { route = it }, back = { route = up() }, newTx = { newType = it }, openTx = { detail = it },
            snack = { m -> scope.launch { snackbar.showSnackbar(m) } }, settings = { settings = true },
            scan = { scanNew = true; newType = TxType.EXPENSE },
            undo = { m, action -> scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                if (snackbar.showSnackbar(m, t("kt_undo"), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) action()
            } },
            tutorial = { tu -> tutorial = tu; tStep = 0; tu.steps.first()?.let { route = it } })
    }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, containerColor = Surface2, contentColor = Ink, shape = MaterialTheme.shapes.medium) } },
        bottomBar = { BottomBar(route, { route = it }) { newType = TxType.EXPENSE } },
    ) { pad ->
        // transición entre vistas: entra desde la derecha con fundido (animate-in slide-in-from-right de la web)
        AnimatedContent(route, Modifier.padding(pad).statusBarsPadding().fillMaxSize(), label = "route",
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (targetState == Route.HOME) -it / 8 else it / 8 }) togetherWith fadeOut(tween(120))
            }) { r ->
            when (r) {
                Route.HOME -> HomeScreen(nav)
                Route.TX -> TransactionsScreen(nav)
                Route.ANALYSIS -> AnalysisScreen(nav)
                Route.BUDGET -> BudgetGoalsScreen(nav, goals = false)
                Route.GOALS -> BudgetGoalsScreen(nav, goals = true)
                Route.WALLETS -> WalletsScreen(nav)
                Route.PLAN -> ScheduledScreen(nav)
                Route.SHOPPING -> ShoppingScreen(nav)
                Route.PROFILE -> ProfileScreen(nav)
                Route.HUB -> PlanHub(nav)
            }
        }
    }
    tutorial?.let { tu ->
        TutorialCoach(tu, tStep, onNext = {
            if (tStep + 1 >= tu.steps.size) tutorial = null else { tStep++; tu.steps[tStep]?.let { route = it } }
        }, onClose = { tutorial = null })
    }
    detail?.let { tx -> TxDetail(tx, onEdit = { detail = null; editingId = it.id }, onClose = { detail = null }) }
    if (editing != null || newType != null) TxEditor(editing, newType ?: TxType.EXPENSE, autoScan = scanNew) { editingId = null; newType = null; scanNew = false }
    if (MODERN) Confetti()
    if (settings) SettingsSheet(nav) { settings = false }
}

/** Barra inferior de la web: Inicio + 3 favoritos, con el botón + central elevado. */
@Composable
private fun BottomBar(route: Route, go: (Route) -> Unit, onAdd: () -> Unit) {
    // Modern: navegación fija y simple (sin favoritos); Legacy: Inicio + 3 favoritos como la web
    val items = if (MODERN) listOf(Route.HOME, Route.TX, Route.HUB, Route.WALLETS) else listOf(Route.HOME) + Prefs.favorites.filter { it != Route.HOME }.take(3)
    // un solo tamaño de letra para toda la barra: baja hasta que la etiqueta más larga entre sin partirse
    var scale by remember(items) { mutableFloatStateOf(1f) }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp).background(Surface1)) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp)) {
                items.forEachIndexed { i, r ->
                    if (i == 2) Spacer(Modifier.width(76.dp))
                    NavItem(r, route == r || (MODERN && r == Route.HUB && route in HUB_CHILDREN), Modifier.weight(1f), scale, onOverflow = { if (scale > 0.6f) scale *= 0.94f }) { go(r) }
                }
                if (items.size < 3) Spacer(Modifier.width(76.dp).weight(1f))
            }
        }
        Box(Modifier.align(Alignment.TopCenter).size(64.dp).shadow(16.dp, CircleShape, spotColor = Brand).clip(CircleShape).background(Brand).tap(onClick = onAdd),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, t("kt_new_tx"), tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun NavItem(r: Route, selected: Boolean, modifier: Modifier, scale: Float, onOverflow: () -> Unit, onClick: () -> Unit) {
    // como la web: activo en color de texto principal (blanco), el resto en secundario; etiqueta hasta en 2 líneas.
    // Íconos arriba (no centrados) para que queden en línea aunque una etiqueta ocupe 2 líneas.
    val c by animateColorAsState(if (selected) Ink else Muted, label = "nav")
    Column(modifier.fillMaxHeight().tap(onClick = onClick).padding(start = 2.dp, end = 2.dp, top = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(routeIcon(r), null, tint = c, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        val label = t(r.key).uppercase(ES)
        val oneWord = ' ' !in label.trim()
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp * scale, lineHeight = 10.sp * scale, letterSpacing = (-0.2).sp),
            color = c, maxLines = if (oneWord) 1 else 2, softWrap = !oneWord, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            onTextLayout = { if (it.hasVisualOverflow) onOverflow() })
    }
}
