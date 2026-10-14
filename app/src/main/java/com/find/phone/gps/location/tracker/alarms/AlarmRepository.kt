package com.find.phone.gps.location.tracker.alarms

import android.content.Context
import com.find.phone.gps.location.tracker.alarms.gate.AlarmGrant
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

object AlarmRepository {

    private const val PREFS = "reminder_prefs"
    private const val KEY = "reminders"
    private const val KEY_SEEDED = "reminder_defaults_seeded"

    fun load(context: Context): MutableList<AlarmEntry> {
        val json = prefs(context).getString(KEY, null)
        if (json.isNullOrEmpty()) return mutableListOf()
        val type = object : TypeToken<MutableList<AlarmEntry>>() {}.type
        return runCatching { Gson().fromJson<MutableList<AlarmEntry>>(json, type) }.getOrNull()
            ?.filter { it.title != null }?.toMutableList() ?: mutableListOf()
    }

    fun get(context: Context, id: Long): AlarmEntry? = load(context).firstOrNull { it.id == id }

    fun upsert(context: Context, item: AlarmEntry) {
        val list = load(context)
        val i = list.indexOfFirst { it.id == item.id }
        if (i >= 0) list[i] = item else list.add(item)
        save(context, list)
    }

    fun remove(context: Context, id: Long) {
        val list = load(context)
        if (list.removeAll { it.id == id }) save(context, list)
    }

    fun activeCount(context: Context): Int = load(context).count { it.isActive }

    private fun save(context: Context, list: List<AlarmEntry>) {
        prefs(context).edit().putString(KEY, Gson().toJson(list)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun seedDefaultsIfNeeded(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val cfg = AlarmSettings.get(context)
        if (!cfg.isDefaultConfig || !AlarmGrant.isAllowed(context)) return
        if (load(context).isEmpty()) {
            val now = System.currentTimeMillis()
            val seeded = cfg.defaultConfigList.filter { it.title.isNotBlank() }.mapIndexed { i, spec ->
                val at = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, spec.daysAhead.coerceAtLeast(0))
                    set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                AlarmEntry(id = now + i, title = spec.title.trim(), note = spec.note.trim(), dateTime = at, enabled = false)
            }
            if (seeded.isNotEmpty()) save(context, seeded)
        }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }
}
