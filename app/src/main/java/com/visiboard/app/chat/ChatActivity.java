package com.visiboard.app.chat;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.net.Uri;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import com.visiboard.app.data.ImgBBService;
import com.visiboard.app.data.ImgBBResponse;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.ChatMessage;
import com.visiboard.app.ui.profile.UserProfileActivity;
import com.visiboard.app.utils.ImageCache;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Full-screen chat activity for real-time messaging.
 * Supports text and voice messages.
 */
public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final int RECORD_AUDIO_PERMISSION_CODE = 101;

    public static final String EXTRA_RECIPIENT_ID = "recipient_id";
    public static final String EXTRA_RECIPIENT_NAME = "recipient_name";
    public static final String EXTRA_RECIPIENT_PIC = "recipient_pic";

    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnVoice;
    private ImageButton btnAttach; // New field
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private ImageButton btnBack;
    private CircleImageView ivRecipientAvatar;
    private TextView tvRecipientName;
    private TextView tvStatus; // New field
    private LinearLayout llEmptyState;
    private TextView tvEmptyTitle;
    
    // Reply UI
    private LinearLayout layoutReplyPreview;
    private TextView tvReplyName, tvReplyText;
    private ImageButton btnCloseReply;
    private ChatMessage replyingToMessage = null;

    // Recording UI
    private LinearLayout layoutRecording;
    private TextView tvRecordingTime;
    private View viewRecordingBlink;

    private MessagesAdapter adapter;
    private ChatManager chatManager;
    private VoiceRecorderHelper voiceHelper;
    private String chatId;
    private String recipientId;
    private String recipientName;
    private String recipientProfilePic;
    private String currentUserId;
    private String currentUserName;
    private String currentUserProfilePic;

    private ChildEventListener messagesListener;
    private boolean isFirstChat = true;
    
    // Recording state
    private float startX;
    private boolean isRecordingCancelled = false;
    private Handler blinkHandler = new Handler(Looper.getMainLooper());
    private Runnable blinkRunnable;

    // Typing & Presence
    private Handler typingHandler = new Handler(Looper.getMainLooper());
    private boolean isTyping = false;
    private static final long TYPING_DELAY = 2000; // 2 seconds
    private Runnable stopTypingRunnable = () -> setIsTyping(false);
    private boolean isRecipientOnline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get intent extras
        recipientId = getIntent().getStringExtra(EXTRA_RECIPIENT_ID);
        recipientName = getIntent().getStringExtra(EXTRA_RECIPIENT_NAME);
        
        if (recipientId == null) {
            com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Error: No recipient specified");
            finish();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        
        if (currentUserId == null) {
            com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Error: Not logged in");
            finish();
            return;
        }

        chatManager = ChatManager.getInstance();
        voiceHelper = new VoiceRecorderHelper(this);
        chatId = ChatManager.generateChatId(currentUserId, recipientId);

        initViews();
        setupRecyclerView();
        setupClickListeners();
        setupVoiceRecording();
        loadRecipientInfo();
        loadCurrentUserInfo();
        checkIfFirstChat();
        checkBlockedStatus();
        checkBlockedStatus();
        setupSwipeToReply();
        setupImagePicker();
    }
    
    private ActivityResultLauncher<String> imagePickerLauncher;
    
    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                uploadImage(uri);
            }
        });
    }

    private void pickImage() {
        imagePickerLauncher.launch("image/*");
    }

    private void uploadImage(Uri uri) {
        // Show loading
        com.visiboard.app.utils.UiHelper.showInfo(findViewById(android.R.id.content), "Uploading image...");

        try {
            // Create compressed temp file
            File file = createCompressedImageFile(uri);
            if (file == null) {
                 com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to process image");
                 return;
            }

            // Create RequestBody
            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), file);
            MultipartBody.Part bBody = MultipartBody.Part.createFormData("image", file.getName(), requestFile);

            // Create Service
            Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.imgbb.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
                
            ImgBBService service = retrofit.create(ImgBBService.class);
            
            // Call API
            String apiKey = "d313408672c8e76389a86662195d862b"; // User provided key
            service.uploadImage(apiKey, bBody).enqueue(new Callback<ImgBBResponse>() {
                @Override
                public void onResponse(Call<ImgBBResponse> call, Response<ImgBBResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        String imageUrl = response.body().getData().getUrl();
                        sendImageMessage(imageUrl);
                    } else {
                        com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Upload failed: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<ImgBBResponse> call, Throwable t) {
                    com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Upload error: " + t.getMessage());
                }
            });

        } catch (Exception e) {
             com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Error: " + e.getMessage());
        }
    }
    
    /**
     * Creates a compressed image file from URI to reduce upload size and bandwidth.
     * Targets ~800KB max compressed size.
     */
    private File createCompressedImageFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;
            
            // Decode with inJustDecodeBounds first to get size
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();
            
            // Calculate sample size to reduce memory usage
            int sampleSize = 1;
            int maxDimension = 1200; // Max width or height
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                int halfWidth = options.outWidth / 2;
                int halfHeight = options.outHeight / 2;
                while ((halfWidth / sampleSize) >= maxDimension && (halfHeight / sampleSize) >= maxDimension) {
                    sampleSize *= 2;
                }
            }
            
            // Decode with sample size
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            inputStream = getContentResolver().openInputStream(uri);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();
            
            if (bitmap == null) return null;
            
            // Scale down if still too large
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > maxDimension || height > maxDimension) {
                float scale = Math.min((float) maxDimension / width, (float) maxDimension / height);
                width = Math.round(width * scale);
                height = Math.round(height * scale);
                bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            // Write to temp file with compression
            File file = new File(getCacheDir(), "chat_img_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, fos); // 75% quality
            fos.close();
            
            Log.d(TAG, "Compressed image: " + file.length() / 1024 + "KB");
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Image compression failed", e);
            // Fallback to original method
            try {
                return createTempFileFromUri(uri);
            } catch (IOException ioException) {
                Log.e(TAG, "Fallback also failed", ioException);
                return null;
            }
        }
    }
    
    private void sendImageMessage(String imageUrl) {
        chatManager.sendImageMessage(chatId, recipientId, recipientName, recipientProfilePic, imageUrl,
            new ChatManager.SendMessageCallback() {
                @Override
                public void onSuccess(ChatMessage message) {
                    runOnUiThread(() -> {
                        isFirstChat = false;
                        Log.d(TAG, "Image message sent");
                    });
                }
                @Override
                public void onError(String error) {
                     runOnUiThread(() -> 
                        com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to send image: " + error));
                }
            });
    }

    private File createTempFileFromUri(Uri uri) throws java.io.IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) return null;
        
        File file = new File(getCacheDir(), "temp_image_" + System.currentTimeMillis() + ".jpg");
        OutputStream outputStream = new FileOutputStream(file);
        
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        outputStream.close();
        inputStream.close();
        return file;
    }


    private void initViews() {
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnVoice = findViewById(R.id.btn_voice);
        btnAttach = findViewById(R.id.btn_attach); // Init
        swipeRefresh = findViewById(R.id.swipe_refresh);
        btnBack = findViewById(R.id.btn_back);
        ivRecipientAvatar = findViewById(R.id.iv_recipient_avatar);
        tvRecipientName = findViewById(R.id.tv_recipient_name);
        tvStatus = findViewById(R.id.tv_status);
        llEmptyState = findViewById(R.id.ll_empty_state);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        
        // Recording UI
        layoutRecording = findViewById(R.id.layout_recording);
        tvRecordingTime = findViewById(R.id.tv_recording_time);
        viewRecordingBlink = findViewById(R.id.view_recording_blink);

        // Reply UI
        layoutReplyPreview = findViewById(R.id.layout_reply_preview);
        tvReplyName = findViewById(R.id.tv_reply_name);
        tvReplyText = findViewById(R.id.tv_reply_text);
        btnCloseReply = findViewById(R.id.btn_close_reply);
        
        btnCloseReply.setOnClickListener(v -> cancelReply());

        // Set recipient name immediately
        tvRecipientName.setText(recipientName != null ? recipientName : "Chat");
        
        // Setup initial status
        tvStatus = findViewById(R.id.tv_status);
        if (tvStatus != null) {
            tvStatus.setText("Offline");
        }
        
        // Setup pull-to-refresh
        setupPullToRefresh();
    }
    
    private void setupPullToRefresh() {
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.primary, R.color.secondary);
            swipeRefresh.setOnRefreshListener(() -> {
                // Reload messages
                if (chatManager != null && chatId != null && messagesListener != null) {
                    chatManager.removeMessagesListener(chatId, messagesListener);
                    adapter.clearMessages();
                    startListeningForMessages();
                }
                // Stop refreshing after a short delay
                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                }, 1500);
            });
        }
    }

    private void loadRecipientInfo() {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(recipientId)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name = doc.getString("name");
                    if (name != null && !name.isEmpty()) {
                        recipientName = name;
                        tvRecipientName.setText(name);
                    }
                    
                    recipientProfilePic = doc.getString("profilePic");
                    if (recipientProfilePic != null && !recipientProfilePic.isEmpty()) {
                        ImageCache.getInstance().loadBase64Image(
                            "chat_" + recipientId, recipientProfilePic, ivRecipientAvatar, R.drawable.ic_profile);
                    }
                }
            });
    }

    private void setupRecyclerView() {
        adapter = new MessagesAdapter(currentUserId);
        adapter.setVoiceHelper(voiceHelper); // Pass helper to adapter
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());
        btnAttach.setOnClickListener(v -> pickImage());

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = s.toString().trim().length() > 0;
                btnSend.setVisibility(hasText ? View.VISIBLE : View.GONE);
                btnVoice.setVisibility(hasText ? View.GONE : View.VISIBLE);
                
                // Handle typing status
                if (hasText) {
                    setIsTyping(true);
                    typingHandler.removeCallbacks(stopTypingRunnable);
                    typingHandler.postDelayed(stopTypingRunnable, TYPING_DELAY);
                } else {
                    setIsTyping(false);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Initial state
        btnSend.setVisibility(View.GONE);
        btnVoice.setVisibility(View.VISIBLE);

        View.OnClickListener profileClickListener = v -> openUserProfile();
        ivRecipientAvatar.setOnClickListener(profileClickListener);
        tvRecipientName.setOnClickListener(profileClickListener);
    }
    
    private void setupVoiceRecording() {
        btnVoice.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (checkPermission()) {
                        startX = event.getX();
                        isRecordingCancelled = false;
                        startRecording();
                    } else {
                        requestPermission();
                    }
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (voiceHelper.isRecording()) {
                        // Check for slide to cancel (e.g., slide left by 100px)
                        if (Math.abs(event.getX() - startX) > 100) {
                            if (!isRecordingCancelled) {
                                isRecordingCancelled = true;
                                tvRecordingTime.setText("Cancelled");
                                tvRecordingTime.setTextColor(ContextCompat.getColor(this, R.color.error));
                                viewRecordingBlink.setBackgroundTintList(
                                    ContextCompat.getColorStateList(this, R.color.text_secondary));
                            }
                        }
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    if (voiceHelper.isRecording()) {
                        if (isRecordingCancelled) {
                            voiceHelper.cancelRecording();
                        } else {
                            voiceHelper.stopRecording();
                        }
                        stopRecordingUI();
                    }
                    return true;
                    
                case MotionEvent.ACTION_CANCEL:
                    if (voiceHelper.isRecording()) {
                        voiceHelper.cancelRecording();
                        stopRecordingUI();
                    }
                    return true;
            }
            return false;
        });
    }
    
    private void startRecording() {
        // Show recording UI
        layoutRecording.setVisibility(View.VISIBLE);
        etMessage.setVisibility(View.INVISIBLE);
        tvRecordingTime.setText("0:00");
        tvRecordingTime.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        viewRecordingBlink.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.error));
        startBlinking();
        
        voiceHelper.startRecording(new VoiceRecorderHelper.RecordingCallback() {
            @Override
            public void onRecordingStarted() {
                // Initial vibration
                btnVoice.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }

            @Override
            public void onDurationUpdate(int seconds) {
                runOnUiThread(() -> {
                    int mins = seconds / 60;
                    int secs = seconds % 60;
                    tvRecordingTime.setText(String.format("%d:%02d", mins, secs));
                });
            }

            @Override
            public void onRecordingComplete(String base64Audio, int durationSeconds) {
                if (durationSeconds < 1) {
                    com.visiboard.app.utils.UiHelper.showWarning(findViewById(android.R.id.content), "Voice message too short");
                    return;
                }
                sendVoiceMessage(base64Audio, durationSeconds);
            }

            @Override
            public void onRecordingCanceled() {
                runOnUiThread(() -> 
                    com.visiboard.app.utils.UiHelper.showInfo(findViewById(android.R.id.content), "Recording cancelled"));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> 
                    com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), error));
            }
        });
    }
    
    private void stopRecordingUI() {
        layoutRecording.setVisibility(View.GONE);
        etMessage.setVisibility(View.VISIBLE);
        stopBlinking();
    }
    
    private void startBlinking() {
        if (blinkRunnable == null) {
            blinkRunnable = new Runnable() {
                @Override
                public void run() {
                    if (viewRecordingBlink.getVisibility() == View.VISIBLE) {
                        viewRecordingBlink.setVisibility(View.INVISIBLE);
                    } else {
                        viewRecordingBlink.setVisibility(View.VISIBLE);
                    }
                    blinkHandler.postDelayed(this, 500);
                }
            };
        }
        blinkHandler.post(blinkRunnable);
    }
    
    private void stopBlinking() {
        if (blinkRunnable != null) {
            blinkHandler.removeCallbacks(blinkRunnable);
        }
        viewRecordingBlink.setVisibility(View.VISIBLE);
    }
    
    private boolean checkPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestPermission() {
        ActivityCompat.requestPermissions(this, 
            new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                com.visiboard.app.utils.UiHelper.showSuccess(findViewById(android.R.id.content), "Permission granted. Tap and hold to record.");
            } else {
                com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Permission denied to record audio");
            }
        }
    }

    private void openUserProfile() {
        Intent intent = new Intent(this, UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, recipientId);
        startActivity(intent);
    }

    private void loadCurrentUserInfo() {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    currentUserName = doc.getString("name");
                    currentUserProfilePic = doc.getString("profilePic");
                    
                    if (currentUserName != null) {
                        chatManager.updateChatUserInfo(chatId, recipientId, 
                            currentUserName, currentUserProfilePic);
                    }
                }
            });
    }

    private void checkIfFirstChat() {
        chatManager.checkChatExists(chatId, exists -> {
            isFirstChat = !exists;
            updateEmptyState();
            startListeningForMessages();
            startListeningForStatus();
        });
    }

    private void setIsTyping(boolean typing) {
        if (isTyping != typing) {
            isTyping = typing;
            chatManager.setTypingStatus(chatId, currentUserId, typing);
        }
    }

    private void startListeningForStatus() {
        // Listen for typing
        chatManager.listenForTyping(chatId, recipientId, isTyping -> {
            runOnUiThread(() -> {
                if (isTyping) {
                    tvStatus.setText("Typing...");
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.primary));
                } else {
                    updateOnlineStatusUI(); // Revert to online/offline status
                }
            });
        });

        // Listen for presence
        chatManager.listenForUserPresence(recipientId, (isOnline, lastSeen) -> {
            runOnUiThread(() -> {
                isRecipientOnline = isOnline;
                // Update avatar border or indicator if needed
                if (ivRecipientAvatar != null) {
                    ivRecipientAvatar.setBorderColor(ContextCompat.getColor(this, 
                        isOnline ? R.color.success : R.color.white));
                }
                updateOnlineStatusUI();
            });
        });
        
        // Start reporting my own presence
        chatManager.setupPresenceSystem(currentUserId);
    }

    private void updateOnlineStatusUI() {
        // Only update if not typing
        String statusText = tvStatus.getText().toString();
        if (statusText.equals("Typing...")) return; 
        
        if (isRecipientOnline) {
            tvStatus.setText("Online");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
        } else {
            tvStatus.setText("Offline");
            tvStatus.setTextColor(0xCCFFFFFF); 
        }
    }

    private void updateEmptyState() {
        if (isFirstChat && adapter.getItemCount() == 0) {
            llEmptyState.setVisibility(View.VISIBLE);
            rvMessages.setVisibility(View.GONE);
            tvEmptyTitle.setText("Say Hello to " + (recipientName != null ? recipientName : "them") + "! 👋");
        } else {
            llEmptyState.setVisibility(View.GONE);
            rvMessages.setVisibility(View.VISIBLE);
        }
    }

    private void startListeningForMessages() {
        chatManager.markChatAsRead(chatId);

        messagesListener = chatManager.listenForMessages(chatId, new ChatManager.MessageListener() {
            @Override
            public void onMessageReceived(ChatMessage message) {
                runOnUiThread(() -> {
                    adapter.addMessage(message);
                    rvMessages.scrollToPosition(adapter.getLastPosition());
                    updateEmptyState();
                    
                    if (!message.getSenderId().equals(currentUserId)) {
                        chatManager.markChatAsRead(chatId);
                    }
                });
            }

            @Override
            public void onMessageUpdated(ChatMessage message) {
                runOnUiThread(() -> adapter.updateMessage(message));
            }
        });
    }

    private boolean isBlockedByRecipient = false;

    private void checkBlockedStatus() {
        // Check if I blocked them (UI logic)
        if (com.visiboard.app.utils.UserCache.getInstance().isBlocked(recipientId)) {
            // I blocked them. 
            // Optional: Update UI to show "You blocked this user"
        }

        // Check if they blocked me
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(recipientId)
            .collection("blocked_users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    isBlockedByRecipient = true;
                }
            });
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        
        // 1. Check if I blocked them
        if (com.visiboard.app.utils.UserCache.getInstance().isBlocked(recipientId)) {
            com.visiboard.app.utils.UiHelper.showWarning(findViewById(android.R.id.content), "You blocked this user");
            return;
        }

        // 2. Check if they blocked me (Simulator)
        if (isBlockedByRecipient) {
            // Fake Network Error
            com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Network error: Failed to send");
            // In a real app, you might show a red "!" icon next to the message
            return;
        }

        etMessage.setText("");

        ChatManager.SendMessageCallback callback = new ChatManager.SendMessageCallback() {
            @Override
            public void onSuccess(ChatMessage message) {
                runOnUiThread(() -> {
                    isFirstChat = false;
                    Log.d(TAG, "Message sent successfully");
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to send: " + error);
                    // Don't restore text to avoid duplicate sends if retried, or handle better
                });
            }
        };

        if (replyingToMessage != null) {
            chatManager.replyToMessage(chatId, recipientId, recipientName, recipientProfilePic, text, replyingToMessage, callback);
            cancelReply();
        } else {
            chatManager.sendMessage(chatId, recipientId, recipientName, recipientProfilePic, text, callback);
        }
    }

    private void cancelReply() {
        replyingToMessage = null;
        layoutReplyPreview.setVisibility(View.GONE);
    }

    private void setupSwipeToReply() {
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    ChatMessage message = adapter.getMessage(position);
                    showReplyPreview(message);
                    // Reset item state
                    adapter.notifyItemChanged(position);
                }
            }
        };
        new ItemTouchHelper(simpleCallback).attachToRecyclerView(rvMessages);
    }

    private void showReplyPreview(ChatMessage message) {
        replyingToMessage = message;
        layoutReplyPreview.setVisibility(View.VISIBLE);
        
        String name = message.getSenderId().equals(currentUserId) ? "You" : recipientName;
        tvReplyName.setText("Replying to " + name);
        tvReplyText.setText(message.isVoice() ? "🎤 Voice message" : message.getText());
        
        // Show keyboard
        etMessage.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        
        // Vibrate
        viewRecordingBlink.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
    }
    
    private void sendVoiceMessage(String base64Audio, int duration) {
        chatManager.sendVoiceMessage(chatId, recipientId, recipientName, recipientProfilePic, base64Audio, duration,
            new ChatManager.SendMessageCallback() {
                @Override
                public void onSuccess(ChatMessage message) {
                    runOnUiThread(() -> {
                        isFirstChat = false;
                        Log.d(TAG, "Voice message sent successfully");
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> 
                        com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to send voice: " + error));
                }
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (messagesListener == null && chatId != null) {
            startListeningForMessages();
        }
        
        // Mark messages as read when chat is opened/resumed
        if (chatId != null && currentUserId != null) {
            chatManager.markMessagesAsRead(chatId, currentUserId);
        }
        
        // Start listening for recipient typing
        setupTypingListener();
    }
    
    private void setupTypingListener() {
        if (recipientId == null || chatId == null) return;
        
        chatManager.listenForTyping(chatId, recipientId, isTyping -> {
            runOnUiThread(() -> {
                if (tvStatus != null) {
                    if (isTyping) {
                        tvStatus.setText("typing...");
                        tvStatus.setTextColor(getResources().getColor(R.color.accent, null));
                    } else {
                        // Restore to online/offline status
                        tvStatus.setText(isRecipientOnline ? "Online" : "Tap to view profile");
                        tvStatus.setTextColor(0xCCFFFFFF);
                    }
                }
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop recording if active
        if (voiceHelper.isRecording()) {
            voiceHelper.cancelRecording();
            stopRecordingUI();
        }
        // Stop playback
        if (voiceHelper.isPlaying()) {
            voiceHelper.stopPlayback();
        }
        
        // Clear typing status when leaving chat
        if (chatId != null) {
            chatManager.setTypingStatus(chatId, currentUserId, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null && chatId != null) {
            chatManager.removeMessagesListener(chatId, messagesListener);
            messagesListener = null;
        }
        voiceHelper.release();
    }
}
