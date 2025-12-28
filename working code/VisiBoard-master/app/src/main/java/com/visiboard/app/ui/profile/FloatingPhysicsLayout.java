package com.visiboard.app.ui.profile;

import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FloatingPhysicsLayout extends FrameLayout {

    private final List<PhysicsEntity> entities = new ArrayList<>();
    private final Random random = new Random();
    private boolean isRunning = false;
    private long lastFrameTime = 0;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!isRunning) return;
            
            long currentTime = System.currentTimeMillis();
            if (lastFrameTime != 0) {
                float dt = (currentTime - lastFrameTime) / 1000f;
                // Cap dt to avoid large jumps if frame dropped
                updatePhysics(Math.min(dt, 0.05f));
            }
            lastFrameTime = currentTime;
            
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public FloatingPhysicsLayout(Context context) {
        super(context);
        init();
    }

    public FloatingPhysicsLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FloatingPhysicsLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(false);
    }

    public void startPhysics() {
        if (isRunning) return;
        isRunning = true;
        lastFrameTime = 0;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    public void stopPhysics() {
        isRunning = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPhysics();
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        // Initialize entity for new child
        // Position will be set in onLayout or explicitly
    }

    public void addFloatingView(View view) {
        addView(view);
        // Random usage or initial position logic can be handled after measurement
    }
    
    public void addFloatingView(View view, boolean isDecorative) {
        addView(view);
        if (view.getTag() == null) {
            view.setTag(isDecorative);
        }
    }
    
    // Call this after views are measured/laid out to init physics
    public void initializeEntities() {
        entities.clear();
        post(() -> {
            // Ensure views are measured
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getWidth() == 0 || child.getHeight() == 0) {
                    child.measure(
                        MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST)
                    );
                    child.layout(child.getLeft(), child.getTop(), 
                                child.getLeft() + child.getMeasuredWidth(),
                                child.getTop() + child.getMeasuredHeight());
                }
            }
            
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                PhysicsEntity entity = new PhysicsEntity(child);
                
                // Ensure width/height are valid
                if (entity.width <= 0) entity.width = child.getMeasuredWidth();
                if (entity.height <= 0) entity.height = child.getMeasuredHeight();
                if (entity.width <= 0) entity.width = child.getWidth();
                if (entity.height <= 0) entity.height = child.getHeight();
                
                // Check if this is a decorative icon (lighter physics)
                boolean isDecorative = child.getTag() != null && (Boolean) child.getTag();
                
                // Random initial velocity (pixels per second)
                // Decorative icons move slower and more gently
                float speed = isDecorative 
                    ? 15f + random.nextFloat() * 20f  // 15-35 px/s for decorative
                    : 30f + random.nextFloat() * 40f; // 30-70 px/s for notes
                double angle = random.nextDouble() * 2 * Math.PI;
                entity.vx = (float) (Math.cos(angle) * speed);
                entity.vy = (float) (Math.sin(angle) * speed);
                
                // Store decorative flag in entity
                entity.isDecorative = isDecorative;
                
                // Random start pos if 0
                if (child.getX() == 0 && child.getY() == 0) {
                    float maxX = Math.max(0, getWidth() - entity.width);
                    float maxY = Math.max(0, getHeight() - entity.height);
                    entity.x = random.nextFloat() * maxX;
                    entity.y = random.nextFloat() * maxY;
                } else {
                    entity.x = child.getX();
                    entity.y = child.getY();
                }
                
                // Initialize bounds immediately
                entity.bounds.set(entity.x, entity.y, entity.x + entity.width, entity.y + entity.height);
                
                // Random angular velocity (-30 to 30 degrees/sec, slower for decorative)
                entity.angularVelocity = isDecorative 
                    ? (random.nextFloat() * 20) - 10  // -10 to 10 for decorative
                    : (random.nextFloat() * 60) - 30; // -30 to 30 for notes
                entity.rotation = child.getRotation(); // Start with initial rotation
                
                entities.add(entity);
            }
            startPhysics();
        });
    }

    private void updatePhysics(float dt) {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0 || entities.isEmpty()) return;

        // Move Everything First
        for (PhysicsEntity e : entities) {
            // Ensure valid dimensions
            if (e.width <= 0) e.width = e.view.getWidth();
            if (e.height <= 0) e.height = e.view.getHeight();
            if (e.width <= 0 || e.height <= 0) continue;
            
            e.x += e.vx * dt;
            e.y += e.vy * dt;
            e.rotation += e.angularVelocity * dt;
            
            // Wall Bouncing (Instead of Wrap) - Keeps them on screen better
            if (e.x < 0) { e.x = 0; e.vx = Math.abs(e.vx); }
            else if (e.x > width - e.width) { e.x = width - e.width; e.vx = -Math.abs(e.vx); }
            
            if (e.y < 0) { e.y = 0; e.vy = Math.abs(e.vy); }
            else if (e.y > height - e.height) { e.y = height - e.height; e.vy = -Math.abs(e.vy); }

            // Update bounds immediately after position change
            e.bounds.set(e.x, e.y, e.x + e.width, e.y + e.height);
        }

        // Solve Collisions (Multiple Iterations for stability)
        // Decorative icons have lighter collision response
        for (int iter = 0; iter < 4; iter++) {
            for (int i = 0; i < entities.size(); i++) {
                PhysicsEntity e1 = entities.get(i);
                if (e1.width <= 0 || e1.height <= 0) continue;
                
                for (int j = i + 1; j < entities.size(); j++) {
                    PhysicsEntity e2 = entities.get(j);
                    if (e2.width <= 0 || e2.height <= 0) continue;

                    // Check if bounds actually overlap (more accurate check)
                    if (e1.bounds.right > e2.bounds.left && 
                        e1.bounds.left < e2.bounds.right &&
                        e1.bounds.bottom > e2.bounds.top && 
                        e1.bounds.top < e2.bounds.bottom) {
                        resolveCollision(e1, e2);
                    }
                }
            }
        }
        
        // Apply final positions
        for (PhysicsEntity e : entities) {
            if (e.view != null && e.width > 0 && e.height > 0) {
                e.view.setX(e.x);
                e.view.setY(e.y);
                e.view.setRotation(e.rotation);
            }
        }
    }

    private void resolveCollision(PhysicsEntity e1, PhysicsEntity e2) {
        // Calculate overlap
        float overlapX = Math.min(e1.bounds.right, e2.bounds.right) - Math.max(e1.bounds.left, e2.bounds.left);
        float overlapY = Math.min(e1.bounds.bottom, e2.bounds.bottom) - Math.max(e1.bounds.top, e2.bounds.top);
        
        // Lighter separation for decorative icons
        boolean bothDecorative = e1.isDecorative && e2.isDecorative;
        boolean oneDecorative = e1.isDecorative || e2.isDecorative;
        
        // Separate entities first (push apart)
        float separationFactor = bothDecorative ? 0.3f : (oneDecorative ? 0.4f : 0.6f);
        if (Math.abs(overlapX) < Math.abs(overlapY)) {
            // Resolve on X axis
            float push = overlapX * separationFactor;
            if (e1.x < e2.x) {
                e1.x -= push;
                e2.x += push;
            } else {
                e1.x += push;
                e2.x -= push;
            }
        } else {
            // Resolve on Y axis
            float push = overlapY * separationFactor;
            if (e1.y < e2.y) {
                e1.y -= push;
                e2.y += push;
            } else {
                e1.y += push;
                e2.y -= push;
            }
        }
        
        // Update bounds immediately
        e1.bounds.set(e1.x, e1.y, e1.x + e1.width, e1.y + e1.height);
        e2.bounds.set(e2.x, e2.y, e2.x + e2.width, e2.y + e2.height);
        
        // Elastic collision response (swap velocities with slight randomization to prevent sticking)
        float tempVx = e1.vx;
        float tempVy = e1.vy;
        
        // Add slight random component to prevent perfect alignment
        // Less jitter for decorative icons (gentler movement)
        float jitter = bothDecorative ? 2f : (oneDecorative ? 3f : 5f);
        e1.vx = e2.vx + (random.nextFloat() * 2 - 1) * jitter;
        e1.vy = e2.vy + (random.nextFloat() * 2 - 1) * jitter;
        
        e2.vx = tempVx + (random.nextFloat() * 2 - 1) * jitter;
        e2.vy = tempVy + (random.nextFloat() * 2 - 1) * jitter;
    }

    private static class PhysicsEntity {
        View view;
        float x, y;
        float vx, vy;
        float rotation;
        float angularVelocity;
        float width, height;
        RectF bounds = new RectF();
        boolean isDecorative = false; // Flag for decorative background icons

        PhysicsEntity(View view) {
            this.view = view;
            this.width = view.getWidth();
            this.height = view.getHeight();
            // X/Y initialized in loop or from view
        }
    }
}
