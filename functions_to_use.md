    public void WSOLAClick(View view) {
        // Check if WSOLA result is already cached
        if (wsolaSamples != null) {
            statusView.setText("Playing cached WSOLA result...");
            currentAudioState = AudioState.PITCH;
            stopAllAudio();
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




                    // 5. Update state
                    currentAudioState = AudioState.PITCH;
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
