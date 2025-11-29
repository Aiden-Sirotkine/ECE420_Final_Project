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
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
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
    private static final int FILE_PICKER_REQUEST = 1;
    private static final int FRAME_SIZE = 1024;

    // File picker
    private Uri selectedAudioUri = null;

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

    // WSOLA caching variables
    private short[] wsolaSamples = null;
    private int wsolaSampleRate = 44100;

    // Speed resampling caching variables
    private short[] speedSamples = null;
    private int speedSampleRate = 44100;

    // Crop caching variables
    private short[] croppedSamples = null;
    private int croppedSampleRate = 44100;

    // Mix caching variables
    private short[] mixedSamples = null;

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
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST && resultCode == RESULT_OK) {
            if (data != null) {
                selectedAudioUri = data.getData();

                // Clear all cached results when new file is loaded
                clearAllCaches();

                // Show confirmation
                String fileName = selectedAudioUri.getLastPathSegment();
                statusView.setText("Loaded: " + fileName);
                Toast.makeText(this, "Audio file loaded: " + fileName, Toast.LENGTH_SHORT).show();
            }
        }
    }

// -------------------------------------------

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

    public void loadFileClick(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, FILE_PICKER_REQUEST);
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
        // Check if Speed result is already cached
        if (speedSamples != null) {
            statusView.setText("Playing cached Speed result...");
            stopCurrentAudio();
            try {
                currentAudioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        speedSampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        speedSamples.length * 2,
                        AudioTrack.MODE_STATIC
                );
                ByteBuffer outBuffer = ByteBuffer.allocate(speedSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (short s : speedSamples) {
                    outBuffer.putShort(s);
                }
                currentAudioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
                currentAudioTrack.play();
                statusView.setText("Playing cached Speed result");
            } catch (Exception e) {
                statusView.setText("Speed playback error: " + e.getMessage());
            }
            return;
        }

        statusView.setText("Processing Speed resampling...");
        try {
            // 1. Load WAV file from user-selected file or fallback to raw resources
            InputStream inputStream = loadAudioFromSource();
            byte[] wavData = new byte[inputStream.available()];
            inputStream.read(wavData);
            inputStream.close();

            // 2. Parse WAV header and extract PCM data
            int[] headerInfo = parseWavHeader(wavData);
            int sampleRate = headerInfo[0];
            int headerSize = headerInfo[1];

            // Check for parsing error
            if (sampleRate == -1 || headerSize == -1) {
                statusView.setText("Cannot process: Invalid audio format");
                return;
            }

            statusView.setText("Speed: Sample rate = " + sampleRate + " Hz");

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

            // Store Speed result in cache
            speedSamples = resampledSamples;
            speedSampleRate = sampleRate;

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
                    // Load WAV file from user-selected file or fallback to raw resources
                    InputStream inputStream = loadAudioFromSource();
                    byte[] wavData = new byte[inputStream.available()];
                    inputStream.read(wavData);
                    inputStream.close();

                    // Parse WAV header and extract PCM data
                    int[] headerInfo = parseWavHeader(wavData);
                    final int actualSampleRate = headerInfo[0];
                    int headerSize = headerInfo[1];

                    // Check for parsing error
                    if (actualSampleRate == -1 || headerSize == -1) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusView.setText("Cannot process: Invalid audio format");
                            }
                        });
                        return;
                    }

                    int pcmDataSize = wavData.length - headerSize;
                    byte[] pcmData = new byte[pcmDataSize];
                    System.arraycopy(wavData, headerSize, pcmData, 0, pcmDataSize);

                    // Convert PCM bytes to short samples (16-bit PCM, mono)
                    ByteBuffer pcmBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
                    int numSamples = pcmDataSize / 2;

                    // Limit audio length to prevent OutOfMemoryError
                    // Max 30 seconds at 48kHz = 1,440,000 samples
                    final int maxSamples = 1440000;
                    if (numSamples > maxSamples) {
                        final int durationSec = numSamples / actualSampleRate;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusView.setText("Audio too long (" + durationSec + "s). Max 30 seconds. Truncating...");
                            }
                        });
                        numSamples = maxSamples;
                    }

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

                    // OPTIMIZED: Precompute Hanning window once (5000x faster than recomputing)
                    float[] hanningWindow = new float[windowSize];
                    for (int i = 0; i < windowSize; i++) {
                        hanningWindow[i] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / (windowSize - 1))));
                    }

                    // Compute STFT (magnitude and phase)
                    float[][] magnitude = null;
                    float[][] phase = null;
                    try {
                        magnitude = new float[numFrames][numBins];
                        phase = new float[numFrames][numBins];
                    } catch (OutOfMemoryError e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusView.setText("Out of memory. Try shorter audio (max 30s).");
                            }
                        });
                        return;
                    }

                    for (int frame = 0; frame < numFrames; frame++) {
                        int startIdx = frame * hopSize;

                        // Apply precomputed Hanning window
                        float[] windowedSignal = new float[windowSize];
                        for (int i = 0; i < windowSize; i++) {
                            windowedSignal[i] = samples[startIdx + i] * hanningWindow[i];
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
                    final float maxAutocorrVal = maxAutocorr;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Period: " + String.format("%.2f", detectedPeriodSeconds) + "s, " + numReps + " reps, autocorr: " + String.format("%.3f", maxAutocorrVal));
                        }
                    });

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Building repeating model...");
                        }
                    });

                    // Compute repeating model using frame-by-frame MEDIAN
                    // Median captures the instrumental baseline better than minimum
                    // because it's robust to outliers (silence or very loud vocals)
                    // OPTIMIZED: Compute median once per period position, then tile
                    float[][] periodModel = new float[periodInFrames][numBins];

                    // Step 1: Compute median for one period
                    for (int frameInPeriod = 0; frameInPeriod < periodInFrames; frameInPeriod++) {
                        for (int k = 0; k < numBins; k++) {
                            // Collect all magnitude values across repetitions at this position
                            java.util.ArrayList<Float> values = new java.util.ArrayList<>();
                            for (int p = frameInPeriod; p < numFrames; p += periodInFrames) {
                                values.add(magnitude[p][k]);
                            }

                            // Find median
                            java.util.Collections.sort(values);
                            int medianIdx = values.size() / 2;
                            float medianValue = values.get(medianIdx);
                            periodModel[frameInPeriod][k] = medianValue;
                        }

                        if (frameInPeriod % 100 == 0) {
                            final int progress = (frameInPeriod * 50) / periodInFrames;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusView.setText("Model: " + progress + "%");
                                }
                            });
                        }
                    }

                    // Step 2: Tile the period model to all frames
                    float[][] repeatingModel = new float[numFrames][numBins];
                    float modelSum = 0;
                    int modelCount = 0;
                    for (int frame = 0; frame < numFrames; frame++) {
                        int frameInPeriod = frame % periodInFrames;
                        for (int k = 0; k < numBins; k++) {
                            repeatingModel[frame][k] = periodModel[frameInPeriod][k];
                            if (frame < 10 && k < 10) {
                                modelSum += periodModel[frameInPeriod][k];
                                modelCount++;
                            }
                        }

                        if (frame % 100 == 0) {
                            final int progress = 50 + (frame * 50) / numFrames;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusView.setText("Model: " + progress + "%");
                                }
                            });
                        }
                    }

                    final float avgModelVal = modelSum / modelCount;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Avg model value: " + String.format("%.2f", avgModelVal));
                        }
                    });

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
                            // OPTIMIZED: Replace Math.pow(x, 2.0) with direct multiplication (10-50x faster)
                            float repeatingPlusEps = repeating + eps;
                            float residualPlusEps = residual + eps;
                            float repeatingPow = repeatingPlusEps * repeatingPlusEps;
                            float residualPow = residualPlusEps * residualPlusEps;

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

                        // Apply synthesis window and overlap-add (use precomputed window)
                        for (int i = 0; i < windowSize && (startIdx + i) < numSamples; i++) {
                            vocalFloat[startIdx + i] += vocalFrame[i] * hanningWindow[i];
                            instrumentalFloat[startIdx + i] += instrumentalFrame[i] * hanningWindow[i];
                            windowSum[startIdx + i] += hanningWindow[i] * hanningWindow[i]; // Track window energy for normalization
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

    private void clearAllCaches() {
        vocalSamples = null;
        instrumentalSamples = null;
        wsolaSamples = null;
        speedSamples = null;
        croppedSamples = null;
        mixedSamples = null;
        statusView.setText("All cached results cleared");
    }

    private InputStream loadAudioFromSource() throws Exception {
        if (selectedAudioUri != null) {
            // Load from user-selected file
            return getContentResolver().openInputStream(selectedAudioUri);
        } else {
            // Fallback to built-in audio file
            return getResources().openRawResource(R.raw.audio_file);
        }
    }

    // Helper method to parse WAV file and extract sample rate and header size
    private int[] parseWavHeader(byte[] wavData) {
        int defaultSampleRate = 44100;
        int defaultHeaderSize = 44;

        try {
            // Verify minimum size
            if (wavData.length < 44) {
                final String msg = "File too small: " + wavData.length + " bytes";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
                return new int[]{defaultSampleRate, defaultHeaderSize};
            }

            // Check file format
            String header = new String(wavData, 0, 4, "ASCII");

            // Detect file type
            if (header.equals("ID3\u0000") || (wavData[0] == (byte)0xFF && (wavData[1] & 0xE0) == 0xE0)) {
                // MP3 file
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "MP3 files not supported. Please use WAV format.", Toast.LENGTH_LONG).show();
                        statusView.setText("Error: MP3 not supported");
                    }
                });
                return new int[]{-1, -1}; // Error code
            }

            // Verify RIFF/WAVE headers
            String riff = new String(wavData, 0, 4, "ASCII");
            String wave = new String(wavData, 8, 4, "ASCII");

            if (!riff.equals("RIFF") || !wave.equals("WAVE")) {
                final String detectedFormat = "Unknown format. Header: " + riff;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, detectedFormat + ". Please use WAV files.", Toast.LENGTH_LONG).show();
                        statusView.setText("Error: Not a WAV file");
                    }
                });
                return new int[]{-1, -1}; // Error code
            }

            // Standard WAV: assume fmt chunk at position 12
            ByteBuffer bb = ByteBuffer.wrap(wavData).order(ByteOrder.LITTLE_ENDIAN);

            // Check if "fmt " is at expected position
            String fmtCheck = new String(wavData, 12, 4, "ASCII");
            if (fmtCheck.equals("fmt ")) {
                // Standard format - sample rate at byte 24
                bb.position(24);
                int sampleRate = bb.getInt();

                // Validate sample rate is reasonable (8kHz to 192kHz)
                if (sampleRate >= 8000 && sampleRate <= 192000) {
                    final int finalRate = sampleRate;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Loaded WAV: " + finalRate + " Hz");
                        }
                    });
                    return new int[]{sampleRate, 44};
                } else {
                    final String msg = "Invalid sample rate: " + sampleRate + " Hz";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } else {
                final String msg = "Non-standard WAV format. Expected 'fmt ' at byte 12, got: '" + fmtCheck + "'";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }

            // Fallback to default
            return new int[]{defaultSampleRate, defaultHeaderSize};

        } catch (Exception e) {
            e.printStackTrace();
            final String errMsg = "Parse error: " + e.getMessage();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, errMsg, Toast.LENGTH_LONG).show();
                }
            });
            return new int[]{defaultSampleRate, defaultHeaderSize};
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
            Toast.makeText(this, "No instrumental data. Run REPET processing first.", Toast.LENGTH_LONG).show();
            return;
        }

        // Debug info
        String debugInfo = "Instrumental: " + instrumentalSamples.length + " samples, " + repetSampleRate + " Hz";
        Toast.makeText(this, debugInfo, Toast.LENGTH_SHORT).show();

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
            Toast.makeText(this, "Playback error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    public void pitchUpClick(View view) {
        // TODO: Implement pitch up functionality
        statusView.setText("Pitch up clicked");
    }


    public void WSOLAClick(View view) {
        // Check if WSOLA result is already cached
        if (wsolaSamples != null) {
            statusView.setText("Playing cached WSOLA result...");
            stopCurrentAudio();
            try {
                currentAudioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        wsolaSampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        wsolaSamples.length * 2,
                        AudioTrack.MODE_STATIC
                );
                ByteBuffer outBuffer = ByteBuffer.allocate(wsolaSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (short s : wsolaSamples) {
                    outBuffer.putShort(s);
                }
                currentAudioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
                currentAudioTrack.play();
                statusView.setText("Playing cached WSOLA result");
            } catch (Exception e) {
                statusView.setText("WSOLA playback error: " + e.getMessage());
            }
            return;
        }

        statusView.setText("Processing WSOLA pitch shift...");

        // Run WSOLA in background thread to avoid ANR
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Load WAV file from user-selected file or fallback to raw resources
                    InputStream inputStream = loadAudioFromSource();
                    byte[] wavData = new byte[inputStream.available()];
                    inputStream.read(wavData);
                    inputStream.close();

                    // 2. Parse WAV header and extract PCM data
                    int[] headerInfo = parseWavHeader(wavData);
                    final int sampleRate = headerInfo[0];
                    int headerSize = headerInfo[1];

                    // Check for parsing error
                    if (sampleRate == -1 || headerSize == -1) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusView.setText("Cannot process: Invalid audio format");
                            }
                        });
                        return;
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("WSOLA: Sample rate = " + sampleRate + " Hz");
                        }
                    });

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

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Starting WSOLA processing...");
                        }
                    });

                    int frameSize = 1024;
                    int analysisHop = frameSize / 2; // input hop size
                    int synthesisHop = (int) (analysisHop * pitch_shift); // output hop size

                    int outputLength = (int) ((numSamples - frameSize) / analysisHop) * synthesisHop + frameSize;
                    if (outputLength <= 0) outputLength = frameSize;

                    // OPTIMIZATION 5: Use float arithmetic to avoid overflow clamping
                    float[] outputSamples = new float[outputLength];

                    final int totalFrames = (numSamples - frameSize) / analysisHop;
                    int frameIndex = 0;

                    int outPos = 0;
                    for (int inPos = 0; inPos + frameSize < numSamples && outPos + frameSize < outputLength; inPos += analysisHop) {
                        // OPTIMIZATION 3: Coarse-to-fine search (mathematically equivalent)
                        int searchRange = analysisHop / 2;
                        int coarseStep = 8; // Step size for coarse search

                        // Step 1: Coarse search (every 8th offset)
                        int coarseBestOffset = 0;
                        double coarseMaxCorr = Double.NEGATIVE_INFINITY;

                        for (int offset = -searchRange; offset <= searchRange; offset += coarseStep) {
                            int refStart = inPos;
                            int cmpStart = inPos + analysisHop + offset;
                            if (cmpStart < 0 || cmpStart + frameSize > numSamples) continue;

                            double corr = 0;
                            for (int j = 0; j < frameSize; j++) {
                                corr += originalSamples[refStart + j] * originalSamples[cmpStart + j];
                            }
                            if (corr > coarseMaxCorr) {
                                coarseMaxCorr = corr;
                                coarseBestOffset = offset;
                            }
                        }

                        // Step 2: Fine search (all offsets within ±coarseStep of coarse best)
                        int bestOffset = coarseBestOffset;
                        double maxCorr = coarseMaxCorr;

                        int fineStart = Math.max(-searchRange, coarseBestOffset - coarseStep);
                        int fineEnd = Math.min(searchRange, coarseBestOffset + coarseStep);

                        for (int offset = fineStart; offset <= fineEnd; offset++) {
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

                        // OPTIMIZATION 5: Overlap-add using float arithmetic (no clamping needed)
                        for (int j = 0; j < frameSize; j++) {
                            int outIdx = outPos + j;
                            if (outIdx < outputSamples.length && bestStart + j < originalSamples.length) {
                                outputSamples[outIdx] += originalSamples[bestStart + j];
                            }
                        }
                        outPos += synthesisHop;

                        // Progress update every 50 frames
                        if (frameIndex % 50 == 0) {
                            final int progress = (frameIndex * 100) / totalFrames;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    statusView.setText("WSOLA: " + progress + "%");
                                }
                            });
                        }
                        frameIndex++;
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Finalizing output...");
                        }
                    });

                    // 4.5 add resampling to get the speed back to the original
                    // Simple 2x speed: take every other sample
                    int resampledLength = (int) (numSamples / rate);
                    short[] resampledSamples = new short[resampledLength];

                    // Convert float to short with clamping (done once at the end)
                    for (int i = 0; i < resampledSamples.length; i++) {
                        int srcIdx = (int) (i * rate);
                        if (srcIdx < outputSamples.length) {
                            float sample = outputSamples[srcIdx];
                            // Clamp to short range
                            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
                            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
                            resampledSamples[i] = (short) sample;
                        }
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Playing output...");
                        }
                    });

                    // Store WSOLA result in cache
                    wsolaSamples = resampledSamples;
                    wsolaSampleRate = sampleRate;

                    // 5. Play resampled PCM data using AudioTrack
                    AudioTrack audioTrack = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            resampledSamples.length * 2,
                            AudioTrack.MODE_STATIC
                    );

                    ByteBuffer outBuffer = ByteBuffer.allocate(resampledSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                    for (short s : resampledSamples) {
                        outBuffer.putShort(s);
                    }
                    audioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
                    audioTrack.play();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("WSOLA complete!");
                        }
                    });



                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("WSOLA error: " + errorMsg);
                        }
                    });
                }
            }
        }).start();
    }

    public void cropClick(View view) {
        // Check if Crop result is already cached
        if (croppedSamples != null) {
            statusView.setText("Playing cached Crop result...");
            stopCurrentAudio();
            try {
                currentAudioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        croppedSampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        croppedSamples.length * 2,
                        AudioTrack.MODE_STATIC
                );
                ByteBuffer outBuffer = ByteBuffer.allocate(croppedSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (short s : croppedSamples) {
                    outBuffer.putShort(s);
                }
                currentAudioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
                currentAudioTrack.play();
                statusView.setText("Playing cached Crop result");
            } catch (Exception e) {
                statusView.setText("Crop playback error: " + e.getMessage());
            }
            return;
        }

        statusView.setText("Processing Crop...");
        try {
            // 1. Load WAV file from user-selected file or fallback to raw resources
            InputStream inputStream = loadAudioFromSource();
            byte[] wavData = new byte[inputStream.available()];
            inputStream.read(wavData);
            inputStream.close();

            // 2. Parse WAV header and extract PCM data
            int[] headerInfo = parseWavHeader(wavData);
            int sampleRate = headerInfo[0];
            int headerSize = headerInfo[1];

            // Check for parsing error
            if (sampleRate == -1 || headerSize == -1) {
                statusView.setText("Cannot process: Invalid audio format");
                return;
            }

            statusView.setText("Crop: Sample rate = " + sampleRate + " Hz");

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

            // Store Crop result in cache
            croppedSamples = resampledSamples;
            croppedSampleRate = sampleRate * 2;

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

        // Check if Mix result is already cached
        if (mixedSamples != null) {
            statusView.setText("Playing cached Mix result...");
            stopCurrentAudio();
            try {
                currentAudioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        repetSampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        mixedSamples.length * 2,
                        AudioTrack.MODE_STATIC
                );
                ByteBuffer outBuffer = ByteBuffer.allocate(mixedSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (short s : mixedSamples) {
                    outBuffer.putShort(s);
                }
                currentAudioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
                currentAudioTrack.play();
                statusView.setText("Playing cached Mix result");
            } catch (Exception e) {
                statusView.setText("Mix playback error: " + e.getMessage());
            }
            return;
        }

        statusView.setText("Processing Mix...");

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
            short[] tempMixedSamples = new short[minLength];

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

                tempMixedSamples[i] = (short) mixed;
            }

            // Store Mix result in cache
            mixedSamples = tempMixedSamples;

            // Convert mixed short array to byte array for AudioTrack
            ByteBuffer outBuffer = ByteBuffer.allocate(tempMixedSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : tempMixedSamples) {
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
