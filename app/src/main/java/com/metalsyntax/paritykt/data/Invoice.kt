package com.metalsyntax.paritykt.data

import org.json.JSONArray
import org.json.JSONObject

/** Renglón de la factura (InvoiceLineItem de la web: packages/core/src/services/ocr/types.ts). */
data class InvoiceItem(val description: String, val quantity: Double? = null, val unitPrice: Double? = null, val total: Double)

/** InvoiceData de la web: se guarda en la transacción como `invoiceData`, junto a la imagen en `receipt`. */
data class InvoiceData(
    val merchant: String? = null, val invoiceDate: String? = null, val items: List<InvoiceItem> = emptyList(),
    val subtotal: Double? = null, val tax: Double? = null, val total: Double? = null, val currency: Currency? = null,
    val rawText: String = "", val digitizedAt: String = nowIso(),
) {
    fun toJson(): JSONObject = JSONObject().put("lineItems", JSONArray(items.map { i ->
        JSONObject().put("description", i.description).put("total", i.total).apply { i.quantity?.let { put("quantity", it) }; i.unitPrice?.let { put("unitPrice", it) } }
    })).put("ocrProvider", "mlkit").put("rawText", rawText).put("digitizedAt", digitizedAt).apply {
        merchant?.let { put("merchant", it) }; invoiceDate?.let { put("invoiceDate", it) }; subtotal?.let { put("subtotal", it) }
        tax?.let { put("tax", it) }; total?.let { put("total", it) }; currency?.let { put("currency", it.name) }
    }

    companion object {
        fun from(o: JSONObject?): InvoiceData? = o?.let {
            val a = it.optJSONArray("lineItems") ?: JSONArray()
            InvoiceData(it.optString("merchant").ifBlank { null }, it.optString("invoiceDate").ifBlank { null },
                (0 until a.length()).map { i -> a.getJSONObject(i) }.map { i ->
                    InvoiceItem(i.optString("description"), i.optDouble("quantity").takeIf { q -> !q.isNaN() },
                        i.optDouble("unitPrice").takeIf { p -> !p.isNaN() }, i.optDouble("total", 0.0))
                }, it.optDouble("subtotal").takeIf { v -> !v.isNaN() }, it.optDouble("tax").takeIf { v -> !v.isNaN() },
                it.optDouble("total").takeIf { v -> !v.isNaN() }, Currency.entries.firstOrNull { c -> c.name == it.optString("currency") },
                it.optString("rawText"), it.optString("digitizedAt", nowIso()))
        }
    }
}

/**
 * Lee una factura completa a partir de sus renglones de texto (ya reconstruidos por el OCR de arriba abajo, cada renglón con
 * su texto de izquierda a derecha). Sin red ni modelos externos: reglas para tickets fiscales venezolanos y facturas comunes
 * (montos 1.234,56 o 1,234.56; «2 x 3,50»; TOTAL / SUBTOTAL / IVA / BASE IMPONIBLE).
 */
object InvoiceParser {
    // monto al final del renglón, con o sin símbolo de moneda
    private val TRAILING = Regex("""(?:bs\.?s?|ves|usd|us\$|\$|€|eur)?\s*(-?\d{1,3}(?:[.,\s]\d{3})*(?:[.,]\d{1,2})|-?\d+(?:[.,]\d{1,2})?)\s*(?:bs\.?s?|ves|usd|\$|€|eur)?\s*[a-z*()]{0,3}$""", RegexOption.IGNORE_CASE)
    private val QTY_X = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*[xX×*]\s*(?:bs\.?\s*|\$\s*)?(\d[\d.,]*)\s*$""")
    private val QTY_LEAD = Regex("""^\s*(\d{1,3}(?:[.,]\d+)?)\s*[xX×*]?\s+(\D.*)$""")
    private val DATE = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})\b|\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val TOTAL = Regex("""^\s*(total|monto total|total a pagar|importe total|total general|total bs|total usd|total \$)\b""", RegexOption.IGNORE_CASE)
    private val SUBTOTAL = Regex("""\b(sub\s*-?\s*total|base imponible|bi\s*g?\s*\d|base\s*g)\b""", RegexOption.IGNORE_CASE)
    private val TAX = Regex("""\b(iva|i\.v\.a|impuesto|tax|igtf)\b""", RegexOption.IGNORE_CASE)
    // renglones con monto que no son productos
    private val NOT_ITEM = Regex("""\b(rif|r\.i\.f|nit|cedula|c\.i|fecha|hora|caja|cajero|factura|ticket|tel[eé]fono|tlf|cambio|vuelto|efectivo|tarjeta|d[eé]bito|cr[eé]dito|pago|zelle|pago m[oó]vil|transf|exento|exonerado|redondeo|n[°º]|nro|serial|maquina|z\d|items?|art[ií]culos|saldo|referencia|ref|lote|aprob|terminal|afiliado|tasa|cliente|direcci[oó]n|recibido|propina)\b""", RegexOption.IGNORE_CASE)

    /** "1.234,56" / "1,234.56" / "1234.5" / "12" → número; el último separador con 1–2 cifras detrás es el decimal. */
    fun amount(s: String): Double? {
        val c = s.replace(" ", "").trim().trimStart('-')
        if (c.isEmpty() || !c.first().isDigit()) return null
        val i = c.indexOfLast { it == '.' || it == ',' }
        val v = if (i >= 0 && c.length - i - 1 in 1..2) c.substring(0, i).replace(".", "").replace(",", "") + "." + c.substring(i + 1)
        else c.replace(".", "").replace(",", "")
        return v.toDoubleOrNull()?.let { if (s.trim().startsWith("-")) -it else it }
    }

    private fun trailing(row: String): Pair<String, Double>? = TRAILING.find(row.trim())?.let { m ->
        val desc = row.trim().substring(0, m.range.first).trim().trimEnd(':', '.', '-', '=').trim()
        amount(m.groupValues[1])?.let { desc to it }
    }

    private fun currency(text: String): Currency? = when {
        Regex("""\b(bs\.?s?|ves|bol[ií]vares)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Currency.VES
        text.contains('€') || Regex("""\beur\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Currency.EUR
        text.contains('$') || Regex("""\busd\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Currency.USD
        else -> null
    }

