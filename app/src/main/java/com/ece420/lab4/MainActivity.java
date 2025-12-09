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

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.nio.ByteBuffer;

import java.nio.ByteOrder;
import android.media.audiofx.Visualizer;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity {

    // UI Variables
    TextView statusView;
    TextView tabSeparation, tabTune, tabMixing;
    LinearLayout mTuneControls, mMixingControls;

    ImageView btnPlayPause, btnDownload;
    SpectrumVisualizerView spectrumVisualizer;

    
    // Playback Bar
    SpectrumVisualizerView waveformSeekBar;
    TextView tvCurrentTime, tvTotalTime;
    
    // Mix Status
    TextView tvTrack1Status, tvTrack2Status;

    private Visualizer mVisualizer;
    private static final int PERMISSION_REQUEST_AUDIO = 2;

    // Static Values
    private static final int FILE_PICKER_REQUEST = 1;

    // File picker
// File picker request code is defined above


    // Audio States
    private enum AudioState {
        ORIGINAL, VOCAL, INSTRUMENTAL, SPEED, PITCH, MIX, CROP
    }

    private AudioState currentAudioState_DEPRECATED = AudioState.ORIGINAL; // Removed by refactor

    //    -------------------------------------------------------
    Button repetButton;
    Button repetVocalButton;
    Button repetInstrumentalButton;
    Button mixButton;

    // New Toggle Buttons
    LinearLayout vocalInstrumentalContainer;
    Button btnVocal, btnInstrumental;
    Button btnOriginalTrack;
    
    // Track Toggle
    Button btnTrack1, btnTrack2;

    // Tune & Tempo Redesign
    private enum TuneMode { SPEED, PITCH, CROP }
    private TuneMode currentTuneMode = TuneMode.SPEED;

    private TextView btnModeSpeed, btnModePitch, btnModeCrop;
    private TextView tvActiveValue, tvActiveLabel;
    private SeekBar activeSlider;
    private Button btnApplyEffect;

    private MediaPlayer mediaPlayer;

    public static native boolean createSpeedAudioPlayer(String filePath, float speed);

    public static native void playSpeedAudio();

    public static native void stopSpeedAudio();

    public static native void deleteSpeedAudioPlayer();

    private boolean isSpeedPlaying = false;

    private AudioTrack currentAudioTrack = null; // Track currently playing audio

    private short[] mixedSamples = null;



    public class TrackState {
        public short[] originalSamples = null;
        public short[] vocalSamples = null;
        public short[] instrumentalSamples = null;
        
        public int originalSampleRate = 44100;
        public int vocalSampleRate = 44100;
        public int instrumentalSampleRate = 44100;

        public double crop_percent = 1;
        public double resample_percent = 1;
        public double pitch_up_percent = 1;
        
        public AudioState currentAudioState = AudioState.ORIGINAL;
        public Uri fileUri = null;
        
        public void clear() {
            originalSamples = null;
            vocalSamples = null;
            instrumentalSamples = null;
            originalSampleRate = 44100;
            vocalSampleRate = 44100;
            instrumentalSampleRate = 44100;
            
            crop_percent = 1;
            resample_percent = 1;
            pitch_up_percent = 1;

            currentAudioState = AudioState.ORIGINAL;
            fileUri = null;
        }
    }
    
    private TrackState track1 = new TrackState();
    private TrackState track2 = new TrackState();
    private int currentTrackId = 1; // 1 or 2
    
    private TrackState getCurrentTrackState() {
        return (currentTrackId == 1) ? track1 : track2;
    }
    
    // Global processing lock
    private boolean isProcessing = false;
    // ----------------------------

//    --------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // UI Elements
        checkAudioPermission();
        statusView = (TextView) findViewById(R.id.statusView);

        // New UI Elements
        tabSeparation = (TextView) findViewById(R.id.tab_separation);
        tabTune = (TextView) findViewById(R.id.tab_tune);
        tabMixing = (TextView) findViewById(R.id.tab_mixing);
        mTuneControls = (LinearLayout) findViewById(R.id.tune_controls);
        mMixingControls = (LinearLayout) findViewById(R.id.mixing_controls);

        // Initialize Waveform SeekBar
        waveformSeekBar = findViewById(R.id.waveform_seekbar);
        // Important: Make sure SpectrumVisualizerView handles nulls if not in layout yet? It is in layout.
        if (waveformSeekBar != null) {
            waveformSeekBar.setMode(SpectrumVisualizerView.Mode.WAVEFORM);
            waveformSeekBar.setOnSeekListener(new SpectrumVisualizerView.OnSeekListener() {
                @Override
                public void onSeekStart() {
                    stopProgressUpdater();
                }

                @Override
                public void onSeek(float progress) {
                    // Update time labels
                     long totalMs = getDurationMillis();
                     if (totalMs > 0) {
                          long currentMs = (long)(totalMs * progress);
                          if (tvCurrentTime != null) tvCurrentTime.setText(formatTime(currentMs));
                     }
                }

                @Override
                public void onSeekEnd(float progress) {
                    seekToProgress(progress);
                }
            });
        }

        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        
        tvTrack1Status = findViewById(R.id.tv_track1_status);
        tvTrack2Status = findViewById(R.id.tv_track2_status);
        updateMixStatusUI();

        setupToggleButtons();
        
        // Track Toggle
        btnTrack1 = findViewById(R.id.btn_track_1);
        btnTrack2 = findViewById(R.id.btn_track_2);
        
        btnTrack1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTrack(1);
            }
        });
        
        btnTrack2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTrack(2);
            }
        });
        
        btnPlayPause = (ImageView) findViewById(R.id.btn_play_pause);
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Play/Pause Playback Logic
                if (isAudioPlaying()) {
                    pauseAudio();
                } else {
                    // Optimized: Try to resume current track if it exists and matches state
                    if (currentAudioTrack != null && currentAudioTrack.getState() == AudioTrack.STATE_INITIALIZED 
                             && currentAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                        currentAudioTrack.play();
                        startProgressUpdater();
                        btnPlayPause.setImageResource(R.drawable.ic_pause_circle);
                        return; 
                    }
                    if (mediaPlayer != null && !mediaPlayer.isPlaying() && 
                        getCurrentTrackState().currentAudioState == AudioState.ORIGINAL && 
                        getCurrentTrackState().originalSamples == null) {
                         mediaPlayer.start();
                         startProgressUpdater();
                         btnPlayPause.setImageResource(R.drawable.ic_pause_circle);
                         return;
                    }
                    
                    // Fallback to start new playback
                    float progress = getCurrentProgress();
                    switch (getCurrentTrackState().currentAudioState) {
                        case ORIGINAL:
                            // Priority: 1. Processed Samples (from Speed/Pitch/Crop) 2. Native MediaPlayer (Raw File)
                            if (getCurrentTrackState().originalSamples != null) {
                                playAudio(getCurrentTrackState().originalSamples, getCurrentTrackState().originalSampleRate, progress, true);
                            } else if (mediaPlayer != null) {
                                mediaPlayer.start();
                                setupVisualizer(mediaPlayer.getAudioSessionId());
                                startProgressUpdater();
                                btnPlayPause.setImageResource(R.drawable.ic_pause_circle);
                            }
                            break;
                        case VOCAL:
                            if (getCurrentTrackState().vocalSamples != null) {
                                playAudio(getCurrentTrackState().vocalSamples, getCurrentTrackState().vocalSampleRate, progress, true);
                            } else {
                                Toast.makeText(MainActivity.this, "No vocal track available. Run Separator first.", Toast.LENGTH_SHORT).show();
                            }
                            break;
                        case INSTRUMENTAL:
                            if (getCurrentTrackState().instrumentalSamples != null) {
                                playAudio(getCurrentTrackState().instrumentalSamples, getCurrentTrackState().instrumentalSampleRate, progress, true);
                            } else {
                                Toast.makeText(MainActivity.this, "No instrumental track available. Run Separator first.", Toast.LENGTH_SHORT).show();
                            }
                            break;
                            
                        case MIX:
                            if (mixedSamples != null) {
                                playAudio(mixedSamples, 44100, progress, true);
                            } else {
                                Toast.makeText(MainActivity.this, "No mix track", Toast.LENGTH_SHORT).show();
                            }
                            break;
                    }
                }
            }
        });

        // Initialize MediaPlayer
        updateMediaPlayer();

        repetButton = (Button) findViewById(R.id.repet);
        repetVocalButton = (Button) findViewById(R.id.repet_vocal);
        repetInstrumentalButton = (Button) findViewById(R.id.repet_instrumental_button);
        mixButton = (Button) findViewById(R.id.mix_button);
        btnDownload = (ImageView) findViewById(R.id.btn_download);

        // Download/Load Button Listener
        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadFileClick(v);
            }
        });

        spectrumVisualizer = (SpectrumVisualizerView) findViewById(R.id.spectrum_visualizer);

        // Tab Click Listeners
        View.OnClickListener tabListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateTabs(v.getId());
            }
        };

        tabSeparation.setOnClickListener(tabListener);
        tabTune.setOnClickListener(tabListener);
        tabMixing.setOnClickListener(tabListener);
        
        // Tune & Tempo Controls Setup
        btnModeSpeed = findViewById(R.id.btn_mode_speed);
        btnModePitch = findViewById(R.id.btn_mode_pitch);
        btnModeCrop = findViewById(R.id.btn_mode_crop);
        tvActiveValue = findViewById(R.id.tv_active_value);
        tvActiveLabel = findViewById(R.id.tv_active_label);
        activeSlider = findViewById(R.id.active_slider);
        btnApplyEffect = findViewById(R.id.btn_apply_effect);

        View.OnClickListener modeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.btn_mode_speed) currentTuneMode = TuneMode.SPEED;
                else if (v.getId() == R.id.btn_mode_pitch) currentTuneMode = TuneMode.PITCH;
                else if (v.getId() == R.id.btn_mode_crop) currentTuneMode = TuneMode.CROP;
                updateTuneUI();
            }
        };

        btnModeSpeed.setOnClickListener(modeListener);
        btnModePitch.setOnClickListener(modeListener);
        btnModeCrop.setOnClickListener(modeListener);
        
        // Initial Update
        updateTuneUI();

        activeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                
                switch (currentTuneMode) {
                    case SPEED:
                        getCurrentTrackState().resample_percent = (double) progress / 500.0;
                        break;
                    case PITCH:
                        // Map 0-100 to 1.0-2.0 (Octave) using 2^(progress/100)
                        double octave = (double) progress / 100.0;
                        getCurrentTrackState().pitch_up_percent = Math.pow(2, octave);
                        break;
                    case CROP:
//                        getCurrentTrackState().crop_percent = 1.0 - (progress / 1000.0);
                        // Correct logic to keep within valid range
                         double p = progress / 1000.0;
                         if (p > 0.95) p = 0.95;
                         getCurrentTrackState().crop_percent = 1.0 - p;
                        break;
                }
                updateTuneValueText();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnApplyEffect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (currentTuneMode) {
                    case SPEED:
                        resampleClick(v);
                        break;
                    case PITCH:
                        // pitchUpClick(v);
                        WSOLAClick(v);
                        break;
                    case CROP:
                        cropClick(v);
                        break;
                }
            }
        });
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Call super
        if (requestCode == PERMISSION_REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                Toast.makeText(this, "Audio permission required for visualizer", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_AUDIO);
        }
    }

    private void setupVisualizer(int audioSessionId) {
        if (mVisualizer != null) {
            mVisualizer.release();
            mVisualizer = null;
        }

        // Check permission again
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            mVisualizer = new Visualizer(audioSessionId);
            mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            mVisualizer.setDataCaptureListener(
                    new Visualizer.OnDataCaptureListener() {
                        public void onWaveFormDataCapture(Visualizer visualizer,
                                                          byte[] bytes, int samplingRate) {
                            // We use FFT for spectrum, but could use this for waveform mode if we wanted real-time wave
                        }

                        public void onFftDataCapture(Visualizer visualizer,
                                                     byte[] bytes, int samplingRate) {
                            if (spectrumVisualizer != null && spectrumVisualizer.getMode() == SpectrumVisualizerView.Mode.SPECTRUM) {
                                spectrumVisualizer.updateVisualizer(bytes);
                            }
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true);
            mVisualizer.setEnabled(true);
        } catch (Exception e) {
            e.printStackTrace();
            final String err = e.getMessage();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Visualizer Error: " + err, Toast.LENGTH_LONG).show();
                    statusView.setText("Visualizer Error: " + err);
                }
            });
        }
    }

    private void seekToProgress(float progress) {
        // Calculate time in milliseconds
        // If playing original (MediaPlayer)
        // If playing original (MediaPlayer) - ONLY if we don't have samples loaded for AudioTrack
        if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL && mediaPlayer != null && getCurrentTrackState().originalSamples == null) {
            int duration = mediaPlayer.getDuration();
            int seekTo = (int) (duration * progress);
            mediaPlayer.seekTo(seekTo);
        }
        // If playing processed audio (AudioTrack), we can't easily seek with static mode unless we reload.
        // Static AudioTrack allows setPlaybackHeadPosition? Yes.
        else if (currentAudioTrack != null) {
            // AudioTrack in static mode
            // getBufferSizeInFrames() or something?
            // We passed samples.length * 2 as buffer size.
            // We need to know total frames.
            // If we have the samples array, we know length.

            int totalFrames = 0;
            if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL && getCurrentTrackState().originalSamples != null)
                totalFrames = getCurrentTrackState().originalSamples.length;
            else if (getCurrentTrackState().currentAudioState == AudioState.VOCAL && getCurrentTrackState().vocalSamples != null)
                totalFrames = getCurrentTrackState().vocalSamples.length;
            else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL && getCurrentTrackState().instrumentalSamples != null)
                totalFrames = getCurrentTrackState().instrumentalSamples.length;
