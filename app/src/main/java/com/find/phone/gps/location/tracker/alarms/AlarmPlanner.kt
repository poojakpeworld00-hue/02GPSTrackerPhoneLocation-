package com.find.phone.gps.location.tracker.alarms

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object AlarmPlanner {

    private const val TAG = "reminder"

    private fun uniqueName(alarmId: Long) = "reminder_$alarmId"

    private fun nextFireAt(entry: AlarmEntry, nowMs: Long): Long? = when {
        entry.dateTime > nowMs -> entry.dateTime
        else -> Repeat.nextAfter(entry.repeatOrOnce, entry.baseTime, nowMs)
    }

    fun sync(context: Context, item: AlarmEntry) {
        if (!item.isActive) {
            cancel(context, item.id)
            return
        }
        val nowMs = System.currentTimeMillis()
        val fireAt = nextFireAt(item, nowMs)
        if (fireAt == null) {
            cancel(context, item.id)
            return
        }
        if (fireAt != item.dateTime) AlarmRepository.upsert(context, item.copy(dateTime = fireAt))

        val job = OneTimeWorkRequestBuilder<AlarmJob>()
            .setInitialDelay(fireAt - nowMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(AlarmJob.KEY_ID to item.id))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName(item.id), ExistingWorkPolicy.REPLACE, job)
    }

    fun cancel(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(id))
    }

    fun save(context: Context, item: AlarmEntry) {
        AlarmRepository.upsert(context, item)
        sync(context, item)
    }

    fun delete(context: Context, id: Long) {
        cancel(context, id)
        AlarmRepository.remove(context, id)
    }

    fun snooze(context: Context, id: Long, minutes: Int) {
        val stored = AlarmRepository.get(context, id) ?: return
        val wakeAt = System.currentTimeMillis() + minutes * 60_000L
        save(context, stored.copy(dateTime = wakeAt, done = false, enabled = true))
    }

    fun markDone(context: Context, id: Long) {
        val stored = AlarmRepository.get(context, id) ?: return
        val upcoming = Repeat.nextAfter(stored.repeatOrOnce, stored.baseTime, System.currentTimeMillis())
        if (upcoming != null) {
            save(context, stored.copy(dateTime = upcoming))
            return
        }
        cancel(context, id)
        AlarmRepository.upsert(context, stored.copy(done = true))
    }
}
