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
    private float[] mPoints;
    private Rect mRect = new Rect();
    private Paint mForePaint = new Paint();
    private Paint mGridPaint = new Paint();
    private int mSpectrumNumColumns = 60; // Number of bars
    private Mode mMode = Mode.SPECTRUM;
    
    // Waveform data (full track)
    private short[] mWaveformSamples;
    private float mProgress = 0f; // 0.0 to 1.0

    private OnSeekListener mListener;

    public interface OnSeekListener {
        void onSeek(float progress);
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
        mForePaint.setStrokeWidth(8f);
        mForePaint.setAntiAlias(true);
        mForePaint.setColor(Color.rgb(0, 128, 255));
        
        mGridPaint.setColor(Color.argb(50, 255, 255, 255));
        mGridPaint.setStrokeWidth(2f);
    }

    public void setListener(OnSeekListener listener) {
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

        if (mPoints == null || mPoints.length < mBytes.length * 4) {
            mPoints = new float[mBytes.length * 4];
        }

        mRect.set(0, 0, getWidth(), getHeight());

        // Create a gradient for the bars
        Shader shader = new LinearGradient(0, getHeight(), 0, 0,
                new int[]{0xFF7C3AED, 0xFF2DD4BF}, // Purple to Cyan
                null, Shader.TileMode.CLAMP);
        mForePaint.setShader(shader);
        mForePaint.setStrokeWidth((float) getWidth() / mSpectrumNumColumns * 0.8f); // Bar width with gap

        final int fftSize = mBytes.length / 2; // Real part only usually, or magnitude
        // Visualizer.getFft() returns real and imaginary parts. 
        // We need to compute magnitude.
        
        // Actually, let's simplify. If we assume mBytes is magnitude (if we pre-process) 
        // OR we process here. Android Visualizer returns [real, img, real, img...]
        // The first byte is DC.
        
        // Let's just draw what we get for now, assuming standard Visualizer FFT data.
        // We will aggregate bins to match mSpectrumNumColumns.
        
        int chunk = fftSize / mSpectrumNumColumns;
        if (chunk < 1) chunk = 1;

        for (int i = 0; i < mSpectrumNumColumns; i++) {
            byte rfk = mBytes[2 * i * chunk];
            byte ifk = mBytes[2 * i * chunk + 1];
            float magnitude = (float) (rfk * rfk + ifk * ifk);
            int dbValue = (int) (10 * Math.log10(magnitude));
            
            // Scaling for display
            // Magnitudes are small, usually. 
            // Let's try a simpler approach often used: just magnitude.
            // Actually, Visualizer gives signed bytes.
            
            float mag = (float) Math.hypot(rfk, ifk);
            
            // Scale to height
            float barHeight = (mag / 128f) * getHeight() * 2; 
            if (barHeight > getHeight()) barHeight = getHeight();
            
            float x = i * ((float) getWidth() / mSpectrumNumColumns) + (mForePaint.getStrokeWidth() / 2);
            float startY = getHeight();
            float stopY = getHeight() - barHeight;

            canvas.drawLine(x, startY, x, stopY, mForePaint);
        }
    }

    private void drawWaveform(Canvas canvas) {
        // Draw background grid/line
        float centerY = getHeight() / 2f;
        canvas.drawLine(0, centerY, getWidth(), centerY, mGridPaint);

        if (mWaveformSamples == null) {
            return;
        }
        
        // Draw full waveform
        // We need to downsample to fit screen width
        int width = getWidth();
        int step = mWaveformSamples.length / width;
        if (step < 1) step = 1;
        
        mForePaint.setShader(null);
        mForePaint.setColor(0xFF2DD4BF); // Cyan
        mForePaint.setStrokeWidth(2f);
        
        // Draw simple envelope
        for (int i = 0; i < width; i++) {
            int idx = i * step;
            if (idx >= mWaveformSamples.length) break;
            
            short val = mWaveformSamples[idx];
            float normalized = (float) val / Short.MAX_VALUE;
            float h = normalized * (getHeight() / 2f);
            
            canvas.drawLine(i, centerY - h, i, centerY + h, mForePaint);
        }
        
        // Draw progress indicator
        float progressX = mProgress * width;
        mForePaint.setColor(0xFFFFFFFF); // White
        mForePaint.setStrokeWidth(4f);
        canvas.drawLine(progressX, 0, progressX, getHeight(), mForePaint);
        
        // Draw glass-like overlay on played part?
        // Maybe just a semi-transparent rect
        Paint overlay = new Paint();
        overlay.setColor(Color.argb(50, 124, 58, 237)); // Transparent Purple
        canvas.drawRect(0, 0, progressX, getHeight(), overlay);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mMode == Mode.WAVEFORM) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float x = event.getX();
                float progress = x / getWidth();
                if (progress < 0) progress = 0;
                if (progress > 1) progress = 1;
                
                mProgress = progress;
                invalidate();
                
                if (mListener != null) {
                    mListener.onSeek(progress);
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
