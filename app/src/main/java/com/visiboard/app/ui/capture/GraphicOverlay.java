package com.visiboard.app.ui.capture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * A view which renders a series of custom graphics to be overlaid on top of an associated preview
 * (i.e., the camera preview). The content is scaled and translated to match the preview.
 */
public class GraphicOverlay extends View {
  private final Object lock = new Object();
  private final List<Graphic> graphics = new ArrayList<>();
  // Matrix for transforming from image coordinates to overlay view coordinates.
  private final Matrix transformationMatrix = new Matrix();

  public List<Graphic> getGraphics() {
      synchronized (lock) {
          return new ArrayList<>(graphics);
      }
  }

  private int imageWidth;
  private int imageHeight;
  // The factor of overlay View ROI to image ROI.
  private float scaleFactor = 1.0f;
  // The number of horizontal pixels needed to be cropped on each side to fit the image with the
  // area of overlay View after scaling.
  private float postScaleWidthOffset;
  // The number of vertical pixels needed to be cropped on each side to fit the image with the
  // area of overlay View after scaling.
  private float postScaleHeightOffset;
  private boolean isImageFlipped;

  public GraphicOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  /**
   * Base class for a custom graphics object to be rendered within the GraphicOverlay. Subclass
   * this and implement the {@link Graphic#draw(Canvas)} method to define the
   * graphics element. Add instances to the overlay using {@link GraphicOverlay#add(Graphic)}.
   */
  public abstract static class Graphic {
    private GraphicOverlay overlay;

    public Graphic(GraphicOverlay overlay) {
      this.overlay = overlay;
    }

    /**
     * Draw the graphic on the supplied canvas. Drawing should use the following methods to
     * convert to view coordinates for the graphics that are drawn:
     * <ol>
     * <li>{@link Graphic#scale(float)} adjusts the size of the supplied value from the image
     * scale to the view scale.</li>
     * <li>{@link Graphic#translateX(float)} and {@link Graphic#translateY(float)} adjust the
     * coordinate from the image's coordinate system to the view coordinate system.</li>
     * </ol>
     *
     * @param canvas drawing canvas
     */
    public abstract void draw(Canvas canvas);

    /**
     * Adjusts a horizontal value of the supplied value from the preview scale to the view
     * scale.
     */
    public float scale(float imagePixel) {
      return imagePixel * overlay.scaleFactor;
    }

    /**
     * Returns the application context of the overlay.
     */
    public Context getApplicationContext() {
      return overlay.getContext().getApplicationContext();
    }

    /**
     * Adjusts the x coordinate from the preview's coordinate system to the view coordinate
     * system.
     */
    public float translateX(float x) {
      if (overlay.isImageFlipped) {
        return overlay.getWidth() - (scale(x) - overlay.postScaleWidthOffset);
      } else {
        return scale(x) - overlay.postScaleWidthOffset;
      }
    }

    /**
     * Adjusts the y coordinate from the preview's coordinate system to the view coordinate
     * system.
     */
    public float translateY(float y) {
      return scale(y) - overlay.postScaleHeightOffset;
    }
  }

  /**
   * Removes all graphics from the overlay.
   */
  public void clear() {
    synchronized (lock) {
      graphics.clear();
    }
    postInvalidate();
  }

  /**
   * Adds a graphic to the overlay.
   */
  public void add(Graphic graphic) {
    synchronized (lock) {
      graphics.add(graphic);
    }
    postInvalidate();
  }

  /**
   * Sets the image info for transforms.
   */
  public void setImageSourceInfo(int imageWidth, int imageHeight, boolean isFlipped) {
    synchronized (lock) {
      this.imageWidth = imageWidth;
      this.imageHeight = imageHeight;
      this.isImageFlipped = isFlipped;
    }
    postInvalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    synchronized (lock) {
      if ((imageWidth != 0) && (imageHeight != 0)) {
        float viewAspectRatio = (float) getWidth() / getHeight();
        float imageAspectRatio = (float) imageWidth / imageHeight;

        // The image needs to be scaled to fit with the area of overlay View
        if (viewAspectRatio > imageAspectRatio) {
          // The image is wider than the view. Scale to fit height and crop width.
          scaleFactor = (float) getWidth() / imageWidth;
          postScaleWidthOffset = 0;
          postScaleHeightOffset = ((float) getWidth() / imageAspectRatio - getHeight()) / 2;
        } else {
          // The image is taller than the view. Scale to fit width and crop height.
          scaleFactor = (float) getHeight() / imageHeight;
          postScaleWidthOffset = ((float) getHeight() * imageAspectRatio - getWidth()) / 2;
          postScaleHeightOffset = 0;
        }
      }

      for (Graphic graphic : graphics) {
        graphic.draw(canvas);
      }
    }
  }
}
