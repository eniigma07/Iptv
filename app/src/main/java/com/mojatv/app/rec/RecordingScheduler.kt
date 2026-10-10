package com.mojatv.app.rec

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.PlayItem
import java.io.File

/** Planowanie nagrań: budzik systemowy uruchamia nagrywanie o czasie, także przy zamkniętej aplikacji. */
object RecordingScheduler {

    private fun store() = MojaTvApp.instance.recordings

    /** Tworzy nagranie (start/end = prawdziwe godziny audycji, zapas dodawany tutaj). */
    fun create(
        context: Context,
        channel: PlayItem,
        title: String,
        programStart: Long,
        programEnd: Long,
        addPadding: Boolean = true,
    ): Recording {
        val storage = MojaTvApp.repo.storage
        val now = System.currentTimeMillis()
        val start = if (addPadding) programStart - storage.recPadBefore * 60_000L else programStart
        val end = if (addPadding) programEnd + storage.recPadAfter * 60_000L else programEnd
        val rec = Recording(
            id = store().newId(),
            channel = channel,
            title = title,
            start = maxOf(start, now),
            end = end,
            status = RecStatus.SCHEDULED,
        )
        store().add(rec)
        schedule(context, rec)
        return rec
    }

    fun schedule(context: Context, rec: Recording) {
        val now = System.currentTimeMillis()
        if (rec.start <= now + 3_000) {
            startNow(context, rec.id)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(context, rec.id)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rec.start, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rec.start, pi)
        }
    }

    fun startNow(context: Context, id: String) {
        val intent = Intent(context, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_ID, id)
        ContextCompat.startForegroundService(context, intent)
    }

    /** Anuluje zaplanowane albo zatrzymuje trwające nagranie. */
    fun cancel(context: Context, rec: Recording) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent(context, rec.id))
        when (rec.status) {
            RecStatus.RECORDING -> context.startService(
                Intent(context, RecordingService::class.java)
                    .setAction(RecordingService.ACTION_STOP)
                    .putExtra(RecordingService.EXTRA_ID, rec.id)
            )
            RecStatus.SCHEDULED -> store().update(rec.id) { it.copy(status = RecStatus.CANCELLED) }
            else -> Unit
        }
    }

    /** Usuwa nagranie z listy (i plik). */
    fun delete(context: Context, rec: Recording) {
        if (rec.active) cancel(context, rec)
        rec.filePath?.let { runCatching { File(it).delete() } }
        rec.contentUri?.let { runCatching { context.contentResolver.delete(android.net.Uri.parse(it), null, null) } }
        store().remove(rec.id)
    }

    /** Po restarcie telefonu / aktualizacji: ponowne ustawienie budzików. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        store().items.value.forEach { r ->
            when {
                r.status == RecStatus.SCHEDULED && r.end > now -> schedule(context, r)
                r.status == RecStatus.SCHEDULED -> store().update(r.id) { it.copy(status = RecStatus.FAILED, error = "Telefon był wyłączony") }
                r.status == RecStatus.RECORDING && r.end > now -> startNow(context, r.id)
                r.status == RecStatus.RECORDING -> store().update(r.id) {
                    it.copy(status = if (it.bytes > 0) RecStatus.DONE else RecStatus.FAILED)
                }
                else -> Unit
            }
        }
    }

    fun overlapping(start: Long, end: Long): List<Recording> =
        store().items.value.filter { it.active && it.start < end && it.end > start }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    fun ignoresBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun alarmIntent(context: Context, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.hashCode(),
            Intent(context, RecordingAlarmReceiver::class.java).putExtra(RecordingService.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Budzik nagrania. */
class RecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(RecordingService.EXTRA_ID) ?: return
        RecordingScheduler.startNow(context, id)
    }
}

/** Po włączeniu telefonu i po aktualizacji aplikacji. */
class RecordingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RecordingScheduler.rescheduleAll(context)
    }
}
