package com.find.phone.gps.location.tracker.alarms

import java.util.Calendar

data class AlarmEntry(
    val id: Long = System.currentTimeMillis(),
    val title: String,

    val number: String = "",
    val note: String = "",
    val dateTime: Long,
    val baseTime: Long = dateTime,
    val repeat: String = Repeat.ONCE,

    val enabled: Boolean = true,

    val done: Boolean = false,
) {

    val repeatOrOnce: String get() = repeat.ifBlank { Repeat.ONCE }

    val isActive: Boolean get() = enabled && !done
}

object Repeat {
    const val ONCE = "once"
    const val DAILY = "daily"
    const val WEEKDAYS = "weekdays"

    fun nextAfter(repeat: String, base: Long, after: Long): Long? {
        if (repeat != DAILY && repeat != WEEKDAYS) return null
        val pick = Calendar.getInstance().apply { timeInMillis = base }
        val c = Calendar.getInstance().apply {
            timeInMillis = after
            set(Calendar.HOUR_OF_DAY, pick.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, pick.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            val weekday = c.get(Calendar.DAY_OF_WEEK) !in setOf(Calendar.SATURDAY, Calendar.SUNDAY)
            if (c.timeInMillis > after && (repeat == DAILY || weekday)) return c.timeInMillis
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }
}
