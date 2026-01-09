package com.visiboard.app.ui.feed.widgets;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Gravity Ball Fidget Game.
 * Features:
 * - Accelerometer-controlled ball (Original Physics).
 * - Floating debris targets.
 * - "Black Hole" reset animation: Tapping creates a singularity that consumes everything.
 */
public class GravityBallView extends View implements SensorEventListener {

    private enum GameState {
        PLAYING,
        BH_FORMING,    // Expanding
        BH_CONSUMING,  // Eating entities
        BH_COLLAPSING  // Reforming into ball
    }

    private GameState currentState = GameState.PLAYING;

    // Ball properties
    private float xPos, yPos;
    private float xVel = 0, yVel = 0;
    private float xAccel = 0, yAccel = 0;
    private float ballRadius = 40f;
    private int themeColor = 0xFF00E5FF; // Default Cyan
    private LinkedList<Point> trail = new LinkedList<>();
    private static final int TRAIL_LENGTH = 15;

    // Black Hole properties
    private float bhX, bhY;
    private float bhRadius = 0;
    private float bhMaxRadius = 0;
    private ValueAnimator bhAnimator;

    // Objects
    private List<Debris> debris = new ArrayList<>();
    private List<Particle> particles = new ArrayList<>();
    private List<Star> stars = new ArrayList<>();
    private long lastDebrisSpawn = 0;

    // System
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Paint paint;
    private Random random = new Random();
    private int score = 0;

    public GravityBallView(Context context) {
        super(context);
        init();
    }

