# Analytics events — GPS Tracker Phone Location

Debug builds log to logcat (tag `AdEvents`) only; release builds send to Firebase Analytics.

## Screen events (all screens, automatic — `insights/ScreenInsights.kt`)
`{token}_open` (once per creation), `{token}_back_press`, `{token}_home_press`, `{token}_recents_press`.

Tokens: `splash`, `language`, `welcome`, `onboarding`, `reminder_intro`, `default_home`, `default_home_tip`, `terms`, `home`, `home_search`, `locating`, `number_detail`, `map_full`, `history`, `contact_picker`, `my_number`, `share_number`, `dual_sim`, `carrier_codes`, `reminder_list`, `reminder_alert`, `package_result`, `charging_status`, `overlay_perm`, `overlay_guide`, `promo_web`, `crash_restart`, `launcher_home`, `launcher_panel`, `launcher_settings`, `launcher_hidden_apps`, `default_home_hint`, `uninstall_sorry`, `uninstall_reason`, `uninstall_progress`, `uninstall_thanks`

## Ad lifecycle (`insights/AdPulse.kt`) — params `screen`, `network`, `error_code`
`ad_{type}_{phase}` · type: banner, native, native_banner, interstitial, interstitial_back, app_open, rewarded, splash_app_open, splash_interstitial · phase: req, loaded, no_fill, failed, impression, clicked (language screen natives only), show, show_failed (interstitial_back), close. Plus `ad_splash_timeout` (splash ad not loaded within RC `splash_ad_wait_ms`; app continued without it). Revenue: reserved `ad_impression` from AdMob `OnPaidEventListener` on every Google placement (EarningsTracker.trackPaidEvent) with params `ad_platform`, `ad_source`, `ad_format`, `ad_unit_name`, `value`, `currency`, `precision`, `placement` (screen token, or `splash`), `segment` (paid/organic).

## Permissions
`Permission_<NAME>_Show` / `_Allow` / `_Deny` / `_Bypass` (no prompt needed), plus `PermissionSheet_*`.

