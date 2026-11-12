package com.metalsyntax.paritykt.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Reglas locales de Parity Modern (docs/REDISENO_DASHBOARD.md): todo se calcula en el teléfono, sin red ni modelos.
 */

private fun day(iso: String): LocalDate? = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()

// ------------------------------------------------------------------------------------------------ ritmo de ingresos

/** Cada cuánto cobra la persona. Quincenal = 1–14 y 15–fin de mes (pagos del 1 y 15). */
enum class PayCycle(val key: String) {
    MONTHLY("kt_cycle_monthly"), BIWEEKLY("kt_cycle_biweekly"), WEEKLY("kt_cycle_weekly"),
    VARIABLE("kt_cycle_variable"), // varios ingresos en fechas distintas: el período llega hasta el próximo cobro esperado
}

/** Período de cobro actual y qué parte de un sobre mensual le toca. */
data class Period(val start: LocalDate, val end: LocalDate, val share: Double)

fun PayCycle.period(today: LocalDate): Period = when (this) {
    PayCycle.MONTHLY, PayCycle.VARIABLE -> Period(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), 1.0)
    PayCycle.BIWEEKLY -> if (today.dayOfMonth < 15) Period(today.withDayOfMonth(1), today.withDayOfMonth(14), 0.5)
                         else Period(today.withDayOfMonth(15), today.withDayOfMonth(today.lengthOfMonth()), 0.5)
    PayCycle.WEEKLY -> today.minusDays(today.dayOfWeek.value - 1L).let { Period(it, it.plusDays(6), 7.0 / today.lengthOfMonth()) }
}

/**
 * Ritmo de cobro: primero los ingresos planificados (semanal / quincenal / dos ingresos mensuales en días distintos = quincenal);
 * si no hay, el historial de ingresos de los últimos 62 días (separación promedio entre cobros). Por defecto, mensual.
 */
fun AppData.detectPayCycle(today: LocalDate = LocalDate.now()): PayCycle {
    val inc = scheduled.filter { it.type == TxType.INCOME && it.frequency != Frequency.ONCE }
    val freqs = inc.map { it.frequency }.toSet()
    val monthlyDays = inc.filter { it.frequency == Frequency.MONTHLY }.mapNotNull { day(it.date)?.dayOfMonth }.distinct()
    when {
        freqs.size > 1 || monthlyDays.size >= 3 -> return PayCycle.VARIABLE          // varias empresas / fechas
        Frequency.WEEKLY in freqs -> return PayCycle.WEEKLY
        Frequency.BIWEEKLY in freqs || monthlyDays.size == 2 -> return PayCycle.BIWEEKLY
        Frequency.MONTHLY in freqs -> return PayCycle.MONTHLY
    }
    val days = transactions.filter { it.type == TxType.INCOME }.mapNotNull { day(it.date) }.filter { it.isAfter(today.minusDays(62)) }.distinct().sorted()
    if (days.size < 2) return PayCycle.MONTHLY
    val gaps = days.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
    val gap = gaps.average()
    if (gaps.size >= 2 && gaps.max() >= 3 * gaps.min().coerceAtLeast(1)) return PayCycle.VARIABLE // cobros irregulares
    return when { gap <= 9 -> PayCycle.WEEKLY; gap <= 20 -> PayCycle.BIWEEKLY; else -> PayCycle.MONTHLY }
}

/** Saldo total que había al empezar [date] (saldo de hoy menos lo que entró y salió desde entonces). Incluye lo que sobró antes. */
fun AppData.balanceAt(date: LocalDate): Double = totalUsd() - transactions.filter { t -> day(t.date)?.let { !it.isBefore(date) } == true }.sumOf {
    when (it.type) { TxType.INCOME -> it.normalizedAmountUSD; TxType.EXPENSE -> -it.normalizedAmountUSD; else -> 0.0 }
}

/** Cobros esperados: ingresos planificados atrasados (fecha pasada sin registrar) y los de los próximos [days] días. */
data class ExpectedIncome(val s: ScheduledPayment, val date: LocalDate, val late: Boolean)

fun AppData.upcomingIncomes(today: LocalDate = LocalDate.now(), days: Long = 30): List<ExpectedIncome> =
    scheduled.filter { it.type == TxType.INCOME }.flatMap { s ->
        val first = day(s.date)
        if (first != null && first.isBefore(today)) listOf(ExpectedIncome(s, first, true))
        else occurrences(s, today, today.plusDays(days)).map { ExpectedIncome(s, it, false) }
    }.sortedWith(compareByDescending<ExpectedIncome> { it.late }.thenBy { it.date })

