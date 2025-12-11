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
import java.util.List;

/**
 * Gravity Ball Fidget.
 * NOW WITH MULTI-BALLS!
 */
public class GravityBallView extends View implements SensorEventListener {

    private Paint paint;
    private float xPos, yPos;
    private float xVel = 0, yVel = 0;
    private float xAccel = 0, yAccel = 0;
    private float ballRadius = 40f;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    
    // Trail
    private static class Point { float x, y; Point(float x, float y){this.x=x; this.y=y;} }
    private java.util.LinkedList<Point> trail = new java.util.LinkedList<>();
    private static final int TRAIL_LENGTH = 15;
    
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
    
    public void randomizeColor() {
        float[] hsv = new float[3];
        hsv[0] = (float) (Math.random() * 360);
        hsv[1] = 0.8f + (float)(Math.random() * 0.2f); // High Saturation
        hsv[2] = 1.0f; // Max Value
        themeColor = Color.HSVToColor(hsv);
        invalidate();
    }
    
    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
            randomizeColor();
            performClick();
            return true;
        }
        return true; // Consume event
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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        xPos = w / 2f;
        yPos = h / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Physics
        xVel -= xAccel * 0.8f; 
        yVel += yAccel * 0.8f;
        
        // Damping
        xVel *= 0.92f;
        yVel *= 0.92f;
        
        xPos += xVel;
        yPos += yVel;
        
        // Boundaries
        if (xPos < ballRadius) { xPos = ballRadius; xVel = -xVel * 0.6f; }
        if (xPos > getWidth() - ballRadius) { xPos = getWidth() - ballRadius; xVel = -xVel * 0.6f; }
        if (yPos < ballRadius) { yPos = ballRadius; yVel = -yVel * 0.6f; }
        if (yPos > getHeight() - ballRadius) { yPos = getHeight() - ballRadius; yVel = -yVel * 0.6f; }
        
        // Add to trail
        trail.addFirst(new Point(xPos, yPos));
        if (trail.size() > TRAIL_LENGTH) trail.removeLast();
        
        // Draw Trail
        for (int i = 0; i < trail.size(); i++) {
            Point p = trail.get(i);
            // Alpha fades
            int alpha = (int) (255 * (1.0f - (float)i / TRAIL_LENGTH));
            // Size shrinks
            float scale = 1.0f - ((float)i / TRAIL_LENGTH) * 0.5f;
            
            paint.setColor(themeColor);
            paint.setAlpha(alpha);
            canvas.drawCircle(p.x, p.y, ballRadius * scale, paint);
        }
        
        // Draw Core Ball
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        canvas.drawCircle(xPos, yPos, ballRadius * 0.4f, paint);

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
