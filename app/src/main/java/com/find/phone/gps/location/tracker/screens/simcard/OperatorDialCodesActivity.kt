package com.find.phone.gps.location.tracker.screens.simcard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.simcard.OwnSimLines
import com.find.phone.gps.location.tracker.databinding.CardCarrierCodeBinding
import com.find.phone.gps.location.tracker.databinding.PageCarrierCodesBinding
import com.find.phone.gps.location.tracker.helpers.isNightMode

class OperatorDialCodesActivity : FoundationActivity<PageCarrierCodesBinding>() {

    override val layoutId: Int = R.layout.page_carrier_codes

    private data class SimSlotTab(val slot: Int, val carrier: String)

    private var tabs: List<SimSlotTab> = emptyList()
    private var selected = 0

    override fun initView() {

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isNightMode(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.codesRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        binding.padBack.setOnClickListener { goBack() }

        tabs = OwnSimLines.read(this).map { SimSlotTab(it.slot, it.carrier) }
        val wantSlot = intent.getIntExtra(EXTRA_SLOT, -1)
        selected = tabs.indexOfFirst { it.slot == wantSlot }.coerceAtLeast(0)

        composeTabs()
        render()
    }

    private fun composeTabs() {
        binding.carrierTabsVw.removeAllViews()
        if (tabs.size < 2) {
            binding.carrierTabsVw.visibility = View.GONE
            return
        }
        tabs.forEachIndexed { i, tab ->
            val view = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).also { if (i > 0) it.marginStart = dp(4) }
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 13f
                text = tab.carrier
                setOnClickListener { select(i) }
            }
            binding.carrierTabsVw.addView(view)
        }
        styleTabs()
    }

    private fun styleTabs() {
        for (i in 0 until binding.carrierTabsVw.childCount) {
            val tab = binding.carrierTabsVw.getChildAt(i) as TextView
            val on = i == selected
            tab.setTextColor(ContextCompat.getColor(this, if (on) R.color.ds_primary else R.color.ds_muted))
            tab.typeface = ResourcesCompat.getFont(this, if (on) R.font.figtree_bold else R.font.figtree_semibold)
            tab.background = if (on) ContextCompat.getDrawable(this, R.drawable.vs_seg_thumb_r10) else null
            tab.elevation = if (on) dp(2).toFloat() else 0f
        }
    }

    private fun select(i: Int) {
        if (i == selected) return
        selected = i
        styleTabs()
        render()
    }

    private fun render() {
        val tab = tabs.getOrNull(selected)
        binding.lblDetected.text = if (tab != null)
            getString(R.string.carrier_codes_detected_fmt, tab.carrier, tab.slot)
        else getString(R.string.carrier_codes_title)

        binding.sectionsVw.removeAllViews()
        val inflater = LayoutInflater.from(this)
        OperatorDialCodes.forCarrier(tab?.carrier).forEachIndexed { index, section ->
            binding.sectionsVw.addView(sectionLabel(section.title, index == 0))
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.vs_card_border_r20)
            }
            section.codes.forEachIndexed { i, code ->
                if (i > 0) card.addView(divider())
                card.addView(codeRow(inflater, card, code))
            }
            binding.sectionsVw.addView(card)
        }
    }

    private fun sectionLabel(title: String, first: Boolean): TextView = TextView(this).apply {
        text = title
        isAllCaps = true
        letterSpacing = 0.12f
        typeface = ResourcesCompat.getFont(this@OperatorDialCodesActivity, R.font.nunito_extrabold)
        textSize = 11f
        setTextColor(ContextCompat.getColor(this@OperatorDialCodesActivity, R.color.ds_muted))
        setPadding(dp(6), dp(if (first) 4 else 16), dp(6), dp(8))
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.marginStart = dp(14); it.marginEnd = dp(14)
        }
        setBackgroundColor(ContextCompat.getColor(this@OperatorDialCodesActivity, R.color.ds_divider))
    }

    private fun codeRow(inflater: LayoutInflater, parent: LinearLayout, code: OperatorDialCodes.Code): View =
        CardCarrierCodeBinding.inflate(inflater, parent, false).apply {
            lblTitle.text = code.title
            lblSubtitle.text = code.subtitle
            lblCode.text = code.code
            codeRowVw.setOnClickListener { dial(code.code) }
        }.root

    private fun dial(code: String) {
        trackEvent("CarrierCodes_Dial")
        ResumeAdKeeper.skipNextAppOpenAd = true
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_SLOT = "slot"

        fun intent(context: Context, slot: Int): Intent =
            Intent(context, OperatorDialCodesActivity::class.java).putExtra(EXTRA_SLOT, slot)
    }
}
