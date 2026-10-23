package com.find.phone.gps.location.tracker.screens.finder

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.engine.PageSponsorSettings
import com.find.phone.gps.location.tracker.monetize.render.InlineSponsor
import com.find.phone.gps.location.tracker.monetize.render.InlineSponsorStrip
import com.find.phone.gps.location.tracker.monetize.render.BonusAdSponsor
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.finder.ZoneGeocoder
import com.find.phone.gps.location.tracker.repo.finder.ZoneAccuracy
import com.find.phone.gps.location.tracker.repo.finder.MapMarkerPoint
import com.find.phone.gps.location.tracker.repo.finder.NumberFinder
import com.find.phone.gps.location.tracker.repo.finder.NumberSummary
import com.find.phone.gps.location.tracker.repo.billing.ProStoreBilling
import com.find.phone.gps.location.tracker.databinding.CardResultCellBinding
import com.find.phone.gps.location.tracker.databinding.PageLocatingBinding
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.dashboard.PastLookup
import com.find.phone.gps.location.tracker.screens.dashboard.PastLookupStore
import com.find.phone.gps.location.tracker.screens.pro.ProUpgradeActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import kotlin.math.roundToInt

class PinpointingActivity : FoundationActivity<PageLocatingBinding>() {

    override val layoutId: Int = R.layout.page_locating

    private lateinit var search: PastLookup
    private var saved: NumberSummary? = null
    private var alreadyUnlocked = false
    private var report: NumberSummary? = null
    private var overlay: ZoneRippleOverlay? = null
    private val animators = mutableListOf<Animator>()
    private var progressAnim: ObjectAnimator? = null
    private var watching = false

    private val premiumPage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val result = report ?: return@registerForActivityResult
        if (ProStoreBilling.isPremium(this)) unlock(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        OpenStreetTiles.init(this)
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        val digits = intent.getStringExtra(EXTRA_DIGITS)
        val iso = intent.getStringExtra(EXTRA_ISO)
        val dial = intent.getStringExtra(EXTRA_DIAL)
        if (digits.isNullOrEmpty() || iso.isNullOrEmpty() || dial.isNullOrEmpty()) {
            finish()
            return
        }
        search = PastLookup(digits, iso, dial, System.currentTimeMillis())
        saved = IntentCompat.getSerializableExtra(intent, EXTRA_REPORT, NumberSummary::class.java)
        alreadyUnlocked = intent.getBooleanExtra(EXTRA_UNLOCKED, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.locRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }

        binding.lblSearchNumber.text = search.display
        binding.padBack.setOnClickListener { goBack() }
        binding.padNotNow.setOnClickListener { finish() }
        VisualTweens.pressScale(binding.padWatchAd)
        binding.padWatchAd.setOnClickListener { reveal() }
        binding.padSkipPro.setOnClickListener {
            if (report == null) return@setOnClickListener
            trackEvent("result_skip_pro")
            premiumPage.launch(ProUpgradeActivity.intent(this))
        }
        binding.mapCardVw.clipToOutline = true
        OpenStreetTiles.style(binding.bigMapVw, interactive = false)

        val premium = ProStoreBilling.isPremium(this)
        if (!premium) {
            BonusAdSponsor.preload(this)
            presentNative()
        }

        val given = saved
        if (given != null) {
            binding.mapCardVw.updateLayoutParams { height = dp(MAP_RESULT_DP) }
            lifecycleScope.launch { placeArea(given, animate = false) }
            presentResult(given)
        } else {
            binding.flagCountry.text = RegionZones.byIso(search.iso2)?.name ?: search.iso2
            binding.flagState.setText(R.string.ds_step_state)
            locate(if (premium) MIN_SEARCH_PREMIUM_MS else MIN_SEARCH_MS)
        }
    }

    private enum class Step { COUNTRY, STATE, AREA }

