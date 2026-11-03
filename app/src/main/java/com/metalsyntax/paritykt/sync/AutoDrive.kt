package com.metalsyntax.paritykt.sync

import android.content.Context
import com.metalsyntax.paritykt.ui.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sincronización automática con Google Drive (modo Dev, como el auto-sync de la web):
 * - al abrir la app: baja, mezcla y sube (completa);
 * - 30 s después del último cambio (agregar / editar / borrar): solo sube el respaldo del día;
 * - al salir de la app con un cambio pendiente: lo sube enseguida.
 * Se activa sola después de la primera sincronización manual con el botón (que es la que concede el permiso).
 * Sin conexión o sin permiso no muestra nada: lo intenta en el siguiente evento.
 */
object AutoDrive {
    const val DEBOUNCE_MS = 30_000L
    private val lock = Mutex()
    private var pending: Job? = null
    @Volatile private var syncing = false

    fun enabled(ctx: Context) = Prefs.dev && Sync.lastDrive(ctx) != null

    private fun run(ctx: Context, scope: CoroutineScope, pull: Boolean) {
        if (!enabled(ctx)) return
        Sync.silentToken(ctx.applicationContext) { token ->
            token ?: return@silentToken
            scope.launch(Dispatchers.IO) {
                lock.withLock { syncing = true; try { runCatching { Sync.driveSync(ctx, token, pull) } } finally { syncing = false } }
            }
        }
    }

    fun onOpen(ctx: Context, scope: CoroutineScope) = run(ctx, scope, pull = true)

    /** Llamar en cada cambio de datos; los cambios que trae la propia mezcla no reprograman. */
    fun onChange(ctx: Context, scope: CoroutineScope) {
        if (syncing || !enabled(ctx)) return
        pending?.cancel()
        pending = scope.launch { delay(DEBOUNCE_MS); pending = null; run(ctx, scope, pull = false) }
    }

    fun onBackground(ctx: Context, scope: CoroutineScope) {
        if (pending?.isActive == true) { pending?.cancel(); pending = null; run(ctx, scope, pull = false) }
    }
}
