package io.homedeck.grid.support

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import io.homedeck.grid.R
import io.homedeck.grid.ext.getDrawableForPackageName
import io.homedeck.grid.ext.hiddenIconsDB
import io.homedeck.grid.ext.launcherConfig
import io.homedeck.grid.ext.launchersDB
import io.homedeck.grid.entities.LaunchableApp
import timber.log.Timber
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.math.max

object HomeRoleProbe {

    private const val COLOR_SAMPLE_EDGE = 16

    private const val PROGRESS_INTERVAL_MS = 150L

    private const val MICRO_G = "com.google.android.gms"

    private val lock = Any()

    fun prewarm(context: Context) {
        val app = context.applicationContext
        Thread { runCatching { scan(app) } }.apply { priority = Thread.MIN_PRIORITY + 2 }.start()
    }

    @SuppressLint("WrongConstant")
    fun quick(context: Context): List<LaunchableApp> = with(context) {
        val hidden = hiddenIconsDB.getHiddenIcons().map { it.getIconIdentifier() }
        wanted(this, launchable(this), hidden).map { info -> bare(info, info.loadLabel(packageManager).toString()) }
    }

    @SuppressLint("WrongConstant")
    fun scan(
        context: Context,
        onQuick: (List<LaunchableApp>) -> Unit = {},
        onProgress: (List<LaunchableApp>) -> Unit = {},
    ): ArrayList<LaunchableApp> = synchronized(lock) {
        with(context) {
            val hiddenIcons = hiddenIconsDB.getHiddenIcons().map { it.getIconIdentifier() }
            val list = launchable(this)

            val signature = installedSignature(list, hiddenIcons)
            val cached = GlyphMemo.launchers
            if (cached.isNotEmpty() && signature == GlyphMemo.installedSignature) {
                return@synchronized ArrayList(cached)
            }

            val started = SystemClock.elapsedRealtime()
            val wanted = wanted(this, list, hiddenIcons)

            val labels = HashMap<ResolveInfo, String>(wanted.size * 2)
            wanted.forEach { labels[it] = it.loadLabel(packageManager).toString() }
            val ordered = wanted.sortedBy { labels[it]!!.lowercase() }

            if (cached.isEmpty()) {
                onQuick(ArrayList(ordered.map { bare(it, labels[it]!!) }))
            }

            val sourceDirs = wanted.associate { keyOf(it) to it.activityInfo.applicationInfo.sourceDir.orEmpty() }
            val knownDirs = GlyphMemo.sourceDirs
            val reusable = cached
                .filter { it.drawable != null }
                .associateBy { "${it.packageName}/${it.activityName}" }
                .filterKeys { key -> knownDirs[key] != null && knownDirs[key] == sourceDirs[key] }

            val legacyTray = launcherConfig.legacyIconTray
            GlyphFetcher.syncTray(legacyTray)
            GlyphFetcher.invalidate(sourceDirs.keys.filter { key -> knownDirs[key] != null && knownDirs[key] != sourceDirs[key] })

            val iconPx = resources.getDimensionPixelSize(R.dimen.launcher_icon_cache_size)
            val allApps = ArrayList<LaunchableApp>()
            val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
            try {
                val decoded = ordered.map { info ->
                    reusable[keyOf(info)]?.let { kept ->

                        val label = labels[info]!!
                        return@map CompletableFuture.completedFuture<LaunchableApp?>(
                            kept.copy(title = label.takeIf { it.isNotBlank() && it != kept.packageName } ?: kept.title)
                        )
                    }
                    pool.submit<LaunchableApp?> { decode(this, info, labels[info]!!, iconPx, legacyTray) }
                }

                val progressive = cached.none { it.drawable != null }
                val restored = cached.associateBy { "${it.packageName}/${it.activityName}" }
                var lastProgress = SystemClock.elapsedRealtime()
                decoded.forEachIndexed { i, future ->
                    runCatching { future.get() }.getOrNull()?.let(allApps::add)
                    val now = SystemClock.elapsedRealtime()
                    if (progressive && i + 1 < decoded.size && now - lastProgress >= PROGRESS_INTERVAL_MS) {
                        lastProgress = now

                        val rest = ordered.drop(i + 1).map { info -> restored[keyOf(info)] ?: bare(info, labels[info]!!) }
                        onProgress(ArrayList(allApps) + rest)
                    }
                }
            } finally {
                pool.shutdown()
            }

            Timber.d("HomeRoleProbe: ${allApps.size} launchers, ${allApps.size - reusable.size} icons decoded in ${SystemClock.elapsedRealtime() - started} ms")
            launchersDB.insertAll(allApps)

            GlyphMemo.installedSignature = signature
            GlyphMemo.sourceDirs = sourceDirs
            allApps
        }
    }

