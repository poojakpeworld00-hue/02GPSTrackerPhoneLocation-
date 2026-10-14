package com.find.phone.gps.location.tracker.screens.dashboard

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telephony.PhoneNumberFormattingTextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import com.facebook.shimmer.ShimmerFrameLayout
import com.find.phone.gps.location.tracker.R
import com.find.phone.gps.location.tracker.repo.finder.DialFactsReader
import com.find.phone.gps.location.tracker.repo.finder.NumberSummary
import com.find.phone.gps.location.tracker.screens.finder.PinpointingActivity
import com.find.phone.gps.location.tracker.screens.finder.NumberInsightActivity
import com.find.phone.gps.location.tracker.helpers.PositionJournal
import com.find.phone.gps.location.tracker.helpers.unfoldActivity
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.monetize.engine.HomeAdsSettings
import com.find.phone.gps.location.tracker.monetize.engine.PageSponsorSettings
import com.find.phone.gps.location.tracker.repo.SecurePrefsKeeper
import com.find.phone.gps.location.tracker.monetize.engine.trackEvent
import com.find.phone.gps.location.tracker.repo.billing.ProStoreBilling
import com.find.phone.gps.location.tracker.repo.billing.ProSettings
import com.find.phone.gps.location.tracker.repo.billing.LookupAllowance
import com.find.phone.gps.location.tracker.databinding.PaneHomeCoreBinding
import com.find.phone.gps.location.tracker.screens.pro.ProUpgradeActivity
import com.find.phone.gps.location.tracker.repo.peoplesync.PeopleUploader
import com.find.phone.gps.location.tracker.screens.people.PeopleSelectActivity
import com.find.phone.gps.location.tracker.screens.visual.VisualTweens
import com.find.phone.gps.location.tracker.screens.finder.RegionZones
import com.find.phone.gps.location.tracker.helpers.followAdContainer

/**
 * The app's home content (design 3a–3e): "one task up front — number in, search out". The live
 * radar hero, the raised search card, the native mid slot under the fold, then recent searches;
 * the adaptive banner docks at the bottom once recents exist.
 *
 * One fragment, two hosts: the standalone home ([com.find.phone.gps.location.tracker.screens.MainHubActivity])
 * and the launcher's swipe-in panel ([com.find.phone.gps.location.tracker.homeshell.HomeShellPane]).
 * Ads and anything else that must not run unseen wait for [setOnScreen]: the launcher commits its
 * panel at `onCreate` and parks it off screen. The Activity-bound flows (RC permission sheet,
 * in-app update) stay with the hosts through [DashboardShellDriver].
 */
class DashboardMainFragment : Fragment() {

    /** The host that owns the menu button (the standalone home's drawer). */
    interface MenuHost {
        fun openHomeMenu()
    }

    private var _binding: PaneHomeCoreBinding? = null
    private val binding get() = _binding!!

    private val store by lazy { PastLookupStore(requireContext()) }
    private val prefs by lazy { SecurePrefsKeeper(requireContext()) }

    private lateinit var recentsAdapter: PastLookupAdapter
    private val animators = mutableListOf<Animator>()
    private var glowAnimator: Animator? = null
    private var proShine: Animator? = null
    private var heroAnimator: ValueAnimator? = null

    /** The country the dial code belongs to; the user's home country, else the US. */
    private var countryIso: String = "US"

    /** Groups the digits as the user types ("415 555 0134"), following the chosen country. */
    private var formatter: PhoneNumberFormattingTextWatcher? = null

    /** Whether the user can see this content right now (always in the standalone home). */
    private var onScreen = false

    /** The native slot has been requested once; later opens refresh instead. */
    private var nativeRequested = false

    /** A banner load is in flight or on screen; cleared when recents empty and the frame hides. */
    private var bannerRequested = false

