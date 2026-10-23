package com.find.phone.gps.location.tracker.screens.pro

import android.graphics.Paint
import android.os.Build
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.billing.ProStoreBilling
import com.find.phone.gps.location.tracker.repo.billing.ProSettings
import com.find.phone.gps.location.tracker.repo.billing.ProTier
import com.find.phone.gps.location.tracker.databinding.CardPremiumPlanBinding
import com.find.phone.gps.location.tracker.databinding.PagePremiumBinding
import com.find.phone.gps.location.tracker.setupflow.HomeRoleJourney
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.helpers.isNightMode
import com.find.phone.gps.location.tracker.helpers.unfoldPolicyLink
import com.find.phone.gps.location.tracker.helpers.unfoldTermLink
import kotlin.math.roundToInt

class ProUpgradeActivity : FoundationActivity<PagePremiumBinding>() {

    override val layoutId: Int = R.layout.page_premium

    private lateinit var config: ProSettings
    private lateinit var rows: List<CardPremiumPlanBinding>
    private var plans: List<ProTier> = emptyList()
    private var selected = 0
    private var buying = false
    private var leaving = false
    private var priceState = PriceState.LOADING
    private var priceAttempts = 0

    private enum class PriceState { LOADING, READY, FAILED }
    private var shine: ValueAnimator? = null

