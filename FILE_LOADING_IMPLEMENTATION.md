# File Loading Implementation Documentation

## Overview
This document explains how the audio file loading system works in the ECE420 Final Project app, including file picker integration, data streaming, WAV parsing, and caching mechanisms.

---

## 1. File Picker Implementation

### User Interface
- **Button**: "Load Audio File" button in `activity_main.xml` (lines 8-16)
- **onClick Handler**: `loadFileClick()` method in MainActivity.java (lines 322-327)

### Code Flow
```java
public void loadFileClick(View view) {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.setType("audio/*");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(intent, FILE_PICKER_REQUEST);
}
```

**What happens:**
1. Creates an `ACTION_OPEN_DOCUMENT` Intent
2. Filters to show only audio files (`audio/*`)
3. Launches Android's file picker UI
4. Waits for user selection via `startActivityForResult()`

### Permissions Required
In `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
                 android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

- **READ_EXTERNAL_STORAGE**: For Android 12 and below
- **READ_MEDIA_AUDIO**: For Android 13+ (new granular media permissions)

---

## 2. File Selection Handling

### Activity Result Processing
Located in `onActivityResult()` (lines 220-236):

```java
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
```

**What happens:**
1. Receives the selected file's URI
2. Stores URI in `selectedAudioUri` instance variable
3. Clears all cached processing results (to avoid using old cached data)
4. Shows confirmation to user with filename

---

## 3. URI vs File Path

### Why Use URI?
Modern Android uses **Content URIs** instead of direct file paths for security:

- **Old approach**: `/storage/emulated/0/Music/song.wav`
- **New approach**: `content://com.android.providers.media.documents/document/audio:1234`

**Benefits:**
- Scoped access (app only sees what user explicitly selected)
- Works across different storage providers (Google Drive, Downloads, etc.)
- Doesn't require broad storage permissions

### URI Storage
```java
private Uri selectedAudioUri = null;  // Instance variable (line 76)
```

---

## 4. Data Streaming with ContentResolver

### Loading Audio Data
The `loadAudioFromSource()` helper method (lines 836-844):

```java
private InputStream loadAudioFromSource() throws Exception {
    if (selectedAudioUri != null) {
        // Load from user-selected file
        return getContentResolver().openInputStream(selectedAudioUri);
    } else {
        // Fallback to built-in audio file
        return getResources().openRawResource(R.raw.audio_file);
    }
}
```

**Two paths:**
1. **User file**: Uses `ContentResolver.openInputStream(uri)`
2. **Built-in file**: Uses `Resources.openRawResource()`

### How ContentResolver Works
```
┌─────────────────┐
│   Your App      │
└────────┬────────┘
         │ openInputStream(uri)
         ▼
┌─────────────────┐
│ ContentResolver │ ──► Handles permission checks
└────────┬────────┘     Validates URI
         │              Opens stream
         ▼
┌─────────────────┐
│ File System     │ ──► Actual WAV file data
└─────────────────┘
```

### Reading the Stream
Example from `REPETClick()` (lines 443-447):

```java
InputStream inputStream = loadAudioFromSource();
byte[] wavData = new byte[inputStream.available()];
inputStream.read(wavData);
inputStream.close();
```

**Steps:**
1. Get InputStream from URI
2. Allocate byte array for entire file
3. Read all bytes into memory
4. Close stream

---

## 5. WAV File Parsing

### Parser Method
The `parseWavHeader()` method (lines 838-880) extracts sample rate and header size.

### WAV File Structure
```
┌─────────────────────────────────────┐
│ RIFF Header (12 bytes)              │
│  - "RIFF" (4 bytes)                 │
│  - File size (4 bytes)              │
│  - "WAVE" (4 bytes)                 │
├─────────────────────────────────────┤
│ Format Chunk (24 bytes)             │
│  - "fmt " (4 bytes)                 │
│  - Chunk size (4 bytes)             │
│  - Audio format (2 bytes) = PCM (1) │
│  - Channels (2 bytes) = Mono (1)    │
│  - Sample rate (4 bytes) ←────────  │  This is what we need!
│  - Byte rate (4 bytes)              │
│  - Block align (2 bytes)            │
│  - Bits/sample (2 bytes) = 16       │
├─────────────────────────────────────┤
│ Data Chunk (8+ bytes)               │
│  - "data" (4 bytes)                 │
│  - Data size (4 bytes)              │
│  - PCM audio data... ←─────────────  │  This is the actual audio
└─────────────────────────────────────┘
```

### Parsing Code
```java
// Verify RIFF/WAVE headers
String riff = new String(wavData, 0, 4, "ASCII");
String wave = new String(wavData, 8, 4, "ASCII");
if (!riff.equals("RIFF") || !wave.equals("WAVE")) {
    return new int[]{defaultSampleRate, defaultHeaderSize};
}

// Check if "fmt " is at expected position
String fmtCheck = new String(wavData, 12, 4, "ASCII");
if (fmtCheck.equals("fmt ")) {
    // Sample rate is at byte 24 (using LITTLE_ENDIAN)
    ByteBuffer bb = ByteBuffer.wrap(wavData).order(ByteOrder.LITTLE_ENDIAN);
    bb.position(24);
    int sampleRate = bb.getInt();

    // Validate sample rate is reasonable (8kHz to 192kHz)
    if (sampleRate >= 8000 && sampleRate <= 192000) {
        return new int[]{sampleRate, 44};
    }
}
```

