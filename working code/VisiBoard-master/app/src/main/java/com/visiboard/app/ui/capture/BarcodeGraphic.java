package com.visiboard.app.ui.capture;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.Random;
import com.google.mlkit.vision.barcode.common.Barcode;

/**
 * Graphic instance for rendering Barcode position and content information in an overlay view.
 */
public class BarcodeGraphic extends GraphicOverlay.Graphic {

  private static final int TEXT_COLOR = Color.WHITE;
  private static final float TEXT_SIZE = 54.0f;
  private static final float STROKE_WIDTH = 10.0f;
  private static final float PADDING = 20.0f;

  private final Paint rectPaint;
  private final Paint barcodePaint;
  private final Paint backgroundPaint;
  private final Barcode barcode;
  private volatile RectF boundingBox; // For hit testing

  // Random vibrant colors
  private static final int[] COLORS = {
      Color.parseColor("#00E5FF"), // Cyan
      Color.parseColor("#76FF03"), // Green
      Color.parseColor("#FFD600"), // Amber
      Color.parseColor("#FF4081"), // Pink
      Color.parseColor("#D500F9")  // Purple
  };

  BarcodeGraphic(GraphicOverlay overlay, Barcode barcode) {
    super(overlay);

    this.barcode = barcode;
    
    // Pick random color
    int selectedColor = COLORS[new Random().nextInt(COLORS.length)];

    rectPaint = new Paint();
    rectPaint.setColor(selectedColor);
    rectPaint.setStyle(Paint.Style.STROKE);
    rectPaint.setStrokeWidth(STROKE_WIDTH);
    rectPaint.setStrokeCap(Paint.Cap.ROUND);
    rectPaint.setStrokeJoin(Paint.Join.ROUND);

    barcodePaint = new Paint();
    barcodePaint.setColor(TEXT_COLOR);
    barcodePaint.setTextSize(TEXT_SIZE);
    barcodePaint.setFakeBoldText(true);

    backgroundPaint = new Paint();
    backgroundPaint.setColor(Color.parseColor("#99000000")); // Semi-transparent black
    backgroundPaint.setStyle(Paint.Style.FILL);
    backgroundPaint.setPathEffect(new android.graphics.CornerPathEffect(16f)); 
  }
  
  public Barcode getBarcode() {
      return barcode;
  }
  
  public RectF getBoundingBox() {
      return boundingBox;
  }

  /**
   * Draws the barcode block annotations for position, size, and raw value on the supplied canvas.
   */
  @Override
  public void draw(Canvas canvas) {
    if (barcode == null) {
      throw new IllegalStateException("Attempting to draw a null barcode.");
    }

    // Draws the bounding box around the BarcodeBlock.
    RectF rect = new RectF(barcode.getBoundingBox());
    // If the image is flipped, the left will be translated to right, and the right to
    // left. This swaps the left and right coordinates of the rect.
    float x0 = translateX(rect.left);
    float x1 = translateX(rect.right);
    rect.left = Math.min(x0, x1);
    rect.right = Math.max(x0, x1);
    rect.top = translateY(rect.top);
    rect.bottom = translateY(rect.bottom);
    
    this.boundingBox = rect; // Store for hit testing
    
    canvas.drawRect(rect, rectPaint);

    // Draws other object info.
    String text = barcode.getRawValue();
    if (text != null) {
        float textWidth = barcodePaint.measureText(text);
        float lineHeight = TEXT_SIZE + (2 * STROKE_WIDTH);
        
        // Draw background for text (centered below rect)
        float textX = rect.centerX() - (textWidth / 2);
        float textY = rect.bottom + lineHeight;
        
        RectF textBg = new RectF(
            textX - PADDING,
            textY - TEXT_SIZE - PADDING/2,
            textX + textWidth + PADDING,
            textY + PADDING/2
        );
        
        // Draw the text background
        canvas.drawRoundRect(textBg, 16f, 16f, backgroundPaint);
        
        // Draw text
        canvas.drawText(text, textX, textY, barcodePaint);
    }
  }
}
