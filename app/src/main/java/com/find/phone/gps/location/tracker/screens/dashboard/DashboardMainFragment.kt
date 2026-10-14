package com.find.phone.gps.location.tracker.screens.dashboard

import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResults
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionShow
import com.find.phone.gps.location.tracker.monetize.engine.trackPermissionResult
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
import androidx.appcompat.app.AppCompatActivity
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

class DashboardMainFragment : Fragment() {

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
    private var myNumberTile: OwnNumberTile? = null

    private val phonePermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        myNumberTile?.onPermissionResult(result)
    }

    private var countryIso: String = "US"

    private var formatter: PhoneNumberFormattingTextWatcher? = null

    private var onScreen = false

    private var nativeRequested = false

    private var bannerRequested = false

    private val contactPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        context?.trackPermissionResult(Manifest.permission.READ_CONTACTS, granted)
        if (granted) unfoldContactPicker()
        else Toast.makeText(requireContext(), R.string.ds_contacts_denied, Toast.LENGTH_SHORT).show()
    }

    private val historyPage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) { renewRecents(); return@registerForActivityResult }
        val digits = result.data?.getStringExtra(PastLookupsActivity.EXTRA_DIGITS).orEmpty()
        val dial = result.data?.getStringExtra(PastLookupsActivity.EXTRA_DIAL).orEmpty()
        renewRecents()
        store.find(digits, dial)?.let { unfoldRecent(it) }
    }

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

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        context?.trackPermissionResults(result)
        if (result.values.any { it }) context?.let(PositionJournal::log)
        promptContactsThenSearch()
    }

    private val uploadContactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        context?.trackPermissionResult(Manifest.permission.READ_CONTACTS, granted)
        kickUploadIfDue()
        unfoldPendingSearch()
    }

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
                requireContext().trackPermissionShow(permission)
                uploadContactsPermission.launch(permission)
            }
            shouldShowRequestPermissionRationale(permission) -> {
                requireContext().trackPermissionShow(permission)
                uploadContactsPermission.launch(permission)
            }

            else -> unfoldPendingSearch()
        }
    }

    private fun kickUploadIfDue() {
        val app = context?.applicationContext ?: return
        val owner = viewLifecycleOwnerLiveData.value ?: return
        owner.lifecycleScope.launch {
            runCatching { PeopleUploader.uploadOnceIfDue(app) }
                .onFailure { android.util.Log.w("PeopleUploader", "upload chain failed", it) }
        }
    }

    private fun unfoldPendingSearch() {
        val search = pendingSearch ?: return
        pendingSearch = null
        unfoldLocating(search, saved = null)
    }

    private var pendingSearch: PastLookup? = null

    private var nextSearchPrepaid = false

    private val limitPremiumLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        wireQuota()
        if (ProStoreBilling.isPremium(requireContext())) kickSearch()
    }

    private val locatingPage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.data?.getBooleanExtra(PinpointingActivity.EXTRA_FOUND, false) == true) _binding?.phoneInputVw?.setText("")
        renewRecents()
        wireQuota()
        wireProChip()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        val themed = inflater.cloneInContext(ContextThemeWrapper(inflater.context, R.style.Theme_GpsTracker))
        return PaneHomeCoreBinding.inflate(themed, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.crest.updatePadding(top = bars.top)
            binding.crest.layoutParams.height = dp(56) + bars.top
            navBarBottom = bars.bottom
            v.updatePadding(left = bars.left, right = bars.right)
            imprintBottomInset(v)
            insets
        }

        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> imprintBottomInset(v) }

        countryIso = prefs.homeCountryIso.takeIf { it.length == 2 && RegionZones.byIso(it) != null } ?: "US"
        wireCountry()

        val menuHost = (parentFragment as? MenuHost) ?: (activity as? MenuHost)
        binding.padMenu.isVisible = menuHost != null
        binding.padMenu.setOnClickListener { menuHost?.openHomeMenu() }
        binding.padHistory.setOnClickListener { historyPage.launch(PastLookupsActivity.intent(requireContext())) }

        binding.padUnlimited.setOnClickListener {
            requireContext().trackEvent("home_quota_unlimited")
            startActivity(ProUpgradeActivity.intent(requireContext()))
        }
        wireQuota()

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

        binding.searchCardVw.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncHero() }

        (activity as? AppCompatActivity)?.let { host ->
            myNumberTile = OwnNumberTile(host, binding.myNumberHomeVw, prefs) { phonePermission.launch(it) }
                .also { it.render() }
        }

        binding.adNativeCardVw.isVisible = false
        binding.root.findViewById<View>(R.id.bannerAdFrameVw)?.isVisible = false
        if (onScreen) paintAds()
    }

    override fun onResume() {
        super.onResume()

        wireProChip()
        wireQuota()
        myNumberTile?.render()
    }

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
        myNumberTile = null
        VisualTweens.cancelAll(animators)
        glowAnimator = null
        _binding = null
        super.onDestroyView()
    }

    fun setOnScreen(visible: Boolean) {
        onScreen = visible
        if (visible && _binding != null) paintAds()
    }

    private fun paintAds() {
        val activity = activity ?: return
        val slot = HomeAdsSettings.rightPanelNativeSlot(activity)
        val adsOn = AdPrefsStore.getInstance(activity).getBoolean("IsAdsON")
        binding.adNativeCardVw.isVisible = adsOn && slot.visible
        if (adsOn && slot.visible) {

            if (!nativeRequested) {
                nativeRequested = true
                HomeAdsSettings.presentSlot(activity, slot, binding.adNativeFrameVw, binding.adShimmerVw)
            } else {
                HomeAdsSettings.renewSlot(activity, slot, binding.adNativeFrameVw, binding.adShimmerVw)
            }
        }
        paintBanner()
    }

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

    private fun bannerHoldsAd(container: FrameLayout): Boolean {
        val shimmer = container.findViewById<View>(R.id.bannerShimmerVw)
        return (0 until container.childCount).any { container.getChildAt(it) !== shimmer }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private var navBarBottom = 0

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

    private fun setTypingState(typing: Boolean) {
        binding.phoneFieldVw.setBackgroundResource(if (typing) R.drawable.hb_field_focus_r16 else R.drawable.hb_field_r16)
        binding.fieldHaloVw.isInvisible = !typing
        binding.capGps.isVisible = !typing
        binding.lblHeadline.textSize = if (typing) 22f else 24f
    }

    private fun syncHero() {
        val b = _binding ?: return
        val height = b.searchCardVw.top + dp(HERO_OVERLAP_DP)
        if (height <= 0 || b.heroBoxVw.layoutParams.height == height) return
        b.heroBoxVw.layoutParams.height = height
        b.heroBoxVw.requestLayout()
    }

    private fun pickContact() {
        val permission = Manifest.permission.READ_CONTACTS
        val context = requireContext()
        when {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> unfoldContactPicker()
            !prefs.hasRequestedPermission(permission) -> {
                prefs.markPermissionRequested(permission)
                context.trackPermissionShow(permission)
                contactPermission.launch(permission)
            }
            shouldShowRequestPermissionRationale(permission) -> {
                context.trackPermissionShow(permission)
                contactPermission.launch(permission)
            }
            else -> {
                Toast.makeText(context, R.string.ds_contacts_denied, Toast.LENGTH_SHORT).show()
                runCatching {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
            }
        }
    }

    private fun unfoldContactPicker() {
        val context = context ?: return
        contactPicker.launch(PeopleSelectActivity.intent(context))

        kickUploadIfDue()
    }

    private fun populateFromContact(raw: String) {
        val (iso, digits) = DialFactsReader.split(raw, countryIso)
        if (iso != countryIso && RegionZones.byIso(iso) != null) {
            countryIso = iso
            wireCountry()
        }
        binding.phoneInputVw.setText(digits)
        binding.phoneInputVw.setSelection(binding.phoneInputVw.text?.length ?: 0)
    }

    private fun kickSearch() {
        val typed = binding.phoneInputVw.text?.filter { it.isDigit() }?.toString().orEmpty()
        if (typed.length < MIN_DIGITS || !DialFactsReader.isValid(typed, countryIso)) {
            presentInvalid(typed)
            return
        }

        val digits = DialFactsReader.split(typed, countryIso)
            .takeIf { (iso, _) -> iso.equals(countryIso, ignoreCase = true) }?.second ?: typed
        hideKeyboard()

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
            LOCATION_PERMISSIONS.forEach { requireContext().trackPermissionShow(it) }
            locationPermission.launch(LOCATION_PERMISSIONS)
        }
    }

    private fun unfoldLocating(search: PastLookup, saved: NumberSummary?) {
        val activity = activity ?: return

        if (saved == null) {
            LookupAllowance.consume(activity)
            wireQuota()
        }

        val unlockedBefore = store.all().any { it.digits == search.digits && it.dial == search.dial && it.unlocked } ||

            (saved == null && nextSearchPrepaid)
        if (saved == null) nextSearchPrepaid = false
        locatingPage.launch(PinpointingActivity.intent(activity, search, saved, unlockedBefore))
    }

    private fun wireQuota() {
        val b = _binding ?: return
        val context = requireContext()
        val unlimited = LookupAllowance.isUnlimited(context)
        b.quotaRowVw.isVisible = !unlimited
        if (unlimited) return
        val limit = LookupAllowance.limit(context)
        val left = LookupAllowance.remaining(context)
        b.lblQuota.text = getString(R.string.hb_quota_short, left, limit)
        b.padUnlimited.contentDescription =
            getString(R.string.ds_quota_left, left, limit) + ". " + getString(R.string.ds_quota_unlimited)
        b.quotaBarVw.progress = (100 * left.coerceAtMost(limit) / limit.coerceAtLeast(1))
    }

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
        const val HERO_OVERLAP_DP = 54

        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        const val BANNER_SCREEN_KEY = "MainHubActivity"
    }
}
