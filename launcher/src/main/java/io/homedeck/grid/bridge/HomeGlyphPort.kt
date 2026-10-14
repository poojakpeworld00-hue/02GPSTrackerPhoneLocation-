package io.homedeck.grid.bridge

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import io.homedeck.grid.R
import io.homedeck.grid.ext.launcherConfig
import io.homedeck.grid.support.GlyphMasker

object HomeGlyphPort {

    @JvmStatic
    fun shape(context: Context, drawable: Drawable): Drawable =
        GlyphMasker.shape(drawable, context.launcherConfig.legacyIconTray)

    @JvmStatic
    fun applyTo(imageView: ImageView) {
        val current = imageView.drawable ?: return
        if (imageView.getTag(R.id.lnch_tag_icon_shaped) === current) return
        val shaped = shape(imageView.context, current)
        imageView.setImageDrawable(shaped)
        imageView.setTag(R.id.lnch_tag_icon_shaped, shaped)
    }
}
