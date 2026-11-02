package com.metalsyntax.paritykt.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth

/** Lápida de una transacción borrada: la sincronización mínima la propaga (mismo campo que la web). */
data class Tombstone(val id: String, val deletedAt: String)

/** Todo el estado de la app (inmutable): Compose recompone cuando [Store.data] cambia. */
data class AppData(
    val profile: UserProfile = UserProfile(),
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val scheduled: List<ScheduledPayment> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val lists: List<ShoppingList> = emptyList(),
    val tombstones: List<Tombstone> = emptyList(),
    val rates: Rates = Rates(),
) {
    fun account(id: String?) = accounts.firstOrNull { it.id == id }
    fun totalUsd() = accounts.sumOf { rates.toUsd(it.balance, it.currency) }
    fun monthTx(m: YearMonth = YearMonth.now()) = transactions.filter { it.date.startsWith(m.toString()) }
    fun spentUsd(categoryId: String, m: YearMonth = YearMonth.now()) =
        monthTx(m).filter { it.type == TxType.EXPENSE && it.category == categoryId }.sumOf { it.normalizedAmountUSD }
}

/**
 * Datos en filesDir/parity.json (escritura atómica: tmp + rename). Las operaciones actualizan los saldos como la web:
 * gasto resta, ingreso suma, transferencia mueve de una cuenta a otra (+ comisión). Editar = revertir y volver a aplicar.
 */
object Store {
    var data by mutableStateOf(AppData())
        private set
    private lateinit var file: File
    private var onChange: (() -> Unit)? = null

    fun init(ctx: Context, changed: () -> Unit) {
        file = File(ctx.filesDir, "parity.json")
        onChange = changed
        data = runCatching { decode(JSONObject(file.readText())) }.getOrDefault(AppData())
        postDueScheduled()
    }

    /** Para pruebas: archivo en [dir], sin Context. */
    internal fun initForTest(dir: File) { file = File(dir, "parity.json"); onChange = null; data = AppData(); syncWrites = true }

    /** Un solo hilo de escritura: el estado cambia al instante y el archivo se escribe en orden, fuera del hilo principal. */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var pendingWrite: AppData? = null
    internal var syncWrites = false // pruebas

    private fun commit(d: AppData) {
        data = d
        pendingWrite = d
        val job = Runnable {
            val latest = pendingWrite ?: return@Runnable
            pendingWrite = null // ráfagas de cambios: se escribe solo el último estado
            val tmp = File(file.path + ".tmp")
            tmp.writeText(encode(latest).toString())
            tmp.renameTo(file)
        }
        if (syncWrites) job.run() else writer.execute(job)
        onChange?.invoke()
    }

    // ------------------------------------------------------------------------------------------ cuentas

    fun saveAccount(a: Account) = commit(data.copy(accounts = data.accounts.filter { it.id != a.id } + a.copy(updatedAt = nowIso())))

    /** No se borra una cuenta con movimientos (quedarían huérfanos). */
    fun deleteAccount(id: String): Boolean {
        if (data.transactions.any { it.accountId == id || it.toAccountId == id }) return false
        commit(data.copy(accounts = data.accounts.filter { it.id != id }))
        return true
    }

    // ------------------------------------------------------------------------------------------ transacciones

    fun usd(amount: Double, c: Currency) = data.rates.toUsd(amount, c)

    /** Nueva o editada (mismo id). La moneda es la de la cuenta de origen. */
    fun saveTx(t: Transaction) {
        val old = data.transactions.firstOrNull { it.id == t.id }
        var accounts = data.accounts
        if (old != null) accounts = apply(accounts, old, -1.0)
        val fixed = t.copy(updatedAt = nowIso(), normalizedAmountUSD = usd(t.amount, t.originalCurrency), exchangeRate = data.rates.snapshot())
        accounts = apply(accounts, fixed, 1.0)
        commit(data.copy(accounts = accounts, transactions = data.transactions.filter { it.id != t.id } + fixed))
    }

    fun deleteTx(id: String) {
        val t = data.transactions.firstOrNull { it.id == id } ?: return
        commit(data.copy(accounts = apply(data.accounts, t, -1.0), transactions = data.transactions.filter { it.id != id },
            tombstones = (data.tombstones + Tombstone(id, nowIso())).takeLast(2000)))
    }