    override fun initView() {
        config = ProSettings.read(this)
        // onboarding shows one plan (weekly + trial); the in-app page keeps all three
        plans = if (afterIntro) config.plans.filter { it.kind == ProTier.Kind.WEEKLY }.take(1).ifEmpty { config.plans.take(1) }
        else config.plans.take(3)
        selected = plans.indexOfFirst { it.preselected }.coerceAtLeast(0)
        trackEvent(if (afterIntro) "premium_view_after_intro" else "premium_view")

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, binding.root).isAppearanceLightStatusBars = !isNightMode(this)
        binding.scrl.isVisible = !afterIntro
        binding.singleVw.isVisible = afterIntro
        binding.padContinueFree.isVisible = !afterIntro
        val closeView = if (afterIntro) binding.padCloseSingle else binding.padClose
        val closeTop = (binding.padClose.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        val singleTop = binding.singleVw.paddingTop
        val bottomPad = binding.footerBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.premiumRootVw) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.padClose.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = closeTop + bars.top }
            binding.heroVw.updateLayoutParams { height = dp(300) + bars.top }
            binding.singleVw.updatePadding(top = singleTop + bars.top)
            binding.footerBar.updatePadding(bottom = bottomPad + bars.bottom)
            binding.premiumRootVw.updatePadding(left = bars.left, right = bars.right)
            insets
        }

        rows = listOf(binding.plan1Vw, binding.plan2Vw, binding.plan3Vw)
        rows.forEachIndexed { i, row ->
            row.root.isVisible = i < plans.size
            row.planCardVw.setOnClickListener { select(i) }
        }
        wirePlans()

        VisualTweens.pressScale(binding.padContinue)
        binding.padContinue.clipToOutline = true
        binding.padContinue.setOnClickListener { buy() }
        binding.padRestore.setOnClickListener { restore() }
        binding.padTerms.setOnClickListener { unfoldTermLink() }
        binding.padPrivacy.setOnClickListener { unfoldPolicyLink() }
        closeView.setOnClickListener { if (closeView.closable) leave(withInter = true) }
        closeView.start(config.closeDelaySec, config.closeButtonOpacity) {}
        binding.padContinueFree.paintFlags = binding.padContinueFree.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        binding.padContinueFree.setOnClickListener {
            trackEvent("premium_continue_free")
            leave(withInter = true)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (closeView.closable) leave(withInter = true)
            }
        })

        ProStoreBilling.onPurchaseResult = { onPurchase(it) }
        fetchPrices()
        kickShine()
    }

    // opened by onboarding: after the intro slides, or as the `premium` step of onboarding.order
    private val afterIntro: Boolean
        get() = intent.getBooleanExtra(EXTRA_AFTER_INTRO, false) || HomeRoleJourney.isOnboarding(this)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun select(index: Int) {
        if (index == selected) return
        selected = index
        wirePlans()
    }

    private fun wirePlans() {
        plans.forEachIndexed { i, plan ->
            val row = rows[i]
            val on = i == selected

            row.planCardVw.setBackgroundResource(if (on) R.drawable.pw_plan_on_r20 else R.drawable.pw_plan_off_r20)
            row.lblName.setTextColor(getColor(if (on) R.color.pw_plan_name_on else R.color.ds_muted))
            row.lblPrice.setTextColor(getColor(if (on) R.color.pw_plan_price_on else R.color.ds_ink))
            row.lblPeriod.setTextColor(getColor(R.color.ds_muted))
            row.planCheckVw.isVisible = on
            row.planCardVw.elevation = if (on) dp(6).toFloat() else 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                row.planCardVw.outlineSpotShadowColor = getColor(R.color.pw_plan_on_stroke)
                row.planCardVw.outlineAmbientShadowColor = getColor(R.color.pw_plan_on_stroke)
            }
            val scale = if (on) SELECTED_SCALE else 1f
            row.root.animate().scaleX(scale).scaleY(scale).setDuration(180L).start()
            row.lblName.setText(nameOf(plan))

            row.lblPrice.text = (if (plan.kind == ProTier.Kind.MONTHLY) ProStoreBilling.weeklyPrice(plan)
            else ProStoreBilling.price(plan)) ?: PRICE_PENDING
            row.lblPeriod.setText(periodOf(plan))
            row.lblTag.text = plan.tag
            row.lblTag.isVisible = plan.tag.isNotBlank()
        }
        val plan = plans.getOrNull(selected) ?: return
        val trial = trialOf(plan)
        binding.lblSingleTrial.isVisible = trial > 0
        if (trial > 0) binding.lblSingleTrial.text = getString(R.string.ds_premium_cta_trial, trial)
        wireConsent(
            when {
                trial > 0 -> R.string.ds_premium_consent_trial
                else -> R.string.ds_premium_consent_subs
            }
        )
        val price = ProStoreBilling.price(plan)
        if (price == null) {

            val failed = priceState == PriceState.FAILED
            binding.lblPriceLine.text = if (failed) getString(R.string.ds_premium_unavailable) else ""
            binding.lblCta.text = getString(R.string.ds_retry)
            if (!buying) {
                binding.lblCta.isInvisible = !failed
                binding.pbCtaVw.isVisible = !failed
            }
            return
        }
        if (!buying) {
            binding.lblCta.isInvisible = false
            binding.pbCtaVw.isVisible = false
        }
        binding.lblCta.text = when {
            trial > 0 -> getString(R.string.ds_premium_cta_trial, trial)
            plan.kind == ProTier.Kind.WEEKLY -> getString(R.string.ds_premium_cta_week, price)
            plan.kind == ProTier.Kind.MONTHLY -> getString(R.string.ds_premium_cta_month, price)
            else -> getString(R.string.ds_premium_cta_year, price)
        }
        binding.lblPriceLine.text = when (plan.kind) {
            ProTier.Kind.WEEKLY -> if (trial > 0) getString(R.string.ds_premium_price_trial_week, price, trial)
            else getString(R.string.ds_premium_price_week, price)
            ProTier.Kind.MONTHLY -> if (trial > 0) getString(R.string.ds_premium_price_trial_month, price, trial)
            else getString(R.string.ds_premium_price_month, price)
            ProTier.Kind.YEARLY -> if (trial > 0) getString(R.string.ds_premium_price_trial_year, price, trial)
            else getString(R.string.ds_premium_price_year, price)
        }
    }

    private fun fetchPrices() {
        priceState = PriceState.LOADING
        wirePlans()
        ProStoreBilling.fetchDetails(this, plans) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                priceAttempts++
                priceState = when {
                    plans.all { ProStoreBilling.price(it) != null } -> PriceState.READY
                    priceAttempts < PRICE_AUTO_ATTEMPTS -> {
                        binding.root.postDelayed({ if (!isFinishing && !isDestroyed) fetchPrices() }, PRICE_RETRY_MS * priceAttempts)
                        PriceState.LOADING
                    }
                    else -> PriceState.FAILED
                }
                if (priceState == PriceState.FAILED) trackEvent("premium_price_unavailable")
                wirePlans()
            }
        }
    }

    private fun trialOf(plan: ProTier): Int = ProStoreBilling.trialDays(plan) ?: 0

    private fun wireConsent(tail: Int) {
        val terms = getString(R.string.ds_premium_terms)
        val privacy = getString(R.string.ds_premium_privacy)
        val text = getString(R.string.ds_premium_consent, terms, privacy) + " " + getString(tail)
        val spannable = SpannableString(text)
        fun link(word: String, open: () -> Unit) {
            val start = text.indexOf(word)
            if (start < 0) return
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = open()
            }, start, start + word.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        link(terms) { unfoldTermLink() }
        link(privacy) { unfoldPolicyLink() }
        binding.lblNote.text = spannable
        binding.lblNote.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun nameOf(plan: ProTier) = when (plan.kind) {
        ProTier.Kind.WEEKLY -> R.string.ds_premium_week
        ProTier.Kind.MONTHLY -> R.string.ds_premium_month
        ProTier.Kind.YEARLY -> R.string.ds_premium_year
    }

    private fun periodOf(plan: ProTier) = when (plan.kind) {
        ProTier.Kind.WEEKLY -> R.string.ds_premium_per_week
        ProTier.Kind.MONTHLY -> R.string.ds_premium_per_week_billed_monthly
        ProTier.Kind.YEARLY -> R.string.ds_premium_per_year
    }

    private fun buy() {
        if (buying) return
        val plan = plans.getOrNull(selected) ?: return
        if (ProStoreBilling.price(plan) == null) {

            if (priceState == PriceState.FAILED) {
                priceAttempts = 0
                fetchPrices()
            }
            return
        }
        trackEvent("premium_cta_${plan.kind.name.lowercase()}")
        if (!ProStoreBilling.purchase(this, plan)) {
            Toast.makeText(this, R.string.ds_premium_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        setBuying(true)
    }

    private fun onPurchase(outcome: ProStoreBilling.PurchaseOutcome) {
        setBuying(false)
        when (outcome) {
            ProStoreBilling.PurchaseOutcome.PURCHASED -> {
                trackEvent("premium_purchased")
                Toast.makeText(this, R.string.ds_premium_welcome, Toast.LENGTH_SHORT).show()
                leave()
            }
            ProStoreBilling.PurchaseOutcome.PENDING -> Toast.makeText(this, R.string.ds_premium_pending, Toast.LENGTH_LONG).show()
            ProStoreBilling.PurchaseOutcome.FAILED -> Toast.makeText(this, R.string.ds_premium_failed, Toast.LENGTH_SHORT).show()
            ProStoreBilling.PurchaseOutcome.CANCELED -> Unit
        }
    }

    private fun restore() {
        binding.padRestore.isEnabled = false
        ProStoreBilling.sync(this) { active ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.padRestore.isEnabled = true
                when (active) {
                    true -> {
                        Toast.makeText(this, R.string.ds_premium_restored, Toast.LENGTH_SHORT).show()
                        leave()
                    }
                    false -> Toast.makeText(this, R.string.ds_premium_nothing_to_restore, Toast.LENGTH_SHORT).show()

                    null -> Toast.makeText(this, R.string.ds_premium_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setBuying(on: Boolean) {
        buying = on
        binding.lblCta.isInvisible = on
        binding.pbCtaVw.isVisible = on
    }

    // withInter: dismissed without buying → RC onboarding.premium decides whether an interstitial runs first
    private fun leave(withInter: Boolean = false) {
        if (isFinishing || leaving) return
        if (!afterIntro) return finish()
        val next = { if (HomeRoleJourney.isOnboardingActive(this)) HomeRoleJourney.advance(this) else HomeRoleJourney.goHome(this) }
        if (!withInter) return next()
        leaving = true
        HomeAdsSettings.runOnboardingInter(this, HomeAdsSettings.OnboardScreen.PREMIUM) { next() }
    }

    private fun kickShine() {
        if (VisualTweens.animationsDisabled(this)) {
            binding.shineVw.isVisible = false
            return
        }
        binding.padContinue.post {
            val w = binding.padContinue.width.toFloat()
            shine = ObjectAnimator.ofFloat(binding.shineVw, View.TRANSLATION_X, -0.3f * w, 1.1f * w).apply {
                duration = 1_800L
                startDelay = 400L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    override fun onDestroy() {
        shine?.cancel()
        if (ProStoreBilling.onPurchaseResult != null) ProStoreBilling.onPurchaseResult = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_AFTER_INTRO = "after_intro"

        private const val SELECTED_SCALE = 1.05f

        private const val PRICE_PENDING = "…"

        private const val PRICE_AUTO_ATTEMPTS = 3
        private const val PRICE_RETRY_MS = 1_500L

        fun showsAfterIntro(context: Context): Boolean =
            ProSettings.read(context).showAfterIntro && offeredInWelcome(context)

        fun offeredInWelcome(context: Context): Boolean =
            ProSettings.read(context).enabled && !ProStoreBilling.isPremium(context)

        fun afterIntro(context: Context, onboarding: Boolean): Intent {
            val intent = if (onboarding) HomeRoleJourney.onboardingIntent(context, ProUpgradeActivity::class.java)
            else Intent(context, ProUpgradeActivity::class.java)
            return intent.putExtra(EXTRA_AFTER_INTRO, true)
        }

        fun intent(context: Context): Intent = Intent(context, ProUpgradeActivity::class.java)
    }
}
