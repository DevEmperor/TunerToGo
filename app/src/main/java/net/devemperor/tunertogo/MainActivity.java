package net.devemperor.tunertogo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchProcessor;

public class MainActivity extends Activity {

    private static final List<Float> frequencies = Arrays.asList(16.3516f, 17.32391f, 18.35405f, 19.44544f, 20.60172f,
            21.82676f, 23.12465f, 24.49971f, 25.95654f, 27.5f, 29.13524f, 30.86771f, 32.7032f, 34.64783f, 36.7081f,
            38.89087f, 41.20344f, 43.65353f, 46.2493f, 48.99943f, 51.91309f, 55.0f, 58.27047f, 61.73541f, 65.40639f,
            69.29566f, 73.41619f, 77.78175f, 82.40689f, 87.30706f, 92.49861f, 97.99886f, 103.8262f, 110.0f, 116.5409f,
            123.4708f, 130.8128f, 138.5913f, 146.8324f, 155.5635f, 164.8138f, 174.6141f, 184.9972f, 195.9977f, 207.6523f,
            220.0f, 233.0819f, 246.9417f, 261.6256f, 277.1826f, 293.6648f, 311.127f, 329.6276f, 349.2282f, 369.9944f,
            391.9954f, 415.3047f, 440.0f, 466.1638f, 493.8833f, 523.2511f, 554.3653f, 587.3295f, 622.254f, 659.2551f,
            698.4565f, 739.9888f, 783.9909f, 830.6094f, 880.0f, 932.3275f, 987.7666f, 1046.502f, 1108.731f, 1174.659f,
            1244.508f, 1318.51f, 1396.913f, 1479.978f, 1567.982f, 1661.219f, 1760.0f, 1864.655f, 1975.533f, 2093.005f,
            2217.461f, 2349.318f, 2489.016f, 2637.02f, 2793.826f, 2959.955f, 3135.963f, 3322.438f, 3520.0f, 3729.31f,
            3951.066f, 4186.009f, 4434.922f, 4698.636f, 4978.032f, 5274.041f, 5587.652f, 5919.911f, 6271.927f, 6644.875f,
            7040.0f, 7458.62f, 7902.133f);
    private static final String[] notes = new String[] {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 1337);
        } else {
            startPitchDetection();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1337) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                TextView tvPitch = findViewById(R.id.tvPitch);
                tvPitch.setTextSize(16);
                tvPitch.setText(R.string.ttg_no_permission);
            } else {
                startPitchDetection();
            }
        }
    }

    void startPitchDetection() {
        UnclickableSeekBar sbFlat = findViewById(R.id.sbFlat);
        UnclickableSeekBar sbSharp = findViewById(R.id.sbSharp);

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(44100,4096,3072);

        PitchDetectionHandler pdh = (result, event) -> {
            final float pitchInHz = result.getPitch();
            final float probability = result.getProbability();
            final float targetFrequency = frequencies.stream().min(Comparator.comparingDouble(i -> Math.abs(i - pitchInHz))).orElse(0f);
            final int targetIndex = frequencies.indexOf(targetFrequency);
            final String note = notes[targetIndex % 12];

            runOnUiThread(() -> {
                TextView tvNote = findViewById(R.id.tvNote);
                TextView tvPitch = findViewById(R.id.tvPitch);
                TextView tvTarget = findViewById(R.id.tvTarget);

                if (pitchInHz == -1 || probability < 0.9) {
                    sbFlat.setProgress(0, true);
                    sbSharp.setProgress(0, true);
                    tvNote.setText("---");
                    tvPitch.setText("---");
                    tvTarget.setText("---");
                } else {
                    tvPitch.setText(String.format(Locale.ENGLISH, "%.2f Hz", pitchInHz));
                    tvTarget.setText(String.format(Locale.ENGLISH, "%.2f Hz", targetFrequency));
                    tvNote.setText(note);

                    if (pitchInHz > targetFrequency) {
                        sbSharp.setProgress((int) ((pitchInHz - targetFrequency) / ((frequencies.get(targetIndex + 1) - targetFrequency) / 2) * 1000), true);
                        sbFlat.setProgress(0, true);
                    } else {
                        sbSharp.setProgress(0, true);
                        sbFlat.setProgress((int) ((targetFrequency - pitchInHz) / ((targetFrequency - frequencies.get(targetIndex - 1)) / 2) * 1000), true);
                    }
                }
            });
        };
        AudioProcessor ap = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 44100, 4096, pdh);
        dispatcher.addAudioProcessor(ap);
        new Thread(dispatcher,"Audio Dispatcher").start();
    }
}