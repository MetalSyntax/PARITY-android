package com.metalsyntax.paritykt.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.metalsyntax.paritykt.data.AppData
import com.metalsyntax.paritykt.data.Store
import com.metalsyntax.paritykt.data.nowIso
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Tres formas de llevar los datos fuera del teléfono, de más simple a más automática:
 *  1. Exportar / importar un archivo con el selector del sistema (elegir "Drive" ahí no requiere configurar nada).
 *  2. Carpeta de respaldo automático (Drive, Dropbox, Syncthing… cualquier proveedor de documentos): se reescribe
 *     parity_backup_auto.json en cada cambio.
 *  3. Google Drive directo con el mismo alcance (drive.file) y el mismo formato mínimo que la web (parity_sync_*.json).
 * Los archivos son los de la web: parity_backup_*.json (completo) y parity_sync_*.json (mínimo, sin recibos).
 */
object Sync {
    private const val PREFS = "sync"
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    // ------------------------------------------------------------------------------------------ 1. archivo

    fun backupName() = "parity_backup_${LocalDate.now()}.json"

    fun export(ctx: Context, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Store.encode(Store.data).toString(2).toByteArray()) }
    }

    /** Lee un respaldo completo o mínimo de la web o de esta app. */
    fun read(ctx: Context, uri: Uri): Pair<AppData, Boolean> {
        val o = JSONObject(ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        return Store.decode(o) to (o.optString("__backupType") == "minimal")
    }

    // ------------------------------------------------------------------------------------------ 2. carpeta automática

    fun folder(ctx: Context): Uri? = ctx.getSharedPreferences(PREFS, 0).getString("folder", null)?.let(Uri::parse)
    fun lastAuto(ctx: Context): String? = ctx.getSharedPreferences(PREFS, 0).getString("last_auto", null)

    fun setFolder(ctx: Context, tree: Uri?) {
        if (tree != null) ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        ctx.getSharedPreferences(PREFS, 0).edit().putString("folder", tree?.toString()).apply()
        if (tree != null) autoBackup(ctx)
    }

    /** Reescribe parity_backup_auto.json en la carpeta elegida. Bloqueante (llamar en segundo plano). */
    fun autoBackup(ctx: Context) {
        val tree = folder(ctx) ?: return
        runCatching {
            val cr = ctx.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = "parity_backup_auto.json"
            val existing = cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                generateSequence { if (c.moveToNext()) c.getString(0) to c.getString(1) else null }.firstOrNull { it.second == name }?.first
            }
            val doc = existing?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
                ?: DocumentsContract.createDocument(cr, parent, "application/json", name)!!
            cr.openOutputStream(doc, "wt")!!.use { it.write(Store.encode(Store.data).toString().toByteArray()) }
            ctx.getSharedPreferences(PREFS, 0).edit().putString("last_auto", nowIso()).apply()
        }
    }

    // ------------------------------------------------------------------------------------------ 3. Google Drive

    fun lastDrive(ctx: Context): String? = ctx.getSharedPreferences(PREFS, 0).getString("last_drive", null)

    /**
     * Pide el token de Drive con la cuenta del teléfono. Si Google necesita confirmación, [onResolution] recibe la
     * pantalla a lanzar (y después [token] con el resultado). Requiere un cliente OAuth Android (paquete + SHA-1)
     * en el mismo proyecto de Google Cloud que la web, para que drive.file vea los mismos archivos.
     */
    fun authorize(activity: Activity, onToken: (String) -> Unit, onResolution: (android.app.PendingIntent) -> Unit, onError: (String) -> Unit) {
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        Identity.getAuthorizationClient(activity).authorize(req)
            .addOnSuccessListener { r: AuthorizationResult ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) onResolution(pi) else r.accessToken?.let(onToken) ?: onError("sin token")
            }
            .addOnFailureListener { onError(it.message ?: it.javaClass.simpleName) }
    }

    /** Token tras la pantalla de permiso, o el motivo del fallo (código de Google: 10 = app sin registrar en Google Cloud). */
    fun tokenFrom(activity: Activity, data: Intent?): Result<String> = runCatching {
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken ?: error("sin token")
    }.recoverCatching { e ->
        val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
        throw IllegalStateException(if (code == 10) com.metalsyntax.paritykt.data.t("kt_drive_not_registered") else "${e.message} (${code ?: "?"})")
    }

    /** Token sin mostrar pantallas (para la sincronización automática); null si Google pide confirmación o falla. */
    fun silentToken(ctx: Context, onResult: (String?) -> Unit) {
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        Identity.getAuthorizationClient(ctx).authorize(req)
            .addOnSuccessListener { r -> onResult(if (r.hasResolution()) null else r.accessToken) }
            .addOnFailureListener { onResult(null) }
    }

    /** Días que se conservan los parity_sync diarios en Drive (la web no borra solos; aquí sí, para no acumular). */
    const val KEEP_DAYS = 30

    /**
     * Sincroniza con Drive (bloqueante), como la web:
     * - [pull]: baja la copia completa de la web (parity_backup_*) y el parity_sync más nuevo y los mezcla (gana el
     *   updatedAt más reciente, las lápidas borran). Al abrir la app y con el botón; tras un cambio solo se sube.
     * - Sube el estado en parity_sync_AAAA-MM-DD.json: un archivo por día que se sobrescribe (respaldo diario).
     * - Borra los parity_sync de más de [KEEP_DAYS] días y los de la versión anterior de la app (con hora en el nombre).
     *   Las copias completas parity_backup_* de la web nunca se tocan.
     */
    fun driveSync(ctx: Context, token: String, pull: Boolean = true): String {
        fun list(prefix: String) = (JSONObject(http("https://www.googleapis.com/drive/v3/files?q=" +
            java.net.URLEncoder.encode("name contains '$prefix' and trashed=false", "UTF-8") +
            "&orderBy=modifiedTime%20desc&pageSize=100&fields=files(id,name,modifiedTime)", token)).optJSONArray("files") ?: JSONArray())
            .let { a -> (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("name").startsWith(prefix) } }
        val files = list("parity_sync_")
        var pulled = 0
        if (pull) listOfNotNull(list("parity_backup_").firstOrNull(), files.firstOrNull()).forEach { f ->
            val remote = Store.decode(JSONObject(http("https://www.googleapis.com/drive/v3/files/${f.getString("id")}?alt=media", token)))
            pulled += remote.transactions.size + remote.scheduled.size + remote.budgets.size + remote.goals.size
            Store.merge(remote)
        }
        val today = java.time.LocalDate.now()
        val name = "parity_sync_$today.json"
        val id = files.firstOrNull { it.optString("name") == name }?.getString("id")
            ?: JSONObject(http("https://www.googleapis.com/drive/v3/files", token, "POST", JSONObject().put("name", name).put("mimeType", "application/json").toString())).getString("id")
        http("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media", token, "PATCH", minimal().toString())
        files.filter { f -> pruneable(f.optString("name"), today) }.forEach { f ->
            runCatching { http("https://www.googleapis.com/drive/v3/files/${f.getString("id")}", token, "DELETE") }
        }
        ctx.getSharedPreferences(PREFS, 0).edit().putString("last_drive", nowIso()).apply()
        return if (pulled > 0) com.metalsyntax.paritykt.data.t("kt_drive_merged", pulled) else com.metalsyntax.paritykt.data.t("kt_drive_saved")
    }

    /** parity_sync a borrar: más de [KEEP_DAYS] días, o del formato viejo de la app (parity_sync_2026-11-03T00-10-20…). */
    internal fun pruneable(name: String, today: java.time.LocalDate): Boolean {
        val rest = name.removePrefix("parity_sync_").removeSuffix(".json")
        val day = runCatching { java.time.LocalDate.parse(rest.take(10)) }.getOrNull() ?: return false
        return rest.length > 10 || day.isBefore(today.minusDays(KEEP_DAYS.toLong()))
    }

    /** Payload mínimo de la web (packages/core/src/utils/minimalSync.ts): sin recibos, con lápidas. */
    private fun minimal(): JSONObject = Store.encode(Store.data).apply {
        put("__backupType", "minimal"); put("syncedAt", nowIso()); put("since", JSONObject.NULL)
        val tx = getJSONArray("transactions")
        for (i in 0 until tx.length()) tx.getJSONObject(i).remove("receipt")
    }.also { it.remove("rates") }

    private fun http(url: String, token: String, method: String = "GET", body: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = if (method == "PATCH") "POST" else method
        if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        c.setRequestProperty("Authorization", "Bearer $token")
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(code in 200..299) { "Drive respondió $code" }
        return text
    }

}