    private fun wireSteps(current: Step) {
        val steps = listOf(
            Triple(binding.stepCountryVw, binding.flagCountry, R.string.ds_step_country),
            Triple(binding.stepStateVw, binding.flagState, R.string.ds_step_state),
            Triple(binding.stepAreaVw, binding.flagArea, R.string.ds_step_area),
        )
        steps.forEachIndexed { index, (step, chip, label) ->
            val name = getString(label)
            when {
                index < current.ordinal -> {
                    step.text = getString(R.string.ds_step_done, name)
                    step.setBackgroundResource(R.drawable.vs_tile_success12_r10)
                    step.setTextColor(ContextCompat.getColor(this, R.color.ds_success))
                    chip.setBackgroundResource(R.drawable.vs_tile_primary_r10)
                    chip.setTextColor(ContextCompat.getColor(this, R.color.white))
                }
                index == current.ordinal -> {
                    step.text = getString(R.string.ds_step_active, name)
                    step.setBackgroundResource(R.drawable.vs_tile_tint_r10)
                    step.setTextColor(ContextCompat.getColor(this, R.color.ds_primary))
                    chip.setBackgroundResource(R.drawable.vs_tile_white_r10)
                    chip.setTextColor(ContextCompat.getColor(this, R.color.ds_primary))
                }
                else -> {
                    step.text = name
                    step.setBackgroundResource(R.drawable.vs_tile_gray_r10)
                    step.setTextColor(ContextCompat.getColor(this, R.color.ds_placeholder))
                    chip.setBackgroundResource(R.drawable.vs_tile_white70_r10)
                    chip.setTextColor(ContextCompat.getColor(this, R.color.ds_placeholder))
                }
            }
        }
    }

    private val nativeType: String by lazy {
        PageSponsorSettings.resolve(this, SCREEN_KEY).nativeType.trim().lowercase()
    }

    private val compactNative: Boolean
        get() = nativeType == "small" || nativeType == "native_banner" || nativeType == "nativebanner"

    private fun presentNative() {
        binding.adFrameVw.isVisible = true
        if (nativeReady()) return paintNative()
        if (compactNative) {
            InlineSponsorStrip().fetchNativeBannerAds(this)
        } else {
            InlineSponsor().fetchNativeADs(this, object : InlineSponsor.InlineAdCallback {
                override fun onNativeAdLoaded() = runOnUiThread { populateNativeIfEmpty() }
                override fun onNativeAdFailed() = Unit
            })
        }

        NATIVE_RECHECK_MS.forEach { binding.adFrameVw.postDelayed({ populateNativeIfEmpty() }, it) }
    }

    private fun nativeReady(): Boolean =
        if (compactNative) InlineSponsorStrip.hasPreloadedNativeBanner() else InlineSponsor.hasPreloadedNative()

    private fun populateNativeIfEmpty() {
        if (isFinishing || isDestroyed || binding.adFrameVw.childCount > 0) return
        if (nativeReady()) paintNative()
    }

    private fun paintNative() {
        when {
            compactNative -> InlineSponsorStrip().presentNativeBannerNative(this, binding.adFrameVw)
            nativeType == "big" -> InlineSponsor().presentBigNative(this, binding.adFrameVw)
            nativeType == "mid" -> InlineSponsor().presentMidNative(this, binding.adFrameVw)
            else -> InlineSponsor().presentMidNative2(this, binding.adFrameVw)
        }
    }

