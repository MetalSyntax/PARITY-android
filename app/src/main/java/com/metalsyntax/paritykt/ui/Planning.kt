package com.metalsyntax.paritykt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metalsyntax.paritykt.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ------------------------------------------------------------------------------------------------ Planificado

@Composable
fun ScheduledScreen(nav: Nav) {
    val d = Store.data
    var edit by remember { mutableStateOf<ScheduledPayment?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = d.scheduled.sortedBy { it.date }
    val monthTotal = list.filter { it.date.startsWith(YearMonth.now().toString()) && it.type == TxType.EXPENSE }.sumOf { Store.usd(it.amount, it.currency) }
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            ViewHeader(t("scheduled"), t("kt_scheduled_sub"), nav.back) {
                RoundIcon(Icons.Outlined.Add, t("kt_plan"), { creating = true }, tint = Color.White, bg = Brand)
            }
        }
        if (list.isEmpty()) item { Empty(Icons.Outlined.CalendarMonth, t("kt_nothing_planned"), t("kt_nothing_planned_desc"), t("add")) { creating = true } }
        else item {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Caps(t("kt_due_this_month"))
                    AutoText(shown(monthTotal), MaterialTheme.typography.headlineMedium, Expense)
                    Text("${list.size} · ${t("kt_auto_count", list.count { it.autoPost })}", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
            }
        }
        items(list, key = { it.id }) { s ->
            val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(s.date)) }.getOrDefault(0)
            val due = when { days < 0 -> "${t("overdue")} · ${-days} ${t("daysAgo")}"; days == 0L -> t("today"); days == 1L -> t("kt_tomorrow"); else -> t("kt_in_days", days) }
            val badge = when { days < 0 -> Expense; days <= 7 -> Color(0xFFFB923C); else -> Muted }
            val c = category(s.category)
            val shape = RoundedCornerShape(16.dp)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().tap(onClick = { edit = s }).clip(shape).background(Surface1)
                .border(1.dp, GlassBorder, shape).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Bubble(icon(c.icon), catColor(c)); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.frequency.label + if (s.autoPost) " · ${t("kt_automatic")}" else "", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    Text(due, Modifier.clip(CircleShape).background(badge.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = badge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(s.amount, s.currency), style = MaterialTheme.typography.titleSmall, color = if (s.type == TxType.INCOME) Income else Ink)
                    Spacer(Modifier.height(6.dp))
                    GhostButton(if (s.type == TxType.INCOME) t("kt_collect") else t("kt_pay"), Icons.Outlined.Check,
                        color = if (s.type == TxType.INCOME) Income else Brand) {
                        if (Store.postScheduled(s, s.accountId ?: d.accounts.firstOrNull()?.id)) nav.snack(t("kt_posted", s.name)) else nav.snack(t("kt_first_wallet"))
                    }
                }
            }
        }
    }
    if (creating || edit != null) ScheduledEditor(edit) { edit = null; creating = false }
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
        Segmented(listOf(TxType.EXPENSE to t("kt_payment"), TxType.INCOME to t("income")), type,
            colorOf = { if (it == TxType.INCOME) Income else Expense }) { type = it; cat = if (it == TxType.INCOME) "income" else "bills" }
        Field(name, { name = it }, t("kt_planned_name_hint"))
        Field(amount, { amount = it }, t("amount"), number = true, suffix = symbol(acc?.currency ?: currency))
        if (d.accounts.isEmpty()) Picker(t("currency"), Currency.entries.map { it to it.name }, currency) { currency = it }
        else Picker(t("wallet"), d.accounts.map { it.id to "${it.name} (${it.currency})" }, accountId) { accountId = it }
        Picker(t("frequency"), Frequency.entries.map { it to it.label }, freq) { freq = it }
        Caps(t("category"))
        CategoryStrip(CATEGORIES.filter { it.id != "transfer" && it.income == (type == TxType.INCOME) }, cat) { cat = it }
        DateField(t("kt_next_date"), date) { date = it }
        SettingRow(t("kt_auto_post"), t("kt_auto_post_desc")) { Toggle(auto, enabled = acc != null) { auto = it } }
    }
}

// ------------------------------------------------------------------------------------------------ Presupuesto y metas

@Composable
fun BudgetGoalsScreen(nav: Nav, goals: Boolean) {
    var creating by remember { mutableStateOf(false) }
    Column {
        ViewHeader(if (goals) t("goals") else t("budget"), if (goals) t("kt_goals_sub") else t("kt_budget_sub"), nav.back) {
            RoundIcon(Icons.Outlined.Add, t("add"), { creating = true }, tint = Color.White, bg = Brand)
        }
        if (goals) Goals(creating) { creating = false } else Budgets(creating) { creating = false }
    }
}

