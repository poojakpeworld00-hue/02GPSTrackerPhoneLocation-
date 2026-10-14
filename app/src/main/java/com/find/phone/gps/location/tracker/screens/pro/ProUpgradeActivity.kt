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
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.billing.ProStoreBilling
import com.find.phone.gps.location.tracker.repo.billing.ProSettings
import com.find.phone.gps.location.tracker.repo.billing.ProTier
import com.find.phone.gps.location.tracker.databinding.CardPremiumPlanBinding
import com.find.phone.gps.location.tracker.databinding.PagePremiumBinding
import com.find.phone.gps.location.tracker.setupflow.HomeRoleJourney
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.helpers.unfoldPolicyLink
import com.find.phone.gps.location.tracker.helpers.unfoldTermLink
import kotlin.math.roundToInt

/**
 * The Premium paywall (design 10a · light map, plan columns). Plans come from Remote Config `premium`;
 * prices only from Play (localized): until Play has answered the prices read "…" and the CTA spins,
 * and if Play can't be reached the CTA turns into Retry. The chosen plan drives the CTA and the
 * renewal note. The close control (and Back) unlock after `close_delay_sec`.
 *
 * Opened [afterIntro] it is a first-run step: closing it — or buying — continues the onboarding
 * order exactly where the intro would have.
 */
class ProUpgradeActivity : FoundationActivity<PagePremiumBinding>() {

    override val layoutId: Int = R.layout.page_premium

    private lateinit var config: ProSettings
    private lateinit var rows: List<CardPremiumPlanBinding>
    private var plans: List<ProTier> = emptyList()
    private var selected = 0
    private var buying = false
    private var priceState = PriceState.LOADING
    private var priceAttempts = 0

    private enum class PriceState { LOADING, READY, FAILED }
    private var shine: ValueAnimator? = null

    override fun initView() {
        config = ProSettings.read(this)
        plans = config.plans.take(3)
        selected = plans.indexOfFirst { it.preselected }.coerceAtLeast(0)
        trackEvent(if (afterIntro) "premium_view_after_intro" else "premium_view")

        // The light map runs under the status bar (edge to edge on every API level): dark icons,
        // and the close sits below the bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, binding.root).isAppearanceLightStatusBars = true
        val closeTop = (binding.padClose.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        val bottomPad = binding.footerBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.premiumRootVw) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.padClose.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = closeTop + bars.top }
            binding.heroVw.updateLayoutParams { height = dp(300) + bars.top }
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
        binding.padClose.setOnClickListener { if (binding.padClose.closable) leave() }
        binding.padClose.start(config.closeDelaySec, config.closeButtonOpacity) {}
        binding.padContinueFree.paintFlags = binding.padContinueFree.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        // Shown and usable from the start: close_delay_sec paces only the X.
        binding.padContinueFree.setOnClickListener {
            trackEvent("premium_continue_free")
            leave()
        }

