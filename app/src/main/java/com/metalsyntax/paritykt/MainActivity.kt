package com.metalsyntax.paritykt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.metalsyntax.paritykt.data.Store
import com.metalsyntax.paritykt.data.Transaction
import com.metalsyntax.paritykt.data.TxType
import com.metalsyntax.paritykt.sync.Sync
import com.metalsyntax.paritykt.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Vistas: 5 en la barra inferior (como los favoritos por defecto de la web); Planificado, Compras y Perfil desde la barra superior. */
private enum class Tab(val title: String, val icon: ImageVector, val bottom: Boolean = true, val short: String = title) {
    HOME("Inicio", Icons.Outlined.Home), TX("Movimientos", Icons.Outlined.ReceiptLong, short = "Historial"),
    ANALYSIS("Análisis", Icons.Outlined.Insights),
    BUDGET("Presupuesto y metas", Icons.Outlined.PieChart, short = "Metas"), WALLETS("Billeteras", Icons.Outlined.AccountBalanceWallet),
    PLAN("Planificado", Icons.Outlined.EventRepeat, false),
    SHOPPING("Lista de compras", Icons.Outlined.ShoppingCart, false), PROFILE("Perfil", Icons.Outlined.Person, false),
}

class MainActivity : ComponentActivity() {
    private val bgScope = MainScope()
    private var pending: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        // cada cambio reescribe la copia automática (si hay carpeta), agrupando ráfagas de cambios
        Store.init(this) {
            pending?.cancel()
            pending = bgScope.launch(Dispatchers.IO) { delay(3000); Sync.autoBackup(this@MainActivity) }
        }
        bgScope.launch(Dispatchers.IO) { runCatching { Store.refreshRates() } }
        setContent { ParityTheme { App() } }
    }

    override fun onDestroy() { super.onDestroy(); pending?.let { if (it.isActive) Sync.autoBackup(this) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var newType by remember { mutableStateOf<TxType?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snack: (String) -> Unit = { m -> scope.launch { snackbar.showSnackbar(m) } }
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
    val go: (String) -> Unit = { r -> tab = when (r) { "tx" -> Tab.TX; "plan" -> Tab.PLAN; "budget" -> Tab.BUDGET; "wallets" -> Tab.WALLETS; "analysis" -> Tab.ANALYSIS; "profile" -> Tab.PROFILE; else -> Tab.HOME } }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                title = {
                    if (tab == Tab.HOME) Column {
                        Caps(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)))
                        Text(Store.data.profile.name.takeIf { it.isNotBlank() }?.let { "Hola, ${it.substringBefore(' ')}" } ?: "Parity",
                            style = MaterialTheme.typography.titleLarge)
                    } else Text(tab.title, style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = { if (!tab.bottom) IconButton(onClick = { tab = Tab.HOME }) { Icon(Icons.Outlined.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { tab = Tab.PLAN }) { Icon(Icons.Outlined.EventRepeat, "Planificado") }
                    IconButton(onClick = { tab = Tab.SHOPPING }) { Icon(Icons.Outlined.ShoppingCart, "Lista de compras") }
                    IconButton(onClick = { tab = Tab.PROFILE }) { Icon(Icons.Outlined.AccountCircle, "Perfil") }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Surface1, tonalElevation = 0.dp) {
                Tab.entries.filter { it.bottom }.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) },
                        label = { Text(t.short, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Indigo, selectedTextColor = Indigo,
                            indicatorColor = Indigo.copy(alpha = 0.15f), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        },
        floatingActionButton = { if (tab == Tab.TX) FloatingActionButton(onClick = { newType = TxType.EXPENSE }, containerColor = Indigo, contentColor = androidx.compose.ui.graphics.Color.White) { Icon(Icons.Outlined.Add, "Nuevo movimiento") } },
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(onNewTx = { newType = it }, onOpenTx = { editing = it }, go = go)
                Tab.TX -> TransactionsScreen { editing = it }
                Tab.PLAN -> ScheduledScreen(snack)
                Tab.ANALYSIS -> AnalysisScreen(go)
                Tab.BUDGET -> BudgetGoalsScreen()
                Tab.WALLETS -> WalletsScreen(snack)
                Tab.SHOPPING -> ShoppingScreen()
                Tab.PROFILE -> ProfileScreen(snack)
            }
        }
    }
    if (editing != null || newType != null) TxEditor(editing, newType ?: TxType.EXPENSE) { editing = null; newType = null }
}
