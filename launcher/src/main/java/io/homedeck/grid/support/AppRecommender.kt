package io.homedeck.grid.support

import android.content.pm.ApplicationInfo
import io.homedeck.grid.entities.LaunchableApp
import io.homedeck.grid.entities.AppOpenStat
import kotlin.math.ln
import kotlin.math.pow

object AppRecommender {

    const val LIMIT = 8

    const val MIN_TO_SHOW = 4

    private const val HALF_LIFE_MS = 3 * 24 * 60 * 60 * 1000.0

    private const val RECENT_INSTALL_MS = 14L * 24 * 60 * 60 * 1000

    val PADDING_CATEGORIES = listOf(
        ApplicationInfo.CATEGORY_SOCIAL,
        ApplicationInfo.CATEGORY_NEWS,
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_AUDIO,
        ApplicationInfo.CATEGORY_MAPS,
        ApplicationInfo.CATEGORY_PRODUCTIVITY,
    )

    class Facts(val firstInstallTime: Long, val category: Int)

    fun score(usage: AppOpenStat, now: Long): Double {
        val age = (now - usage.lastLaunched).coerceAtLeast(0L)
        val recency = 0.5.pow(age / HALF_LIFE_MS)
        return recency + 0.35 * ln(1.0 + usage.launchCount)
    }

    fun rank(
        installed: List<LaunchableApp>,
        usage: List<AppOpenStat>,
        facts: (packageName: String) -> Facts?,
        now: Long,
        limit: Int = LIMIT,
    ): List<LaunchableApp> {
        val byPackage = installed.associateBy { it.packageName }
        val picked = LinkedHashMap<String, LaunchableApp>()

        usage.filter { it.packageName in byPackage }
            .sortedByDescending { score(it, now) }
            .forEach { picked.putIfAbsent(it.packageName, byPackage.getValue(it.packageName)) }
        if (picked.size >= limit) return picked.values.take(limit)

        installed.asSequence()
            .filter { it.packageName !in picked }
            .mapNotNull { app -> facts(app.packageName)?.let { app to it } }
            .filter { (_, f) -> now - f.firstInstallTime in 0..RECENT_INSTALL_MS }
            .sortedByDescending { (_, f) -> f.firstInstallTime }
            .forEach { (app, _) -> if (picked.size < limit) picked.putIfAbsent(app.packageName, app) }
        if (picked.size >= limit) return picked.values.take(limit)

        for (category in PADDING_CATEGORIES) {
            installed.asSequence()
                .filter { it.packageName !in picked && facts(it.packageName)?.category == category }
                .sortedBy { it.title.lowercase() }
                .forEach { if (picked.size < limit) picked.putIfAbsent(it.packageName, it) }
            if (picked.size >= limit) break
        }
        return picked.values.take(limit)
    }
}
