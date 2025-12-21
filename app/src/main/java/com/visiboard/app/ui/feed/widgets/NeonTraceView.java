package com.visiboard.app.ui.feed.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Formerly "Neon Trace", now "Cosmic Void" / "Starfield".
 * Displays an ambient field of stars that drift and react to touch.
 * Solves the "Black Box" issue by always having content.
 */
public class NeonTraceView extends View {

    private final List<Star> stars = new ArrayList<>();
    private final Random random = new Random();
    private Paint starPaint;
    private ValueAnimator animator;
    private float touchX = -1, touchY = -1;
    private boolean isTouching = false;
    
    // Theme Colors (Nebula tints)
    private int[] nebulaColors = {
        0xFF1A237E, // Deep Indigo
        0xFF311B92, // Deep Purple
        0xFF006064, // Cyan Dark
        0xFF263238  // Blue Grey
    };
    private int baseColor = 0xFF000000;

    public NeonTraceView(Context context) {
        super(context);
        init();
    }

    public NeonTraceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        starPaint = new Paint();
        starPaint.setColor(Color.WHITE);
        starPaint.setAntiAlias(true);
        starPaint.setStyle(Paint.Style.FILL);
        
        // Initial Star Population
        for (int i = 0; i < 80; i++) {
             stars.add(createStar(true));
        }

        // Animation Loop
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(anim -> {
            updateStars();
            invalidate();
        });
        
        randomizeTheme(); // Set initial background
    }
    
    // Create a random star
    private Star createStar(boolean randomY) {
        Star s = new Star();
        s.x = random.nextFloat() * getWidth();
        s.y = randomY ? random.nextFloat() * getHeight() : 0; // Top or Random
        s.z = 0.5f + random.nextFloat(); // Depth/Speed
        s.size = 1f + random.nextFloat() * 3f;
        s.alpha = 50 + random.nextInt(205);
        return s;
    }

    public void randomizeTheme() {
        int colorIndex = random.nextInt(nebulaColors.length);
        baseColor = nebulaColors[colorIndex];
        // Make it very dark for background
        int r = Color.red(baseColor) / 5;
        int g = Color.green(baseColor) / 5;
        int b = Color.blue(baseColor) / 5;
        baseColor = Color.rgb(r, g, b);
        
        // Reset stars? Nah, they just drift over new void.
        invalidate();
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
        stars.clear(); // Free mem
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (stars.isEmpty()) {
             for (int i = 0; i < 80; i++) stars.add(createStar(true));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        touchX = event.getX();
        touchY = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                isTouching = true;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isTouching = false;
                break;
        }
        return true;
    }

    private void updateStars() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        Iterator<Star> it = stars.iterator();
        while (it.hasNext()) {
            Star s = it.next();
            
            // Standard drift down
            float speed = s.z * 1.5f;
            
            // Interaction: Warp Drive towards touch? Or Repel?
            // "Gravity Well" implementation
            if (isTouching) {
                float dx = touchX - s.x;
                float dy = touchY - s.y;
                float dist = (float) Math.sqrt(dx*dx + dy*dy);
                if (dist > 10) {
                    s.x += (dx / dist) * 5f * s.z;
                    s.y += (dy / dist) * 5f * s.z;
                } else {
                    // Sucked in! Respawn
                    it.remove();
                    continue; // Skip rest
                }
            } else {
                 s.y += speed;
            }
            
            // Twinkle
            if (random.nextFloat() < 0.05f) {
                s.alpha = 50 + random.nextInt(205);
            }

            // Boundary check
            if (s.y > h || s.x < 0 || s.x > w) {
                it.remove();
            }
        }
        
        // Replenish
        while (stars.size() < 80) {
            stars.add(createStar(false)); // Spawn at top
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(baseColor); // Deep Space Background
        
        for (Star s : stars) {
            starPaint.setAlpha(s.alpha);
            canvas.drawCircle(s.x, s.y, s.size, starPaint);
        }
    }

    private static class Star {
        float x, y, z, size;
        int alpha;
    }
}
