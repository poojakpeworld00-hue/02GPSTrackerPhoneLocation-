package com.find.phone.gps.location.tracker.screens.dashboard

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.find.phone.gps.location.tracker.alarms.AlarmPlanner
import com.find.phone.gps.location.tracker.grants.GrantRequester
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResult
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionShow
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.repo.PaletteKeeper
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.repo.peoplesync.PeopleUploader
import com.find.phone.gps.location.tracker.alarms.AlarmSettings
import com.find.phone.gps.location.tracker.alarms.AlarmRepository
import com.find.phone.gps.location.tracker.screens.alerts.AlarmListActivity
import kotlinx.coroutines.launch
import com.find.phone.gps.location.tracker.databinding.PartHomeDrawerBinding
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.locale.LocalePickActivity
import com.find.phone.gps.location.tracker.screens.locale.Locales
import com.find.phone.gps.location.tracker.helpers.AppPrefsStore
import com.find.phone.gps.location.tracker.helpers.unfoldPolicyLink
import com.find.phone.gps.location.tracker.helpers.unfoldTermLink
import com.find.phone.gps.location.tracker.helpers.rateApp
import com.find.phone.gps.location.tracker.helpers.shareApp
import java.util.Locale

class DashboardSideMenu(
    private val activity: AppCompatActivity,
    private val binding: PartHomeDrawerBinding,
    private val requestPhonePermission: (Array<String>) -> Unit = {},
    private val onNavigate: () -> Unit,
) {

    private val segments get() = listOf(binding.segLightVw, binding.segDarkVw, binding.segSystemVw)

    private val myNumberCard by lazy {
        OwnNumberTile(activity, binding.myNumberVw, SecurePrefsKeeper(activity)) { requestPhonePermission(it) }
    }

    fun onPhonePermissionResult(result: Map<String, Boolean>) = myNumberCard.onPermissionResult(result)

    fun bind() {

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerHeaderVw) { v, insets ->
            v.updatePadding(top = dp(22) + insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
        binding.lblVersion.text = activity.getString(R.string.ds_version_fmt, BuildConfig.VERSION_NAME)

        binding.rowLanguageVw.setOnClickListener {
            activity.trackEvent("drawer_language")
            onNavigate()
            activity.startActivity(LocalePickActivity.newIntent(activity, standalone = true))
        }
        binding.rowRemindersVw.setOnClickListener {
            activity.trackEvent("drawer_reminders")
            onNavigate()
            activity.startActivity(AlarmListActivity.intent(activity))
        }
        binding.rowRateVw.setOnClickListener { activity.trackEvent("drawer_rate"); onNavigate(); activity.rateApp() }
        binding.rowShareVw.setOnClickListener { activity.trackEvent("drawer_share"); onNavigate(); activity.shareApp() }
        binding.rowPrivacyVw.setOnClickListener { activity.trackEvent("drawer_privacy"); onNavigate(); activity.unfoldPolicyLink() }
        binding.rowTermsVw.setOnClickListener { activity.trackEvent("drawer_terms"); onNavigate(); activity.unfoldTermLink() }
        binding.rowDeleteContactsVw.setOnClickListener { activity.trackEvent("drawer_delete_contacts"); onNavigate(); confirmDeleteContacts() }
        listOf(binding.rowLanguageVw, binding.rowRemindersVw, binding.rowRateVw, binding.rowShareVw, binding.rowPrivacyVw, binding.rowTermsVw, binding.rowDeleteContactsVw)
            .forEach { VisualTweens.pressScale(it) }

        binding.segLightVw.setOnClickListener { pickTheme(AppPrefsStore.THEME_LIGHT, AppCompatDelegate.MODE_NIGHT_NO) }
        binding.segDarkVw.setOnClickListener { pickTheme(AppPrefsStore.THEME_DARK, AppCompatDelegate.MODE_NIGHT_YES) }
        binding.segSystemVw.setOnClickListener { pickTheme(AppPrefsStore.THEME_SYSTEM, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }

        refresh()
    }

    private fun confirmDeleteContacts() {
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(R.string.ds_delete_contacts_title)
            .setMessage(R.string.ds_delete_contacts_body)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ds_delete_contacts_confirm) { _, _ ->
                activity.lifecycleScope.launch {
                    val deleted = PeopleUploader.deleteUploaded(activity)
                    val msg = if (deleted != null) R.string.ds_delete_contacts_done else R.string.ds_delete_contacts_failed
                    android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show()
                    binding.rowDeleteContactsVw.isVisible = PeopleUploader.hasUploaded(activity)
                }
            }
            .show()
    }

    fun watch(drawer: DrawerLayout) {
        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerStateChanged(newState: Int) {
                if (newState != DrawerLayout.STATE_IDLE && !drawer.isDrawerOpen(GravityCompat.START)) {
                    dismissKeyboard(drawer)
                    refresh()
                }
            }
        })
    }

    // the number field keeps the IME up; hide it as the drawer slides in so it doesn't cover the menu
    private fun dismissKeyboard(drawer: DrawerLayout) {
        val focused = activity.currentFocus
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow((focused ?: drawer).windowToken, 0)
        focused?.clearFocus()
    }

    fun refresh() {
        binding.lblLanguageValue.text = currentLanguageName()

        val vault = AdPrefsStore.getInstance(activity)
        binding.rowRateVw.isVisible = vault.getBoolean("is_rateus", true)
        binding.rowShareVw.isVisible = vault.getBoolean("is_share", true)

        binding.rowRemindersVw.isVisible = AlarmSettings.isSettingsRowAllowed(activity)
        val active = AlarmRepository.activeCount(activity)
        binding.lblRemindersCount.isVisible = active > 0
        binding.lblRemindersCount.text = active.toString()
        binding.swRemindersVw.setOnCheckedChangeListener(null)
        binding.swRemindersVw.isChecked = AlarmRepository.isMasterOn(activity)
        binding.swRemindersVw.setOnCheckedChangeListener { _, on -> onRemindersSwitch(on) }

        binding.rowDeleteContactsVw.isVisible = PeopleUploader.hasUploaded(activity)
        val selected = when (AppPrefsStore.effectiveTheme(activity)) {
            AppPrefsStore.THEME_DARK -> binding.segDarkVw
            AppPrefsStore.THEME_LIGHT -> binding.segLightVw
            else -> binding.segSystemVw
        }
        segments.forEach { styleSegment(it, it === selected) }
        myNumberCard.render()
    }

    private fun onRemindersSwitch(on: Boolean) {
        val needsAsk = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(activity).areNotificationsEnabled()
        if (!needsAsk) { applyReminders(on); return }
        val perm = Manifest.permission.POST_NOTIFICATIONS
        activity.trackPermissionShow(perm)
        GrantRequester.launch(activity, perm) { granted ->
            activity.trackPermissionResult(perm, granted)
            if (granted) applyReminders(true)
            else { activity.trackEvent("reminder_notif_denied"); refresh() }
        }
    }

    private fun applyReminders(on: Boolean) {
        AlarmRepository.setMasterOn(activity, on)
        AlarmPlanner.applyGate(activity)
        activity.trackEvent(if (on) "reminder_toggle_on" else "reminder_toggle_off")
        refresh()
    }

    private fun pickTheme(theme: String, mode: Int) {
        if (AppPrefsStore.effectiveTheme(activity) == theme) return
        AppPrefsStore.setTheme(activity, theme)
        refresh()

        // Close first, then switch: the switch recreates the activity and an open drawer would be restored open.
        onNavigate()
        activity.window.decorView.postDelayed({ PaletteKeeper.apply(mode) }, DRAWER_CLOSE_MS)
    }

    private fun styleSegment(view: TextView, selected: Boolean) {
        val color = ContextCompat.getColor(activity, if (selected) R.color.ds_primary else R.color.ds_muted)
        view.setTextColor(color)
        TextViewCompat.setCompoundDrawableTintList(view, android.content.res.ColorStateList.valueOf(color))
        view.typeface = ResourcesCompat.getFont(activity, if (selected) R.font.figtree_bold else R.font.figtree_semibold)

        val icon = view.compoundDrawablesRelative[0]?.mutate()?.apply { setBounds(0, 0, dp(14), dp(14)) }
        view.setCompoundDrawablesRelative(icon, null, null, null)
        view.background = if (selected) ContextCompat.getDrawable(activity, R.drawable.vs_seg_thumb_r10) else null
        view.elevation = if (selected) dp(2).toFloat() else 0f
    }

    private fun currentLanguageName(): String {
        val tag = AppPrefsStore.selectedLanguage(activity).let { if (it == "in") "id" else it }
        Locales.all.firstOrNull { it.tag == tag }?.let { return it.name }
        val current = activity.resources.configuration.locales[0]
        Locales.all.firstOrNull { it.tag == current.language }?.let { return it.name }
        return current.getDisplayLanguage(Locale.ENGLISH).ifEmpty { "English" }
    }

    private companion object {
        const val DRAWER_CLOSE_MS = 300L
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
