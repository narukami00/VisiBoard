package com.visiboard.app.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.LruCache;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageCache {
    
    private static ImageCache instance;
    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executor;
    
    private ImageCache() {
        // Get max available VM memory
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // Use 1/8th of available memory for cache, but cap at 32MB for better multi-window support
        final int cacheSize = Math.min(maxMemory / 8, 32 * 1024);
        
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
        
        // Thread pool for image decoding (adaptive based on cores)
        int processors = Runtime.getRuntime().availableProcessors();
        int threadCount = Math.max(2, Math.min(processors, 4));
        executor = Executors.newFixedThreadPool(threadCount);
    }
    
    public static synchronized ImageCache getInstance() {
        if (instance == null) {
            instance = new ImageCache();
        }
        return instance;
    }
    
    // Backward compatibility overload
    public void loadBase64Image(String base64String, ImageView imageView, int placeholderResId) {
        loadBase64Image(null, base64String, imageView, placeholderResId);
    }
    
    public void loadBase64Image(String cacheKey, String base64String, ImageView imageView, int placeholderResId) {
        if (base64String == null || base64String.isEmpty()) {
            imageView.setImageResource(placeholderResId);
            return;
        }
        
        // Use provided key (Note ID) or fallback to hash if null (though caller should provide ID)
        final String key = cacheKey != null ? cacheKey : String.valueOf(base64String.hashCode());
        
        // Check memory cache
        Bitmap cachedBitmap = memoryCache.get(key);
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap);
            return;
        }
        
        // Set placeholder
        imageView.setImageResource(placeholderResId);
        
        // Load asynchronously
        final WeakReference<ImageView> imageViewRef = new WeakReference<>(imageView);
        executor.execute(() -> {
            try {
                byte[] bytes = Base64.decode(base64String, Base64.DEFAULT);
                
                // Decode with downsampling if needed
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                
                // Calculate inSampleSize (increased from 200 to 800 for better quality)
                options.inSampleSize = calculateInSampleSize(options, 800, 800);
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.RGB_565; // Use less memory
                
                final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                
                if (bitmap != null) {
                    // Add to cache
                    memoryCache.put(key, bitmap);
                    
                    // Set on UI thread
                    ImageView view = imageViewRef.get();
                    if (view != null) {
                        view.post(() -> {
                            if (imageViewRef.get() != null) {
                                view.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    public void loadImageFromPath(String path, ImageView imageView, int placeholderResId) {
        if (path == null || path.isEmpty()) {
            imageView.setImageResource(placeholderResId);
            return;
        }
        
        // Use path as key
        final String key = path;
        
        // Check memory cache
        Bitmap cachedBitmap = memoryCache.get(key);
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap);
            return;
        }
        
        // Set placeholder
        imageView.setImageResource(placeholderResId);
        
        // Load asynchronously
        final WeakReference<ImageView> imageViewRef = new WeakReference<>(imageView);
        executor.execute(() -> {
            try {
                // Decode from file with downsampling
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);
                
                // Calculate inSampleSize (increased from 200 to 800 for better quality)
                options.inSampleSize = calculateInSampleSize(options, 800, 800);
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.RGB_565; 
                
                final Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                
                if (bitmap != null) {
                    // Add to cache
                    memoryCache.put(key, bitmap);
                    
                    // Set on UI thread
                    ImageView view = imageViewRef.get();
                    if (view != null) {
                        view.post(() -> {
                            if (imageViewRef.get() != null) {
                                view.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }
    
    public int getCacheSize() {
        return memoryCache.size();
    }
    
    public void shutdown() {
        executor.shutdown();
        clearCache();
    }
    
    public void clearCache() {
        memoryCache.evictAll();
    }
    
    public void trimMemory() {
        memoryCache.trimToSize(memoryCache.size() / 2);
    }
}