@Composable
private fun Budgets(creating: Boolean, doneCreating: () -> Unit) {
    val d = Store.data
    var edit by remember { mutableStateOf<Budget?>(null) }
    val list = d.budgets.filter { it.month == null || it.month == YearMonth.now().toString() }
    val limit = list.sumOf { it.limit }
    val spent = list.sumOf { d.spentUsd(it.categoryId) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp)) {
        if (list.isEmpty()) item { Empty(Icons.Outlined.PieChart, t("kt_no_budget"), t("kt_no_budget_desc"), t("kt_create_budget")) { edit = Budget("", 0.0) } }
        else item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Caps(t("kt_left_this_month"))
                    AutoText(money(limit - spent), MaterialTheme.typography.headlineMedium, if (spent > limit) Expense else Ink)
                    Progress(t("kt_spent"), spent, limit, "${money(spent)} / ${money(limit)}")
                }
            }
            Section(t("kt_by_category"))
        }
        items(list.sortedByDescending { d.spentUsd(it.categoryId) / it.limit }, key = { it.categoryId }) { b ->
            val s = d.spentUsd(b.categoryId)
            val c = category(b.categoryId)
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { edit = b }, radius = 16.dp) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Bubble(icon(c.icon), catColor(c), 40.dp); Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) { Progress(c.name, s, b.limit, "${money(s)} / ${money(b.limit)}", catColor(c)) }
                }
            }
        }
    }
    if (creating || edit != null) {
        val initial = edit?.takeIf { it.categoryId.isNotEmpty() }
        val firstFree = (CATEGORIES.firstOrNull { it.id !in list.map { b -> b.categoryId } && !it.income && it.id != "transfer" } ?: CATEGORIES.first()).id
        var cat by remember(initial) { mutableStateOf(initial?.categoryId ?: firstFree) }
        var amount by remember(initial) { mutableStateOf(initial?.limit?.let(::num) ?: "") }
        val v = parseAmount(amount)
        val close = { edit = null; doneCreating() }
        FormSheet(if (initial == null) t("kt_new_limit") else t("kt_edit_limit"), close, saveEnabled = v != null && v > 0, onSave = {
            if (initial != null && initial.categoryId != cat) Store.deleteBudget(initial.categoryId)
            Store.saveBudget(Budget(cat, v!!)); close()
        }, onDelete = initial?.let { { Store.deleteBudget(it.categoryId); close() } }) {
            Caps(t("category"))
            CategoryStrip(CATEGORIES.filter { !it.income && it.id != "transfer" }, cat) { cat = it }
            Field(amount, { amount = it }, t("monthlyLimit"), number = true, suffix = "USD")
        }
    }
}

@Composable
private fun Goals(creating: Boolean, doneCreating: () -> Unit) {
    val d = Store.data
    var edit by remember { mutableStateOf<Goal?>(null) }
    var contribute by remember { mutableStateOf<Goal?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (d.goals.isEmpty()) item { Empty(Icons.Outlined.EmojiEvents, t("kt_no_goals"), t("kt_no_goals_desc"), t("addGoal")) { edit = Goal(name = "", targetAmount = 0.0, deadline = "") } }
        items(d.goals.sortedBy { it.completed }, key = { it.id }) { g ->
            val f = if (g.targetAmount > 0) (g.savedAmount / g.targetAmount).toFloat() else 0f
            GlassCard(Modifier.fillMaxWidth(), onClick = { edit = g }) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Bubble(if (g.completed) Icons.Outlined.TaskAlt else Icons.Outlined.EmojiEvents, Color(0xFFEAB308)); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${t("deadline")}: ${dayLabel(g.deadline)}", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(g.targetAmount), style = MaterialTheme.typography.titleSmall, color = Ink)
                            Text("${(f * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Bar(f, if (g.completed) Income else Color(0xFFEAB308))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (g.completed) t("kt_achieved") else t("kt_missing", money((g.targetAmount - g.savedAmount).coerceAtLeast(0.0))),
                            Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = if (g.completed) Income else Muted)
                        if (!g.completed) GhostButton(t("kt_contribute"), Icons.Outlined.Add, Brand) { contribute = g }
                    }
                }
            }
        }
    }
    if (creating || edit != null) {
        val initial = edit?.takeIf { it.name.isNotEmpty() || it.targetAmount > 0 }
        var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
        var target by remember(initial) { mutableStateOf(initial?.targetAmount?.let(::num) ?: "") }
        var saved by remember(initial) { mutableStateOf(initial?.savedAmount?.let(::num) ?: "0") }
        var date by remember(initial) { mutableStateOf(initial?.deadline?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now().plusMonths(6)) }
        val tg = parseAmount(target)
        val close = { edit = null; doneCreating() }
        FormSheet(if (initial == null) t("addGoal") else t("kt_edit_goal"), close, saveEnabled = name.isNotBlank() && tg != null && tg > 0, onSave = {
            Store.saveGoal((initial ?: Goal(name = "", targetAmount = 0.0, deadline = "")).copy(name = name.trim(), targetAmount = tg!!,
                savedAmount = parseAmount(saved) ?: 0.0, deadline = date.toString())); close()
        }, onDelete = initial?.let { { Store.deleteGoal(it.id); close() } }) {
            Field(name, { name = it }, t("kt_goal_name_hint"))
            Field(target, { target = it }, t("kt_target"), number = true, suffix = "USD")
            Field(saved, { saved = it }, t("kt_saved_so_far"), number = true, suffix = "USD")
            DateField(t("deadline"), date) { date = it }
        }
    }
    contribute?.let { g ->
        var amount by remember(g) { mutableStateOf("") }
        val v = parseAmount(amount)
        AlertDialog(onDismissRequest = { contribute = null }, containerColor = Surface1, shape = RoundedCornerShape(28.dp),
            title = { Text(t("kt_contribute_to", g.name), color = Ink) },
            text = { Field(amount, { amount = it }, t("amount"), number = true, suffix = "USD") },
            confirmButton = { BrandButton(t("kt_contribute"), enabled = v != null && v > 0) { Store.saveGoal(g.copy(savedAmount = g.savedAmount + v!!)); contribute = null } },
            dismissButton = { TextButton(onClick = { contribute = null }) { Text(t("cancel"), color = Muted) } })
    }
}
