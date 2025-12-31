package com.visiboard.app.ui.common;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.visiboard.app.R;
import com.visiboard.app.utils.UiHelper;

public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "extra_image_url";

    private ImageView ivFullscreenImage;
    private ImageButton btnClose;
    private ImageButton btnDownload;
    private String imageUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        if (imageUrl == null || imageUrl.isEmpty()) {
            finish();
            return;
        }

        initViews();
        loadImage();
        setupListeners();
    }

    private void initViews() {
        ivFullscreenImage = findViewById(R.id.iv_fullscreen_image);
        btnClose = findViewById(R.id.btn_close);
        btnDownload = findViewById(R.id.btn_download);
    }

    private void loadImage() {
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.ic_error_outline)
            .into(ivFullscreenImage);
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> finish());

        btnDownload.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                downloadImage();
            } else {
                requestStoragePermission();
            }
        });
    }

    private boolean checkStoragePermission() {
        // Android 10+ uses scoped storage, no permission needed for downloads
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            downloadImage();
        } else {
            UiHelper.showError(findViewById(android.R.id.content), "Storage permission required");
        }
    }

    private void downloadImage() {
        try {
            String fileName = "VisiBoard_" + System.currentTimeMillis() + ".jpg";

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(imageUrl));
            request.setTitle("Downloading Image");
            request.setDescription("Saving image from chat...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, fileName);

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                downloadManager.enqueue(request);
                UiHelper.showSuccess(findViewById(android.R.id.content), "Download started");
            }
        } catch (Exception e) {
            UiHelper.showError(findViewById(android.R.id.content), "Download failed: " + e.getMessage());
        }
    }
}
