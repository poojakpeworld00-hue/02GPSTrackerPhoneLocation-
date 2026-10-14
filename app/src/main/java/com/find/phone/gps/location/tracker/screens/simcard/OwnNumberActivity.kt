package com.find.phone.gps.location.tracker.screens.simcard

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResult
import com.find.phone.gps.location.tracker.monetize.render.ResumeAdKeeper
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.repo.simcard.OwnSimLabels
import com.find.phone.gps.location.tracker.repo.simcard.OwnSimLines
import com.find.phone.gps.location.tracker.databinding.CardMyNumberCardCompactBinding
import com.find.phone.gps.location.tracker.databinding.PageMyNumberBinding
import com.find.phone.gps.location.tracker.helpers.SimLineJournal
import com.find.phone.gps.location.tracker.helpers.isNightMode
import kotlinx.coroutines.launch

class OwnNumberActivity : FoundationActivity<PageMyNumberBinding>() {

    override val layoutId: Int = R.layout.page_my_number

    private val prefs by lazy { SecurePrefsKeeper(this) }

    private class Card(val line: OwnSimLines.SimSlotLine, val subId: Int?, val views: CardMyNumberCardCompactBinding)

    private var cards: List<Card> = emptyList()
    private var selectedSlot = -1
    private val signals = HashMap<Int, Pair<TelephonyManager, PhoneStateListener>>()
    private val levels = HashMap<Int, Int>()

