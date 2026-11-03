package com.metalsyntax.paritykt.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Modelo del MVP: subconjunto de packages/core/src/types.ts de la Parity web, con los MISMOS nombres de campo,
 * para que los respaldos (parity_backup_*.json) y la sincronización mínima (parity_sync_*.json) sirvan en las dos.
 * Los campos que el MVP no usa se conservan tal cual en [extra] y se vuelven a escribir.
 */
enum class Currency { USD, VES, EUR, USDT;
    val usdLike get() = this == USD || this == USDT
}
enum class TxType { EXPENSE, INCOME, TRANSFER }
enum class Frequency(val json: String, private val key: String) {
    MONTHLY("Monthly", "monthly"), BIWEEKLY("Bi-weekly", "biweekly"), WEEKLY("Weekly", "weekly"),
    YEARLY("Yearly", "yearly"), ONCE("One-Time", "oneTime");

    val label get() = t(key)

    companion object { fun of(s: String?) = entries.firstOrNull { it.json == s } ?: MONTHLY }
}

fun newId(): String = UUID.randomUUID().toString()
fun nowIso(): String = Instant.now().toString()

data class Account(
    val id: String = newId(), val name: String, val currency: Currency, val balance: Double,
    val icon: String = "wallet", val color: String? = null, val updatedAt: String = nowIso(), val extra: JSONObject = JSONObject(),
    val payrollClient: String? = null, // «Cliente de nómina» de la web
)

data class Transaction(
    val id: String = newId(), val amount: Double, val originalCurrency: Currency, val exchangeRate: Double,
    val normalizedAmountUSD: Double, val type: TxType, val category: String, val accountId: String,
    val toAccountId: String? = null, val note: String = "", val date: String, val fee: Double? = null,
    val scheduledId: String? = null, val updatedAt: String = nowIso(), val extra: JSONObject = JSONObject(),
)

data class ScheduledPayment(
    val id: String = newId(), val name: String, val amount: Double, val currency: Currency, val date: String, // próxima fecha YYYY-MM-DD
    val frequency: Frequency, val type: TxType = TxType.EXPENSE, val category: String = "bills", val accountId: String? = null,
    val autoPost: Boolean = false, val updatedAt: String = nowIso(), val extra: JSONObject = JSONObject(),
)

data class Budget(val categoryId: String, val limit: Double, val month: String? = null, val updatedAt: String = nowIso(), val extra: JSONObject = JSONObject()) {
    val key get() = categoryId + "|" + (month ?: "")
}

data class Goal(
    val id: String = newId(), val name: String, val targetAmount: Double, val savedAmount: Double = 0.0, val deadline: String,
    val icon: String = "flag", val color: String = "#00897B", val completed: Boolean = false, val updatedAt: String = nowIso(),
    val extra: JSONObject = JSONObject(),
)

data class ShoppingItem(
    val id: String = newId(), val name: String, val quantity: String = "1", val completed: Boolean = false,
    val price: Double? = null, val currency: Currency? = null,
)

data class ShoppingList(
    val id: String = newId(), val name: String, val items: List<ShoppingItem> = emptyList(), val createdAt: String = nowIso(),
    val updatedAt: String = nowIso(), val extra: JSONObject = JSONObject(),
)

data class UserProfile(val id: String = newId(), val name: String = "", val language: String = "es", val updatedAt: String = nowIso(), val extra: JSONObject = JSONObject())

/** Tasas en bolívares: VES por 1 USD (oficial BCV) y VES por 1 EUR. USDT se toma 1:1 con USD. */
/** [usd]/[eur] = tasa en uso; [bcvUsd]/[bcvEur] oficial; [usdPar]/[eurPar] paralela (Dev); [parallel] = usar la paralela. */
data class Rates(val usd: Double = 0.0, val eur: Double = 0.0, val updatedAt: String? = null, val manual: Boolean = false,
                 val bcvUsd: Double = usd, val bcvEur: Double = eur, val usdPar: Double = 0.0, val eurPar: Double = 0.0, val parallel: Boolean = false) {
    fun toUsd(amount: Double, c: Currency): Double = when (c) {
        Currency.USD, Currency.USDT -> amount
        Currency.VES -> if (usd > 0) amount / usd else 0.0
        Currency.EUR -> if (usd > 0 && eur > 0) amount * eur / usd else amount
    }
    /** Tasa que se guarda en la transacción (VES por USD, como la web). */
    fun snapshot() = usd
}

/** Categoría de la web (apps/web/constants.tsx): nombre = clave i18n, color = el text-*-400 de Tailwind de la web. */
data class Category(val id: String, val key: String, val icon: String, val income: Boolean = false, val color: Long = 0xFF9CA3AF) {
    val name get() = t(key)
}

