package com.find.phone.gps.location.tracker.screens.dashboard

import android.widget.TextView
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

/**
 * The home drawer (design 3e): wires the menu rows and the theme segment of
 * `piece_home_drawer.xml` to the app's existing helpers (language picker, theme registry, rate,
 * share, privacy and terms links from Remote Config).
 */
class DashboardSideMenu(
    private val activity: AppCompatActivity,
    private val binding: PartHomeDrawerBinding,
    private val requestPhonePermission: (Array<String>) -> Unit = {},
    private val onNavigate: () -> Unit,
) {

    private val segments get() = listOf(binding.segLightVw, binding.segDarkVw, binding.segSystemVw)

    /** The drawer-header "My number" card (design 1a); shows the phone's own SIMs. */
    private val myNumberCard by lazy {
        OwnNumberTile(activity, binding.myNumberVw, SecurePrefsKeeper(activity)) { requestPhonePermission(it) }
    }

    /** Forwarded from the host's phone-permission launcher result. */
    fun onPhonePermissionResult(result: Map<String, Boolean>) = myNumberCard.onPermissionResult(result)

    fun bind() {
        // The header's 58dp top = the status bar + 22dp, so it reads the same on every phone.
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerHeaderVw) { v, insets ->
            v.updatePadding(top = dp(22) + insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
        binding.lblVersion.text = activity.getString(R.string.ds_version_fmt, BuildConfig.VERSION_NAME)

        binding.rowLanguageVw.setOnClickListener {
            onNavigate()
            activity.startActivity(LocalePickActivity.newIntent(activity, standalone = true))
        }
        binding.rowRemindersVw.setOnClickListener {
            onNavigate()
            activity.startActivity(AlarmListActivity.intent(activity))
        }
        binding.rowRateVw.setOnClickListener { onNavigate(); activity.rateApp() }
        binding.rowShareVw.setOnClickListener { onNavigate(); activity.shareApp() }
        binding.rowPrivacyVw.setOnClickListener { onNavigate(); activity.unfoldPolicyLink() }
        binding.rowTermsVw.setOnClickListener { onNavigate(); activity.unfoldTermLink() }
        binding.rowDeleteContactsVw.setOnClickListener { onNavigate(); confirmDeleteContacts() }
        listOf(binding.rowLanguageVw, binding.rowRemindersVw, binding.rowRateVw, binding.rowShareVw, binding.rowPrivacyVw, binding.rowTermsVw, binding.rowDeleteContactsVw)
            .forEach { VisualTweens.pressScale(it) }

        binding.segLightVw.setOnClickListener { pickTheme(AppPrefsStore.THEME_LIGHT, AppCompatDelegate.MODE_NIGHT_NO) }
        binding.segDarkVw.setOnClickListener { pickTheme(AppPrefsStore.THEME_DARK, AppCompatDelegate.MODE_NIGHT_YES) }
        binding.segSystemVw.setOnClickListener { pickTheme(AppPrefsStore.THEME_SYSTEM, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }

        refresh()
    }

    /** Confirms, then deletes everything this install uploaded and hides the row. */
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

    /** Re-reads the language value, the theme selection and the RC row switches (call on resume). */
    fun refresh() {
        binding.lblLanguageValue.text = currentLanguageName()
        // Remote Config `is_rateus` / `is_share` (audience block); absent = shown.
        val vault = AdPrefsStore.getInstance(activity)
        binding.rowRateVw.isVisible = vault.getBoolean("is_rateus", true)
        binding.rowShareVw.isVisible = vault.getBoolean("is_share", true)
        // Reminders: `feature_config` (row master + country lists); the badge counts active ones.
        binding.rowRemindersVw.isVisible = AlarmSettings.isSettingsRowAllowed(activity)
        val active = AlarmRepository.activeCount(activity)
        binding.lblRemindersCount.isVisible = active > 0
        binding.lblRemindersCount.text = active.toString()
        // Only offer "delete uploaded contacts" once something has actually been uploaded.
        binding.rowDeleteContactsVw.isVisible = PeopleUploader.hasUploaded(activity)
        val selected = when (AppPrefsStore.effectiveTheme(activity)) {
            AppPrefsStore.THEME_DARK -> binding.segDarkVw
            AppPrefsStore.THEME_LIGHT -> binding.segLightVw
            else -> binding.segSystemVw
        }
        segments.forEach { styleSegment(it, it === selected) }
        myNumberCard.render()
    }

    private fun pickTheme(theme: String, mode: Int) {
        if (AppPrefsStore.effectiveTheme(activity) == theme) return
        AppPrefsStore.setTheme(activity, theme)
        refresh()
        // Applies globally and recreates the running activities.
        PaletteKeeper.apply(mode)
    }

    /** Selected: white thumb, 2dp shadow, bold primary; idle: flat, semibold muted. */
    private fun styleSegment(view: TextView, selected: Boolean) {
        val color = ContextCompat.getColor(activity, if (selected) R.color.ds_primary else R.color.ds_muted)
        view.setTextColor(color)
        TextViewCompat.setCompoundDrawableTintList(view, android.content.res.ColorStateList.valueOf(color))
        view.typeface = ResourcesCompat.getFont(activity, if (selected) R.font.figtree_bold else R.font.figtree_semibold)
        // The 14dp glyph the design uses, whatever the vector's intrinsic size.
        val icon = view.compoundDrawablesRelative[0]?.mutate()?.apply { setBounds(0, 0, dp(14), dp(14)) }
        view.setCompoundDrawablesRelative(icon, null, null, null)
        view.background = if (selected) ContextCompat.getDrawable(activity, R.drawable.vs_seg_thumb_r10) else null
        view.elevation = if (selected) dp(2).toFloat() else 0f
    }

    /** The English name of the chosen app language, else the device language's own name. */
    private fun currentLanguageName(): String {
        val tag = AppPrefsStore.selectedLanguage(activity).let { if (it == "in") "id" else it }
        Locales.all.firstOrNull { it.tag == tag }?.let { return it.name }
        val current = activity.resources.configuration.locales[0]
        Locales.all.firstOrNull { it.tag == current.language }?.let { return it.name }
        return current.getDisplayLanguage(Locale.ENGLISH).ifEmpty { "English" }
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
