package com.find.phone.gps.location.tracker.screens.alerts

import android.animation.Animator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.render.InlineSponsor
import com.find.phone.gps.location.tracker.databinding.PageReminderAlertBinding
import com.find.phone.gps.location.tracker.alarms.AlarmSettings
import com.find.phone.gps.location.tracker.alarms.AlarmEntry
import com.find.phone.gps.location.tracker.alarms.AlarmPlanner
import com.find.phone.gps.location.tracker.alarms.AlarmRepository
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.loop
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens.pvh
import com.find.phone.gps.location.tracker.helpers.OsDialogAssist

class AlarmPopupActivity : AppCompatActivity() {

    private lateinit var binding: PageReminderAlertBinding
    private var item: AlarmEntry? = null
    private var snoozeMinutes = 0
    private val animators = mutableListOf<Animator>()
    private var adRequested = false
    private var acted = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = loadAdIfUnlocked()
    }

    private val systemDialogHelper by lazy {
        OsDialogAssist(this, closeOnHome = true) { if (!isFinishing && !isDestroyed) finishAndRemoveTask() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presentOverLockScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = PageReminderAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sheetBottom = binding.alertSheetVw.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.alertRootVw) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.alertSheetVw.updatePadding(bottom = sheetBottom + bars.bottom)
            insets
        }
        ContextCompat.registerReceiver(
            this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED,
        )
        lifecycle.addObserver(systemDialogHelper)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.snoozeOverlayVw.isVisible) binding.snoozeOverlayVw.isVisible = false else finishAndRemoveTask()
            }
        })
        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        bind(intent)
    }

    private fun presentOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun bind(intent: Intent) {
        NotificationManagerCompat.from(this).cancel(intent.getIntExtra(EXTRA_NOTIF_ID, 0))
        val reminder = AlarmRepository.get(this, intent.getLongExtra(EXTRA_ID, -1L))
        if (reminder == null) { finishAndRemoveTask(); return }
        item = reminder
        trackEvent("Reminder_Alert_Show")
        if (intent.action == ACTION_TAP) trackEvent("reminder_alert_tap")

        binding.alertTitle.text = reminder.title
        binding.alertNoteVw.text = reminder.note
        binding.alertNoteVw.isVisible = reminder.note.isNotBlank()
        binding.alertNumberVw.text = reminder.number
        binding.alertNumberVw.isVisible = reminder.number.isNotBlank()

        val call = reminder.number.isNotBlank()
        binding.padPrimary.setText(if (call) R.string.rem_call_now else R.string.rem_open)
        binding.padPrimary.setIconResource(if (call) R.drawable.vs_ic_call else 0)
        binding.padPrimary.backgroundTintList =
            ContextCompat.getColorStateList(this, if (call) R.color.ds_success else R.color.ds_primary)
        binding.padPrimary.setOnClickListener { if (call) callBack(reminder) else unfoldList() }
        binding.padSecondary.setOnClickListener { presentSnooze() }
        binding.padDone.setOnClickListener {
            acted = true
            trackEvent("Reminder_Alert_Done")
            finishAndRemoveTask()
        }
        listOf(binding.padPrimary, binding.padSecondary, binding.padDone).forEach { VisualTweens.pressScale(it) }
        binding.snoozeOverlayVw.isVisible = false
        riseSheet()
        kickMotion()
    }

    override fun onResume() {
        super.onResume()
        loadAdIfUnlocked()
    }

    private fun loadAdIfUnlocked() {
        if (adRequested || isFinishing || isDestroyed) return
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) return
        adRequested = true
        binding.alertAdFrameVw.isVisible = true
        InlineSponsor().presentMidNative(this, binding.alertAdFrameVw)
        trackEvent("Reminder_Alert_Ad")
    }

    private fun riseSheet() {
        if (VisualTweens.animationsDisabled(this)) return
        val sheet = binding.alertSheetVw
        sheet.post {
            sheet.translationY = sheet.height.toFloat()
            sheet.animate().translationY(0f).setDuration(300L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
        }
        binding.alertRootVw.background?.alpha = 0
        android.animation.ValueAnimator.ofInt(0, 255).apply {
            duration = 300L
            addUpdateListener { binding.alertRootVw.background?.alpha = it.animatedValue as Int }
            start()
        }
    }

    private fun callBack(reminder: AlarmEntry) {
        acted = true
        trackEvent("Reminder_Alert_Call")
        afterUnlock {
            runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(reminder.number)))) }
            finishAndRemoveTask()
        }
    }

    private fun unfoldList() {
        acted = true
        trackEvent("Reminder_Alert_Open_List_Click")
        afterUnlock {
            startActivity(AlarmListActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finishAndRemoveTask()
        }
    }

    private fun afterUnlock(then: () -> Unit) {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) { then(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = then()
        })
    }

    private fun presentSnooze() {
        val options = AlarmSettings.get(this).actionDelayOptions.split(',')
            .mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..24 * 60 }.distinct()
            .ifEmpty { listOf(5, 10, 30, 60) }
        snoozeMinutes = options.getOrElse(1) { options.first() }

        val container = binding.snoozeOptionsVw
        container.removeAllViews()
        val tiles = mutableListOf<TextView>()
        options.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, minutes ->
                val tile = TextView(this).apply {
                    text = label(minutes)
                    gravity = android.view.Gravity.CENTER
                    textSize = 15f
                    typeface = ResourcesCompat.getFont(context, R.font.figtree_bold)
                    setTextColor(ContextCompat.getColorStateList(context, R.color.ds_reminder_option_text))
                    setBackgroundResource(R.drawable.vs_reminder_option)
                    isSelected = minutes == snoozeMinutes
                    setOnClickListener {
                        snoozeMinutes = minutes
                        tiles.forEach { t -> t.isSelected = t === this }
                    }
                }
                tiles += tile
                row.addView(tile, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    if (i == 1) marginStart = dp(10)
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(10) })
            container.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(10) })
        }
        binding.snoozeOverlayVw.setOnClickListener { binding.snoozeOverlayVw.isVisible = false }
        binding.padSnoozeConfirm.setOnClickListener { snooze() }
        binding.snoozeOverlayVw.isVisible = true
    }

    private fun snooze() {
        val reminder = item ?: return finishAndRemoveTask()
        acted = true
        AlarmPlanner.snooze(this, reminder.id, snoozeMinutes)
        trackEvent("Reminder_Alert_Snooze_$snoozeMinutes")
        val until = AlarmFormatter.time(this, System.currentTimeMillis() + snoozeMinutes * 60_000L)
        Toast.makeText(this, getString(R.string.rem_snoozed, until), Toast.LENGTH_SHORT).show()
        finishAndRemoveTask()
    }

    private fun label(minutes: Int): String =
        if (minutes % 60 == 0) resources.getQuantityString(R.plurals.rem_snooze_hours, minutes / 60, minutes / 60)
        else getString(R.string.rem_snooze_min, minutes)

    private fun kickMotion() {
        VisualTweens.cancelAll(animators)
        if (VisualTweens.animationsDisabled(this)) return
        val e = VisualTweens.EASE_OUT
        animators += loop(
            binding.alertRingVw, 2400L, 0L,
            pvh(View.SCALE_X, e, 0f to 0.9f, 1f to 1.35f),
            pvh(View.SCALE_Y, e, 0f to 0.9f, 1f to 1.35f),
            pvh(View.ALPHA, e, 0f to 0.9f, 1f to 0f),
        )
        val bell = binding.alertBellVw
        bell.post { bell.pivotX = bell.width / 2f; bell.pivotY = bell.height * 0.12f }
        animators += loop(
            bell, 3000L, 0L,
            pvh(View.ROTATION, VisualTweens.EASE_IN_OUT,
                0f to 0f, 0.3f to 0f, 0.4f to 16f, 0.5f to -13f, 0.6f to 9f, 0.7f to -5f, 0.8f to 2f, 0.9f to 0f, 1f to 0f),
        )
    }

    override fun onDestroy() {
        // closed by back/home without call, open, snooze or done
        if (isFinishing && item != null && !acted) trackEvent("reminder_alert_dismiss")
        runCatching { unregisterReceiver(unlockReceiver) }
        VisualTweens.cancelAll(animators)
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_ID = "reminder_id"
        private const val EXTRA_NOTIF_ID = "notif_id"
        const val ACTION_TAP = "com.find.phone.gps.location.tracker.REMINDER_TAP"

        fun intent(context: Context, id: Long, notifId: Int): Intent =
            Intent(context, AlarmPopupActivity::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_NOTIF_ID, notifId)
    }
}