// ------------------------------------------------------------------------------------------------ recomendación del período

/**
 * Recomendación para el período de cobro actual (todo en USD). El número principal de la app es el SALDO; esto es la guía:
 * - [bills]: pagos planificados (gastos) que vencen de hoy al fin del período (o al día antes del próximo ingreso planificado).
 * - [envelopes]: la parte de cada sobre mensual que le toca al período (½ en quincenal) menos lo gastado en esa categoría en
 *   el período, sin pasar de lo que le queda al sobre en el mes y sin contar dos veces un pago de la misma categoría.
 *   Es flexible: nunca lleva la recomendación a negativo ([capped] = no alcanzó el dinero libre).
 * - Las metas NO se reservan: ahorro opcional.
 */
data class SafeToSpend(val balance: Double, val cycle: PayCycle, val period: Period, val upcoming: List<Pair<ScheduledPayment, LocalDate>>,
                       val billsUsd: Double, val envelopes: List<Pair<Budget, Double>>, val envelopesUsd: Double, val capped: Boolean) {
    val reserved get() = billsUsd + envelopesUsd
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

fun AppData.safeToSpend(today: LocalDate = LocalDate.now(), cycle: PayCycle = detectPayCycle(today)): SafeToSpend {
    val period = cycle.period(today)
    val nextIncome = scheduled.filter { it.type == TxType.INCOME }.flatMap { occurrences(it, today.plusDays(1), period.end) }.minOrNull()
    val horizon = nextIncome?.minusDays(1) ?: period.end
    val up = scheduled.filter { it.type == TxType.EXPENSE }.flatMap { s -> occurrences(s, today, horizon).map { s to it } }.sortedBy { it.second }
    val usd = { s: ScheduledPayment -> rates.toUsd(s.amount, s.currency) }
    val bills = up.sumOf { usd(it.first) }
    val month = java.time.YearMonth.from(today)
    fun spentIn(cat: String, from: LocalDate) = transactions.filter { t ->
        t.type == TxType.EXPENSE && t.category == cat && day(t.date)?.let { !it.isBefore(from) && !it.isAfter(today) } == true
    }.sumOf { it.normalizedAmountUSD }
    // con un cobro esperado: cada sobre reserva lo que le queda del mes en proporción a los días hasta ese cobro
    // (sirve para cualquier mezcla de empresas y fechas); sin cobro conocido: la parte del período según el ritmo
    val daysLeftInMonth = ChronoUnit.DAYS.between(today, today.withDayOfMonth(today.lengthOfMonth())) + 1
    val pace = nextIncome?.let { (ChronoUnit.DAYS.between(today, it).toDouble() / daysLeftInMonth).coerceIn(0.0, 1.0) }
    val env = activeBudgets(month).filter { it.limit > 0 }.map { b ->
        val monthLeft = (b.limit - spentUsd(b.categoryId, month)).coerceAtLeast(0.0)
        val periodLeft = if (pace != null) monthLeft * pace else (b.limit * period.share - spentIn(b.categoryId, period.start)).coerceAtLeast(0.0)
        val billsHere = up.filter { it.first.category == b.categoryId }.sumOf { usd(it.first) }
        b to (minOf(periodLeft, monthLeft) - billsHere).coerceAtLeast(0.0)
    }.filter { it.second > 0.005 }
    val envTotal = env.sumOf { it.second }
    val free = (totalUsd() - bills).coerceAtLeast(0.0)
    return SafeToSpend(totalUsd(), cycle, period.copy(end = horizon), up, bills, env, minOf(envTotal, free), envTotal > free + 0.005)
}

// ------------------------------------------------------------------------------------------------ Para hoy

enum class CardKind { DUE, INCOME, BUDGET, GOAL, NO_SPEND, WEEK }

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
    // cobros atrasados primero (avisar que no ha llegado), luego pagos que vencen, luego cobros de los próximos 3 días
    upcomingIncomes(today, 3).filter { it.late }.take(2).forEach { out += TodayCard(CardKind.INCOME, scheduled = it.s, date = it.date, value = it.s.amount) }
    scheduled.filter { it.type == TxType.EXPENSE }.mapNotNull { s -> day(s.date)?.takeIf { !it.isAfter(today.plusDays(1)) }?.let { s to it } }
        .sortedBy { it.second }.take(2).forEach { (s, d) -> out += TodayCard(CardKind.DUE, scheduled = s, date = d, value = s.amount) }
    upcomingIncomes(today, 3).filter { !it.late }.take(1).forEach { out += TodayCard(CardKind.INCOME, scheduled = it.s, date = it.date, value = it.s.amount) }
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
