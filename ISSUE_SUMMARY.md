# Issue Summary & Solutions

This document summarizes the technical challenges encountered with the Progress Bar and File Loading mechanisms, and the solutions implemented to resolve them.

## 1. Progress Bar & Playback Synchronization

### The Issue
The application has multiple audio states (Original, Vocal, Instrumental, Mix) and playback engines (`MediaPlayer` for original files, `AudioTrack` for processed raw samples). The progress bar (SeekBar) and the visualizer were often out of sync, leading to:
- **Jumping Progress**: The seek bar would jump back to 0 or stutter when dragging.
- **State Loss on Toggle**: When switching from "Vocal" to "Instrumental" while paused, the playback position was lost, causing the track to restart from the beginning when "Play" was pressed.
- **Conflict**: The UI thread trying to update the seek bar would conflict with the user trying to drag it.

### The Solution
1.  **Unified Progress Logic**: We centralized the progress calculation in `getCurrentProgress()`, which prioritizes the active `AudioTrack` playback head, then `MediaPlayer` position, and falls back to the `SpectrumVisualizer`'s last known position if paused.
2.  **Drag-Event Handling**: Implemented listeners on the `SeekBar`.
    -   **OnDragStart**: We mistakenly pause the *update runnable* (not the audio) to stop the slider from fighting the user's finger.
    -   **OnDragEnd (Release)**: We calculate the final percentage and call `seekToProgress()`, which handles the low-level seeking for the active engine.
3.  **State Preservation on Toggle**: We updated the listeners for `btnVocal`, `btnInstrumental`, and `btnOriginal`. Now, when you toggle tracks while paused:
    -   The app immediately loads the target waveform into the `SpectrumVisualizer`.
    -   It explicitly sets the visualizer's progress to match the previous track's position. this ensures that when `getCurrentProgress()` is called upon resuming playback, it returns the correct timestamp.

## 2. Two File Loading (Dual Track & large Files)

### The Issue
The app needs to handle loading external WAV files from the device storage (via `Uri`) as well as fallback raw resources. Challenges included:
-   **Memory Constraints**: Loading full 3-minute WAV files into RAM as `short[]` arrays caused `OutOfMemoryError` crashes.
-   **Format Inconsistency**: User files could be Stereo or Mono, and at varying sample rates (44.1kHz vs 48kHz), leading to "chipmunk" speed effects or playback distortion.
-   **Dual Track State**: The Mixing feature required managing two independent file states (Track 1 and Track 2) without overwriting each other.

### The Solution
1.  **Dynamic Downmixing**: We implemented a robust `loadAudioFromSource` and `parseWavHeader` pipeline. This reads the WAV header to detect channel count. If the file is Stereo, we mathematically average the Left and Right channels into a Mono signal (`(L+R)/2`) during load, ensuring consistent playback speed on our Mono `AudioTrack` configuration.
2.  **Sample Rate Extraction**: We extract the actual sample rate from the WAV header and store it in the `TrackState` (e.g., `vocalSampleRate`). The `AudioTrack` is then initialized with this exact rate, preventing pitch/speed shifts.
3.  **Safety Limits**: To solve the crash issues, we added a hard limit (approx. 7.5 million samples or ~2.5 minutes). If a loaded file exceeds this, we seamlessly truncate it and notify the user via the Status View, preserving app stability.
4.  **TrackState Architecture**: We separated data into distinct `TrackState` objects (Track 1 and Track 2). Each object holds its own `Uri`, `originalSamples`, and processed stems (`vocalSamples`, etc.), allowing for independent loading and mixing.
