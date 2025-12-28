package com.visiboard.app.chat;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Helper class for recording and playing voice messages.
 * Handles MediaRecorder, encoding to base64, and MediaPlayer for playback.
 */
public class VoiceRecorderHelper {
    
    private static final String TAG = "VoiceRecorderHelper";
    public static final int MAX_DURATION_SECONDS = 60;
    
    private MediaRecorder recorder;
    private MediaPlayer player;
    private String currentFilePath;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private long recordingStartTime;
    
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RecordingCallback recordingCallback;
    private PlaybackCallback playbackCallback;
    
    private Runnable durationUpdater;
    private Runnable maxDurationChecker;
    
    public VoiceRecorderHelper(Context context) {
        this.context = context;
    }
    
    /**
     * Starts recording audio.
     */
    public void startRecording(RecordingCallback callback) {
        this.recordingCallback = callback;
        
        // Create temporary file
        File cacheDir = context.getCacheDir();
        currentFilePath = cacheDir.getAbsolutePath() + "/voice_" + System.currentTimeMillis() + ".m4a";
        
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(64000); // 64kbps for smaller file size
            recorder.setAudioSamplingRate(44100);
            recorder.setMaxDuration(MAX_DURATION_SECONDS * 1000);
            recorder.setOutputFile(currentFilePath);
            
            recorder.setOnInfoListener((mr, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecording();
                }
            });
            
            recorder.prepare();
            recorder.start();
            isRecording = true;
            recordingStartTime = System.currentTimeMillis();
            
            // Update duration periodically
            startDurationUpdater();
            
            // Safety: auto-stop at max duration
            maxDurationChecker = () -> {
                if (isRecording) {
                    stopRecording();
                }
            };
            handler.postDelayed(maxDurationChecker, (MAX_DURATION_SECONDS + 1) * 1000);
            
            callback.onRecordingStarted();
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording", e);
            callback.onError("Failed to start recording: " + e.getMessage());
            cleanup();
        }
    }
    
    /**
     * Stops recording and returns the base64 encoded audio.
     */
    public void stopRecording() {
        if (!isRecording || recorder == null) return;
        
        handler.removeCallbacks(durationUpdater);
        if (maxDurationChecker != null) {
            handler.removeCallbacks(maxDurationChecker);
        }
        
        try {
            recorder.stop();
            recorder.release();
            recorder = null;
            isRecording = false;
            
            int durationSeconds = (int) ((System.currentTimeMillis() - recordingStartTime) / 1000);
            
            // Encode to base64
            String base64Audio = encodeFileToBase64(currentFilePath);
            
            if (base64Audio != null && recordingCallback != null) {
                recordingCallback.onRecordingComplete(base64Audio, durationSeconds);
            } else if (recordingCallback != null) {
                recordingCallback.onError("Failed to encode audio");
            }
            
            // Delete temp file
            new File(currentFilePath).delete();
            currentFilePath = null;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop recording", e);
            if (recordingCallback != null) {
                recordingCallback.onError("Failed to stop recording: " + e.getMessage());
            }
            cleanup();
        }
    }
    
    /**
     * Cancels recording without saving.
     */
    public void cancelRecording() {
        if (!isRecording) return;
        
        handler.removeCallbacks(durationUpdater);
        if (maxDurationChecker != null) {
            handler.removeCallbacks(maxDurationChecker);
        }
        
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error canceling recording", e);
        }
        
        isRecording = false;
        
        // Delete temp file
        if (currentFilePath != null) {
            new File(currentFilePath).delete();
            currentFilePath = null;
        }
        
        if (recordingCallback != null) {
            recordingCallback.onRecordingCanceled();
        }
    }
    
    /**
     * Plays a voice message from base64.
     */
    public void playVoiceMessage(String base64Audio, PlaybackCallback callback) {
        this.playbackCallback = callback;
        
        if (isPlaying) {
            stopPlayback();
        }
        
        try {
            // Decode and save to temp file
            byte[] audioData = Base64.decode(base64Audio, Base64.DEFAULT);
            File tempFile = new File(context.getCacheDir(), "play_" + System.currentTimeMillis() + ".m4a");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            fos.write(audioData);
            fos.close();
            
            player = new MediaPlayer();
            player.setDataSource(tempFile.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                isPlaying = true;
                player.start();
                callback.onPlaybackStarted(player.getDuration() / 1000);
                startPlaybackUpdater();
            });
            player.setOnCompletionListener(mp -> {
                stopPlayback();
                callback.onPlaybackComplete();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                stopPlayback();
                callback.onError("Playback error");
                return true;
            });
            player.prepareAsync();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to play voice message", e);
            callback.onError("Failed to play: " + e.getMessage());
        }
    }
    
    /**
     * Stops playback.
     */
    public void stopPlayback() {
        handler.removeCallbacks(durationUpdater);
        
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
                player.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping playback", e);
            }
            player = null;
        }
        isPlaying = false;
    }
    
    /**
     * Toggles playback (play/pause).
     */
    public void togglePlayback() {
        if (player == null) return;
        
        if (player.isPlaying()) {
            player.pause();
            isPlaying = false;
            if (playbackCallback != null) {
                playbackCallback.onPlaybackPaused();
            }
        } else {
            player.start();
            isPlaying = true;
            startPlaybackUpdater();
            if (playbackCallback != null) {
                playbackCallback.onPlaybackResumed();
            }
        }
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    private void startDurationUpdater() {
        durationUpdater = new Runnable() {
            @Override
            public void run() {
                if (isRecording && recordingCallback != null) {
                    int seconds = (int) ((System.currentTimeMillis() - recordingStartTime) / 1000);
                    recordingCallback.onDurationUpdate(seconds);
                    handler.postDelayed(this, 500);
                }
            }
        };
        handler.post(durationUpdater);
    }
    
    private void startPlaybackUpdater() {
        durationUpdater = new Runnable() {
            @Override
            public void run() {
                if (isPlaying && player != null && playbackCallback != null) {
                    int currentPos = player.getCurrentPosition() / 1000;
                    int total = player.getDuration() / 1000;
                    playbackCallback.onPlaybackProgress(currentPos, total);
                    handler.postDelayed(this, 200);
                }
            }
        };
        handler.post(durationUpdater);
    }
    
    private String encodeFileToBase64(String filePath) {
        try {
            File file = new File(filePath);
            byte[] bytes = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            fis.read(bytes);
            fis.close();
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to encode file to base64", e);
            return null;
        }
    }
    
    private void cleanup() {
        if (recorder != null) {
            try {
                recorder.release();
            } catch (Exception e) {}
            recorder = null;
        }
        isRecording = false;
        
        if (currentFilePath != null) {
            new File(currentFilePath).delete();
            currentFilePath = null;
        }
    }
    
    public void release() {
        stopPlayback();
        cancelRecording();
        handler.removeCallbacksAndMessages(null);
    }
    
    // Callbacks
    public interface RecordingCallback {
        void onRecordingStarted();
        void onDurationUpdate(int seconds);
        void onRecordingComplete(String base64Audio, int durationSeconds);
        void onRecordingCanceled();
        void onError(String error);
    }
    
    public interface PlaybackCallback {
        void onPlaybackStarted(int totalSeconds);
        void onPlaybackProgress(int currentSeconds, int totalSeconds);
        void onPlaybackPaused();
        void onPlaybackResumed();
        void onPlaybackComplete();
        void onError(String error);
    }
}