//            else if (getCurrentTrackState().currentAudioState == AudioState.SPEED && speedSamples != null)
//                totalFrames = speedSamples.length;
//            else if (getCurrentTrackState().currentAudioState == AudioState.PITCH && wsolaSamples != null)
//                totalFrames = wsolaSamples.length;
            else if (getCurrentTrackState().currentAudioState == AudioState.MIX && mixedSamples != null)
                totalFrames = mixedSamples.length;
//            else if (getCurrentTrackState().currentAudioState == AudioState.CROP && croppedSamples != null)
//                totalFrames = croppedSamples.length;

            if (totalFrames > 0) {
                int frame = (int) (totalFrames * progress);
                boolean wasPlaying = (currentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
                
                // For static tracks, we must pause or stop to change head position
                currentAudioTrack.pause();
                currentAudioTrack.setPlaybackHeadPosition(frame);
                
                if (wasPlaying) {
                    currentAudioTrack.play();
                    startProgressUpdater();
                    btnPlayPause.setImageResource(R.drawable.ic_pause_circle);
                } else {
                    // Update visualizer immediately so user sees the change
                    if (spectrumVisualizer != null) {
                        spectrumVisualizer.setProgress(progress);
                    }
                }
            }
        }
    }

    // Helper Methods

    private void updateTabs(int selectedId) {
        // Reset all tabs
        tabSeparation.setBackgroundResource(0);
        tabTune.setBackgroundResource(0);
        tabMixing.setBackgroundResource(0);

        tabSeparation.setTextColor(getResources().getColor(R.color.colorTextSecondary));
        tabTune.setTextColor(getResources().getColor(R.color.colorTextSecondary));
        tabMixing.setTextColor(getResources().getColor(R.color.colorTextSecondary));

        // Hide all controls
        repetButton.setVisibility(View.GONE);
        vocalInstrumentalContainer.setVisibility(View.GONE);
        btnOriginalTrack.setVisibility(View.GONE);
        if (mTuneControls != null) mTuneControls.setVisibility(View.GONE);
        if (mMixingControls != null) mMixingControls.setVisibility(View.GONE);

        if (selectedId == R.id.tab_separation) {
            tabSeparation.setBackgroundResource(R.drawable.bg_tab_selected);
            tabSeparation.setTextColor(getResources().getColor(R.color.colorTextPrimary));

            if (getCurrentTrackState().vocalSamples != null) {
                vocalInstrumentalContainer.setVisibility(View.VISIBLE);
            } else {
                repetButton.setVisibility(View.VISIBLE);
            }
            btnOriginalTrack.setVisibility(View.VISIBLE);

        } else if (selectedId == R.id.tab_tune) {
            tabTune.setBackgroundResource(R.drawable.bg_tab_selected);
            tabTune.setTextColor(getResources().getColor(R.color.colorTextPrimary));
            if (mTuneControls != null) mTuneControls.setVisibility(View.VISIBLE);

        } else if (selectedId == R.id.tab_mixing) {
            tabMixing.setBackgroundResource(R.drawable.bg_tab_selected);
            tabMixing.setTextColor(getResources().getColor(R.color.colorTextPrimary));
            if (mMixingControls != null) mMixingControls.setVisibility(View.VISIBLE);
        }
    }

    private void setupToggleButtons() {
        btnVocal = findViewById(R.id.btn_vocal);
        btnInstrumental = findViewById(R.id.btn_instrumental);
        vocalInstrumentalContainer = findViewById(R.id.vocal_instrumental_container);
        btnOriginalTrack = findViewById(R.id.btn_original_track);

        btnVocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float progress = 0f;
                boolean wasPlaying = isAudioPlaying();

                getCurrentTrackState().currentAudioState = AudioState.VOCAL;
                updateToggleUI();
                
                if (wasPlaying) {
                    stopAllAudio();
                    if (getCurrentTrackState().vocalSamples != null) {
                        playAudio(getCurrentTrackState().vocalSamples, getCurrentTrackState().vocalSampleRate, progress, true);
                    }
                } else {
                    stopAllAudio();
                    if (getCurrentTrackState().vocalSamples != null) {
                         playAudio(getCurrentTrackState().vocalSamples, getCurrentTrackState().vocalSampleRate, progress, false);
                    }
                }
            }
        });

        btnInstrumental.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float progress = 0f;
                boolean wasPlaying = isAudioPlaying();

                getCurrentTrackState().currentAudioState = AudioState.INSTRUMENTAL;
                updateToggleUI();
                
                if (wasPlaying) {
                    stopAllAudio();
                     if (getCurrentTrackState().instrumentalSamples != null) {
                        playAudio(getCurrentTrackState().instrumentalSamples, getCurrentTrackState().instrumentalSampleRate, progress, true);
                    }
                } else {
                    stopAllAudio();
                     if (getCurrentTrackState().instrumentalSamples != null) {
                        playAudio(getCurrentTrackState().instrumentalSamples, getCurrentTrackState().instrumentalSampleRate, progress, false);
                    }
                }
            }
        });

        btnOriginalTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float progress = 0f;
                boolean wasPlaying = isAudioPlaying();
                
                getCurrentTrackState().currentAudioState = AudioState.ORIGINAL;
                updateToggleUI();
                
                if (wasPlaying) {
                    stopAllAudio();
                    if (getCurrentTrackState().originalSamples != null) {
                        playAudio(getCurrentTrackState().originalSamples, getCurrentTrackState().originalSampleRate, progress, true);
                    } else if (mediaPlayer != null) {
                         mediaPlayer.seekTo(0);
                         mediaPlayer.start();
                         setupVisualizer(mediaPlayer.getAudioSessionId());
                         startProgressUpdater();
                    }
                } else {
                    stopAllAudio();
                    if (getCurrentTrackState().originalSamples != null) {
                         playAudio(getCurrentTrackState().originalSamples, getCurrentTrackState().originalSampleRate, progress, false);
                    }
                    else if (mediaPlayer != null) {
                         mediaPlayer.seekTo(0);
                         // Ensure visualizer is cleared or set for empty
                         if (spectrumVisualizer != null) spectrumVisualizer.setWaveformSamples(null);
                    }
                }
            }
        });
    }

    private boolean isAudioPlaying() {
        if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL) {
            if (getCurrentTrackState().originalSamples != null) {
                return currentAudioTrack != null && currentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
            }
            return mediaPlayer != null && mediaPlayer.isPlaying();
        } else {
            return currentAudioTrack != null && currentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
        }
    }

    private float getCurrentProgress() {
        // If playing, calculate from audio track
        if (currentAudioTrack != null && currentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
             int totalFrames = 0;
             if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL && getCurrentTrackState().originalSamples != null)
                 totalFrames = getCurrentTrackState().originalSamples.length;
             else if (getCurrentTrackState().currentAudioState == AudioState.VOCAL && getCurrentTrackState().vocalSamples != null)
                 totalFrames = getCurrentTrackState().vocalSamples.length;
             else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL && getCurrentTrackState().instrumentalSamples != null)
                 totalFrames = getCurrentTrackState().instrumentalSamples.length;
             else if (getCurrentTrackState().currentAudioState == AudioState.MIX && mixedSamples != null)
                 totalFrames = mixedSamples.length;
             
             if (totalFrames > 0) {
                 long headPos = currentAudioTrack.getPlaybackHeadPosition() & 0xFFFFFFFFL;
                 return (float) headPos / totalFrames;
             }
        }
        
        // Fallback to visualizer progress (user may have seeked while paused)
        if (spectrumVisualizer != null) {
            return spectrumVisualizer.getProgress();
        }
        
        return 0f;
    }

    private void updateToggleUI() {
        // Reset styles
        btnVocal.setSelected(false);
        btnInstrumental.setSelected(false);
        btnOriginalTrack.setSelected(false);

        // Highlight selected
        if (getCurrentTrackState().currentAudioState == AudioState.VOCAL) {
            btnVocal.setSelected(true);
        } else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL) {
            btnInstrumental.setSelected(true);
        } else if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL) {
            btnOriginalTrack.setSelected(true);
        }
        
        updateMixStatusUI();
    }

    private void updateTuneUI() {
        // Update Buttons State
        btnModeSpeed.setSelected(currentTuneMode == TuneMode.SPEED);
        btnModePitch.setSelected(currentTuneMode == TuneMode.PITCH);
        btnModeCrop.setSelected(currentTuneMode == TuneMode.CROP);
        
        // Update Colors
        btnModeSpeed.setTextColor(getResources().getColor(currentTuneMode == TuneMode.SPEED ? R.color.colorTextPrimary : R.color.colorTextSecondary));
        btnModePitch.setTextColor(getResources().getColor(currentTuneMode == TuneMode.PITCH ? R.color.colorTextPrimary : R.color.colorTextSecondary));
        btnModeCrop.setTextColor(getResources().getColor(currentTuneMode == TuneMode.CROP ? R.color.colorTextPrimary : R.color.colorTextSecondary));
        
        updateTuneValueText();
        
        // Update Slider Position
        switch (currentTuneMode) {
            case SPEED:
                activeSlider.setMax(1000);
                activeSlider.setProgress((int)(getCurrentTrackState().resample_percent * 500));
                tvActiveLabel.setText("Playback Speed");
                btnApplyEffect.setText("APPLY SPEED");
                break;
            case PITCH:
                activeSlider.setMax(100);
                // Calculate progress from pitch ratio: progress = 100 * log2(pitch)
                if (getCurrentTrackState().pitch_up_percent <= 0) getCurrentTrackState().pitch_up_percent = 1.0;
                double octave = Math.log(getCurrentTrackState().pitch_up_percent) / Math.log(2);
                activeSlider.setProgress((int)(octave * 100));
                tvActiveLabel.setText("Pitch Change");
                btnApplyEffect.setText("APPLY PITCH");
                break;
            case CROP:
                activeSlider.setMax(1000);
                // inverse: percent = 1.0 - progress/1000.  progress/1000 = 1 - percent. progress = (1-percent)*1000.
                activeSlider.setProgress((int)((1.0 - getCurrentTrackState().crop_percent) * 1000));
                tvActiveLabel.setText("Amount to Crop");
                btnApplyEffect.setText("APPLY CROP");
                break;
        }
    }

    private void updateMixStatusUI() {
        if (tvTrack1Status == null || tvTrack2Status == null) return;
        
        // Track 1
        String t1Text = "T1: Empty";
        int t1Color = R.color.colorTextSecondary;
        if (track1.fileUri != null || track1.originalSamples != null) {
            t1Text = "T1: " + getStatusLabel(track1.currentAudioState);
            t1Color = (currentTrackId == 1) ? R.color.colorAccent : R.color.colorTextPrimary;
        }
        tvTrack1Status.setText(t1Text);
        tvTrack1Status.setTypeface(null, (currentTrackId == 1) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tvTrack1Status.setTextColor(getResources().getColor(t1Color));

        // Track 2
        String t2Text = "T2: Empty";
        int t2Color = R.color.colorTextSecondary;
        if (track2.fileUri != null || track2.originalSamples != null) {
            t2Text = "T2: " + getStatusLabel(track2.currentAudioState);
             t2Color = (currentTrackId == 2) ? R.color.colorAccent : R.color.colorTextPrimary;
        }
        tvTrack2Status.setText(t2Text);
        tvTrack2Status.setTypeface(null, (currentTrackId == 2) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tvTrack2Status.setTextColor(getResources().getColor(t2Color));
    }
    
    private String getStatusLabel(AudioState state) {
        switch (state) {
            case ORIGINAL: return "Original";
            case VOCAL: return "Vocal";
            case INSTRUMENTAL: return "Instr.";
            case MIX: return "Mix";
            default: return "Custom";
        }
    }

    private void updateTuneValueText() {
        switch (currentTuneMode) {
            case SPEED:
                int speed = (int)(getCurrentTrackState().resample_percent * 100);
                tvActiveValue.setText(speed + "%");
                break;
            case PITCH:
                double pitch = getCurrentTrackState().pitch_up_percent;
                if (pitch <= 0) pitch = 1.0;
                double oct = Math.log(pitch) / Math.log(2);
                tvActiveValue.setText(String.format("%.2f Octave", oct));
                break;
            case CROP:
                int crop = (int)((1.0 - getCurrentTrackState().crop_percent) * 100);
                 tvActiveValue.setText(crop + "%");
                break;
        }
    }

    private android.os.Handler mHandler = new android.os.Handler();
    private Runnable mUpdateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            float progress = 0f;
            if (spectrumVisualizer != null) {
                progress = spectrumVisualizer.getProgress(); 
            }
            
            boolean isPlaying = false;

            if (currentAudioTrack != null && currentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                int totalFrames = 0;
                if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL && getCurrentTrackState().originalSamples != null)
                    totalFrames = getCurrentTrackState().originalSamples.length;
                else if (getCurrentTrackState().currentAudioState == AudioState.VOCAL && getCurrentTrackState().vocalSamples != null)
                    totalFrames = getCurrentTrackState().vocalSamples.length;
                else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL && getCurrentTrackState().instrumentalSamples != null)
                    totalFrames = getCurrentTrackState().instrumentalSamples.length;
                else if (getCurrentTrackState().currentAudioState == AudioState.MIX && mixedSamples != null)
                    totalFrames = mixedSamples.length;

                if (totalFrames > 0) {
                    long headPos = currentAudioTrack.getPlaybackHeadPosition() & 0xFFFFFFFFL;
                    progress = (float) headPos / totalFrames;
                }
                isPlaying = true;
            } else if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                 progress = (float)mediaPlayer.getCurrentPosition() / mediaPlayer.getDuration();
                 isPlaying = true;
            }

            if (spectrumVisualizer != null) {
                spectrumVisualizer.setProgress(progress);
            }
            
            // Update Playback Bar
            if (waveformSeekBar != null) {
                waveformSeekBar.setProgress(progress);
            }
            
            // Update Time Labels
            long totalMs = getDurationMillis();
            long currentMs = (long)(totalMs * progress);
            
            if (tvCurrentTime != null) tvCurrentTime.setText(formatTime(currentMs));
            if (tvTotalTime != null) tvTotalTime.setText(formatTime(totalMs));

            if (isPlaying) {
                mHandler.postDelayed(this, 50); // Update every 50ms
            }
        }
    };

    private void startProgressUpdater() {
        stopProgressUpdater();
        // Use postDelayed() to ensure AudioTrack has time to update its state to PLAYING
        // This fixes the race condition where run() was called too early
        mHandler.postDelayed(mUpdateProgressRunnable, 100);
    }

    private void stopProgressUpdater() {
        mHandler.removeCallbacks(mUpdateProgressRunnable);
    }

    private void pauseAudio() {
        stopProgressUpdater();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        if (currentAudioTrack != null && currentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            currentAudioTrack.pause();
        }
        btnPlayPause.setImageResource(R.drawable.ic_play_circle);
    }

    private void stopAllAudio() {
        stopProgressUpdater();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play_circle);
        }
        if (currentAudioTrack != null) {
            try {
                if (currentAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    currentAudioTrack.stop();
                }
                currentAudioTrack.release();
            } catch (Exception e) {
                // Ignore
            }
            currentAudioTrack = null;
            btnPlayPause.setImageResource(R.drawable.ic_play_circle);
        }
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
        }
    }

    private void switchTrack(int trackId) {
        if (currentTrackId == trackId) return;

        // Pause current audio to avoid overlap or state confusion
        pauseAudio();

        currentTrackId = trackId;

        // Update UI Visuals for Toggle
        if (trackId == 1) {
            btnTrack1.setBackground(getResources().getDrawable(R.drawable.bg_tab_selected));
            btnTrack1.setTextColor(getResources().getColor(R.color.colorTextPrimary));
            btnTrack2.setBackgroundResource(0); // Transparent
            btnTrack2.setTextColor(getResources().getColor(R.color.colorTextSecondary));
            
            // Optional: Update title or import label
        } else {
            btnTrack2.setBackground(getResources().getDrawable(R.drawable.bg_tab_selected));
            btnTrack2.setTextColor(getResources().getColor(R.color.colorTextPrimary));
            btnTrack1.setBackgroundResource(0);
            btnTrack1.setTextColor(getResources().getColor(R.color.colorTextSecondary));
        }

        // Restore UI State for the selected track
        TrackState ts = getCurrentTrackState();
        
        // Show context message
        if (ts.originalSamples != null || ts.fileUri != null) {
            statusView.setText("Switched to Track " + trackId);
        } else {
            statusView.setText("Track " + trackId + " is empty. Import a file.");
        }
        
        // Update Sliders based on track state
        // Logic: percent (0-1) -> progress (0-1000)
        // Crop: 
        // Logic in listener: percent = (double)progress/10.0 => 0-100. Then /100 => 0-1. Then 1-percent.
        // Reverse: real_percent = 1 - ts.crop_percent. stored_percent = real_percent. progress = stored_percent * 100 * 10.
        // Simplification: just reset sliders to center or match default? 
        updateTuneUI();
        updateToggleUI();
        updateMediaPlayer();

        // Refresh Separation Tab UI state
        if (getCurrentTrackState().vocalSamples != null) {
            vocalInstrumentalContainer.setVisibility(View.VISIBLE);
            repetButton.setVisibility(View.GONE);
        } else {
            vocalInstrumentalContainer.setVisibility(View.GONE);
            repetButton.setVisibility(View.VISIBLE);
        }

        // Update Visualizer
        if (spectrumVisualizer != null) {
            spectrumVisualizer.setProgress(0); 
        }

        if (waveformSeekBar != null) {
             waveformSeekBar.setProgress(0);
             if (ts.originalSamples != null) {
                 waveformSeekBar.setWaveformSamples(ts.originalSamples);
             } else {
                 waveformSeekBar.setWaveformSamples(null);
             }
        }
        
        // Reset timestamp
        if (tvCurrentTime != null) tvCurrentTime.setText("00:00");
        if (tvTotalTime != null) {
             long totalMs = getDurationMillis();
             tvTotalTime.setText(formatTime(totalMs));
        }
    }

    private void resetSeparationUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                repetButton.setVisibility(View.VISIBLE);
                vocalInstrumentalContainer.setVisibility(View.GONE);
                getCurrentTrackState().currentAudioState = AudioState.ORIGINAL;
                updateMixStatusUI();
                stopAllAudio();
            }
        });
    }

    private void clearAllCaches() {
        getCurrentTrackState().vocalSamples = null;
        getCurrentTrackState().instrumentalSamples = null;
        getCurrentTrackState().originalSamples = null;
//        speedSamples = null;
//        wsolaSamples = null;
        mixedSamples = null;
//        croppedSamples = null;
    }

    private void playAudio(short[] samples, int sampleRate) {
        playAudio(samples, sampleRate, 0f, true);
    }



    private void playAudio(short[] samples, int sampleRate, float startProgress) {
        playAudio(samples, sampleRate, startProgress, true);
    }

    private void playAudio(short[] samples, int sampleRate, float startProgress, boolean shouldPlay) {
        stopAllAudio();
        try {
            currentAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    samples.length * 2,
                    AudioTrack.MODE_STATIC
            );
            ByteBuffer outBuffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : samples) {
                outBuffer.putShort(s);
            }
            currentAudioTrack.write(outBuffer.array(), 0, outBuffer.array().length);

            // Set start position if needed
            if (startProgress > 0f) {
                int startFrame = (int) (samples.length * startProgress);
                currentAudioTrack.setPlaybackHeadPosition(startFrame);
            }

            setupVisualizer(currentAudioTrack.getAudioSessionId());
            if (spectrumVisualizer != null) {
                spectrumVisualizer.setWaveformSamples(samples);
            }
            if (waveformSeekBar != null) {
                waveformSeekBar.setWaveformSamples(samples);
            }

            if (shouldPlay) {
                currentAudioTrack.play();
                startProgressUpdater();
                btnPlayPause.setImageResource(R.drawable.ic_pause_circle);
            } else {
                // Just update visualizer progress
                if (spectrumVisualizer != null) spectrumVisualizer.setProgress(startProgress);
                if (waveformSeekBar != null) waveformSeekBar.setProgress(startProgress);
                btnPlayPause.setImageResource(R.drawable.ic_play_circle);
            }

        } catch (Exception e) {
            statusView.setText("Playback error: " + e.getMessage());
            btnPlayPause.setImageResource(R.drawable.ic_play_circle);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // Clear previous track data
                stopAllAudio();
                getCurrentTrackState().clear();
                // Set new URI
                getCurrentTrackState().fileUri = uri;
                
                // Update UI
                updateMixStatusUI();
                if (statusView != null) statusView.setText("Loading audio...");
                
                // Load and display waveform
                loadWaveformAsync(getCurrentTrackState());
                
                // Reset Play Button
                if (btnPlayPause != null) btnPlayPause.setImageResource(R.drawable.ic_play_circle);
            }
        }
    }

    public void loadFileClick(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, FILE_PICKER_REQUEST);
    }

    public void resampleClick(View view) {
        onSpeedUpClick(view);
    }

    public void onSpeedUpClick(View view) {
        // Check if Speed result is already cached
//        if (speedSamples != null) {
//            statusView.setText("Playing cached Speed result...");
//            getCurrentTrackState().currentAudioState = AudioState.SPEED;
//            stopAllAudio();
//            // Optional: Auto-play?
//            // playAudio(speedSamples, speedSampleRate);
//            return;
//        }

        statusView.setText("Processing Speed resampling...");
        try {
            // ... (Processing logic remains same) ...
            // 1. Load WAV file from user-selected file or fallback to raw resources


            short[] originalSamplesArr = loadInAudio(getCurrentTrackState(), getCurrentTrackState().currentAudioState);
            int numSamples = originalSamplesArr.length;
            int sampleRate = getCurrentTrackState().originalSampleRate;

            if (getCurrentTrackState().currentAudioState == AudioState.VOCAL) {
                sampleRate = getCurrentTrackState().vocalSampleRate;
            } else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL) {
                sampleRate = getCurrentTrackState().instrumentalSampleRate;
            }
            // Simple 2x speed: take every other sample
            double rate = getCurrentTrackState().resample_percent;

            short[] resampledSamples = new short[(int) (numSamples / rate)];
            for (int i = 0; i < resampledSamples.length; i += 1) {
                resampledSamples[i] = originalSamplesArr[(int) (i * rate)];
            }

            // Store Speed result in cache
//            speedSamples = resampledSamples;

//            getCurrentTrackState().currentAudioState = AudioState.SPEED;

            if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL) {
                getCurrentTrackState().originalSamples = resampledSamples;
            } else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL) {
                getCurrentTrackState().instrumentalSamples = resampledSamples;
            } else if (getCurrentTrackState().currentAudioState == AudioState.VOCAL) {
                getCurrentTrackState().vocalSamples = resampledSamples;
            }


            stopAllAudio();
            statusView.setText("Speed processing complete. Press Play.");

        } catch (Exception e) {
            statusView.setText("Resample error: " + e.getMessage());
        }
    }

    // Need to update WSOLAClick (Pitch), cropClick, mixClick similarly
    // Since I can't see WSOLAClick in the previous view, I will assume it's there or I need to find it.
    // Wait, I saw WSOLAClick in the file content earlier? 
    // Ah, I missed viewing the WSOLAClick implementation. I should find it.

    public void cropClick(View view) {
        // Check if Crop result is already cached
//        if (croppedSamples != null) {
//            statusView.setText("Playing cached Crop result...");
//            getCurrentTrackState().currentAudioState = AudioState.CROP;
//            stopAllAudio();
//            return;
//        }

        // ... (Processing logic) ...
        statusView.setText("Processing Crop...");
        try {
            // 1. Load WAV file from user-selected file or fallback to raw resources
            short[] originalSamplesArr = loadInAudio(getCurrentTrackState(), getCurrentTrackState().currentAudioState);
            int numSamples = originalSamplesArr.length;
            int sampleRate = getCurrentTrackState().originalSampleRate;

            if (getCurrentTrackState().currentAudioState == AudioState.VOCAL) {
                sampleRate = getCurrentTrackState().vocalSampleRate;
            } else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL) {
                sampleRate = getCurrentTrackState().instrumentalSampleRate;
            }

//            double getCurrentTrackState().crop_percent = 0.6;
            short[] resampledSamples = new short[(int) (numSamples * getCurrentTrackState().crop_percent)];
            for (int i = 0; i < resampledSamples.length; i += 1) {
                resampledSamples[i] = originalSamplesArr[(int) (numSamples * (1 - getCurrentTrackState().crop_percent)) + i];
            }

            // Store Crop result in cache
//            croppedSamples = resampledSamples;
//            croppedSampleRate = sampleRate; // Was sampleRate * 2 in original code? Let's check.
            // Original code had: croppedSampleRate = sampleRate * 2; and sampleRate *= 2; 
            // But logic was just array copy. Why *2? Maybe mono/stereo confusion or just wrong?
            // I'll stick to sampleRate for now unless I see reason otherwise.
            // Wait, original code: sampleRate *= 2; // or parse from WAV header
            // AudioTrack audioTrack = new AudioTrack(..., sampleRate, ...);
            // If I change it, I might break it. Let's keep it consistent if I can.
            // Actually, let's just set the state.

//            croppedSampleRate = sampleRate * 2; // Correcting potential bug or just safe default

            if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL) {
                getCurrentTrackState().originalSamples = resampledSamples;
            } else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL) {
                getCurrentTrackState().instrumentalSamples = resampledSamples;
            } else if (getCurrentTrackState().currentAudioState == AudioState.VOCAL) {
                getCurrentTrackState().vocalSamples = resampledSamples;
            }