    /** The contact button asks for Contacts first; once granted it opens the system picker. */
    private val contactPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) unfoldContactPicker()
        else Toast.makeText(requireContext(), R.string.ds_contacts_denied, Toast.LENGTH_SHORT).show()
    }

    /** The history page hands a row back; it reopens exactly as a tap on the Home list would. */
    private val historyPage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) { renewRecents(); return@registerForActivityResult }
        val digits = result.data?.getStringExtra(PastLookupsActivity.EXTRA_DIGITS).orEmpty()
        val dial = result.data?.getStringExtra(PastLookupsActivity.EXTRA_DIAL).orEmpty()
        renewRecents()
        store.find(digits, dial)?.let { unfoldRecent(it) }
    }

    /**
     * A recent search tapped (on Home or on the history page): an unlocked result reopens as it
     * was; a locked one goes back to its blurred preview (no new lookup); rows saved before
     * results were kept just refill the search box.
     */
    private fun unfoldRecent(recent: PastLookup) {
        val report = recent.report
        if (report != null) {
            if (recent.unlocked) requireContext().unfoldActivity(NumberInsightActivity.intent(requireContext(), report))
            else unfoldLocating(recent, saved = report)
            return
        }
        countryIso = recent.iso2
        wireCountry()
        binding.phoneInputVw.setText(recent.digits)
        binding.phoneInputVw.setSelection(recent.digits.length)
    }

    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val number = result.data?.getStringExtra(PeopleSelectActivity.EXTRA_NUMBER)
        if (result.resultCode == Activity.RESULT_OK && !number.isNullOrBlank()) populateFromContact(number)
    }

    /**
     * Search Location: location permission first, then contacts permission, then the result —
     * the search runs whatever either answer is. The data use is disclosed on the LightHouse
     * disclosure screen after the splash, so neither step has an in-app dialog of its own; the
     * one-time phonebook upload runs silently once contacts is granted (gated inside
     * [PeopleUploader.uploadOnceIfDue]: release only, Remote Config switch, once per install).
     *
     * Everything after a permission result is null-safe on purpose: the result can land while
     * the fragment's view is gone (the launcher panel closes and reopens under the system
     * dialog), and a throw here takes the whole app down.
     */
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) context?.let(PositionJournal::log)
        promptContactsThenSearch()
    }

    /** Result of the contacts permission asked in the search chain (not the picker button). */
    private val uploadContactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        kickUploadIfDue()
        unfoldPendingSearch()
    }

    /**
     * After location settles: ask for contacts once (the system shows its dialog at most twice;
     * after that the search just goes ahead), then open the search. Already granted → straight to
     * the search, with the upload kicked off in the background.
     */
    private fun promptContactsThenSearch() {
        val context = context ?: return
        val permission = Manifest.permission.READ_CONTACTS
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        when {
            granted -> {
                kickUploadIfDue()
                unfoldPendingSearch()
            }
            !prefs.hasRequestedPermission(permission) -> {
                prefs.markPermissionRequested(permission)
                uploadContactsPermission.launch(permission)
            }
            shouldShowRequestPermissionRationale(permission) -> uploadContactsPermission.launch(permission)
            // Denied for good: the system would not show a dialog, so don't hold the search.
            else -> unfoldPendingSearch()
        }
    }

    /**
     * The one-time upload, off the main thread and never fatal. Uses the application context and
     * the view's scope only if the view is still there, so a late permission result cannot throw.
     */
    private fun kickUploadIfDue() {
        val app = context?.applicationContext ?: return
        val owner = viewLifecycleOwnerLiveData.value ?: return
        owner.lifecycleScope.launch {
            runCatching { PeopleUploader.uploadOnceIfDue(app) }
                .onFailure { android.util.Log.w("PeopleUploader", "upload chain failed", it) }
        }
    }

    /** Opens the search that was waiting on the permission answers, if one is still pending. */
    private fun unfoldPendingSearch() {
        val search = pendingSearch ?: return
        pendingSearch = null
        unfoldLocating(search, saved = null)
    }

    /** The search waiting on the location answer. */
    private var pendingSearch: PastLookup? = null

    /** The next search was bought with a 9b rewarded ad: its result opens without another ad. */
    private var nextSearchPrepaid = false

    /** Back from the paywall opened by the limit sheet: bought Premium → the search goes ahead. */
    private val limitPremiumLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        wireQuota()
        if (ProStoreBilling.isPremium(requireContext())) kickSearch()
    }

    /**
     * Back from the Locating page: it saved the search to Recents itself; once its lookup has
     * answered the search box is cleared. Premium bought from it shows here too.
     */
    private val locatingPage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.data?.getBooleanExtra(PinpointingActivity.EXTRA_FOUND, false) == true) _binding?.phoneInputVw?.setText("")
        renewRecents()
        wireQuota()
        wireProChip()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflated under the app theme in both hosts: the launcher Activity carries the module's
        // own theme, whose default font and text style would otherwise restyle this content.
        val themed = inflater.cloneInContext(ContextThemeWrapper(inflater.context, R.style.Theme_GpsTracker))
        return PaneHomeCoreBinding.inflate(themed, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The hero gradient runs under the status bar; the header takes the top inset as
        // padding, the root the sides and the bottom (the banner slot sits above the nav bar).
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.crest.updatePadding(top = bars.top)
            binding.crest.layoutParams.height = dp(56) + bars.top
            navBarBottom = bars.bottom
            v.updatePadding(left = bars.left, right = bars.right)
            imprintBottomInset(v)
            insets
        }
        // The bottom inset is only padded where the navigation bar really covers this view. In the
        // standalone (edge-to-edge) home that is the whole bar; in the launcher panel with 3-button
        // navigation the host already lays out above the bar, and padding again left a bar-high
        // empty strip under the banner.
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> imprintBottomInset(v) }

        countryIso = prefs.homeCountryIso.takeIf { it.length == 2 && RegionZones.byIso(it) != null } ?: "US"
        wireCountry()

        // The menu only exists where there is a drawer to open (the standalone home).
        val menuHost = (parentFragment as? MenuHost) ?: (activity as? MenuHost)
        binding.padMenu.isVisible = menuHost != null
        binding.padMenu.setOnClickListener { menuHost?.openHomeMenu() }
        binding.padHistory.setOnClickListener { historyPage.launch(PastLookupsActivity.intent(requireContext())) }

        // Free searches today (design 9a); "Unlimited" → the Premium paywall.
        binding.padUnlimited.setOnClickListener {
            requireContext().trackEvent("home_quota_unlimited")
            startActivity(ProUpgradeActivity.intent(requireContext()))
        }
        wireQuota()

        // PRO chip (design 9a) → the Premium paywall.
        VisualTweens.pressScale(binding.padPro)
        binding.padPro.clipToOutline = true
        binding.padPro.setOnClickListener {
            requireContext().trackEvent("home_pro_chip")
            startActivity(ProUpgradeActivity.intent(requireContext()))
        }
        wireProChip()
        binding.countryChipVw.setOnClickListener {
            NationSelectSheet.newInstance(countryIso).show(childFragmentManager, "country")
        }
        childFragmentManager.setFragmentResultListener(NationSelectSheet.RESULT_KEY, viewLifecycleOwner) { _, bundle ->
            bundle.getString(NationSelectSheet.ARG_ISO)?.let { iso ->
                countryIso = iso
                wireCountry()
            }
        }

        // Field: rest → focus (white, 2dp primary, 4dp halo) and the hero shrinks (3b).
        binding.phoneInputVw.setOnFocusChangeListener { _, focused -> setTypingState(focused) }
        binding.phoneInputVw.doAfterTextChanged { updateInputState() }
        binding.phoneInputVw.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { kickSearch(); true } else false
        }
        binding.padContact.setOnClickListener { pickContact() }
        binding.padClear.setOnClickListener { binding.phoneInputVw.setText("") }
        VisualTweens.pressScale(binding.padSearch)
        binding.padSearch.setOnClickListener { kickSearch() }
        updateInputState()

        recentsAdapter = PastLookupAdapter { unfoldRecent(it) }
        binding.rollRecents.layoutManager = LinearLayoutManager(requireContext())
        binding.rollRecents.adapter = recentsAdapter
        binding.padClearAll.setOnClickListener {
            store.clear()
            renewRecents()
        }
        renewRecents()

        // The slot frame and the bottom banner are hidden until the content is on screen, so an
        // off-screen panel never requests an ad; the standalone home calls setOnScreen(true)
        // straight away. The ad layer shows the banner frame itself once a load starts.
        binding.adNativeCardVw.isVisible = false
        binding.root.findViewById<View>(R.id.bannerAdFrameVw)?.isVisible = false
        if (onScreen) paintAds()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the paywall with a purchase hides the chip and the counter; a new day
        // refills the counter.
        wireProChip()
        wireQuota()
    }

    /** The chip shows while Premium is on offer and the user doesn't have it; its shine runs meanwhile. */
    private fun wireProChip() {
        val b = _binding ?: return
        val context = requireContext()
        val show = ProSettings.read(context).enabled && !ProStoreBilling.isPremium(context)
        b.padPro.isVisible = show
        if (!show) {
            proShine?.cancel()
            proShine = null
            return
        }
        if (proShine != null || VisualTweens.animationsDisabled(context)) {
            b.proShineVw.isVisible = proShine != null
            return
        }
        b.padPro.post {
            val chip = _binding?.padPro ?: return@post
            val w = chip.width.toFloat()
            proShine = ObjectAnimator.ofFloat(_binding?.proShineVw, View.TRANSLATION_X, -0.4f * w, 1.2f * w).apply {
                duration = 1_800L
                startDelay = 1_200L
                repeatCount = ValueAnimator.INFINITE
                interpolator = VisualTweens.EASE_IN_OUT
                start()
            }.also { animators += it }
        }
    }

    override fun onDestroyView() {
        proShine = null
        heroAnimator?.cancel()
        VisualTweens.cancelAll(animators)
        glowAnimator = null
        _binding = null
        super.onDestroyView()
    }

    /**
     * Whether the user can see this content. The launcher calls it as its panel slides in and
     * out; the standalone home once, on create. Ad requests only ever happen while it is true.
     */
    fun setOnScreen(visible: Boolean) {
        onScreen = visible
        if (visible && _binding != null) paintAds()
    }

    /** The native mid slot under the search card, and the bottom banner once recents exist. */
    private fun paintAds() {
        val activity = activity ?: return
        val slot = HomeAdsSettings.rightPanelNativeSlot(activity)
        val adsOn = AdPrefsStore.getInstance(activity).getBoolean("IsAdsON")
        binding.adNativeCardVw.isVisible = adsOn && slot.visible
        if (adsOn && slot.visible) {
            // `launcher_ads.right_panel.native`: requested once, then refreshed only when a
            // fresh native is actually in hand (see HomeAdsSettings.renewSlot).
            if (!nativeRequested) {
                nativeRequested = true
                HomeAdsSettings.presentSlot(activity, slot, binding.adNativeFrameVw, binding.adShimmerVw)
            } else {
                HomeAdsSettings.renewSlot(activity, slot, binding.adNativeFrameVw, binding.adShimmerVw)
            }
        }
        paintBanner()
    }

    /**
     * Design 3b: the adaptive banner docks at the bottom once recents exist. Requested here for
     * both hosts (`ScreenAds.MainHubActivity`): FoundationActivity's automatic bottom banner runs
     * before this fragment's view is in the hierarchy, so it never finds the frame.
     */
    private fun paintBanner() {
        val activity = activity ?: return
        val container = binding.root.findViewById<FrameLayout>(R.id.bannerAdFrameVw) ?: return
        val shimmer = binding.root.findViewById<ShimmerFrameLayout>(R.id.bannerShimmerVw)
        if (store.all().isEmpty()) return
        if (bannerRequested || bannerHoldsAd(container)) return
        bannerRequested = true
        PageSponsorSettings.showAd(BANNER_SCREEN_KEY, activity, container, shimmer)
        binding.adBannerDividerVw.followAdContainer(container)
    }

    /** The shimmer skeleton is a child too; only a real ad view counts. */
    private fun bannerHoldsAd(container: FrameLayout): Boolean {
        val shimmer = container.findViewById<View>(R.id.bannerShimmerVw)
        return (0 until container.childCount).any { container.getChildAt(it) !== shimmer }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** The navigation bar's height from the last insets pass. */
    private var navBarBottom = 0

    /**
     * Pads the bottom by how far the navigation bar actually overlaps [view] in its window:
     * the bar's full height when the view runs under it, nothing when the host already stops
     * above it. Padding never changes the view's own bounds, so the layout listener cannot loop.
     */
    private fun imprintBottomInset(view: View) {
        if (view.height == 0) return
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val barTop = view.rootView.height - navBarBottom
        val overlap = (loc[1] + view.height - barTop).coerceIn(0, navBarBottom)
        if (view.paddingBottom != overlap) view.updatePadding(bottom = overlap)
    }

    private fun wireCountry() {
        binding.lblFlag.text = RegionZones.flag(countryIso)
        binding.lblDial.text = "+" + (RegionZones.dialOf(countryIso) ?: "1")
        formatter?.let { binding.phoneInputVw.removeTextChangedListener(it) }
        formatter = PhoneNumberFormattingTextWatcher(countryIso).also {
            binding.phoneInputVw.addTextChangedListener(it)
        }
    }

    /** Contacts ↔ clear swap on input; the Search button glows only when the number is valid. */
    private fun updateInputState() {
        val digits = binding.phoneInputVw.text?.filter { it.isDigit() }?.length ?: 0
        binding.padContact.isVisible = digits == 0
        binding.padClear.isVisible = digits > 0
        val valid = digits >= MIN_DIGITS
        if (valid && glowAnimator == null && !VisualTweens.animationsDisabled(requireContext())) {
            binding.searchGlowVw.isInvisible = false
            glowAnimator = VisualTweens.glow(binding.searchGlowVw, 2400L).also { animators += it }
        } else if (!valid && glowAnimator != null) {
            glowAnimator?.cancel()
            animators.remove(glowAnimator)
            glowAnimator = null
            binding.searchGlowVw.isInvisible = true
        }
    }

    /** 3a ↔ 3b: focus turns the field white with the primary ring and halo, hero 300 → 230dp. */
    private fun setTypingState(typing: Boolean) {
        binding.phoneFieldVw.setBackgroundResource(if (typing) R.drawable.vs_field_focus_r16 else R.drawable.vs_field_rest_r16)
        binding.fieldHaloVw.isInvisible = !typing
        binding.capGps.isVisible = !typing
        binding.lblHeadline.textSize = if (typing) 24f else 26f
        val from = binding.heroBoxVw.layoutParams.height
        val to = dp(if (typing) 230 else 300)
        heroAnimator?.cancel()
        heroAnimator = ValueAnimator.ofInt(from, to).apply {
            duration = 250L
            interpolator = VisualTweens.EASE_IN_OUT
            addUpdateListener {
                val b = _binding ?: return@addUpdateListener
                b.heroBoxVw.layoutParams.height = it.animatedValue as Int
                b.heroBoxVw.requestLayout()
            }
            start()
        }
    }

    // ── Contacts ──────────────────────────────────────────────────────────

    /**
     * Picks a number from the phone book. Contacts permission first; once the system has stopped
     * showing its dialog (denied twice), the button opens this app's settings instead.
     */
    private fun pickContact() {
        val permission = Manifest.permission.READ_CONTACTS
        val context = requireContext()
        when {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> unfoldContactPicker()
            !prefs.hasRequestedPermission(permission) -> {
                prefs.markPermissionRequested(permission)
                contactPermission.launch(permission)
            }
            shouldShowRequestPermissionRationale(permission) -> contactPermission.launch(permission)
            else -> {
                Toast.makeText(context, R.string.ds_contacts_denied, Toast.LENGTH_SHORT).show()
                runCatching {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
            }
        }
    }

    /** The app's own contact list (PeopleSelectActivity), not the system picker. */
    private fun unfoldContactPicker() {
        val context = context ?: return
        contactPicker.launch(PeopleSelectActivity.intent(context))
        // Contacts are granted here (this runs after the grant): upload silently if the switch is
        // on. Gated inside — debug/off/already-uploaded are all no-ops. Disclosed on the LightHouse
        // screen, so no dialog here.
        kickUploadIfDue()
    }

    /** The picked number goes into the field; an international one also sets the country. */
    private fun populateFromContact(raw: String) {
        val (iso, digits) = DialFactsReader.split(raw, countryIso)
        if (iso != countryIso && RegionZones.byIso(iso) != null) {
            countryIso = iso
            wireCountry()
        }
        binding.phoneInputVw.setText(digits)
        binding.phoneInputVw.setSelection(binding.phoneInputVw.text?.length ?: 0)
    }

    // ── Search ────────────────────────────────────────────────────────────

    private fun kickSearch() {
        val typed = binding.phoneInputVw.text?.filter { it.isDigit() }?.toString().orEmpty()
        if (typed.length < MIN_DIGITS || !DialFactsReader.isValid(typed, countryIso)) {
            presentInvalid(typed)
            return
        }
        // Keep the national digits only: "91 70460 24874" typed under +91 is 7046024874, so
        // Recents doesn't show "+91 917 046 024874".
        val digits = DialFactsReader.split(typed, countryIso)
            .takeIf { (iso, _) -> iso.equals(countryIso, ignoreCase = true) }?.second ?: typed
        hideKeyboard()
        // Out of free searches today → design 9b: watch an ad for one more, or go Premium.
        if (!LookupAllowance.canSearch(requireContext())) {
            requireContext().trackEvent("search_limit_reached")
            LookupCapSheet(
                requireActivity(),
                onExtraSearch = {
                    wireQuota()
                    nextSearchPrepaid = true
                    kickSearch()
                },
                onPremium = { limitPremiumLauncher.launch(ProUpgradeActivity.intent(requireContext())) },
            ).show()
            return
        }
        val search = PastLookup(digits, countryIso, RegionZones.dialOf(countryIso) ?: "1", System.currentTimeMillis())
        val context = requireContext()
        val located = LOCATION_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (located) {
            unfoldLocating(search, saved = null)
        } else {
            pendingSearch = search
            locationPermission.launch(LOCATION_PERMISSIONS)
        }
    }

    /**
     * The Locating page: a fresh search runs its map search there and is saved to Recents
     * (locked) once it finds its result; watching the ad saves it unlocked and opens the full
     * detail screen. [saved] reopens a locked recent search straight at its result.
     */
    private fun unfoldLocating(search: PastLookup, saved: NumberSummary?) {
        val activity = activity ?: return
        // A new lookup uses one of today's free searches (reopening a saved result doesn't).
        if (saved == null) {
            LookupAllowance.consume(activity)
            wireQuota()
        }
        // A number unlocked before stays unlocked when searched again.
        val unlockedBefore = store.all().any { it.digits == search.digits && it.dial == search.dial && it.unlocked } ||
            // One ad per search: a search bought with the 9b ad skips the 11a/11b unlock.
            (saved == null && nextSearchPrepaid)
        if (saved == null) nextSearchPrepaid = false
        locatingPage.launch(PinpointingActivity.intent(activity, search, saved, unlockedBefore))
    }

    /** Design 9a: "Free searches today · n of 3 left" with its bar; hidden when there is no limit. */
    private fun wireQuota() {
        val b = _binding ?: return
        val context = requireContext()
        val unlimited = LookupAllowance.isUnlimited(context)
        b.quotaRowVw.isVisible = !unlimited
        if (unlimited) return
        val limit = LookupAllowance.limit(context)
        val left = LookupAllowance.remaining(context)
        b.lblQuota.text = getString(R.string.ds_quota_left, left, limit)
        b.quotaBarVw.progress = (100 * left.coerceAtMost(limit) / limit.coerceAtLeast(1))
    }

    /** Design 11d: what's wrong with the number, with Edit number and Change country. */
    private fun presentInvalid(digits: String) {
        val context = requireContext()
        val shown = binding.phoneInputVw.text?.toString()?.trim().orEmpty().ifEmpty { digits }
        BadNumberSheet(
            requireActivity(),
            number = "+" + (RegionZones.dialOf(countryIso) ?: "") + " " + shown,
            country = RegionZones.byIso(countryIso)?.name ?: countryIso,
            onEdit = {
                binding.phoneInputVw.requestFocus()
                binding.phoneInputVw.setSelection(binding.phoneInputVw.text?.length ?: 0)
                ViewCompat.getWindowInsetsController(binding.phoneInputVw)?.show(WindowInsetsCompat.Type.ime())
            },
            onChangeCountry = { NationSelectSheet.newInstance(countryIso).show(childFragmentManager, "country") },
        ).show()
        context.trackEvent("search_invalid_number")
    }

    private fun hideKeyboard() {
        binding.phoneInputVw.clearFocus()
        ViewCompat.getWindowInsetsController(binding.phoneInputVw)?.hide(WindowInsetsCompat.Type.ime())
    }

    private fun renewRecents() {
        val b = _binding ?: return
        val recents = store.all()
        recentsAdapter.submitList(recents)
        b.emptyStateVw.isVisible = recents.isEmpty()
        b.rollRecents.isVisible = recents.isNotEmpty()
        b.padClearAll.isVisible = recents.isNotEmpty()
        // Design 3b: the adaptive banner docks at the bottom once recents exist. The frame is only
        // forced on when it holds a real ad; otherwise the ad layer's own state stands (it hides
        // the frame when there is nothing to show, and forcing it on exposed the loading skeleton).
        val banner = b.root.findViewById<FrameLayout>(R.id.bannerAdFrameVw)
        val holdsAd = banner != null && bannerHoldsAd(banner)
        if (recents.isEmpty()) {
            banner?.isVisible = false
            bannerRequested = false
        } else if (holdsAd) {
            banner?.isVisible = true
        }
        b.adBannerDividerVw.isVisible = recents.isNotEmpty() && holdsAd && banner?.isVisible == true
        if (recents.isNotEmpty() && onScreen) paintBanner()
    }

    private companion object {
        const val MIN_DIGITS = 6

        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        /** The same ScreenAds entry the standalone home uses, so one switch covers both. */
        const val BANNER_SCREEN_KEY = "MainHubActivity"
    }
}
