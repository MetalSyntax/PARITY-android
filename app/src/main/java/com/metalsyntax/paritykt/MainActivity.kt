package com.metalsyntax.paritykt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metalsyntax.paritykt.data.I18n
import com.metalsyntax.paritykt.data.Store
import com.metalsyntax.paritykt.data.Transaction
import com.metalsyntax.paritykt.data.TxType
import com.metalsyntax.paritykt.data.t
import com.metalsyntax.paritykt.sync.Sync
import com.metalsyntax.paritykt.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val bgScope = MainScope()
    private var pending: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        I18n.load(this)
        // cada cambio reescribe la copia automática (si hay carpeta), agrupando ráfagas de cambios
        Store.init(this) {
            pending?.cancel()
            pending = bgScope.launch(Dispatchers.IO) { delay(3000); Sync.autoBackup(this@MainActivity) }
        }
        Reminders.schedule(this)
        bgScope.launch(Dispatchers.IO) { runCatching { Store.refreshRates() } }
        setContent {
            val dark = Prefs.theme.dark
            LaunchedEffect(dark) {
                val bars = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(bars, bars)
            }
            ParityTheme { App() }
        }
    }

    override fun onDestroy() { super.onDestroy(); pending?.let { if (it.isActive) Sync.autoBackup(this) } }
}

@Composable
private fun App() {
    var route by remember { mutableStateOf(Route.HOME) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var newType by remember { mutableStateOf<TxType?>(null) }
    var settings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = route != Route.HOME) { route = Route.HOME }
    val nav = remember {
        Nav(go = { route = it }, back = { route = Route.HOME }, newTx = { newType = it }, openTx = { editing = it },
            snack = { m -> scope.launch { snackbar.showSnackbar(m) } }, settings = { settings = true }
        )
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
            }
        }
    }
    if (editing != null || newType != null) TxEditor(editing, newType ?: TxType.EXPENSE) { editing = null; newType = null }
    if (settings) SettingsSheet(nav) { settings = false }
}

/** Barra inferior de la web: Inicio + 3 favoritos, con el botón + central elevado. */
@Composable
private fun BottomBar(route: Route, go: (Route) -> Unit, onAdd: () -> Unit) {
    val items = listOf(Route.HOME) + Prefs.favorites.filter { it != Route.HOME }.take(3)
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp).background(Bg)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, r ->
                    if (i == 2) Spacer(Modifier.width(76.dp))
                    NavItem(r, route == r, Modifier.weight(1f)) { go(r) }
                }
                if (items.size < 3) Spacer(Modifier.width(76.dp).weight(1f))
            }
        }
        // ring blanco alrededor del + (border-4 de la web)
        Box(Modifier.align(Alignment.TopCenter).size(68.dp).clip(CircleShape).background(Ink.copy(alpha = 0.9f)).padding(4.dp)
            .clip(CircleShape).background(Brand).tap(onClick = onAdd), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, t("kt_new_tx"), tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun NavItem(r: Route, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c by androidx.compose.animation.animateColorAsState(if (selected) Ink else Muted.copy(alpha = 0.8f), label = "nav")
    Column(modifier.fillMaxHeight().tap(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(routeIcon(r), null, tint = c, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(t(r.key).uppercase(ES), style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
