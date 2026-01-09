package com.visiboard.app.ui.feed.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A "Lava Lamp" style fidget widget.
 * Simulates rising blobs with elegant gradients.
 */
public class LavaLampView extends View {

    private final List<Blob> blobs = new ArrayList<>();
    private Paint paint;
    private Paint bgPaint;
    private ValueAnimator animator;
    private Random random = new Random();
    
    // Theme Palettes (Solid Colors)
    // Dark Mode Backgrounds
    private int[] darkBgs = {0xFF120024, 0xFF000000, 0xFF1A237E, 0xFF263238}; 
    private int darkBlob = 0xFFD500F9; // Neon Purple/Pink
    
    // Light Mode Backgrounds
    private int[] lightBgs = {0xFFFFF3E0, 0xFFECEFF1, 0xFFF3E5F5, 0xFFE0F7FA};
    private int lightBlob = 0xFFFF5722; // Deep Orange

    private float hueOffset = 0;
    private int currentBgColor;

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
        
        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);

        // create initial blobs
        for (int i = 0; i < 7; i++) {
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
        
        // Tap to shift hue/randomize
        setOnClickListener(v -> {
            randomizeTheme();
            // Pop effect
            for (Blob b : blobs) {
                 b.radius += 5;
            }
            performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        });
    }
    
    public void randomizeTheme() {
        hueOffset = random.nextInt(360);
        
        // Pick new random background
        int nightModeFlags = getContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int[] bgs = isDark ? darkBgs : lightBgs;
        currentBgColor = bgs[random.nextInt(bgs.length)];
        
        invalidate();
    }
    
    public void randomize() {
        randomizeTheme();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null && !animator.isRunning()) animator.start();
        
        // Init color
        randomizeTheme();
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
            blob.x += Math.sin(blob.y * 0.015 + blob.phase) * 1.8;

            // Reset if goes off top
            if (blob.y < -blob.radius * 2) {
                resetBlob(blob);
            }
        }
    }
    
    // Reset a blob to the bottom
    private void resetBlob(Blob blob) {
        blob.radius = 50 + random.nextFloat() * 50; 
        blob.x = random.nextFloat() * getWidth();
        blob.y = getHeight() + blob.radius;
        blob.speed = 1.5f + random.nextFloat() * 2.5f; 
        blob.phase = random.nextFloat() * 100;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        for (Blob blob : blobs) {
            if (blob.y == 0) { 
                blob.x = random.nextFloat() * w;
                blob.y = h + random.nextFloat() * h * 0.5f; 
                resetBlob(blob); 
                blob.y = random.nextFloat() * h; 
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int nightModeFlags = getContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        
        // Ensure color is set if 0 (first run)
        if (currentBgColor == 0) {
             int[] bgs = isDark ? darkBgs : lightBgs;
             currentBgColor = bgs[0];
        }
        
        int blobBase = isDark ? darkBlob : lightBlob;
        
        // Apply Hue Offset if user tapped
        if (hueOffset != 0) {
            float[] hsv = new float[3];
            Color.colorToHSV(blobBase, hsv);
            hsv[0] = (hsv[0] + hueOffset) % 360;
            blobBase = Color.HSVToColor(hsv);
        }

        // Draw Solid Background
        canvas.drawColor(currentBgColor);
        
        // Draw Blobs
        paint.setColor(blobBase);
        
        for (Blob blob : blobs) {
            // Slight transparency for "goo" feel
            paint.setAlpha(200); 
            canvas.drawCircle(blob.x, blob.y, blob.radius, paint);
            
            // Highlight/Reflection
            Paint highlight = new Paint();
            highlight.setColor(Color.WHITE);
            highlight.setAlpha(50);
            canvas.drawCircle(blob.x - blob.radius/3, blob.y - blob.radius/3, blob.radius/4, highlight);
        }
    }

    private static class Blob {
        float x, y, radius, speed, phase;
    }
}
