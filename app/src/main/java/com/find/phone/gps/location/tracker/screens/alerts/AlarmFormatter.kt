package com.find.phone.gps.location.tracker.screens.alerts

import android.content.Context
import android.text.format.DateFormat
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.alarms.Repeat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AlarmFormatter {

    private fun matchesDate(first: Calendar, second: Calendar): Boolean =
        first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

    private fun offsetFrom(base: Calendar, days: Int): Calendar =
        (base.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, days) }

    private fun repeatRes(repeat: String): Int = when (repeat) {
        Repeat.DAILY -> R.string.rem_repeat_daily
        Repeat.WEEKDAYS -> R.string.rem_repeat_weekdays
        else -> R.string.rem_repeat_once
    }

    fun time(context: Context, millis: Long): String {
        val formatter = DateFormat.getTimeFormat(context)
        return formatter.format(Date(millis))
    }

    fun day(context: Context, millis: Long): String {
        val moment = Calendar.getInstance().also { it.timeInMillis = millis }
        val now = Calendar.getInstance()
        return when {
            matchesDate(moment, now) -> context.getString(R.string.rem_today)
            matchesDate(moment, offsetFrom(now, 1)) -> context.getString(R.string.rem_tomorrow)
            matchesDate(moment, offsetFrom(now, -1)) -> context.getString(R.string.rem_yesterday)
            else -> {
                val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM")
                DateFormat.format(pattern, moment).toString()
            }
        }
    }

    fun dayAndTime(context: Context, millis: Long): String {
        val dayPart = day(context, millis)
        val timePart = time(context, millis)
        return context.getString(R.string.rem_when_fmt, dayPart, timePart)
    }

    fun repeatLabel(context: Context, repeat: String): String = context.getString(repeatRes(repeat))
}