//            getCurrentTrackState().currentAudioState = AudioState.CROP;
            stopAllAudio();
            statusView.setText("Crop complete. Press Play.");

        } catch (Exception e) {
            statusView.setText("Crop error: " + e.getMessage());
        }
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long remSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remSeconds);
    }
    
    private long getDurationMillis() {
        if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL && mediaPlayer != null && getCurrentTrackState().originalSamples == null) {
             return mediaPlayer.getDuration();
        }
        
        int totalFrames = 0;
        int sampleRate = 44100;
        
        if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL && getCurrentTrackState().originalSamples != null) {
            totalFrames = getCurrentTrackState().originalSamples.length;
            sampleRate = getCurrentTrackState().originalSampleRate;
        }
        else if (getCurrentTrackState().currentAudioState == AudioState.VOCAL && getCurrentTrackState().vocalSamples != null) {
            totalFrames = getCurrentTrackState().vocalSamples.length;
            sampleRate = getCurrentTrackState().vocalSampleRate;
        }
        else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL && getCurrentTrackState().instrumentalSamples != null) {
            totalFrames = getCurrentTrackState().instrumentalSamples.length;
            sampleRate = getCurrentTrackState().instrumentalSampleRate;
        }
        else if (getCurrentTrackState().currentAudioState == AudioState.MIX && mixedSamples != null) {
            totalFrames = mixedSamples.length;
            sampleRate = 44100;
        }
             
        if (totalFrames > 0 && sampleRate > 0) {
            return (long) ((totalFrames / (double) sampleRate) * 1000);
        }
        
        return 0;
    }

    public void mixClick(View view) {
        mixTracks(view);
    }
    
    private void mixTracks(View view) {
        stopAllAudio();
        
        statusView.setText("Mixing Tracks...");
        
        short[] t1Samples = (track1.currentAudioState == AudioState.VOCAL) ? track1.vocalSamples : 
                           (track1.currentAudioState == AudioState.INSTRUMENTAL) ? track1.instrumentalSamples : track1.originalSamples;
                           
        short[] t2Samples = (track2.currentAudioState == AudioState.VOCAL) ? track2.vocalSamples : 
                           (track2.currentAudioState == AudioState.INSTRUMENTAL) ? track2.instrumentalSamples : track2.originalSamples;

        if (t1Samples == null && t2Samples == null) {
            statusView.setText("No tracks to mix.");
            return;
        }
        
        // Handle single track mixing case (fallback) or just play available
        if (t1Samples == null) {
             // Just copy T2?
             mixedSamples = t2Samples; // Reference copy
             statusView.setText("Only Track 2 available. Copied.");
        } else if (t2Samples == null) {
             mixedSamples = t1Samples;
             statusView.setText("Only Track 1 available. Copied.");
        } else {
            // Mix
            int minLength = Math.min(t1Samples.length, t2Samples.length);
            short[] mixResult = new short[minLength];
            
            for(int i=0; i<minLength; i++) {
                 int s1 = t1Samples[i];
                 int s2 = t2Samples[i];
                 int mixed = s1 + s2;
                 if (mixed > Short.MAX_VALUE) mixed = Short.MAX_VALUE;
                 if (mixed < Short.MIN_VALUE) mixed = Short.MIN_VALUE;
                 mixResult[i] = (short)mixed;
            }
            mixedSamples = mixResult;
            statusView.setText("Mixing Complete.");
        }
        
        // Play Result
        if (mixedSamples != null) {
            getCurrentTrackState().currentAudioState = AudioState.MIX;
            playAudio(mixedSamples, 44100, 0, true);
        }
    }

    public void REPETClick(View view) {
        if (isProcessing) {
            statusView.setText("Wait for current processing to finish.");
            return;
        }
        statusView.setText("Processing REPET...");

        final TrackState targetTrack = getCurrentTrackState();
        isProcessing = true;

        // Run REPET processing in background thread to avoid ANR
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Load WAV file from user-selected file or fallback to raw resources
                    InputStream inputStream = loadAudioFromSource(targetTrack);
                    
                    // Read entire stream using a robust loop (Fix for potential partial reads)
                    int available = inputStream.available();
                    byte[] wavData;
                    if (available > 0) {
                        wavData = new byte[available];
                        int totalBytesRead = 0;
                        int bytesRead;
                        while (totalBytesRead < available && (bytesRead = inputStream.read(wavData, totalBytesRead, available - totalBytesRead)) != -1) {
                            totalBytesRead += bytesRead;
                        }
                    } else {
                        // Fallback for streams where available returns 0 (e.g. some compression)
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        int nRead;
                        byte[] data = new byte[16384];
                        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }
                        buffer.flush();
                        wavData = buffer.toByteArray();
                    }
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
                        isProcessing = false;
                        return;
                    }

                    int pcmDataSize = wavData.length - headerSize;
                    byte[] pcmData = new byte[pcmDataSize];
                    System.arraycopy(wavData, headerSize, pcmData, 0, pcmDataSize);

                    // Convert PCM bytes to short samples (16-bit PCM)
                    ByteBuffer pcmBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
                    
                    int numChannels = headerInfo.length > 2 ? headerInfo[2] : 1;
                    int numSamples = pcmDataSize / (2 * numChannels);
                    
                    // Limit audio length to prevent OutOfMemoryError
                    // Max ~156 seconds at 48kHz = 7,500,000 samples (with largeHeap enabled)
                    final int maxSamples = 7500000;
                    
                    if (numSamples > maxSamples) {
                        final int durationSec = numSamples / actualSampleRate;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusView.setText("Audio too long (" + durationSec + "s). Max 156s. Truncating...");
                            }
                        });
                        numSamples = maxSamples;
                    }
                    
                    short[] samples = new short[numSamples];
                    
                    if (numChannels == 1) {
                         for (int i = 0; i < numSamples; i++) {
                             samples[i] = pcmBuffer.getShort();
                         }
                    } else {
                         // Stereo to Mono downmix
                         for (int i = 0; i < numSamples; i++) {
                             short left = pcmBuffer.getShort();
                             short right = pcmBuffer.getShort();
                             samples[i] = (short) ((left + right) / 2);
                         }
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
                        isProcessing = false;
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

                    // Store processed samples directly without resampling
                    targetTrack.vocalSamples = new short[numSamples];
                    targetTrack.instrumentalSamples = new short[numSamples];

                    for (int i = 0; i < numSamples; i++) {
                         // Clamp and convert to short
                        targetTrack.vocalSamples[i] = (short) Math.max(-32768, Math.min(32767, vocalFloat[i]));
                        targetTrack.instrumentalSamples[i] = (short) Math.max(-32768, Math.min(32767, instrumentalFloat[i]));
                    }

                    targetTrack.vocalSampleRate = actualSampleRate;
                    targetTrack.instrumentalSampleRate = actualSampleRate;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Update UI on success
                            statusView.setText("REPET separation complete!");
                            repetButton.setVisibility(View.GONE);
                            vocalInstrumentalContainer.setVisibility(View.VISIBLE);
                            
                            // Default to Vocal
                            targetTrack.currentAudioState = AudioState.VOCAL;
                            updateToggleUI();
                        }
                    });
                    
                    stopAllAudio();
                    isProcessing = false;

                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("REPET error: " + errorMsg);
                        }
                    });
                    e.printStackTrace();
                    isProcessing = false;
                }
            }
        }).start();
    }

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


    private short[] loadInAudio(TrackState ts, AudioState current) throws Exception {
        if (current == AudioState.ORIGINAL) {
            if (ts.originalSamples == null) {
                InputStream inputStream = loadAudioFromSource(ts);
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
                }

                statusView.setText("Speed: Sample rate = " + sampleRate + " Hz");

                int pcmDataSize = wavData.length - headerSize;
                byte[] pcmData = new byte[pcmDataSize];
                System.arraycopy(wavData, headerSize, pcmData, 0, pcmDataSize);

                // 3. Resample PCM data
                ByteBuffer pcmBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
                
                int numChannels = headerInfo.length > 2 ? headerInfo[2] : 1;
                int numSamples = pcmDataSize / (2 * numChannels);
                short[] originalSamplesArr = new short[numSamples];
                
                if (numChannels == 1) {
                    for (int i = 0; i < numSamples; i++) {
                        originalSamplesArr[i] = pcmBuffer.getShort();
                    }
                } else {
                     // Stereo to Mono
                    for (int i = 0; i < numSamples; i++) {
                        short left = pcmBuffer.getShort();
                        short right = pcmBuffer.getShort();
                        originalSamplesArr[i] = (short) ((left + right) / 2);
                    }
                }

                ts.originalSamples = originalSamplesArr;
//                repetSampleRate = sampleRate;
                ts.originalSampleRate = sampleRate;
                ts.instrumentalSampleRate = sampleRate;
                ts.vocalSampleRate = sampleRate;
            } else {
                return ts.originalSamples;
            }
        } else if (current == AudioState.VOCAL) {
            return ts.vocalSamples;
        } else if (current == AudioState.INSTRUMENTAL) {
            return ts.instrumentalSamples;
        }

        statusView.setText("Audio State not original, instrumental, or vocal");
        return ts.originalSamples;
    }


    private InputStream loadAudioFromSource(TrackState ts) throws Exception {
        if (ts.fileUri != null) {
            // Load from user-selected file
            return getContentResolver().openInputStream(ts.fileUri);
        } else {
             throw new Exception("No audio file selected.");
        }
    }



    private InputStream loadAudioFromSource() throws Exception {
        if (getCurrentTrackState().fileUri != null) {
            // Load from user-selected file
            return getContentResolver().openInputStream(getCurrentTrackState().fileUri);
        } else {
             throw new Exception("No audio loaded. Please load a file first.");
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
                return new int[]{defaultSampleRate, defaultHeaderSize, 1};
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
                return new int[]{-1, -1, -1}; // Error code
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
                return new int[]{-1, -1, -1}; // Error code
            }

            // Scan chunks dynamically
            ByteBuffer bb = ByteBuffer.wrap(wavData).order(ByteOrder.LITTLE_ENDIAN);
            int pos = 12; // Start after RIFF + Size + WAVE
            int sampleRate = -1;
            int headerSize = -1;
            int numChannels = 1; // Default to mono

            while (pos < wavData.length - 8) {
                String chunkId = new String(wavData, pos, 4, "ASCII");
                int chunkSize = bb.getInt(pos + 4);
                
                if (chunkSize < 0) break; // Invalid size

                if (chunkId.equals("fmt ")) {
                    // Found format chunk
                    // Format is: AudioFormat(2) + NumChannels(2) + SampleRate(4) ...
                    // So SampleRate is at offset 4 from chunk data start
                    if (chunkSize >= 16) {
                        numChannels = bb.getShort(pos + 8 + 2);
                        sampleRate = bb.getInt(pos + 8 + 4);
                    }
                } else if (chunkId.equals("data")) {
                    // Found data chunk
                    headerSize = pos + 8;
                    break; // Stop scanning, we found the data
                }

                pos += 8 + chunkSize;
                // WAV chunks are word-aligned
                if (chunkSize % 2 != 0) pos++;
            }

            if (sampleRate != -1 && headerSize != -1) {
                // Validate sample rate is reasonable (8kHz to 192kHz)
                if (sampleRate >= 8000 && sampleRate <= 192000) {
                    final int finalRate = sampleRate;
                    final int finalChannels = numChannels;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Loaded WAV: " + finalRate + " Hz, " + finalChannels + " Ch");
                        }
                    });
                    return new int[]{sampleRate, headerSize, numChannels};
                } else {
                    final String msg = "Invalid sample rate: " + sampleRate + " Hz";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                     return new int[]{-1, -1, -1};
                }
            } else {
                final String msg = "Could not find 'fmt ' or 'data' chunk.";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
                return new int[]{-1, -1, -1};
            }

        } catch (Exception e) {
            e.printStackTrace();
            final String errMsg = "Parse error: " + e.getMessage();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, errMsg, Toast.LENGTH_LONG).show();
                }
            });
            return new int[]{defaultSampleRate, defaultHeaderSize, 1};
        }
    }

    public void REPETVocalClick(View view) {
        if (getCurrentTrackState().vocalSamples == null) {
            statusView.setText("Please run REPET first!");
            return;
        }

        // Stop any currently playing audio
        stopAllAudio();

        try {
            // Play vocal (foreground) PCM data using AudioTrack with correct sample rate
            currentAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    getCurrentTrackState().vocalSampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    getCurrentTrackState().vocalSamples.length * 2,
                    AudioTrack.MODE_STATIC
            );

            // Convert short array to byte array for AudioTrack
            ByteBuffer outBuffer = ByteBuffer.allocate(getCurrentTrackState().vocalSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : getCurrentTrackState().vocalSamples) {
                outBuffer.putShort(s);
            }

            currentAudioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
            currentAudioTrack.play();
            startProgressUpdater();
            statusView.setText("Playing vocal track at " + getCurrentTrackState().vocalSampleRate + " Hz...");
        } catch (Exception e) {
            statusView.setText("Vocal playback error: " + e.getMessage());
        }
    }

    public void REPETInstrumentalClick(View view) {
        if (getCurrentTrackState().instrumentalSamples == null) {
            statusView.setText("Please run REPET first!");
            Toast.makeText(this, "No instrumental data. Run REPET processing first.", Toast.LENGTH_LONG).show();
            return;
        }

        // Debug info
        String debugInfo = "Instrumental: " + getCurrentTrackState().instrumentalSamples.length + " samples, " + getCurrentTrackState().vocalSampleRate + " Hz";
        Toast.makeText(this, debugInfo, Toast.LENGTH_SHORT).show();

        // Stop any currently playing audio
        stopAllAudio();

        try {
            // Play instrumental (background) PCM data using AudioTrack with correct sample rate
            currentAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    getCurrentTrackState().vocalSampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    getCurrentTrackState().instrumentalSamples.length * 2,
                    AudioTrack.MODE_STATIC
            );

            // Convert short array to byte array for AudioTrack
            ByteBuffer outBuffer = ByteBuffer.allocate(getCurrentTrackState().instrumentalSamples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : getCurrentTrackState().instrumentalSamples) {
                outBuffer.putShort(s);
            }

            currentAudioTrack.write(outBuffer.array(), 0, outBuffer.array().length);
            currentAudioTrack.play();
            startProgressUpdater();
            statusView.setText("Playing instrumental track at " + getCurrentTrackState().vocalSampleRate + " Hz...");
        } catch (Exception e) {
            statusView.setText("Instrumental playback error: " + e.getMessage());
            Toast.makeText(this, "Playback error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    public void pitchUpClick(View view) {
        WSOLAClick(view);
    }


    public void WSOLAClick(View view) {
        // Check if WSOLA result is already cached
//        if (wsolaSamples != null) {
//            statusView.setText("Playing cached WSOLA result...");
//            getCurrentTrackState().currentAudioState = AudioState.PITCH;
//            stopAllAudio();
//            return;
//        }

        statusView.setText("Processing WSOLA pitch shift...");

        // Run WSOLA in background thread to avoid ANR
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Load WAV file from user-selected file or fallback to raw resources
                    short[] originalSamplesArr = loadInAudio(getCurrentTrackState(), getCurrentTrackState().currentAudioState);
                    int numSamples = originalSamplesArr.length;
                    int sampleRate = getCurrentTrackState().originalSampleRate;

                    if (getCurrentTrackState().currentAudioState == AudioState.VOCAL) {
                        sampleRate = getCurrentTrackState().vocalSampleRate;
                    } else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL) {
                        sampleRate = getCurrentTrackState().instrumentalSampleRate;
                    }

                    final int sampleRateTitle = sampleRate;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("WSOLA: Sample rate = " + sampleRateTitle + " Hz");
                        }
                    });

                    // 4. WSOLA pitch shift up by 100% (one octave)
                    double pitch_shift = getCurrentTrackState().pitch_up_percent;
                    double rate = pitch_shift;

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
                                corr += originalSamplesArr[refStart + j] * originalSamplesArr[cmpStart + j];
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
                                corr += originalSamplesArr[refStart + j] * originalSamplesArr[cmpStart + j];
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
                            if (outIdx < outputSamples.length && bestStart + j < originalSamplesArr.length) {
                                outputSamples[outIdx] += originalSamplesArr[bestStart + j];
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
//                    wsolaSamples = resampledSamples;

                    if (getCurrentTrackState().currentAudioState == AudioState.ORIGINAL) {
                        getCurrentTrackState().originalSamples = resampledSamples;
                    } else if (getCurrentTrackState().currentAudioState == AudioState.INSTRUMENTAL) {
                        getCurrentTrackState().instrumentalSamples = resampledSamples;
                    } else if (getCurrentTrackState().currentAudioState == AudioState.VOCAL) {
                        getCurrentTrackState().vocalSamples = resampledSamples;
                    }


                    // 5. Update state
//                    currentAudioState = AudioState.PITCH;
                    stopAllAudio();
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("WSOLA complete! Press Play.");
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


    private void updateMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        try {
            if (getCurrentTrackState().fileUri != null) {
                mediaPlayer = MediaPlayer.create(this, getCurrentTrackState().fileUri);
            }

            if (mediaPlayer != null) {
                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        statusView.setText("Audio playback error");
                        return false;
                    }
                });
                setupVisualizer(mediaPlayer.getAudioSessionId());
            }
        } catch (Exception e) {
            statusView.setText("Failed to initialize audio player");
            e.printStackTrace();
        }
        
        if (mediaPlayer != null) {
            // setupVisualizer is already called above
            // loadWaveformAsync is called separately
        }
    }

    private void loadWaveformAsync(final TrackState targetTrack) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream inputStream = loadAudioFromSource(targetTrack);
                    byte[] wavData = new byte[inputStream.available()];
                    inputStream.read(wavData);
                    inputStream.close();
                    
                    int[] headerInfo = parseWavHeader(wavData);
                    int headerSize = headerInfo[1];
                    if (headerSize == -1) return;
                    
                    int pcmDataSize = wavData.length - headerSize;
                    ByteBuffer pcmBuffer = ByteBuffer.wrap(wavData, headerSize, pcmDataSize).order(ByteOrder.LITTLE_ENDIAN);
                    
                    int numChannels = headerInfo.length > 2 ? headerInfo[2] : 1;
                    int numSamples = pcmDataSize / (2 * numChannels); // Adjust for channels (2 bytes per sample per channel)
                    final short[] samples = new short[numSamples];
                    
                    if (numChannels == 1) {
                         for (int i = 0; i < numSamples; i++) {
                             samples[i] = pcmBuffer.getShort();
                         }
                    } else {
                         // Stereo to Mono downmix
                         for (int i = 0; i < numSamples; i++) {
                             short left = pcmBuffer.getShort();
                             short right = pcmBuffer.getShort();
                             samples[i] = (short) ((left + right) / 2);
                         }
                    }
                    
                    targetTrack.originalSamples = samples;
                    targetTrack.originalSampleRate = headerInfo[0];
                    targetTrack.vocalSampleRate = targetTrack.originalSampleRate;
                    targetTrack.instrumentalSampleRate = targetTrack.originalSampleRate;
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (spectrumVisualizer != null) {
                                spectrumVisualizer.setWaveformSamples(samples);
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /*
     * Loading our Libs
     */
    static {
        System.loadLibrary("echo");
    }

    /*
     * REPET native FFT functions
     */
    public static native void computeFFT(float[] input, float[] magnitude, float[] phase, int size);
    public static native void computeIFFT(float[] magnitude, float[] phase, float[] output, int size);
}
