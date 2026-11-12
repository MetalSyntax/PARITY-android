package com.metalsyntax.paritykt.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.metalsyntax.paritykt.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ================================================================================================ OCR en el teléfono

/** Factura leída: imagen lista para guardar (`receipt`, data URL como la web) y los datos extraídos. */
class ScannedReceipt(val dataUrl: String, val invoice: InvoiceData)

object ReceiptOcr {
    /** Imagen reducida a [max] px de lado, enderezada según el EXIF de la cámara. */
    fun load(ctx: Context, uri: Uri, max: Int): Bitmap? {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rot = runCatching {
            cr.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90; 3 -> 180; 8 -> 270; else -> 0 }
            }
        }.getOrNull() ?: 0
        val scale = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
        if (rot == 0 && scale >= 1f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()); postScale(scale, scale) }, true)
    }

    fun dataUrl(b: Bitmap): String {
        val out = java.io.ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 72, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decode(dataUrl: String?): ImageBitmap? = dataUrl?.takeIf { it.isNotBlank() }?.let { s ->
        runCatching { Base64.decode(s.substringAfter("base64,"), Base64.DEFAULT) }.getOrNull()
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    /**
     * Lee la factura de principio a fin: el modelo de texto de ML Kit (incluido en el APK, sin red) devuelve renglones con su
     * posición; aquí se vuelven a armar las filas (descripción a la izquierda, monto a la derecha) y [InvoiceParser] las interpreta.
     */
    suspend fun scan(ctx: Context, uri: Uri): ScannedReceipt = withContext(Dispatchers.Default) {
        val ocrBmp = load(ctx, uri, 2200) ?: error("image")
        // si el modelo falla, la imagen igual se adjunta y el total se escribe a mano
        val text = runCatching {
            suspendCancellableCoroutine { k ->
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(ocrBmp, 0))
                    .addOnSuccessListener { k.resume(it) }.addOnFailureListener { k.resumeWithException(it) }
            }
        }.getOrNull()
        val lines = (text?.textBlocks ?: emptyList()).flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { it to l.text } }.sortedBy { it.first.centerY() }
        val rows = mutableListOf<MutableList<Pair<android.graphics.Rect, String>>>()
        for (l in lines) {
            val row = rows.lastOrNull()
            val ref = row?.let { r -> r.sumOf { it.first.centerY() } / r.size }
            // misma fila si los centros verticales están a menos de media altura de línea
            if (row != null && ref != null && kotlin.math.abs(l.first.centerY() - ref) < minOf(l.first.height(), row.first().first.height()) * 0.55) row += l
            else rows += mutableListOf(l)
        }
        val joined = rows.map { r -> r.sortedBy { it.first.left }.joinToString("  ") { it.second } }
        val small = Bitmap.createScaledBitmap(ocrBmp, (ocrBmp.width * minOf(1f, 1400f / maxOf(ocrBmp.width, ocrBmp.height))).toInt().coerceAtLeast(1),
            (ocrBmp.height * minOf(1f, 1400f / maxOf(ocrBmp.width, ocrBmp.height))).toInt().coerceAtLeast(1), true)
        ScannedReceipt(dataUrl(small), InvoiceParser.parse(joined))
    }
}

/** Uri temporal para que la cámara guarde la foto (FileProvider, sin permiso de cámara: lo hace la app de cámara). */
fun cameraUri(ctx: Context): Uri {
    val f = File(ctx.cacheDir, "scan").apply { mkdirs() }.let { File(it, "factura.jpg") }
    return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
}

/** Botones de cámara, galería y micrófono para la nota; devuelve el estado de escaneo y la factura leída por [onScanned]. */
class ReceiptActions(val camera: () -> Unit, val gallery: () -> Unit, val dictate: () -> Unit, val scanning: Boolean)

@Composable
fun rememberReceiptActions(onDictated: (String) -> Unit, onScanned: (ScannedReceipt?) -> Unit): ReceiptActions {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<Uri?>(null) }
    fun scan(uri: Uri) {
        scanning = true
        scope.launch {
            val r = runCatching { ReceiptOcr.scan(ctx, uri) }.getOrNull()
            scanning = false; onScanned(r)
        }
    }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> shot?.takeIf { ok }?.let(::scan) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(::scan) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onDictated)
    }
    return ReceiptActions(
        camera = { shot = cameraUri(ctx); runCatching { cam.launch(shot!!) }.onFailure { toast(ctx, t("kt_no_camera")) } },
        gallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        dictate = {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, I18n.locale.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // dictado con el paquete de voz del teléfono, sin red
                .putExtra(RecognizerIntent.EXTRA_PROMPT, t("kt_dictate_prompt"))
            runCatching { speech.launch(i) }.onFailure { toast(ctx, t("kt_no_dictation")) }
        },
        scanning = scanning,
    )
}