## Literal events in code
| Event | Where |
|---|---|
| `Reminder_Access_Settings` | alarms/gate/AlarmGrant.kt |
| `Reminder_Prompt_Show` | alarms/gate/AlarmGrant.kt |
| `Reminder_Prompt_Allow` | alarms/gate/AlarmGrant.kt |
| `reminder_prompt_dismiss` | alarms/gate/AlarmGrant.kt (FSI prompt closed without Allow) |
| `reminder_fsi_granted` / `reminder_fsi_denied` | alarms/gate/AlarmGrant.kt (FSI access state on return from Settings) |
| `reminder_toggle_on` / `reminder_toggle_off` | screens/dashboard/DashboardSideMenu.kt (drawer Reminders master switch) |
| `reminder_notif_denied` | screens/dashboard/DashboardSideMenu.kt (POST_NOTIFICATIONS denied while switching on; switch reverts) |
| `drawer_language` / `drawer_reminders` / `drawer_rate` / `drawer_share` / `drawer_privacy` / `drawer_terms` / `drawer_delete_contacts` | screens/dashboard/DashboardSideMenu.kt (row taps) |
| `un_open_shortcut` | screens/boot/BootActivity.kt (launch from the launcher "Uninstall" shortcut) |
| `un_language_next` | screens/locale/LocalePickActivity.kt (uninstall advance route: Done / Back) |
| `un_sorry_search` / `un_sorry_my_number` / `un_sorry_reminders` | screens/uninstall/UninstallSorryActivity.kt (quick fix tapped → back into the app) |
| `un_sorry_keep` / `un_sorry_still` | screens/uninstall/UninstallSorryActivity.kt ("Don't uninstall yet" / "Still want to uninstall" or Back) |
| `un_reason_1` … `un_reason_6` | screens/uninstall/UninstallReasonActivity.kt (reason selected when continuing: 1 location, 2 notifications, 3 ads, 4 not needed, 5 battery, 6 other app) |
| `un_tip_location` / `un_tip_notif` | screens/uninstall/UninstallReasonActivity.kt (tip button: location permission / notification settings) |
| `un_uninstall_anyway` / `un_keep_app` | screens/uninstall/UninstallReasonActivity.kt |
| `un_thanks_continue` / `un_thanks_auto` | screens/uninstall/UninstallThanksActivity.kt (Continue or Back / `thanks_delay` timer) |
| `un_app_info_opened` / `un_app_closed` | screens/uninstall/UninstallThanksActivity.kt (simple route end: App info / advance route end: app closed) |
| `${com.find.phone.gps.location.tracker.insights.ScreenTokens.of(this)}_open` | foundation/FoundationFragment.kt |
| `Permission_${spec.androidPermission.substringAfterLast('.')}_Bypass` | grants/GrantOrchestrator.kt |
| `Permission_${shortName}_Show` | grants/GrantOrchestrator.kt |
| `PermissionSheet_Show` | grants/GrantPanelDialog.kt |
| `PermissionSheet_AllowAll` | grants/GrantPanelDialog.kt |
| `PermissionSheet_AllDone` | grants/GrantPanelDialog.kt |
| `PermissionSheet_Skip` | grants/GrantPanelDialog.kt |
| `PermissionSheet_Close` | grants/GrantPanelDialog.kt (outside tap / swipe-down / back; not its own buttons) |
| `PermissionSheet_Ask_${page.key}` | grants/GrantPanelDialog.kt |
| `ad_${type}_$phase` | insights/AdPulse.kt |
| `${ScreenTokens.of(screen)}_$suffix` | insights/ScreenInsights.kt |
| `${ScreenTokens.of(activity)}_open` | insights/ScreenInsights.kt |
| `${ScreenTokens.of(owner)}_back_press` | insights/ScreenInsights.kt |
| `${ScreenTokens.of(f)}_open` | insights/ScreenInsights.kt |
| `Permission_${shortName}_${if (granted) ` | monetize/engine/EarningsTracker.kt |
| `reward_nofill` | monetize/render/BonusAdSponsor.kt |
| `reward_load_error_${error.code}` | monetize/render/BonusAdSponsor.kt |
| `reward_retry_exhausted` | monetize/render/BonusAdSponsor.kt |
| `reward_cooldown_free` | monetize/render/BonusAdSponsor.kt |
| `reward_cooldown_wait` | monetize/render/BonusAdSponsor.kt |
| `NativeAds_load` | monetize/render/InlineSponsor.kt |
| `NativeAds_Fail` | monetize/render/InlineSponsor.kt |
| `NativeAds_showBigNative_Google` | monetize/render/InlineSponsor.kt |
| `NativeAds_showBigNative_FB_Load` | monetize/render/InlineSponsor.kt |
| `NativeAds_showMidNative_Google` | monetize/render/InlineSponsor.kt |
| `NativeAds_showMid_FB` | monetize/render/InlineSponsor.kt |
| `NativeAds_showMidNative2_Google` | monetize/render/InlineSponsor.kt |
| `NativeBanner_Load` | monetize/render/InlineSponsorStrip.kt |
| `NativeBanner_fail` | monetize/render/InlineSponsorStrip.kt |
| `NativeBanner_Show_Google` | monetize/render/InlineSponsorStrip.kt |
| `NativeBAnner_FB` | monetize/render/InlineSponsorStrip.kt |
| `AppOpen_Loaded` | monetize/render/MonetizeGateActivity.kt |
| `Interstitial_Splash_Loaded` | monetize/render/MonetizeGateActivity.kt |
| `recent_playstore_open` | monetize/render/RecentsAdObserver.kt |
| `appopen_ad_loaded` | monetize/render/ResumeAdKeeper.kt |
| `appopen_ad_show_fail` | monetize/render/ResumeAdKeeper.kt |
| `appopen_ad_dismissed` | monetize/render/ResumeAdKeeper.kt |
| `appopen_ad_shown` | monetize/render/ResumeAdKeeper.kt |
| `Banner_Load` | monetize/render/StripAdLoader.kt |
| `Banner_fail_Load` | monetize/render/StripAdLoader.kt |
| `google_banner` | monetize/render/StripAdLoader.kt |
| `facebook_banner_load` | monetize/render/StripAdLoader.kt |
| `google_inter_show_attempt` | monetize/render/fullscreen/FullscreenOnAction.kt |
| `Reminder_Delete` | screens/alerts/AlarmListActivity.kt |
| `Reminder_Alert_Show` | screens/alerts/AlarmPopupActivity.kt |
| `Reminder_Alert_Done` | screens/alerts/AlarmPopupActivity.kt |
| `Reminder_Alert_Ad` | screens/alerts/AlarmPopupActivity.kt |
| `Reminder_Alert_Call` | screens/alerts/AlarmPopupActivity.kt |
| `Reminder_Alert_Open_List_Click` | screens/alerts/AlarmPopupActivity.kt |
| `Reminder_Alert_Snooze_$snoozeMinutes` | screens/alerts/AlarmPopupActivity.kt |
| `reminder_alert_tap` | screens/alerts/AlarmPopupActivity.kt (alert opened by tapping the notification, not the full-screen launch) |
| `reminder_alert_dismiss` | screens/alerts/AlarmPopupActivity.kt (alert closed via back/home with no action) |
| `search_limit_reached` | screens/dashboard/DashboardMainFragment.kt |
| `search_invalid_number` | screens/dashboard/DashboardMainFragment.kt |
| `MyNumber_Manage_Click` | screens/dashboard/OwnNumberTile.kt |
| `MyNumber_Show_Click` | screens/dashboard/OwnNumberTile.kt |
| `MyNumber_Copy` | screens/dashboard/OwnNumberTile.kt |
| `language_info_click` | screens/locale/LocalePickActivity.kt |
| `language_done_click` | screens/locale/LocalePickActivity.kt |
| `CarrierCodes_Dial` | screens/simcard/OperatorDialCodesActivity.kt |
| `MyNumberPage_Show_Click` | screens/simcard/OwnNumberActivity.kt |
| `MyNumberPage_Copy` | screens/simcard/OwnNumberActivity.kt |
| `ShareNumber_Copy` | screens/simcard/SendNumberActivity.kt |
| `ShareNumber_Share_${selected.name.lowercase()}` | screens/simcard/SendNumberActivity.kt |
| `ShareNumber_SaveQr` | screens/simcard/SendNumberActivity.kt |
| `onboarding_page${position + 1}` | screens/walkthrough/GuidedTourActivity.kt |
| `onboarding_skip_click` | screens/walkthrough/GuidedTourActivity.kt |
| `onboarding_next_click` | screens/walkthrough/GuidedTourActivity.kt |
| `onboarding_completed` | screens/walkthrough/GuidedTourActivity.kt |
| `Reminder_Intro_Skip` | setupflow/AlarmIntroActivity.kt |
| `Reminder_Intro_Show` | setupflow/AlarmIntroActivity.kt |
| `Reminder_Intro_Allow` | setupflow/AlarmIntroActivity.kt |
| `default_home_already` | setupflow/HomeRoleJourney.kt |
| `default_home_view` | setupflow/HomeRoleStageActivity.kt |
| `default_home_skip` | setupflow/HomeRoleStageActivity.kt |
| `default_home_declined` | setupflow/HomeRoleStageActivity.kt |
| `welcome_continue_click` | setupflow/WelcomeStageActivity.kt |
| `welcome_skip_click` | setupflow/WelcomeStageActivity.kt |

## Uninstall funnel (R21)
All `un_*` events above carry params `route` (simple / advance) and `segment` (paid / organic) — screens/uninstall/UninstallFlow.kt `track()`. Funnel screens also get the automatic `uninstall_*_open` / `_back_press` screen events; the funnel interstitial reports through `ad_interstitial_*` and `ad_impression`.
