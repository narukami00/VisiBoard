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
    private final Random random = new Random();
    private Paint paint;
    private ValueAnimator animator;
    private long lastSpawnTime = 0;
    
    // Bubble Class
    private static class Bubble {
        float x, y;
        float radius;
        float speed;
        int color;
        int alpha = 200;
        boolean isPopped = false;
        long popTime = 0;
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
             if (bubbles.size() < 15) { // Max bubbles
                 spawnBubble();
                 lastSpawnTime = currentTime;
             }
        }
        
        Iterator<Bubble> iterator = bubbles.iterator();
        while (iterator.hasNext()) {
            Bubble b = iterator.next();
            
            if (b.isPopped) {
                // Popping animation (scale down quickly)
                b.radius -= 2f;
                b.alpha -= 20;
                if (b.radius <= 0 || b.alpha <= 0) {
                    iterator.remove();
                }
            } else {
                // Move Right
                b.x += b.speed;
                // Wiggle Y
                b.y += (float) Math.sin(b.x / 50f) * 1.5f; 
                
                // Remove if off screen
                if (b.x - b.radius > getWidth()) {
                    iterator.remove();
                }
            }
        }
    }
    
    private void spawnBubble() {
        Bubble b = new Bubble();
        b.radius = 30f + random.nextFloat() * 40f; // 30-70 radius
        b.x = -b.radius * 2; // Start off screen left
        b.y = b.radius + random.nextFloat() * (getHeight() - b.radius * 2);
        b.speed = 2f + random.nextFloat() * 3f; // 2-5 speed
        
        // Pastel Colors
        int[] colors = {0xFFB3E5FC, 0xFFE1BEE7, 0xFFC8E6C9, 0xFFFFF9C4, 0xFFFFCCBC};
        b.color = colors[random.nextInt(colors.length)];
        
        bubbles.add(b);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        for (Bubble b : bubbles) {
            paint.setColor(b.color);
            paint.setAlpha(b.alpha);
            canvas.drawCircle(b.x, b.y, b.radius, paint);
            
            // Draw shine
            paint.setColor(Color.WHITE);
            paint.setAlpha((int)(b.alpha * 0.6));
            canvas.drawCircle(b.x - b.radius/3, b.y - b.radius/3, b.radius/4, paint);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float tx = event.getX();
            float ty = event.getY();
            
            // Check Hits
            for (Bubble b : bubbles) {
                if (!b.isPopped) {
                    float dist = (float) Math.hypot(tx - b.x, ty - b.y);
                    if (dist < b.radius + 20) { // +20 hitbox forgiveness
                        b.isPopped = true;
                        performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
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
