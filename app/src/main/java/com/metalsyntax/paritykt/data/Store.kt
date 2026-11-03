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

/** Reemplaza en su lugar (conserva el orden) o agrega al final. */
fun <T> List<T>.upsert(x: T, id: (T) -> Any): List<T> =
    if (any { id(it) == id(x) }) map { if (id(it) == id(x)) x else it } else this + x

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
    /** Sobres del mes: los específicos de [m] más los generales que ese mes no reemplaza; sin los ocultos (limit < 0). */
    fun activeBudgets(m: YearMonth = YearMonth.now()): List<Budget> {
        val specific = budgets.filter { it.month == m.toString() }
        return (specific + budgets.filter { it.month == null && specific.none { s -> s.categoryId == it.categoryId } }).filter { it.limit >= 0 }
    }
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

    val ready get() = ::file.isInitialized

    fun init(ctx: Context, changed: () -> Unit) {
        file = File(ctx.filesDir, "parity.json")
        Vault.init(ctx.filesDir)
        onChange = changed
        load()
    }

    /** Lee parity.json (cifrado o en claro). Un archivo ilegible se aparta, nunca se sobrescribe. */
    internal fun load() {
        val bytes = runCatching { file.readBytes() }.getOrNull()
        val read = bytes?.let { b -> runCatching { decode(JSONObject(String(Vault.open(b)))) } }
        data = read?.getOrNull() ?: AppData()
        // nunca pisar un archivo que no se pudo leer: se aparta intacto para recuperarlo
        if (read?.isFailure == true) file.renameTo(File(file.parentFile, "parity.unreadable.${System.currentTimeMillis()}.json"))
        if (bytes != null && Vault.enabled && !Vault.sealed(bytes) && data != AppData()) commit(data) // migrar el archivo en claro a cifrado
        postDueScheduled()
    }

    /** Para pruebas: archivo en [dir], sin Context. */
    internal fun initForTest(dir: File) { file = File(dir, "parity.json"); onChange = null; data = AppData(); syncWrites = true; Vault.enabled = false }

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
            tmp.writeBytes(Vault.seal(encode(latest).toString().toByteArray()))
            tmp.renameTo(file)
        }
        if (syncWrites) job.run() else writer.execute(job)
        onChange?.invoke()
    }

    // ------------------------------------------------------------------------------------------ cuentas

    fun saveAccount(a: Account) = commit(data.copy(accounts = data.accounts.upsert(a.copy(updatedAt = nowIso())) { it.id }))
    /** Nuevo orden de las billeteras (arrastrar en Billetera). */
    fun reorderAccounts(order: List<Account>) = commit(data.copy(accounts = order))

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
        // tasas del momento guardadas en la transacción (exchangeRate y euroRate de la web); al editar se conservan las originales
        val rate = old?.exchangeRate?.takeIf { it > 0 } ?: data.rates.snapshot()
        val eur = old?.extra?.optDouble("euroRate", 0.0)?.takeIf { it > 0 } ?: data.rates.eur
        val fixed = t.copy(updatedAt = nowIso(), normalizedAmountUSD = usd(t.amount, t.originalCurrency), exchangeRate = rate,
            extra = org.json.JSONObject(t.extra.toString()).put("euroRate", eur))
        accounts = apply(accounts, fixed, 1.0)
        commit(data.copy(accounts = accounts, transactions = data.transactions.upsert(fixed) { it.id }))
    }

    fun deleteTx(id: String) {
        val t = data.transactions.firstOrNull { it.id == id } ?: return
        commit(data.copy(accounts = apply(data.accounts, t, -1.0), transactions = data.transactions.filter { it.id != id },
            tombstones = (data.tombstones + Tombstone(id, nowIso())).takeLast(2000)))
    }

    /** Aplica (sign = 1) o revierte (sign = −1) el efecto de [t] en los saldos. */
    /**
     * Efecto de una transacción en los saldos (applyTransactionDelta de la web): la de origen descuenta el monto + la comisión
     * (gasto y transferencia), convertido de la moneda de la transacción a la de la cuenta con la tasa guardada en la
     * transacción; la de destino de una transferencia recibe el monto convertido.
     */
    private fun apply(accounts: List<Account>, t: Transaction, sign: Double): List<Account> = accounts.map { a ->
        val fee = if (t.type != TxType.INCOME) t.fee ?: 0.0 else 0.0
        val delta = when {
            a.id == t.accountId && t.type == TxType.INCOME -> convertAt(t.amount, t.originalCurrency, a.currency, t)
            a.id == t.accountId -> -convertAt(t.amount + fee, t.originalCurrency, a.currency, t)
            a.id == t.toAccountId && t.type == TxType.TRANSFER -> convertAt(t.amount, t.originalCurrency, a.currency, t)
            else -> 0.0
        }
        if (delta == 0.0) a else a.copy(balance = a.balance + sign * delta, updatedAt = nowIso())
    }

    /** Conversión con las tasas de la transacción (exchangeRate = Bs por $, euroRate = Bs por €), para que editar o borrar revierta exacto. */
    private fun convertAt(amount: Double, from: Currency, to: Currency, t: Transaction): Double {
        if (from == to || (from.usdLike && to.usdLike)) return amount
        val usd = t.exchangeRate.takeIf { it > 0 } ?: data.rates.usd
        val eur = t.extra.optDouble("euroRate", 0.0).takeIf { it > 0 } ?: data.rates.eur.takeIf { it > 0 } ?: usd
        val ves = when (from) { Currency.VES -> amount; Currency.EUR -> amount * eur; else -> amount * usd }
        return when (to) { Currency.VES -> ves; Currency.EUR -> ves / eur; else -> ves / usd }
    }


    // ------------------------------------------------------------------------------------------ planificado

    fun saveScheduled(s: ScheduledPayment) = commit(data.copy(scheduled = data.scheduled.upsert(s.copy(updatedAt = nowIso())) { it.id }))
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

    /** Un presupuesto es único por categoría + mes (month = null: general, vale para todos los meses), como la web. */
    fun saveBudget(b: Budget) = commit(data.copy(budgets = data.budgets.upsert(b.copy(updatedAt = nowIso())) { it.key }))
    fun deleteBudget(b: Budget) = commit(data.copy(budgets = data.budgets.filter { it.key != b.key }))

    /**
     * Quitar un sobre del mes [m] (BudgetView.tsx): el específico del mes se borra; uno general se oculta en ese mes
     * con limit = -1, sin tocar los demás meses.
     */
    fun removeBudget(b: Budget, m: YearMonth) =
        if (b.month == m.toString()) deleteBudget(b) else saveBudget(Budget(b.categoryId, -1.0, m.toString()))

    /** Copia los sobres del mes anterior al mes [m]. */
    fun copyBudgets(from: YearMonth, m: YearMonth) = commit(data.copy(budgets = data.activeBudgets(from)
        .map { it.copy(month = m.toString(), updatedAt = nowIso()) }.fold(data.budgets) { acc, b -> acc.upsert(b) { it.key } }))
    fun saveGoal(g: Goal) = commit(data.copy(goals = data.goals.upsert(g.copy(updatedAt = nowIso(), completed = g.savedAmount >= g.targetAmount)) { it.id }))
    fun deleteGoal(id: String) = commit(data.copy(goals = data.goals.filter { it.id != id }))

    // ------------------------------------------------------------------------------------------ compras

    fun saveList(l: ShoppingList) = commit(data.copy(lists = data.lists.upsert(l.copy(updatedAt = nowIso())) { it.id }))
    fun deleteList(id: String) = commit(data.copy(lists = data.lists.filter { it.id != id }))

    // ------------------------------------------------------------------------------------------ perfil y tasas

    fun saveProfile(p: UserProfile) = commit(data.copy(profile = p.copy(updatedAt = nowIso())))
    fun setRates(r: Rates) = commit(data.copy(rates = r))

    /**
     * Tasas de ve.dolarapi.com (la fuente de la web): oficial BCV y paralela (modo Dev), dólar y euro.
     * La tasa en uso ([Rates.usd]/[Rates.eur]) es la oficial, o la paralela si el usuario la eligió. La manual no se pisa.
     * Bloqueante: llamar fuera del hilo principal.
     */
    fun refreshRates(): Rates {
        if (data.rates.manual) return data.rates
        fun get(url: String): Map<String, Double> {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            val a = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            return (0 until a.length()).map { a.getJSONObject(it) }.associate { it.optString("fuente") to it.optDouble("promedio", 0.0) }
        }
        val usd = get("https://ve.dolarapi.com/v1/dolares")
        val eur = runCatching { get("https://ve.dolarapi.com/v1/euros") }.getOrDefault(emptyMap())
        val par = data.rates.parallel
        val r = data.rates.copy(bcvUsd = usd["oficial"] ?: 0.0, bcvEur = eur["oficial"] ?: 0.0, usdPar = usd["paralelo"] ?: 0.0,
            eurPar = eur["paralelo"] ?: 0.0, updatedAt = nowIso()).let { it.copy(usd = if (par && it.usdPar > 0) it.usdPar else it.bcvUsd,
            eur = if (par && it.eurPar > 0) it.eurPar else it.bcvEur) }
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
        .put("rates", JSONObject().put("usd", d.rates.usd).put("eur", d.rates.eur).put("updatedAt", d.rates.updatedAt ?: "").put("manual", d.rates.manual)
            .put("bcvUsd", d.rates.bcvUsd).put("bcvEur", d.rates.bcvEur).put("usdPar", d.rates.usdPar).put("eurPar", d.rates.eurPar).put("parallel", d.rates.parallel))

    fun decode(o: JSONObject) = AppData(
        Json.profile(o.optJSONObject("userProfile")),
        Json.arr(o, "accounts", Json::account), Json.arr(o, "transactions", Json::tx), Json.arr(o, "scheduledPayments", Json::scheduled),
        Json.arr(o, "budgets", Json::budget), Json.arr(o, "goals", Json::goal),
        // respaldos viejos de la web: productos sueltos («shoppingItems») sin listas → una lista
        Json.arr(o, "shoppingLists", Json::list).ifEmpty {
            o.optJSONArray("shoppingItems")?.takeIf { it.length() > 0 }?.let { listOf(Json.list(JSONObject().put("id", "legacy-items").put("name", "Lista de compras").put("items", it))) }.orEmpty()
        },
        Json.arr(o, "deletedTransactions") { Tombstone(it.getString("id"), it.optString("deletedAt")) },
        o.optJSONObject("rates")?.let { Rates(it.optDouble("usd", 0.0), it.optDouble("eur", 0.0), it.optString("updatedAt").ifEmpty { null }, it.optBoolean("manual"),
            it.optDouble("bcvUsd", it.optDouble("usd", 0.0)), it.optDouble("bcvEur", it.optDouble("eur", 0.0)), it.optDouble("usdPar", 0.0),
            it.optDouble("eurPar", 0.0), it.optBoolean("parallel")) } ?: Rates(),
    )
}
