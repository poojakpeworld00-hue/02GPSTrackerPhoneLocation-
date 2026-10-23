package com.find.phone.gps.location.tracker.insights

import com.find.phone.gps.location.tracker.helpers.FaultRecoveryActivity
import com.find.phone.gps.location.tracker.homeshell.HomeShellPane
import com.find.phone.gps.location.tracker.monetize.render.SponsorBrowserActivity
import com.find.phone.gps.location.tracker.monetize.render.TipSheetActivity
import com.find.phone.gps.location.tracker.screens.MainHubActivity
import com.find.phone.gps.location.tracker.screens.alerts.AlarmListActivity
import com.find.phone.gps.location.tracker.screens.alerts.AlarmPopupActivity
import com.find.phone.gps.location.tracker.screens.appevent.InstallOutcomeActivity
import com.find.phone.gps.location.tracker.screens.battery.PowerStateActivity
import com.find.phone.gps.location.tracker.screens.boot.BootActivity
import com.find.phone.gps.location.tracker.screens.consent.OverlayConsentActivity
import com.find.phone.gps.location.tracker.screens.consent.PolicyConsentActivity
import com.find.phone.gps.location.tracker.screens.dashboard.DashboardMainFragment
import com.find.phone.gps.location.tracker.screens.dashboard.PastLookupsActivity
import com.find.phone.gps.location.tracker.screens.finder.MapExpandedActivity
import com.find.phone.gps.location.tracker.screens.finder.NumberInsightActivity
import com.find.phone.gps.location.tracker.screens.finder.PinpointingActivity
import com.find.phone.gps.location.tracker.screens.locale.LocalePickActivity
import com.find.phone.gps.location.tracker.screens.people.PeopleSelectActivity
import com.find.phone.gps.location.tracker.screens.pro.ProUpgradeActivity
import com.find.phone.gps.location.tracker.screens.simcard.OperatorDialCodesActivity
import com.find.phone.gps.location.tracker.screens.simcard.OwnNumberActivity
import com.find.phone.gps.location.tracker.screens.simcard.SendNumberActivity
import com.find.phone.gps.location.tracker.screens.simcard.TwinSimOverviewActivity
import com.find.phone.gps.location.tracker.screens.uninstall.UninstallProgressActivity
import com.find.phone.gps.location.tracker.screens.uninstall.UninstallReasonActivity
import com.find.phone.gps.location.tracker.screens.uninstall.UninstallSorryActivity
import com.find.phone.gps.location.tracker.screens.uninstall.UninstallThanksActivity
import com.find.phone.gps.location.tracker.screens.walkthrough.GuidedTourActivity
import com.find.phone.gps.location.tracker.setupflow.AlarmIntroActivity
import com.find.phone.gps.location.tracker.setupflow.HomeRoleStageActivity
import com.find.phone.gps.location.tracker.setupflow.HomeRoleTipActivity
import com.find.phone.gps.location.tracker.setupflow.WelcomeStageActivity
import io.homedeck.grid.screens.ConcealedAppsPane
import io.homedeck.grid.screens.HomeDeskPane
import io.homedeck.grid.screens.HomeRoleTipPane
import io.homedeck.grid.screens.HomeSettingsPane
import java.util.Locale

object ScreenTokens {

    private val TOKENS: Map<Class<*>, String> = mapOf(
        BootActivity::class.java to "splash",
        LocalePickActivity::class.java to "language",
        WelcomeStageActivity::class.java to "welcome",
        GuidedTourActivity::class.java to "onboarding",
        AlarmIntroActivity::class.java to "reminder_intro",
        HomeRoleStageActivity::class.java to "default_home",
        HomeRoleTipActivity::class.java to "default_home_tip",
        PolicyConsentActivity::class.java to "terms",
        ProUpgradeActivity::class.java to "premium",
        MainHubActivity::class.java to "home",
        DashboardMainFragment::class.java to "home_search",
        PinpointingActivity::class.java to "locating",
        NumberInsightActivity::class.java to "number_detail",
        MapExpandedActivity::class.java to "map_full",
        PastLookupsActivity::class.java to "history",
        PeopleSelectActivity::class.java to "contact_picker",
        OwnNumberActivity::class.java to "my_number",
        SendNumberActivity::class.java to "share_number",
        TwinSimOverviewActivity::class.java to "dual_sim",
        OperatorDialCodesActivity::class.java to "carrier_codes",
        AlarmListActivity::class.java to "reminder_list",
        AlarmPopupActivity::class.java to "reminder_alert",
        InstallOutcomeActivity::class.java to "package_result",
        PowerStateActivity::class.java to "charging_status",
        OverlayConsentActivity::class.java to "overlay_perm",
        TipSheetActivity::class.java to "overlay_guide",
        SponsorBrowserActivity::class.java to "promo_web",
        FaultRecoveryActivity::class.java to "crash_restart",
        HomeDeskPane::class.java to "launcher_home",
        HomeShellPane::class.java to "launcher_panel",
        HomeSettingsPane::class.java to "launcher_settings",
        ConcealedAppsPane::class.java to "launcher_hidden_apps",
        HomeRoleTipPane::class.java to "default_home_hint",
        UninstallSorryActivity::class.java to "uninstall_sorry",
        UninstallReasonActivity::class.java to "uninstall_reason",
        UninstallProgressActivity::class.java to "uninstall_progress",
        UninstallThanksActivity::class.java to "uninstall_thanks",
    )

    val FRAGMENTS: Set<Class<*>> = setOf(DashboardMainFragment::class.java, HomeShellPane::class.java)

    fun of(screen: Any): String = tokenFor(screen.javaClass)

    fun tokenFor(cls: Class<*>): String =
        TOKENS[cls] ?: cls.simpleName
            .removeSuffix("Activity").removeSuffix("Fragment").removeSuffix("Pane")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .lowercase(Locale.ROOT)
            .ifBlank { "screen" }
            .take(24)
}
