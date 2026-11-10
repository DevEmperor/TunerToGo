# Changelog

## 2.0.1 – 2026-11-10

Fixes for the Wear OS quality review on Google Play.

- The start screen shows the full round app icon at 48 × 48 dp on black.
- Lists keep their first and last entry inside the round display: the last menu entry no
  longer ends under the curved bottom edge.
- Screen titles, the reference tone frequency and button labels fit on small watches with a
  large font setting; long words are hyphenated instead of broken at random letters.

## 2.0.0 – 2026-10-26

Rebuilt from scratch in Kotlin and Jetpack Compose for Wear OS (Material 3).

- New pitch detection (YIN with FFT, parabolic interpolation, octave correction) from 25 Hz
  (B0 of a 5-string bass) to 4.5 kHz, with outlier rejection, smoothing and a short hold
  after the note stops.
- Instrument mode with automatic string detection, tuned-string ticks and a string lock on
  the crown (or a tap on the dial):
  guitar (10 tunings), 7-string guitar, bass (4, 5, 6 strings), ukulele, violin, viola,
  cello, double bass, mandolin and banjo. Chromatic mode for everything else.
- Gauge and strobe displays, colour that follows the deviation, vibration when in tune.
- Reference pitch from 400 to 480 Hz, transposition for B♭, E♭ and F instruments.
- English, German (H) and solfège note names; sharps or flats.
- Reference tone on watches with a speaker.
- Tile for starting the tuner with one tap.
- English and German user interface; menus follow the system font size, small text on the
  dial grows with it.
- One-time purchase on Google Play: no ads, no in-app purchases, no tracking.

## 1.0 – 2022-12-10

First simple version (Java, TarsosDSP).
