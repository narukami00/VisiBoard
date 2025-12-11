package com.visiboard.app.ui.feed.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A "Lava Lamp" style fidget widget.
 * Simulates rising blobs that merge using a metaball-like effect 
 * (approximated here with gooey connections for performance).
 */
public class LavaLampView extends View {

    private final List<Blob> blobs = new ArrayList<>();
    private Paint paint;
    private ValueAnimator animator;
    private Random random = new Random();
    
    // Colors for the "Lava"
    private int[] colors = {
        0xFFFF5252, // Red
        0xFFFF4081, // Pink
        0xFFFF6E40, // Deep Orange
        0xFFFFD740  // Amber
    };
    
    private int currentColorIndex = 0;

    public LavaLampView(Context context) {
        super(context);
        init();
    }

    public LavaLampView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setColor(colors[0]);

        // Create initial blobs
        for (int i = 0; i < 6; i++) {
            blobs.add(new Blob());
        }

        // Animation loop
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            updateBlobs();
            invalidate();
        });
        
        // Tap to change color
        setOnClickListener(v -> {
            currentColorIndex = (currentColorIndex + 1) % colors.length;
            paint.setColor(colors[currentColorIndex]);
            // Add a "pop" effect
            for (Blob b : blobs) {
                b.radius += 10; 
            }
            performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
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

    private void updateBlobs() {
        for (Blob blob : blobs) {
            blob.y -= blob.speed;
            
            // "Wobble" logic
            blob.x += Math.sin(blob.y * 0.02 + blob.phase) * 1.5;

            // Reset if goes off top
            if (blob.y < -blob.radius * 2) {
                resetBlob(blob);
            }
            
            // Shrink slowly as it rises to simulate cooling/stretching potentially
            // blob.radius = Math.max(20, blob.radius - 0.05f);
        }
    }
    
    // Reset a blob to the bottom
    private void resetBlob(Blob blob) {
        blob.radius = 40 + random.nextFloat() * 40; // 40 to 80
        blob.x = random.nextFloat() * getWidth();
        blob.y = getHeight() + blob.radius;
        blob.speed = 1 + random.nextFloat() * 2; // 1 to 3 pixels per frame
        blob.phase = random.nextFloat() * 100;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        for (Blob blob : blobs) {
            if (blob.y == 0) { // Initial placement relative to height
                blob.x = random.nextFloat() * w;
                blob.y = h + random.nextFloat() * 200; // Staggered start below
                resetBlob(blob); // Proper init
                blob.y = h + random.nextFloat() * h; // Spread out initially
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw background container implied? No, simpler to draw blobs on transparent.
        // Actually, let's draw connections (Metaball approx)
        // For simple, optimized "Lava", we just draw circles. 
        // Real metaballs are expensive in Java Canvas without shaders.
        // We can do a "Gooey" effect by drawing a path connecting close blobs.
        
        // Simple circle approach first for performance/smoothness
        for (Blob blob : blobs) {
            canvas.drawCircle(blob.x, blob.y, blob.radius, paint);
        }
    }

    private class Blob {
        float x, y, radius, speed, phase;
    }
}
