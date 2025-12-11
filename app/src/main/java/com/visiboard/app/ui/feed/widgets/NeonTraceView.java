package com.visiboard.app.ui.feed.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A "Neon Trace" fidget.
 * Allows user to draw glowing lines that fade away over time.
 */
public class NeonTraceView extends View {

    private static final long FADE_DURATION = 1500; // ms to fully fade
    
    private final List<TracePoint> points = new ArrayList<>();
    private Paint paint;
    private Paint glowPaint;
    private boolean isDrawing = false;
    
    // Neon Colors
    private int[] neonColors = {
        0xFF00E5FF, // Cyan
        0xFFE040FB, // Purple
        0xFF76FF03, // Lime
        0xFFFFEA00  // Yellow
    };
    private int currentColor = neonColors[0];

    public NeonTraceView(Context context) {
        super(context);
        init();
    }

    public NeonTraceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Main line paint
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
        
        // Glow effect (simulated with wider semi-transparent stroke)
        glowPaint = new Paint();
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(25f);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setAntiAlias(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDrawing = true;
                currentColor = neonColors[(int)(Math.random() * neonColors.length)];
                addPoint(x, y);
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                invalidate();
                return true;
                
            case MotionEvent.ACTION_MOVE:
                addPoint(x, y);
                invalidate();
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDrawing = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void addPoint(float x, float y) {
        points.add(new TracePoint(x, y, currentColor));
        
        // Remove old points if too many (performance)
        if (points.size() > 500) {
            points.remove(0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Dark background
        canvas.drawColor(0xFF111111);

        long currentTime = System.currentTimeMillis();
        
        // Draw all live points
        // We draw as segments to handle per-point alpha/color
        
        Iterator<TracePoint> it = points.iterator();
        TracePoint prev = null;
        
        while (it.hasNext()) {
            TracePoint p = it.next();
            long life = currentTime - p.timestamp;
            
            if (life > FADE_DURATION) {
                it.remove();
                continue;
            }
            
            // Calculate alpha based on life
            float alphaNorm = 1.0f - ((float) life / FADE_DURATION);
            int alphaFn = (int) (alphaNorm * 255);
            
            if (prev != null) {
                // Draw segment from prev to p
                
                // Glow
                glowPaint.setColor(p.color);
                glowPaint.setAlpha((int)(alphaFn * 0.3f)); // Fainter glow
                canvas.drawLine(prev.x, prev.y, p.x, p.y, glowPaint);
                
                // Core
                paint.setColor(p.color);
                paint.setAlpha(alphaFn);
                canvas.drawLine(prev.x, prev.y, p.x, p.y, paint);
            }
            
            prev = p;
        }
        
        if (!points.isEmpty()) {
            // Request next frame to animate fading even if not touching
            invalidate();
        }
    }

    private static class TracePoint {
        float x, y;
        int color;
        long timestamp;

        TracePoint(float x, float y, int color) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
