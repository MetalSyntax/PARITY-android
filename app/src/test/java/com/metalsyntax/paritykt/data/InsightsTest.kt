package com.metalsyntax.paritykt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private val today = LocalDate.now()
    private val acc = Account(id = "a", name = "Zelle", currency = Currency.USD, balance = 1000.0)
    private fun tx(amount: Double, daysAgo: Long, note: String = "", cat: String = "food") = Transaction(amount = amount, originalCurrency = Currency.USD,
        exchangeRate = 40.0, normalizedAmountUSD = amount, type = TxType.EXPENSE, category = cat, accountId = "a", note = note,
        date = today.minusDays(daysAgo).toString() + "T10:00:00")

    @Test fun safeToSpendReservesUpcomingAndGoals() {
        val d = AppData(accounts = listOf(acc), rates = Rates(usd = 40.0),
            scheduled = listOf(
                ScheduledPayment(name = "Netflix", amount = 15.0, currency = Currency.USD, date = today.plusDays(3).toString(), frequency = Frequency.MONTHLY),
                ScheduledPayment(name = "Café", amount = 2.0, currency = Currency.USD, date = today.toString(), frequency = Frequency.WEEKLY),
                ScheduledPayment(name = "Sueldo", amount = 500.0, currency = Currency.USD, date = today.toString(), frequency = Frequency.MONTHLY, type = TxType.INCOME)),
            goals = listOf(Goal(name = "Laptop", targetAmount = 600.0, savedAmount = 300.0, deadline = today.plusMonths(2).toString())))
        val s = d.safeToSpend(today)
        // Netflix 1 vez + café semanal 5 veces en 31 días; el ingreso no reserva
        assertEquals(15.0 + 2.0 * 5, s.upcomingUsd, 1e-9)
        assertEquals(100.0, s.goals.single().second, 1e-9) // faltan 300 en 3 meses
        assertEquals(1000.0 - 25.0 - 100.0, s.available, 1e-9)
    }

    @Test fun todayCardsAndWeekFallback() {
        val due = ScheduledPayment(name = "Luz", amount = 10.0, currency = Currency.USD, date = today.plusDays(1).toString(), frequency = Frequency.MONTHLY)
        val withDue = AppData(accounts = listOf(acc), scheduled = listOf(due), transactions = listOf(tx(5.0, 0)))
        assertEquals(CardKind.DUE, withDue.todayCards(today).first().kind)
        val calm = AppData(accounts = listOf(acc), transactions = listOf(tx(30.0, 1), tx(60.0, 9), tx(4.0, 0)))
        val week = calm.todayCards(today).single()
        assertEquals(CardKind.WEEK, week.kind); assertEquals(34.0, week.value, 1e-9); assertEquals((34.0 - 60.0) / 60.0, week.change!!, 1e-9)
        // sin registros hoy: ofrece marcar el día sin gastos (y el resumen semanal)
        assertTrue(AppData(accounts = listOf(acc)).todayCards(today).any { it.kind == CardKind.NO_SPEND })
    }

    @Test fun streakCountsConsecutiveDays() {
        val d = AppData(transactions = listOf(tx(1.0, 1), tx(1.0, 2), tx(1.0, 4)))
        assertEquals(2, d.streak(today))                                               // hoy aún sin registro no rompe
        assertEquals(5, d.streak(today, setOf(today.toString(), today.minusDays(3).toString())))
    }

    @Test fun templatesAndSuggestion() {
        val d = AppData(transactions = listOf(tx(3.5, 1, "Café", "coffee"), tx(3.5, 3, "café ", "coffee"), tx(20.0, 2, "Mercado")))
        val t = d.templates(today).single()
        assertEquals("Café", t.note); assertEquals(2, t.count)
        assertEquals("coffee", d.suggestCategory("CAFÉ"))
        assertNull(d.suggestCategory("xy"))
    }

    @Test fun dictation() {
        val p = VoiceParse.parse("gasté 20 dólares de comida ayer", today)
        assertEquals(20.0, p.amount!!, 1e-9); assertEquals(Currency.USD, p.currency); assertEquals("food", p.category)
        assertEquals(today.minusDays(1), p.date); assertEquals("Comida", p.note)
        val b = VoiceParse.parse("taxi 150 bolívares", today)
        assertEquals(Currency.VES, b.currency); assertEquals("transport", b.category); assertNull(b.date); assertEquals("Taxi", b.note)
    }
}
