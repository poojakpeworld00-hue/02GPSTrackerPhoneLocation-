package com.find.phone.gps.location.tracker.grants

/**
 * Decides which [GrantPolicy]s apply to a given Activity.
 *
 * Matching is done on the Activity's *simple class name*, case-insensitively,
 * so Remote Config stays readable and refactors that only move a class between
 * packages don't break targeting.
 *
 * [LEGACY_NAMES] maps every name an earlier build used to the current class names,
 * so Remote Config documents written against any of them keep matching. Two
 * renames have happened across generations, and the table is chained: a first-generation name maps
 * straight to today's class, not to the intermediate one.
 *
 * The value is a list because one old name can now mean two screens: the original
 * app's single MainActivity became this app's AppHubActivity, and the launcher
 * module later brought its own MainActivity, which is now HomeDeckActivity. A rule
 * targeting "MainActivity" has always applied to both, and still does.
 *
 * Drop an entry once the server-side rule has been updated.
 */
object PageMatcher {

    private val LEGACY_NAMES: Map<String, List<String>> = mapOf(
        "ADDashboardActivity" to listOf("MonetizeGateActivity"),
        "ADHomeActivity" to listOf("MonetizeGateActivity"),
        "AdBeaconActivity" to listOf("MonetizeGateActivity"),
        "AdRelayActivity" to listOf("MonetizeGateActivity"),
        "AgreementActivity" to listOf("PolicyConsentActivity"),
        "AppCoreActivity" to listOf("MainHubActivity"),
        "AppHubActivity" to listOf("MainHubActivity"),
        "AppsPanelSurface" to listOf("AppTrayPane"),
        "BaseActivity" to listOf("FoundationActivity"),
        "BatteryActivity" to listOf("ChargeToolActivity"),
        "BatteryToolActivity" to listOf("ChargeToolActivity"),
        "BearingActivity" to listOf("NeedleToolActivity"),
        "BlockLedgerActivity" to listOf("BlockVaultActivity"),
        "BlockRosterActivity" to listOf("BlockVaultActivity"),
        "BlocklistActivity" to listOf("BlockVaultActivity"),
        "BootSplashActivity" to listOf("BootActivity"),
        "BrightToolActivity" to listOf("LumenToolActivity"),
        "CallBriefActivity" to listOf("CallReportActivity"),
        "CallDetailActivity" to listOf("CallReportActivity"),
        "CallInsightActivity" to listOf("CallReportActivity"),
        "CanvasActivity" to listOf("FoundationActivity"),
        "CarrierCodesActivity" to listOf("OperatorDialCodesActivity"),
        "CarrierFragment" to listOf("FoundationFragment"),
        "ChargingStatusActivity" to listOf("PowerStateActivity"),
        "ChronoActivity" to listOf("LapTimerActivity"),
        "CompassActivity" to listOf("NeedleToolActivity"),
        "ConsentActivity" to listOf("PolicyConsentActivity"),
        "ContactPickerActivity" to listOf("PeopleSelectActivity"),
        "CoreDeckActivity" to listOf("ShellHubActivity"),
        "CountdownActivity" to listOf("SandTimerActivity"),
        "CountryPickerActivity" to listOf("CountryDeckActivity"),
        "CrashRestartActivity" to listOf("FaultRecoveryActivity"),
        "DecibelActivity" to listOf("SoundToolActivity"),
        "DeckSettingsActivity" to listOf("StageSettingsActivity"),
        "DefaultHomeHintActivity" to listOf("HomeRoleTipActivity"),
        "DefaultHomeHintPanel" to listOf("HomeRoleTipPane"),
        "DefaultHomeStepActivity" to listOf("HomeRoleStageActivity"),
        "DialPadActivity" to listOf("NumPadActivity"),
        "DialerActivity" to listOf("NumPadActivity"),
        "DrawerSurface" to listOf("AppTrayFragment"),
        "DualSimDashboardActivity" to listOf("TwinSimOverviewActivity"),
        "EggTimerActivity" to listOf("SandTimerActivity"),
        "FlashToolActivity" to listOf("BeamToolActivity"),
        "FlashlightActivity" to listOf("BeamToolActivity"),
        "FloatAccessActivity" to listOf("OverlayConsentActivity"),
        "FloatGateActivity" to listOf("OverlayConsentActivity"),
        "FsiGateActivity" to listOf("FsiPortalActivity"),
        "FsiPermissionActivity" to listOf("FsiPortalActivity"),
        "FullScreenAccessActivity" to listOf("FsiPortalActivity"),
        "GreetingStepActivity" to listOf("WelcomeStageActivity"),
        "GuideSheetActivity" to listOf("TipSheetActivity"),
        "HeadingToolActivity" to listOf("NeedleToolActivity"),
        "HiddenIconsActivity" to listOf("VeiledAppsActivity"),
        "HiddenIconsPanel" to listOf("ConcealedAppsPane"),
        "HintSheetActivity" to listOf("TipSheetActivity"),
        "HistoryActivity" to listOf("PastLookupsActivity"),
        "HomeCoreFragment" to listOf("DashboardMainFragment"),
        "HomeDeckActivity" to listOf("HomeStageActivity"),
        "HomeRoleStepActivity" to listOf("HomeRoleStageActivity"),
        "HostActivity" to listOf("FoundationActivity"),
        "HostPanelSurface" to listOf("HostAppPane"),
        "IdentifyInsightActivity" to listOf("SearchBriefActivity"),
        "IdentifyTraceActivity" to listOf("SearchLogActivity"),
        "InboundCallActivity" to listOf("IncomingRingActivity"),
        "IncomingCallActivity" to listOf("IncomingRingActivity"),
        "IntroActivity" to listOf("GuidedTourActivity"),
        "KeypadActivity" to listOf("NumPadActivity"),
        "LangChooserActivity" to listOf("LocalePickActivity"),
        "LanguageActivity" to listOf("LocalePickActivity"),
        "LanguagePickActivity" to listOf("LocalePickActivity"),
        "LaunchActivity" to listOf("BootActivity"),
        "LauncherBasePanel" to listOf("HomeFoundationPane"),
        "LauncherPanel" to listOf("HomeDeskPane"),
        "LauncherPrefsPanel" to listOf("HomeSettingsPane"),
        "LauncherShellFragment" to listOf("HomeShellPane"),
        "LauncherSurface" to listOf("HomeDeskFragment"),
        "LevelActivity" to listOf("PlumbToolActivity"),
        "LevelToolActivity" to listOf("PlumbToolActivity"),
        "LightMeterActivity" to listOf("LumenToolActivity"),
        "LocaleActivity" to listOf("LocalePickActivity"),
        "LocatingActivity" to listOf("PinpointingActivity"),
        "LookupBriefActivity" to listOf("SearchBriefActivity"),
        "LookupDetailActivity" to listOf("SearchBriefActivity"),
        "LookupHistoryActivity" to listOf("SearchLogActivity"),
        "LookupLogActivity" to listOf("SearchLogActivity"),
        "LuxMeterActivity" to listOf("LumenToolActivity"),
        "MainActivity" to listOf("MainHubActivity", "HomeStageActivity"),
        "MapFullActivity" to listOf("MapExpandedActivity"),
        "MaskedAppsActivity" to listOf("VeiledAppsActivity"),
        "MyNumberActivity" to listOf("OwnNumberActivity"),
        "NoiseToolActivity" to listOf("SoundToolActivity"),
        "NumberDetailActivity" to listOf("NumberInsightActivity"),
        "OnboardingActivity" to listOf("GuidedTourActivity"),
        "OnboardingDefaultLauncherActivity" to listOf("HomeRoleStageActivity"),
        "OnboardingWelcomeActivity" to listOf("WelcomeStageActivity"),
        "OptionsDeckActivity" to listOf("PrefsHubActivity"),
        "OverlayGateActivity" to listOf("OverlayConsentActivity"),
        "OverlayGuideActivity" to listOf("TipSheetActivity"),
        "OverlayPermissionActivity" to listOf("OverlayConsentActivity"),
        "PackageResultActivity" to listOf("InstallOutcomeActivity"),
        "PowerGaugeActivity" to listOf("ChargeToolActivity"),
        "PreferencesActivity" to listOf("PrefsHubActivity"),
        "PremiumActivity" to listOf("ProUpgradeActivity"),
        "PrimerActivity" to listOf("GuidedTourActivity"),
        "PromoWebActivity" to listOf("SponsorBrowserActivity"),
        "RegionPickerActivity" to listOf("CountryDeckActivity"),
        "ReminderAlertActivity" to listOf("AlarmPopupActivity"),
        "ReminderIntroActivity" to listOf("AlarmIntroActivity"),
        "ReminderListActivity" to listOf("AlarmListActivity"),
        "RingScreenActivity" to listOf("IncomingRingActivity"),
        "RoleHintActivity" to listOf("HomeRoleTipActivity"),
        "ScreenBaseActivity" to listOf("FoundationActivity"),
        "SettingsActivity" to listOf("PrefsHubActivity", "StageSettingsActivity"),
        "ShareNumberActivity" to listOf("SendNumberActivity"),
        "ShellActivity" to listOf("MainHubActivity"),
        "ShellDeckActivity" to listOf("ShellHubActivity"),
        "SimCardActivity" to listOf("SimHubActivity"),
        "SimDeckActivity" to listOf("SimHubActivity"),
        "SimInfoActivity" to listOf("SimHubActivity"),
        "SimpleActivity" to listOf("ShellHubActivity"),
        "SoundMeterActivity" to listOf("SoundToolActivity"),
        "SpeedToolActivity" to listOf("PaceToolActivity"),
        "SpeedometerActivity" to listOf("PaceToolActivity"),
        "SplashActivity" to listOf("BootActivity"),
        "StartupActivity" to listOf("BootActivity"),
        "StopClockActivity" to listOf("LapTimerActivity"),
        "StopwatchActivity" to listOf("LapTimerActivity"),
        "TermsActivity" to listOf("PolicyConsentActivity"),
        "TermsGateActivity" to listOf("PolicyConsentActivity"),
        "TerritoryPickerActivity" to listOf("CountryDeckActivity"),
        "TiltActivity" to listOf("PlumbToolActivity"),
        "TimerActivity" to listOf("SandTimerActivity"),
        "ToolboxActivity" to listOf("GadgetsActivity"),
        "ToolsActivity" to listOf("GadgetsActivity"),
        "TorchActivity" to listOf("BeamToolActivity"),
        "TourActivity" to listOf("GuidedTourActivity"),
        "UtilityActivity" to listOf("GadgetsActivity"),
        "VelocityActivity" to listOf("PaceToolActivity"),
        "WelcomeStepActivity" to listOf("WelcomeStageActivity"),
        "WidgetsSurface" to listOf("WidgetPickerFragment"),
    )