    private fun locate(minMs: Long) = lifecycleScope.launch {
        val started = SystemClock.elapsedRealtime()
        fun at(ms: Long) = (started + ms - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

        val countryName = RegionZones.byIso(search.iso2)?.name ?: search.iso2
        val lookup = async { NumberFinder.run(this@PinpointingActivity, search.digits, search.iso2, search.dial) }

        stage(getString(R.string.ds_locating_country, countryName), toPercent = 30, overMs = minMs * 3 / 10, step = Step.COUNTRY)
        ZoneGeocoder.locate(this@PinpointingActivity, null, null, null, countryName)?.let { pin ->
            val point = GeoPoint(pin.lat, pin.lng)
            binding.bigMapVw.controller.setZoom(ZoneAccuracy.COUNTRY.zoom - 2)
            binding.bigMapVw.controller.setCenter(point)
            binding.bigMapVw.controller.animateTo(point, ZoneAccuracy.COUNTRY.zoom, FLY_MS)
        }
        delay(at(minMs * 3 / 10))

        val result = lookup.await()
        if (!result.serverReached) {
            showLookupFailed()
            return@launch
        }
        report = result
        onFound(result)

        val state = result.state?.takeIf { it.isNotBlank() }
        val statePin = state?.let { ZoneGeocoder.locate(this@PinpointingActivity, null, null, it, result.countryName) }
        if (state != null && statePin != null) {
            binding.flagState.text = state
            stage(getString(R.string.ds_locating_state, state), toPercent = 70, overMs = minMs * 4 / 10, step = Step.STATE)
            binding.bigMapVw.controller.animateTo(GeoPoint(statePin.lat, statePin.lng), ZoneAccuracy.STATE.zoom - 1, FLY_MS)
            delay(at(minMs * 7 / 10))
        }

        stage(getString(R.string.ds_locating_found, state ?: result.countryName), toPercent = 100, overMs = at(minMs), step = Step.AREA)
        placeArea(result, animate = true, knownState = statePin)
        delay(at(minMs))

        presentResult(result)
    }

    private fun showLookupFailed() {
        if (isFinishing || isDestroyed) return
        progressAnim?.cancel()
        LookupFailedSheet(
            this,
            onRetry = { restartLookup() },
            onCancel = { finish() },
        ).show()
    }

    private fun restartLookup() {
        if (isFinishing || isDestroyed) return
        progressAnim?.cancel()
        binding.prog.progress = 0
        binding.lblPercent.text = getString(R.string.ds_locating_percent, 0)
        binding.flagState.setText(R.string.ds_step_state)
        locate(if (ProStoreBilling.isPremium(this)) MIN_SEARCH_PREMIUM_MS else MIN_SEARCH_MS)
    }

    private fun stage(text: String, toPercent: Int, overMs: Long, step: Step) {
        binding.lblStage.text = text
        wireSteps(step)
        progressAnim?.cancel()
        progressAnim = ObjectAnimator.ofInt(binding.prog, "progress", binding.prog.progress, toPercent * 10).apply {
            duration = overMs.coerceAtLeast(300L)
            interpolator = LinearInterpolator()
            addUpdateListener { binding.lblPercent.text = getString(R.string.ds_locating_percent, binding.prog.progress / 10) }
            start()
        }
        animators += progressAnim!!
    }

    private suspend fun placeArea(result: NumberSummary, animate: Boolean, knownState: MapMarkerPoint? = null) {
        val state = result.state?.takeIf { it.isNotBlank() }
        val own = result.pin
        val pin = own ?: knownState ?: ZoneGeocoder.locate(this, null, null, state, result.countryName) ?: return
        if (isFinishing || isDestroyed) return
        val precision = when {
            own != null -> result.precision
            state != null -> ZoneAccuracy.STATE
            else -> ZoneAccuracy.COUNTRY
        }

        val zoom = OpenStreetTiles.cardZoom(precision)
        if (animate) {
            binding.bigMapVw.controller.animateTo(GeoPoint(pin.lat, pin.lng), zoom, FLY_MS)
            delay(FLY_MS)
            if (isFinishing || isDestroyed) return
        }
        overlay?.let { it.stop(); binding.bigMapVw.overlays.remove(it) }
        overlay = OpenStreetTiles.placeAt(binding.bigMapVw, pin, precision, zoom)
    }

    private fun onFound(result: NumberSummary) {
        PastLookupStore(this).add(search.copy(searchedAt = System.currentTimeMillis(), report = result, unlocked = alreadyUnlocked))
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_FOUND, true))
    }

    private fun presentResult(result: NumberSummary) {
        report = result
        if (alreadyUnlocked || ProStoreBilling.isPremium(this)) return unlock(result)

        binding.lblTitle.setText(R.string.ds_location_found)
        binding.lblSearchNumber.setText(R.string.ds_locating_complete)
        binding.lblSearchNumber.setTextColor(ContextCompat.getColor(this, R.color.ds_success))
        binding.livePillVw.isVisible = false

        binding.scanGroupVw.isVisible = false
        binding.mapChipsVw.isVisible = false
        binding.searchingGroupVw.isVisible = false
        binding.lblAreaPill.isVisible = true
        shrinkMap()

        binding.lblCountryTile.text = search.iso2.uppercase()
        binding.nameRowVw.isVisible = !result.name.isNullOrBlank()
        binding.lblName.text = result.name
        binding.lblName.setBlurred(true)
        binding.lblNumber.text = result.international
        binding.lblRegion.text = listOfNotNull(result.state, result.countryName).distinct().joinToString(", ")
        if (result.valid) {
            binding.lblValidity.setText(R.string.ds_result_valid_short)
            binding.lblValidity.setBackgroundResource(R.drawable.vs_pill_success_soft)
            binding.lblValidity.setTextColor(ContextCompat.getColor(this, R.color.ds_success))
        } else {
            binding.lblValidity.setText(R.string.ds_verdict_unverified)
            binding.lblValidity.setBackgroundResource(R.drawable.vs_pill_warn)
            binding.lblValidity.setTextColor(ContextCompat.getColor(this, R.color.ds_warn))
        }

        cell(binding.cellCountryVw, R.string.ds_label_country, result.countryName, locked = false)
        cell(binding.cellLineVw, R.string.ds_fact_line, result.lineType.label(this), locked = false)
        cell(binding.cellCityVw, R.string.ds_label_city, result.city, locked = true)
        cell(binding.cellCarrierVw, R.string.ds_fact_carrier, result.carrier, locked = true)

        binding.resultGroupVw.alpha = 0f
        binding.resultGroupVw.translationY = dp(40).toFloat()
        binding.resultGroupVw.isVisible = true
        binding.resultGroupVw.animate().alpha(1f).translationY(0f).setDuration(450)
            .setInterpolator(VisualTweens.EASE_IN_OUT).start()
    }

    private fun shrinkMap() {
        val from = binding.mapCardVw.height.takeIf { it > 0 } ?: return
        val to = dp(MAP_RESULT_DP)
        if (from <= to) return
        val center = binding.bigMapVw.mapCenter
        ValueAnimator.ofInt(from, to).apply {
            duration = 400L
            interpolator = VisualTweens.EASE_IN_OUT
            addUpdateListener {
                binding.mapCardVw.updateLayoutParams { height = it.animatedValue as Int }
                binding.bigMapVw.controller.setCenter(center)
            }
            start()
        }.also { animators += it }
    }

    private fun cell(cell: CardResultCellBinding, @StringRes label: Int, value: String?, locked: Boolean) {
        cell.lblLabel.setText(label)
        val known = !value.isNullOrBlank()
        cell.lblValue.text = if (known) value else getString(R.string.ds_not_available)
        cell.lblValue.setTextColor(ContextCompat.getColor(this, if (known) R.color.ds_ink else R.color.ds_muted))
        cell.lblValue.setBlurred(locked && known)
        cell.picLock.isVisible = locked && known
    }

    private fun reveal() {
        val result = report ?: return
        if (watching) return
        watching = true
        binding.lblWatchAd.isInvisible = true
        binding.pbWatchAdVw.isVisible = true
        BonusAdSponsor().show(this) { outcome ->
            watching = false
            binding.lblWatchAd.isInvisible = false
            binding.pbWatchAdVw.isVisible = false
            when (outcome) {
                BonusAdSponsor.Result.EARNED -> unlock(result)
                BonusAdSponsor.Result.NOT_EARNED ->
                    Toast.makeText(this, R.string.ds_reward_not_earned, Toast.LENGTH_SHORT).show()
                BonusAdSponsor.Result.CANCELED -> Unit
            }
        }
    }

    private fun unlock(result: NumberSummary) {
        if (isFinishing) return
        PastLookupStore(this).add(search.copy(searchedAt = System.currentTimeMillis(), report = result, unlocked = true))
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_FOUND, true))
        startActivity(NumberInsightActivity.intent(this, result))
        finish()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onResume() {
        super.onResume()
        if (::search.isInitialized) binding.bigMapVw.onResume()
    }

    override fun onPause() {
        if (::search.isInitialized) binding.bigMapVw.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        VisualTweens.cancelAll(animators)
        overlay?.stop()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_DIGITS = "digits"
        private const val EXTRA_ISO = "iso"
        private const val EXTRA_DIAL = "dial"
        private const val EXTRA_REPORT = "report"
        private const val EXTRA_UNLOCKED = "unlocked"

        const val EXTRA_FOUND = "found"

        private const val MIN_SEARCH_MS = 10_000L

        private const val MIN_SEARCH_PREMIUM_MS = 4_000L

        private val NATIVE_RECHECK_MS = longArrayOf(2_500L, 5_000L, 8_000L)

        private const val SCREEN_KEY = "PinpointingActivity"

        private const val FLY_MS = 1_800L

        private const val MAP_RESULT_DP = 150

        fun intent(context: Context, search: PastLookup, saved: NumberSummary?, unlocked: Boolean): Intent =
            Intent(context, PinpointingActivity::class.java)
                .putExtra(EXTRA_DIGITS, search.digits)
                .putExtra(EXTRA_ISO, search.iso2)
                .putExtra(EXTRA_DIAL, search.dial)
                .putExtra(EXTRA_UNLOCKED, unlocked)
                .apply { if (saved != null) putExtra(EXTRA_REPORT, saved) }
    }
}
