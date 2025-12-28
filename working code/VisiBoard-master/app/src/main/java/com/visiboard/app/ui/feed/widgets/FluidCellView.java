package com.visiboard.app.ui.feed.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Fluid Cell" Fidget.
 * A small, 1-column wide widget that simulates color diffusion.
 * Touching it spawns expanding, fading ripples of random color from a palette.
 */
public class FluidCellView extends View {

    private final List<Ripple> ripples = new ArrayList<>();
    private Paint paint;
    private ValueAnimator animator;
    private int[] palette;
    private Random random = new Random();

    // Palettes
    private static final int[][] PALETTES = {
        {0xFF00E5FF, 0xFF18FFFF, 0xFFE040FB}, // Cyberpunk (Cyan/Purple)
        {0xFFFF5252, 0xFFFFAB40, 0xFFFFD740}, // Magma (Red/Orange/Yellow)
        {0xFF76FF03, 0xFF69F0AE, 0xFFB2FF59}, // Slime (Green/Lime)
        {0xFF2979FF, 0xFF448AFF, 0xFF82B1FF}  // Ocean (Blue)
    };

    public FluidCellView(Context context) {
        super(context);
        init();
    }

    public FluidCellView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        
        // Pick random palette for this instance
        palette = PALETTES[random.nextInt(PALETTES.length)];
        
        // Animation Loop
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(1000); // Dummy duration, loop infinite
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(anim -> {
            updateRipples();
            invalidate();
        });
    }

    public void randomizeTheme() {
        palette = PALETTES[random.nextInt(PALETTES.length)];
        ripples.clear();
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
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            // Spawn ripple
            // Limit rate slightly
            if (ripples.isEmpty() || ripples.get(ripples.size()-1).radius > 20) {
                 int color = palette[random.nextInt(palette.length)];
                 ripples.add(new Ripple(event.getX(), event.getY(), color));
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateRipples() {
        List<Ripple> toRemove = new ArrayList<>();
        float maxRadius = Math.max(getWidth(), getHeight()) * 1.5f;
        
        for (Ripple r : ripples) {
            r.radius += 5f; // Expansion speed
            r.alpha -= 0.02f; // Fade speed
            
            if (r.alpha <= 0 || r.radius > maxRadius) {
                toRemove.add(r);
            }
        }
        ripples.removeAll(toRemove);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Background - very dark version of palette[0]
        int baseC = palette[0];
        int bg = Color.rgb(Color.red(baseC)/10, Color.green(baseC)/10, Color.blue(baseC)/10);
        canvas.drawColor(bg); // Dark tint
        
        for (Ripple r : ripples) {
            // Radial Gradient for soft look
            RadialGradient gradient = new RadialGradient(
                r.x, r.y, 
                Math.max(1f, r.radius), 
                new int[]{setAlpha(r.color, (int)(r.alpha * 255)), setAlpha(r.color, 0)}, 
                null, 
                Shader.TileMode.CLAMP
            );
            paint.setShader(gradient);
            canvas.drawCircle(r.x, r.y, r.radius, paint);
        }
    }
    
    private int setAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static class Ripple {
        float x, y, radius;
        int color;
        float alpha;

        Ripple(float x, float y, int color) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.radius = 0;
            this.alpha = 1.0f;
        }
    }
}