    /** Aplica (sign = 1) o revierte (sign = −1) el efecto de [t] en los saldos. */
    private fun apply(accounts: List<Account>, t: Transaction, sign: Double): List<Account> = accounts.map { a ->
        val delta = when {
            a.id == t.accountId && t.type == TxType.EXPENSE -> -t.amount
            a.id == t.accountId && t.type == TxType.INCOME -> t.amount
            a.id == t.accountId && t.type == TxType.TRANSFER -> -(t.amount + (t.fee ?: 0.0))
            a.id == t.toAccountId && t.type == TxType.TRANSFER -> convert(t.amount, t.originalCurrency, a.currency)
            else -> 0.0
        }
        if (delta == 0.0) a else a.copy(balance = a.balance + sign * delta, updatedAt = nowIso())
    }

    /** Transferencia entre monedas distintas: se pasa por USD con la tasa actual. */
    private fun convert(amount: Double, from: Currency, to: Currency): Double {
        if (from == to) return amount
        val usd = data.rates.toUsd(amount, from)
        return when (to) {
            Currency.USD, Currency.USDT -> usd
            Currency.VES -> usd * data.rates.usd
            Currency.EUR -> if (data.rates.eur > 0) usd * data.rates.usd / data.rates.eur else usd
        }
    }

    // ------------------------------------------------------------------------------------------ planificado

    fun saveScheduled(s: ScheduledPayment) = commit(data.copy(scheduled = data.scheduled.filter { it.id != s.id } + s.copy(updatedAt = nowIso())))
    fun deleteScheduled(id: String) = commit(data.copy(scheduled = data.scheduled.filter { it.id != id }))

    /** Registra el pago como transacción y avanza a la próxima fecha (los de una vez se eliminan). */
    fun postScheduled(s: ScheduledPayment, accountId: String? = s.accountId): Boolean {
        val acc = data.account(accountId) ?: return false
        saveTx(Transaction(amount = s.amount, originalCurrency = acc.currency, exchangeRate = 0.0, normalizedAmountUSD = 0.0,
            type = s.type, category = s.category, accountId = acc.id, note = s.name, date = LocalDate.parse(s.date).atStartOfDay().toString(), scheduledId = s.id))
        if (s.frequency == Frequency.ONCE) deleteScheduled(s.id) else saveScheduled(s.copy(date = next(LocalDate.parse(s.date), s.frequency).toString()))
        return true
    }

    fun next(d: LocalDate, f: Frequency): LocalDate = when (f) {
        Frequency.WEEKLY -> d.plusWeeks(1)
        Frequency.BIWEEKLY -> d.plusWeeks(2)
        Frequency.YEARLY -> d.plusYears(1)
        else -> d.plusMonths(1)
    }

    /** Al abrir: los automáticos vencidos se registran solos (hasta ponerse al día). */
    private fun postDueScheduled() {
        val today = LocalDate.now()
        repeat(60) { // tope de seguridad
            val due = data.scheduled.firstOrNull { it.autoPost && it.accountId != null && runCatching { !LocalDate.parse(it.date).isAfter(today) }.getOrDefault(false) }
                ?: return
            if (!postScheduled(due)) return
        }
    }

    // ------------------------------------------------------------------------------------------ presupuesto y metas

    fun saveBudget(b: Budget) = commit(data.copy(budgets = data.budgets.filter { it.categoryId != b.categoryId } + b.copy(updatedAt = nowIso())))
    fun deleteBudget(categoryId: String) = commit(data.copy(budgets = data.budgets.filter { it.categoryId != categoryId }))
    fun saveGoal(g: Goal) = commit(data.copy(goals = data.goals.filter { it.id != g.id } + g.copy(updatedAt = nowIso(), completed = g.savedAmount >= g.targetAmount)))
    fun deleteGoal(id: String) = commit(data.copy(goals = data.goals.filter { it.id != id }))

    // ------------------------------------------------------------------------------------------ compras

    fun saveList(l: ShoppingList) = commit(data.copy(lists = data.lists.filter { it.id != l.id } + l.copy(updatedAt = nowIso())))
    fun deleteList(id: String) = commit(data.copy(lists = data.lists.filter { it.id != id }))

    // ------------------------------------------------------------------------------------------ perfil y tasas

