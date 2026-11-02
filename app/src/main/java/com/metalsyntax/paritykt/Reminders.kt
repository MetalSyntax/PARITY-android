package com.metalsyntax.paritykt

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.metalsyntax.paritykt.data.I18n
import com.metalsyntax.paritykt.data.Store
import com.metalsyntax.paritykt.data.TxType
import com.metalsyntax.paritykt.data.t
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Recordatorios de pagos (notificationsEnabled / notificationLeadTime del perfil, como la web): una revisión diaria a las 9:00
 * que avisa de los planificados que vencen dentro del aviso previo.
 * ponytail: alarma inexacta; tras reiniciar el teléfono se reprograma al abrir la app (sin BOOT_COMPLETED).
 */
object Reminders {
    fun enabled() = Store.data.profile.extra.optBoolean("notificationsEnabled", false)
    fun leadDays() = Store.data.profile.extra.optInt("notificationLeadTime", 1)

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (!enabled()) { am.cancel(pi(ctx)); return }
        var next = LocalDate.now().atTime(9, 0)
        if (next.isBefore(LocalDateTime.now())) next = next.plusDays(1)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), AlarmManager.INTERVAL_DAY, pi(ctx))
    }

    /** Planificados que vencen entre hoy (o vencidos) y hoy + aviso previo. */
    fun due() = Store.data.scheduled.filter {
        runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it.date)) <= leadDays() }.getOrDefault(false)
    }

    fun notifyDue(ctx: Context) {
        val list = due()
        if (list.isEmpty()) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("payments", t("scheduledNotifications"), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        list.forEachIndexed { i, s ->
            val days = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(s.date))
            val whenTxt = when { days < 0 -> t("overdue"); days == 0L -> t("today"); else -> t("kt_in_days", days) }
            val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(ctx, "payments") else @Suppress("DEPRECATION") android.app.Notification.Builder(ctx)
            nm.notify(100 + i, b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(s.name)
                .setContentText("${if (s.type == TxType.INCOME) t("income") else t("kt_payment")} · ${s.amount} ${s.currency} · $whenTxt")
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Store.ready) { I18n.load(ctx); Store.init(ctx) {} }
        if (Reminders.enabled()) Reminders.notifyDue(ctx)
    }
}