    private val phonePermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result.forEach { (permission, granted) -> trackPermissionResult(permission, granted) }
        if (OwnSimLines.isGranted(this)) SimLineJournal.log(this)
        render()
    }

    override fun initView() {

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isNightMode(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.myNumberRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        binding.padBack.setOnClickListener { goBack() }
        binding.padShow.setOnClickListener { onShow() }
        binding.rowShareQrVw.setOnClickListener { unfoldShare() }
        binding.rowDashboardVw.setOnClickListener { startActivity(TwinSimOverviewActivity.intent(this)) }
        binding.rowCarrierCodesVw.setOnClickListener {
            startActivity(OperatorDialCodesActivity.intent(this, selectedSlot))
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onPause() {
        stopSignals()
        super.onPause()
    }

    private fun render() {
        stopSignals()
        val lines = if (OwnSimLines.hasReadySim(this)) OwnSimLines.read(this) else emptyList()
        val showing = lines.isNotEmpty()
        binding.simCardsVw.isVisible = showing
        binding.simToolsSectionVw.isVisible = showing
        binding.emptyStateVw.isVisible = !showing
        binding.padShow.setText(if (isPermanentlyDenied()) R.string.my_number_settings else R.string.my_number_show)

        binding.simCardsVw.removeAllViews()
        if (!showing) {
            cards = emptyList()
            return
        }
        val inflater = LayoutInflater.from(this)
        cards = lines.mapIndexed { i, line ->
            val v = CardMyNumberCardCompactBinding.inflate(inflater, binding.simCardsVw, false)
            v.root.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = dp(12)
            }
            binding.simCardsVw.addView(v.root)
            Card(line, SimSignal.subIdForSlot(this, line.slot), v)
        }
        if (cards.none { it.line.slot == selectedSlot }) selectedSlot = cards.first().line.slot
        cards.forEach { card ->
            wireStatic(card)
            card.views.root.setOnClickListener { select(card.line.slot) }
        }
        imprintSelection()
        enrollSignals()
    }

    private fun wireStatic(card: Card) {
        val v = card.views
        val line = card.line
        v.lblBadge.text = getString(R.string.my_number_sim_badge, line.slot)
        val network = card.subId?.let { SimSignal.networkLabel(this, it) }
        v.lblCarrier.text = if (network != null) "${line.carrier} · $network" else line.carrier
        v.lblRegion.text = line.region.orEmpty()
        v.lblRegion.isVisible = !line.region.isNullOrBlank()

        val number = line.number
        if (number != null) {
            v.lblNumber.text = number
            v.padCopy.isVisible = true
            v.padCopy.setOnClickListener { copy(number) }
        } else {
            v.lblNumber.setText(R.string.my_number_not_stored)
            v.padCopy.isVisible = false
        }
        v.badgeDefaultVw.isVisible = card.subId != null && card.subId == SimSignal.defaultVoiceSubId()

        line.e164?.let { e164 ->
            if (OwnSimLabels.cached(this, e164) == null) lifecycleScope.launch {
                OwnSimLabels.resolve(this@OwnNumberActivity, e164, getString(R.string.my_number_sim_label, line.slot, line.carrier))
            }
        }
    }

    private fun select(slot: Int) {
        if (slot == selectedSlot) return
        selectedSlot = slot
        imprintSelection()
    }

    private fun imprintSelection() {
        cards.forEach { styleCard(it, it.line.slot == selectedSlot) }
    }

    private fun styleCard(card: Card, on: Boolean) {
        val v = card.views
        v.root.setBackgroundResource(if (on) R.drawable.vs_sim_card_selected else R.drawable.vs_sim_card_unselected)

        val ink = color(if (on) R.color.white else R.color.ds_ink_fixed)
        val sub = if (on) 0xCCFFFFFF.toInt() else color(R.color.ds_muted_fixed)
        v.lblCarrier.setTextColor(ink)
        v.lblNumber.setTextColor(ink)
        v.lblRegion.setTextColor(sub)

        v.lblBadge.setBackgroundResource(if (on) R.drawable.vs_sim_badge_white else R.drawable.vs_tile_tint_r8)
        v.lblBadge.setTextColor(if (on) color(R.color.white) else color(R.color.ds_splash_blue))

        v.padCopy.setBackgroundResource(if (on) R.drawable.vs_copy_translucent_r12 else R.drawable.vs_copy_gray_fixed_r12)
        val copyInk = if (on) color(R.color.white) else color(R.color.ds_ink_fixed)
        v.padCopy.setTextColor(copyInk)
        TextViewCompat.setCompoundDrawableTintList(v.padCopy, ColorStateList.valueOf(copyInk))

        v.badgeDefaultVw.setBackgroundResource(if (on) R.drawable.vs_pill_white_solid else R.drawable.vs_pill_success)
        v.badgeDefaultVw.setTextColor(if (on) color(R.color.ds_splash_blue) else color(R.color.ds_success))

        setBars(card, levels[card.line.slot] ?: 0, on)
    }

    private fun setBars(card: Card, level: Int, on: Boolean) {
        val onColor = if (on) color(R.color.white) else color(R.color.ds_splash_blue)
        val offColor = if (on) 0x55FFFFFF else color(R.color.ds_border)
        listOf(card.views.bar1Vw, card.views.bar2Vw, card.views.bar3Vw, card.views.bar4Vw).forEachIndexed { i, bar ->
            ViewCompat.setBackgroundTintList(bar, ColorStateList.valueOf(if (i < level) onColor else offColor))
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun enrollSignals() {
        if (!OwnSimLines.isGranted(this)) return
        cards.forEach { card ->
            val subId = card.subId ?: return@forEach
            val tm = SimSignal.tmFor(this, subId)
            val listener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val level = signalStrength.level.coerceIn(0, 4)
                    levels[card.line.slot] = level
                    setBars(card, level, card.line.slot == selectedSlot)
                }
            }
            signals[subId] = tm to listener
            runCatching { tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopSignals() {
        signals.values.forEach { (tm, l) -> runCatching { tm.listen(l, PhoneStateListener.LISTEN_NONE) } }
        signals.clear()
    }

    private fun selected(): Card? = cards.firstOrNull { it.line.slot == selectedSlot } ?: cards.firstOrNull()

    private fun unfoldShare() {
        val card = selected()
        val e164 = card?.line?.e164
        val number = card?.line?.number
        if (card == null || e164 == null || number == null) {
            Toast.makeText(this, R.string.my_number_not_stored, Toast.LENGTH_SHORT).show()
            return
        }
        val name = OwnSimLabels.cached(this, e164)?.takeIf { it.source != OwnSimLabels.Source.SIM }?.name
        startActivity(SendNumberActivity.intent(this, e164, number, name, card.line.slot, card.line.carrier))
    }

    private fun onShow() {
        trackEvent("MyNumberPage_Show_Click")
        val permission = OwnSimLines.PERMISSIONS.first()
        when {
            OwnSimLines.isGranted(this) -> render()
            !prefs.hasRequestedPermission(permission) -> {
                prefs.markPermissionRequested(permission)
                phonePermission.launch(OwnSimLines.PERMISSIONS)
            }
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, permission) ->
                phonePermission.launch(OwnSimLines.PERMISSIONS)
            else -> unfoldPhoneSettings()
        }
    }

    private fun isPermanentlyDenied(): Boolean {
        val permission = OwnSimLines.PERMISSIONS.first()
        return !OwnSimLines.isGranted(this) &&
            prefs.hasRequestedPermission(permission) &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
    }

    private fun unfoldPhoneSettings() {
        Toast.makeText(this, R.string.my_number_denied, Toast.LENGTH_LONG).show()
        ResumeAdKeeper.skipNextAppOpenAd = true
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    private fun copy(number: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.my_number_title), number))
        Toast.makeText(this, R.string.my_number_copied, Toast.LENGTH_SHORT).show()
        trackEvent("MyNumberPage_Copy")
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun intent(context: Context): Intent = Intent(context, OwnNumberActivity::class.java)
    }
}
