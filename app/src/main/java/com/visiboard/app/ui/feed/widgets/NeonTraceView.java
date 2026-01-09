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
    private final List<PointF> touchTrail = new ArrayList<>();
    private final Random random = new Random();
    private Paint starPaint;
    private Paint linePaint;
    private Paint trailPaint;
    private ValueAnimator animator;
    private float touchX = -1, touchY = -1;
    private boolean isTouching = false;
    
    // Theme Colors (Solid)
    private int[] darkBgs = {0xFF000000, 0xFF050510, 0xFF000814, 0xFF120021};
    // Neon generally looks best on dark, but providing light options too
    private int[] lightBgs = {0xFF212121, 0xFF000000, 0xFF102027}; // Forcing dark-ish for Neon even in Light mode? Or maybe Grey. User asked for "themed colors". Let's stick to Dark for space typically, or very deep Blue.
    // Actually, "app themed colors". If light mode, maybe a solid deep purple is better than white for stars.
    // But I'll use standard darks.
    
    private int currentBgColor = 0xFF000000;

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
        
        linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
        linePaint.setAntiAlias(true);
        linePaint.setStrokeWidth(1.5f);
        
        trailPaint = new Paint();
        trailPaint.setColor(0xFF00E5FF); // Cyan Glow
        trailPaint.setAntiAlias(true);
        trailPaint.setStrokeWidth(5f);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        
        // Initial Star Population
        for (int i = 0; i < 60; i++) {
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
        s.size = 2f + random.nextFloat() * 2f;
        s.alpha = 50 + random.nextInt(205);
        return s;
    }

    public void randomizeTheme() {
        int nightModeFlags = getContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int[] bgs = darkBgs; // Default to dark for stars
        
        currentBgColor = bgs[random.nextInt(bgs.length)];
        
        // Change trail color too
        trailPaint.setColor(Color.HSVToColor(new float[]{random.nextInt(360), 0.8f, 1.0f}));
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
             for (int i = 0; i < 60; i++) stars.add(createStar(true));
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
                touchTrail.add(new PointF(touchX, touchY));
                if (touchTrail.size() > 20) touchTrail.remove(0); // Limit trail size
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isTouching = false;
                touchTrail.clear();
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
            float speed = s.z * 1.0f;
            
            // Interaction: Warp Drive towards touch? Or Repel?
            // "Gravity Well" implementation
            if (isTouching) {
                float dx = touchX - s.x;
                float dy = touchY - s.y;
                float dist = (float) Math.sqrt(dx*dx + dy*dy);
                if (dist > 10) {
                    s.x += (dx / dist) * 2f * s.z;
                    s.y += (dy / dist) * 2f * s.z;
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
        while (stars.size() < 60) {
            stars.add(createStar(false)); // Spawn at top
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Solid Background
        canvas.drawColor(currentBgColor);
        
        // Draw Constellation Lines (Connect nearby stars)
        for (int i = 0; i < stars.size(); i++) {
            Star s1 = stars.get(i);
            for (int j = i + 1; j < stars.size(); j++) {
                Star s2 = stars.get(j);
                float dist = (float) Math.hypot(s1.x - s2.x, s1.y - s2.y);
                if (dist < 150) { // Connect if close
                    int alpha = (int) (100 * (1 - dist/150));
                    linePaint.setAlpha(alpha);
                    canvas.drawLine(s1.x, s1.y, s2.x, s2.y, linePaint);
                }
            }
        }
        
        // Draw Stars
        for (Star s : stars) {
            starPaint.setAlpha(s.alpha);
            canvas.drawCircle(s.x, s.y, s.size, starPaint);
        }
        
        // Draw Touch Trail
        if (!touchTrail.isEmpty()) {
            for (int i = 0; i < touchTrail.size() - 1; i++) {
                PointF p1 = touchTrail.get(i);
                PointF p2 = touchTrail.get(i+1);
                trailPaint.setAlpha((int)(255 * (float)i/touchTrail.size()));
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, trailPaint);
            }
        }
    }

    private static class Star {
        float x, y, z, size;
        int alpha;
    }
    
    private static class PointF {
        float x, y;
        PointF(float x, float y) { this.x = x; this.y = y; }
    }
}
