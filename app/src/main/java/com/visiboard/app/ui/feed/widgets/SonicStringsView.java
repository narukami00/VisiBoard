package com.visiboard.app.ui.feed.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * "Sonic Strings" Fidget.
 * Simulates elastic strings that can be plucked.
 * Uses spring physics for satisfying bounce-back.
 */
public class SonicStringsView extends View {

    private final List<GuitarString> strings = new ArrayList<>();
    private Paint paint;
    private ValueAnimator animator;
    private int activeStringIndex = -1;

    public SonicStringsView(Context context) {
        super(context);
        init();
    }

    public SonicStringsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
        // Neon-ish colors
        int[] colors = {0xFFFF4081, 0xFF00E5FF, 0xFFFFD740, 0xFF76FF03}; 

        // Create 4 strings
        for (int i = 0; i < 4; i++) {
            strings.add(new GuitarString(colors[i % colors.length]));
        }

        // Physics Loop (60 FPS approx)
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            boolean needsInvalidate = false;
            for (GuitarString s : strings) {
                if (s.update()) needsInvalidate = true;
            }
            if (activeStringIndex != -1 || needsInvalidate) invalidate();
        });
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null && !animator.isRunning()) animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float spacing = h / 5f;
        for (int i = 0; i < strings.size(); i++) {
            strings.get(i).yBase = spacing * (i + 1);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // Find closest string
                float minIso = Float.MAX_VALUE;
                int closest = -1;
                
                for (int i = 0; i < strings.size(); i++) {
                    GuitarString s = strings.get(i);
                    float d = Math.abs(y - s.yBase);
                    if (d < 100 && d < minIso) { // threshold
                        minIso = d;
                        closest = i;
                    }
                }
                
                if (closest != -1) {
                    activeStringIndex = closest;
                    GuitarString s = strings.get(closest);
                    // Displace
                    s.displacement = y - s.yBase;
                    // Cap displacement
                    s.displacement = Math.max(-100, Math.min(100, s.displacement));
                    s.velocity = 0; // Hold it
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeStringIndex != -1) {
                    // Release!
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    activeStringIndex = -1;
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        
        for (GuitarString s : strings) {
            paint.setColor(s.color);
            
            Path p = new Path();
            p.moveTo(0, s.yBase);
            // Quadratic Bezier for string shape
            // Control point is middle width, yBase + displacement
            // But Bezier curve passes through half the control point distance roughly. 
            // So we multiply displacement by 2 to make it touch the finger visually approx.
            p.quadTo(w / 2f, s.yBase + (s.displacement * 2), w, s.yBase);
            
            canvas.drawPath(p, paint);
        }
    }
    
    private static class GuitarString {
        float yBase;
        float displacement = 0;
        float velocity = 0;
        int color;
        
        // Physics constants
        final float k = 0.2f; // Spring stiffness
        final float damping = 0.85f; // Energy loss

        GuitarString(int color) {
            this.color = color;
        }

        boolean update() {
            if (displacement == 0 && velocity == 0) return false;
            
            // F = -kx (Hooks Law)
            float force = -k * displacement;
            
            // a = F (mass = 1)
            // v += a
            velocity += force;
            
            // Damping
            velocity *= damping;
            
            // Update pos
            displacement += velocity;
            
            // Stop if small
            if (Math.abs(displacement) < 0.5 && Math.abs(velocity) < 0.5) {
                displacement = 0;
                velocity = 0;
            }
            return true;
        }
    }
}