**Key Points:**
- Uses `ByteOrder.LITTLE_ENDIAN` (WAV files are little-endian)
- Validates headers before trusting data
- Returns `[sampleRate, headerSize]` array
- Falls back to defaults (44100 Hz, 44 bytes) if invalid

### Error Detection
The parser detects:
- **MP3 files**: Checks for `ID3` tag or MP3 frame sync bytes
- **Non-WAV files**: Validates RIFF/WAVE headers
- **Invalid sample rates**: Must be 8kHz - 192kHz
- **Non-standard formats**: Expects "fmt " at byte 12

---

## 6. Processing Audio Data

### Extracting PCM Data
After parsing header:

```java
int[] headerInfo = parseWavHeader(wavData);
int sampleRate = headerInfo[0];
int headerSize = headerInfo[1];

// Skip header, get PCM data
int pcmDataSize = wavData.length - headerSize;
byte[] pcmData = new byte[pcmDataSize];
System.arraycopy(wavData, headerSize, pcmData, 0, pcmDataSize);

// Convert bytes to 16-bit samples
ByteBuffer pcmBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
int numSamples = pcmDataSize / 2;  // 16-bit = 2 bytes per sample
short[] samples = new short[numSamples];
for (int i = 0; i < numSamples; i++) {
    samples[i] = pcmBuffer.getShort();
}
```

### Memory Protection
To prevent OutOfMemoryError (added after crash):

```java
// Limit to ~156 seconds at 48kHz = 7,500,000 samples (with largeHeap enabled)
final int maxSamples = 7500000;
if (numSamples > maxSamples) {
    statusView.setText("Audio too long. Max 156 seconds. Truncating...");
    numSamples = maxSamples;
}
```

**Note**: `largeHeap="true"` is enabled in AndroidManifest.xml to allow processing longer audio files.

---

## 7. Caching System

### Cache Variables
Instance variables in MainActivity:

```java
// REPET caching
private short[] vocalSamples = null;
private short[] instrumentalSamples = null;
private int repetSampleRate = 44100;

// WSOLA caching
private short[] wsolaSamples = null;
private int wsolaSampleRate = 44100;

// Speed resampling caching
private short[] speedSamples = null;
private int speedSampleRate = 44100;

// Crop caching
private short[] croppedSamples = null;
private int croppedSampleRate = 44100;

// Mix caching
private short[] mixedSamples = null;
```

### How Caching Works

**1. Check Cache First:**
```java
public void WSOLAClick(View view) {
    // Check if WSOLA result is already cached
    if (wsolaSamples != null) {
        statusView.setText("Playing cached WSOLA result...");
        // Play cached audio directly
        playAudio(wsolaSamples, wsolaSampleRate);
        return;  // Skip processing!
    }

    // Not cached - run full processing...
}
```

**2. Store Result After Processing:**
```java
// After WSOLA processing completes:
wsolaSamples = resampledSamples;  // Store result
wsolaSampleRate = sampleRate;
```

**3. Clear Cache When New File Loaded:**
```java
private void clearAllCaches() {
    vocalSamples = null;
    instrumentalSamples = null;
    wsolaSamples = null;
    speedSamples = null;
    croppedSamples = null;
    mixedSamples = null;
    statusView.setText("All cached results cleared");
}
```

### Cache Benefits
- **Speed**: Second button tap plays instantly (no reprocessing)
- **Battery**: Saves CPU/battery on repeated playback
- **UX**: Immediate response for users

---

## 8. Data Flow Diagram

```
┌──────────────────┐
│  User taps       │
│  "Load File"     │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Android File    │
│  Picker UI       │
└────────┬─────────┘
         │ (user selects file)
         ▼
┌──────────────────┐
│  onActivityResult│
│  stores URI      │
│  clears caches   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  User taps       │
│  "REPET" button  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Check cache     │◄────┐ Cache hit?
│  (wsolaSamples)  │     │ Play & exit
└────────┬─────────┘     │
         │ Cache miss    │
         ▼               │
┌──────────────────┐     │
│ loadAudioFrom    │     │
│ Source()         │     │
└────────┬─────────┘     │
         │               │
         ▼               │
┌──────────────────┐     │
│ ContentResolver  │     │
│ opens stream     │     │
└────────┬─────────┘     │
         │               │
         ▼               │
┌──────────────────┐     │
│ Read all bytes   │     │
│ into byte[]      │     │
└────────┬─────────┘     │
         │               │
         ▼               │
┌──────────────────┐     │
│ parseWavHeader() │     │
│ validates format │     │
└────────┬─────────┘     │
         │               │
         ▼               │
┌──────────────────┐     │
│ Extract PCM data │     │
│ (skip header)    │     │
└────────┬─────────┘     │
         │               │
         ▼               │
┌──────────────────┐     │
│ Convert bytes to │     │
│ short[] samples  │     │
└────────┬─────────┘     │
         │               │
         ▼               │
┌──────────────────┐     │
│ Run REPET/WSOLA  │     │
│ algorithm        │     │
└────────┬─────────┘     │
         │               │
         ▼               │
┌──────────────────┐     │
│ Store in cache   │─────┘
│ (wsolaSamples)   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Play audio       │
└──────────────────┘
```

