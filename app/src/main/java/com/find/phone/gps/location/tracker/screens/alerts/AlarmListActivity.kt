package com.find.phone.gps.location.tracker.screens.alerts

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.InlineSponsor
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.databinding.PopupReminderDeleteBinding
import com.find.phone.gps.location.tracker.databinding.CardReminderBinding
import com.find.phone.gps.location.tracker.databinding.PageReminderListBinding
import com.find.phone.gps.location.tracker.alarms.AskGatekeeper
import com.find.phone.gps.location.tracker.alarms.AlarmEntry
import com.find.phone.gps.location.tracker.alarms.AlarmPlanner
import com.find.phone.gps.location.tracker.alarms.AlarmRepository
import com.find.phone.gps.location.tracker.alarms.Repeat
import com.find.phone.gps.location.tracker.alarms.gate.AlarmGrant
import com.find.phone.gps.location.tracker.alarms.gate.AlarmAskDialog
import com.find.phone.gps.location.tracker.screens.people.PeopleSelectActivity
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.helpers.followAdContainer
import com.find.phone.gps.location.tracker.helpers.isNightMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reminders (design 2): Upcoming and Done, a switch per reminder, "+ New reminder". Opened from
 * the drawer. Also the host of the "Turn on reminders" prompt, which appears after the user saves
 * their first reminder (design 4), so the ask always comes with a reason.
 */
class AlarmListActivity : FoundationActivity<PageReminderListBinding>() {

    override val layoutId: Int = R.layout.page_reminder_list

