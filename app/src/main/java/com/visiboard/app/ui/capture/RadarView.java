package com.visiboard.app.ui.capture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class RadarView extends View {

    private Paint circlePaint;
    private Paint dotPaint;
    private Paint userPaint;
    private List<RadarDot> dots = new ArrayList<>();
    private float maxDistance = 100f; // Default max distance in meters

    public static class RadarDot {
        float distance; // meters
        float bearing; // degrees relative to North
        int color;

        public RadarDot(float distance, float bearing, int color) {
            this.distance = distance;
            this.bearing = bearing;
            this.color = color;
        }
    }

    public RadarView(Context context) {
        super(context);
        init();
    }

    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        circlePaint = new Paint();
        circlePaint.setColor(Color.parseColor("#40000000")); // Semi-transparent black
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setAntiAlias(true);

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setAntiAlias(true);

        dotPaint = new Paint();
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);
        
        userPaint = new Paint();
        userPaint.setColor(Color.WHITE);
        userPaint.setStyle(Paint.Style.FILL);
        userPaint.setAntiAlias(true);
    }

    public void setMaxDistance(float maxDistance) {
        this.maxDistance = maxDistance;
        invalidate();
    }

    public void setDots(List<RadarDot> dots) {
        this.dots = dots;
        invalidate();
    }
    
    // Update user's heading to rotate the radar
    private float currentAzimuth = 0;
    public void setAzimuth(float azimuth) {
        this.currentAzimuth = azimuth;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int radius = Math.min(width, height) / 2;
        int centerX = width / 2;
        int centerY = height / 2;

        // Draw radar background
        canvas.drawCircle(centerX, centerY, radius, circlePaint);
        
        // Draw border
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setAntiAlias(true);
        canvas.drawCircle(centerX, centerY, radius, borderPaint);
        
        // Draw center (user)
        canvas.drawCircle(centerX, centerY, 5, userPaint);

        // Draw dots
        for (RadarDot dot : dots) {
            // Calculate position
            // Normalize distance
            float distRatio = Math.min(1.0f, dot.distance / maxDistance);
            float drawDist = distRatio * (radius - 10); // Leave some padding
            
            // Adjust bearing based on user's heading (North is up)
            // If user is facing North (0), dot at North (0) should be at top (-90 deg in standard math)
            // Standard math: 0 is Right. Android Canvas: 0 is Right.
            // We want North to be Up.
            // If bearing is 0 (North), and Azimuth is 0 (North), dot should be at top (270 deg).
            // Angle = dotBearing - currentAzimuth - 90
            
            float angle = dot.bearing - currentAzimuth - 90;
            double angleRad = Math.toRadians(angle);
            
            float x = centerX + (float) (drawDist * Math.cos(angleRad));
            float y = centerY + (float) (drawDist * Math.sin(angleRad));
            
            dotPaint.setColor(dot.color);
            canvas.drawCircle(x, y, 8, dotPaint);
        }
    }
}
