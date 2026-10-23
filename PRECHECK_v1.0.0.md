# Pre-release checklist — GPS Tracker Phone Location

Package: `com.find.phone.gps.location.tracker` · filled from the code on branch `v_1.0.0`

## Version *
`1.0.0`

## AAB *
- [ ] AAB built & uploaded to the Play Console — not done yet, tick it only after upload
- Version code: `1` (`app/build.gradle.kts`)

## Change log * (0 / 4000)
```
- First release: live phone location, find-my-phone and number finder
- Home launcher (panels, app drawer) with default-home setup
- Quota tile now sits on its own line above the Find button
- Navigation bar hidden app-wide (swipe to show)
- Uninstall feedback flow with remote-config controlled ads
- In-app update (flexible / immediate) with analytics events
- 11 languages: ar, es, fr, hi, ja, pt, ru, th, tr, vi, zh (+ English)
- Fixes: ad title bar showing app name, dead ad-counter config removed
```

## APIs used in the app * — **Uses APIs**
| API name | What it's used for, provider / endpoint, account |
|---|---|
| Contact saver backend | Finds similar numbers (`GET similar-phone-number`) and uploads contacts (`POST android/upload/contacts`). Base URL `https://contact-saver.dailymorningupdate.com/`, overridable by Remote Config `contacts_base_url`. |
| ip-api.com | Country lookup for audience/geo gating. `http://ip-api.com/json/` (cleartext, free tier). |
| Firebase (Remote Config, Analytics, Crashlytics) | Project `gpstrackerphonelocation` (number `1034768878900`). |
| AdMob / Google Mobile Ads | Ads and paid-event revenue logging (`ad_impression`). |
| Meta Audience Network + Facebook SDK | Fallback ads; needs `FbAppId` / `FbClientToken` in RC. |
| LightHouse push SDK | Push notifications; base URL from `BuildConfig.LH_BASE_URL`. |
| Google Play In-App Update | Flexible / immediate update, gated by RC `In_App_Update_Show` and `In_App_Update_Force_Show`. |
| Google Maps (web links) | Opens `google.com/maps` for a location. No API key. |

## Notification SDK version *
`LightHouse 0.7.7` (`io.lighthouse:light-house` and `light-house-extended`)

## Ads SDK version *
`AdMob 24.9.0` (`play-services-ads`); also Facebook SDK `18.1.3`

## Firebase email *
`<fill in: the Google account that owns project gpstrackerphonelocation>`. The code cannot tell me this.

## Footer
- [ ] Store metadata (listing, screenshots, declarations) is updated for this version
- [ ] I confirm these details are correct for this version

## Check before ticking the boxes
- Contacts upload goes to a third-party server. The Play Data safety form and privacy policy must say so.
- `ip-api.com` is called over `http://`. Check the network security config allows it, or move it to HTTPS.
- Replace the placeholder ad unit IDs, `faceB_*` IDs, `FbAppId` and `FbClientToken` in `rc/publish/` before publishing the RC.
