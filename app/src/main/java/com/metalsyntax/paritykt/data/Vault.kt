package com.metalsyntax.paritykt.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Cifrado en reposo de parity.json: AES-256-GCM con una clave del Android Keystore (no se puede extraer del teléfono).
 * Formato: "PEN1" + IV(12) + texto cifrado. Los archivos sin cabecera (versiones anteriores) se leen en claro y se
 * vuelven a escribir cifrados en el siguiente guardado. Los respaldos exportados siguen en JSON plano (compatibles con la web).
 */
object Vault {
    private val MAGIC = "PEN1".toByteArray()
    private const val ALIAS = "parity_data"
    internal var enabled = true // pruebas JVM: sin Keystore
    private var dir: java.io.File? = null
    @Volatile private var dataKey: SecretKey? = null

    /** Carpeta donde vive vault.key (la clave de datos envuelta). Llamar antes de leer o escribir. */
    fun init(filesDir: java.io.File) { dir = filesDir }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }

    /**
     * Cifrado de sobre: una clave de datos aleatoria de 256 bits, guardada en vault.key envuelta (AES-GCM) por la clave del
     * Keystore. El Keystore descifra solo 32 bytes; el archivo grande se cifra en memoria (el Keystore es lento con MB).
     */
    @Synchronized private fun key(): SecretKey {
        dataKey?.let { return it }
        val f = java.io.File(dir ?: error("Vault.init"), "vault.key")
        val raw = if (f.exists()) {
            val w = f.readBytes()
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, w, 0, 12)) }.doFinal(w, 12, w.size - 12)
        } else ByteArray(32).also(SecureRandom()::nextBytes).also { k ->
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
            val tmp = java.io.File(f.path + ".tmp"); tmp.writeBytes(c.iv + c.doFinal(k)); tmp.renameTo(f)
        }
        return javax.crypto.spec.SecretKeySpec(raw, "AES").also { dataKey = it }
    }

    fun sealed(bytes: ByteArray) = bytes.size >= 16 && bytes.copyOfRange(0, 4).contentEquals(MAGIC)

    fun seal(plain: ByteArray): ByteArray {
        if (!enabled) return plain
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return MAGIC + iv + c.doFinal(plain)
    }

    fun open(bytes: ByteArray): ByteArray {
        if (!sealed(bytes)) return bytes // archivo viejo, en claro
        return runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 4, 12)) }.doFinal(bytes, 16, bytes.size - 16)
        }.getOrElse { // archivos de la primera versión cifrada: directo con la clave del Keystore
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, bytes, 4, 12)) }.doFinal(bytes, 16, bytes.size - 16)
        }
    }
}

/**
 * PIN de la app (Ajustes → Seguridad, como la web): se guarda solo el hash PBKDF2-SHA256 con sal aleatoria.
 * Tras 5 intentos fallidos se bloquea 30 s.
 */
object Pin {
    private lateinit var sp: android.content.SharedPreferences
    var enabled by mutableStateOf(false); private set
    var lockOnExit by mutableStateOf(true); private set
    var locked by mutableStateOf(false)
    var fails by mutableIntStateOf(0); private set
    private var until = 0L

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("security", Context.MODE_PRIVATE)
        enabled = sp.contains("hash"); lockOnExit = sp.getBoolean("lockOnExit", true)
        locked = enabled
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun set(pin: String) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        sp.edit().putString("salt", hex(salt)).putString("hash", hex(hash(pin, salt))).apply()
        enabled = true; locked = false
    }

    fun clear() { sp.edit().remove("salt").remove("hash").apply(); enabled = false; locked = false }

    /** Segundos de espera si hay bloqueo por intentos; 0 si se puede intentar. */
    fun waitSeconds() = ((until - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    fun check(pin: String): Boolean {
        if (waitSeconds() > 0) return false
        val salt = sp.getString("salt", null)?.let(::unhex) ?: return false
        val ok = MessageDigest.isEqual(hash(pin, salt), sp.getString("hash", "")!!.let(::unhex))
        if (ok) { fails = 0; locked = false } else if (++fails >= 5) { until = System.currentTimeMillis() + 30_000; fails = 0 }
        return ok
    }

    @JvmName("applyLockOnExit") fun setLockOnExit(v: Boolean) { lockOnExit = v; sp.edit().putBoolean("lockOnExit", v).apply() }
}
