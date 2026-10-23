package com.find.phone.gps.location.tracker.foundation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.find.phone.gps.location.tracker.monetize.engine.PageSponsorSettings
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResult
import com.find.phone.gps.location.tracker.monetize.render.MonetizeGateActivity
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.FullscreenOnExit
import com.find.phone.gps.location.tracker.monetize.render.fullscreen.FullscreenOnAction
import com.find.phone.gps.location.tracker.repo.LanguageKeeper
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.helpers.AppPrefsStore
import com.find.phone.gps.location.tracker.helpers.imprintNativeAdTheme
import com.find.phone.gps.location.tracker.helpers.followAdContainer
import com.find.phone.gps.location.tracker.helpers.AppPrefsStore.THEME_DARK
import com.find.phone.gps.location.tracker.helpers.AppPrefsStore.THEME_LIGHT
import com.find.phone.gps.location.tracker.helpers.AppPrefsStore.THEME_SYSTEM
import java.util.Locale
import kotlin.sequences.ifEmpty

abstract class FoundationActivity<DB : ViewDataBinding> : MonetizeGateActivity() {

    protected lateinit var binding: DB
        private set

    @get:LayoutRes
    protected abstract val layoutId: Int

    override fun onCreate(savedInstanceState: Bundle?) {
        imprintLocale()
        imprintTheme(AppPrefsStore.effectiveTheme(this))
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, layoutId)

        binding.lifecycleOwner = this

        imprintNativeAdTheme()

        onBackPressedDispatcher.addCallback(this, backAdCallback)

        initView()
        initObservers()

        showBottomBanner()
    }

    private val backAdCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = goBack()
    }

    protected fun goBack() {
        FullscreenOnExit().presentBackAds(this) { performBack() }
    }

    protected open fun performBack() {
        if (!isFinishing) finish()
    }

    protected open fun showBottomBanner() {
        val container = binding.root.findViewById<FrameLayout>(R.id.bannerAdFrameVw) ?: return
        val shimmer = binding.root.findViewById<ShimmerFrameLayout>(R.id.bannerShimmerVw)
        PageSponsorSettings.showAd(this::class.java.simpleName, this, container, shimmer)

        binding.root.findViewById<View>(R.id.adBannerDividerVw)?.followAdContainer(container)
    }

    protected open fun initView() {}

    protected open fun initObservers() {}

    protected fun seekPermissionManaged(
        permission: String,
        launcher: ActivityResultLauncher<String>
    ) {
        val prefs = SecurePrefsKeeper(this)
        when {
            !prefs.hasRequestedPermission(permission) -> {
                prefs.markPermissionRequested(permission)
                launcher.launch(permission)
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission) ->
                launcher.launch(permission)
            // denied twice: stop asking, never send the user to Settings (R08)
            else -> Unit
        }
    }

    protected fun unfoldAppSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    private var pendingCallNumber: String? = null

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        trackPermissionResult(Manifest.permission.CALL_PHONE, granted)
        val number = pendingCallNumber
        pendingCallNumber = null
        if (number != null) if (granted) kickCall(number) else unfoldDialer(number)
    }

    protected fun placeCall(number: String) {
        if (number.isBlank()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            kickCall(number)
        } else {
            pendingCallNumber = number
            seekPermissionManaged(Manifest.permission.CALL_PHONE, callPermissionLauncher)
        }
    }

    private fun kickCall(number: String) {
        val placed = runCatching {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))); true
        }.getOrDefault(false)
        if (!placed) unfoldDialer(number)
    }

    private fun unfoldDialer(number: String) {
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    }

    override fun onResume() {
        super.onResume()
        imprintLocale()
        FullscreenOnAction.processTabReturn(this)

    }

    protected fun imprintLocale() = LanguageKeeper.imprintSaved(this)

    protected open fun imprintTheme(theme: String) {
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
