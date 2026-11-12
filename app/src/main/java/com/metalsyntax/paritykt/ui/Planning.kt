package com.metalsyntax.paritykt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.metalsyntax.paritykt.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ================================================================================================ Planificado

/** Fila de planificado (ScheduledPaymentView de la web): ícono con + verde para registrarlo, fecha · frecuencia, monto y ≈ Bs. */
@Composable
fun ScheduledRow(s: ScheduledPayment, nav: Nav, onEdit: (() -> Unit)? = null) {
    val d = Store.data
    val c = category(s.category)
    val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(s.date)) }.getOrDefault(0)
    val due = when { days < 0 -> t("overdue"); days == 0L -> t("today"); days == 1L -> t("kt_tomorrow"); else -> t("kt_in_days", days) }
    val badge = when { days < 0 -> Expense; days <= 7 -> Color(0xFFFB923C); else -> Muted }
    val income = s.type == TxType.INCOME
    val usd = Store.usd(s.amount, s.currency)
    ListRow(s.name, subtitle = "${shortDate(s.date).uppercase(ES)} • ${s.frequency.label.uppercase(ES)}" + if (s.autoPost) " • ${t("kt_automatic").uppercase(ES)}" else "",
        leading = { Bubble(icon(c.icon), if (income) Income else Expense, badge = {
            if (Store.postScheduled(s, s.accountId ?: d.accounts.firstOrNull()?.id)) nav.snack(t("kt_posted", s.name)) else nav.snack(t("kt_first_wallet"))
        }) },
        pill = { Pill(due, badge) }, trailing = money(s.amount, s.currency), trailingColor = if (income) Income else Ink,
        trailingSub = if (s.currency == Currency.VES) "≈ ${money(usd)}" else "≈ ${money(inDisplay(usd, Currency.VES), Currency.VES)}",
        onClick = onEdit ?: { nav.go(Route.PLAN) })
}

