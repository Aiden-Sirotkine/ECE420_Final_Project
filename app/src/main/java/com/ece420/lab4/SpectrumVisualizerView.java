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
        mForePaint.setAntiAlias(true);
        mForePaint.setStyle(Paint.Style.FILL);
        
        mGridPaint.setColor(Color.argb(30, 255, 255, 255));
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
        // Draw background grid/line
        float centerY = getHeight() / 2f;
        canvas.drawLine(0, centerY, getWidth(), centerY, mGridPaint);

        if (mWaveformSamples == null) {
            return;
        }
        
        // Draw full waveform
        int width = getWidth();
        int step = mWaveformSamples.length / width;
        if (step < 1) step = 1;
        
        mForePaint.setShader(null);
        mForePaint.setColor(0xFF2DD4BF); // Cyan
        mForePaint.setStrokeWidth(2f);
        
        // Draw simple envelope
        float[] pts = new float[width * 4];
        int ptr = 0;
        
        for (int i = 0; i < width; i++) {
            int idx = i * step;
            if (idx >= mWaveformSamples.length) break;
            
            short val = mWaveformSamples[idx];
            float normalized = (float) val / Short.MAX_VALUE;
            float h = normalized * (getHeight() / 2f);
            
            pts[ptr++] = i;
            pts[ptr++] = centerY - h;
            pts[ptr++] = i;
            pts[ptr++] = centerY + h;
        }
        canvas.drawLines(pts, 0, ptr, mForePaint);
        
        // Draw progress indicator
        float progressX = mProgress * width;
        mForePaint.setColor(0xFFFFFFFF); // White
        mForePaint.setStrokeWidth(4f);
        canvas.drawLine(progressX, 0, progressX, getHeight(), mForePaint);
        
        // Draw glass-like overlay on played part
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
