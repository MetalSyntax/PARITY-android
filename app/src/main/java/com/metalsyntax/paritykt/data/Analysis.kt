package com.metalsyntax.paritykt.data

import java.time.YearMonth

/** Cálculos de la vista Análisis, iguales a apps/web/views/AnalysisView.tsx (montos en USD normalizado). */
/** [share] = fracción del ingreso (gasto excesivo); [count] = cantidad de gastos (fuga hormiga). */
data class Leak(val impulse: Boolean, val category: Category, val amount: Double, val share: Double, val count: Int)

data class MonthAnalysis(
    val income: Double, val expense: Double,
    val topExpenses: List<Pair<Category, Double>>, // top 4, como la web
    val incomeByCategory: List<Pair<Category, Double>>,
    val leaks: List<Leak>,
) { val net get() = income - expense }

fun AppData.analysis(m: YearMonth): MonthAnalysis {
    val tx = monthTx(m)
    fun byCat(type: TxType) = tx.filter { it.type == type }.groupBy { it.category }
    val exp = byCat(TxType.EXPENSE)
    val income = tx.filter { it.type == TxType.INCOME }.sumOf { it.normalizedAmountUSD }
    val expense = tx.filter { it.type == TxType.EXPENSE }.sumOf { it.normalizedAmountUSD }
    val leaks = exp.flatMap { (id, list) ->
        val total = list.sumOf { it.normalizedAmountUSD }
        val cat = category(id)
        listOfNotNull(
            // gasto excesivo: > 35 % de los ingresos (ahorro e inversión no son fugas)
            if (id != "investment" && id != "savings" && income > 0 && total > income * 0.35)
                Leak(false, cat, total, total / income, list.size) else null,
            // fuga hormiga: más de 6 gastos con promedio < 15 USD
            if (id != "investment" && id != "savings" && list.size > 6 && total / list.size < 15)
                Leak(true, cat, total, if (income > 0) total / income else 0.0, list.size) else null,
        )
    }
    return MonthAnalysis(
        income, expense,
        exp.map { (id, l) -> category(id) to l.sumOf { it.normalizedAmountUSD } }.filter { it.second > 0 }.sortedByDescending { it.second }.take(4),
        byCat(TxType.INCOME).map { (id, l) -> category(id) to l.sumOf { it.normalizedAmountUSD } }.filter { it.second > 0 }.sortedByDescending { it.second },
        leaks,
    )
}

/** Ingresos y gastos de los últimos [n] meses hasta [last] inclusive (para el gráfico). */
fun AppData.monthlyTotals(last: YearMonth, n: Int = 6): List<Triple<YearMonth, Double, Double>> =
    (n - 1 downTo 0).map { last.minusMonths(it.toLong()) }.map { m ->
        val t = monthTx(m)
        Triple(m, t.filter { it.type == TxType.INCOME }.sumOf { it.normalizedAmountUSD }, t.filter { it.type == TxType.EXPENSE }.sumOf { it.normalizedAmountUSD })
    }
