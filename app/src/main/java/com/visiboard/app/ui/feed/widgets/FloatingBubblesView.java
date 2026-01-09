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

public class FloatingBubblesView extends View {

    private final List<Bubble> bubbles = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<ScorePopup> scorePopups = new ArrayList<>();
    private final Random random = new Random();
    private Paint paint;
    private ValueAnimator animator;
    private long lastSpawnTime = 0;
    private int totalScore = 0;
    private float flashIntensity = 0f;
    private int currentBgColor = 0;
    
    private int[] darkBgs = {0xFF212121, 0xFF263238, 0xFF37474F, 0xFF121212};
    private int[] lightBgs = {0xFFF5F5F5, 0xFFECEFF1, 0xFFFAFAFA, 0xFFEEEEEE};
    
    // Bubble Class
    private static class Bubble {
        float x, y;
        float radius;
        float speed;
        int color;
        int alpha = 200;
        boolean isPopped = false;
    }
    
    // Pop Particle
    private static class Particle {
        float x, y;
        float vx, vy;
        float size;
        int alpha = 255;
        int color;
    }
    
    // Score Popup
    private static class ScorePopup {
        float x, y;
        int score;
        int alpha = 255;
        float scale = 1.0f;
    }

    public FloatingBubblesView(Context context) {
        super(context);
        init();
    }

    public FloatingBubblesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            update();
            invalidate();
        });
    }

    private void update() {
        long currentTime = System.currentTimeMillis();
        
        // Spawn new bubbles
        if (currentTime - lastSpawnTime > 300) { // Spawn every 300ms
             if (bubbles.size() < 12) { // Max bubbles
                 spawnBubble();
                 lastSpawnTime = currentTime;
             }
        }
        
        // Update Bubbles
        Iterator<Bubble> iterator = bubbles.iterator();
        while (iterator.hasNext()) {
            Bubble b = iterator.next();
            
            if (b.isPopped) {
                iterator.remove();
            } else {
                // Move Right & Upwards slightly
                b.x += b.speed;
                b.y -= 0.5f; // Float up
                // Wiggle Y
                b.y += (float) Math.sin(b.x / 60f) * 1.0f; 
                
                // Remove if off screen
                if (b.x - b.radius > getWidth()) {
                    iterator.remove();
                }
            }
        }
        
        // Update Particles
        Iterator<Particle> pIter = particles.iterator();
        while(pIter.hasNext()) {
            Particle p = pIter.next();
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.5f; // Gravity
            p.alpha -= 10;
            if (p.alpha <= 0) pIter.remove();
        }
        
        // Update Score Popups
        Iterator<ScorePopup> sIter = scorePopups.iterator();
        while(sIter.hasNext()) {
            ScorePopup s = sIter.next();
            s.y -= 2f; // Float up
            s.alpha -= 5;
            s.scale += 0.02f;
            if (s.alpha <= 0) sIter.remove();
        }
        
        // Decay Flash smoothly
        if (flashIntensity > 0) {
            flashIntensity -= 0.02f; // Smooth fade out
            if (flashIntensity < 0) flashIntensity = 0;
        }
    }
    
    private void spawnBubble() {
        Bubble b = new Bubble();
        b.radius = 40f + random.nextFloat() * 50f; // 40-90 radius
        b.x = -b.radius * 2; // Start off screen left
        b.y = b.radius + random.nextFloat() * (getHeight() - b.radius * 2);
        b.speed = 1.5f + random.nextFloat() * 2.5f; // 1.5-4 speed
        
        // Vibrant Colors
        int[] colors = {0xFF4FC3F7, 0xFFBA68C8, 0xFF81C784, 0xFFFFD54F, 0xFFFF8A65, 0xFF4DB6AC};
        b.color = colors[random.nextInt(colors.length)];
        
        bubbles.add(b);
    }
    
    private void createPopEffect(float x, float y, int color, int score) {
        // Particles
        for(int i=0; i<8; i++) {
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            double angle = random.nextDouble() * 2 * Math.PI;
            float speed = 5f + random.nextFloat() * 8f;
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = (float) Math.sin(angle) * speed;
            p.size = 5f + random.nextFloat() * 6f;
            p.color = color;
            particles.add(p);
        }
        
        // Score Popup
        ScorePopup sp = new ScorePopup();
        sp.x = x;
        sp.y = y;
        sp.score = score;
        scorePopups.add(sp);
        
        totalScore += score;
        flashIntensity = 0.3f; // Set flash
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Theme-aware Background (Solid)
        int nightModeFlags = getContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        
        if (currentBgColor == 0) {
            int[] bgs = isDark ? darkBgs : lightBgs;
            currentBgColor = bgs[random.nextInt(bgs.length)];
        }
        
        canvas.drawColor(currentBgColor);
        
        // Flash Overlay
        if (flashIntensity > 0) {
            paint.setColor(Color.WHITE);
            paint.setAlpha((int)(flashIntensity * 255));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
        
        paint.setShader(null); // Reset
        
        // Draw Bubbles
        for (Bubble b : bubbles) {
            // Main Bubble
            paint.setColor(b.color);
            paint.setAlpha(100);
            canvas.drawCircle(b.x, b.y, b.radius, paint);
            
            // Iridescent Rim
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(Color.WHITE);
            paint.setAlpha(150);
            canvas.drawCircle(b.x, b.y, b.radius, paint);
            paint.setStyle(Paint.Style.FILL);
            
            // Reflection (Shine)
            paint.setColor(Color.WHITE);
            paint.setAlpha(180);
            canvas.drawOval(b.x - b.radius*0.5f, b.y - b.radius*0.6f, 
                            b.x - b.radius*0.2f, b.y - b.radius*0.4f, paint);
        }
        
        // Draw Particles
        for (Particle p : particles) {
            paint.setColor(p.color);
            paint.setAlpha(p.alpha);
            canvas.drawCircle(p.x, p.y, p.size, paint);
        }
        
        // Draw Score Popups
        paint.setColor(Color.WHITE);
        paint.setTextSize(50f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        for (ScorePopup s : scorePopups) {
            paint.setAlpha(s.alpha);
            paint.setTextSize(50f * s.scale);
            canvas.drawText("+" + s.score, s.x, s.y, paint);
        }
        
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float tx = event.getX();
            float ty = event.getY();
            
            // Check Hits in Reverse (top ones first)
            for (int i = bubbles.size() - 1; i >= 0; i--) {
                Bubble b = bubbles.get(i);
                if (!b.isPopped) {
                    float dist = (float) Math.hypot(tx - b.x, ty - b.y);
                    if (dist < b.radius + 30) { // Forgiving hitbox
                        b.isPopped = true;
                        int score = (int)(1000 / b.radius); // Smaller = Higher Score
                        createPopEffect(b.x, b.y, b.color, score);
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        return true;
                    }
                }
            }
        }
        return super.onTouchEvent(event);
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator.start();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animator.cancel();
    }
}