        // Back does nothing until the close control has appeared; then it leaves like the X.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.padClose.closable) leave()
            }
        })

        ProStoreBilling.onPurchaseResult = { onPurchase(it) }
        fetchPrices()
        kickShine()
    }

    private val afterIntro: Boolean get() = intent.getBooleanExtra(EXTRA_AFTER_INTRO, false)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    // ── Plans ─────────────────────────────────────────────────────────────

    private fun select(index: Int) {
        if (index == selected) return
        selected = index
        wirePlans()
    }

    private fun wirePlans() {
        plans.forEachIndexed { i, plan ->
            val row = rows[i]
            val on = i == selected
            // The selected plan fills with the CTA's gradient, turns its copy white and lifts.
            row.planCardVw.setBackgroundResource(if (on) R.drawable.vs_plan_on_r18 else R.drawable.vs_plan_off_r18)
            row.lblName.setTextColor(getColor(if (on) R.color.white else R.color.ds_ink))
            row.lblPrice.setTextColor(getColor(if (on) R.color.white else R.color.ds_ink))
            row.lblPeriod.setTextColor(if (on) getColor(R.color.white) and 0xD9FFFFFF.toInt() else getColor(R.color.ds_muted))
            row.planCardVw.elevation = if (on) dp(10).toFloat() else 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                row.planCardVw.outlineSpotShadowColor = getColor(R.color.ds_primary)
                row.planCardVw.outlineAmbientShadowColor = getColor(R.color.ds_primary)
            }
            val scale = if (on) SELECTED_SCALE else 1f
            row.root.animate().scaleX(scale).scaleY(scale).setDuration(180L).start()
            row.lblName.setText(nameOf(plan))
            // The monthly card reads per week (its Play price / 4); the CTA and the price line
            // below keep the amount actually billed each month.
            row.lblPrice.text = (if (plan.kind == ProTier.Kind.MONTHLY) ProStoreBilling.weeklyPrice(plan)
            else ProStoreBilling.price(plan)) ?: PRICE_PENDING
            row.lblPeriod.setText(periodOf(plan))
            row.lblTag.text = plan.tag
            row.lblTag.isVisible = plan.tag.isNotBlank()
        }
        val plan = plans.getOrNull(selected) ?: return
        val trial = trialOf(plan)
        wireConsent(
            when {
                trial > 0 -> R.string.ds_premium_consent_trial
                plan.isSubscription -> R.string.ds_premium_consent_subs
                else -> R.string.ds_premium_consent_lifetime
            }
        )
        val price = ProStoreBilling.price(plan)
        if (price == null) {
            // No Play price for this plan yet: a spinner while Play is asked, Retry once it failed.
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
            else -> getString(R.string.ds_premium_cta_lifetime, price)
        }
        binding.lblPriceLine.text = when (plan.kind) {
            ProTier.Kind.WEEKLY -> if (trial > 0) getString(R.string.ds_premium_price_trial_week, price, trial)
            else getString(R.string.ds_premium_price_week, price)
            ProTier.Kind.MONTHLY -> if (trial > 0) getString(R.string.ds_premium_price_trial_month, price, trial)
            else getString(R.string.ds_premium_price_month, price)
            ProTier.Kind.LIFETIME -> getString(R.string.ds_premium_price_lifetime, price)
        }
    }

    /**
     * Asks Play for the plans' localized prices (usually already in memory from app start).
     * Retries twice on its own; after that the CTA offers Retry.
     */
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

    /**
     * Free-trial days for [plan]: what Play offers this user once it has answered (0 = none,
     * e.g. already used), until then the Remote Config / default value.
     */
    private fun trialOf(plan: ProTier): Int = ProStoreBilling.trialDays(plan) ?: plan.fallbackTrialDays

    /** "By continuing you agree to our Terms & Privacy. <tail>" with both words as links. */
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
        ProTier.Kind.LIFETIME -> R.string.ds_premium_lifetime
    }

    private fun periodOf(plan: ProTier) = when (plan.kind) {
        ProTier.Kind.WEEKLY -> R.string.ds_premium_per_week
        ProTier.Kind.MONTHLY -> R.string.ds_premium_per_week_billed_monthly
        ProTier.Kind.LIFETIME -> R.string.ds_premium_one_time
    }

    // ── Purchase / restore ────────────────────────────────────────────────

    private fun buy() {
        if (buying) return
        val plan = plans.getOrNull(selected) ?: return
        if (ProStoreBilling.price(plan) == null) {
            // Never sell without Play's price on screen: still loading → wait, failed → ask again.
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
                    // Play couldn't be asked: say so rather than "nothing found".
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

    /** Leaves the paywall: on to the next first-run step after the intro, else just back. */
    private fun leave() {
        if (isFinishing) return
        if (!afterIntro) return finish()
        if (HomeRoleJourney.isOnboardingActive(this)) HomeRoleJourney.advance(this) else HomeRoleJourney.goHome(this)
    }

    // ── Shine ─────────────────────────────────────────────────────────────

    /** The light streak sweeping across the CTA every 3 s (design `shine`). */
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

        /** How much the selected plan card grows over the other two. */
        private const val SELECTED_SCALE = 1.05f

        /** A plan's price while Play hasn't answered yet. */
        private const val PRICE_PENDING = "…"

        /** Price loads tried before the CTA turns into Retry, and the step between them. */
        private const val PRICE_AUTO_ATTEMPTS = 3
        private const val PRICE_RETRY_MS = 1_500L

        /** Whether the paywall should follow the intro: switched on, and the user isn't premium. */
        fun showsAfterIntro(context: Context): Boolean {
            val config = ProSettings.read(context)
            return config.enabled && config.showAfterIntro && !ProStoreBilling.isPremium(context)
        }

        /** The paywall as the step after the intro; [onboarding] carries the first-run marker. */
        fun afterIntro(context: Context, onboarding: Boolean): Intent {
            val intent = if (onboarding) HomeRoleJourney.onboardingIntent(context, ProUpgradeActivity::class.java)
            else Intent(context, ProUpgradeActivity::class.java)
            return intent.putExtra(EXTRA_AFTER_INTRO, true)
        }

        fun intent(context: Context): Intent = Intent(context, ProUpgradeActivity::class.java)
    }
}
