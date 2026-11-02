package com.metalsyntax.paritykt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class StoreTest {
    private lateinit var usd: Account
    private lateinit var ves: Account

    @Before fun setUp() {
        Store.initForTest(Files.createTempDirectory("parity").toFile())
        Store.setRates(Rates(usd = 40.0, eur = 44.0, updatedAt = nowIso()))
        usd = Account(name = "Zelle", currency = Currency.USD, balance = 100.0).also(Store::saveAccount)
        ves = Account(name = "Banco", currency = Currency.VES, balance = 4000.0).also(Store::saveAccount)
    }

    private fun bal(a: Account) = Store.data.account(a.id)!!.balance
    private fun tx(type: TxType, amount: Double, from: Account, to: Account? = null, fee: Double? = null) =
        Transaction(amount = amount, originalCurrency = from.currency, exchangeRate = 0.0, normalizedAmountUSD = 0.0, type = type,
            category = "food", accountId = from.id, toAccountId = to?.id, date = "2026-11-02T10:00", fee = fee)

    @Test fun expenseAndIncomeMoveBalanceAndNormalizeToUsd() {
        val e = tx(TxType.EXPENSE, 800.0, ves).also(Store::saveTx)
        assertEquals(3200.0, bal(ves), 1e-9)
        assertEquals(20.0, Store.data.transactions.first { it.id == e.id }.normalizedAmountUSD, 1e-9) // 800 Bs / 40
        Store.saveTx(tx(TxType.INCOME, 50.0, usd))
        assertEquals(150.0, bal(usd), 1e-9)
    }

    @Test fun editRevertsOldEffectAndDeleteRestoresAndLeavesTombstone() {
        val e = tx(TxType.EXPENSE, 30.0, usd).also(Store::saveTx)
        Store.saveTx(e.copy(amount = 10.0))
        assertEquals(90.0, bal(usd), 1e-9)
        Store.deleteTx(e.id)
        assertEquals(100.0, bal(usd), 1e-9)
        assertTrue(Store.data.tombstones.any { it.id == e.id })
    }

    @Test fun transferAcrossCurrenciesWithFee() {
        Store.saveTx(tx(TxType.TRANSFER, 10.0, usd, ves, fee = 0.5)) // 10 $ → 400 Bs, comisión 0,5 $
        assertEquals(89.5, bal(usd), 1e-9)
        assertEquals(4400.0, bal(ves), 1e-9)
    }

    @Test fun accountWithMovementsCannotBeDeleted() {
        Store.saveTx(tx(TxType.EXPENSE, 1.0, usd))
        assertFalse(Store.deleteAccount(usd.id))
    }

    @Test fun mergeKeepsNewestAndHonorsTombstones() {
        val a = tx(TxType.EXPENSE, 5.0, usd).also(Store::saveTx)
        val remoteEdit = Store.data.transactions.first().copy(note = "remoto", updatedAt = "2999-01-01T00:00:00Z")
        val dead = tx(TxType.EXPENSE, 7.0, usd)
        Store.merge(AppData(transactions = listOf(remoteEdit, dead), tombstones = listOf(Tombstone(dead.id, nowIso()))))
        assertEquals("remoto", Store.data.transactions.first { it.id == a.id }.note)
        assertFalse(Store.data.transactions.any { it.id == dead.id })
    }

    @Test fun backupRoundTripKeepsUnknownWebFields() {
        val t = tx(TxType.EXPENSE, 3.0, usd).copy(extra = org.json.JSONObject().put("fiscalTag", "NEUTRAL"))
        Store.saveTx(t)
        val back = Store.decode(Store.encode(Store.data))
        assertEquals("NEUTRAL", back.transactions.first { it.id == t.id }.extra.getString("fiscalTag"))
        assertEquals(Store.data.accounts.size, back.accounts.size)
    }

    @Test fun scheduledPostAdvancesDate() {
        val s = ScheduledPayment(name = "Alquiler", amount = 20.0, currency = Currency.USD, date = "2026-11-01", frequency = Frequency.MONTHLY, accountId = usd.id)
        Store.saveScheduled(s)
        assertTrue(Store.postScheduled(s))
        assertEquals(80.0, bal(usd), 1e-9)
        assertEquals("2026-12-01", Store.data.scheduled.first().date)
    }

    /** Respaldo completo como lo arma la web (useGoogleDriveSync localData): todas las secciones deben entrar. */
    @Test fun webFullBackupImportsEverySection() {
        val web = org.json.JSONObject("""{
          "userId":"Ana","lastBackup":"2026-11-01T10:00:00.000Z","exchangeRate":40,
          "userProfile":{"id":"p1","name":"Ana","language":"es","rateType":"OFFICIAL"},
          "accounts":[{"id":"a1","name":"Zelle","currency":"USD","balance":120.5,"icon":"wallet"}],
          "transactions":[{"id":"t1","amount":10,"originalCurrency":"USD","exchangeRate":40,"normalizedAmountUSD":10,"type":"EXPENSE",
             "category":"food","accountId":"a1","note":"","date":"2026-11-01T12:00:00.000Z","receipt":"data:image/jpeg;base64,AAAA","fiscalTag":"NEUTRAL"}],
          "scheduledPayments":[{"id":"s1","name":"Alquiler","amount":"300","currency":"USD","date":"2026-11-05","frequency":"Monthly","type":"EXPENSE","category":"home","accountId":"a1","autoPost":false}],
          "budgets":[{"categoryId":"food","limit":200,"month":"2026-11"}],
          "goals":[{"id":"g1","name":"Moto","targetAmount":1500,"savedAmount":300,"deadline":"2027-06-01","icon":"flag","color":"#00897B","contributions":[]}],
          "shoppingItems":[],"shoppingLists":[{"id":"l1","name":"Mercado","items":[{"id":"i1","name":"Arroz","quantity":"2","completed":false}],"createdAt":"2026-11-01"}],
          "syncQueue":[]
        }""")
        val d = Store.decode(web)
        assertEquals(1, d.accounts.size); assertEquals(1, d.transactions.size); assertEquals(1, d.scheduled.size)
        assertEquals(1, d.budgets.size); assertEquals(1, d.goals.size); assertEquals(1, d.lists.size)
        assertEquals(300.0, d.scheduled.first().amount, 1e-9) // la web a veces guarda montos como texto
        assertEquals(Frequency.MONTHLY, d.scheduled.first().frequency)
        // el recibo y campos que el MVP no usa se conservan para devolverlos a la web
        assertTrue(Store.encode(d).getJSONArray("transactions").getJSONObject(0).has("receipt"))
        assertTrue(Store.encode(d).getJSONArray("goals").getJSONObject(0).has("contributions"))
    }
}
