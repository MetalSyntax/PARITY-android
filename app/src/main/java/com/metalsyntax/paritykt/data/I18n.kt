package com.metalsyntax.paritykt.data

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Textos de Parity web (packages/i18n, copiados a assets/i18n.json: en / es / pt) + los propios de la app
 * (assets/i18n_app.json, claves kt_*). Igual que getTranslation de la web: idioma del perfil → inglés → la clave.
 */
object I18n {
    private var maps: Map<String, JSONObject> = emptyMap()

    fun load(ctx: Context) {
        fun read(f: String) = JSONObject(ctx.assets.open(f).bufferedReader().use { it.readText() })
        val web = read("i18n.json"); val app = read("i18n_app.json")
        maps = listOf("en", "es", "pt").associateWith { l ->
            web.getJSONObject(l).also { m -> app.optJSONObject(l)?.let { a -> a.keys().forEach { k -> m.put(k, a.get(k)) } } }
        }
    }

    // lee Store.data (estado de Compose): cambiar el idioma en el perfil recompone toda la app
    val lang: String get() = Store.data.profile.language.takeIf { it in maps } ?: "es"
    val locale: Locale get() = when (lang) { "en" -> Locale.US; "pt" -> Locale("pt", "BR"); else -> Locale("es", "VE") }

    fun t(key: String): String = maps[lang]?.optString(key)?.takeIf { it.isNotEmpty() } ?: maps["en"]?.optString(key)?.takeIf { it.isNotEmpty() } ?: key
}

fun t(key: String) = I18n.t(key)
/** Texto con marcadores {0}, {1}… */
fun t(key: String, vararg args: Any): String = args.foldIndexed(I18n.t(key)) { i, s, a -> s.replace("{$i}", a.toString()) }