    private fun date(text: String): String? = DATE.find(text)?.let { m ->
        runCatching {
            if (m.groupValues[4].isNotEmpty()) java.time.LocalDate.of(m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt())
            else {
                val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
                java.time.LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()) // dd/mm/aaaa, como en Venezuela
            }
        }.getOrNull()?.toString()
    }

    fun parse(rows: List<String>): InvoiceData {
        val clean = rows.map { it.replace('\t', ' ').replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        val text = clean.joinToString("\n")
        var total: Double? = null; var subtotal: Double? = null; var tax: Double? = null
        val items = mutableListOf<InvoiceItem>()
        var pendingQty: Pair<Double, Double>? = null // «2 x 3,50» en su propio renglón: se une al siguiente producto
        var totalsStarted = false
        for (row in clean) {
            QTY_X.find(row)?.let { m ->
                val q = amount(m.groupValues[1]); val p = amount(m.groupValues[2])
                if (q != null && p != null) pendingQty = q to p
            }
            if (QTY_X.matches(row)) continue
            val t = trailing(row) ?: continue // renglón sin monto (descripción partida, encabezado): no corta la «2 x 3,50» pendiente
            when {
                TOTAL.containsMatchIn(row) && !SUBTOTAL.containsMatchIn(row) -> { if (total == null) total = t.second; totalsStarted = true }
                SUBTOTAL.containsMatchIn(row) -> { if (subtotal == null) subtotal = t.second; totalsStarted = true }
                TAX.containsMatchIn(row) -> { tax = (tax ?: 0.0) + t.second; totalsStarted = true }
                totalsStarted || NOT_ITEM.containsMatchIn(row) -> Unit
                t.first.count { it.isLetter() } >= 2 -> {
                    var desc = t.first; var qty = pendingQty?.first; var unit = pendingQty?.second
                    if (qty == null) QTY_LEAD.find(desc)?.let { m -> amount(m.groupValues[1])?.takeIf { it in 1.0..999.0 }?.let { qty = it; desc = m.groupValues[2].trim() } }
                    desc = desc.replace(Regex("""\s*\((e|g|r|a)\)\s*$""", RegexOption.IGNORE_CASE), "").trim()
                    if (unit == null && qty != null && qty!! > 0) unit = t.second / qty!!
                    items += InvoiceItem(desc, qty, unit, t.second)
                }
            }
            pendingQty = null
        }
        // comercio: el primer renglón con palabras que no sea dato fiscal ni monto
        val merchant = clean.take(6).firstOrNull { r -> r.count { it.isLetter() } >= 3 && !NOT_ITEM.containsMatchIn(r) && trailing(r) == null && !TAX.containsMatchIn(r) }
        val sum = items.sumOf { it.total }.takeIf { items.isNotEmpty() }
        return InvoiceData(merchant = merchant?.take(60), invoiceDate = date(text), items = items, subtotal = subtotal, tax = tax,
            total = total ?: subtotal?.let { it + (tax ?: 0.0) } ?: sum, currency = currency(text), rawText = text)
    }
}
