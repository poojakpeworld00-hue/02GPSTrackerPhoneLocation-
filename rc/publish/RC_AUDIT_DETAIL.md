# Remote Config audit - 02GPSTrackerPhoneLocation

## 0. Action list (read this first)

**Top-level parameters the code reads (9):** `GET_DATA_LIST_1` (release) / `DEBUG_GET_DATA_LIST_1` (debug), legacy fallbacks `GET_DATA_LIST` / `DEBUG_GET_DATA_LIST`, `permission_engine`, `feature_config`, `launcher_config`, `home_profile_rules`, `home_profile_probes`, and `ads_config`. The launcher asks for `ads_config`, but the host intercepts it, so the app never reads it from Remote Config.

**Publish these:** `GET_DATA_LIST_1`, `DEBUG_GET_DATA_LIST_1` (from a variant file; no plain file exists), `feature_config`, `launcher_config`.
**Do not publish these:** `GET_DATA_LIST`/`DEBUG_GET_DATA_LIST` (old schema fallback), `ads_config` (never read), `permission_engine` as its own parameter (it would override the blob copy and is not audience-split).
**Optional, with no template:** `home_profile_rules`, `home_profile_probes`.

### A. MISMATCH
1. `GET_DATA_LIST_1.marketing.launcher_ads.reminders` is at the wrong path. The code reads `launcher_ads.onboarding.reminders` (`HomeAdsSettings.onboardingBlock`). Move it under `onboarding`, as `GET_DATA_LIST_1_fix.json` / `_all_fixes.json` already do. Until then marketing gets the code defaults (interstitial off, mid native). Those match the template values today, but any edit to the misplaced block is ignored.

### B. UNUSED / dead keys in the templates (delete)
1. `GET_DATA_LIST_1.{marketing,organic}.MarketLink`: only copied to DirectLink for a flat (non-split) blob (`MonetizeGateActivity` `isMarketingOn && !isSplitConfig`). The template is split, so this key is never used.
2. `GET_DATA_LIST_1.{marketing,organic}.AppopenCounter`: ingested but read nowhere.
3. `GET_DATA_LIST_1.marketing.launcher_ads.home_open.*`: no launcher surface is named `home_open`. Valid surfaces: app_click, app_close, left_panel.open_ad, right_panel.open_ad, home_click, drawer_click, home_back, recent_click, home_press.
4. `launcher_config.{organic,marketing}.panels.*` (launcher/remote-config/launcher_config.json): always overwritten by `TrackerHomeBridge.imprintLauncherAds` from `launcher_ads.left_panel/right_panel.enabled`.
5. `launcher_config.{organic,marketing}.gestures.*`: always overwritten by the host from `launcher_ads` (left_swipe, right_swipe, drawer_open, app_launch, home_launch, drawer_launch, home_back, home_press).
6. `launcher/remote-config/ads_config_launcher_entries.json` (whole file): `ads_config` is never read from Remote Config. The host synthesises `screenWiseAds.app_drawer.ad_row_position` from `launcher_ads.app_drawer.bottom_native.position`.
7. Variants only: `premium.products[].price` (DEBUG_* variants) is never read.
8. Shadowed, not dead: `marketing.isLoaderForFB` is ignored because marketing also carries `Inter_Loader_Show`. In organic it is the effective loader switch.

### C. MISSING in template (read by code, so the code default applies)
| Key | Default when absent | Comment |
|---|---|---|
| `GET_DATA_LIST_1.*.Config_Sync_Hrs` | getInt -1, so the backstop fetch runs every 6h (`DEFAULT_STALE_HOURS`) | fine, or set explicitly |
| `GET_DATA_LIST_1.*.Inter_Loader_Ms` | -1 is coerced to 0: no loader beat before a preloaded interstitial | set it if a loader beat is wanted (0..3000) |
| `GET_DATA_LIST_1.*.contacts_upload` (`{enabled}`) | `{}`: enabled=false, so the one-time phonebook upload stays off | add `{"enabled":true}` only if wanted |
| `GET_DATA_LIST_1.*.intro_display.reminders_intro.{enabled,prompt_frequency,prompt_interval}` | enabled=true, once, 0 | add for explicit control |
| `GET_DATA_LIST_1.*.launcher_ads.home_press` | surface off (no Home-press ad) | `_all_fixes` variants add it |
| `GET_DATA_LIST_1.*.launcher_ads.home_click` / `drawer_click` | fall back to the `app_click` block (`clickSurface`) | benign |
| `GET_DATA_LIST_1.*.premium.products[]` | DEFAULT_PLANS `gps_premium_weekly` (SUGGESTED, 3-day trial), `gps_premium_monthly`, `gps_premium_lifetime` | **these product IDs must exist in the new app's Play Console**, or publish `products[]` (the DEBUG_* variants have the shape) |
| `GET_DATA_LIST_1.*.premium.close_button_opacity` | 1.0 (range 0.3..1) | fine |
| `GET_DATA_LIST_1.*.launcher_config.should_show_time_widget` / `should_show_search_widget` | true, also supplied by the top-level `launcher_config` base | harmless if `launcher_config` is published |
| `launcher_config.*.unlock_ads.*` (top-level file) | unlock ad off. **Supplied by the GET_DATA_LIST_1 overlay** (marketing: enabled, 72h, 30min, 15/day, inter) | harmless; the overlay wins |
| `launcher_config.*.dock_host_app` (top-level file) | true. Supplied by the overlay (false in the blob) | harmless |
| `GET_DATA_LIST_1.*.custom_ads[]`, `launcher_config.drawerAppAds[]`, `feature_config.default_config_list[]` item fields | arrays are empty in the template | fields: icon/title/description/bannerImage/buttonText; logo/landing_url/label; title/note/daysAhead |

The 36 `MISSING_OPTIONAL` rows in section 3 are optional overrides whose default is fine: per-placement overrides in `placements.<name>.*`, the `googleFullNative` / `ad_flow` / `ad_flow_show_all` globals, `launcher_ads.<surface>.ads|directlink|<type>` alternate forms, and `permission_engine.*.show_once`.

### D. Present in one audience only (WIRED_PARTIAL)
| Key | Present in | Effect in the other audience |
|---|---|---|
| `Inter_Loader_Show` | marketing | organic falls back to `isLoaderForFB` (false) |
| `link_first_then`, `link_first_show_all`, `inter_fallback` | marketing | organic: no link-first chain, no inter fallback |
| `launcher_ads.app_drawer.bottom_native` (+`.position`) | organic | **marketing: drawer native slot hidden, launcher `ad_row_position`=0**, although `ScreenAds.LauncherAppDrawer.show=true` there. Likely an oversight; copy it to marketing. |
| `launcher_ads.onboarding.reminders` | organic | marketing uses defaults (see MISMATCH A1) |

### E. Type / case mismatches
None found. Every template key exists as an exact-case string literal in code, except the dead `home_open` and the `ads_config` SDK fields (adType, loadType, adData, adId, isActive, adSubType, adDataList, notes), which are never read. No string-encoded booleans or numbers appear in the templates. `RewardedAds`, `launcher_enabled`, `link_first_show_all` and `placements.*.ads_on` are JSON booleans that the code stores and compares as strings ("true"/"false"); that works. One value is not canonical: `launcher_ads.system_ads.{charge,discharge}.native = "MediumNative"` is not a recognised token. It lands in PowerStateActivity's else branch, which is the mid native, so it works by accident. Prefer `"mid"`.