private fun c(id: String, icon: String, color: Long, income: Boolean = false, key: String = "cat_$id") = Category(id, key, icon, income, color)

val CATEGORIES = listOf(
    c("food", "food", 0xFFFB923C), c("coffee", "coffee", 0xFFFCA5A5, key = "cat_fastfood"), c("transport", "car", 0xFF60A5FA),
    c("home", "home", 0xFF818CF8), c("bills", "bolt", 0xFFFACC15), c("internet", "wifi", 0xFF38BDF8),
    c("health", "health", 0xFFF87171), c("shopping", "bag", 0xFFF472B6), c("entertainment", "movie", 0xFFC084FC),
    c("education", "school", 0xFF34D399), c("subscription", "tv", 0xFFA78BFA), c("loans", "card", 0xFF10B981),
    c("insurance", "shield", 0xFF818CF8), c("pets", "pets", 0xFFFBBF24), c("kids", "kids", 0xFF93C5FD),
    c("travel", "plane", 0xFF38BDF8), c("vacation", "beach", 0xFF22D3EE), c("electronics", "phone", 0xFFD4D4D8),
    c("gift", "gift", 0xFFFB7185), c("personal", "spa", 0xFF2DD4BF), c("services", "build", 0xFF94A3B8),
    c("taxes", "receipt", 0xFF9CA3AF), c("commission", "percent", 0xFFFCA5A5), c("charity", "health", 0xFFFDA4AF),
    c("savings", "savings", 0xFF34D399), c("other", "more", 0xFFD1D5DB),
    c("income", "money", 0xFF34D399, true), c("work", "work", 0xFFCBD5E1, true), c("freelance", "laptop", 0xFFA5B4FC, true),
    c("business", "store", 0xFF60A5FA, true), c("interest", "trending", 0xFF818CF8, true), c("investment", "trending", 0xFF4ADE80, true),
    c("transfer", "swap", 0xFF818CF8),
)

fun category(id: String) = CATEGORIES.firstOrNull { it.id == id } ?: Category(id, id.replaceFirstChar { it.uppercase() }, "more")  // clave desconocida: t() devuelve el id

// ------------------------------------------------------------------------------------------------ JSON

private fun JSONObject.str(k: String) = optString(k, "").takeIf { has(k) && !isNull(k) }
private fun JSONObject.dbl(k: String) = if (has(k) && !isNull(k)) optDouble(k) else null
private fun cur(s: String?) = Currency.entries.firstOrNull { it.name == s } ?: Currency.USD
private fun txType(s: String?) = TxType.entries.firstOrNull { it.name == s } ?: TxType.EXPENSE

/** Copia [o] sin las claves que maneja el MVP (lo demás se conserva para no perder datos de la web). */
// copia superficial de claves (sin pasar por texto: los recibos en base64 de la web pesan MB)
private fun rest(o: JSONObject, vararg known: String) = JSONObject().also { r -> o.keys().forEach { k -> if (k !in known) r.put(k, o.get(k)) } }
private fun base(extra: JSONObject) = JSONObject().also { r -> extra.keys().forEach { k -> r.put(k, extra.get(k)) } }

object Json {
    fun account(o: JSONObject) = Account(o.getString("id"), o.optString("name"), cur(o.str("currency")), o.optDouble("balance", 0.0),
        o.optString("icon", "wallet"), o.str("color"), o.str("updatedAt") ?: nowIso(), rest(o, "id", "name", "currency", "balance", "icon", "color", "updatedAt", "payrollClient"),
        o.str("payrollClient")?.takeIf { it.isNotBlank() })

    fun account(a: Account) = base(a.extra).put("id", a.id).put("name", a.name).put("currency", a.currency.name).put("balance", a.balance)
        .put("icon", a.icon).apply { a.color?.let { put("color", it) }; a.payrollClient?.let { put("payrollClient", it) } }.put("updatedAt", a.updatedAt)

    private val TX_KEYS = arrayOf("id", "amount", "originalCurrency", "exchangeRate", "normalizedAmountUSD", "type", "category", "accountId",
        "toAccountId", "note", "date", "fee", "scheduledId", "updatedAt")

    fun tx(o: JSONObject) = Transaction(o.getString("id"), o.optDouble("amount", 0.0), cur(o.str("originalCurrency")), o.optDouble("exchangeRate", 0.0),
        o.optDouble("normalizedAmountUSD", 0.0), txType(o.str("type")), o.optString("category", "other"), o.optString("accountId"), o.str("toAccountId"),
        o.optString("note", ""), o.optString("date"), o.dbl("fee"), o.str("scheduledId"), o.str("updatedAt") ?: o.optString("date"), rest(o, *TX_KEYS))

