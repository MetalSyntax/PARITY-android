package com.metalsyntax.paritykt.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Reglas locales de Parity Modern (docs/REDISENO_DASHBOARD.md): todo se calcula en el teléfono, sin red ni modelos.
 */

private fun day(iso: String): LocalDate? = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()

// ------------------------------------------------------------------------------------------------ Disponible para gastar

/** Saldo − pagos planificados de los próximos 30 días − aporte del mes comprometido a metas (en USD). */
data class SafeToSpend(val balance: Double, val upcoming: List<Pair<ScheduledPayment, LocalDate>>, val upcomingUsd: Double,
                       val goals: List<Pair<Goal, Double>>) {
    val reserved get() = upcomingUsd + goals.sumOf { it.second }
    val available get() = balance - reserved
}

/** Fechas de [s] entre hoy y [until] (incluidas), según su frecuencia. */
fun occurrences(s: ScheduledPayment, today: LocalDate, until: LocalDate): List<LocalDate> {
    var d = day(s.date) ?: return emptyList()
    val out = mutableListOf<LocalDate>()
    var guard = 0
    while (!d.isAfter(until) && guard++ < 60) {
        if (!d.isBefore(today)) out += d
        if (s.frequency == Frequency.ONCE) break
        d = Store.next(d, s.frequency)
    }
    return out
}

/** Aporte mensual que pide una meta para llegar a tiempo (lo que falta ÷ meses restantes, mínimo 1). */
fun Goal.monthlyNeed(today: LocalDate): Double {
    val left = targetAmount - savedAmount
    if (completed || left <= 0) return 0.0
    val end = day(deadline) ?: return 0.0
    val months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), end.withDayOfMonth(1)).coerceAtLeast(0) + 1
    return left / months
}

fun AppData.safeToSpend(today: LocalDate = LocalDate.now()): SafeToSpend {
    val until = today.plusDays(30)
    val up = scheduled.filter { it.type == TxType.EXPENSE }.flatMap { s -> occurrences(s, today, until).map { s to it } }.sortedBy { it.second }
    val upUsd = up.sumOf { (s, _) -> rates.toUsd(s.amount, s.currency) }
    val g = goals.map { it to it.monthlyNeed(today) }.filter { it.second > 0 }
    return SafeToSpend(totalUsd(), up, upUsd, g)
}

// ------------------------------------------------------------------------------------------------ Para hoy

enum class CardKind { DUE, BUDGET, GOAL, NO_SPEND, WEEK }

/** Tarjeta accionable del bloque «Para hoy». [value] depende del tipo (monto, % o diferencia). */
data class TodayCard(val kind: CardKind, val scheduled: ScheduledPayment? = null, val date: LocalDate? = null, val budget: Budget? = null,
                     val goal: Goal? = null, val value: Double = 0.0, val change: Double? = null)

private fun AppData.expensesUsd(from: LocalDate, to: LocalDate) = transactions.filter { t ->
    t.type == TxType.EXPENSE && day(t.date)?.let { !it.isBefore(from) && !it.isAfter(to) } == true
}.sumOf { it.normalizedAmountUSD }

/**
 * Máximo 3, por urgencia: pagos que vencen hoy/mañana, presupuestos ≥ 80 %, metas ≥ 90 %, «¿hoy no gastaste?».
 * Si no hay nada, el resumen de la semana (siempre en tono positivo).
 */
fun AppData.todayCards(today: LocalDate = LocalDate.now(), noSpend: Set<String> = emptySet()): List<TodayCard> {
    val out = mutableListOf<TodayCard>()
    scheduled.mapNotNull { s -> day(s.date)?.takeIf { !it.isAfter(today.plusDays(1)) }?.let { s to it } }.sortedBy { it.second }.take(2)
        .forEach { (s, d) -> out += TodayCard(CardKind.DUE, scheduled = s, date = d, value = s.amount) }
    activeBudgets().filter { it.limit > 0 }.map { it to spentUsd(it.categoryId) }.filter { (b, s) -> s / b.limit >= 0.8 }
        .sortedByDescending { (b, s) -> s / b.limit }.forEach { (b, s) -> out += TodayCard(CardKind.BUDGET, budget = b, value = b.limit - s) }
    goals.filter { !it.completed && it.targetAmount > 0 && it.savedAmount / it.targetAmount >= 0.9 }
        .forEach { out += TodayCard(CardKind.GOAL, goal = it, value = it.savedAmount / it.targetAmount) }
    if (transactions.none { it.date.startsWith(today.toString()) } && today.toString() !in noSpend) out += TodayCard(CardKind.NO_SPEND)
    if (out.isEmpty() || out.all { it.kind == CardKind.NO_SPEND }) {
        val week = expensesUsd(today.minusDays(6), today); val prev = expensesUsd(today.minusDays(13), today.minusDays(7))
        out.add(0, TodayCard(CardKind.WEEK, value = week, change = if (prev > 0) (week - prev) / prev else null))
    }
    return out.take(3)
}

