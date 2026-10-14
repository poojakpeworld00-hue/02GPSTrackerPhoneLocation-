package com.find.phone.gps.location.tracker.screens.people

import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionShow
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResult
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.PageSponsorSettings
import com.find.phone.gps.location.tracker.monetize.render.InlineSponsorStrip
import com.find.phone.gps.location.tracker.foundation.FoundationActivity
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.databinding.PageContactPickerBinding
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.visual.VisualPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class PeopleSelectActivity : FoundationActivity<PageContactPickerBinding>() {

    override val layoutId: Int = R.layout.page_contact_picker

    private val adapter = PeopleListAdapter(onLocate = { pick(it) }, onCall = { dial(it) })
    private val favorites = StarredListAdapter { pick(it) }
    private val prefs by lazy { SecurePrefsKeeper(this) }

    private var all: List<PersonPhone> = emptyList()
    private var account: String? = null
    private var descending = false

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        trackPermissionResult(Manifest.permission.READ_CONTACTS, granted)
        if (granted) onGranted() else presentPermissionState()
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.contactsRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }

        binding.rollContacts.layoutManager = LinearLayoutManager(this)
        binding.rollContacts.adapter = adapter
        binding.rollFavorites.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rollFavorites.adapter = favorites

        binding.searchInputVw.doAfterTextChanged {
            binding.padClearSearch.isVisible = !it.isNullOrEmpty()
            imprintFilter()
        }
        binding.padClearSearch.setOnClickListener { binding.searchInputVw.setText("") }
        binding.accountChipVw.setOnClickListener { presentAccountMenu() }
        binding.padTools.setOnClickListener { presentToolsMenu() }
        binding.padAllow.setOnClickListener { promptPermission() }
        listOf(binding.accountChipVw, binding.padTools, binding.padAllow).forEach { VisualTweens.pressScale(it) }
        prepareRail()

        if (hasPermission()) onGranted() else presentPermissionState()
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun promptPermission() {
        val perm = Manifest.permission.READ_CONTACTS
        when {
            !prefs.hasRequestedPermission(perm) -> {
                prefs.markPermissionRequested(perm)
                trackPermissionShow(perm)
                permission.launch(perm)
            }
            shouldShowRequestPermissionRationale(perm) -> {
                trackPermissionShow(perm)
                permission.launch(perm)
            }
            else -> runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (binding.permissionStateVw.isVisible && hasPermission()) onGranted()
    }

    private fun presentPermissionState() {
        binding.permissionStateVw.isVisible = true
        binding.listGroupVw.isVisible = false
        binding.railBoxVw.isVisible = false
    }

    private fun onGranted() {
        binding.permissionStateVw.isVisible = false
        binding.listGroupVw.isVisible = true
        paintAd()
        load()
    }

    private fun load() {
        binding.pbLoadingVw.isVisible = true
        binding.lblAccountCount.isVisible = false
        lifecycleScope.launch {
            all = withContext(Dispatchers.IO) { runCatching { HandsetPeople.load(this@PeopleSelectActivity) }.getOrDefault(emptyList()) }
            binding.pbLoadingVw.isVisible = false
            if (account != null && all.none { it.account == account }) account = null
            wireAccountChip()
            imprintFilter()
        }
    }

    private fun wireAccountChip() {
        val scope = all.filter { account == null || it.account == account }
        binding.lblAccount.text = account ?: getString(R.string.ds_contacts_all_accounts)
        binding.lblAccountCount.text = scope.distinctBy { it.contactId }.size.toString()
        binding.lblAccountCount.isVisible = true
    }

    private fun imprintFilter() {
        if (binding.pbLoadingVw.isVisible) return
        val query = binding.searchInputVw.text?.toString()?.trim().orEmpty()
        val needle = query.lowercase(Locale.getDefault())
        val digits = query.filter(Char::isDigit)
        val scoped = all.filter { account == null || it.account == account }
        var shown = if (query.isEmpty()) scoped else scoped.filter {
            it.name.lowercase(Locale.getDefault()).contains(needle) ||
                (digits.isNotEmpty() && it.number.filter(Char::isDigit).contains(digits))
        }
        if (descending) shown = shown.sortedWith(compareBy<PersonPhone> { it.letter == "#" }.thenByDescending { it.name.lowercase(Locale.getDefault()) })
        adapter.submitContacts(shown) {
            binding.rollContacts.scrollToPosition(0)
            composeRail()
        }

        val favs = scoped.filter { it.starred }.distinctBy { it.contactId }
        favorites.submitList(favs)
        binding.favoritesGroupVw.isVisible = query.isEmpty() && favs.isNotEmpty()
        binding.lblFavoritesCount.text = favs.size.toString()

        when {
            all.isEmpty() -> presentEmpty(R.string.ds_contacts_empty, R.string.ds_contacts_empty_body)
            shown.isEmpty() -> presentEmpty(R.string.ds_contacts_no_match, R.string.ds_contacts_no_match_body)
            else -> binding.emptyStateVw.isVisible = false
        }
        binding.railBoxVw.isVisible = shown.isNotEmpty()
    }

    private fun presentEmpty(title: Int, body: Int) {
        binding.lblEmpty.setText(title)
        binding.lblEmptyBody.setText(body)
        binding.emptyStateVw.isVisible = true
    }

    private fun presentAccountMenu() {
        val people = all.distinctBy { it.contactId }
        val counts = people.groupingBy { it.account }.eachCount()
        val popup = VisualPopup(this)
        popup.add(
            label = getString(R.string.ds_contacts_all_accounts),
            icon = R.drawable.vs_ic_contact_card,
            badge = people.size.toString(),
            selected = account == null,
        ) { selectAccount(null) }
        counts.keys.sorted().forEach { name ->
            popup.add(
                label = name,
                icon = when (name) {
                    PersonPhone.ACCOUNT_PHONE -> R.drawable.vs_ic_phone
                    PersonPhone.ACCOUNT_SIM -> R.drawable.vs_ic_contact_card
                    else -> R.drawable.vs_ic_globe
                },
                badge = counts.getValue(name).toString(),
                selected = account == name,
            ) { selectAccount(name) }
        }
        popup.show(binding.accountChipVw, Gravity.START)
    }

    private fun selectAccount(name: String?) {
        account = name
        wireAccountChip()
        imprintFilter()
    }

    private fun presentToolsMenu() {
        VisualPopup(this)
            .add(
                label = getString(if (descending) R.string.ds_contacts_sort_az else R.string.ds_contacts_sort_za),
                icon = R.drawable.vs_ic_grid,
            ) { descending = !descending; imprintFilter() }
            .add(label = getString(R.string.ds_contacts_refresh), icon = R.drawable.vs_ic_history) { load() }
            .show(binding.padTools, Gravity.END)
    }

    private fun pick(contact: PersonPhone) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_NUMBER, contact.number))
        finish()
    }

    private fun dial(contact: PersonPhone) {
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))) }
    }

    private fun paintAd() {
        val show = AdPrefsStore.getInstance(this).getBoolean("IsAdsON") && PageSponsorSettings.resolve(this, javaClass.simpleName).show
        binding.adCardVw.isVisible = show
        if (show) InlineSponsorStrip().presentNativeBannerNative(this, binding.adNativeFrameVw, binding.adShimmerVw)
    }

    private fun composeRail() {
        binding.railVw.removeAllViews()
        val font = ResourcesCompat.getFont(this, R.font.figtree_bold)
        adapter.letterPositions.keys.forEach { letter ->
            binding.railVw.addView(TextView(this).apply {
                text = letter
                gravity = Gravity.CENTER
                typeface = font
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(ContextCompat.getColor(context, R.color.ds_primary))
                setPadding(dp(4), dp(1), dp(4), dp(1))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun prepareRail() {
        binding.railVw.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val letters = adapter.letterPositions.keys.toList()
                    if (letters.isNotEmpty() && v.height > 0) {
                        val idx = ((event.y / v.height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        adapter.letterPositions[letters[idx]]?.let { pos ->
                            (binding.rollContacts.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.performClick(); true }
                else -> false
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_NUMBER = "number"

        fun intent(context: Context): Intent = Intent(context, PeopleSelectActivity::class.java)
    }
}
