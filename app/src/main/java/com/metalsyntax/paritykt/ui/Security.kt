package com.metalsyntax.paritykt.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metalsyntax.paritykt.data.Pin
import com.metalsyntax.paritykt.data.t
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Teclado de PIN reutilizable: puntos, mensaje y teclas 0–9. Llama [onComplete] al llegar a [length] dígitos. */
@Composable
fun PinPad(title: String, subtitle: String, error: String? = null, length: Int = 4, onBiometric: (() -> Unit)? = null, onComplete: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(error) {
        if (error != null) { pin = ""; repeat(3) { shake.animateTo(14f, tween(40)); shake.animateTo(-14f, tween(40)) }; shake.animateTo(0f, tween(40)) }
    }
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Bubble(Icons.Outlined.Lock, Brand, 64.dp)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center)
        Text(error ?: subtitle, style = MaterialTheme.typography.bodyMedium, color = if (error != null) Expense else Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.graphicsLayer { translationX = shake.value }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(length) { i -> Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < pin.length) Brand else Surface2)) }
        }
        Spacer(Modifier.height(32.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(if (onBiometric != null) "bio" else "", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(Modifier.size(76.dp).tap(onClick = if (k.isEmpty()) null else { {
                        if (k == "bio") onBiometric?.invoke()
                        else if (k == "⌫") pin = pin.dropLast(1)
                        else if (pin.length < length) {
                            pin += k
                            if (pin.length == length) { val p = pin; scope.launch { delay(120); onComplete(p); pin = "" } }
                        }
                    } }).clip(CircleShape).background(if (k.isEmpty()) Bg.copy(alpha = 0f) else Surface1), contentAlignment = Alignment.Center) {
                        if (k == "bio") Icon(Icons.Outlined.Fingerprint, t("biometrics"), tint = Brand, modifier = Modifier.size(32.dp))
                        else if (k == "⌫") Icon(Icons.Outlined.Backspace, t("kt_erase"), tint = Ink)
                        else Text(k, style = MaterialTheme.typography.headlineSmall, color = Ink)
                    }
                }
            }
        }
    }
}

/** Huella / rostro con BiometricPrompt (mismo enfoque que YiSecure). Solo biometría: el respaldo es el PIN de la app. */
object Biometric {
    private const val AUTH = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun available(ctx: android.content.Context) =
        androidx.biometric.BiometricManager.from(ctx).canAuthenticate(AUTH) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(act: androidx.fragment.app.FragmentActivity, onOk: () -> Unit) {
        androidx.biometric.BiometricPrompt(act, androidx.core.content.ContextCompat.getMainExecutor(act),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: androidx.biometric.BiometricPrompt.AuthenticationResult) = onOk()
            }).authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle("Parity").setSubtitle(t("biometricsDesc"))
            .setNegativeButtonText(t("kt_use_pin")).setAllowedAuthenticators(AUTH).build())
    }
}

/** Pantalla de bloqueo a pantalla completa: huella (si está activa) y PIN. */
@Composable
fun LockScreen() {
    var error by remember { mutableStateOf<String?>(null) }
    val act = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity
    val bio = Pin.biometric && Biometric.available(act)
    val ask = { Biometric.prompt(act) { Pin.locked = false } }
    LaunchedEffect(Unit) { if (bio) ask() } // al abrir, la huella aparece sola
    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding(), contentAlignment = Alignment.Center) {
        PinPad(t("kt_enter_pin"), if (bio) t("kt_enter_pin_or_bio") else t("kt_enter_pin_desc"), error, onBiometric = if (bio) ask else null) { p ->
            error = if (Pin.check(p)) null else Pin.waitSeconds().let { s -> if (s > 0) t("kt_pin_wait", s) else t("kt_pin_wrong") }
        }
    }
}

/** Crear o cambiar el PIN: (PIN actual) → nuevo → confirmar. */
@Composable
fun PinSetupSheet(onDone: (Boolean) -> Unit) {
    var step by remember { mutableIntStateOf(if (Pin.enabled) 0 else 1) }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Sheet({ onDone(false) }) {
        when (step) {
            0 -> PinPad(t("kt_current_pin"), t("kt_current_pin_desc"), error) { p ->
                if (Pin.check(p)) { error = null; step = 1 } else error = t("kt_pin_wrong")
            }
            1 -> PinPad(t("kt_new_pin"), t("kt_new_pin_desc"), error) { p -> first = p; error = null; step = 2 }
            else -> PinPad(t("kt_confirm_pin"), t("kt_confirm_pin_desc"), error) { p ->
                if (p == first) { Pin.set(p); onDone(true) } else { error = t("kt_pin_mismatch"); step = 1 }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