    private var accessPollJob: Job? = null
    private var pendingNumber: ((String) -> Unit)? = null
    private var afterNotificationAsk: (() -> Unit)? = null

    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra(PeopleSelectActivity.EXTRA_NUMBER)?.let { pendingNumber?.invoke(it) }
        pendingNumber = null
    }

    /** Whatever the answer, carry on: the reminder is saved either way. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Deferred: the result lands around the resume that follows the system dialog, and
        // opening Settings inside it races onResume / onHostResume.
        val next = afterNotificationAsk
        afterNotificationAsk = null
        window.decorView.post { next?.invoke() }
    }

    override fun initView() {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isNightMode(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.remindersRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        binding.padBack.setOnClickListener { goBack() }
        VisualTweens.pressScale(binding.padNewReminder)
        binding.padNewReminder.setOnClickListener { unfoldSheet(null) }
        trackEvent("Reminders_Open")

        // Bottom mid native (ScreenAds["AlarmListActivity"].show turns it off).
        InlineSponsor().presentMidNative(this, binding.adNativeFrameVw, binding.adShimmerVw)
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)
    }

    override fun onResume() {
        super.onResume()
        accessPollJob?.cancel()
        AlarmGrant.onHostResume(this)
        AlarmRepository.seedDefaultsIfNeeded(this)
        render()
    }

    override fun onDestroy() {
        accessPollJob?.cancel()
        AlarmAskDialog.retireIfShowing()
        super.onDestroy()
    }

    // ───────────────────────────── list ─────────────────────────────

    private fun render() {
        val container = binding.rollReminders
        container.removeAllViews()
        val all = AlarmRepository.load(this)
        val upcoming = all.filter { !it.done }.sortedBy { it.dateTime }
        val done = all.filter { it.done }.sortedByDescending { it.dateTime }

        binding.emptyStateVw.isVisible = all.isEmpty()
        if (upcoming.isNotEmpty()) {
            container.addView(sectionLabel(R.string.rem_upcoming))
            upcoming.forEach { container.addView(card(it)) }
        }
        if (done.isNotEmpty()) {
            container.addView(sectionLabel(R.string.rem_done_section))
            done.forEach { container.addView(card(it)) }
        }
    }

    private fun sectionLabel(res: Int) = TextView(this).apply {
        setText(res)
        isAllCaps = true
        letterSpacing = 0.08f
        textSize = 11f
        setTextColor(ContextCompat.getColor(context, R.color.ds_muted))
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.figtree_bold)
        layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(dp(4), dp(10), 0, dp(8)) }
    }

    private fun card(item: AlarmEntry) = CardReminderBinding.inflate(LayoutInflater.from(this), binding.rollReminders, false).apply {
        lblTitle.text = item.title
        lblWhen.text = AlarmFormatter.dayAndTime(this@AlarmListActivity, item.dateTime)
        lblNumber.text = item.number
        lblNumber.isVisible = item.number.isNotBlank()
        lblRepeat.text = AlarmFormatter.repeatLabel(this@AlarmListActivity, item.repeatOrOnce)

        // A number makes it a call-back (phone, purple); otherwise a plain reminder (bell, blue).
        val call = item.number.isNotBlank()
        iconTileVw.setBackgroundResource(if (call) R.drawable.vs_tile_purple_r12 else R.drawable.vs_tile_tint_r12)
        icon.setImageResource(if (call) R.drawable.vs_ic_call else R.drawable.gp_ic_bell)
        ImageViewCompat.setImageTintList(
            icon, ContextCompat.getColorStateList(this@AlarmListActivity, if (call) R.color.ds_purple else R.color.ds_primary),
        )

        itemReminderVw.alpha = if (item.done) 0.6f else 1f
        lblTitle.paintFlags = if (item.done) lblTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else lblTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        swEnabledVw.setOnCheckedChangeListener(null)
        swEnabledVw.isChecked = item.isActive
        swEnabledVw.setOnCheckedChangeListener { _, checked -> onToggle(item, checked) }

        itemReminderVw.setOnClickListener { unfoldSheet(item) }
        itemReminderVw.setOnLongClickListener { confirmDelete(item); true }
    }.root

    private fun onToggle(item: AlarmEntry, on: Boolean) {
        // Turning a finished one-off back on needs a new time: open it instead.
        if (on && item.repeatOrOnce == Repeat.ONCE && item.dateTime <= System.currentTimeMillis()) {
            render()
            unfoldSheet(item)
            return
        }
        AlarmPlanner.save(this, item.copy(enabled = on, done = false))
        trackEvent(if (on) "Reminder_Enable" else "Reminder_Disable")
        render()
    }

    // ───────────────────────────── edit / delete ─────────────────────────────

    private fun unfoldSheet(item: AlarmEntry?) {
        AlarmEditorSheet(
            activity = this,
            existing = item,
            onPickContact = { onNumber ->
                pendingNumber = onNumber
                contactPicker.launch(PeopleSelectActivity.intent(this))
            },
            onSave = { saved, isNew -> onSaved(saved, isNew) },
            onDelete = { confirmDelete(it) },
        ).show()
    }

    private fun onSaved(item: AlarmEntry, isNew: Boolean) {
        AlarmPlanner.save(this, item)
        render()
        val stored = AlarmRepository.get(this, item.id) ?: item
        Toast.makeText(this, getString(R.string.rem_saved, AlarmFormatter.dayAndTime(this, stored.dateTime)), Toast.LENGTH_SHORT).show()
        trackEvent(if (isNew) "Reminder_Create" else "Reminder_Edit")
        if (isNew) {
            AskGatekeeper.onReminderCreated(this)
            promptAfterSave(stored)
        }
    }

    /**
     * After a save: the "Turn on reminders" prompt (Remote Config gated). Its button first asks
     * for notifications when they are off (13+), then opens the reminders setting. When the
     * prompt does not show but notifications are off, ask for those anyway: nothing can be shown
     * without them.
     */
    private fun promptAfterSave(item: AlarmEntry) {
        AlarmGrant.prime(
            activity = this,
            returnTo = AlarmListActivity::class.java,
            reminderTitle = item.title,
            reminderTime = AlarmFormatter.time(this, item.dateTime),
            onReady = { if (needsNotificationPermission()) promptNotifications {} },
            onSettingsOpened = { kickAccessPoll() },
            onManage = { proceed -> if (needsNotificationPermission()) promptNotifications(proceed) else proceed() },
        )
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun promptNotifications(then: () -> Unit) {
        afterNotificationAsk = then
        runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            .onFailure { afterNotificationAsk = null; then() }
    }

    /** The primary return path: Settings sits in this task, so this Activity may bring itself back. */
    private fun kickAccessPoll() {
        accessPollJob?.cancel()
        accessPollJob = lifecycleScope.launch {
            while (isActive) {
                if (AlarmGrant.isGranted(this@AlarmListActivity)) {
                    runCatching { startActivity(AlarmGrant.returnIntent(this@AlarmListActivity, AlarmListActivity::class.java)) }
                    break
                }
                delay(500L)
            }
        }
    }

    private fun confirmDelete(item: AlarmEntry) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = PopupReminderDeleteBinding.inflate(layoutInflater)
        dialog.setContentView(view.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        view.lblDeleteBody.text = getString(R.string.rem_delete_body, item.title)
        view.padCancel.setOnClickListener { dialog.dismiss() }
        view.padConfirmDelete.setOnClickListener {
            dialog.dismiss()
            AlarmPlanner.delete(this, item.id)
            trackEvent("Reminder_Delete")
            render()
        }
        dialog.show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun intent(context: android.content.Context) = Intent(context, AlarmListActivity::class.java)
    }
}