---

## 9. Error Handling

### File Format Errors
```java
// MP3 detection
if (header.equals("ID3\u0000") || (wavData[0] == 0xFF && ...)) {
    Toast.makeText(this, "MP3 files not supported. Use WAV.", LONG).show();
    return new int[]{-1, -1};  // Error code
}

// Invalid WAV
if (!riff.equals("RIFF") || !wave.equals("WAVE")) {
    Toast.makeText(this, "Unknown format: " + riff + ". Use WAV.", LONG).show();
    return new int[]{-1, -1};
}
```

### Memory Errors
```java
// Limit audio length
if (numSamples > 1440000) {  // 30 seconds at 48kHz
    statusView.setText("Audio too long. Truncating...");
    numSamples = 1440000;
}

// Catch OutOfMemoryError during array allocation
try {
    magnitude = new float[numFrames][numBins];
    phase = new float[numFrames][numBins];
} catch (OutOfMemoryError e) {
    statusView.setText("Out of memory. Try shorter audio (max 30s).");
    return;
}
```

### Processing Method Error Checks
```java
// Check for invalid format before processing
if (sampleRate == -1 || headerSize == -1) {
    statusView.setText("Cannot process: Invalid audio format");
    return;
}
```

---

## 10. Memory Management

### Heap Limit
Android apps have a heap limit (typically 256-512 MB). For this app:
- **largeHeap enabled**: Provides 384-768 MB (device-dependent)
- **Target limit**: ~390 MB peak for 156-second audio
- **REPET arrays**: Can use significant memory for long audio

### Memory Usage Calculation
For a 156-second (~2.6 minute) audio file at 48kHz:
```
Samples: 156s × 48000 Hz = 7,488,000 samples (~7.5 million)

STFT arrays:
- numFrames = (7,488,000 - 2048) / 512 + 1 = 14,621 frames
- numBins = 2048 / 2 + 1 = 1,025 bins
- magnitude[14621][1025] = 14,621 × 1,025 × 4 bytes = 59.9 MB
- phase[14621][1025] = 59.9 MB
- backgroundMask[14621][1025] = 59.9 MB
- foregroundMask[14621][1025] = 59.9 MB
- vocalFloat[7,488,000] = 7,488,000 × 4 = 28.6 MB
- instrumentalFloat[7,488,000] = 28.6 MB

Total: ~297 MB for 156 seconds
```

With temporary allocations and overhead, peak usage is ~390 MB, fitting comfortably within largeHeap limits on most devices. Files longer than 156 seconds will be truncated to prevent **OutOfMemoryError**.

---

## 11. Background Processing

### Why Use Threads?
```java
new Thread(new Runnable() {
    @Override
    public void run() {
        // Heavy processing here
    }
}).start();
```

**Reason**: REPET processing takes several seconds. Running on UI thread would cause:
- App freeze (ANR - Application Not Responding)
- Poor user experience

### UI Updates from Background Thread
```java
runOnUiThread(new Runnable() {
    @Override
    public void run() {
        statusView.setText("Processing: 50%");
    }
});
```

**Rule**: Only the UI thread can update UI elements. Background threads must use `runOnUiThread()`.

---

## 12. Best Practices Used

1. **Graceful Fallback**: If user file fails, built-in file still works
2. **Clear Error Messages**: Toast + statusView for user feedback
3. **Cache Invalidation**: Clear old caches when new file loaded
4. **Memory Limits**: Prevent crashes by limiting audio length
5. **Format Validation**: Detect and reject unsupported formats
6. **Background Processing**: Don't block UI thread
7. **Resource Cleanup**: Close InputStreams after use
8. **Defensive Coding**: Null checks, try-catch blocks

---

## 13. Known Limitations

1. **File Size**: Max 156 seconds (~2.6 minutes) with largeHeap enabled (to prevent OutOfMemoryError)
2. **Format Support**: Only uncompressed 16-bit PCM WAV files
3. **Channels**: Only mono audio supported
4. **Sample Rate**: Must be 8kHz - 192kHz
5. **Header Format**: Must be standard 44-byte WAV header

---

## 14. Future Improvements

1. **Larger Files**: Stream/chunk processing instead of loading entire file
2. **More Formats**: Use MediaCodec/MediaExtractor for MP3, M4A, FLAC
3. **Stereo Support**: Process left/right channels separately or mix to mono
4. **Progress Bar**: Visual progress indicator instead of text status
5. **File Info Display**: Show duration, sample rate, file size before processing
6. **Configurable Limits**: Let user adjust max file size vs memory usage

---

## Summary

The file loading system uses modern Android APIs (ContentResolver, URIs) to provide secure, flexible audio file access. The implementation includes robust error handling, memory protection, and an efficient caching system to optimize the user experience.