    fun tx(t: Transaction) = base(t.extra).put("id", t.id).put("amount", t.amount).put("originalCurrency", t.originalCurrency.name)
        .put("exchangeRate", t.exchangeRate).put("normalizedAmountUSD", t.normalizedAmountUSD).put("type", t.type.name).put("category", t.category)
        .put("accountId", t.accountId).put("note", t.note).put("date", t.date).put("updatedAt", t.updatedAt).apply {
            t.toAccountId?.let { put("toAccountId", it) }; t.fee?.let { put("fee", it) }; t.scheduledId?.let { put("scheduledId", it) }
        }

    fun scheduled(o: JSONObject) = ScheduledPayment(o.getString("id"), o.optString("name"), o.optDouble("amount", 0.0), cur(o.str("currency")),
        o.optString("date").take(10), Frequency.of(o.str("frequency")), txType(o.str("type")), o.optString("category", "bills"), o.str("accountId"),
        o.optBoolean("autoPost"), o.str("updatedAt") ?: nowIso(),
        rest(o, "id", "name", "amount", "currency", "date", "frequency", "type", "category", "accountId", "autoPost", "updatedAt"))

    fun scheduled(s: ScheduledPayment) = base(s.extra).put("id", s.id).put("name", s.name).put("amount", s.amount).put("currency", s.currency.name)
        .put("date", s.date).put("frequency", s.frequency.json).put("type", s.type.name).put("category", s.category)
        .put("autoPost", s.autoPost).put("updatedAt", s.updatedAt).apply { s.accountId?.let { put("accountId", it) } }

    fun budget(o: JSONObject) = Budget(o.getString("categoryId"), o.optDouble("limit", 0.0), o.str("month"), o.str("updatedAt") ?: nowIso(),
        rest(o, "categoryId", "limit", "month", "updatedAt"))

    fun budget(b: Budget) = base(b.extra).put("categoryId", b.categoryId).put("limit", b.limit).put("updatedAt", b.updatedAt).apply { b.month?.let { put("month", it) } }

    fun goal(o: JSONObject) = Goal(o.getString("id"), o.optString("name"), o.optDouble("targetAmount", 0.0), o.optDouble("savedAmount", 0.0),
        o.optString("deadline"), o.optString("icon", "flag"), o.optString("color", "#00897B"), o.optBoolean("completed"), o.str("updatedAt") ?: nowIso(),
        rest(o, "id", "name", "targetAmount", "savedAmount", "deadline", "icon", "color", "completed", "updatedAt"))

    fun goal(g: Goal) = base(g.extra).put("id", g.id).put("name", g.name).put("targetAmount", g.targetAmount).put("savedAmount", g.savedAmount)
        .put("deadline", g.deadline).put("icon", g.icon).put("color", g.color).put("completed", g.completed).put("updatedAt", g.updatedAt)

    private fun item(o: JSONObject) = ShoppingItem(o.getString("id"), o.optString("name"), o.optString("quantity", "1"), o.optBoolean("completed"),
        o.dbl("price"), o.str("currency")?.let(::cur))

    private fun item(i: ShoppingItem) = JSONObject().put("id", i.id).put("name", i.name).put("quantity", i.quantity).put("completed", i.completed)
        .apply { i.price?.let { put("price", it) }; i.currency?.let { put("currency", it.name) } }

    fun list(o: JSONObject) = ShoppingList(o.getString("id"), o.optString("name"),
        o.optJSONArray("items")?.let { a -> (0 until a.length()).map { item(a.getJSONObject(it)) } }.orEmpty(),
        o.optString("createdAt", nowIso()), o.str("updatedAt") ?: nowIso(), rest(o, "id", "name", "items", "createdAt", "updatedAt"))

    fun list(l: ShoppingList) = base(l.extra).put("id", l.id).put("name", l.name).put("items", JSONArray(l.items.map(::item)))
        .put("createdAt", l.createdAt).put("updatedAt", l.updatedAt)

    fun profile(o: JSONObject?) = o?.let { UserProfile(it.optString("id", newId()), it.optString("name"), it.optString("language", "es"),
        it.str("updatedAt") ?: nowIso(), rest(it, "id", "name", "language", "updatedAt")) } ?: UserProfile()

    fun profile(p: UserProfile) = base(p.extra).put("id", p.id).put("name", p.name).put("language", p.language).put("updatedAt", p.updatedAt)

    fun <T> arr(o: JSONObject, k: String, f: (JSONObject) -> T): List<T> =
        o.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { runCatching { f(a.getJSONObject(it)) }.getOrNull() } }.orEmpty()
}
