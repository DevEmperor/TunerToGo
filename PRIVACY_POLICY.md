# Privacy Policy for TunerToGo

**Effective date:** 26 October 2026
**Last updated:** 26 October 2026

This Privacy Policy explains how **TunerToGo** (the "App", application ID
`net.devemperor.tunertogo`), an instrument tuner for Wear OS watches, handles your
information. The App is developed and maintained by **Jannis Zahn**, trading as
DevEmperor (the "Developer", "we", "us"), Talstraße 84, 35625 Hüttenberg, Germany —
the data controller for the limited purposes described below.

If you have any questions about this policy, contact us at
**contact@devemperor.net**.

---

## 1. Summary (the short version)

- **We do not receive any of your data.** The App has no server, no account, no
  database about you and no analytics.
- **The App cannot send anything anywhere.** It does not even request the internet
  permission.
- **Sound from the microphone is analysed live on your watch** to measure the pitch
  of your instrument, and discarded right away. Nothing is recorded, stored or
  transmitted.
- Your settings (instrument, tuning, reference pitch, display options) are stored
  **only on your watch** — and in Android's own backup, if you use it (section 2.3).
- The App contains **no advertising, no tracking, no telemetry and no
  crash-reporting SDKs**.
- **The purchase is handled by Google Play**, which is the seller (section 3).
- **All of this can be checked**: the App is open source (section 6).

---

## 2. What information the App processes

### 2.1 Sound from the microphone

To tune, the App needs to hear your instrument. While the tuner screen is open and
the App is in the foreground, it reads the microphone signal and measures its
pitch, a few dozen times per second, on the watch itself. Each piece of sound
(about a tenth of a second) is overwritten by the next one as soon as it has been
analysed.

- The sound is **never written to storage**, never kept in a recording and never
  sent off the watch.
- The microphone is **switched off** within a second when you leave the tuner screen,
  open the menu, or the watch goes to sleep or switches to another app.
- The App only measures pitch and loudness. It does not recognise speech, and it
  does not analyse or keep anything else about the sound.

### 2.2 Settings

Your choice of instrument and tuning, the reference pitch, note names,
transposition, display style, in-tune range, sensitivity and the vibration and
screen options are stored in the App's private storage on the watch. They never
leave the watch, except through Android's own backup (section 2.3). The App's tile
reads the same settings to show your current tuning.

Which strings you have already tuned and which string you have locked are only
kept in memory while the App runs; they are not stored.

### 2.3 Android's own backup

If backup is turned on for your watch, Android may include the App's settings
(section 2.2) in your device backup with Google, so that they come back after a
reset or on a new watch. This backup is created, stored and protected by Google
under Google's privacy policy (https://policies.google.com/privacy); we have no
access to it. No sound is ever part of it. You can turn backup off in your watch's
or phone's system settings.

### 2.4 Vibration

When a note is in tune, the App makes the watch vibrate briefly. This uses the
vibration motor only; it processes no information. You can turn it off in the
App's settings.

---

## 3. Google Play

The App is sold through **Google Play**. Google is the seller and an independent
controller for the purchase and its payment; we never see your payment details,
your name or your email address. See https://policies.google.com/privacy.

As the seller's partner, we receive from Google:

- **Order and sales reports** — for example the order number, date, price and the
  buyer's country or region — which we need for accounting, tax and refunds. They do
  not contain your name, email address or payment details.
- **Aggregated statistics** about installs and ratings, and — only if you allow
  your devices to share usage and diagnostics data with Google — anonymous crash
  and performance reports (Android vitals), such as the device model, the Android
  version and where in the App an error occurred.

**Legal bases:** we process the order reports to fulfil the purchase contract,
including refunds (Art. 6(1)(b) GDPR), and to meet our accounting and tax
obligations (Art. 6(1)(c) GDPR); we keep them for as long as tax law requires. We
use the anonymous statistics and crash reports to fix errors and improve the App
(our legitimate interest, Art. 6(1)(f) GDPR).

---

## 4. Permissions and why they are used

| Permission | Purpose |
| --- | --- |
| `RECORD_AUDIO` (microphone) | To hear your instrument and measure its pitch, only while the tuner screen is open (section 2.1). |
| `VIBRATE` | The short vibration when a note is in tune (section 2.4). |

The App requests **no internet, location, contacts, storage, sensor or account
permissions**. The microphone permission is requested when you first open the
tuner, and you can revoke it at any time in the watch's settings.

---

## 5. Data storage and retention

- **On your watch:** your settings remain until you change them, clear the App's
  data or uninstall the App. Sound is never stored (section 2.1).
- **With the Developer:** nothing from the App itself. Order reports from Google
  Play are kept as long as tax law requires (section 3).
- **With Google:** your purchase, and your backup if you use it, under Google's
  privacy policy.

To erase all locally stored data, clear the App's storage in the watch's settings
or uninstall the App.

---

## 6. You do not have to take our word for any of this

The App is open source, under the Apache License 2.0:

**<https://github.com/DevEmperor/TunerToGo>**

You can read every line, including the parts this policy describes: that the App
has no internet permission (`app/src/main/AndroidManifest.xml`), how the
microphone is read and released (`app/src/main/kotlin/…/audio/`), and what is
stored as settings (`app/src/main/kotlin/…/settings/`). You are free to build the
App yourself rather than install ours.

One honest limit: published source is not proof of what is installed. The version
on Google Play is built from this repository, but nobody can verify that byte for
byte from outside — here or anywhere else. What you can always check on your own
watch is the permission list in the system settings, which shows that the App has
no internet access.

---

## 7. Data security

The App keeps its settings in its private, sandboxed storage, which on Android is
not accessible to other apps. Because the App has no internet permission, nothing
it processes can be transmitted by it. No method of storage is 100% secure.

---

## 8. Children's privacy

The App is suitable for all ages and is not specifically directed at children. As
it collects no personal data, it does not knowingly collect data from children
either.

---

## 9. International data transfers

The App transfers no data. Google may process purchase, backup and diagnostics
data in other countries, including the United States, under Google's privacy
policy.

---

## 10. Your choices and rights

- You can refuse or revoke the microphone permission at any time; the App then
  cannot tune, but does nothing else with your device either.
- You can turn off the vibration and change all settings in the App.
- You can clear the App's data or uninstall it to remove everything stored locally.
- Under the GDPR you have the rights of access, rectification, erasure,
  restriction, data portability and objection (Art. 15–21), and the right to
  complain to a supervisory authority. As we hold no data about you apart from
  the anonymous order reports described in section 3, there is usually nothing we
  could look up; for your purchase itself, please contact Google.

---

## 11. Changes to this policy

We may update this Privacy Policy from time to time. Material changes will be
reflected by updating the "Last updated" date above and publishing the new version
at this location. Continued use of the App after an update constitutes acceptance
of the revised policy.

---

## 12. Contact

If you have questions or requests regarding this Privacy Policy, contact:

**Jannis Zahn** (DevEmperor) — contact@devemperor.net
Talstraße 84, 35625 Hüttenberg, Germany

Project repository: https://github.com/DevEmperor/TunerToGo