/** Días seguidos con al menos un registro (o marcados «hoy no gasté»). Hoy sin registro todavía no rompe la racha. */
fun AppData.streak(today: LocalDate = LocalDate.now(), noSpend: Set<String> = emptySet()): Int {
    val days = transactions.mapNotNull { it.date.take(10).takeIf { d -> d.length == 10 } }.toSet() + noSpend
    var d = if (today.toString() in days) today else today.minusDays(1)
    var n = 0
    while (d.toString() in days) { n++; d = d.minusDays(1) }
    return n
}

// ------------------------------------------------------------------------------------------------ registro rápido

/** Gasto frecuente para repetir con un toque (misma nota, categoría, monto, moneda y billetera, ≥ 2 veces en 90 días). */
data class Template(val note: String, val category: String, val amount: Double, val currency: Currency, val accountId: String, val count: Int)

fun AppData.templates(today: LocalDate = LocalDate.now(), max: Int = 5): List<Template> =
    transactions.filter { t -> t.type == TxType.EXPENSE && t.note.isNotBlank() && day(t.date)?.isAfter(today.minusDays(90)) == true }
        .groupBy { listOf(it.note.trim().lowercase(), it.category, it.amount, it.originalCurrency, it.accountId) }
        .filter { it.value.size >= 2 }
        .map { (_, l) -> val f = l.maxBy { it.date }; Template(f.note.trim(), f.category, f.amount, f.originalCurrency, f.accountId, l.size) to f.date }
        .sortedWith(compareByDescending<Pair<Template, String>> { it.first.count }.thenByDescending { it.second })
        .map { it.first }.take(max)

/** Categoría que se usó la última vez con la misma nota o el mismo comercio de factura. */
fun AppData.suggestCategory(note: String): String? {
    val n = note.trim().lowercase().takeIf { it.length >= 3 } ?: return null
    return transactions.filter { t -> t.type == TxType.EXPENSE && (t.note.trim().lowercase() == n ||
            t.extra.optJSONObject("invoiceData")?.optString("merchant")?.trim()?.lowercase() == n) }
        .maxByOrNull { it.date }?.category
}

/** Lo que se entendió de un dictado como «20 dólares de comida ayer». */
data class Dictation(val amount: Double?, val currency: Currency?, val category: String?, val date: LocalDate?, val note: String)

object VoiceParse {
    // límites por letras Unicode: \b no trata bien «gasté», «café» o «bolívares» en todas las JVM
    private fun w(alts: String) = Regex("""(?<!\p{L})(?:$alts)(?!\p{L})""")
    private val CUR = listOf(
        w("usdt|tether") to Currency.USDT, Regex("""(?<!\p{L})(?:d[oó]lar(?:es)?|usd|verdes?)(?!\p{L})|\$""") to Currency.USD,
        w("bol[ií]var(?:es)?|bs\\.?|bolos?") to Currency.VES, Regex("""(?<!\p{L})(?:euros?|eur)(?!\p{L})|€""") to Currency.EUR,
    )
    private val CAT = mapOf(
        "food" to "comida|almuerzo|cena|desayuno|mercado|supermercado|víveres|viveres|groceries|food|lunch|dinner",
        "coffee" to "café|cafe|empanadas?|arepas?|hamburguesas?|pizza|coffee",
        "transport" to "taxi|uber|yummy|ridery|gasolina|transporte|bus|metro|pasaje|estacionamiento",
        "bills" to "luz|agua|electricidad|condominio|servicios?",
        "internet" to "internet|wifi|datos",
        "health" to "farmacia|médico|medico|medicinas?|doctor|consulta|salud",
        "shopping" to "ropa|zapatos|compras|tienda",
        "entertainment" to "cine|salida|fiesta|concierto|juegos?",
        "subscription" to "netflix|spotify|suscripción|suscripcion|youtube|disney",
        "pets" to "perro|gato|mascota|veterinario",
        "home" to "alquiler|casa|hogar|renta",
        "education" to "curso|libros?|colegio|universidad|clases?",
        "gift" to "regalos?",
    ).mapValues { w(it.value) }
    private val DAY2 = w("ante\\s?ayer"); private val DAY1 = w("ayer")
    private val FILLER = w("ante\\s?ayer|ayer|hoy|gast[eé]|pagu[eé]|compr[eé]")

    fun parse(text: String, today: LocalDate = LocalDate.now()): Dictation {
        val low = text.lowercase()
        val num = Regex("""\d+(?:[.,]\d+)*""").find(low)
        val amount = num?.let { InvoiceParser.amount(it.value) }
        val currency = CUR.firstOrNull { it.first.containsMatchIn(low) }?.second
        val category = CAT.entries.firstOrNull { it.value.containsMatchIn(low) }?.key
        val date = when { DAY2.containsMatchIn(low) -> today.minusDays(2); DAY1.containsMatchIn(low) -> today.minusDays(1); else -> null }
        // la nota queda sin el monto, la moneda ni el día
        var note = low
        num?.let { note = note.replaceRange(it.range, " ") }
        CUR.forEach { note = it.first.replace(note, " ") }
        note = FILLER.replace(note, " ").replace(Regex("""\s+"""), " ").trim().replace(Regex("""^(de|en|por|para)\s+"""), "")
        return Dictation(amount, currency, category, date, note.replaceFirstChar { it.uppercase() })
    }
}
