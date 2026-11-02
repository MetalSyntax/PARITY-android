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

    fun tokenFrom(activity: Activity, data: Intent?): String? =
        runCatching { Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()

    /**
     * Sincroniza con Drive (bloqueante): baja el parity_sync_*.json más nuevo, lo mezcla (gana el updatedAt más
     * reciente, las lápidas borran) y sube uno nuevo con el estado mezclado. Devuelve un resumen para mostrar.
     */
    fun driveSync(ctx: Context, token: String): String {
        val files = JSONObject(http("https://www.googleapis.com/drive/v3/files?q=" +
            java.net.URLEncoder.encode("name contains 'parity_sync_' and trashed=false", "UTF-8") +
            "&orderBy=modifiedTime%20desc&pageSize=5&fields=files(id,name,modifiedTime)", token)).optJSONArray("files") ?: JSONArray()
        var pulled = 0
        if (files.length() > 0) {
            val latest = files.getJSONObject(0)
            val remote = Store.decode(JSONObject(http("https://www.googleapis.com/drive/v3/files/${latest.getString("id")}?alt=media", token)))
            pulled = remote.transactions.size
            Store.merge(remote)
        }
        val name = "parity_sync_${nowIso().replace(":", "-")}.json"
        val meta = JSONObject(http("https://www.googleapis.com/drive/v3/files", token, "POST", JSONObject().put("name", name).put("mimeType", "application/json").toString()))
        http("https://www.googleapis.com/upload/drive/v3/files/${meta.getString("id")}?uploadType=media", token, "PATCH", minimal().toString())
        // deja solo los 5 más nuevos (como el historial de la web)
        for (i in 4 until files.length()) runCatching { http("https://www.googleapis.com/drive/v3/files/${files.getJSONObject(i).getString("id")}", token, "DELETE") }
        ctx.getSharedPreferences(PREFS, 0).edit().putString("last_drive", nowIso()).apply()
        return if (pulled > 0) "Sincronizado: se mezclaron $pulled movimientos de Drive" else "Primer respaldo en Drive creado"
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