    public GravityBallView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);

        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        
        // Initial Stars
        for (int i = 0; i < 60; i++) {
             stars.add(createStar());
        }
    }
    
    private Star createStar() {
        Star s = new Star();
        s.x = random.nextFloat() * 1000; 
        s.y = random.nextFloat() * 2000;
        s.size = 2 + random.nextFloat() * 4;
        s.alpha = 100 + random.nextInt(155);
        return s;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (bhAnimator != null) bhAnimator.cancel();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (xPos == 0 && yPos == 0) {
            xPos = w / 2f;
            yPos = h / 2f;
        }
        stars.clear();
        for(int i=0; i<60; i++) {
            Star s = createStar();
            s.x = random.nextFloat() * w;
            s.y = random.nextFloat() * h;
            stars.add(s);
        }
        bhMaxRadius = Math.min(w, h) * 0.4f;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            xAccel = event.values[0];
            yAccel = event.values[1];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (currentState == GameState.PLAYING) {
                // Trigger Black Hole
                startBlackHoleSequence(event.getX(), event.getY());
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
    
    // --- Black Hole Logic ---
    
    private void startBlackHoleSequence(float x, float y) {
        currentState = GameState.BH_FORMING;
        bhX = x;
        bhY = y;
        
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        
        if (bhAnimator != null) bhAnimator.cancel();
        bhAnimator = ValueAnimator.ofFloat(0, bhMaxRadius);
        bhAnimator.setDuration(800);
        bhAnimator.setInterpolator(new DecelerateInterpolator());
        bhAnimator.addUpdateListener(anim -> {
            bhRadius = (float) anim.getAnimatedValue();
            invalidate();
        });
        bhAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = GameState.BH_CONSUMING;
                postDelayed(() -> {
                     if (currentState == GameState.BH_CONSUMING) collapseBlackHole();
                }, 4000); 
            }
        });
        bhAnimator.start();
    }
    
    private void collapseBlackHole() {
        currentState = GameState.BH_COLLAPSING;
        
        randomizeTheme();
        score = 0;
        
        if (bhAnimator != null) bhAnimator.cancel();
        bhAnimator = ValueAnimator.ofFloat(bhRadius, 0); 
        bhAnimator.setDuration(600);
        bhAnimator.setInterpolator(new AccelerateInterpolator());
        bhAnimator.addUpdateListener(anim -> {
            bhRadius = (float) anim.getAnimatedValue();
            invalidate();
        });
        bhAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = GameState.PLAYING;
                xPos = bhX; 
                yPos = bhY;
                xVel = 0; yVel = 0;
                replenishDebris();
                performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            }
        });
        bhAnimator.start();
    }

    public void randomizeTheme() {
        float[] hsv = {random.nextInt(360), 1.0f, 1.0f};
        themeColor = Color.HSVToColor(hsv);
        trail.clear();
        debris.clear(); 
    }
    
    // Original Spawning Logic
    private void spawnDebris() {
        Debris d = new Debris();
        if (random.nextBoolean()) {
            d.x = random.nextBoolean() ? -50 : getWidth() + 50;
            d.y = random.nextFloat() * getHeight();
        } else {
            d.x = random.nextFloat() * getWidth();
            d.y = random.nextBoolean() ? -50 : getHeight() + 50;
        }
        
        d.radius = 20f + random.nextFloat() * 20f;
        
        // Velocity towards center (Original Logic)
        float angle = (float) Math.atan2(getHeight()/2f - d.y, getWidth()/2f - d.x);
        float speed = 1f + random.nextFloat() * 2f;
        angle += (random.nextFloat() - 0.5f) * 0.5f; 
        
        d.vx = (float) Math.cos(angle) * speed;
        d.vy = (float) Math.sin(angle) * speed;
        
        d.color = Color.HSVToColor(new float[]{random.nextInt(360), 0.6f, 0.9f});
        debris.add(d);
    }
    
    private void replenishDebris() {
        while (debris.size() < 5) {
             spawnDebris();
        }
    }
    
    private void createExplosion(float x, float y, int count, int color) {
        for(int i=0; i<count; i++) {
            Particle p = new Particle();
            p.x = x; p.y = y;
            double angle = random.nextDouble() * 2 * Math.PI;
            float speed = 2f + random.nextFloat() * 5f;
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = (float) Math.sin(angle) * speed;
            p.size = 3f + random.nextFloat() * 5f;
            p.alpha = 255;
            p.color = color;
            particles.add(p);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Background - Keeping Solid as per previous requests for "no gradient"
        canvas.drawColor(0xFF0D0D0D); 
        
        // Stars with Lensing
        paint.setStyle(Paint.Style.FILL);
        for (Star s : stars) {
             float drawX = s.x;
             float drawY = s.y;
             
             if (currentState != GameState.PLAYING && bhRadius > 10) {
                 float dx = s.x - bhX;
                 float dy = s.y - bhY;
                 float dist = (float) Math.hypot(dx, dy);
                 
                 if (dist < bhRadius * 2.5f && dist > bhRadius) {
                     float force = (bhRadius * 2.5f - dist) / (bhRadius * 2.5f);
                     float push = force * 60f; 
                     float angle = (float) Math.atan2(dy, dx);
                     drawX += Math.cos(angle) * push;
                     drawY += Math.sin(angle) * push;
                 }
                 if (dist < bhRadius) continue; 
             }
             
             paint.setColor(Color.WHITE);
             // Twinkle
             if (random.nextFloat() > 0.98f) s.alpha = random.nextInt(255);
             paint.setAlpha(s.alpha);
             canvas.drawCircle(drawX, drawY, s.size, paint);
        }
        
        // Spawn debris periodically in Playing state
        long now = System.currentTimeMillis();
        if (currentState == GameState.PLAYING && now - lastDebrisSpawn > 800 && debris.size() < 8) {
            spawnDebris();
            lastDebrisSpawn = now;
        }
        
        boolean allConsumed = true;
        
        Iterator<Debris> debIter = debris.iterator();
        while (debIter.hasNext()) {
            Debris d = debIter.next();
            
            if (currentState == GameState.PLAYING) {
                // Original Physics: Velocity based
                d.x += d.vx;
                d.y += d.vy;
                
                float dist = (float) Math.hypot(d.x - xPos, d.y - yPos);
                if (dist < d.radius + ballRadius) {
                    createExplosion(d.x, d.y, 8, d.color);
                    score++;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    debIter.remove();
                    continue;
                }
                
                // Remove if way off screen
                if (d.x < -100 || d.x > getWidth()+100 || d.y < -100 || d.y > getHeight()+100) {
                    debIter.remove();
                    continue;
                }
            } else {
                allConsumed = false;
                // Black Hole Suction
                float dx = bhX - d.x;
                float dy = bhY - d.y;
                float dist = (float) Math.hypot(dx, dy);
                
                if (dist < bhRadius) {
                    debIter.remove(); 
                    continue;
                }
                
                float pull = 2000f / (dist + 1f); 
                if (pull > 50) pull = 50; 
                
                float angle = (float) Math.atan2(dy, dx);
                d.x += Math.cos(angle) * pull;
                d.y += Math.sin(angle) * pull;
                
                d.x += Math.cos(angle + Math.PI/2) * (pull * 0.5f); 
                d.y += Math.sin(angle + Math.PI/2) * (pull * 0.5f);
            }
            
            paint.setColor(d.color);
            paint.setAlpha(220);
            canvas.drawCircle(d.x, d.y, d.radius, paint);
        }
        
        Iterator<Particle> partIter = particles.iterator();
        while(partIter.hasNext()) {
            Particle p = partIter.next();
            if (currentState == GameState.PLAYING) {
                p.x += p.vx;
                p.y += p.vy;
                p.alpha -= 10;
            } else {
                float dx = bhX - p.x;
                float dy = bhY - p.y;
                float dist = (float) Math.hypot(dx, dy);
                if (dist < bhRadius) { p.alpha = 0; }
                else {
                    float angle = (float) Math.atan2(dy, dx);
                    p.x += Math.cos(angle) * 30f;
                    p.y += Math.sin(angle) * 30f;
                }
            }
            if (p.alpha <= 0) {
                partIter.remove();
                continue;
            }
            paint.setColor(p.color);
            paint.setAlpha(p.alpha);
            canvas.drawCircle(p.x, p.y, p.size, paint);
        }
        
        if (currentState == GameState.PLAYING) {
            // Original Physics Logic
            xVel -= xAccel * 0.9f; 
            yVel += yAccel * 0.9f;
            
            xVel *= 0.94f; // Original Damping
            yVel *= 0.94f;
            
            xPos += xVel;
            yPos += yVel;
            
            // Original Wall Bounce (-0.5 factor implicitly via WALL_BOUNCE var, actually user code used 0.5 manually?)
            // User code: if (xPos < ballRadius) ... xVel = -xVel * 0.5f;
            // My Constants: WALL_BOUNCE = 0.5f? (I'll define it)
            float bounce = 0.5f;
            if (xPos < ballRadius) { xPos = ballRadius; xVel = -xVel * bounce; }
            if (xPos > getWidth() - ballRadius) { xPos = getWidth() - ballRadius; xVel = -xVel * bounce; }
            if (yPos < ballRadius) { yPos = ballRadius; yVel = -yVel * bounce; }
            if (yPos > getHeight() - ballRadius) { yPos = getHeight() - ballRadius; yVel = -yVel * bounce; }

            trail.addFirst(new Point((int)xPos, (int)yPos));
            if (trail.size() > TRAIL_LENGTH) trail.removeLast();
        } else {
             // Sucked in
             float dx = bhX - xPos;
             float dy = bhY - yPos;
             float dist = (float) Math.hypot(dx, dy);
             
             if (dist > bhRadius) {
                 allConsumed = false;
                 float pull = 50f; 
                 float angle = (float) Math.atan2(dy, dx);
                 xPos += Math.cos(angle) * pull;
                 yPos += Math.sin(angle) * pull;
                 xPos += Math.cos(angle + Math.PI/2) * 15f; 
                 yPos += Math.sin(angle + Math.PI/2) * 15f;
                 trail.addFirst(new Point((int)xPos, (int)yPos));
                 if (trail.size() > TRAIL_LENGTH/2) trail.removeLast();
             } else {
                 trail.clear();
             }
        }
        
        if (currentState == GameState.BH_CONSUMING && allConsumed && debris.isEmpty()) {
            collapseBlackHole();
        }

        if (currentState == GameState.PLAYING || (currentState != GameState.PLAYING && Math.hypot(bhX-xPos, bhY-yPos) > bhRadius)) {
             for (int i = 0; i < trail.size(); i++) {
                Point p = trail.get(i);
                int alpha = (int) (200 * (1.0f - (float)i / trail.size()));
                float scale = 1.0f - ((float)i / trail.size()) * 0.6f;
                paint.setColor(themeColor);
                paint.setAlpha(alpha);
                canvas.drawCircle(p.x, p.y, ballRadius * scale, paint);
            }
            paint.setColor(themeColor);
            paint.setAlpha(100); 
            canvas.drawCircle(xPos, yPos, ballRadius + 10, paint); 
            paint.setColor(Color.WHITE);
            paint.setAlpha(255);
            canvas.drawCircle(xPos, yPos, ballRadius * 0.5f, paint); 
        }
        
        if (currentState != GameState.PLAYING) {
            paint.setShader(new RadialGradient(bhX, bhY, bhRadius * 1.5f + 1, 
                new int[]{0x00000000, themeColor, 0x00000000}, 
                new float[]{0.3f, 0.7f, 1.0f}, Shader.TileMode.CLAMP));
            paint.setAlpha(200);
            canvas.drawCircle(bhX, bhY, bhRadius * 1.5f, paint);
            paint.setShader(null);
            
            paint.setColor(Color.BLACK);
            paint.setAlpha(255);
            paint.setShadowLayer(20, 0, 0, themeColor); 
            canvas.drawCircle(bhX, bhY, bhRadius, paint);
            paint.setShadowLayer(0,0,0,0);
        }

        invalidate(); 
    }
    
    // Classes
    private static class Debris { float x, y, radius, vx, vy; int color; } // Added vx,vy
    private static class Particle { float x, y, vx, vy, size; int alpha, color; }
    private static class Star { float x, y, size; int alpha; }
}
