# Publishing on Google Play

Step-by-step notes for releasing TunerToGo in the Play Console, with the answers for every
form. TunerToGo is a **paid, Wear OS–only** app: €1.49 one-time, no ads, no in-app purchases.

> **Paid is a one-way decision at the start.** Play never lets a free app become paid; a paid
> app can be made free later. So the app must be created as *Paid* from the beginning.

## 0. Before you start

- [ ] **Payments profile** linked to the developer account (*Settings → Payments profile*).
      Needed to sell apps. If Dictate is already sold or has purchases, it is set up.
- [ ] **Repository pushed**, so the privacy policy URL below works.
- [ ] **Signed bundle:** `./gradlew :app:bundleRelease` →
      `app/build/outputs/bundle/release/app-release.aab` (signed with the upload key from
      `keystore.properties`; the same upload key as Dictate).

## 1. Create the app

*Home → Create app*

| Field | Value |
| --- | --- |
| App name | `TunerToGo: Instrument Tuner` |
| Default language | English (United States) – en-US |
| App or game | App |
| Free or paid | **Paid** |
| Declarations | Developer Program Policies, US export laws: accept |

## 2. Store listing

*Grow users → Store presence → Main store listing*, then *Manage translations → German (de-DE)*.
All text and images are in `fastlane/metadata/android/<locale>/`:

| Field | Source |
| --- | --- |
| App name | `title.txt` (EN: *TunerToGo: Instrument Tuner*, DE: *TunerToGo: Stimmgerät*) |
| Short description | `short_description.txt` |
| Full description | `full_description.txt` (the `<b>` tags are supported by Play) |
| App icon | `images/icon.png` (512 × 512) |
| Feature graphic | `images/featureGraphic.png` (1024 × 500, localised) |
| Wear OS screenshots | `images/wearScreenshots/1–7.png` (454 × 454, unmasked, unframed) |

Phone screenshots: Play asks for at least two screenshots across the listing's device types.
The seven Wear OS screenshots should satisfy that; if the Console still insists on phone
screenshots for a Wear-only app, tell me and I'll make a proper set.

*Store settings:*

| Field | Value |
| --- | --- |
| App category | Music & Audio |
| Tags | the closest ones offered (tuner, music instruments) |
| Email | contact@devemperor.net |
| Website | https://github.com/DevEmperor/TunerToGo |
| External marketing | allowed |

## 3. Price

*Monetize with Play → Products → App pricing → Set price*

- Germany: **€1.49** (Play prices in the EU include VAT; Play collects and pays it).
- Other countries: let Play convert from the default price.
- For reference: €1.49 incl. 19 % VAT is €1.25 net. Google's service fee is 15 % once the
  account is enrolled in the 15 % service-fee tier (enrolment is not automatic), otherwise
  30 % → about **€1.06** (or €0.88) per sale in Germany.

## 4. App content

*Policy → App content*

| Section | Answer |
| --- | --- |
| Privacy policy | https://github.com/DevEmperor/TunerToGo/blob/master/PRIVACY_POLICY.md |
| App access | All functionality is available without special access (no login) |
| Ads | **No**, the app contains no ads |
| Content rating | Category *All other app types*; every question *No* (no violence, sexuality, profanity, drugs, gambling, user interaction, location sharing, digital purchases, web browsing). Expected: PEGI 3 / USK 0 / Everyone |
| Target audience | 13–15, 16–17, 18+ (avoids the Families programme requirements; the app is still fine for younger players) |
| News app | No |
| Data safety | *Does your app collect or share user data?* → **No.** Audio is analysed on the watch and discarded right away; data that is only accessed on the device and never sent off it does not count as collected. No account, so no deletion URL needed |
| Government app | No |
| Financial features | None |
| Health | No health features |

The microphone (`RECORD_AUDIO`) is a normal runtime permission; it needs no Permissions
Declaration form.

## 5. Closed test first

*Test and release → Testing → Closed testing → Create track/release*

1. Use **Play App Signing** with a Google-generated app signing key; the upload key is the
   one in `keystore.properties`.
2. Upload `app-release.aab`, release name `2.0.0`, release notes from
   `fastlane/metadata/android/<locale>/changelogs/20000.txt`.
3. Add testers (an email list with your own account at least).

Personal developer accounts created after 13 November 2023 must run a closed test with at
least 12 opted-in testers for 14 consecutive days before production access is granted
(organisation accounts are exempt). The Console shows on the dashboard whether this applies.

## 6. Opt in to Wear OS

*Test and release → Advanced settings → Form factors → Add form factor → Wear OS*

- Opt in and accept the Wear OS review policy, then roll out the release.
- Google reviews Wear OS apps against the [Wear OS app quality
  guidelines](https://developer.android.com/develop/adaptive-apps/quality-guidelines/wear-app-quality).
  The review status is shown under *Pricing and distribution → Wear OS* (pending, approved or
  not approved, with an email listing the problems).

What TunerToGo already covers for that review:

| Guideline | How |
| --- | --- |
| Target API (WO-P1), 64-bit | targetSdk 36; pure Kotlin, AndroidX native libs for all ABIs |
| Touch targets ≥ 48 dp (WO-V2) | buttons and list items are Material 3; string chips are display-only, strings are picked with the crown or a large tap area |
| Swipe to close (WO-V3) | every screen is swipe-dismissable |
| Scroll bar (WO-V8) | all lists use `ScreenScaffold` with a scroll indicator |
| Tile preview (WO-V10) | round and square previews, 384 × 384 |
| Black background (WO-V13) | app and tile |
| Font sizes (WO-V1, WO-V14) | menus scale with the system font; essential dial text ≥ 12 sp, the rest ≥ 10 sp |
| Splash (WO-V15) | launcher icon centred on black |
| Round screens (WO-V16) | tested on 192, 227 dp round and 180 dp square emulators |
| Listing (WO-G2, WO-G5) | localised EN/DE, mentions Wear OS and the tile; screenshots incl. tile |

## 7. Production

After the closed test (and Wear OS approval), promote the release to *Production*, choose
all countries, and send it for review.

## Later releases

1. Bump `appVersionName` in `app/build.gradle.kts` (versionCode follows automatically).
2. Add `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` and a CHANGELOG entry.
3. `./gradlew :app:testDebugUnitTest :app:lintRelease :app:bundleRelease`.
4. If the UI changed: `tools/capture-screenshots.sh` and `tools/render-store-assets.sh`.
