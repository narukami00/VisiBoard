package com.visiboard.app.ui.feed.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Gravity Ball Fidget Game.
 * Use the gravity ball to destroy floating space debris!
 */
public class GravityBallView extends View implements SensorEventListener {

    private Paint paint;
    private float xPos, yPos;
    private float xVel = 0, yVel = 0;
    private float xAccel = 0, yAccel = 0;
    private float ballRadius = 40f;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    
    // Game Elements
    private List<Debris> debrisList = new ArrayList<>();
    private List<Particle> particles = new ArrayList<>();
    private Random random = new Random();
    private int score = 0;
    private long lastDebrisSpawn = 0;
    
    // Trail
    private static class Point { float x, y; Point(float x, float y){this.x=x; this.y=y;} }
    private java.util.LinkedList<Point> trail = new java.util.LinkedList<>();
    private static final int TRAIL_LENGTH = 12;
    
    private int themeColor = 0xFF00E5FF; // Cyan Default

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
    }
    
    public void randomizeTheme() {
        float[] hsv = new float[3];
        hsv[0] = (float) (Math.random() * 360);
        hsv[1] = 0.8f + (float)(Math.random() * 0.2f); // High Saturation
        hsv[2] = 1.0f; // Max Value
        themeColor = Color.HSVToColor(hsv);
        // Also clear screen for fun
        score = 0;
        debrisList.clear();
        invalidate();
    }
    
    public void randomize() {
        randomizeTheme();
    }
    
    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            // Tap to shockwave/push debris away? Or just randomize theme
            randomizeTheme();
            createExplosion(event.getX(), event.getY(), 10, Color.WHITE);
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }
    
    @Override
    public boolean performClick() {
        return super.performClick();
    }
    
    public void startSensor() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }
    
    public void stopSensor() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    // Stars
    private static class Star { float x, y, size; int alpha; }
    private List<Star> stars = new ArrayList<>();
    private final int STAR_COUNT = 60;
    
    // Debris Target
    private static class Debris {
        float x, y;
        float radius;
        float vx, vy;
        int color;
        int health;
    }
    
    // Explosion Particle
    private static class Particle {
        float x, y;
        float vx, vy;
        int color;
        int alpha = 255;
        float size;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        xPos = w / 2f;
        yPos = h / 2f;
        
        // Init Stars
        stars.clear();
        for(int i=0; i<STAR_COUNT; i++) {
            Star s = new Star();
            s.x = random.nextFloat() * w;
            s.y = random.nextFloat() * h;
            s.size = 2f + random.nextFloat() * 4f;
            s.alpha = random.nextInt(255);
            stars.add(s);
        }
    }
    
    private void spawnDebris() {
        Debris d = new Debris();
        // Spawn from edges
        if (random.nextBoolean()) {
            d.x = random.nextBoolean() ? -50 : getWidth() + 50;
            d.y = random.nextFloat() * getHeight();
        } else {
            d.x = random.nextFloat() * getWidth();
            d.y = random.nextBoolean() ? -50 : getHeight() + 50;
        }
        
        d.radius = 20f + random.nextFloat() * 20f;
        
        // Velocity towards center (roughly)
        float angle = (float) Math.atan2(getHeight()/2f - d.y, getWidth()/2f - d.x);
        float speed = 1f + random.nextFloat() * 2f;
        angle += (random.nextFloat() - 0.5f) * 0.5f; // Randomize angle slightly
        
        d.vx = (float) Math.cos(angle) * speed;
        d.vy = (float) Math.sin(angle) * speed;
        
        // Random Pastel Color
        d.color = Color.HSVToColor(new float[]{random.nextInt(360), 0.6f, 0.9f});
        d.health = 1;
        
        debrisList.add(d);
    }
    
    private void createExplosion(float x, float y, int count, int color) {
        for(int i=0; i<count; i++) {
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            double angle = random.nextDouble() * 2 * Math.PI;
            float speed = 2f + random.nextFloat() * 5f;
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = (float) Math.sin(angle) * speed;
            p.color = color;
            p.size = 3f + random.nextFloat() * 5f;
            particles.add(p);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw Space Background Gradient
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, getHeight(), 
            0xFF0B0B15, 0xFF1A1A2E, android.graphics.Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
           canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null); // Reset
        
        // Draw Stars
        for (Star s : stars) {
            paint.setColor(Color.WHITE);
            // Twinkle
            if (Math.random() > 0.98) s.alpha = random.nextInt(255);
            paint.setAlpha(s.alpha);
            canvas.drawCircle(s.x, s.y, s.size, paint);
        }
        
        // Update & Draw Debris
        long now = System.currentTimeMillis();
        if (now - lastDebrisSpawn > 800 && debrisList.size() < 8) {
            spawnDebris();
            lastDebrisSpawn = now;
        }
        
        Iterator<Debris> debIter = debrisList.iterator();
        while(debIter.hasNext()) {
            Debris d = debIter.next();
            d.x += d.vx;
            d.y += d.vy;
            
            // Check Collision with Ball
            float dist = (float) Math.hypot(d.x - xPos, d.y - yPos);
            if (dist < d.radius + ballRadius) {
                // Destroy!
                createExplosion(d.x, d.y, 8, d.color);
                score++;
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                debIter.remove();
                continue;
            }
            
            // Remove if way off screen
            if (d.x < -100 || d.x > getWidth()+100 || d.y < -100 || d.y > getHeight()+100) {
                debIter.remove();
                continue;
            }
            
            paint.setColor(d.color);
            paint.setAlpha(220);
            canvas.drawCircle(d.x, d.y, d.radius, paint);
            
            // Draw core
            paint.setColor(Color.WHITE);
            paint.setAlpha(100);
            canvas.drawCircle(d.x, d.y, d.radius * 0.3f, paint);
        }
        
        // Update & Draw Particles (Explosions)
        Iterator<Particle> partIter = particles.iterator();
        while(partIter.hasNext()) {
            Particle p = partIter.next();
            p.x += p.vx;
            p.y += p.vy;
            p.alpha -= 10; // Fade faster
            
            if (p.alpha <= 0) {
                partIter.remove();
                continue;
            }
            
            paint.setColor(p.color);
            paint.setAlpha(p.alpha);
            canvas.drawCircle(p.x, p.y, p.size, paint);
        }
        
        // Physics for Gravity Ball
        xVel -= xAccel * 0.9f; 
        yVel += yAccel * 0.9f;
        
        // Damping
        xVel *= 0.94f;
        yVel *= 0.94f;
        
        xPos += xVel;
        yPos += yVel;
        
        // Boundaries
        if (xPos < ballRadius) { xPos = ballRadius; xVel = -xVel * 0.5f; }
        if (xPos > getWidth() - ballRadius) { xPos = getWidth() - ballRadius; xVel = -xVel * 0.5f; }
        if (yPos < ballRadius) { yPos = ballRadius; yVel = -yVel * 0.5f; }
        if (yPos > getHeight() - ballRadius) { yPos = getHeight() - ballRadius; yVel = -yVel * 0.5f; }
        
        // Add to trail
        trail.addFirst(new Point(xPos, yPos));
        if (trail.size() > TRAIL_LENGTH) trail.removeLast();
        
        // Draw Trail (Neon Glow)
        for (int i = 0; i < trail.size(); i++) {
            Point p = trail.get(i);
            int alpha = (int) (200 * (1.0f - (float)i / TRAIL_LENGTH));
            float scale = 1.0f - ((float)i / TRAIL_LENGTH) * 0.6f;
            
            paint.setColor(themeColor);
            paint.setAlpha(alpha);
            canvas.drawCircle(p.x, p.y, ballRadius * scale, paint);
        }
        
        // Draw Core Ball (Glowing)
        paint.setColor(themeColor);
        paint.setAlpha(100); // Glow
        canvas.drawCircle(xPos, yPos, ballRadius + 10, paint);
        
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        canvas.drawCircle(xPos, yPos, ballRadius * 0.5f, paint);

        invalidate(); 
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
    
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); stopSensor(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); startSensor(); }
}