    @SuppressLint("WrongConstant")
    private fun launchable(context: Context): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
    }

    private fun wanted(context: Context, list: List<ResolveInfo>, hiddenIcons: List<String>): List<ResolveInfo> {
        val own = context.applicationContext.packageName
        val selfHidden = context.launcherConfig.selfIconHidden
        return list.filter { info ->
            val packageName = info.activityInfo.applicationInfo.packageName

            (packageName != own || !selfHidden) && packageName != MICRO_G &&
                !hiddenIcons.contains("$packageName/${info.activityInfo.name}")
        }
    }

    private fun keyOf(info: ResolveInfo) = "${info.activityInfo.applicationInfo.packageName}/${info.activityInfo.name}"

    private fun bare(info: ResolveInfo, label: String) = LaunchableApp(
        id = null,
        title = label,
        packageName = info.activityInfo.applicationInfo.packageName,
        activityName = info.activityInfo.name,
        order = 0,
        thumbnailColor = 0,
        drawable = null
    )

    private fun decode(context: Context, info: ResolveInfo, label: String, iconPx: Int, tray: GlyphMasker.Tray): LaunchableApp? {
        val packageName = info.activityInfo.applicationInfo.packageName
        val activityName = info.activityInfo.name

        GlyphFetcher.cached(packageName, activityName)?.let { hit ->
            val bitmap = (hit as? BitmapDrawable)?.bitmap
            return LaunchableApp(
                id = null, title = label, packageName = packageName, activityName = activityName, order = 0,
                thumbnailColor = bitmap?.let(::deriveAverageColor) ?: 0, drawable = hit,
            )
        }
        val drawable = info.loadIcon(context.packageManager)?.let { GlyphMasker.shape(it, tray) }
            ?: context.getDrawableForPackageName(packageName)
            ?: return null
        val bitmap = drawable.toBitmap(
            width = drawable.intrinsicWidth.coerceIn(1, iconPx),
            height = drawable.intrinsicHeight.coerceIn(1, iconPx),
            config = Bitmap.Config.ARGB_8888
        )
        val icon = bitmap.toDrawable(context.resources)
        GlyphFetcher.put(packageName, activityName, icon)
        return LaunchableApp(
            id = null,
            title = label,
            packageName = packageName,
            activityName = activityName,
            order = 0,
            thumbnailColor = deriveAverageColor(bitmap),
            drawable = icon
        )
    }

    private fun installedSignature(list: List<ResolveInfo>, hiddenIcons: List<String>): String {
        val apps = list
            .map { "${it.activityInfo.packageName}/${it.activityInfo.name}/${it.activityInfo.applicationInfo.sourceDir}" }
            .sorted()
        return apps.joinToString("\n") + "\u0000" + hiddenIcons.sorted().joinToString("\n")
    }

    private fun deriveAverageColor(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return Color.TRANSPARENT
        }

        val stepX = max(1, width / COLOR_SAMPLE_EDGE)
        val stepY = max(1, height / COLOR_SAMPLE_EDGE)
        val row = IntArray(width)

        var red = 0L
        var green = 0L
        var blue = 0L
        var n = 0
        var y = 0
        while (y < height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            var x = 0
            while (x < width) {
                val color = row[x]
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
                n++
                x += stepX
            }
            y += stepY
        }

        if (n == 0) {
            return Color.TRANSPARENT
        }

        return Color.rgb((red / n).toInt(), (green / n).toInt(), (blue / n).toInt())
    }
}
