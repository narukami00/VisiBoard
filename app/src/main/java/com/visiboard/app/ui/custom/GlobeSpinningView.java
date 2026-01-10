package com.visiboard.app.ui.custom;

import com.visiboard.app.R;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.Nullable;

public class GlobeSpinningView extends View {

    private Paint paint;
    private Paint glowPaint;
    private Bitmap mapBitmap;
    private BitmapShader bitmapShader;
    private Matrix shaderMatrix = new Matrix();
    private ValueAnimator animator;
    private int primaryColor;
    private float animationValue = 0f;

    // Mesh Data
    private static final int GRID_ROWS = 24; // Latitude steps
    private static final int GRID_COLS = 24; // Longitude steps
    private float[] verts;
    private float[] texs;
    private short[] indices;
    private int indexCount;

    public GlobeSpinningView(Context context) {
        super(context);
        init();
    }

    public GlobeSpinningView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GlobeSpinningView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Paint color is largely irrelevant when using a shader without vertex colors, 
        // but we rely on the shader's pixel colors.
        
        // Resolve primary color
        TypedValue typedValue = new TypedValue();
        getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        primaryColor = typedValue.data;

        // Setup Glow/Outline Paint
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setColor(primaryColor);
        glowPaint.setStrokeWidth(dpToPx(1.5f));
        // Soft outer glow
        glowPaint.setShadowLayer(dpToPx(6), 0, 0, primaryColor);
        // Important: hardware acceleration can sometimes mess up shadowLayer on lines, 
        // but for a simple circle it usually works or requires VIEW_TYPE_SOFTWARE if needed.
        // We'll try default first.

        // Load and Bitmap Process
        Bitmap original = BitmapFactory.decodeResource(getResources(), R.drawable.world_map_texture);
        if (original != null) {
            // Process pixels: Dark -> Transparent, Light -> Primary Color
            mapBitmap = original.copy(Bitmap.Config.ARGB_8888, true);
            int w = mapBitmap.getWidth();
            int h = mapBitmap.getHeight();
            int[] pixels = new int[w * h];
            mapBitmap.getPixels(pixels, 0, w, 0, 0, w, h);

            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                // Brightness
                if ((0.299 * r + 0.587 * g + 0.114 * b) > 100) {
                    pixels[i] = primaryColor; // Land
                } else {
                    pixels[i] = Color.TRANSPARENT; // Ocean
                }
            }
            mapBitmap.setPixels(pixels, 0, w, 0, 0, w, h);

            // Create Shader
            bitmapShader = new BitmapShader(mapBitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
            paint.setShader(bitmapShader);
        }

        // Animator: 0 to 1 represents full texture width scroll
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(12000); // 12s rotation
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(val -> {
            animationValue = (float) val.getAnimatedValue();
            invalidate();
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        generateMesh(w, h);
    }

    private void generateMesh(float w, float h) {
        if (mapBitmap == null) return;
        
        int vertexCount = (GRID_ROWS + 1) * (GRID_COLS + 1);
        verts = new float[vertexCount * 2];
        texs = new float[vertexCount * 2];
        
        // We calculate indices for triangles
        // Each grid cell is 2 triangles (6 indices)
        int cellCount = GRID_ROWS * GRID_COLS;
        indexCount = cellCount * 6;
        indices = new short[indexCount];

        float centerX = w / 2f;
        float centerY = h / 2f;
        float radius = Math.min(centerX, centerY) * 0.9f; // Leave some padding

        // Texture Mapping:
        // The texture is in equirectangular projection (plate carrée)
        // We need to reverse-map from sphere coordinates (phi, lambda) to texture coords (u, v)
        float bmW = mapBitmap.getWidth();
        float bmH = mapBitmap.getHeight();

        int vIndex = 0; // vertex index counter

        for (int r = 0; r <= GRID_ROWS; r++) {
            // Latitude Phi: -PI/2 to PI/2
            float phi = (float) (Math.PI * ((float)r / GRID_ROWS - 0.5f));
            // y coord on screen (projected sphere)
            // y = -R * sin(phi)
            // SHAPE: Circle (Removed stretch)
            float y = centerY - radius * (float)Math.sin(phi);

            for (int c = 0; c <= GRID_COLS; c++) {
                // Longitude Lambda: -PI/2 to PI/2 (The visible face)
                float lambda = (float) (Math.PI * ((float)c / GRID_COLS - 0.5f));
                
                // Orthographic Projection x
                // x = R * cos(phi) * sin(lambda)
                float x = centerX + radius * (float)Math.cos(phi) * (float)Math.sin(lambda);
                
                // Store Screen Coords
                verts[vIndex * 2] = x;
                verts[vIndex * 2 + 1] = y;

                // Store Texture Coords
                // Proper equirectangular mapping:
                // u = (longitude + PI) / (2*PI) maps -PI...PI to 0...1
                // v = (PI/2 - latitude) / PI maps -PI/2...PI/2 to 0...1
                // Texture (0,0) is Top-Left. North (+PI/2) should map to v=0.
                float uNorm = (lambda + (float)Math.PI) / (2f * (float)Math.PI);
                float vNorm = ((float)Math.PI/2f - phi) / (float)Math.PI;
                
                texs[vIndex * 2] = uNorm * bmW;
                texs[vIndex * 2 + 1] = vNorm * bmH;

                vIndex++;
            }
        }

        // Generate Indices (Triangle Strip logic converted to Triangles list for drawVertices)
        int iIndex = 0;
        int colsPlus1 = GRID_COLS + 1;
        for (int r = 0; r < GRID_ROWS; r++) {
            for (int c = 0; c < GRID_COLS; c++) {
                // 4 corners of the cell
                short tl = (short) (r * colsPlus1 + c);
                short tr = (short) (tl + 1);
                short bl = (short) ((r + 1) * colsPlus1 + c);
                short br = (short) (bl + 1);

                // Triangle 1 (TL, TR, BL)
                indices[iIndex++] = tl;
                indices[iIndex++] = tr;
                indices[iIndex++] = bl;

                // Triangle 2 (TR, BR, BL)
                indices[iIndex++] = tr;
                indices[iIndex++] = br;
                indices[iIndex++] = bl;
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null && !animator.isStarted()) animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            if (animator != null && !animator.isStarted()) animator.start();
        } else {
            if (animator != null) animator.cancel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (verts == null || bitmapShader == null) return;

        // Animate Texture Translation
        // We move the texture to the left (negative x translate) to simulate globe rotation
        float translateX = -animationValue * mapBitmap.getWidth();
        
        // Use identity scale - no stretching needed with proper UV mapping
        shaderMatrix.setTranslate(translateX, 0);
        
        bitmapShader.setLocalMatrix(shaderMatrix);

        // Draw the mesh
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, 
            verts.length, verts, 0, 
            texs, 0, 
            null, 0, // no color array
            indices, 0, indexCount, 
            paint);
            
        // Draw Glow/Outline
        // SHAPE: Circle (Removed stretch)
        float radius = Math.min(getWidth(), getHeight()) / 2f * 0.9f;
        
        // Disable hardware accel for shadow layer if needed, but let's try just drawing.
        canvas.drawCircle(getWidth()/2f, getHeight()/2f, radius, glowPaint);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
