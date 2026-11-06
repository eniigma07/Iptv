package com.artykulacja.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.artykulacja.app.R
import com.artykulacja.app.data.Settings
import com.artykulacja.app.data.Store
import com.artykulacja.app.ui.MainActivity
import java.util.Calendar

object Reminder {
    private const val CHANNEL = "daily"

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Codzienne przypomnienie", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(ctx: Context, settings: Settings) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx))
        if (!settings.reminder) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.reminderHour)
            set(Calendar.MINUTE, settings.reminderMinute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pending(ctx))
    }

    fun show(ctx: Context) {
        val store = Store(ctx)
        if (store.todayAttempts() > 0) return
        val streak = store.streak()
        val text = if (streak > 0) "Twoja seria: $streak dni. Pięć minut wystarczy, żeby jej nie przerwać."
        else "Pięć minut dziennie robi różnicę. Gotowa na dzisiejsze zdanie?"
        val open = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Czas na wymowę")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(10, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminder.show(context)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminder.schedule(context, Settings(context))
}
