package io.homedeck.grid.support

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import io.homedeck.grid.R
import io.homedeck.grid.ext.getDrawableForPackageName
import io.homedeck.grid.ext.launcherConfig
import java.util.concurrent.Executors

object GlyphFetcher {

    private val cache = object : LruCache<String, Drawable>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(1024)
    ) {
        override fun sizeOf(key: String, value: Drawable): Int =
            ((value as? BitmapDrawable)?.bitmap?.allocationByteCount ?: 0) / 1024 + 1
    }
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "icon-loader").apply { priority = Thread.NORM_PRIORITY - 1; isDaemon = true }
    }

    @Volatile
    private var tray: GlyphMasker.Tray? = null

    private fun key(packageName: String, activityName: String) = "$packageName/$activityName"

    fun cached(packageName: String, activityName: String): Drawable? = cache.get(key(packageName, activityName))

    fun put(packageName: String, activityName: String, drawable: Drawable) {
        cache.put(key(packageName, activityName), drawable)
    }

    fun invalidate(keys: Collection<String>) {
        keys.forEach { cache.remove(it) }
    }

    fun clear() {
        cache.evictAll()
    }

    fun syncTray(current: GlyphMasker.Tray) {
        if (tray != current) {
            if (tray != null) clear()
            tray = current
        }
    }

    fun load(context: Context, packageName: String, activityName: String, onReady: (Drawable) -> Unit) {
        val app = context.applicationContext
        pool.execute {
            val drawable = cached(packageName, activityName)
                ?: decode(app, packageName, activityName)?.also { put(packageName, activityName, it) }
            if (drawable != null) main.post { onReady(drawable) }
        }
    }

    private fun decode(context: Context, packageName: String, activityName: String): Drawable? = runCatching {
        val pm = context.packageManager
        val legacyTray = context.launcherConfig.legacyIconTray
        syncTray(legacyTray)
        val raw = pm.getActivityInfo(ComponentName(packageName, activityName), 0).loadIcon(pm)
            ?.let { GlyphMasker.shape(it, legacyTray) }
            ?: context.getDrawableForPackageName(packageName)
            ?: return null
        val side = context.resources.getDimensionPixelSize(R.dimen.launcher_icon_cache_size)
        raw.toBitmap(
            width = raw.intrinsicWidth.coerceIn(1, side),
            height = raw.intrinsicHeight.coerceIn(1, side),
            config = Bitmap.Config.ARGB_8888,
        ).toDrawable(context.resources)
    }.getOrNull()
}
