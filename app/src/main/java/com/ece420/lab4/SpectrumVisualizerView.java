package com.ece420.lab4;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class SpectrumVisualizerView extends View {

    public enum Mode {
        SPECTRUM, WAVEFORM
    }

    private byte[] mBytes;
    private float[] mPreviousHeights;
    private Rect mRect = new Rect();
    private Paint mForePaint = new Paint();
    private Paint mGridPaint = new Paint();
    private int mSpectrumNumColumns = 40; // Fewer columns for blockier "pixel" look
    private Mode mMode = Mode.SPECTRUM;
    
    // Waveform data (full track)
    private short[] mWaveformSamples;
    private float mProgress = 0f; // 0.0 to 1.0
    
    // Paints for Waveform
    private Paint mPlayedPaint = new Paint();
    private Paint mRemainingPaint = new Paint();

    private OnSeekListener mListener;

    public interface OnSeekListener {
        void onSeekStart();
        void onSeek(float progress);
        void onSeekEnd(float progress);
    }

    public SpectrumVisualizerView(Context context) {
        super(context);
        init();
    }

    public SpectrumVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpectrumVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBytes = null;
        mForePaint.setAntiAlias(true);
        mForePaint.setStyle(Paint.Style.FILL);
        
        mGridPaint.setColor(Color.argb(30, 255, 255, 255));
        
        mPlayedPaint.setColor(0xFF7C3AED); // Violet Accent
        mPlayedPaint.setStyle(Paint.Style.FILL);
        mPlayedPaint.setAntiAlias(true);

        mRemainingPaint.setColor(0xFF555555); // Dark Gray
        mRemainingPaint.setStyle(Paint.Style.FILL);
        mRemainingPaint.setAntiAlias(true);
        mGridPaint.setStrokeWidth(2f);
    }

    public void setOnSeekListener(OnSeekListener listener) {
        this.mListener = listener;
    }

    public void setMode(Mode mode) {
        this.mMode = mode;
        invalidate();
    }
    
    public Mode getMode() {
        return mMode;
    }

    /**
     * Update the visualizer with new FFT or Waveform data from Visualizer.
     * @param bytes
     */
    public void updateVisualizer(byte[] bytes) {
        mBytes = bytes;
        invalidate();
    }

    /**
     * Set the full waveform data for the Waveform mode.
     * @param samples
     */
    public void setWaveformSamples(short[] samples) {
        this.mWaveformSamples = samples;
        invalidate();
    }

    public void setProgress(float progress) {
        this.mProgress = progress;
        if (mMode == Mode.WAVEFORM) {
            invalidate();
        }
    }

    public float getProgress() {
        return mProgress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mMode == Mode.SPECTRUM) {
            drawSpectrum(canvas);
        } else {
            drawWaveform(canvas);
        }
    }

    private void drawSpectrum(Canvas canvas) {
        if (mBytes == null) {
            return;
        }

        if (mPreviousHeights == null || mPreviousHeights.length != mSpectrumNumColumns) {
            mPreviousHeights = new float[mSpectrumNumColumns];
        }

        // Create a gradient for the bars
        Shader shader = new LinearGradient(0, getHeight(), 0, 0,
                new int[]{0xFF4F46E5, 0xFFEC4899, 0xFFFBBF24}, // Indices: Indigo -> Pink -> Amber
                new float[]{0.0f, 0.5f, 1.0f}, Shader.TileMode.CLAMP);
        mForePaint.setShader(shader);

        final int fftSize = mBytes.length / 2;
        int chunk = fftSize / mSpectrumNumColumns;
        if (chunk < 1) chunk = 1;

        float barWidth = getWidth() / (float) mSpectrumNumColumns;
        float gap = barWidth * 0.2f; // 20% gap
        float pixelHeight = 15f; // Height of each "pixel" block
        float pixelGap = 4f;     // Gap between vertical blocks

        for (int i = 0; i < mSpectrumNumColumns; i++) {
            // Calculate magnitude for this frequency bin
            byte rfk = mBytes[2 * i * chunk];
            byte ifk = mBytes[2 * i * chunk + 1];
            float magnitude = (float) Math.hypot(rfk, ifk);
            
            // Standardize and scale
            float dbValue = (float) (10 * Math.log10(magnitude + 1)); // +1 to avoid log(0)
            float targetHeight = (dbValue * 20f); 
            if (targetHeight > getHeight()) targetHeight = getHeight();
            if (targetHeight < 0) targetHeight = 0;

            // Smooth decay animation:
            // If new > old, rise quickly (but maybe smoothed a bit too). 
            // If new < old, fall slowly (decay).
            if (targetHeight > mPreviousHeights[i]) {
                mPreviousHeights[i] = mPreviousHeights[i] + (targetHeight - mPreviousHeights[i]) * 0.5f; // Fast rise
            } else {
               mPreviousHeights[i] = Math.max(targetHeight, mPreviousHeights[i] - 10f); // Slow decay
            }

            float currentHeight = mPreviousHeights[i];
            float x = i * barWidth + gap / 2;
            
            // Draw "Pixels"
            int numPixels = (int) (currentHeight / (pixelHeight + pixelGap));
            for (int j = 0; j < numPixels; j++) {
                float y = getHeight() - (j * (pixelHeight + pixelGap)) - pixelHeight;
                canvas.drawRoundRect(x, y, x + barWidth - gap, y + pixelHeight, 4f, 4f, mForePaint);
            }
        }
        
        // Request next frame for smooth animation if any bar is still dropping
        // We can just postInvalidateDelayed(16) to keep 60fps while animating
        postInvalidateDelayed(16);
    }

    private void drawWaveform(Canvas canvas) {
        if (mWaveformSamples == null) {
            // Draw a flat line if no samples
            canvas.drawLine(0, getHeight() / 2f, getWidth(), getHeight() / 2f, mGridPaint);
            return;
        }
        
        int width = getWidth();
        int height = getHeight();
        float centerY = height / 2f;

        // Sampling step
        int step = mWaveformSamples.length / width;
        if (step < 1) step = 1;
        
        // Draw bars
        for (int i = 0; i < width; i++) {
             int index = i * step;
             if (index >= mWaveformSamples.length) break;
             
             short val = mWaveformSamples[index];
             // Normalize to height
             float normalized = (float) val / Short.MAX_VALUE;
             float barHeight = Math.abs(normalized * centerY * 0.8f); // 80% height max
             if (barHeight < 2) barHeight = 2; // Minimum visible
             
             float top = centerY - barHeight;
             float bottom = centerY + barHeight;
             
             // Check if played
             float currentXPct = (float) i / width;
             if (currentXPct <= mProgress) {
                 canvas.drawRect(i, top, i + 1, bottom, mPlayedPaint);
             } else {
                 canvas.drawRect(i, top, i + 1, bottom, mRemainingPaint);
             }
        }
        
        // Draw scrubber line
        float scrubberX = mProgress * width;
        canvas.drawRect(scrubberX - 2, 0, scrubberX + 2, height, mPlayedPaint);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mMode == Mode.WAVEFORM) {
            float x = event.getX();
            float progress = x / getWidth();
            if (progress < 0) progress = 0;
            if (progress > 1) progress = 1;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mListener != null) mListener.onSeekStart();
                    setProgress(progress);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    setProgress(progress);
                    if (mListener != null) mListener.onSeek(progress);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (mListener != null) mListener.onSeekEnd(progress);
                    return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
