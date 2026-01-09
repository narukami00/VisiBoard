package com.visiboard.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class for image processing operations.
 * Handles EXIF orientation correction and other image transformations.
 */
public class ImageUtils {

    /**
     * Loads a bitmap from a Uri and corrects its orientation based on EXIF data.
     * This fixes the issue where camera-captured images appear rotated.
     *
     * @param context The context
     * @param imageUri The Uri of the image
     * @return The correctly oriented bitmap, or null if loading fails
     */
    public static Bitmap loadBitmapWithCorrectOrientation(Context context, Uri imageUri) {
        try {
            // First, decode the bitmap
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) return null;
            
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            
            if (bitmap == null) return null;
            
            // Get the EXIF orientation
            InputStream exifStream = context.getContentResolver().openInputStream(imageUri);
            if (exifStream == null) return bitmap;
            
            ExifInterface exif = new ExifInterface(exifStream);
            int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            );
            exifStream.close();
            
            // Rotate based on orientation
            int rotation = getRotationFromExif(orientation);
            if (rotation != 0) {
                bitmap = rotateBitmap(bitmap, rotation);
            }
            
            return bitmap;
            
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Gets the rotation degrees from EXIF orientation value.
     */
    private static int getRotationFromExif(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            case ExifInterface.ORIENTATION_NORMAL:
            default:
                return 0;
        }
    }
    
    /**
     * Rotates a bitmap by the specified degrees.
     *
     * @param bitmap The source bitmap
     * @param degrees The rotation in degrees (90, 180, 270)
     * @return The rotated bitmap
     */
    public static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees == 0 || bitmap == null) {
            return bitmap;
        }
        
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        
        Bitmap rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, 
            bitmap.getWidth(), bitmap.getHeight(), 
            matrix, true
        );
        
        // Recycle the original bitmap if a new one was created
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        
        return rotatedBitmap;
    }
    
    /**
     * Scales down a bitmap if it exceeds the max dimension while maintaining aspect ratio.
     *
     * @param bitmap The source bitmap
     * @param maxDimension Maximum width or height
     * @return The scaled bitmap (or original if no scaling needed)
     */
    public static Bitmap scaleBitmapIfNeeded(Bitmap bitmap, int maxDimension) {
        if (bitmap == null) return null;
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap;
        }
        
        float scale = Math.min(
            (float) maxDimension / width,
            (float) maxDimension / height
        );
        
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        
        return scaled;
    }
    
    /**
     * Creates a center-cropped square thumbnail from a bitmap.
     * This maintains aspect ratio by cropping from the center.
     *
     * @param bitmap The source bitmap
     * @param size The target size (width and height)
     * @return The cropped and scaled square bitmap
     */
    public static Bitmap createCenterCroppedThumbnail(Bitmap bitmap, int size) {
        if (bitmap == null) return null;
        
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int cropSize = Math.min(w, h);
        int x = (w - cropSize) / 2;
        int y = (h - cropSize) / 2;
        
        Bitmap cropped = Bitmap.createBitmap(bitmap, x, y, cropSize, cropSize);
        Bitmap thumbnail = Bitmap.createScaledBitmap(cropped, size, size, true);
        
        if (cropped != bitmap) {
            cropped.recycle();
        }
        
        return thumbnail;
    }
}
