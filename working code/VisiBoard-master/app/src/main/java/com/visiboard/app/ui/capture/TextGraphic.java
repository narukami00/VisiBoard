package com.visiboard.app.ui.capture;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import com.google.mlkit.vision.text.Text;

/**
 * Graphic instance for rendering TextBlock position and content information in an overlay view.
 */
public class TextGraphic extends GraphicOverlay.Graphic {

  private static final int TEXT_COLOR = Color.RED;
  private static final float TEXT_SIZE = 54.0f;
  private static final float STROKE_WIDTH = 5.0f;

  private final Paint rectPaint;
  private final Paint textPaint;
  private final Text.TextBlock textBlock;
  private volatile RectF boundingBox;

  TextGraphic(GraphicOverlay overlay, Text.TextBlock text) {
    super(overlay);

    this.textBlock = text;

    rectPaint = new Paint();
    rectPaint.setColor(TEXT_COLOR);
    rectPaint.setStyle(Paint.Style.STROKE);
    rectPaint.setStrokeWidth(STROKE_WIDTH);

    textPaint = new Paint();
    textPaint.setColor(TEXT_COLOR);
    textPaint.setTextSize(TEXT_SIZE);
  }
  
  public Text.TextBlock getTextBlock() {
      return textBlock;
  }
  
  public RectF getBoundingBox() {
      return boundingBox;
  }

  @Override
  public void draw(Canvas canvas) {
    if (textBlock == null) {
      throw new IllegalStateException("Attempting to draw a null text.");
    }

    RectF rect = new RectF(textBlock.getBoundingBox());
    float x0 = translateX(rect.left);
    float x1 = translateX(rect.right);
    rect.left = Math.min(x0, x1);
    rect.right = Math.max(x0, x1);
    rect.top = translateY(rect.top);
    rect.bottom = translateY(rect.bottom);
    
    this.boundingBox = rect;
    
    canvas.drawRect(rect, rectPaint);
    
    // Optionally draw the text above the box for reference
    // float lineHeight = TEXT_SIZE + (2 * STROKE_WIDTH);
    // canvas.drawText(textBlock.getText(), rect.left, rect.bottom + lineHeight, textPaint);
  }
}
