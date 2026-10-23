package com.find.phone.gps.location.tracker.screens.finder

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.render.BonusAdSponsor
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.finder.NumberFinder
import com.find.phone.gps.location.tracker.repo.finder.NumberSummary
import com.find.phone.gps.location.tracker.repo.finder.UnveiledLabels
import com.find.phone.gps.location.tracker.repo.billing.ProStoreBilling
import com.find.phone.gps.location.tracker.databinding.CardDetailActionBinding
import com.find.phone.gps.location.tracker.databinding.CardDetailCellBinding
import com.find.phone.gps.location.tracker.databinding.CardDetailNameBinding
import com.find.phone.gps.location.tracker.databinding.PageNumberDetailBinding
import com.find.phone.gps.location.tracker.screens.people.PeopleListAdapter
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class NumberInsightActivity : FoundationActivity<PageNumberDetailBinding>() {

    override val layoutId: Int = R.layout.page_number_detail

    private lateinit var report: NumberSummary
    private var overlay: ZoneRippleOverlay? = null
    private var revealing = false
    private var topInset = 0
    private var allNamesShown = false

    override fun onCreate(savedInstanceState: Bundle?) {

        OpenStreetTiles.init(this)
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        val given = IntentCompat.getSerializableExtra(intent, EXTRA_REPORT, NumberSummary::class.java)
        if (given == null) {
            finish()
            return
        }
        report = given

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        val topPad = binding.mapTopBarVw.paddingTop
        val mapHeight = binding.mapBoxVw.layoutParams.height
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.mapTopBarVw.updatePadding(top = topPad + bars.top)
            binding.mapBoxVw.updateLayoutParams { height = mapHeight + bars.top }
            if (topInset != bars.top) {
                topInset = bars.top
                if (::report.isInitialized) centerPin()
            }
            v.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }

        binding.padBack.setOnClickListener { goBack() }
        binding.padShare.setOnClickListener { share() }
        binding.padRetry.setOnClickListener { retry() }
        binding.mapTapVw.setOnClickListener { unfoldFullMap() }
        binding.padFullMap.setOnClickListener { unfoldFullMap() }
        VisualTweens.pressScale(binding.padFullMap)
        binding.identityCardVw.clipToOutline = false

        action(binding.actCallVw, R.drawable.vs_ic_call, R.string.ds_act_call, lead = true) { placeCall(report.e164) }
        action(binding.actSmsVw, R.drawable.vs_ic_message, R.string.ds_act_sms) { sendSms() }
        action(binding.actMapVw, R.drawable.vs_ic_navigation, R.string.ds_act_map) { unfoldFullMap() }
        action(binding.actCopyVw, R.drawable.vs_ic_copy, R.string.ds_act_copy) { copyNumber() }

        OpenStreetTiles.style(binding.mapViewVw, interactive = false)
        bind()
        placeOnMap()
    }

    private fun bind() {
        val r = report
        binding.lblIso.text = r.iso2
        binding.lblNumber.text = r.international
        binding.lblPlace.text = listOfNotNull(r.city, r.state).distinct().joinToString(", ").ifEmpty { r.countryName }
        binding.lblMapPlace.text = binding.lblPlace.text

        when {
            r.spam -> chip(R.string.ds_verdict_spam, R.drawable.vs_pill_danger, R.color.ds_danger)
            r.valid -> chip(R.string.ds_result_valid_short, R.drawable.vs_pill_success_soft, R.color.ds_success)
            else -> chip(R.string.ds_verdict_unverified, R.drawable.vs_pill_warn, R.color.ds_warn)
        }
        val line = r.lineType.label(this)
        binding.flagLine.text = line
        binding.flagLine.isVisible = line != null
        binding.offlineCardVw.isVisible = !r.serverReached

        cell(binding.cellCountryVw, R.string.ds_label_country, r.countryName)
        cell(binding.cellPlaceVw, R.string.ds_detail_city_region, listOfNotNull(r.city, r.state).distinct().joinToString(", "))
        cell(binding.cellCarrierVw, R.string.ds_fact_carrier, r.carrier)
        cell(binding.cellLineVw, R.string.ds_fact_line, line)

        wireNames()
    }

    private fun chip(@StringRes text: Int, @DrawableRes pill: Int, color: Int) {
        binding.flagVerdict.setText(text)
        binding.flagVerdict.setBackgroundResource(pill)
        binding.flagVerdict.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun cell(cell: CardDetailCellBinding, @StringRes label: Int, value: String?) {
        cell.lblLabel.setText(label)
        val known = !value.isNullOrBlank()
        cell.lblValue.text = if (known) value else getString(R.string.ds_not_available)
        cell.lblValue.setTextColor(ContextCompat.getColor(this, if (known) R.color.ds_ink else R.color.ds_muted))
    }

    private fun action(tile: CardDetailActionBinding, @DrawableRes icon: Int, @StringRes label: Int, lead: Boolean = false, onClick: () -> Unit) {
        tile.picIcon.setImageResource(icon)
        tile.lblLabel.setText(label)
        if (lead) {
            tile.iconBoxVw.setBackgroundResource(R.drawable.vs_tile_primary_r16)
            tile.picIcon.setColorFilter(ContextCompat.getColor(this, R.color.white))
        }
        VisualTweens.pressScale(tile.actionRootVw)
        tile.actionRootVw.setOnClickListener { onClick() }
    }

    private val names: List<String>
        get() = listOfNotNull(report.name) + report.otherNames

    private fun isRevealed(index: Int, name: String): Boolean =
        index == 0 && report.name != null || ProStoreBilling.isPremium(this) || UnveiledLabels.isRevealed(this, report.e164, name)

    private fun wireNames() {
        val all = names
        binding.namesBlockVw.isVisible = all.isNotEmpty()
        binding.namesListVw.removeAllViews()

        val shown = if (allNamesShown) all else all.take(NAMES_PREVIEW)
        binding.padShowAllNames.isVisible = shown.size < all.size
        binding.padShowAllNames.text = getString(R.string.ds_names_show_all, all.size)
        binding.padShowAllNames.setOnClickListener {
            allNamesShown = true
            wireNames()
        }
        shown.forEachIndexed { i, name ->
            val item = CardDetailNameBinding.inflate(LayoutInflater.from(this), binding.namesListVw, true)
            item.lblName.text = name
            item.nameDividerVw.isVisible = i < shown.lastIndex
            presentName(item, name, isRevealed(i, name))
            item.padReveal.setOnClickListener { revealName(item, name) }
        }
        updateRevealedCount()
        if (all.indices.any { !isRevealed(it, all[it]) }) BonusAdSponsor.preload(this)
    }

    private fun presentName(item: CardDetailNameBinding, name: String, revealed: Boolean) {
        item.lblName.setBlurred(!revealed)
        item.lblRevealed.isVisible = revealed
        item.padReveal.isVisible = !revealed
        if (revealed) {
            item.lblAvatar.text = initials(name)
            item.lblAvatar.setBackgroundResource(PeopleListAdapter.AVATARS[abs(name.hashCode()) % PeopleListAdapter.AVATARS.size])
        } else {
            item.lblAvatar.text = ""
            item.lblAvatar.setBackgroundResource(R.drawable.vs_circle_track)
        }
    }

    private fun initials(name: String): String =
        name.split(' ').filter { it.isNotBlank() }.take(2)
            .mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
            .joinToString("")

    private fun updateRevealedCount() {
        val all = names
        val revealed = all.indices.count { isRevealed(it, all[it]) }
        binding.lblRevealedCount.text = getString(R.string.ds_names_count, revealed, all.size)
        binding.lblNamesNote.isVisible = revealed < all.size
    }

    private fun revealName(item: CardDetailNameBinding, name: String) {
        if (revealing) return
        revealing = true
        item.lblReveal.isInvisible = true
        item.pbRevealVw.isVisible = true
        BonusAdSponsor().show(this) { result ->
            revealing = false
            item.lblReveal.isInvisible = false
            item.pbRevealVw.isVisible = false
            if (result != BonusAdSponsor.Result.EARNED) {
                if (result == BonusAdSponsor.Result.NOT_EARNED) {
                    Toast.makeText(this, R.string.ds_reward_not_earned, Toast.LENGTH_SHORT).show()
                }
                return@show
            }
            UnveiledLabels.reveal(this, report.e164, name)
            presentName(item, name, revealed = true)
            updateRevealedCount()
        }
    }

    private fun placeOnMap() {
        overlay?.let { it.stop(); binding.mapViewVw.overlays.remove(it) }
        overlay = OpenStreetTiles.place(binding.mapViewVw, report, coarse = false)
        centerPin()
    }

    private fun centerPin() {
        val d = resources.displayMetrics.density
        val above = topInset + (46 * d).roundToInt()
        val below = (34 * d).roundToInt()
        binding.mapViewVw.setMapCenterOffset(0, (above - below) / 2)
        binding.mapViewVw.invalidate()
    }

    private fun retry() {
        binding.padRetry.isVisible = false
        binding.pbRetryVw.isVisible = true
        lifecycleScope.launch {

            val fresh = NumberFinder.run(this@NumberInsightActivity, report.e164, report.iso2, "")
            binding.padRetry.isVisible = true
            binding.pbRetryVw.isVisible = false
            if (!fresh.serverReached) {
                Toast.makeText(this@NumberInsightActivity, R.string.ds_offline_note, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val moved = fresh.pin != report.pin || fresh.precision != report.precision
            report = fresh
            bind()
            if (moved) placeOnMap()
        }
    }

    private fun sendSms() = launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${report.e164}")))

    private fun copyNumber() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(report.e164, report.international))
        Toast.makeText(this, R.string.ds_copied, Toast.LENGTH_SHORT).show()
    }

    private fun unfoldFullMap() = startActivity(MapExpandedActivity.intent(this, report))

    private fun share() {
        val r = report
        val pin = r.pin
        val link = if (pin != null) "https://www.google.com/maps/search/?api=1&query=${pin.lat},${pin.lng}"
        else "https://www.google.com/maps/search/?api=1&query=${Uri.encode(r.placeLabel)}"
        val text = listOfNotNull(
            r.name?.let { getString(R.string.ds_share_name, it) },
            getString(R.string.ds_share_number, r.international),
            r.placeLabel.takeIf { it.isNotEmpty() }?.let { getString(R.string.ds_share_place, it) },
            r.carrier?.let { getString(R.string.ds_share_carrier, it) },
            link,
        ).joinToString("\n")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        launch(Intent.createChooser(send, getString(R.string.ds_share_title)))
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.ds_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::report.isInitialized) binding.mapViewVw.onResume()
    }

    override fun onPause() {
        if (::report.isInitialized) binding.mapViewVw.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        overlay?.stop()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_REPORT = "report"
        private const val NAMES_PREVIEW = 10

        fun intent(context: Context, report: NumberSummary): Intent =
            Intent(context, NumberInsightActivity::class.java).putExtra(EXTRA_REPORT, report)
    }
}
