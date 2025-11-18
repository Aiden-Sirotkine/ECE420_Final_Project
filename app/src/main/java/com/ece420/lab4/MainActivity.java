/*
 * Copyright 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ece420.lab4;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
//import android.support.annotation.NonNull;
//import android.support.v4.app.ActivityCompat;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

//  ADDED STUFF
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;


import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import android.media.AudioTrack;


public class MainActivity extends Activity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    // UI Variables
    Button   controlButton;
    TextView statusView;
    static TextView freq_view;
    String  nativeSampleRate;
    String  nativeSampleBufSize;
    boolean supportRecording;
    Boolean isPlaying = false;
    // Static Values
    private static final int AUDIO_ECHO_REQUEST = 0;
    private static final int FRAME_SIZE = 1024;


//    -------------------------------------------------------
    Button somethingButton;
    Button resampleButton;
    Button repetButton;
    Button repetVocalButton;
    Button repetInstrumentalButton;
    Button pitchUpButton;
    Button cropButton;
    Button mixButton;
    String thing = "something";
    private MediaPlayer mediaPlayer;
    public static native boolean createSpeedAudioPlayer(String filePath, float speed);
    public static native void playSpeedAudio();
    public static native void stopSpeedAudio();
    public static native void deleteSpeedAudioPlayer();
    private boolean isSpeedPlaying = false;

    // REPET variables to store separated audio
    private short[] vocalSamples = null;
    private short[] instrumentalSamples = null;
    private int repetSampleRate = 44100; // Store the sample rate for playback
    private AudioTrack currentAudioTrack = null; // Track currently playing audio

//    --------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        super.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Google NDK Stuff
        controlButton = (Button)findViewById((R.id.capture_control_button));
        statusView = (TextView)findViewById(R.id.statusView);
        somethingButton = (Button)findViewById((R.id.do_something_button));
//        resampleButton = (Button)findViewById((R.id.resample_button));


        // ADDED CODE
        // Initialize MediaPlayer with your audio file
        // mediaPlayer = MediaPlayer.create(this, R.raw.audio_file);
        initializeMediaPlayer();
        resampleButton = (Button) findViewById(R.id.resample_button);
        resampleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSpeedUpClick(v);
            }
        });

        repetButton = (Button) findViewById(R.id.repet);
        repetVocalButton = (Button) findViewById(R.id.repet_vocal);
        repetInstrumentalButton = (Button) findViewById(R.id.repet_instrumental_button);
        pitchUpButton = (Button) findViewById(R.id.pitch_up_button);
        cropButton = (Button) findViewById(R.id.crop_button);
        mixButton = (Button) findViewById(R.id.mix_button);

        pitchUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WSOLAClick(v);
            }
        });

        cropButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cropClick(v);
            }
        });

        mixButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mixClick(v);
            }
        });



//        ---------------------------------------------
        

        queryNativeAudioParameters();
        // initialize native audio system
        updateNativeAudioUI();
        if (supportRecording) {
            createSLEngine(Integer.parseInt(nativeSampleRate), FRAME_SIZE);
        }

        // Setup UI
        freq_view = (TextView)findViewById(R.id.textFrequency);
        initializeFreqTextBackgroundTask(100);
    }
    @Override
    protected void onDestroy() {
        // Clean up REPET audio
        stopCurrentAudio();

        // Clean up speed audio player
        if (isSpeedPlaying) {
            stopSpeedAudio();
            deleteSpeedAudioPlayer();
        }

        // Clean up MediaPlayer
//        ADDED
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Your existing cleanup
        if (supportRecording) {
            if (isPlaying) {
                stopPlay();
            }
            deleteSLEngine();
            isPlaying = false;
        }

// -------------------------------------------
        if (supportRecording) {
            if (isPlaying) {
                stopPlay();
            }
            deleteSLEngine();
            isPlaying = false;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startEcho() {
        if(!supportRecording){
            return;
        }
        if (!isPlaying) {
            if(!createSLBufferQueueAudioPlayer()) {
                statusView.setText(getString(R.string.error_player));
                return;
            }
            if(!createAudioRecorder()) {
                deleteSLBufferQueueAudioPlayer();
                statusView.setText(getString(R.string.error_recorder));
                return;
            }
            startPlay();   // this must include startRecording()
            statusView.setText(getString(R.string.status_echoing));
        } else {
            stopPlay();  //this must include stopRecording()
            updateNativeAudioUI();
            deleteAudioRecorder();
            deleteSLBufferQueueAudioPlayer();
        }
        isPlaying = !isPlaying;
        controlButton.setText(getString((isPlaying == true) ?
                R.string.StopEcho: R.string.StartEcho));
    }

    public void onEchoClick(View view) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
            statusView.setText(getString(R.string.status_record_perm));
            ActivityCompat.requestPermissions(
                    this,
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    AUDIO_ECHO_REQUEST);
            return;
        }
        startEcho();
    }



    //    -----------------------------------------------------
    public void doSomethingClick(View view) {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.seekTo(0); // Restart from beginning if already playing
            }
        mediaPlayer.start();
    }

        if (thing.equals("something")) {
            thing = "nothing";
        } else {
            thing = "something";
        }
        somethingButton.setText(thing);
        return;
    }

    public void resampleClick(View view) {

        if (thing.equals("resample")) {
            thing = "resample2";
        } else {
            thing = "resample";
        }
        resampleButton.setText(thing);
        return;
    }



    public void onSpeedUpClick(View view) {
        try {
            // 1. Load WAV file from raw resources
            InputStream inputStream = getResources().openRawResource(R.raw.audio_file);
            byte[] wavData = new byte[inputStream.available()];
            inputStream.read(wavData);
            inputStream.close();

            // 2. Parse WAV header and extract PCM data (assume 44-byte header)
            int sampleRate = 44100;
            int headerSize = 44;
            if (wavData.length >= 28) { // Ensure header is large enough
                // Sample rate is at bytes 24-27 (little-endian)
                sampleRate = ((wavData[27] & 0xFF) << 24) |
                        ((wavData[26] & 0xFF) << 16) |
                        ((wavData[25] & 0xFF) << 8) |
                        ((wavData[24] & 0xFF));
            }


            int pcmDataSize = wavData.length - headerSize;
            byte[] pcmData = new byte[pcmDataSize];
            System.arraycopy(wavData, headerSize, pcmData, 0, pcmDataSize);

            // 3. Resample PCM data (e.g., 2x speed: skip every other sample)
            // Assuming 16-bit PCM, mono
            ByteBuffer pcmBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
            int numSamples = pcmDataSize / 2; // times 2
            short[] originalSamples = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                originalSamples[i] = pcmBuffer.getShort();
            }
            // Simple 2x speed: take every other sample
            double rate = 1.2;
            rate *= 2;
            short[] resampledSamples = new short[(int) (numSamples / rate)];
            for (int i = 0; i < resampledSamples.length; i+=1) {
                resampledSamples[i] = originalSamples[(int) (i* rate)];
            }

            // 4. Play resampled PCM data using AudioTrack
            sampleRate = (int) (sampleRate); // or parse from WAV header
            AudioTrack audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    resampledSamples.length * 2,
                    AudioTrack.MODE_STATIC
            );

            ByteBuffer outBuffer = ByteBuffer.allocate(resampledSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : resampledSamples) outBuffer.putShort(s);
            audioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
            audioTrack.play();

        } catch (Exception e) {
            statusView.setText("Resample/play error: " + e.getMessage());
        }
    }

    public void REPETClick(View view) {
        statusView.setText("Processing REPET...");

        // Run REPET processing in background thread to avoid ANR
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Load WAV file from raw resources
                    InputStream inputStream = getResources().openRawResource(R.raw.audio_file);
                    byte[] wavData = new byte[inputStream.available()];
                    inputStream.read(wavData);
                    inputStream.close();

                    // Parse WAV header properly to get sample rate
                    ByteBuffer headerBuffer = ByteBuffer.wrap(wavData).order(ByteOrder.LITTLE_ENDIAN);

                    // Skip RIFF header (4 bytes)
                    headerBuffer.position(24); // Jump to sample rate position
                    final int actualSampleRate = headerBuffer.getInt();

                    // Parse WAV header and extract PCM data (assume 44-byte header)
                    int headerSize = 44;
                    int pcmDataSize = wavData.length - headerSize;
                    byte[] pcmData = new byte[pcmDataSize];
                    System.arraycopy(wavData, headerSize, pcmData, 0, pcmDataSize);

                    // Convert PCM bytes to short samples (16-bit PCM, mono)
                    ByteBuffer pcmBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
                    int numSamples = pcmDataSize / 2;
                    short[] samples = new short[numSamples];
                    for (int i = 0; i < numSamples; i++) {
                        samples[i] = pcmBuffer.getShort();
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Sample rate: " + actualSampleRate + " Hz");
                        }
                    });

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Computing STFT with Kiss FFT...");
                        }
                    });

                    // REPET Algorithm Parameters
                    int windowSize = 2048;  // FFT window size
                    int hopSize = windowSize / 4;  // 75% overlap (25% hop)
                    int numFrames = (numSamples - windowSize) / hopSize + 1;
                    int numBins = windowSize / 2 + 1;  // FFT bins

                    // Compute STFT (magnitude and phase)
                    float[][] magnitude = new float[numFrames][numBins];
                    float[][] phase = new float[numFrames][numBins];

                    for (int frame = 0; frame < numFrames; frame++) {
                        int startIdx = frame * hopSize;

                        // Apply Hanning window
                        float[] windowedSignal = new float[windowSize];
                        for (int i = 0; i < windowSize; i++) {
                            float window = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / (windowSize - 1))));
                            windowedSignal[i] = samples[startIdx + i] * window;
                        }

                        // Compute FFT using Kiss FFT
                        float[] mag = new float[numBins];
                        float[] ph = new float[numBins];
                        computeFFT(windowedSignal, mag, ph, windowSize);

                        magnitude[frame] = mag;
                        phase[frame] = ph;

                        // Progress update every 100 frames
                        if (frame % 100 == 0) {
                            final int progress = (frame * 100) / numFrames;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusView.setText("STFT: " + progress + "%");
                                }
                            });
                        }
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Detecting period from beat spectrogram...");
                        }
                    });

                    // Compute beat spectrogram (sum of magnitude across frequencies for each frame)
                    float[] beatSpectrum = new float[numFrames];
                    for (int frame = 0; frame < numFrames; frame++) {
                        float sum = 0;
                        for (int k = 0; k < numBins; k++) {
                            sum += magnitude[frame][k];
                        }
                        beatSpectrum[frame] = sum;
                    }

                    // Normalize beat spectrum
                    float maxBeat = 0;
                    for (int frame = 0; frame < numFrames; frame++) {
                        if (beatSpectrum[frame] > maxBeat) {
                            maxBeat = beatSpectrum[frame];
                        }
                    }
                    if (maxBeat > 0) {
                        for (int frame = 0; frame < numFrames; frame++) {
                            beatSpectrum[frame] /= maxBeat;
                        }
                    }

                    // Compute autocorrelation of beat spectrum to find period
                    int minPeriodFrames = (int) ((1.0 * actualSampleRate) / hopSize);  // 1 second min
                    int maxPeriodFrames = (int) ((10.0 * actualSampleRate) / hopSize); // 10 seconds max
                    maxPeriodFrames = Math.min(maxPeriodFrames, numFrames / 2);
                    minPeriodFrames = Math.max(minPeriodFrames, 1);

                    float[] autocorr = new float[maxPeriodFrames + 1];
                    for (int lag = minPeriodFrames; lag <= maxPeriodFrames; lag++) {
                        float sum = 0;
                        int count = 0;
                        for (int i = 0; i < numFrames - lag; i++) {
                            sum += beatSpectrum[i] * beatSpectrum[i + lag];
                            count++;
                        }
                        autocorr[lag] = count > 0 ? sum / count : 0;
                    }

                    // Find peak in autocorrelation (highest peak is the period)
                    int periodInFrames = minPeriodFrames;
                    float maxAutocorr = autocorr[minPeriodFrames];
                    for (int lag = minPeriodFrames; lag <= maxPeriodFrames; lag++) {
                        if (autocorr[lag] > maxAutocorr) {
                            maxAutocorr = autocorr[lag];
                            periodInFrames = lag;
                        }
                    }

                    final int detectedPeriodFrames = periodInFrames;
                    final float detectedPeriodSeconds = (float) (periodInFrames * hopSize) / actualSampleRate;
                    final int numReps = numFrames / periodInFrames;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Period: " + String.format("%.2f", detectedPeriodSeconds) + "s, " + numReps + " reps");
                        }
                    });

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Building repeating model...");
                        }
                    });

                    // Compute repeating model using frame-by-frame MINIMUM
                    // Minimum captures the instrumental baseline better than median
                    // because vocals add energy on top of the baseline
                    float[][] repeatingModel = new float[numFrames][numBins];

                    for (int frame = 0; frame < numFrames; frame++) {
                        for (int k = 0; k < numBins; k++) {
                            // Collect magnitude values at this position in the period across all repetitions
                            int frameInPeriod = frame % periodInFrames;

                            // Find minimum magnitude across all repetitions at this position
                            // This captures the baseline instrumental that's always present
                            float minValue = Float.MAX_VALUE;
                            for (int p = frameInPeriod; p < numFrames; p += periodInFrames) {
                                if (magnitude[p][k] < minValue) {
                                    minValue = magnitude[p][k];
                                }
                            }

                            repeatingModel[frame][k] = minValue;
                        }

                        if (frame % 100 == 0) {
                            final int progress = (frame * 100) / numFrames;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusView.setText("Model: " + progress + "%");
                                }
                            });
                        }
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Creating masks...");
                        }
                    });

                    // Create soft time-frequency masks using ratio formula (matches Python reference)
                    float[][] backgroundMask = new float[numFrames][numBins];
                    float[][] foregroundMask = new float[numFrames][numBins];
                    float eps = 1e-10f;

                    // Debug: sample values
                    float sumBgMask = 0;
                    float sumFgMask = 0;
                    int sampleCount = 0;

                    for (int frame = 0; frame < numFrames; frame++) {
                        for (int k = 0; k < numBins; k++) {
                            float original = magnitude[frame][k];
                            float repeating = repeatingModel[frame][k];

                            // Use Wiener-like filtering with aggressive separation
                            // The residual (non-repeating part) is the difference
                            float residual = Math.max(original - repeating, 0.0f);

                            // Use higher power for more aggressive separation
                            // This emphasizes differences between repeating and non-repeating
                            float power = 2.0f;
                            float repeatingPow = (float) Math.pow(repeating + eps, power);
                            float residualPow = (float) Math.pow(residual + eps, power);

                            // Compute masks with aggressive Wiener filter
                            float denominator = repeatingPow + residualPow + eps;
                            backgroundMask[frame][k] = repeatingPow / denominator;
                            foregroundMask[frame][k] = residualPow / denominator;

                            // Debug: accumulate for average
                            if (frame % 10 == 0 && k < 100) {
                                sumBgMask += backgroundMask[frame][k];
                                sumFgMask += foregroundMask[frame][k];
                                sampleCount++;
                            }
                        }
                    }

                    final float avgBgMask = sumBgMask / sampleCount;
                    final float avgFgMask = sumFgMask / sampleCount;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Avg masks - BG: " + String.format("%.3f", avgBgMask) + ", FG: " + String.format("%.3f", avgFgMask));
                        }
                    });

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Reconstructing audio...");
                        }
                    });

                    // Initialize output arrays (use float for better precision during overlap-add)
                    float[] vocalFloat = new float[numSamples];
                    float[] instrumentalFloat = new float[numSamples];
                    float[] windowSum = new float[numSamples];
                    java.util.Arrays.fill(vocalFloat, 0.0f);
                    java.util.Arrays.fill(instrumentalFloat, 0.0f);
                    java.util.Arrays.fill(windowSum, 0.0f);

                    // Inverse STFT with overlap-add
                    for (int frame = 0; frame < numFrames; frame++) {
                        int startIdx = frame * hopSize;

                        // Apply masks
                        float[] vocalMag = new float[numBins];
                        float[] instrMag = new float[numBins];
                        for (int k = 0; k < numBins; k++) {
                            vocalMag[k] = magnitude[frame][k] * foregroundMask[frame][k];
                            instrMag[k] = magnitude[frame][k] * backgroundMask[frame][k];
                        }

                        // Compute inverse FFT using Kiss FFT
                        float[] vocalFrame = new float[windowSize];
                        float[] instrumentalFrame = new float[windowSize];
                        computeIFFT(vocalMag, phase[frame], vocalFrame, windowSize);
                        computeIFFT(instrMag, phase[frame], instrumentalFrame, windowSize);

                        // Apply synthesis window and overlap-add
                        for (int i = 0; i < windowSize && (startIdx + i) < numSamples; i++) {
                            // Use Hanning window for synthesis (same as analysis)
                            float window = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / (windowSize - 1))));

                            vocalFloat[startIdx + i] += vocalFrame[i] * window;
                            instrumentalFloat[startIdx + i] += instrumentalFrame[i] * window;
                            windowSum[startIdx + i] += window * window; // Track window energy for normalization
                        }

                        if (frame % 100 == 0) {
                            final int progress = (frame * 100) / numFrames;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusView.setText("ISTFT: " + progress + "%");
                                }
                            });
                        }
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Normalizing and resampling...");
                        }
                    });

                    // Normalize by window sum
                    for (int i = 0; i < numSamples; i++) {
                        if (windowSum[i] > 1e-8f) {
                            vocalFloat[i] /= windowSum[i];
                            instrumentalFloat[i] /= windowSum[i];
                        }
                    }

                    // Resample to 2x speed (similar to onSpeedUpClick)
                    // This compensates for any slowdown and improves quality
                    int resampleRate = 2;
                    int resampledLength = numSamples / resampleRate;

                    vocalSamples = new short[resampledLength];
                    instrumentalSamples = new short[resampledLength];

                    for (int i = 0; i < resampledLength; i++) {
                        int srcIdx = i * resampleRate;
                        // Convert to short with clipping
                        vocalSamples[i] = (short) Math.max(-32768, Math.min(32767, vocalFloat[srcIdx]));
                        instrumentalSamples[i] = (short) Math.max(-32768, Math.min(32767, instrumentalFloat[srcIdx]));
                    }

                    // Store the sample rate for playback (maintain original rate since we resampled)
                    repetSampleRate = actualSampleRate;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("REPET processing complete!");
                        }
                    });

                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("REPET error: " + errorMsg);
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // Helper method to stop any currently playing audio
    private void stopCurrentAudio() {
        if (currentAudioTrack != null) {
            try {
                if (currentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    currentAudioTrack.stop();
                }
                currentAudioTrack.release();
            } catch (Exception e) {
                // Ignore errors during cleanup
            }
            currentAudioTrack = null;
        }
    }

    public void REPETVocalClick(View view) {
        if (vocalSamples == null) {
            statusView.setText("Please run REPET first!");
            return;
        }

        // Stop any currently playing audio
        stopCurrentAudio();

        try {
            // Play vocal (foreground) PCM data using AudioTrack with correct sample rate
            currentAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    repetSampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    vocalSamples.length * 2,
                    AudioTrack.MODE_STATIC
            );

            // Convert short array to byte array for AudioTrack
            ByteBuffer outBuffer = ByteBuffer.allocate(vocalSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : vocalSamples) {
                outBuffer.putShort(s);
            }

            currentAudioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
            currentAudioTrack.play();
            statusView.setText("Playing vocal track at " + repetSampleRate + " Hz...");
        } catch (Exception e) {
            statusView.setText("Vocal playback error: " + e.getMessage());
        }
    }

    public void REPETInstrumentalClick(View view) {
        if (instrumentalSamples == null) {
            statusView.setText("Please run REPET first!");
            return;
        }

        // Stop any currently playing audio
        stopCurrentAudio();

        try {
            // Play instrumental (background) PCM data using AudioTrack with correct sample rate
            currentAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    repetSampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    instrumentalSamples.length * 2,
                    AudioTrack.MODE_STATIC
            );

            // Convert short array to byte array for AudioTrack
            ByteBuffer outBuffer = ByteBuffer.allocate(instrumentalSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : instrumentalSamples) {
                outBuffer.putShort(s);
            }

            currentAudioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
            currentAudioTrack.play();
            statusView.setText("Playing instrumental track at " + repetSampleRate + " Hz...");
        } catch (Exception e) {
            statusView.setText("Instrumental playback error: " + e.getMessage());
        }
    }

    public void pitchUpClick(View view) {
        // TODO: Implement pitch up functionality
        statusView.setText("Pitch up clicked");
    }


    public void WSOLAClick(View view) {
        try {
            // 1. Load WAV file from raw resources
            InputStream inputStream = getResources().openRawResource(R.raw.audio_file);
            byte[] wavData = new byte[inputStream.available()];
            inputStream.read(wavData);
            inputStream.close();

            // 2. Parse WAV header and extract PCM data (assume 44-byte header)
            int headerSize = 44;
            if (wavData.length >= 28) { // Ensure header is large enough
                // Sample rate is at bytes 24-27 (little-endian)
                int sampleRate = ((wavData[27] & 0xFF) << 24) |
                        ((wavData[26] & 0xFF) << 16) |
                        ((wavData[25] & 0xFF) << 8) |
                        ((wavData[24] & 0xFF));
            }

            int pcmDataSize = wavData.length - headerSize;
            byte[] pcmData = new byte[pcmDataSize];
            System.arraycopy(wavData, headerSize, pcmData, 0, pcmDataSize);

            // 3. Convert to short array
            ByteBuffer pcmBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
            int numSamples = pcmDataSize / 2;
            short[] originalSamples = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                originalSamples[i] = pcmBuffer.getShort();
            }

            // 4. WSOLA pitch shift up by 100% (one octave)
            double pitch_shift = 1.5;
            double rate = pitch_shift*2;

            int frameSize = 1024;
            int analysisHop = frameSize / 2; // input hop size
            int synthesisHop = (int) (analysisHop * pitch_shift); // output hop size

            int outputLength = (int) ((numSamples - frameSize) / analysisHop) * synthesisHop + frameSize;
            if (outputLength <= 0) outputLength = frameSize;
            short[] outputSamples = new short[outputLength];

            int outPos = 0;
            for (int inPos = 0; inPos + frameSize < numSamples && outPos + frameSize < outputLength; inPos += analysisHop) {
                // Find best overlap position using cross-correlation
                int bestOffset = 0;
                double maxCorr = Double.NEGATIVE_INFINITY;
                int searchRange = analysisHop / 2;
                for (int offset = -searchRange; offset <= searchRange; offset++) {
                    int refStart = inPos;
                    int cmpStart = inPos + analysisHop + offset;
                    if (cmpStart < 0 || cmpStart + frameSize > numSamples) continue;
                    double corr = 0;
                    for (int j = 0; j < frameSize; j++) {
                        corr += originalSamples[refStart + j] * originalSamples[cmpStart + j];
                    }
                    if (corr > maxCorr) {
                        maxCorr = corr;
                        bestOffset = offset;
                    }
                }
                int bestStart = inPos + analysisHop + bestOffset;
                if (bestStart < 0 || bestStart + frameSize > numSamples) continue;

                // Overlap-add with bounds check and clamping
                for (int j = 0; j < frameSize; j++) {
                    int outIdx = outPos + j;
                    if (outIdx < outputSamples.length && bestStart + j < originalSamples.length) {
                        int sum = outputSamples[outIdx] + originalSamples[bestStart + j];
                        if (sum > Short.MAX_VALUE) sum = Short.MAX_VALUE;
                        if (sum < Short.MIN_VALUE) sum = Short.MIN_VALUE;
                        outputSamples[outIdx] = (short) sum;
                    }
                }
                outPos += synthesisHop;
            }

//            4.5 add resampling to get the speed back to the original
            // Simple 2x speed: take every other sample

            short[] resampledSamples = new short[(int) (numSamples / rate)];
            for (int i = 0; i < resampledSamples.length; i+=1) {
                resampledSamples[i] = outputSamples[(int) (i* rate)];
            }

            // 4. Play resampled PCM data using AudioTrack
            int sampleRate = 44100; // or parse from WAV header
            AudioTrack audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    resampledSamples.length * 2,
                    AudioTrack.MODE_STATIC
            );

            ByteBuffer outBuffer = ByteBuffer.allocate(resampledSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : resampledSamples) outBuffer.putShort(s);
            audioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
            audioTrack.play();



//            // 5. Play output using AudioTrack
//            int sampleRate = 44100;
//            AudioTrack audioTrack = new AudioTrack(
//                    AudioManager.STREAM_MUSIC,
//                    sampleRate,
//                    AudioFormat.CHANNEL_OUT_MONO,
//                    AudioFormat.ENCODING_PCM_16BIT,
//                    outputSamples.length * 2,
//                    AudioTrack.MODE_STATIC
//            );
//            ByteBuffer outBuffer = ByteBuffer.allocate(outputSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
//            for (short s : outputSamples) outBuffer.putShort(s);
//            audioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
//            audioTrack.play();
//
        } catch (Exception e) {
            statusView.setText(
            "Exception: " + e.getClass().getName() + "\n" +
            "Message: " + e.getMessage()
            );
        }
    }

    public void cropClick(View view) {
        try {
            // 1. Load WAV file from raw resources
            InputStream inputStream = getResources().openRawResource(R.raw.audio_file);
            byte[] wavData = new byte[inputStream.available()];
            inputStream.read(wavData);
            inputStream.close();

            // 2. Parse WAV header and extract PCM data (assume 44-byte header)
            int headerSize = 44;
            int sampleRate = 44100;
            if (wavData.length >= 28) { // Ensure header is large enough
                // Sample rate is at bytes 24-27 (little-endian)
                sampleRate = ((wavData[27] & 0xFF) << 24) |
                        ((wavData[26] & 0xFF) << 16) |
                        ((wavData[25] & 0xFF) << 8) |
                        ((wavData[24] & 0xFF));
            }


            int pcmDataSize = wavData.length - headerSize;
            byte[] pcmData = new byte[pcmDataSize];
            System.arraycopy(wavData, headerSize, pcmData, 0, pcmDataSize);

            // 3. Resample PCM data (e.g., 2x speed: skip every other sample)
            // Assuming 16-bit PCM, mono
            ByteBuffer pcmBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
            int numSamples = pcmDataSize / 2;
            short[] originalSamples = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                originalSamples[i] = pcmBuffer.getShort();
            }
            // Simple 2x speed: take every other sample
//            int rate = 3;
//            short[] resampledSamples = new short[numSamples / rate];
//            for (int i = 0; i < resampledSamples.length; i+=1) {
//                resampledSamples[i] = originalSamples[i* rate];
//            }

//            2. crop some random amount of stuff
            double crop_percent = 0.6;
            short[] resampledSamples = new short[(int) (numSamples * crop_percent)];
            for (int i = 0; i < resampledSamples.length; i+=1) {
                resampledSamples[i] = originalSamples[(int) (numSamples * (1-crop_percent)) + i];
            }
//            short[] resampledSamples = originalSamples;

            // 4. Play resampled PCM data using AudioTrack
            sampleRate *= 2; // or parse from WAV header
            AudioTrack audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    resampledSamples.length * 2, // resampleSampled.length * 2
                    AudioTrack.MODE_STATIC
            );

            ByteBuffer outBuffer = ByteBuffer.allocate(resampledSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : resampledSamples) outBuffer.putShort(s);
            audioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
            audioTrack.play();

        } catch (Exception e) {
            statusView.setText("Resample/play error: " + e.getMessage());
        }
    }

    public void mixClick(View view){
        if (instrumentalSamples == null) {
            statusView.setText("Please run REPET first!");
            return;
        }

        // Stop any currently playing audio
        stopCurrentAudio();

        try {

            // Convert short array to byte array for AudioTrack
//            ByteBuffer insBuffer = ByteBuffer.allocate(instrumentalSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
//            for (short s : instrumentalSamples) {
//                insBuffer.putShort(s);
//            }
//
//            // Convert short array to byte array for AudioTrack
//            ByteBuffer vocalBuffer = ByteBuffer.allocate(vocalSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
////            for (short s : vocalSamples) {
//            for (int i = 0; i < vocalSamples.length; i++) {
//                vocalBuffer.putShort(vocalSamples[vocalSamples.length - i - 1]);
//            }

//         mixmixmixmixmixmixmixmixmixmixmixmixmixmixmixmixmixmixi
            // Ensure both arrays are the same length - use the shorter one as limit
            int minLength = Math.min(instrumentalSamples.length, vocalSamples.length);
            short[] mixedSamples = new short[minLength];

            // Mix the samples
            for (int i = 0; i < minLength; i++) {
                // Convert to int to prevent overflow during addition
                int mixed = instrumentalSamples[i] + vocalSamples[minLength - i - 1];

                // Prevent clipping - clamp to short range
                if (mixed > Short.MAX_VALUE) {
                    mixed = Short.MAX_VALUE;
                } else if (mixed < Short.MIN_VALUE) {
                    mixed = Short.MIN_VALUE;
                }

                mixedSamples[i] = (short) mixed;
            }

            // Convert mixed short array to byte array for AudioTrack
            ByteBuffer outBuffer = ByteBuffer.allocate(mixedSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : mixedSamples) {
                outBuffer.putShort(s);
            }


            // Play instrumental (background) PCM data using AudioTrack with correct sample rate
            currentAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    repetSampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    instrumentalSamples.length * 2,
                    AudioTrack.MODE_STATIC
            );

            currentAudioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
            currentAudioTrack.play();
            statusView.setText("Playing instrumental track at " + repetSampleRate + " Hz...");
        } catch (Exception e) {
            statusView.setText("Instrumental playback error: " + e.getMessage());
        }
    }
//    -------------------------------------------------------------

    public void getLowLatencyParameters(View view) {
        updateNativeAudioUI();
        return;
    }

    private void queryNativeAudioParameters() {
        AudioManager myAudioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        nativeSampleRate  =  myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        nativeSampleBufSize =myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        int recBufSize = AudioRecord.getMinBufferSize(
                Integer.parseInt(nativeSampleRate),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        supportRecording = true;
        if (recBufSize == AudioRecord.ERROR ||
                recBufSize == AudioRecord.ERROR_BAD_VALUE) {
            supportRecording = false;
        }
    }
    private void updateNativeAudioUI() {
        if (!supportRecording) {
            statusView.setText(getString(R.string.error_no_mic));
            controlButton.setEnabled(false);
            return;
        }

        statusView.setText("nativeSampleRate    = " + nativeSampleRate + "\n" +
                "nativeSampleBufSize = " + nativeSampleBufSize + "\n");

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        /*
         * if any permission failed, the sample could not play
         */
        if (AUDIO_ECHO_REQUEST != requestCode) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 1  ||
                grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            /*
             * When user denied permission, throw a Toast to prompt that RECORD_AUDIO
             * is necessary; also display the status on UI
             * Then application goes back to the original state: it behaves as if the button
             * was not clicked. The assumption is that user will re-click the "start" button
             * (to retry), or shutdown the app in normal way.
             */
            statusView.setText(getString(R.string.error_no_permission));
            Toast.makeText(getApplicationContext(),
                    getString(R.string.prompt_permission),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        /*
         * When permissions are granted, we prompt the user the status. User would
         * re-try the "start" button to perform the normal operation. This saves us the extra
         * logic in code for async processing of the button listener.
         */
        statusView.setText("RECORD_AUDIO permission granted, touch " +
                getString(R.string.StartEcho) + " to begin");

        // The callback runs on app's thread, so we are safe to resume the action
        startEcho();
    }

    // All this does is calls the UpdateStftTask at a fixed interval
    // http://stackoverflow.com/questions/6531950/how-to-execute-async-task-repeatedly-after-fixed-time-intervals
    public void initializeFreqTextBackgroundTask(int timeInMs) {
        final Handler handler = new Handler();
        Timer timer = new Timer();
        TimerTask doAsynchronousTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        try {
                            UpdateFreqTextTask performFreqTextUpdate = new UpdateFreqTextTask();
                            performFreqTextUpdate.execute();
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                        }
                    }
                });
            }
        };
        timer.schedule(doAsynchronousTask, 0, timeInMs); // execute every 100 ms
    }


    private void initializeMediaPlayer() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.audio_file);
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    statusView.setText("Audio playback error");
                    return false;
                }
            });
        } catch (Exception e) {
            statusView.setText("Failed to initialize audio player");
        }
    }

    // UI update
    private class UpdateFreqTextTask extends AsyncTask<Void, Float, Void> {
        @Override
        protected Void doInBackground(Void... params) {

            // Update screen, needs to be done on UI thread
            publishProgress(getFreqUpdate());

            return null;
        }

        protected void onProgressUpdate(Float... newFreq) {
            if (newFreq[0] > 0) {
                freq_view.setText(Long.toString(newFreq[0].longValue()) + " Hz");
            } else {
                freq_view.setText("Unvoiced");
            }
        }
    }

    /*
     * Loading our Libs
     */
    static {
        System.loadLibrary("echo");
    }

    /*
     * jni function implementations...
     */
    public static native void createSLEngine(int rate, int framesPerBuf);
    public static native void deleteSLEngine();

    public static native boolean createSLBufferQueueAudioPlayer();
    public static native void deleteSLBufferQueueAudioPlayer();

    public static native boolean createAudioRecorder();
    public static native void deleteAudioRecorder();
    public static native void startPlay();
    public static native void stopPlay();

    public static native float getFreqUpdate();

    // REPET native FFT functions
    public static native void computeFFT(float[] input, float[] magnitude, float[] phase, int size);
    public static native void computeIFFT(float[] magnitude, float[] phase, float[] output, int size);
}
