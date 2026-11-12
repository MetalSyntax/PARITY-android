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

    private val month = today.toString().take(7)

    @Test fun goalsAreNeverReserved() {
        val d = AppData(accounts = listOf(acc), goals = listOf(Goal(name = "Casa", targetAmount = 50000.0, savedAmount = 100.0, deadline = today.plusMonths(2).toString())))
        val s = d.safeToSpend(today)
        assertEquals(1000.0, s.available, 1e-9) // meta enorme sin aporte definido: no resta nada
    }

    @Test fun payCycleDetection() {
        fun inc(day: Int, f: Frequency = Frequency.MONTHLY) = ScheduledPayment(name = "Sueldo", amount = 250.0, currency = Currency.USD,
            date = today.withDayOfMonth(day).toString(), frequency = f, type = TxType.INCOME)
        assertEquals(PayCycle.BIWEEKLY, AppData(scheduled = listOf(inc(1), inc(15))).detectPayCycle(today))   // 1 y 15
        assertEquals(PayCycle.MONTHLY, AppData(scheduled = listOf(inc(1))).detectPayCycle(today))
        assertEquals(PayCycle.WEEKLY, AppData(scheduled = listOf(inc(1, Frequency.WEEKLY))).detectPayCycle(today))
        val hist = (0..3).map { tx(250.0, it * 14L).copy(type = TxType.INCOME, category = "income") }
        assertEquals(PayCycle.BIWEEKLY, AppData(transactions = hist).detectPayCycle(today))
    }

    @Test fun biweeklyGetsHalfOfEachMonthlyEnvelope() {
        // 1ª quincena: sobres de 500 al mes → 250 para esta quincena; saldo 550 (250 cobrados + 300 que sobraron)
        val day3 = today.withDayOfMonth(3)
        val m = day3.toString().take(7)
        val d = AppData(accounts = listOf(acc.copy(balance = 550.0)), budgets = listOf(Budget("food", 300.0, m), Budget("transport", 200.0, m)),
            transactions = listOf(tx(250.0, 0).copy(type = TxType.INCOME, category = "income", date = day3.toString() + "T09:00:00"),
                tx(40.0, 0).copy(date = day3.toString() + "T12:00:00")))
        val s = d.safeToSpend(day3, PayCycle.BIWEEKLY)
        assertEquals(day3.withDayOfMonth(14), s.period.end)
        // comida: 150 de la quincena − 40 gastados = 110; transporte: 100
        assertEquals(210.0, s.envelopesUsd, 1e-9)
        assertEquals(550.0 - 210.0, s.available, 1e-9)        // no se come todo el saldo como si fuera el mes entero
        val monthly = d.safeToSpend(day3, PayCycle.MONTHLY)
        assertEquals(260.0 + 200.0, monthly.envelopesUsd, 1e-9)
    }

    @Test fun multipleEmployersAndLateIncome() {
        fun inc(name: String, date: LocalDate) = ScheduledPayment(name = name, amount = 200.0, currency = Currency.USD, date = date.toString(),
            frequency = Frequency.MONTHLY, type = TxType.INCOME)
        val d = AppData(accounts = listOf(acc), scheduled = listOf(inc("Empresa A", today.minusDays(2)), inc("Empresa B", today.plusDays(5)),
            inc("Freelance", today.plusDays(12))))
        assertEquals(PayCycle.VARIABLE, d.detectPayCycle(today))
        val up = d.upcomingIncomes(today, 30)
        assertTrue(up.first().late); assertEquals("Empresa A", up.first().s.name)       // el atrasado primero
        assertEquals("Empresa B", up[1].s.name)
        assertEquals(CardKind.INCOME, d.todayCards(today).first().kind)
    }

    @Test fun envelopesFollowNextExpectedIncome() {
        // cobro esperado en 10 días: cada sobre aparta lo que le queda del mes × días hasta el cobro / días que quedan del mes
        val start = today.withDayOfMonth(1)
        val m = start.toString().take(7)
        val d = AppData(accounts = listOf(acc), budgets = listOf(Budget("food", 300.0, m)),
            scheduled = listOf(ScheduledPayment(name = "Cliente", amount = 400.0, currency = Currency.USD, date = start.plusDays(10).toString(),
                frequency = Frequency.ONCE, type = TxType.INCOME)))
        val s = d.safeToSpend(start, PayCycle.VARIABLE)
        assertEquals(start.plusDays(9), s.period.end)
        assertEquals(300.0 * 10.0 / start.lengthOfMonth(), s.envelopesUsd, 1e-9)
    }

    @Test fun balanceAtIncludesCarryOver() {
        val d = AppData(accounts = listOf(acc.copy(balance = 510.0)), transactions = listOf(
            tx(250.0, 0).copy(type = TxType.INCOME, category = "income"), tx(40.0, 0)))
        assertEquals(300.0, d.balanceAt(today), 1e-9)          // lo que había antes de cobrar y gastar hoy
    }

    @Test fun envelopesNeverPushAvailableBelowZero() {
        val d = AppData(accounts = listOf(acc.copy(balance = 100.0)), budgets = listOf(Budget("food", 500.0, month)),
            scheduled = listOf(ScheduledPayment(name = "Luz", amount = 30.0, currency = Currency.USD, date = today.toString(), frequency = Frequency.ONCE, category = "bills")))
        val s = d.safeToSpend(today, PayCycle.MONTHLY)
        assertTrue(s.capped)
        assertEquals(0.0, s.available, 1e-9)        // sobres solo usan los 70 libres
        val broke = AppData(accounts = listOf(acc.copy(balance = 10.0)), scheduled = d.scheduled).safeToSpend(today, PayCycle.MONTHLY)
        assertEquals(-20.0, broke.available, 1e-9)  // negativo solo si los pagos fijos superan el saldo
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
