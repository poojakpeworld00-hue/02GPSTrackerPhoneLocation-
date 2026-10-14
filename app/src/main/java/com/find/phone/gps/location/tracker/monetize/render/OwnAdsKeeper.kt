package com.find.phone.gps.location.tracker.monetize.render
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.find.phone.gps.location.tracker.monetize.model.InHouseOffer
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.R

class OwnAdsKeeper {

    companion object {
        private var cachedAds: List<InHouseOffer>? = null

        fun wipeCache() {
            cachedAds = null
        }
    }

    enum class OwnAdFormat {
        BIG_NATIVE,
        MID_NATIVE,
        BANNER,
        FULLSCREEN_NATIVE
    }

    fun fetchCustomAd(
        context: Context,
        container: FrameLayout,
        type: OwnAdFormat,
        backupImage: ImageView? = null,
        backupLayout: LinearLayout? = null,
        onFail: (() -> Unit)? = null
    ) {
        if (context.isHostActivityDead()) {
            fallback(container, backupImage, backupLayout, onFail)
            return
        }

        val pref = AdPrefsStore.getInstance(context)

        if (!pref.getBoolean("IsCustomADS")) {
            fallback(container, backupImage, backupLayout, onFail)
            return
        }

        val ads: List<InHouseOffer> = if (cachedAds != null) {
            cachedAds!!
        } else {
            val json = pref.getString("CUSTOM_ADS", null)
            if (json.isNullOrEmpty()) {
                fallback(container, backupImage, backupLayout, onFail)
                return
            }
            try {
                val parsed: List<InHouseOffer> = Gson().fromJson(json, object : TypeToken<List<InHouseOffer>>() {}.type)
                cachedAds = parsed
                parsed
            } catch (e: Exception) {
                fallback(container, backupImage, backupLayout, onFail)
                return
            }
        }

        if (ads.isEmpty()) {
            fallback(container, backupImage, backupLayout, onFail)
            return
        }

        val ad = ads.random()

        val layoutId = when (type) {
            OwnAdFormat.BIG_NATIVE -> R.layout.own_native_ads1
            OwnAdFormat.MID_NATIVE -> R.layout.own_native_ads2
            OwnAdFormat.BANNER -> R.layout.strip_custom_ads
            OwnAdFormat.FULLSCREEN_NATIVE -> R.layout.own_native_full_screen_ads1
        }
        val view = try {
            LayoutInflater.from(container.context).inflate(layoutId, container, false)
        } catch (e: Exception) {
            fallback(container, backupImage, backupLayout, onFail)
            return
        }

        imprintTheme(context, view, pref)
        wireData( view, ad)
        setClickListeners(view)

        container.post {
            if (context.isHostActivityDead()) {
                container.visibility = View.GONE
                return@post
            }

            container.findFocus()?.clearFocus()
            container.removeAllViews()
            container.addView(view)
            container.visibility = View.VISIBLE

            backupImage?.visibility = View.GONE
            backupLayout?.visibility = View.GONE
        }
    }

    private fun imprintTheme(context: Context, view: View, pref: AdPrefsStore) {
        try {

            val bgColor = color(pref.getString("NativeBgColor"), "#FFFFFF")
            val btnColor = color(pref.getString("NativebtnColor"), "#000000")
            val txtColor = color(pref.getString("NativetxtColor"), "#000000")
            val btnTxtColor = color(pref.getString("NativebtntxtColor"), "#FFFFFF")

            val main = view.findViewById<LinearLayout?>(R.id.mainlnrVw)
            main?.backgroundTintList = ColorStateList.valueOf(bgColor)

            val button = view.findViewById<TextView?>(R.id.btntextVw)
            button?.let { SponsorButtonStyle.apply(it) }
            button?.setTextColor(btnTxtColor)

            val bannerButton = view.findViewById<TextView?>(R.id.only_banner_buttonVw)
            bannerButton?.let { SponsorButtonStyle.apply(it) }
            bannerButton?.setTextColor(btnTxtColor)

            view.findViewById<TextView?>(R.id.titileTextVw)?.setTextColor(txtColor)
            view.findViewById<TextView?>(R.id.TexttextVw)?.setTextColor(txtColor)

            view.findViewById<TextView?>(R.id.only_banner_titleVw)?.setTextColor(txtColor)
            view.findViewById<TextView?>(R.id.only_banner_descVw)?.setTextColor(txtColor)

        } catch (_: Exception) {

        }
    }

    private fun wireData(view: View, ad: InHouseOffer) {

        view.findViewById<TextView?>(R.id.titileTextVw)?.text = ad.title
        view.findViewById<TextView?>(R.id.TexttextVw)?.text = ad.description
        view.findViewById<TextView?>(R.id.btntextVw)?.text = ad.buttonText

        view.findViewById<TextView?>(R.id.only_banner_titleVw)?.text = ad.title
        view.findViewById<TextView?>(R.id.only_banner_descVw)?.text = ad.description
        view.findViewById<TextView?>(R.id.only_banner_buttonVw)?.text = ad.buttonText

        view.findViewById<ImageView?>(R.id.gif_imageVw)?.let { wireGlideSafe(it, ad.icon) }
        view.findViewById<ImageView?>(R.id.only_banner_logoVw)?.let { wireGlideSafe(it, ad.icon) }
        view.findViewById<ImageView?>(R.id.custom_nativeVw)?.let { wireGlideSafe(it, ad.bannerImage) }
    }

    private fun Context.locateHostActivity(): Activity? {
        var ctx: Context = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun Context.isHostActivityDead(): Boolean {
        val act = this as? Activity ?: locateHostActivity() ?: return false
        if (act.isFinishing) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && act.isDestroyed
    }

    private fun wireGlideSafe(imageView: ImageView, url: String?) {
        if (url.isNullOrEmpty()) return
        if (imageView.context.isHostActivityDead()) return
        try {
            Glide.with(imageView).load(url).into(imageView)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun setClickListeners(view: View) {
        val click = View.OnClickListener {
            Directlink(view.context)
        }

        view.setOnClickListener(click)
        view.findViewById<View?>(R.id.btntextVw)?.setOnClickListener(click)
        view.findViewById<View?>(R.id.only_banner_buttonVw)?.setOnClickListener(click)
    }

    private fun fallback(
        container: FrameLayout,
        img: ImageView?,
        layout: LinearLayout?,
        onFail: (() -> Unit)?
    ) {
        container.post {
            if (container.context.isHostActivityDead()) {
                container.visibility = View.GONE
                return@post
            }

            container.findFocus()?.clearFocus()
            container.removeAllViews()
            container.visibility = View.GONE

            img?.visibility = View.VISIBLE
            layout?.visibility = View.VISIBLE

            onFail?.invoke()
        }
    }

    private fun color(value: String?, default: String): Int {
        return try {
            Color.parseColor(value ?: default)
        } catch (_: Exception) {
            Color.parseColor(default)
        }
    }
}
