package com.find.phone.gps.location.tracker.alarms

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object AlarmPlanner {

    private fun workName(id: Long) = "reminder_$id"

    fun sync(context: Context, item: AlarmEntry) {
        if (!item.isActive) return cancel(context, item.id)
        val now = System.currentTimeMillis()
        val at = if (item.dateTime > now) item.dateTime
        else Repeat.nextAfter(item.repeatOrOnce, item.baseTime, now) ?: return cancel(context, item.id)
        if (at != item.dateTime) AlarmRepository.upsert(context, item.copy(dateTime = at))

        val request = OneTimeWorkRequestBuilder<AlarmJob>()
            .setInitialDelay(at - now, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(AlarmJob.KEY_ID to item.id))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(item.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
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
        val item = AlarmRepository.get(context, id) ?: return
        save(context, item.copy(dateTime = System.currentTimeMillis() + minutes * 60_000L, done = false, enabled = true))
    }

    fun markDone(context: Context, id: Long) {
        val item = AlarmRepository.get(context, id) ?: return
        val next = Repeat.nextAfter(item.repeatOrOnce, item.baseTime, System.currentTimeMillis())
        if (next == null) {
            cancel(context, id)
            AlarmRepository.upsert(context, item.copy(done = true))
        } else {
            save(context, item.copy(dateTime = next))
        }
    }

    private const val TAG = "reminder"
}