### F. Screen names (PageMatcher)
Every screen name in every rc/*GET_DATA_LIST_1*.json resolves; none is unresolved.
- ScreenAds keys: default, LocalePickActivity, MainHubActivity, PolicyConsentActivity, GuidedTourActivity, NumberInsightActivity, PinpointingActivity, PeopleSelectActivity, ProUpgradeActivity, PastLookupsActivity, WelcomeStageActivity and HomeRoleStageActivity are current classes. LauncherAppsPanel, LauncherAppsPanelMid and LauncherAppDrawer are virtual keys mapped in `TrackerHomeAds.screenAdsKey`.
- permission_engine.notification.activities: BootActivity, WelcomeStageActivity, HomeRoleStageActivity, MainHubActivity and HomeDeskPane are all current classes.
- Effectiveness caveats:
  - `activities` only matters for screens that call `GrantOrchestrator.check()`: WelcomeStageActivity, HomeRoleStageActivity, LocalePickActivity and GuidedTourActivity, plus the splash prime on BootActivity. `MainHubActivity` and `HomeDeskPane` never call check(); MainHub's permission sheet uses `request()`, which ignores `activities`. Listing them has no effect.
  - The marketing list has no BootActivity, so there is no splash prompt for marketing. The organic list has no HomeRoleStageActivity.
  - LocalePickActivity and GuidedTourActivity call check() but are in neither list.
- ScreenAds `nativeType` is only consumed by PinpointingActivity. For other screens it is resolved but has no effect. Per-screen `native` ids are also ignored; native loading uses `ScreenAds.default.native` (`nativeAdUnitId`).
- Code-side problems (not RC):
  - GrantOrigin `DEFAULT_CONFIG` lists `HomeStageActivity`, which no longer exists; PageMatcher maps `HomeDeckActivity`/`MainActivity` to it as well.
  - 23 PageMatcher LEGACY_NAMES targets name classes that no longer exist: BeamToolActivity, BlockVaultActivity, CallReportActivity, ChargeToolActivity, CountryDeckActivity, FsiPortalActivity, GadgetsActivity, HomeStageActivity, IncomingRingActivity, LapTimerActivity, LumenToolActivity, NeedleToolActivity, NumPadActivity, PaceToolActivity, PlumbToolActivity, PrefsHubActivity, SandTimerActivity, SearchBriefActivity, SearchLogActivity, SimHubActivity, SoundToolActivity, StageSettingsActivity, VeiledAppsActivity. These are harmless unless RC uses one of the old names.

### G. Ad unit IDs and placeholders (replace before publishing)
- `rc/GET_DATA_LIST_1.json` (and the `_fix`, `_all_fixes`, `_setdefault_native_off` variants) has 52 occurrences of `/23343433547/com.find.phone.gps.location.tracker_<format>`: googleBanner x18, googleNative x24, and x2 each of googleInter, googleS_Inter, googleBackInter, googleAppopen, googleRewarded. They sit in the flat `google*` keys and in every `ScreenAds.*.banner/native`. `GET_DATA_LIST_1_organic.json` has 26. **Replace them all with the new app's real ad unit IDs.**
- `faceB_*` keys hold the placeholder `IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID` (8 per file). `FbAppId`/`FbClientToken` are empty, so the Facebook SDK is never initialised.
- DEBUG_* variants use Google test units (`ca-app-pub-3940256099942544/...`, 52 occurrences); that is fine for debug.
- Also review: `PrivacyPolicy`/`TermLink` (sites.google.com/view/gps-phone-location-tracker/...), `contacts_base_url`, `CountryList_Counter_NShow`="Indore", and the 30 `marketing.launcher_config.drawerAppAds` third-party tiles.

### H. Prefs read from AdPrefsStore that never come from Remote Config
`OnMaketing` (install referrer), `GET_DATA_RAW`, `__cfg_audience_split`, `country_nshow_match`, `user_country`/`user_Region`/`user_City` (IP lookup), `NativeTheme_active`, `CUSTOM_ADS` (copied from `custom_ads`), `isGrid`, `IsSplash`/`isIntro`/`IsFirsttime`/`IsLanguageOnTime`/`isPermission`, the `VD_*` keys, `__launcher_*` counters and pointers, `__unlock_ad_*`, `is_intro`, `isReferrerDone` (activity prefs).
**Code bug (cosmetic):** the debug log in `MonetizeConfigReader.ingest` prints `BannerAds=${getBoolean("StripSponsor")}`. `StripSponsor` is never written, so the log always shows false. The real key is `BannerAds`.
**Ingested but never consumed (code cleanup, nothing to publish):** `Iscountry_Marketing_Counter`, `CountryList_Marketing_Counter_NShow`, `Perm_Sheet_Show`, `Perm_Sheet_Mode`, `Perm_Sheet_Interval_Days`. The `Market*Counter` keys only apply to a flat blob.
**Parsers never called:** `launcher_ads.recent_ad` (recentAdSettings), `launcher_ads.home_hint` (homeHint), `launcher_ads.app_drawer.promo` (drawerPromoItems). None of these is in the template; leave them out.

### I. Variant files (rc/)
- `_fix`: moves reminders under onboarding, drops home_open, changes `recent_playstore_window_sec` and `organic.launcher_enabled`.
- `_all_fixes`: the `_fix` changes plus `launcher_ads.home_press` and a `set_default` slot tweak.
- `_setdefault_native_off`: home_press plus the slot tweak; it keeps home_open.
- `organic`: organic block only.
- All 7 variants except `_organic` (that is, `_fix`, `_all_fixes`, `_setdefault_native_off` and the three DEBUG_* files) drop `organic.launcher_ads.onboarding.intro`, so organic intro falls back to inter ON, mid2 native.
- DEBUG_* variants also add `premium.products[]` and swap in Google test ad units.
- **Recommendation:** publish `GET_DATA_LIST_1_all_fixes.json`-equivalent content as `GET_DATA_LIST_1`, after you:
  - restore `organic.launcher_ads.onboarding.intro`,
  - add `marketing.launcher_ads.app_drawer.bottom_native`,
  - add `premium.products` (or create the default product IDs),
  - delete MarketLink and AppopenCounter,
  - replace every ad ID.


Generated 2026-10-14 13:28. READ-ONLY audit of `app` + `launcher` vs `rc/*.json` and `launcher/remote-config/*.json`. File:line citations are relative to `app/src/main/java/com/find/phone/gps/location/tracker/` (app) or `launcher/src/main/java/io/homedeck/grid/` (prefixed `launcher:`), taken from the tree at generation time.

> Note: Kotlin sources were rewritten (comments stripped) around 13:19 and resources were still being modified while this audit ran, so line numbers were re-resolved by regex at the end; re-run `gen_audit.py` + `gen_md.py` from the scratchpad if the tree changes again.

No `setDefaultsAsync`/XML defaults exist anywhere: every fallback below is a code default. Fetch settings: min interval 0s debug / 1s release, timeout 10s (`monetize/engine/RemoteValuesRules.kt:10`). Realtime listener only re-ingests on updates to the blob key, `permission_engine` or `feature_config` (`monetize/engine/RemoteValuesObserver.kt:41`); a change to `launcher_config` alone is picked up at the next fetch (splash or 6h backstop).


## 1. Top-level Remote Config parameters

| Parameter | Type | Code default when absent | Readers | Template | Status | Notes |
|---|---|---|---|---|---|---|
| `GET_DATA_LIST_1` | JSON (audience-split marketing/organic) | "" -> falls back to legacy GET_DATA_LIST; if that is empty too, nothing is ingested (cached prefs or code defaults = every ad off) | monetize/engine/MonetizeConfigReader.kt:17<br>monetize/engine/MonetizeConfigReader.kt:23<br>monetize/render/MonetizeGateActivity.kt:227<br>monetize/engine/RemoteValuesObserver.kt:110<br>grants/GrantOrigin.kt:59 | rc/GET_DATA_LIST_1.json | WIRED | Release builds. Main ads/config blob, see section 3. |
| `DEBUG_GET_DATA_LIST_1` | JSON | same as above (falls back to DEBUG_GET_DATA_LIST) | monetize/engine/MonetizeConfigReader.kt:17 | NONE with that exact name - only rc/DEBUG_GET_DATA_LIST_1_fix.json / _all_fixes.json / _setdefault_native_off.json | WIRED (variant templates only) | Debug builds (BuildConfig.DEBUG). Pick one variant and publish it under this name. |
| `GET_DATA_LIST / DEBUG_GET_DATA_LIST` | JSON | "" | monetize/engine/MonetizeConfigReader.kt:19 | none | LEGACY_FALLBACK | Only read when the _1 parameter is blank. Do NOT publish for the new app (would serve the old schema). |
| `permission_engine` | JSON | blob copy (GET_DATA_LIST_1.<audience>.permission_engine), else compiled DEFAULT_CONFIG | grants/GrantOrigin.kt:57<br>grants/RemoteGrantDecoder.kt:16<br>monetize/engine/RemoteValuesObserver.kt:18 | none (blob copy exists) | OPTIONAL / NO_TEMPLATE | If published it WINS over the blob copy and it is NOT audience-split (RemoteGrantDecoder has no marketing/organic handling). Recommendation: leave unpublished and keep using the blob copy. |
| `feature_config` | JSON (audience-split) | AlarmSettings.DEFAULT_CONFIG (compiled) | alarms/AlarmSettings.kt:66<br>alarms/AlarmSettings.kt:56 | rc/feature_config.json | WIRED | All 17 fields + default_config_list[] fields wired. Note: template organic.is_intro_screen=true while in-app DEFAULT_CONFIG organic.is_intro_screen=false (code comment says keep identical). |
| `launcher_config` | JSON (audience-split) | HomeOptions.DEFAULT (os_style off, google bar, widgets on, prompt on, guide on, unlock ads off, drawer tiles off) | homeshell/TrackerHomeBridge.kt:42<br>homeshell/TrackerHomeBridge.kt:40<br>launcher:bridge/HomeServiceLocator.kt:48<br>launcher:setup/HomeOptions.kt:69 | launcher/remote-config/launcher_config.json | WIRED (base layer) | Base layer; GET_DATA_LIST_1.<audience>.launcher_config overlays it key-by-key (overlay wins). panels/gestures are always overwritten by the host from launcher_ads. |
| `home_profile_rules` | JSON | HomeLayoutRules.DEFAULT_RULES_JSON (built-in) | launcher:oemprofile/HomeLayoutResolver.kt:10<br>launcher:oemprofile/HomeLayoutApplier.kt:27 | none | OPTIONAL / NO_TEMPLATE | Optional override only; module ships its own table. |
| `home_profile_probes` | JSON | PackageProfileParser.DEFAULT_PROBES_JSON (built-in) | launcher:oemprofile/HomeLayoutResolver.kt:11 | none | OPTIONAL / NO_TEMPLATE | Optional override only. |
| `ads_config` | JSON | n/a | homeshell/TrackerHomeBridge.kt:31<br>launcher:sponsor/HomeAdsSettings.kt:58 | launcher/remote-config/ads_config_launcher_entries.json | NOT READ FROM RC (UNUSED) | The launcher asks for it, but TrackerHomeBridge.configString intercepts the key and returns a synthesised {"screenWiseAds":{"app_drawer":{"ad_row_position":<launcher_ads.app_drawer.bottom_native.position>}}}. Do not publish; the whole entries file is dead for this app. |

## 2. Status legend and counts (nested keys, section 3-5)

| Status | Count | Meaning |
|---|---|---|
| WIRED | 197 | read by code and present in the template (both audiences where split) |
| WIRED_PARTIAL | 7 | read by code, present in only one audience branch; the other falls back to the code default |
| MISSING_IN_TEMPLATE | 19 | read by code, absent from the template -> code default applies (actionable: decide value) |
| MISSING_OPTIONAL | 36 | read by code, absent, but it is an optional override/alternate form whose default is fine |
| UNUSED_IN_TEMPLATE | 6 | present in the template but never read (dead) -> delete |
| UNUSED_NOT_IN_TEMPLATE | 3 | shape documented but would be ignored if published |
| MISMATCH | 1 | present but at the wrong path / wrong shape for the code |
| DEAD_INGEST | 5 | ingested into AdPrefsStore by MonetizeConfigReader but no consumer in code; not in template |
| LEGACY_FLAT_ONLY | 5 | only used for a non-audience-split (flat) blob; irrelevant with the split template |
| SUPERSEDED_BY_NativeTheme | 5 | flat key still ingested, but always overwritten from NativeTheme |
| DEAD_CODE | 3 | parser exists but is never called |
| OPTIONAL | 5 | legacy/alternate form accepted by code; not needed |
| **total** | 292 | |

## 3. `GET_DATA_LIST_1` (per audience block: `marketing.*` / `organic.*`)

| Path | Type | Code default | Readers | In template | Status | Notes |
|---|---|---|---|---|---|---|
| `IsAdsON` | bool | false (all ads off) | monetize/engine/MonetizeConfigReader.kt:40<br>monetize/engine/HomeSlotAds.kt:179<br>monetize/engine/PageSponsorSettings.kt:134<br>monetize/render/fullscreen/FullscreenOnAction.kt:83<br>homeshell/TrackerHomeBridge.kt:85<br>homeshell/UnlockAdObserver.kt:96 | marketing,organic | WIRED |  |
| `IsFail_FB` | bool | false | monetize/engine/MonetizeConfigReader.kt:40<br>monetize/render/MonetizeGateActivity.kt:545<br>monetize/render/InlineSponsor.kt:269<br>monetize/render/StripAdLoader.kt:356 | marketing,organic | WIRED |  |
| `isLoaderForFB` | bool | false | monetize/engine/MonetizeConfigReader.kt:40<br>monetize/render/fullscreen/BlockingLoaderDialog.kt:33 | marketing,organic | WIRED | Legacy fallback: only consulted when Inter_Loader_Show is absent (organic block). Shadowed in marketing (which carries Inter_Loader_Show). |
| `IsCustomADS` | bool | false | monetize/engine/MonetizeConfigReader.kt:40<br>monetize/engine/HomeSlotAds.kt:105<br>monetize/render/MonetizeGateActivity.kt:592<br>monetize/render/OwnAdsKeeper.kt:53 | marketing,organic | WIRED |  |
| `IsBack` | bool | false | monetize/engine/MonetizeConfigReader.kt:40<br>monetize/render/fullscreen/FullscreenOnExit.kt:44 | marketing,organic | WIRED |  |
| `NativeBanner` | bool | false | monetize/engine/MonetizeConfigReader.kt:41<br>monetize/render/InlineSponsorStrip.kt:51 | marketing,organic | WIRED | Alias accepted: NativeBannerPresenter. |
| `BannerAds` | bool | false | monetize/engine/MonetizeConfigReader.kt:41<br>monetize/render/StripAdLoader.kt:55 | marketing,organic | WIRED | Alias accepted: BannerAdPresenter. |
| `In_App_Update_Show` | bool | false | monetize/engine/MonetizeConfigReader.kt:41<br>screens/dashboard/DashboardShellDriver.kt:72 | marketing,organic | WIRED |  |
| `In_App_Update_Force_Show` | bool | false | monetize/engine/MonetizeConfigReader.kt:41<br>screens/dashboard/DashboardShellDriver.kt:74 | marketing,organic | WIRED |  |
| `Iscountry_Counter` | bool | false (country check off) | monetize/engine/MonetizeConfigReader.kt:42<br>monetize/render/MonetizeGateActivity.kt:341 | marketing,organic | WIRED |  |
| `is_preload_ads` | bool | false | monetize/engine/MonetizeConfigReader.kt:42<br>monetize/render/fullscreen/FullscreenOnAction.kt:47 | marketing,organic | WIRED |  |
| `is_splash_inter_show` | bool | false | monetize/engine/MonetizeConfigReader.kt:43<br>monetize/render/MonetizeGateActivity.kt:524 | marketing,organic | WIRED |  |
| `is_splash_ads` | bool | false | monetize/engine/MonetizeConfigReader.kt:43<br>monetize/render/MonetizeGateActivity.kt:472 | marketing,organic | WIRED | Only honoured when this launch's RC fetch + ingest succeeded (cached value alone never shows a splash ad). |
| `splash_ad_wait_ms` | int(ms) | missing/<=0/invalid -> 5000 | monetize/engine/MonetizeConfigReader.kt:108<br>monetize/render/MonetizeGateActivity.kt:492 | marketing,organic | WIRED | Splash ad load deadline (App Open -> interstitial -> FB chain). On timeout: `ad_splash_timeout`, continue, late load never shown. |
| `uninstall_flow` | object | missing -> feature off | monetize/engine/MonetizeConfigReader.kt (stored raw)<br>screens/uninstall/UninstallConfig.kt | marketing,organic | WIRED | R21 uninstall funnel. Always overwritten on ingest (missing block clears it): shortcut removed + drawer row hidden. |
| `uninstall_flow.enabled` | bool | false (feature off) | screens/uninstall/UninstallConfig.kt | marketing,organic | WIRED | Launcher shortcut synced on every launch (BootActivity) and RC sync/push (RemoteValuesObserver). |
| `uninstall_flow.type` | string simple\|advance | simple | screens/uninstall/UninstallConfig.kt | marketing,organic | WIRED | Organic is forced to simple; unknown -> simple. advance = Language -> Sorry -> Reason -> Progress -> Thanks -> close app. |
| `uninstall_flow.thanks_delay` | int (s) | 3, clamped 1-10 | screens/uninstall/UninstallThanksActivity.kt | marketing,organic | WIRED | Auto-continue delay of the Thanks screen. |
| `uninstall_flow.splash_ad.show` | bool | true when the block exists; block missing -> is_splash_ads | monetize/render/MonetizeGateActivity.kt (uninstallSplashAd) | marketing,organic | WIRED | Uninstall-shortcut launch only. Still gated by IsAdsON, consent, rcFreshThisLaunch, splash_ad_wait_ms. |
| `uninstall_flow.splash_ad.type` | string inter\|appopen | missing/unknown -> is_splash_inter_show | monetize/render/MonetizeGateActivity.kt (prefetchAds) | marketing,organic | WIRED | Pins the splash format (also bypasses the `placements.splash` own flow). |
| `uninstall_flow.<page>_ad.show / .type` | bool / big\|mid\|small\|banner | show true; type unknown -> mid; block missing -> no ad | screens/uninstall/UninstallAds.kt | marketing,organic | WIRED | Pages: language, sorry, reason, progress, thanks. Rendered via HomeAdsSettings.presentSlot with the app-wide units. |
| `uninstall_flow.inter_ad.show / .on` | bool / array | show true; on missing -> all pages; block missing -> no inter | screens/uninstall/UninstallAds.kt | marketing,organic | WIRED | googleInter, own preload slot, no InterCounter; not ready -> move on immediately. |
| `InterAds` | bool | false | monetize/engine/MonetizeConfigReader.kt:43<br>monetize/render/fullscreen/FullscreenOnAction.kt:215<br>monetize/render/fullscreen/FullscreenOnExit.kt:39 | marketing,organic | WIRED |  |
| `AppopenAds` | bool | false | monetize/engine/MonetizeConfigReader.kt:43<br>monetize/render/ResumeAdKeeper.kt:58 | marketing,organic | WIRED |  |
| `NativeAd` | bool | false | monetize/engine/MonetizeConfigReader.kt:44<br>monetize/render/InlineSponsor.kt:101 | marketing,organic | WIRED |  |
| `is_rateus` | bool | true (getBoolean default true) | monetize/engine/MonetizeConfigReader.kt:44<br>screens/dashboard/DashboardSideMenu.kt:101 | marketing,organic | WIRED |  |
| `is_share` | bool | true (getBoolean default true) | monetize/engine/MonetizeConfigReader.kt:44<br>screens/dashboard/DashboardSideMenu.kt:102 | marketing,organic | WIRED |  |
| `screen_wise_ad` | bool | false (global googleBanner/googleNative ids) | monetize/engine/MonetizeConfigReader.kt:45<br>monetize/engine/PageSponsorSettings.kt:34 | marketing,organic | WIRED |  |
| `screen_wise_default` | bool | false | monetize/engine/MonetizeConfigReader.kt:45<br>monetize/engine/PageSponsorSettings.kt:35 | marketing,organic | WIRED |  |
| `recent_playstore` | bool | false | monetize/engine/MonetizeConfigReader.kt:47<br>monetize/render/RecentsAdObserver.kt:58 | marketing,organic | WIRED |  |
| `Overlay_Permission_Show` | bool | true (getBoolean(KEY,true)) | monetize/engine/MonetizeConfigReader.kt:49<br>screens/consent/OverlayToolbox.kt:18 | marketing,organic | WIRED |  |
| `Inter_Loader_Show` | bool | absent -> falls back to isLoaderForFB | monetize/engine/MonetizeConfigReader.kt:51<br>monetize/render/fullscreen/BlockingLoaderDialog.kt:30 | marketing | WIRED_PARTIAL | Present only in: marketing; absent in: organic (falls back to default there). |
| `Iscountry_Marketing_Counter` | bool | n/a | monetize/engine/MonetizeConfigReader.kt:42 | no | DEAD_INGEST | Ingested by MonetizeConfigReader but no consumer anywhere in code. Not in template; nothing to publish. |
| `Perm_Sheet_Show` | bool | n/a | monetize/engine/MonetizeConfigReader.kt:44 | no | DEAD_INGEST | Ingested by MonetizeConfigReader but no consumer anywhere in code. Not in template; nothing to publish. |
| `CountryList_Marketing_Counter_NShow` | string | n/a | monetize/engine/MonetizeConfigReader.kt:61 | no | DEAD_INGEST | Ingested by MonetizeConfigReader but no consumer anywhere in code. Not in template; nothing to publish. |
| `Perm_Sheet_Mode` | string | n/a | monetize/engine/MonetizeConfigReader.kt:69 | no | DEAD_INGEST | Ingested by MonetizeConfigReader but no consumer anywhere in code. Not in template; nothing to publish. |
| `Perm_Sheet_Interval_Days` | int | n/a | monetize/engine/MonetizeConfigReader.kt:96 | no | DEAD_INGEST | Ingested by MonetizeConfigReader but no consumer anywhere in code. Not in template; nothing to publish. |
| `MarketInterCounter` | int | -1 | monetize/engine/MonetizeConfigReader.kt:93<br>monetize/render/MonetizeGateActivity.kt:414 | no | LEGACY_FLAT_ONLY | Only copied when OnMaketing && config is NOT audience-split. The template is split, so never used. Not in template (correct). |
| `MarketBackCounter` | int | -1 | monetize/engine/MonetizeConfigReader.kt:93<br>monetize/render/MonetizeGateActivity.kt:415 | no | LEGACY_FLAT_ONLY | Only copied when OnMaketing && config is NOT audience-split. The template is split, so never used. Not in template (correct). |
| `MarketNativeCounter` | int | -1 | monetize/engine/MonetizeConfigReader.kt:94<br>monetize/render/MonetizeGateActivity.kt:416 | no | LEGACY_FLAT_ONLY | Only copied when OnMaketing && config is NOT audience-split. The template is split, so never used. Not in template (correct). |
| `MarketBannerCounter` | int | -1 | monetize/engine/MonetizeConfigReader.kt:95<br>monetize/render/MonetizeGateActivity.kt:418 | no | LEGACY_FLAT_ONLY | Only copied when OnMaketing && config is NOT audience-split. The template is split, so never used. Not in template (correct). |
| `MarketAppopenCounter` | int | -1 | monetize/engine/MonetizeConfigReader.kt:95<br>monetize/render/MonetizeGateActivity.kt:419 | no | LEGACY_FLAT_ONLY | Only copied when OnMaketing && config is NOT audience-split. The template is split, so never used. Not in template (correct). |
| `MarketLink` | string | "" | monetize/engine/MonetizeConfigReader.kt:62<br>monetize/render/MonetizeGateActivity.kt:421 | marketing,organic | UNUSED_IN_TEMPLATE | Only copied to DirectLink when the config is NOT audience-split. Template is split -> value is never used. Safe to delete. |
| `AppopenCounter` | int | -1 | monetize/engine/MonetizeConfigReader.kt:95 | marketing,organic | UNUSED_IN_TEMPLATE | Ingested (and written from MarketAppopenCounter) but never read by any ad code. Dead key. |
| `IsAdType` | string | "" -> AdFormatKind.UNKNOWN (no network) | monetize/engine/MonetizeConfigReader.kt:60<br>monetize/model/AdFormatKind.kt:14<br>monetize/render/MonetizeGateActivity.kt:460 | marketing,organic | WIRED | Accepted (case-insensitive): google \| facebook \| fb \| custom. |
| `In_App_Update_Link` | string | "" | monetize/engine/MonetizeConfigReader.kt:60<br>screens/boot/BootActivity.kt:126<br>monetize/render/SponsorRenderKit.kt:93 | marketing,organic | WIRED |  |
| `CountryList_Counter_NShow` | string (CSV of country/region/city, or "all") | "" | monetize/engine/MonetizeConfigReader.kt:60<br>monetize/render/MonetizeGateActivity.kt:342 | marketing,organic | WIRED | Template value "Indore" (a city) - review for the new app. |
| `PrivacyPolicy` | string(url) | "" (link opens nothing) | monetize/engine/MonetizeConfigReader.kt:61<br>helpers/ScreenHelpers.kt:114 | marketing,organic | WIRED | Template points at sites.google.com/view/gps-phone-location-tracker - confirm it is the new app's policy. |
| `TermLink` | string(url) | "" | monetize/engine/MonetizeConfigReader.kt:61<br>helpers/ScreenHelpers.kt:137 | marketing,organic | WIRED |  |
| `DirectLink` | string(url) or JSON array of urls (placements) | "" | monetize/engine/MonetizeConfigReader.kt:62<br>monetize/engine/HomeAdsSettings.kt:214<br>monetize/render/MonetizeGateActivity.kt:595<br>monetize/engine/HomeSlotAds.kt:85 | marketing,organic | WIRED |  |
| `DirectLinkType` | string | webview | monetize/engine/MonetizeConfigReader.kt:64<br>monetize/render/DeepLinkRouter.kt:16 | marketing,organic | WIRED | webview \| custom_tab \| browser (+aliases). |
| `googleS_Inter` | string(ad unit) | "" (format not loaded) | monetize/engine/MonetizeConfigReader.kt:65<br>monetize/render/MonetizeGateActivity.kt:638 | marketing,organic | WIRED | AD UNIT ID: template value "/23343433547/com.find.phone.gps.location.tracker_googleS_Inter" must be replaced with the new app's real unit. |
| `googleBackInter` | string(ad unit) | "" (format not loaded) | monetize/engine/MonetizeConfigReader.kt:65<br>monetize/render/fullscreen/FullscreenOnExit.kt:49 | marketing,organic | WIRED | AD UNIT ID: template value "/23343433547/com.find.phone.gps.location.tracker_googleBackInter" must be replaced with the new app's real unit. |
| `googleInter` | string(ad unit) | "" (format not loaded) | monetize/engine/MonetizeConfigReader.kt:65<br>monetize/render/fullscreen/FullscreenOnAction.kt:223 | marketing,organic | WIRED | AD UNIT ID: template value "/23343433547/com.find.phone.gps.location.tracker_googleInter" must be replaced with the new app's real unit. |
| `googleAppopen` | string(ad unit) | "" (format not loaded) | monetize/engine/MonetizeConfigReader.kt:65<br>monetize/render/ResumeAdKeeper.kt:67 | marketing,organic | WIRED | AD UNIT ID: template value "/23343433547/com.find.phone.gps.location.tracker_googleAppopen" must be replaced with the new app's real unit. |
| `googleNative` | string(ad unit) | "" (format not loaded) | monetize/engine/MonetizeConfigReader.kt:66<br>monetize/engine/PageSponsorSettings.kt:33 | marketing,organic | WIRED | AD UNIT ID: template value "/23343433547/com.find.phone.gps.location.tracker_googleNative" must be replaced with the new app's real unit. |
| `googleBanner` | string(ad unit) | "" (format not loaded) | monetize/engine/MonetizeConfigReader.kt:66<br>monetize/render/StripAdLoader.kt:141 | marketing,organic | WIRED | AD UNIT ID: template value "/23343433547/com.find.phone.gps.location.tracker_googleBanner" must be replaced with the new app's real unit. |
| `googleRewarded` | string(ad unit) | "" (format not loaded) | monetize/engine/MonetizeConfigReader.kt:66<br>monetize/render/BonusAdSponsor.kt:62 | marketing,organic | WIRED | AD UNIT ID: template value "/23343433547/com.find.phone.gps.location.tracker_googleRewarded" must be replaced with the new app's real unit. |
| `faceB_InterAds` | string(FB placement) | "" | monetize/engine/MonetizeConfigReader.kt:66<br>monetize/render/fullscreen/FullscreenOnAction.kt:85 | marketing,organic | WIRED | Template value is the placeholder "IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID" - replace if Facebook is ever used (IsFail_FB=false today). |
| `faceB_NativeAds` | string(FB placement) | "" | monetize/engine/MonetizeConfigReader.kt:67<br>monetize/render/InlineSponsor.kt:388 | marketing,organic | WIRED | Template value is the placeholder "IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID" - replace if Facebook is ever used (IsFail_FB=false today). |
| `faceB_NativeBannerAds` | string(FB placement) | "" | monetize/engine/MonetizeConfigReader.kt:67<br>monetize/render/InlineSponsorStrip.kt:269 | marketing,organic | WIRED | Template value is the placeholder "IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID" - replace if Facebook is ever used (IsFail_FB=false today). |
| `faceB_BannerAds` | string(FB placement) | "" | monetize/engine/MonetizeConfigReader.kt:67<br>monetize/render/StripAdLoader.kt:278 | marketing,organic | WIRED | Template value is the placeholder "IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID" - replace if Facebook is ever used (IsFail_FB=false today). |
| `NativeBgColor` | string(#color) | unset -> layout colours | monetize/engine/MonetizeConfigReader.kt:68<br>helpers/InlineAdPalette.kt:46 | no | SUPERSEDED_BY_NativeTheme | Flat key still ingested, but overwritten from NativeTheme.<NativeLight\|NativeDark> by imprintNativeAdTheme on every screen. Template uses NativeTheme (correct); flat key not needed. |
| `NativebtnColor` | string(#color) | unset -> layout colours | monetize/engine/MonetizeConfigReader.kt:68<br>helpers/InlineAdPalette.kt:43 | no | SUPERSEDED_BY_NativeTheme | Flat key still ingested, but overwritten from NativeTheme.<NativeLight\|NativeDark> by imprintNativeAdTheme on every screen. Template uses NativeTheme (correct); flat key not needed. |
| `NativebtnColorEnd` | string(#color) | unset -> layout colours | monetize/engine/MonetizeConfigReader.kt:68<br>helpers/InlineAdPalette.kt:44 | no | SUPERSEDED_BY_NativeTheme | Flat key still ingested, but overwritten from NativeTheme.<NativeLight\|NativeDark> by imprintNativeAdTheme on every screen. Template uses NativeTheme (correct); flat key not needed. |
| `NativetxtColor` | string(#color) | unset -> layout colours | monetize/engine/MonetizeConfigReader.kt:69<br>helpers/InlineAdPalette.kt:47 | no | SUPERSEDED_BY_NativeTheme | Flat key still ingested, but overwritten from NativeTheme.<NativeLight\|NativeDark> by imprintNativeAdTheme on every screen. Template uses NativeTheme (correct); flat key not needed. |
| `NativebtntxtColor` | string(#color) | unset -> layout colours | monetize/engine/MonetizeConfigReader.kt:69<br>helpers/InlineAdPalette.kt:45 | no | SUPERSEDED_BY_NativeTheme | Flat key still ingested, but overwritten from NativeTheme.<NativeLight\|NativeDark> by imprintNativeAdTheme on every screen. Template uses NativeTheme (correct); flat key not needed. |
| `NativeTheme` | object | absent -> colours untouched | monetize/engine/MonetizeConfigReader.kt:151 | marketing,organic | WIRED |  |
| `NativeTheme.NativeLight.btnColor` | string(#color) | "" (optString) | helpers/InlineAdPalette.kt:43 | marketing,organic | WIRED | -> pref NativebtnColor |
| `NativeTheme.NativeLight.btnColorEnd` | string(#color) | "" (optString) | helpers/InlineAdPalette.kt:44 | marketing,organic | WIRED | -> pref NativebtnColorEnd |
| `NativeTheme.NativeLight.btnText` | string(#color) | "" (optString) | helpers/InlineAdPalette.kt:45 | marketing,organic | WIRED | -> pref NativebtntxtColor |
| `NativeTheme.NativeLight.bgColor` | string(#color) | "" (optString) | helpers/InlineAdPalette.kt:46 | marketing,organic | WIRED | -> pref NativeBgColor |
| `NativeTheme.NativeLight.textColor` | string(#color) | "" (optString) | helpers/InlineAdPalette.kt:47 | marketing,organic | WIRED | -> pref NativetxtColor |
| `NativeTheme.NativeDark.btnColor` | string(#color) | "" (optString) | helpers/InlineAdPalette.kt:43 | marketing,organic | WIRED | -> pref NativebtnColor |
| `NativeTheme.NativeDark.btnColorEnd` | string(#color) | "" (optString) | helpers/InlineAdPalette.kt:44 | marketing,organic | WIRED | -> pref NativebtnColorEnd |
| `NativeTheme.NativeDark.btnText` | string(#color) | "" (optString) | helpers/InlineAdPalette.kt:45 | marketing,organic | WIRED | -> pref NativebtntxtColor |
| `NativeTheme.NativeDark.bgColor` | string(#color) | "" (optString) | helpers/InlineAdPalette.kt:46 | marketing,organic | WIRED | -> pref NativeBgColor |
| `NativeTheme.NativeDark.textColor` | string(#color) | "" (optString) | helpers/InlineAdPalette.kt:47 | marketing,organic | WIRED | -> pref NativetxtColor |
| `NativeTheme.(marketing\|default).*` | object (legacy nested form) | n/a | monetize/engine/MonetizeConfigReader.kt:156 | no | OPTIONAL | Legacy shape still accepted; not needed. |
| `contacts_base_url` | string(url) | https://contact-saver.dailymorningupdate.com/ (compiled DEFAULT_BASE_URL) | monetize/engine/MonetizeConfigReader.kt:71<br>repo/finder/FinderEndpoint.kt:65<br>repo/peoplesync/PeopleUploadService.kt:101 | marketing,organic | WIRED |  |
| `contacts_upload` | object | {} -> upload disabled | monetize/engine/MonetizeConfigReader.kt:73<br>repo/peoplesync/PeopleUploader.kt:36 | no | MISSING_IN_TEMPLATE |  |
| `contacts_upload.enabled` | bool | false (one-time phonebook upload off) | repo/peoplesync/PeopleUploader.kt:38 | no | MISSING_IN_TEMPLATE |  |
| `onboarding_home` | string | "" -> onboarding ends on the launcher home (anything but "app") | monetize/engine/MonetizeConfigReader.kt:75<br>setupflow/HomeRoleJourney.kt:86 | marketing,organic | WIRED |  |
| `launcher_enabled` | bool/string | blank -> launcher mode ON (only "false" disables) | monetize/engine/MonetizeConfigReader.kt:104<br>homeshell/HomeModeToggle.kt:15 | marketing,organic | WIRED | Stored as string; JSON bool is fine. |
| `FbAppId` | string | "" (FB SDK not initialised) | monetize/engine/MonetizeConfigReader.kt:117 | marketing,organic | WIRED |  |
| `FbClientToken` | string | "" | monetize/engine/MonetizeConfigReader.kt:118 | marketing,organic | WIRED |  |
| `custom_ads` | array<{icon,title,description,bannerImage,buttonText}> | absent -> previous CUSTOM_ADS cache kept | monetize/engine/MonetizeConfigReader.kt:108<br>monetize/render/OwnAdsKeeper.kt:61<br>monetize/model/InHouseOffer.kt:4 | marketing,organic | WIRED |  |
| `custom_ads[].icon` | string | required (Gson) | monetize/model/InHouseOffer.kt:4 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `custom_ads[].title` | string | required (Gson) | monetize/model/InHouseOffer.kt:5 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `custom_ads[].description` | string | required (Gson) | monetize/model/InHouseOffer.kt:6 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `custom_ads[].bannerImage` | string | required (Gson) | monetize/model/InHouseOffer.kt:7 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `custom_ads[].buttonText` | string | required (Gson) | monetize/model/InHouseOffer.kt:8 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `InterCounter` | int | -1 (getInt default; ad every time) | monetize/engine/MonetizeConfigReader.kt:93<br>monetize/render/fullscreen/FullscreenOnAction.kt:271 | marketing,organic | WIRED |  |
| `InterBackCounter` | int | -1 | monetize/engine/MonetizeConfigReader.kt:93<br>monetize/render/fullscreen/FullscreenOnExit.kt:101 | marketing,organic | WIRED |  |
| `NativeCounter` | int | -1 | monetize/engine/MonetizeConfigReader.kt:94<br>monetize/render/InlineSponsor.kt:208 | marketing,organic | WIRED |  |
| `MidNativeCounter` | int | -1 | monetize/engine/MonetizeConfigReader.kt:94<br>monetize/render/InlineSponsor.kt:536 | marketing,organic | WIRED |  |
| `BannerCounter` | int | -1 | monetize/engine/MonetizeConfigReader.kt:94<br>monetize/render/StripAdLoader.kt:61 | marketing,organic | WIRED |  |
| `Config_Sync_Hrs` | int(hours) | -1 -> DEFAULT_STALE_HOURS = 6h backstop fetch (0 = every foreground) | monetize/engine/MonetizeConfigReader.kt:98<br>monetize/engine/RemoteValuesObserver.kt:104<br>monetize/engine/RemoteValuesObserver.kt:24 | no | MISSING_IN_TEMPLATE |  |
| `recent_playstore_window_sec` | int(sec) | -1 -> no time window (any time) | monetize/engine/MonetizeConfigReader.kt:99<br>monetize/render/RecentsAdObserver.kt:59 | marketing,organic | WIRED |  |
| `Inter_Loader_Ms` | int(ms, 0..3000) | -1 -> coerced to 0 = no loader beat before preloaded interstitial | monetize/engine/MonetizeConfigReader.kt:101<br>monetize/render/fullscreen/BlockingLoaderDialog.kt:37 | no | MISSING_IN_TEMPLATE |  |
| `link_first_then` | string | "" (no link-first chain) | monetize/engine/HomeSlotAds.kt:26 | marketing | WIRED_PARTIAL | CSV of reward\|inter\|app_open\|full_native\|custom\|directlink. Needs IsCustomADS + DirectLink. Present only in: marketing; absent in: organic (falls back to default there). |
| `link_first_show_all` | string | false (waterfall) | monetize/engine/HomeSlotAds.kt:26 | marketing | WIRED_PARTIAL | Present only in: marketing; absent in: organic (falls back to default there). |
| `link_open_in` | string | "" -> DirectLinkType | monetize/engine/HomeSlotAds.kt:26<br>homeshell/TrackerHomeAds.kt:102 | marketing,organic | WIRED | webview\|custom_tab\|browser; also read by TrackerHomeAds. |
| `inter_fallback` | string | "" (no fallback) | monetize/engine/HomeSlotAds.kt:26 | marketing | WIRED_PARTIAL | CSV rewarded\|full_native\|custom\|directlink Present only in: marketing; absent in: organic (falls back to default there). |
| `RewardedAds` | string | unset -> rewarded allowed (only "false" blocks) | monetize/engine/HomeSlotAds.kt:26 | marketing,organic | WIRED | Ingested as STRING (placement global key); JSON bool fine. |
| `googleFullNative` | string | "" -> googleNative | monetize/engine/HomeSlotAds.kt:27 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `ad_flow` | string | "" (placement default chain) | monetize/engine/HomeSlotAds.kt:27 | no | MISSING_OPTIONAL | global ad_flow; placement-specific preferred Optional override / alternate form; default is fine. |
| `ad_flow_show_all` | string | false | monetize/engine/HomeSlotAds.kt:27 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `<placement>_<key> (flat legacy, e.g. unlock_ads_on)` | string | n/a | monetize/engine/HomeSlotAds.kt:32 | no | OPTIONAL | Legacy flat form for placements leftPanel,rightPanel,drawer,onboarding,recent,appExit,unlock,splash,back,appOpen,install,uninstall,charge,discharge. Prefer the nested "placements" object. |
| `placements` | object | absent -> every placement on, no own flow | monetize/engine/HomeSlotAds.kt:56<br>monetize/engine/MonetizeConfigReader.kt:85 | marketing,organic | WIRED |  |
| `placements.<name>.ads_on` | string/bool/array | true (only "false" mutes) | monetize/engine/HomeSlotAds.kt:80 | marketing,organic | WIRED | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). |
| `placements.<name>.DirectLink` | string/bool/array | -> global DirectLink | monetize/engine/HomeSlotAds.kt:85 | no | MISSING_OPTIONAL | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). Optional override / alternate form; default is fine. |
| `placements.<name>.link_first_then` | string/bool/array | -> global | monetize/engine/HomeSlotAds.kt:26 | no | MISSING_OPTIONAL | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). Optional override / alternate form; default is fine. |
| `placements.<name>.link_first_show_all` | string/bool/array | -> global | monetize/engine/HomeSlotAds.kt:26 | no | MISSING_OPTIONAL | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). Optional override / alternate form; default is fine. |
| `placements.<name>.link_open_in` | string/bool/array | -> global | monetize/engine/HomeSlotAds.kt:26 | no | MISSING_OPTIONAL | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). Optional override / alternate form; default is fine. |
| `placements.<name>.inter_fallback` | string/bool/array | -> global | monetize/engine/HomeSlotAds.kt:26 | no | MISSING_OPTIONAL | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). Optional override / alternate form; default is fine. |
| `placements.<name>.googleInter` | string/bool/array | -> global | monetize/engine/HomeSlotAds.kt:99 | no | MISSING_OPTIONAL | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). Optional override / alternate form; default is fine. |
| `placements.<name>.googleRewarded` | string/bool/array | -> global | monetize/engine/HomeSlotAds.kt:98 | no | MISSING_OPTIONAL | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). Optional override / alternate form; default is fine. |
| `placements.<name>.googleAppopen` | string/bool/array | -> global | monetize/engine/HomeSlotAds.kt:100 | no | MISSING_OPTIONAL | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). Optional override / alternate form; default is fine. |
| `placements.<name>.googleFullNative` | string/bool/array | -> global/googleNative | monetize/engine/HomeSlotAds.kt:27 | no | MISSING_OPTIONAL | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). Optional override / alternate form; default is fine. |
| `placements.<name>.ad_flow` | string/bool/array | -> global ad_flow | monetize/engine/HomeSlotAds.kt:27 | marketing,organic | WIRED | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). |
| `placements.<name>.ad_flow_show_all` | string/bool/array | -> global | monetize/engine/HomeSlotAds.kt:27 | no | MISSING_OPTIONAL | Names resolved by code: onboarding, unlock, splash, back, appOpen, install, uninstall, charge, discharge, app, onboarding_welcome, onboarding_set_default, onboarding_intro, onboarding_language, onboarding_reminders (leftPanel/rightPanel/drawer/recent/appExit accepted but no caller). Optional override / alternate form; default is fine. |
| `permission_engine` | object | GrantOrigin DEFAULT_CONFIG (notification on Boot/WelcomeStage/MainHub/HomeStage) | grants/GrantOrigin.kt:64<br>grants/GrantOrigin.kt:15 | marketing,organic | WIRED | Ignored if the dedicated top-level permission_engine parameter is non-empty. |
| `permission_engine.<key>` | object | only key with a catalog entry: notification | grants/GrantToolbox.kt:14 | marketing,organic | WIRED |  |
| `permission_engine.<key>.enabled` | bool | false | grants/RemoteGrantDecoder.kt:32 | marketing,organic | WIRED |  |
| `permission_engine.<key>.activities` | array<string screen> | [] | grants/RemoteGrantDecoder.kt:24 | marketing,organic | WIRED |  |
| `permission_engine.<key>.delay` | long(ms) | 0 | grants/RemoteGrantDecoder.kt:34 | marketing,organic | WIRED |  |
| `permission_engine.<key>.priority` | int | Int.MAX_VALUE | grants/RemoteGrantDecoder.kt:35 | marketing,organic | WIRED |  |
| `permission_engine.<key>.show_once` | bool (alias showOnce) | false | grants/RemoteGrantDecoder.kt:36 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `intro_display` | object | per-section defaults below | monetize/engine/MonetizeConfigReader.kt:77<br>screens/reveal/TourUnveilSettings.kt:30 | marketing,organic | WIRED |  |
| `intro_display.language.enabled` | bool | enabled=true, once | screens/reveal/TourUnveilSettings.kt:69 | marketing,organic | WIRED |  |
| `intro_display.language.prompt_frequency` | string always\|once\|every_days\|app_launches\|never (else once) | once | screens/reveal/TourUnveilSettings.kt:71 | marketing,organic | WIRED |  |
| `intro_display.language.prompt_interval` | int | 0 | screens/reveal/TourUnveilSettings.kt:73 | marketing,organic | WIRED |  |
| `intro_display.terms.enabled` | bool | enabled=false, once | screens/reveal/TourUnveilSettings.kt:69 | marketing,organic | WIRED |  |
| `intro_display.terms.prompt_frequency` | string always\|once\|every_days\|app_launches\|never (else once) | once | screens/reveal/TourUnveilSettings.kt:71 | marketing,organic | WIRED |  |
| `intro_display.terms.prompt_interval` | int | 0 | screens/reveal/TourUnveilSettings.kt:73 | marketing,organic | WIRED |  |
| `intro_display.onboarding.enabled` | bool | enabled=false, once | screens/reveal/TourUnveilSettings.kt:69 | marketing,organic | WIRED |  |
| `intro_display.onboarding.prompt_frequency` | string always\|once\|every_days\|app_launches\|never (else once) | once | screens/reveal/TourUnveilSettings.kt:71 | marketing,organic | WIRED |  |
| `intro_display.onboarding.prompt_interval` | int | 0 | screens/reveal/TourUnveilSettings.kt:73 | marketing,organic | WIRED |  |
| `intro_display.permission_sheet.enabled` | bool | enabled=true, always | screens/reveal/TourUnveilSettings.kt:69 | marketing,organic | WIRED |  |
| `intro_display.permission_sheet.prompt_frequency` | string always\|once\|every_days\|app_launches\|never (else once) | always | screens/reveal/TourUnveilSettings.kt:71 | marketing,organic | WIRED |  |
| `intro_display.permission_sheet.prompt_interval` | int | 0 | screens/reveal/TourUnveilSettings.kt:73 | marketing,organic | WIRED |  |
| `intro_display.reminders_intro.enabled` | bool | enabled=true, once | screens/reveal/TourUnveilSettings.kt:69 | no | MISSING_IN_TEMPLATE |  |
| `intro_display.reminders_intro.prompt_frequency` | string always\|once\|every_days\|app_launches\|never (else once) | once | screens/reveal/TourUnveilSettings.kt:71 | no | MISSING_IN_TEMPLATE |  |
| `intro_display.reminders_intro.prompt_interval` | int | 0 | screens/reveal/TourUnveilSettings.kt:73 | no | MISSING_IN_TEMPLATE |  |
| `ScreenAds` | object keyed by screen name | {} -> show=true, global ids, nativeType mid | monetize/engine/MonetizeConfigReader.kt:77<br>monetize/engine/PageSponsorSettings.kt:37 | marketing,organic | WIRED |  |
| `ScreenAds.<screen>.show` | bool | true | monetize/engine/PageSponsorSettings.kt:47 | marketing,organic | WIRED |  |
| `ScreenAds.<screen>.banner` | string(ad unit) | -> googleBanner | monetize/engine/PageSponsorSettings.kt:64 | marketing,organic | WIRED | Used only when screen_wise_ad=true. |
| `ScreenAds.<screen>.bannerType` | string adaptive\|inline\|normal\|collapsible | adaptive | monetize/engine/PageSponsorSettings.kt:71 | marketing,organic | WIRED | Used only when screen_wise_ad=true (GLOBAL mode always adaptive). |
| `ScreenAds.<screen>.native` | string(ad unit) | -> googleNative | monetize/engine/PageSponsorSettings.kt:65 | marketing,organic | WIRED | Only ScreenAds.default.native is used for loading (nativeAdUnitId); per-screen native ids are resolved but natives are pooled. |
| `ScreenAds.<screen>.nativeType` | string big\|mid\|small\|native_banner | mid | monetize/engine/PageSponsorSettings.kt:49 | marketing,organic | WIRED | Only consumed by PinpointingActivity (SCREEN_KEY) - for every other screen the value is resolved and logged but has no effect. |
| `launcher_ads` | object (+ optional defaultHome / notDefaultHome overlay variants) | {} -> every launcher moment off, all slots hidden | monetize/engine/HomeAdsSettings.kt:24<br>monetize/engine/MonetizeConfigReader.kt:77 | marketing,organic | WIRED |  |
| `launcher_ads.(defaultHome\|notDefaultHome).*` | object overlay | none | monetize/engine/HomeAdsSettings.kt:26 | no | OPTIONAL | Deep-merged over the base when the app is / is not the default home. |
| `launcher_ads.<surface>.enabled` | bool (alias fallback_enabled) | false | monetize/engine/HomeAdsSettings.kt:315 | marketing,organic | WIRED | surface = app_click \| app_close \| left_panel.open_ad \| right_panel.open_ad \| home_click \| drawer_click \| home_back \| recent_click \| home_press |
| `launcher_ads.<surface>.counter` | int (alias ad_counter) | 0 | monetize/engine/HomeAdsSettings.kt:316 | marketing,organic | WIRED | surface = app_click \| app_close \| left_panel.open_ad \| right_panel.open_ad \| home_click \| drawer_click \| home_back \| recent_click \| home_press |
| `launcher_ads.<surface>.show_all` | bool (alias show_all_ads) | false | monetize/engine/HomeAdsSettings.kt:317 | marketing,organic | WIRED | surface = app_click \| app_close \| left_panel.open_ad \| right_panel.open_ad \| home_click \| drawer_click \| home_back \| recent_click \| home_press |
| `launcher_ads.<surface>.always_start_first` | bool (alias restart_sequence) | false | monetize/engine/HomeAdsSettings.kt:319 | marketing,organic | WIRED | surface = app_click \| app_close \| left_panel.open_ad \| right_panel.open_ad \| home_click \| drawer_click \| home_back \| recent_click \| home_press |
| `launcher_ads.<surface>.sequence` | array inter\|appopen\|app_open\|directlink\|link\|rewarded\|reward\|fullscreen_native\|full_native\|native\|custom | [inter,appopen,directlink,rewarded,fullscreen_native] | monetize/engine/HomeAdsSettings.kt:322 | marketing,organic | WIRED | surface = app_click \| app_close \| left_panel.open_ad \| right_panel.open_ad \| home_click \| drawer_click \| home_back \| recent_click \| home_press |
| `launcher_ads.<surface>.ads` | object {<type>:{enabled,ad_unit_id,urls\|links\|landing_urls,url,link,landing_url,open_type}} | ids fall back to googleInter/googleAppopen/DirectLink/googleRewarded/googleNative | monetize/engine/HomeAdsSettings.kt:320 | no | MISSING_OPTIONAL | surface = app_click \| app_close \| left_panel.open_ad \| right_panel.open_ad \| home_click \| drawer_click \| home_back \| recent_click \| home_press Optional override / alternate form; default is fine. |
| `launcher_ads.<surface>.directlink` | object | none | monetize/engine/HomeAdsSettings.kt:73 | no | MISSING_OPTIONAL | surface = app_click \| app_close \| left_panel.open_ad \| right_panel.open_ad \| home_click \| drawer_click \| home_back \| recent_click \| home_press Optional override / alternate form; default is fine. |
| `launcher_ads.<surface>.directlink_urls` | array<string> | none | monetize/engine/HomeAdsSettings.kt:74 | marketing,organic | WIRED | surface = app_click \| app_close \| left_panel.open_ad \| right_panel.open_ad \| home_click \| drawer_click \| home_back \| recent_click \| home_press |
| `launcher_ads.<surface>.on_demand` | bool | false | monetize/engine/HomeAdsSettings.kt:349 | marketing,organic | WIRED | surface = app_click \| app_close \| left_panel.open_ad \| right_panel.open_ad \| home_click \| drawer_click \| home_back \| recent_click \| home_press |
| `launcher_ads.<surface>.loader_timeout_ms` | long (alias on_demand_timeout_ms), 1000..20000 | 8000 | monetize/engine/HomeAdsSettings.kt:298 | marketing,organic | WIRED | surface = app_click \| app_close \| left_panel.open_ad \| right_panel.open_ad \| home_click \| drawer_click \| home_back \| recent_click \| home_press |
| `launcher_ads.<surface>.<type>` | object (per-format block at surface level) | none | monetize/engine/HomeAdsSettings.kt:332 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `launcher_ads.app_click` | object (ad flow) | absent -> off | monetize/engine/HomeAdsSettings.kt:30 | marketing,organic | WIRED |  |
| `launcher_ads.app_close` | object (ad flow) | absent -> off | monetize/engine/HomeAdsSettings.kt:31 | marketing,organic | WIRED |  |
| `launcher_ads.left_panel.open_ad` | object (ad flow) | absent -> off | monetize/engine/HomeAdsSettings.kt:51 | marketing,organic | WIRED |  |
| `launcher_ads.right_panel.open_ad` | object (ad flow) | absent -> off | monetize/engine/HomeAdsSettings.kt:52 | marketing,organic | WIRED |  |
| `launcher_ads.home_click` | object (ad flow) | absent -> uses app_click block (clickSurface) | monetize/engine/HomeAdsSettings.kt:35 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `launcher_ads.drawer_click` | object (ad flow) | absent -> uses app_click block (clickSurface) | monetize/engine/HomeAdsSettings.kt:37 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `launcher_ads.home_back` | object (ad flow) | absent -> off | monetize/engine/HomeAdsSettings.kt:39 | marketing,organic | WIRED |  |
| `launcher_ads.recent_click` | object (ad flow) | absent -> off | monetize/engine/HomeAdsSettings.kt:41 | marketing,organic | WIRED |  |
| `launcher_ads.home_press` | object (ad flow) | absent -> off | monetize/engine/HomeAdsSettings.kt:43 | no | MISSING_IN_TEMPLATE |  |
| `launcher_ads.recent_click.return_window_sec` | long(sec) | 60 | monetize/engine/HomeAdsSettings.kt:65 | marketing,organic | WIRED |  |
| `launcher_ads.(left_panel\|right_panel).enabled` | bool | true (panel open) | monetize/engine/HomeAdsSettings.kt:120 | marketing,organic | WIRED |  |
| `launcher_ads.<slot>` | object (slot) | panel/drawer slots: absent -> hidden; onboarding slots: absent -> native with screen default type | monetize/engine/HomeAdsSettings.kt:151<br>monetize/engine/HomeAdsSettings.kt:155 | marketing,organic | WIRED | slot = left_panel.native_bottom \| left_panel.native_mid \| right_panel.native \| app_drawer.bottom_native \| onboarding.<screen>.slot |
| `launcher_ads.<slot>.enabled` | bool | true | monetize/engine/HomeAdsSettings.kt:495 | marketing,organic | WIRED |  |
| `launcher_ads.<slot>.ad_type` | string native\|banner\|none | native | monetize/engine/HomeAdsSettings.kt:488 | marketing,organic | WIRED |  |
| `launcher_ads.<slot>.native_type` | string big\|mid\|mid2\|native_banner | slot default (mid2 / native_banner / big / mid) | monetize/engine/HomeAdsSettings.kt:497 | marketing,organic | WIRED |  |
| `launcher_ads.<slot>.banner_type` | string adaptive\|inline\|normal\|collapsible | adaptive | monetize/engine/HomeAdsSettings.kt:498 | marketing,organic | WIRED |  |
| `launcher_ads.<slot>.ad_unit_id` | string | "" (global unit) | monetize/engine/HomeAdsSettings.kt:499 | marketing,organic | WIRED |  |
| `launcher_ads.<slot>.position` | int | 0 (app_drawer: launcher ad row) | monetize/engine/HomeAdsSettings.kt:501 | organic | WIRED_PARTIAL | Present only in: organic; absent in: marketing (falls back to default there). |
| `launcher_ads.left_panel.native_bottom` | object (slot) | absent -> hidden | monetize/engine/HomeAdsSettings.kt:139 | marketing,organic | WIRED |  |
| `launcher_ads.left_panel.native_mid` | object (slot) | absent -> hidden | monetize/engine/HomeAdsSettings.kt:143 | marketing,organic | WIRED |  |
| `launcher_ads.right_panel.native` | object (slot) | absent -> hidden | monetize/engine/HomeAdsSettings.kt:147 | marketing,organic | WIRED |  |
| `launcher_ads.app_drawer.bottom_native` | object (slot) | absent -> hidden (no drawer native; launcher ad_row_position=0) | monetize/engine/HomeAdsSettings.kt:155 | organic | WIRED_PARTIAL | Present only in: organic; absent in: marketing (falls back to default there). |
| `launcher_ads.onboarding.welcome` | object | absent -> inter off, mid native | monetize/engine/HomeAdsSettings.kt:567 | marketing,organic | WIRED |  |
| `launcher_ads.onboarding.set_default` | object | absent -> inter off, big native | monetize/engine/HomeAdsSettings.kt:568 | marketing,organic | WIRED |  |
| `launcher_ads.onboarding.intro` | object | absent -> inter ON, mid2 native | monetize/engine/HomeAdsSettings.kt:569 | marketing,organic | WIRED |  |
| `launcher_ads.onboarding.language` | object | absent -> inter ON, big native | monetize/engine/HomeAdsSettings.kt:570 | marketing,organic | WIRED |  |
| `launcher_ads.onboarding.reminders` | object | absent -> inter off, mid native | monetize/engine/HomeAdsSettings.kt:572 | organic | WIRED_PARTIAL | Present only in: organic; absent in: marketing (falls back to default there). |
| `launcher_ads.package_result.(enabled\|min_gap_ms\|body_native)` | bool/long/bool | true / 0 / true | monetize/engine/HomeAdsSettings.kt:169 | marketing,organic | WIRED |  |
| `launcher_ads.system_ads.(charge\|discharge).enabled` | bool | false | monetize/engine/HomeAdsSettings.kt:198 | marketing,organic | WIRED |  |
| `launcher_ads.system_ads.(charge\|discharge).native` | string off\|big\|mid\|mid2\|native_banner (else mid) | off | monetize/engine/HomeAdsSettings.kt:206<br>screens/battery/PowerStateActivity.kt:170 | marketing,organic | WIRED | Template value "MediumNative" is not a recognised token; it falls to the else-branch = mid native (works by accident). Prefer "mid". |
| `launcher_ads.system_ads.(charge\|discharge).close_ad` | string none\|inter\|full_native\|directlink\|link | none | monetize/engine/HomeAdsSettings.kt:207 | marketing,organic | WIRED |  |
| `launcher_ads.system_ads.(charge\|discharge).min_gap_sec` | long | 0 | monetize/engine/HomeAdsSettings.kt:208 | marketing,organic | WIRED |  |
| `launcher_ads.onboarding.order` | array welcome\|set_default\|intro\|language\|reminders | [set_default, language, welcome, reminders, intro] (reminders auto-inserted after welcome) | monetize/engine/HomeAdsSettings.kt:630 | marketing,organic | WIRED |  |
| `launcher_ads.onboarding.<screen>.inter_enabled` | bool | per screen: intro,language=true; welcome,set_default,reminders=false | monetize/engine/HomeAdsSettings.kt:603 | marketing,organic | WIRED |  |
| `launcher_ads.onboarding.<screen>.ads_counter` | int | 0 | monetize/engine/HomeAdsSettings.kt:611 | marketing,organic | WIRED |  |
| `launcher_ads.onboarding.<screen>.skip_enabled` | bool | true | monetize/engine/HomeAdsSettings.kt:595 | marketing,organic | WIRED | Only read for welcome, set_default, intro (onboardingUi callers). |
| `launcher_ads.onboarding.<screen>.back_action` | string next_screen | not next_screen | monetize/engine/HomeAdsSettings.kt:596 | marketing,organic | WIRED | Only read for welcome, set_default, intro. |
| `launcher_ads.onboarding.<screen>.auto_next` | bool | true | monetize/engine/HomeAdsSettings.kt:654 | marketing,organic | WIRED | Only read for set_default (HomeRoleStageActivity). |
| `launcher_ads.onboarding.<screen>.auto_next_delay_ms` | long 0..5000 | 650 | monetize/engine/HomeAdsSettings.kt:655 | marketing,organic | WIRED | Only read for set_default. |
| `launcher_ads.default_home_screen.enabled` | bool | true | monetize/engine/HomeAdsSettings.kt:121 | marketing,organic | WIRED |  |
| `launcher_ads.default_home_screen.skip_if_default` | bool | true | monetize/engine/HomeAdsSettings.kt:672 | marketing,organic | WIRED |  |
| `launcher_ads.default_home_screen.skip_rest_on_grant` | bool | true | monetize/engine/HomeAdsSettings.kt:673 | marketing,organic | WIRED |  |
| `launcher_ads.recent_ad` | object | n/a | monetize/engine/HomeAdsSettings.kt:194 | no | DEAD_CODE | Parser exists (recentAdSettings) but nothing calls it; do not publish. |
| `launcher_ads.home_hint` | object | n/a | monetize/engine/HomeAdsSettings.kt:534 | no | DEAD_CODE | Parser exists (homeHint) but nothing calls it; do not publish. |
| `launcher_ads.app_drawer.promo` | object | n/a | monetize/engine/HomeAdsSettings.kt:223 | no | DEAD_CODE | Parser exists (drawerPromoItems) but nothing calls it; do not publish. |
| `launcher_ads.home_open` | object | n/a | - | marketing | UNUSED_IN_TEMPLATE | No HomeDeskFragment surface named home_open (surfaces: app_click, app_close, left_panel.open_ad, right_panel.open_ad, home_click, drawer_click, home_back, recent_click, home_press). Dropped in the *_fix variants. |
| `launcher_ads.reminders` | object | n/a | monetize/engine/HomeAdsSettings.kt:659 | marketing | MISMATCH | MISPLACED: code reads launcher_ads.onboarding.reminders.*, not launcher_ads.reminders.*. Move it under onboarding (the *_fix variants do). Today reminders falls back to defaults (inter off, mid native slot) - same as the template values, so no behaviour change, but edits to it would be ignored. |
| `premium` | object | absent -> enabled, show_after_intro, close 5s, default plans | monetize/engine/MonetizeConfigReader.kt:79<br>repo/billing/ProSettings.kt:32 | marketing,organic | WIRED |  |
| `premium.enabled` | bool | true | repo/billing/ProSettings.kt:61 | marketing,organic | WIRED |  |
| `premium.show_after_intro` | bool | true | repo/billing/ProSettings.kt:62 | marketing,organic | WIRED |  |
| `premium.close_delay_sec` | int 0..30 | 5 | repo/billing/ProSettings.kt:63 | marketing,organic | WIRED |  |
| `premium.free_search_limit` | int | 3 | repo/billing/ProSettings.kt:65 | marketing,organic | WIRED |  |
| `premium.rewarded_extra_searches` | int >=0 | 0 | repo/billing/ProSettings.kt:66 | marketing,organic | WIRED |  |
| `premium.close_button_opacity` | double 0.3..1 | 1.0 | repo/billing/ProSettings.kt:67 | no | MISSING_IN_TEMPLATE |  |
| `premium.products` | array | DEFAULT_PLANS: gps_premium_weekly (SUGGESTED, 3-day trial), gps_premium_monthly, gps_premium_lifetime | repo/billing/ProSettings.kt:45<br>repo/billing/ProSettings.kt:36 | no | MISSING_IN_TEMPLATE | Product IDs must exist in the NEW app's Play Console (either publish products[] or create the three default IDs). |
| `premium.products[].id` | string (required) | entry dropped | repo/billing/ProSettings.kt:48 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `premium.products[].type` | string "inapp" = lifetime | subscription | repo/billing/ProSettings.kt:50 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `premium.products[].period` | string P1M = monthly | weekly | repo/billing/ProSettings.kt:51 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `premium.products[].tag` | string | "" | repo/billing/ProSettings.kt:55 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `premium.products[].default` | bool | false | repo/billing/ProSettings.kt:55 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `premium.products[].trial_days` | int | 0 | repo/billing/ProSettings.kt:56 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `premium.products[].price` | string | n/a | - | no | UNUSED_NOT_IN_TEMPLATE | Not read (price comes from Play Billing). Present only in DEBUG_* variants. |
| `rewarded` | object | absent -> FREE mode, 120s cooldown, backoff on, max 900s, 2 retries | monetize/engine/MonetizeConfigReader.kt:81<br>monetize/engine/BonusAdSettings.kt:17 | marketing,organic | WIRED |  |
| `rewarded.nofill_mode` | string wait\|free | free | monetize/engine/BonusAdSettings.kt:26 | marketing,organic | WIRED |  |
| `rewarded.nofill_cooldown_sec` | int | 120 | monetize/engine/BonusAdSettings.kt:24 | marketing,organic | WIRED |  |
| `rewarded.nofill_backoff` | bool | true | monetize/engine/BonusAdSettings.kt:28 | marketing,organic | WIRED |  |
| `rewarded.nofill_cooldown_max_sec` | int | 900 | monetize/engine/BonusAdSettings.kt:29 | marketing,organic | WIRED |  |
| `rewarded.max_retries` | int | 2 | monetize/engine/BonusAdSettings.kt:30 | marketing,organic | WIRED |  |
| `launcher_config` | object (flat, per audience) | absent -> top-level launcher_config param only | monetize/engine/MonetizeConfigReader.kt:83<br>homeshell/TrackerHomeBridge.kt:42 | marketing,organic | WIRED | Overlay: its top-level keys replace the top-level launcher_config parameter's keys (key by key, not deep). |
| `launcher_config.os_style` | bool | false | launcher:setup/HomeOptions.kt:97 | marketing,organic | WIRED |  |
| `launcher_config.search_widget` | string google\|chrome | google | launcher:setup/HomeOptions.kt:98 | marketing,organic | WIRED |  |
| `launcher_config.should_show_time_widget` | bool | true | launcher:setup/HomeOptions.kt:100 | no | MISSING_IN_TEMPLATE |  |
| `launcher_config.should_show_search_widget` | bool | true | launcher:setup/HomeOptions.kt:101 | no | MISSING_IN_TEMPLATE |  |
| `launcher_config.default_launcher_prompt` | bool | true | launcher:setup/HomeOptions.kt:102 | marketing,organic | WIRED |  |
| `launcher_config.guide.enabled` | bool | true | launcher:setup/HomeOptions.kt:104 | marketing,organic | WIRED |  |
| `launcher_config.guide.<step>` | bool (swipe_right_contacts\|swipe_left_apps\|swipe_up_drawer) | true | launcher:setup/HomeOptions.kt:82<br>launcher:sponsor/HomeTutorialStage.kt:5 | marketing,organic | WIRED |  |
| `launcher_config.panels.*` | object | n/a | homeshell/TrackerHomeBridge.kt:57 | no | UNUSED_NOT_IN_TEMPLATE | Overwritten by the host: TrackerHomeBridge.imprintLauncherAds always replaces panels with launcher_ads.left_panel/right_panel.enabled. |
| `launcher_config.gestures.*` | object | n/a | homeshell/TrackerHomeBridge.kt:67 | no | UNUSED_NOT_IN_TEMPLATE | Overwritten by the host: every gesture (left_swipe, right_swipe, drawer_open, app_launch, home_launch, drawer_launch, home_back, home_press) is rebuilt from launcher_ads. |
| `launcher_config.unlock_ads` | object | absent -> unlock ad off | launcher:setup/HomeOptions.kt:107<br>homeshell/UnlockAdObserver.kt:100 | marketing,organic | WIRED |  |
| `launcher_config.unlock_ads.enabled` | bool | false | homeshell/UnlockAdObserver.kt:104 | marketing,organic | WIRED |  |
| `launcher_config.unlock_ads.start_after_hours` | long | 24 | homeshell/UnlockAdObserver.kt:108 | marketing,organic | WIRED |  |
| `launcher_config.unlock_ads.gap_minutes` | long | 30 | homeshell/UnlockAdObserver.kt:110 | marketing,organic | WIRED |  |
| `launcher_config.unlock_ads.max_per_day` | int | 5 | homeshell/UnlockAdObserver.kt:113 | marketing,organic | WIRED |  |
| `launcher_config.unlock_ads.format` | string inter\|full_native | inter | homeshell/UnlockAdObserver.kt:84 | marketing,organic | WIRED |  |
| `launcher_config.unlock_ads.countries.<CC>.*` | object per ISO-2 override | none | homeshell/UnlockAdObserver.kt:102 | no | OPTIONAL | Optional per-country override of any unlock_ads field. |
| `launcher_config.drawerAppAdEnabled` | bool | false (also forced false when IsAdsON=false) | launcher:setup/HomeOptions.kt:109<br>homeshell/TrackerHomeBridge.kt:86 | marketing,organic | WIRED |  |
| `launcher_config.drawerAppAdEvery` | int >=1 | 8 | launcher:setup/HomeOptions.kt:110 | marketing,organic | WIRED |  |
| `launcher_config.drawerAppAds` | array | [] | launcher:setup/HomeOptions.kt:111 | marketing,organic | WIRED |  |
| `launcher_config.drawerAppAds[].logo` | string | "" (landing_url required, else tile dropped) | launcher:setup/HomeOptions.kt:121 | marketing,organic | WIRED | Optional override / alternate form; default is fine. |
| `launcher_config.drawerAppAds[].landing_url` | string | "" (landing_url required, else tile dropped) | launcher:setup/HomeOptions.kt:119 | marketing,organic | WIRED | Optional override / alternate form; default is fine. |
| `launcher_config.drawerAppAds[].label` | string | "" (landing_url required, else tile dropped) | launcher:setup/HomeOptions.kt:121 | marketing,organic | WIRED | Optional override / alternate form; default is fine. |
| `launcher_config.dock_host_app` | bool | true (dock slot = this app; false = default SMS app) | homeshell/TrackerHomeBridge.kt:91 | marketing,organic | WIRED | Read by the host from the MERGED launcher_config. |

## 4. `feature_config` (per audience block)

| Path | Type | Code default | Readers | In template | Status | Notes |
|---|---|---|---|---|---|---|
| `feature_title` | string | "" (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:19 | marketing,organic | WIRED |  |
| `feature_desc` | string ({title},{time}) | "" (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:21 | marketing,organic | WIRED |  |
| `feature_action` | string | "" (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:22 | marketing,organic | WIRED |  |
| `action_delay_options` | string CSV minutes | 5,10,30,60 (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:24 | marketing,organic | WIRED |  |
| `is_default_config` | bool | false (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:26 | marketing,organic | WIRED |  |
| `default_config_list` | array<{title,note,daysAhead}> | [] (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:27 | marketing,organic | WIRED |  |
| `is_feature_prompt` | bool | false (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:29 | marketing,organic | WIRED |  |
| `is_enhanced_mode` | bool | true (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:31 | marketing,organic | WIRED |  |
| `is_intro_screen` | bool | false (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:33 | marketing,organic | WIRED |  |
| `prompt_frequency` | string always\|once\|every_days\|app_launches\|reminder_creations\|never | always (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:35 | marketing,organic | WIRED |  |
| `prompt_interval` | int | 0 (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:36 | marketing,organic | WIRED |  |
| `is_country_filter` | bool | false (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:38 | marketing,organic | WIRED |  |
| `excluded_countries` | string CSV ISO-2 | "" (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:40 | marketing,organic | WIRED |  |
| `is_settings_row` | bool | false (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:42 | marketing,organic | WIRED |  |
| `settings_row_excluded_countries` | string CSV ISO-2 | "" (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:43 | marketing,organic | WIRED |  |
| `intro_title` | string | "" (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:45 | marketing,organic | WIRED |  |
| `intro_desc` | string | "" (field default; whole-param default = in-app DEFAULT_CONFIG) | alarms/AlarmSettings.kt:46 | marketing,organic | WIRED |  |
| `default_config_list[].title` | string | "" | alarms/AlarmSettings.kt:13 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `default_config_list[].note` | string | "" | alarms/AlarmSettings.kt:14 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `default_config_list[].daysAhead` | int | 1 | alarms/AlarmSettings.kt:15 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |

## 5. `launcher_config` top-level parameter (per audience block)

| Path | Type | Code default | Readers | In template | Status | Notes |
|---|---|---|---|---|---|---|
| `os_style` | bool | false | launcher:setup/HomeOptions.kt:97 | organic,marketing | WIRED |  |
| `search_widget` | string google\|chrome | google | launcher:setup/HomeOptions.kt:98 | organic,marketing | WIRED |  |
| `should_show_time_widget` | bool | true | launcher:setup/HomeOptions.kt:100 | organic,marketing | WIRED |  |
| `should_show_search_widget` | bool | true | launcher:setup/HomeOptions.kt:101 | organic,marketing | WIRED |  |
| `default_launcher_prompt` | bool | true | launcher:setup/HomeOptions.kt:102 | organic,marketing | WIRED |  |
| `guide.enabled` | bool | true | launcher:setup/HomeOptions.kt:104 | organic,marketing | WIRED |  |
| `guide.<step>` | bool (swipe_right_contacts\|swipe_left_apps\|swipe_up_drawer) | true | launcher:setup/HomeOptions.kt:82<br>launcher:sponsor/HomeTutorialStage.kt:5 | organic,marketing | WIRED |  |
| `panels.*` | object | n/a | homeshell/TrackerHomeBridge.kt:57 | organic,marketing | UNUSED_IN_TEMPLATE | Overwritten by the host: TrackerHomeBridge.imprintLauncherAds always replaces panels with launcher_ads.left_panel/right_panel.enabled. |
| `gestures.*` | object | n/a | homeshell/TrackerHomeBridge.kt:67 | organic,marketing | UNUSED_IN_TEMPLATE | Overwritten by the host: every gesture (left_swipe, right_swipe, drawer_open, app_launch, home_launch, drawer_launch, home_back, home_press) is rebuilt from launcher_ads. |
| `unlock_ads` | object | absent -> unlock ad off | launcher:setup/HomeOptions.kt:107<br>homeshell/UnlockAdObserver.kt:100 | no | MISSING_IN_TEMPLATE |  |
| `unlock_ads.enabled` | bool | false | homeshell/UnlockAdObserver.kt:104 | no | MISSING_IN_TEMPLATE |  |
| `unlock_ads.start_after_hours` | long | 24 | homeshell/UnlockAdObserver.kt:108 | no | MISSING_IN_TEMPLATE |  |
| `unlock_ads.gap_minutes` | long | 30 | homeshell/UnlockAdObserver.kt:110 | no | MISSING_IN_TEMPLATE |  |
| `unlock_ads.max_per_day` | int | 5 | homeshell/UnlockAdObserver.kt:113 | no | MISSING_IN_TEMPLATE |  |
| `unlock_ads.format` | string inter\|full_native | inter | homeshell/UnlockAdObserver.kt:84 | no | MISSING_IN_TEMPLATE |  |
| `unlock_ads.countries.<CC>.*` | object per ISO-2 override | none | homeshell/UnlockAdObserver.kt:102 | no | OPTIONAL | Optional per-country override of any unlock_ads field. |
| `drawerAppAdEnabled` | bool | false (also forced false when IsAdsON=false) | launcher:setup/HomeOptions.kt:109<br>homeshell/TrackerHomeBridge.kt:86 | organic,marketing | WIRED |  |
| `drawerAppAdEvery` | int >=1 | 8 | launcher:setup/HomeOptions.kt:110 | organic,marketing | WIRED |  |
| `drawerAppAds` | array | [] | launcher:setup/HomeOptions.kt:111 | organic,marketing | WIRED |  |
| `drawerAppAds[].logo` | string | "" (landing_url required, else tile dropped) | launcher:setup/HomeOptions.kt:121 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `drawerAppAds[].landing_url` | string | "" (landing_url required, else tile dropped) | launcher:setup/HomeOptions.kt:119 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `drawerAppAds[].label` | string | "" (landing_url required, else tile dropped) | launcher:setup/HomeOptions.kt:121 | no | MISSING_OPTIONAL | Optional override / alternate form; default is fine. |
| `dock_host_app` | bool | true (dock slot = this app; false = default SMS app) | homeshell/TrackerHomeBridge.kt:91 | no | MISSING_IN_TEMPLATE | Read by the host from the MERGED launcher_config. Supplied by the GET_DATA_LIST_1 overlay (blob launcher_config), so absence in this file is harmless. |

## 6. `ads_config` (launcher entries file)

| Path | Type | Code default | Readers | In template | Status | Notes |
|---|---|---|---|---|---|---|
| `screenWiseAds.app_drawer.ad_row_position` | int | synthesised by host from launcher_ads.app_drawer.bottom_native.position | homeshell/TrackerHomeBridge.kt:31<br>launcher:sponsor/HomeAdsSettings.kt:78 | flat | UNUSED_IN_TEMPLATE | The host intercepts configString("ads_config") and returns a synthesised JSON - the ads_config RC parameter is never read in this app. |