@Composable
fun ScheduledScreen(nav: Nav) {
    val d = Store.data
    var edit by remember { mutableStateOf<ScheduledPayment?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = d.scheduled.sortedBy { it.date }
    val monthExp = list.filter { it.date.startsWith(YearMonth.now().toString()) && it.type == TxType.EXPENSE }.sumOf { Store.usd(it.amount, it.currency) }
    val monthInc = list.filter { it.date.startsWith(YearMonth.now().toString()) && it.type == TxType.INCOME }.sumOf { Store.usd(it.amount, it.currency) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            Box(Modifier.offset(x = (-16).dp).fillParentMaxWidth()) {
                ViewHeader(t("scheduled"), t("manageSubscriptions"), nav.back) { DisplayCurrencyButton(); AddButton(t("kt_plan")) { creating = true } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(t("kt_due_this_month"), shown(monthExp), Modifier.weight(1f), valueColor = Expense)
                StatCard(t("scheduledIncome"), shown(monthInc), Modifier.weight(1f), valueColor = Income)
            }
        }
        group(t("kt_scheduled_income"), Icons.Outlined.TrendingUp, Income, list.filter { it.type == TxType.INCOME }, t("kt_no_scheduled_income")) { s ->
            ScheduledRow(s, nav) { edit = s }
        }
        group(t("kt_scheduled_expenses"), Icons.Outlined.TrendingDown, Expense, list.filter { it.type == TxType.EXPENSE }, t("kt_no_scheduled_expenses")) { s ->
            ScheduledRow(s, nav) { edit = s }
        }
    }
    if (creating || edit != null) ScheduledEditor(edit) { edit = null; creating = false }
}

/** Sección de lista con rótulo e ícono, o aviso si está vacía. */
private fun <T : Any> LazyListScope.group(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, rows: List<T>, empty: String,
                                          row: @Composable (T) -> Unit) {
    item { Section(title, icon = icon, iconTint = tint); if (rows.isEmpty()) EmptyNote(empty) }
    items(rows) { row(it) }
}

@Composable
private fun ScheduledEditor(initial: ScheduledPayment?, onDone: () -> Unit) {
    val d = Store.data
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(initial?.amount?.let(::num) ?: "") }
    var accountId by remember { mutableStateOf(initial?.accountId ?: d.accounts.firstOrNull()?.id) }
    var currency by remember { mutableStateOf(initial?.currency ?: Currency.USD) }
    var freq by remember { mutableStateOf(initial?.frequency ?: Frequency.MONTHLY) }
    var cat by remember { mutableStateOf(initial?.category ?: "bills") }
    var date by remember { mutableStateOf(initial?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()) }
    var auto by remember { mutableStateOf(initial?.autoPost ?: false) }
    val value = parseAmount(amount)
    val acc = d.account(accountId)
    FormSheet(if (initial == null) t("kt_plan") else t("kt_edit_planned"), onDone, saveEnabled = name.isNotBlank() && value != null && value > 0, onSave = {
        Store.saveScheduled((initial ?: ScheduledPayment(name = "", amount = 0.0, currency = Currency.USD, date = "", frequency = freq)).copy(
            name = name.trim(), type = type, amount = value!!, currency = acc?.currency ?: currency, frequency = freq, category = cat,
            date = date.toString(), accountId = acc?.id, autoPost = auto && acc != null))
        onDone()
    }, onDelete = initial?.let { { Store.deleteScheduled(it.id); onDone() } }) {
        Segmented(listOf(TxType.EXPENSE to t("kt_payment"), TxType.INCOME to t("kt_income_one")), type,
            colorOf = { if (it == TxType.INCOME) Income else Expense }) { type = it; cat = if (it == TxType.INCOME) "income" else "bills" }
        Field(name, { name = it }, t("kt_planned_name_hint"))
        Field(amount, { amount = it }, t("amount"), number = true, suffix = symbol(acc?.currency ?: currency))
        if (d.accounts.isEmpty()) Picker(t("currency"), Currency.entries.map { it to it.name }, currency) { currency = it }
        else Picker(t("wallet"), d.accounts.map { it.id to "${it.name} (${it.currency})" }, accountId) { accountId = it }
        Picker(t("frequency"), Frequency.entries.map { it to it.label }, freq) { freq = it }
        CategoryPicker(t("category"), CATEGORIES.filter { it.id != "transfer" && it.income == (type == TxType.INCOME) }, cat) { cat = it }
        DateField(t("kt_next_date"), date) { date = it }
        SettingRow(t("kt_auto_post"), t("kt_auto_post_desc")) { Toggle(auto, enabled = acc != null) { auto = it } }
    }
}

// ================================================================================================ Presupuestos & Metas

/** Presupuestos & Metas de la web (BudgetView.tsx): pestañas Sobres / Metas. */
@Composable
fun BudgetGoalsScreen(nav: Nav, goals: Boolean) {
    val d = Store.data
    var tab by remember { mutableStateOf(goals) }
    var creating by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    // meses con sobres + los últimos 6 y el siguiente, como el menú de la web
    val months = remember(d.budgets) {
        (d.budgets.mapNotNull { b -> b.month?.let { runCatching { YearMonth.parse(it) }.getOrNull() } } +
            (-6..1).map { YearMonth.now().plusMonths(it.toLong()) }).distinct().sortedDescending()
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically) {
            DisplayCurrencyButton(); MonthPicker(month, months) { month = it }
        }
        ViewHeader(t("kt_budgets_goals"), t("kt_budgets_goals_sub"), nav.back)
        Box(Modifier.padding(horizontal = 16.dp)) {
            Segmented(listOf(false to t("envelopes"), true to t("goals")), tab, icons = { if (it) Icons.Outlined.EmojiEvents else Icons.Outlined.PieChart },
                container = Surface1) { tab = it }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (tab) t("goals") else t("envelopes"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Ink)
            BrandButton(if (tab) t("addGoal") else t("kt_new_envelope"), icon = Icons.Outlined.Add) { creating = true }
        }
        if (tab) Goals(creating) { creating = false } else Budgets(month, creating) { creating = false }
    }
}