    /**
     * True when a Remote Config screen name refers to [activitySimpleName], directly
     * or through [LEGACY_NAMES].
     *
     * Every name-keyed lookup in the app goes through here, so a class rename only
     * has to be recorded in [LEGACY_NAMES] once instead of being chased across the
     * permission engine, the splash primer and the per-screen ad config.
     */
    fun matches(configuredName: String, activitySimpleName: String): Boolean =
        configuredName.equals(activitySimpleName, ignoreCase = true) ||
            LEGACY_NAMES[configuredName]
                ?.any { it.equals(activitySimpleName, ignoreCase = true) } == true

    /**
     * The key in [keys] that refers to [activitySimpleName], or null. For config
     * objects keyed by screen name (`ScreenAds`), where the key on the server may be
     * a name from an earlier build.
     */
    fun keyFor(keys: Iterator<String>, activitySimpleName: String): String? {
        while (keys.hasNext()) {
            val key = keys.next()
            if (matches(key, activitySimpleName)) return key
        }
        return null
    }

    /**
     * Returns the enabled rules that target [activitySimpleName], preserving
     * the caller's ordering (the queue applies priority afterwards).
     */
    fun rulesFor(
        activitySimpleName: String,
        allRules: List<GrantPolicy>,
    ): List<GrantPolicy> = allRules.filter { rule ->
        rule.enabled && rule.activities.any { named -> matches(named, activitySimpleName) }
    }
}