fun toast(ctx: Context, msg: String) = android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()

// ================================================================================================ UI de la factura

/** Aviso animado mientras el modelo lee la factura (barrido de un «láser» sobre un ticket). */
@Composable
fun ScanningBanner() {
    val x by rememberInfiniteTransition(label = "scan").animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.copy(alpha = 0.12f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp, 34.dp).clip(RoundedCornerShape(6.dp)).background(Surface1)) {
            Box(Modifier.fillMaxWidth().height(2.dp).graphicsLayer { translationY = x * 32.dp.toPx() }.background(Brand))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(t("kt_reading_receipt"), style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(t("kt_reading_receipt_desc"), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

/** Ítems, subtotal, IVA y total de la factura. [onRemove] permite quitar renglones mal leídos. */
@Composable
fun InvoiceLines(inv: InvoiceData, currency: Currency, onRemove: ((Int) -> Unit)? = null) {
    val c = inv.currency ?: currency
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (inv.items.isEmpty()) Text(t("kt_no_items"), style = MaterialTheme.typography.bodySmall, color = Muted)
        inv.items.forEachIndexed { i, it ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(it.description, style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (it.quantity != null && it.quantity != 1.0) Text("${trimQty(it.quantity)} × ${num(it.unitPrice ?: 0.0)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Text(money(it.total, c), style = MaterialTheme.typography.titleSmall, color = Ink)
                onRemove?.let { rm -> Spacer(Modifier.width(4.dp)); IconButton({ rm(i) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, t("delete"), tint = Muted, modifier = Modifier.size(16.dp)) } }
            }
        }
        HorizontalDivider(color = Glass)
        inv.subtotal?.let { Line(t("kt_subtotal"), money(it, c)) }
        inv.tax?.let { Line(t("kt_tax"), money(it, c)) }
        inv.total?.let { Line(t("total"), money(it, c), Brand) }
    }
}

private fun trimQty(q: Double) = if (q % 1.0 == 0.0) q.toLong().toString() else num(q)

@Composable
private fun Line(label: String, value: String, color: Color = Ink) = Row {
    Caps(label, Modifier.weight(1f)); Text(value, style = MaterialTheme.typography.titleSmall, color = color)
}

/** Revisión tras leer la factura: imagen, comercio, ítems y total; «Usar» rellena el monto, la nota, la moneda y la fecha. */
@Composable
fun ReceiptReview(scan: ScannedReceipt, currency: Currency, onRemove: (() -> Unit)? = null, onUse: (InvoiceData) -> Unit, onDismiss: () -> Unit) {
    var inv by remember { mutableStateOf(scan.invoice) }
    val img = remember(scan) { ReceiptOcr.decode(scan.dataUrl) }
    TitledSheet(inv.merchant ?: t("receipt"), inv.invoiceDate?.let(::shortDate) ?: t("kt_items_found", inv.items.size), onDismiss) {
        Spacer(Modifier.height(12.dp))
        img?.let { Image(it, t("receipt"), Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black), contentScale = ContentScale.Crop) }
        Spacer(Modifier.height(12.dp))
        InvoiceLines(inv, currency) { i ->
            val items = inv.items.toMutableList().also { it.removeAt(i) }
            // si el total salió de la suma de ítems, se recalcula
            val fromSum = inv.total == inv.items.sumOf { it.total }
            inv = inv.copy(items = items, total = if (fromSum) items.sumOf { it.total } else inv.total)
        }
        if (inv.total == null) Text(t("kt_no_total"), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Warning)
        Spacer(Modifier.height(14.dp))
        BrandButton(t("kt_use_receipt"), Modifier.fillMaxWidth().height(52.dp), Icons.Outlined.Check) { onUse(inv) }
        onRemove?.let { LinkText(t("kt_remove_receipt"), Expense, Modifier.fillMaxWidth().padding(top = 14.dp), onClick = it) }
    }
}
