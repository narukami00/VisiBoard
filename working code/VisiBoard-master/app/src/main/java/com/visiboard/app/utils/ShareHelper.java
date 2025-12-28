package com.visiboard.app.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ShareHelper {
    
    /**
     * Share text content
     */
    public static void shareText(Context context, String text, String subject) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        
        context.startActivity(Intent.createChooser(shareIntent, "Share via"));
    }
    
    /**
     * Share image with text
     */
    public static void shareImage(Context context, Bitmap bitmap, String text) {
        try {
            File cachePath = new File(context.getCacheDir(), "images");
            cachePath.mkdirs();
            File file = new File(cachePath, "shared_image.png");
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();
            
            Uri imageUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
            );
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            context.startActivity(Intent.createChooser(shareIntent, "Share via"));
        } catch (IOException e) {
            e.printStackTrace();
            ErrorHandler.showError(context, "Failed to share image");
        }
    }
    
    /**
     * Share app link
     */
    public static void shareApp(Context context) {
        String appPackageName = context.getPackageName();
        String shareMessage = "Check out VisiBoard - Create and discover location-based notes!\n\n" +
                            "Download: https://play.google.com/store/apps/details?id=" + appPackageName;
        
        shareText(context, shareMessage, "VisiBoard App");
    }
    
    /**
     * Open URL in browser
     */
    public static void openUrl(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            context.startActivity(intent);
        } catch (Exception e) {
            ErrorHandler.showError(context, "Failed to open link");
        }
    }
    
    /**
     * Open email client
     */
    public static void sendEmail(Context context, String email, String subject, String body) {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:" + email));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);
        
        try {
            context.startActivity(Intent.createChooser(emailIntent, "Send email"));
        } catch (Exception e) {
            ErrorHandler.showError(context, "No email app found");
        }
    }
}