    fun saveProfile(p: UserProfile) = commit(data.copy(profile = p.copy(updatedAt = nowIso())))
    fun setRates(r: Rates) = commit(data.copy(rates = r))

    /**
     * Tasa oficial del BCV (dólar y euro) desde ve.dolarapi.com, la misma fuente que usa la web; solo la oficial.
     * Bloqueante: llamar fuera del hilo principal. Si el usuario fijó una tasa manual, no se pisa.
     */
    fun refreshRates(): Rates {
        if (data.rates.manual) return data.rates
        fun oficial(url: String): Double {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            val a = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            return (0 until a.length()).map { a.getJSONObject(it) }.first { it.optString("fuente") == "oficial" }.getDouble("promedio")
        }
        val r = Rates(oficial("https://ve.dolarapi.com/v1/dolares"), runCatching { oficial("https://ve.dolarapi.com/v1/euros") }.getOrDefault(0.0), nowIso())
        commit(data.copy(rates = r))
        return r
    }

    // ------------------------------------------------------------------------------------------ respaldo

    /** Reemplaza todo (restaurar un respaldo completo). */
    fun replaceAll(d: AppData) = commit(d.copy(rates = if (d.rates.usd > 0) d.rates else data.rates))

    /** Mezcla (sincronización mínima): gana el updatedAt más nuevo; las lápidas borran. */
    fun merge(incoming: AppData) {
        fun <T> m(a: List<T>, b: List<T>, key: (T) -> String, ts: (T) -> String) =
            (a + b).groupBy(key).values.map { v -> v.maxBy(ts) }
        val dead = (data.tombstones + incoming.tombstones).map { it.id }.toSet()
        commit(data.copy(
            accounts = m(data.accounts, incoming.accounts, { it.id }, { it.updatedAt }),
            transactions = m(data.transactions, incoming.transactions, { it.id }, { it.updatedAt }).filter { it.id !in dead },
            scheduled = m(data.scheduled, incoming.scheduled, { it.id }, { it.updatedAt }),
            budgets = m(data.budgets, incoming.budgets, { it.categoryId + (it.month ?: "") }, { it.updatedAt }),
            goals = m(data.goals, incoming.goals, { it.id }, { it.updatedAt }),
            lists = m(data.lists, incoming.lists, { it.id }, { it.updatedAt }),
            tombstones = (data.tombstones + incoming.tombstones).distinctBy { it.id }.takeLast(2000),
            profile = if (incoming.profile.updatedAt > data.profile.updatedAt) incoming.profile else data.profile,
        ))
    }

    fun encode(d: AppData): JSONObject = JSONObject()
        .put("version", 3).put("date", nowIso())
        .put("userProfile", Json.profile(d.profile))
        .put("accounts", JSONArray(d.accounts.map(Json::account)))
        .put("transactions", JSONArray(d.transactions.map(Json::tx)))
        .put("scheduledPayments", JSONArray(d.scheduled.map(Json::scheduled)))
        .put("budgets", JSONArray(d.budgets.map(Json::budget)))
        .put("goals", JSONArray(d.goals.map(Json::goal)))
        .put("shoppingLists", JSONArray(d.lists.map(Json::list)))
        .put("deletedTransactions", JSONArray(d.tombstones.map { JSONObject().put("id", it.id).put("deletedAt", it.deletedAt) }))
        .put("rates", JSONObject().put("usd", d.rates.usd).put("eur", d.rates.eur).put("updatedAt", d.rates.updatedAt ?: "").put("manual", d.rates.manual))

    fun decode(o: JSONObject) = AppData(
        Json.profile(o.optJSONObject("userProfile")),
        Json.arr(o, "accounts", Json::account), Json.arr(o, "transactions", Json::tx), Json.arr(o, "scheduledPayments", Json::scheduled),
        Json.arr(o, "budgets", Json::budget), Json.arr(o, "goals", Json::goal), Json.arr(o, "shoppingLists", Json::list),
        Json.arr(o, "deletedTransactions") { Tombstone(it.getString("id"), it.optString("deletedAt")) },
        o.optJSONObject("rates")?.let { Rates(it.optDouble("usd", 0.0), it.optDouble("eur", 0.0), it.optString("updatedAt").ifEmpty { null }, it.optBoolean("manual")) } ?: Rates(),
    )
}
