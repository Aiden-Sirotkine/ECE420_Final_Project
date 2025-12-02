# Audio File Handling and Caching Mechanism

This document outlines how audio files are loaded, processed, and cached within the `MainActivity` of the application.

## 1. Audio File Loading

The application supports loading audio from two sources:
1.  **User-Selected File**: A WAV file selected from the device storage.
2.  **Default Resource**: A fallback raw resource (`R.raw.audio_file`) used if no file is selected.

### Key Components:
*   **`selectedAudioUri`**: A `Uri` variable that stores the path to the user-selected file. It is `null` by default.
*   **`FILE_PICKER_REQUEST`**: Request code used for the system file picker intent.

### Loading Process:
1.  **User Action**: The user clicks the "Load" or "Download" button (`loadFileClick`).
2.  **Intent**: An `ACTION_OPEN_DOCUMENT` intent is launched with MIME type `audio/*`.
3.  **Result Handling** (`onActivityResult`):
    *   If a file is selected, `selectedAudioUri` is updated.
    *   **`clearAllCaches()`** is called to invalidate any previous processing results.
    *   **`resetSeparationUI()`** is called to reset the UI state.
    *   **`updateMediaPlayer()`** is called to initialize the `MediaPlayer` with the new file.
    *   **`loadWaveformAsync()`** is triggered to update the visualizer.

### Helper Method: `loadAudioFromSource()`
This private method abstracts the source of the audio stream.
*   If `selectedAudioUri` is not null, it opens an `InputStream` from the Uri.
*   Otherwise, it opens the default `R.raw.audio_file`.
This ensures that all processing algorithms (REPET, WSOLA, Speed, etc.) automatically use the currently loaded file without needing to know the source.

## 2. WAV File Parsing

Since the processing algorithms work with raw PCM data, the application manually parses WAV headers.

### `parseWavHeader(byte[] wavData)`
*   Validates the "RIFF" and "WAVE" chunks.
*   Checks for the "fmt " chunk to extract the **Sample Rate**.
*   Returns the sample rate and the size of the header (usually 44 bytes) so the header can be skipped when reading PCM data.
*   **Note**: The app currently supports standard WAV files. MP3s or other formats are rejected with a Toast message.

## 3. Caching Mechanism

To ensure responsiveness and avoid re-processing the same audio data multiple times, the application uses a robust caching system. Processed audio data is stored in memory as `short[]` arrays (16-bit PCM).

### Cache Variables:
*   **`vocalSamples`**: Stores the isolated vocal track from REPET separation.
*   **`instrumentalSamples`**: Stores the isolated instrumental track from REPET separation.
*   **`speedSamples`**: Stores the result of the Speed Up (resampling) algorithm.
*   **`wsolaSamples`**: Stores the result of the Pitch Up (WSOLA) algorithm.
*   **`mixedSamples`**: Stores the result of mixing vocals and instrumentals.
*   **`croppedSamples`**: Stores the result of the Crop operation.

### Caching Logic:
1.  **Check Cache**: When a processing button (e.g., "Speed Up") is clicked, the app first checks if the corresponding cache variable (e.g., `speedSamples`) is not null.
2.  **Hit**: If data exists, it plays immediately from the cache (`playAudio(speedSamples, ...)`).
3.  **Miss**: If data is null, the processing algorithm runs:
    *   Loads raw audio using `loadAudioFromSource()`.
    *   Processes the data.
    *   Saves the result into the cache variable.
    *   Plays the result.

### Cache Invalidation (`clearAllCaches`)
This method sets all cache variables to `null`. It is **CRITICAL** to call this whenever the source audio changes (i.e., when a new file is loaded) to prevent playing processed versions of the *old* file.

## 4. Playback State Management

The `AudioState` enum tracks what is currently being played, which helps in managing UI toggles and playback logic.

```java
private enum AudioState {
    ORIGINAL,      // Playing the raw file via MediaPlayer
    VOCAL,         // Playing cached vocalSamples
    INSTRUMENTAL,  // Playing cached instrumentalSamples
    SPEED,         // Playing cached speedSamples
    PITCH,         // Playing cached wsolaSamples
    MIX,           // Playing cached mixedSamples
    CROP           // Playing cached croppedSamples
}
```

*   **`currentAudioState`**: Holds the current state.
*   **`updateToggleUI()`**: Updates the visual state of buttons (e.g., highlighting "Vocal" vs "Instrumental" vs "Original") based on `currentAudioState`.

## 5. Audio Playback Engines

The app uses two different engines depending on the state:
1.  **`MediaPlayer`**: Used for the **ORIGINAL** state. It handles streaming and playback of the file URI directly.
2.  **`AudioTrack`**: Used for all **Processed** states (Vocal, Speed, etc.). It plays the raw PCM data stored in the `short[]` caches.
    *   The `playAudio(short[] samples, int sampleRate)` helper method handles creating and writing to the `AudioTrack`.
    *   It also attaches the `SpectrumVisualizer` to the new session ID.

## Summary Flow
1.  **Load File** -> Updates Uri -> Clears Caches.
2.  **User clicks "Speed Up"**:
    *   `speedSamples` is null? -> Run Resampling -> Save to `speedSamples` -> Play.
3.  **User clicks "Speed Up" again**:
    *   `speedSamples` has data? -> Play directly from memory (Instant).
4.  **User loads new file**:
    *   `clearAllCaches()` -> `speedSamples` becomes null.
    *   Next "Speed Up" click will re-process the new file.
