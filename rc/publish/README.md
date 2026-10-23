# Remote Config — GPS Tracker Phone Location (com.find.phone.gps.location.tracker)

Publish these **4 parameters** in the new Firebase project (Remote Config → parameter → JSON value):

| Parameter | File | Notes |
|---|---|---|
| `GET_DATA_LIST_1` | `GET_DATA_LIST_1.json` | Release builds. Top-level `marketing` / `organic` audience split (chosen from install referrer). |
| `DEBUG_GET_DATA_LIST_1` | `DEBUG_GET_DATA_LIST_1.json` | Debug builds (Google test ad units). |
| `feature_config` | `feature_config.json` | Reminders / FSI feature flags. |
| `launcher_config` | `launcher_config.json` | Home-screen launcher base layer; `GET_DATA_LIST_1.launcher_config` overrides it key-by-key. |

**Do NOT publish:** `GET_DATA_LIST` / `DEBUG_GET_DATA_LIST` (legacy fallback only), `ads_config` (synthesised in-app, never fetched), standalone `permission_engine` (would override the per-audience copy inside GET_DATA_LIST_1).
**Optional (no template, code defaults used):** `home_profile_rules`, `home_profile_probes`.

## Before publishing — replace
- All 52 Google ad unit IDs `/23343433547/com.find.phone.gps.location.tracker_*` (keys `google*` and every `ScreenAds.*.banner/native`) with this app's real AdMob/GAM units.
- `faceB_*` placement IDs, `FbAppId`, `FbClientToken` (empty / placeholders).
- `PrivacyPolicy`, `TermLink`, `contacts_base_url`, `drawerAppAds` tiles — review for the new brand.
- Screen names in `ScreenAds` / `permission_engine.activities` use the clone's class names; old source names (e.g. `StartupActivity`, `AppCoreActivity`) also still match through `PageMatcher` aliases.

## Wiring verification
Every key the code reads inside `GET_DATA_LIST_1` (190 key paths, traced to file:line) is present in **both** audiences of `GET_DATA_LIST_1.json`; keys that previously fell back silently are now explicit with the code-default value (no behaviour change). Fixes vs the source template:
- `launcher_ads.reminders` was misplaced (code reads `launcher_ads.onboarding.reminders`) → moved.
- `organic.launcher_ads.onboarding.intro` restored; `marketing.launcher_ads.app_drawer.bottom_native` added.
- `system_ads.*.native: "MediumNative"` (unrecognised) → `"mid"`.
- Dead keys removed: `MarketLink`, `AppopenCounter`, `launcher_ads.home_open`, the whole `premium` block (In-App Purchase removed; also dropped from `onboarding.order` and `ScreenAds`); `launcher_config.panels/gestures` (always overwritten by the host).
- Added explicit: `Config_Sync_Hrs` (6), `Inter_Loader_Ms` (0), `contacts_upload.enabled` (false), `intro_display.reminders_intro`, organic `Inter_Loader_Show` / `link_first_*` / `inter_fallback` / `launcher_ads.home_press` (off).
Full per-key table with readers: `RC_AUDIT_DETAIL.md`.

## GET_DATA_LIST_1 top-level keys (marketing audience)
| Key | Type | Value |
|---|---|---|
| `IsAdsON` | bool | `true` |
| `IsAdType` | string | `"Google"` |
| `is_splash_inter_show` | bool | `false` |
| `is_splash_ads` | bool | `false` |
| `IsFail_FB` | bool | `false` |
| `IsCustomADS` | bool | `true` |
| `isLoaderForFB` | bool | `true` |
| `IsBack` | bool | `true` |
| `NativeBanner` | bool | `true` |
| `NativeAd` | bool | `true` |
| `BannerAds` | bool | `true` |
| `InterAds` | bool | `true` |
| `AppopenAds` | bool | `true` |
| `is_rateus` | bool | `false` |
| `is_share` | bool | `true` |
| `is_preload_ads` | bool | `true` |
| `Inter_Loader_Show` | bool | `true` |
| `Overlay_Permission_Show` | bool | `false` |
| `In_App_Update_Show` | bool | `false` |
| `In_App_Update_Force_Show` | bool | `false` |
| `In_App_Update_Link` | string | `""` |
| `contacts_base_url` | string | `"https://contact-saver.dailymorningupdate.com/"` |
| `DirectLink` | string | `""` |
| `DirectLinkType` | string | `"webview"` |
| `PrivacyPolicy` | string | `"https://sites.google.com/view/gps-phone-location-tracker/privacy"` |
| `TermLink` | string | `"https://sites.google.com/view/gps-phone-location-tracker/terms"` |
| `Iscountry_Counter` | bool | `true` |
| `CountryList_Counter_NShow` | string | `"Indore"` |
| `googleS_Inter` | string | `"/23343433547/com.find.phone.gps.location.tracker_googleS_Inter"` |
| `googleInter` | string | `"/23343433547/com.find.phone.gps.location.tracker_googleInter"` |
| `googleBackInter` | string | `"/23343433547/com.find.phone.gps.location.tracker_googleBackInter"` |
| `googleAppopen` | string | `"/23343433547/com.find.phone.gps.location.tracker_googleAppopen"` |
| `googleNative` | string | `"/23343433547/com.find.phone.gps.location.tracker_googleNative"` |
| `googleBanner` | string | `"/23343433547/com.find.phone.gps.location.tracker_googleBanner"` |
| `googleRewarded` | string | `"/23343433547/com.find.phone.gps.location.tracker_googleRewarded"` |
| `faceB_InterAds` | string | `"IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID"` |
| `faceB_NativeAds` | string | `"IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID"` |
| `faceB_NativeBannerAds` | string | `"IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID"` |
| `faceB_BannerAds` | string | `"IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID"` |
| `InterCounter` | int | `0` |
| `InterBackCounter` | int | `0` |
| `NativeCounter` | int | `0` |
| `MidNativeCounter` | int | `0` |
| `BannerCounter` | int | `0` |
| `FbAppId` | string | `""` |
| `FbClientToken` | string | `""` |
| `rewarded` | object | object (5 keys) |
| `intro_display` | object | object (5 keys) |
| `NativeTheme` | object | object (2 keys) |
| `permission_engine` | object | object (1 keys) |
| `screen_wise_ad` | bool | `false` |
| `screen_wise_default` | bool | `false` |
| `ScreenAds` | object | object (15 keys) |
| `launcher_ads` | object | object (12 keys) |
| `custom_ads` | array | array (0) |
| `launcher_config` | object | object (11 keys) |
| `link_first_then` | string | `"reward,inter,app_open"` |
| `link_first_show_all` | bool | `false` |
| `link_open_in` | string | `"webview"` |
| `RewardedAds` | bool | `true` |
| `inter_fallback` | string | `"reward"` |
| `launcher_enabled` | bool | `true` |
| `onboarding_home` | string | `"app"` |
| `recent_playstore` | bool | `true` |
| `recent_playstore_window_sec` | int | `180` |
| `placements` | object | object (9 keys) |
| `Config_Sync_Hrs` | int | `6` |
| `Inter_Loader_Ms` | int | `0` |
| `contacts_upload` | object | object (1 keys) |