@Composable
private fun Budgets(month: YearMonth, creating: Boolean, doneCreating: () -> Unit) {
    val d = Store.data
    var edit by remember { mutableStateOf<Budget?>(null) }
    val list = d.activeBudgets(month)
    val limit = list.sumOf { it.limit }
    val received = d.monthTx(month).filter { it.type == TxType.INCOME }.sumOf { it.normalizedAmountUSD }
    // lo que sobró del mes anterior también financia los sobres (saldo al empezar el mes)
    val carried = d.balanceAt(month.atDay(1)).coerceAtLeast(0.0)
    val income = carried + received
    val cycle = payCycle(d)
    val prev = month.minusMonths(1)
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        if (list.isEmpty()) item {
            Empty(Icons.Outlined.PieChart, t("kt_no_budget"), t("kt_no_budget_desc"), t("kt_create_budget")) { edit = Budget("", 0.0) }
            if (d.activeBudgets(prev).isNotEmpty()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GhostButton(t("kt_copy_prev_month"), Icons.Outlined.ContentCopy) { Store.copyBudgets(prev, month) }
            }
        }
        else item {
            // resumen: total en sobres frente al ingreso del mes
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) { Caps(t("kt_total_envelopes")); AutoText(shown(limit), MaterialTheme.typography.headlineMedium, Ink); Text(secondary(limit), style = MaterialTheme.typography.labelSmall, color = Muted) }
                        Column(horizontalAlignment = Alignment.End) { Caps(t("kt_month_money")); Text(shown(income), style = MaterialTheme.typography.headlineSmall, color = Income); Text(secondary(income), style = MaterialTheme.typography.labelSmall, color = Muted) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Bar(if (income > 0) (limit / income).toFloat() else 0f, Brand)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pct(limit, income), style = MaterialTheme.typography.labelLarge, color = Brand); Spacer(Modifier.width(8.dp))
                        Text(t("kt_of_income_here"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Muted)
                        Text("+ ${shown((income - limit).coerceAtLeast(0.0))} ${t("remaining")}", style = MaterialTheme.typography.labelMedium, color = Income)
                    }
                    // de dónde sale: lo que sobró + lo cobrado; y cuánto le toca a cada período de cobro
                    Text(t("kt_month_money_detail", shown(carried), shown(received)), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (cycle != PayCycle.MONTHLY && month == YearMonth.now()) {
                        val per = cycle.period(java.time.LocalDate.now())
                        Text(t("kt_envelopes_per_period", t(cycle.key), shown(limit * per.share)), Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = Brand)
                    }
                }
            }
            Section(t("activeEnvelopes"))
        }
        items(list.sortedByDescending { d.spentUsd(it.categoryId, month) / it.limit }, key = { it.key }) { b ->
            val s = d.spentUsd(b.categoryId, month)
            val c = category(b.categoryId)
            val f = if (b.limit > 0) s / b.limit else 0.0
            val (status, col) = when { f > 1 -> t("kt_exceeded") to Expense; f > 0.8 -> t("kt_near_limit") to Warning; else -> t("onTrack") to Income }
            ProgressCard(c.name, icon(c.icon), catColor(c), s, b.limit, status, col, shown(s), "${t("kt_of")} ${shown(b.limit)}", amountSub = secondary(s)) { edit = b }
        }
    }
    if (creating || edit != null) {
        val initial = edit?.takeIf { it.categoryId.isNotEmpty() }
        val firstFree = (CATEGORIES.firstOrNull { it.id !in list.map { b -> b.categoryId } && !it.income && it.id != "transfer" } ?: CATEGORIES.first()).id
        var cat by remember(initial) { mutableStateOf(initial?.categoryId ?: firstFree) }
        var amount by remember(initial) { mutableStateOf(initial?.limit?.let(::num) ?: "") }
        val v = parseAmount(amount)
        val close = { edit = null; doneCreating() }
        FormSheet(if (initial == null) t("kt_new_envelope") else t("kt_edit_limit"), close, saveEnabled = v != null && v > 0, onSave = {
            // al editar en un mes se guarda como sobre de ese mes (el general sigue igual para los demás)
            if (initial != null && initial.categoryId != cat) Store.removeBudget(initial, month)
            Store.saveBudget(Budget(cat, v!!, month.toString())); close()
        }, onDelete = initial?.let { { Store.removeBudget(it, month); close() } }) {
            CategoryPicker(t("category"), CATEGORIES.filter { !it.income && it.id != "transfer" }, cat) { cat = it }
            Field(amount, { amount = it }, t("monthlyLimit"), number = true, suffix = "USD")
        }
    }
}

