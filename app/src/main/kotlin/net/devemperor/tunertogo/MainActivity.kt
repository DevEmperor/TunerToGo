package net.devemperor.tunertogo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import net.devemperor.tunertogo.audio.MicrophonePitchSource
import net.devemperor.tunertogo.audio.PitchSource
import net.devemperor.tunertogo.audio.SyntheticPitchSource
import net.devemperor.tunertogo.ui.TunerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val pitchSource = debugPitchSource() ?: MicrophonePitchSource(applicationContext)
        val routes = if (BuildConfig.DEBUG) {
            intent.getStringExtra(EXTRA_ROUTES)?.split(',')?.filter(String::isNotBlank).orEmpty()
        } else {
            emptyList()
        }
        setContent { TunerApp(pitchSource, routes) }
    }

    /**
     * Debug builds accept a synthetic input for emulator tests and store screenshots, e.g.
     * `adb shell am start -n net.devemperor.tunertogo.debug/net.devemperor.tunertogo.MainActivity
     * --ef demo_hz 110.6 --ef demo_drift 4 --es routes menu`.
     */
    private fun debugPitchSource(): PitchSource? {
        if (!BuildConfig.DEBUG) return null
        val frequency = intent.getFloatExtra(EXTRA_DEMO_HZ, 0f)
        if (frequency <= 0f) return null
        return SyntheticPitchSource(frequency.toDouble(), intent.getFloatExtra(EXTRA_DEMO_DRIFT, 0f).toDouble())
    }

    private companion object {
        const val EXTRA_DEMO_HZ = "demo_hz"
        const val EXTRA_DEMO_DRIFT = "demo_drift"
        const val EXTRA_ROUTES = "routes"
    }
}