@Composable
private fun Goals(creating: Boolean, doneCreating: () -> Unit) {
    val d = Store.data
    var edit by remember { mutableStateOf<Goal?>(null) }
    var contribute by remember { mutableStateOf<Goal?>(null) }
    val saved = d.goals.sumOf { it.savedAmount }; val target = d.goals.sumOf { it.targetAmount }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        if (d.goals.isEmpty()) item { Empty(Icons.Outlined.EmojiEvents, t("kt_no_goals"), t("kt_no_goals_desc"), t("addGoal")) { edit = Goal(name = "", targetAmount = 0.0, deadline = "") } }
        else item {
            StatCard(t("kt_saved_total"), shown(saved), Modifier.fillMaxWidth(), valueColor = GOAL, sub = "${t("kt_of")} ${shown(target)} · ${pct(saved, target)}") {
                Spacer(Modifier.height(12.dp)); Bar(if (target > 0) (saved / target).toFloat() else 0f, GOAL)
            }
            Spacer(Modifier.height(8.dp))
        }
        items(d.goals.sortedBy { it.completed }, key = { it.id }) { g ->
            ProgressCard(g.name, if (g.completed) Icons.Outlined.TaskAlt else Icons.Outlined.EmojiEvents, GOAL, g.savedAmount, g.targetAmount,
                if (g.completed) t("kt_achieved") else "${t("deadline")}: ${dayLabel(g.deadline)}", if (g.completed) Income else Muted,
                shown(g.savedAmount), "${t("kt_of")} ${shown(g.targetAmount)}", barColor = if (g.completed) Income else GOAL,
                footer = {
                    Text(if (g.completed) pct(g.savedAmount, g.targetAmount) else t("kt_missing", money((g.targetAmount - g.savedAmount).coerceAtLeast(0.0))),
                        Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Muted)
                    if (!g.completed) GhostButton(t("kt_contribute"), Icons.Outlined.Add, Brand, bg = Bg) { contribute = g }
                }) { edit = g }
        }
    }
    if (creating || edit != null) {
        val initial = edit?.takeIf { it.name.isNotEmpty() || it.targetAmount > 0 }
        var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
        var tgt by remember(initial) { mutableStateOf(initial?.targetAmount?.let(::num) ?: "") }
        var sv by remember(initial) { mutableStateOf(initial?.savedAmount?.let(::num) ?: "0") }
        var date by remember(initial) { mutableStateOf(initial?.deadline?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now().plusMonths(6)) }
        val tg = parseAmount(tgt)
        val close = { edit = null; doneCreating() }
        FormSheet(if (initial == null) t("addGoal") else t("kt_edit_goal"), close, saveEnabled = name.isNotBlank() && tg != null && tg > 0, onSave = {
            Store.saveGoal((initial ?: Goal(name = "", targetAmount = 0.0, deadline = "")).copy(name = name.trim(), targetAmount = tg!!,
                savedAmount = parseAmount(sv) ?: 0.0, deadline = date.toString())); close()
        }, onDelete = initial?.let { { Store.deleteGoal(it.id); close() } }) {
            Field(name, { name = it }, t("kt_goal_name_hint"))
            Field(tgt, { tgt = it }, t("kt_target"), number = true, suffix = "USD")
            Field(sv, { sv = it }, t("kt_saved_so_far"), number = true, suffix = "USD")
            DateField(t("deadline"), date) { date = it }
        }
    }
    contribute?.let { g ->
        var amount by remember(g) { mutableStateOf("") }
        val v = parseAmount(amount)
        Confirm(t("kt_contribute_to", g.name), "", t("kt_contribute"), enabled = v != null && v > 0, onDismiss = { contribute = null },
            content = { Field(amount, { amount = it }, t("amount"), number = true, suffix = "USD") }) {
            Store.saveGoal(g.copy(savedAmount = g.savedAmount + v!!)); contribute = null
        }
    }
}
