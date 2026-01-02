package com.visiboard.app.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.visiboard.app.R;
import com.visiboard.app.data.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for chat messages.
 * Supports text and voice messages with different layouts for sent/received.
 */
public class MessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TEXT_SENT = 1;
    private static final int TYPE_TEXT_RECEIVED = 2;
    private static final int TYPE_VOICE_SENT = 3;
    private static final int TYPE_VOICE_RECEIVED = 4;
    private static final int TYPE_IMAGE_SENT = 5;
    private static final int TYPE_IMAGE_RECEIVED = 6;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final String currentUserId;
    private final String chatId;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
    
    private VoiceRecorderHelper voiceHelper;
    private int currentlyPlayingPosition = -1;

    public MessagesAdapter(String currentUserId, String chatId) {
        this.currentUserId = currentUserId;
        this.chatId = chatId;
    }
    
    public void setVoiceHelper(VoiceRecorderHelper helper) {
        this.voiceHelper = helper;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        boolean isSent = message.getSenderId().equals(currentUserId);
        
        if (message.isVoice()) {
            return isSent ? TYPE_VOICE_SENT : TYPE_VOICE_RECEIVED;
        } else if (message.isImage()) {
            return isSent ? TYPE_IMAGE_SENT : TYPE_IMAGE_RECEIVED;
        } else {
            return isSent ? TYPE_TEXT_SENT : TYPE_TEXT_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        
        switch (viewType) {
            case TYPE_TEXT_SENT:
                return new TextMessageViewHolder(
                    inflater.inflate(R.layout.item_message_sent, parent, false));
            case TYPE_TEXT_RECEIVED:
                return new TextMessageViewHolder(
                    inflater.inflate(R.layout.item_message_received, parent, false));
            case TYPE_VOICE_SENT:
                return new VoiceMessageViewHolder(
                    inflater.inflate(R.layout.item_voice_sent, parent, false));
            case TYPE_VOICE_RECEIVED:
                return new VoiceMessageViewHolder(
                    inflater.inflate(R.layout.item_voice_received, parent, false));
            case TYPE_IMAGE_SENT:
                return new ImageMessageViewHolder(
                    inflater.inflate(R.layout.item_image_sent, parent, false));
            case TYPE_IMAGE_RECEIVED:
                return new ImageMessageViewHolder(
                    inflater.inflate(R.layout.item_image_received, parent, false));
            default:
                return new TextMessageViewHolder(
                    inflater.inflate(R.layout.item_message_sent, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        if (holder instanceof TextMessageViewHolder) {
            ((TextMessageViewHolder) holder).bind(message);
        } else if (holder instanceof VoiceMessageViewHolder) {
            ((VoiceMessageViewHolder) holder).bind(message, position);
        } else if (holder instanceof ImageMessageViewHolder) {
            ((ImageMessageViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message) {
        for (ChatMessage existing : messages) {
            if (existing.getId() != null && existing.getId().equals(message.getId())) {
                return;
            }
        }
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessage(ChatMessage message) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId() != null && messages.get(i).getId().equals(message.getId())) {
                messages.set(i, message);
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }

    public int getLastPosition() {
        return messages.isEmpty() ? 0 : messages.size() - 1;
    }

    public ChatMessage getMessage(int position) {
        return messages.get(position);
    }
    
    private String formatMessageTime(long timestamp) {
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTimeInMillis(timestamp);
        
        Calendar now = Calendar.getInstance();
        
        if (msgCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
            msgCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
            return timeFormat.format(new Date(timestamp));
        }
        
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (msgCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
            msgCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)) {
            return "Yesterday, " + timeFormat.format(new Date(timestamp));
        }
        
        return dateTimeFormat.format(new Date(timestamp));
    }
    
    private String formatDuration(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", mins, secs);
    }

    // --- Text Message ViewHolder ---
    class TextMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvMessage, tvTime;
        ImageView ivReadStatus;
        LinearLayout layoutReply;
        TextView tvReplyName, tvReplyText;
        RecyclerView rvReactions;

        TextMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTime = itemView.findViewById(R.id.tv_time);
            ivReadStatus = itemView.findViewById(R.id.iv_read_status);
            layoutReply = itemView.findViewById(R.id.layout_reply);
            tvReplyName = itemView.findViewById(R.id.tv_reply_name);
            tvReplyText = itemView.findViewById(R.id.tv_reply_text);
            rvReactions = itemView.findViewById(R.id.rv_reactions);
        }

        void bind(ChatMessage message) {
            tvMessage.setText(message.getText());
            tvTime.setText(formatMessageTime(message.getTimestamp()));
            
            // Show read status for sent messages
            if (ivReadStatus != null && message.getSenderId() != null && message.getSenderId().equals(currentUserId)) {
                ivReadStatus.setVisibility(View.VISIBLE);
                if (message.isRead()) {
                    ivReadStatus.setImageResource(R.drawable.ic_check_double);
                    ivReadStatus.setColorFilter(itemView.getContext().getResources().getColor(R.color.accent, null));
                } else {
                    ivReadStatus.setImageResource(R.drawable.ic_check);
                    ivReadStatus.setColorFilter(0xAAFFFFFF); 
                }
            } else if (ivReadStatus != null) {
                ivReadStatus.setVisibility(View.GONE);
            }
            
            if (message.getReplyToId() != null && message.getReplyToName() != null && message.getReplyToText() != null) {
                layoutReply.setVisibility(View.VISIBLE);
                tvReplyName.setText(message.getReplyToName());
                tvReplyText.setText(message.getReplyToText());
            } else {
                layoutReply.setVisibility(View.GONE);
            }
            
            // Reactions
            if (rvReactions != null) {
                bindReactions(rvReactions, message);
            }
            
            itemView.setOnLongClickListener(v -> {
                showReactionPopup(v, message);
                return true;
            });
        }
        
        private void copyToClipboard(Context context, String text) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Message", text);
            clipboard.setPrimaryClip(clip);
            itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            com.visiboard.app.utils.UiHelper.showInfo(itemView, "Message copied");
        }
    }

    // --- Voice Message ViewHolder ---
    class VoiceMessageViewHolder extends RecyclerView.ViewHolder {
        private final ImageButton btnPlay;
        private final ProgressBar progressPlayback;
        private final TextView tvDuration;
        private final TextView tvTime;
        private final ImageView ivReadStatus;
        private final RecyclerView rvReactions;
        
        private boolean isPlaying = false;

        public VoiceMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            btnPlay = itemView.findViewById(R.id.btn_play);
            progressPlayback = itemView.findViewById(R.id.progress_playback);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvTime = itemView.findViewById(R.id.tv_time);
            ivReadStatus = itemView.findViewById(R.id.iv_read_status);
            rvReactions = itemView.findViewById(R.id.rv_reactions);
        }

        public void bind(ChatMessage message, int position) {
            tvDuration.setText(formatDuration(message.getVoiceDuration()));
            tvTime.setText(formatMessageTime(message.getTimestamp()));
            progressPlayback.setProgress(0);

            // Show read status for sent messages
            if (ivReadStatus != null && message.getSenderId() != null && message.getSenderId().equals(currentUserId)) {
                ivReadStatus.setVisibility(View.VISIBLE);
                if (message.isRead()) {
                    // Double check - message has been read
                    ivReadStatus.setImageResource(R.drawable.ic_check_double);
                    ivReadStatus.setColorFilter(itemView.getContext().getResources().getColor(R.color.accent, null));
                } else {
                    // Single check - message sent but not read
                    ivReadStatus.setImageResource(R.drawable.ic_check);
                    ivReadStatus.setColorFilter(0xAAFFFFFF); // Light white
                }
            } else if (ivReadStatus != null) {
                ivReadStatus.setVisibility(View.GONE);
            }

            // Reactions
            if (rvReactions != null) {
                bindReactions(rvReactions, message);
            }
            
            itemView.setOnLongClickListener(v -> {
                showReactionPopup(v, message);
                return true;
            });
            
            // Reset state if another voice is playing
            if (currentlyPlayingPosition != position) {
                isPlaying = false;
                btnPlay.setImageResource(R.drawable.ic_play);
            }
            
            btnPlay.setOnClickListener(v -> {
                if (voiceHelper == null) {
                    com.visiboard.app.utils.UiHelper.showWarning(itemView, "Voice playback unavailable");
                    return;
                }
                
                if (isPlaying) {
                    // Pause
                    voiceHelper.togglePlayback();
                    isPlaying = false;
                    btnPlay.setImageResource(R.drawable.ic_play);
                } else {
                    // Stop any other playing voice
                    if (currentlyPlayingPosition != -1 && currentlyPlayingPosition != position) {
                        voiceHelper.stopPlayback();
                        notifyItemChanged(currentlyPlayingPosition);
                    }
                    
                    currentlyPlayingPosition = position;
                    
                    // Start playback
                    String voiceBase64 = message.getVoiceBase64();
                    if (voiceBase64 == null || voiceBase64.isEmpty()) {
                        com.visiboard.app.utils.UiHelper.showWarning(itemView, "Voice data not available");
                        return;
                    }
                    
                    voiceHelper.playVoiceMessage(voiceBase64, new VoiceRecorderHelper.PlaybackCallback() {
                        @Override
                        public void onPlaybackStarted(int totalSeconds) {
                            isPlaying = true;
                            btnPlay.setImageResource(R.drawable.ic_pause);
                        }

                        @Override
                        public void onPlaybackProgress(int currentSeconds, int totalSeconds) {
                            if (totalSeconds > 0) {
                                int progress = (currentSeconds * 100) / totalSeconds;
                                progressPlayback.setProgress(progress);
                                tvDuration.setText(formatDuration(currentSeconds) + " / " + formatDuration(totalSeconds));
                            }
                        }

                        @Override
                        public void onPlaybackPaused() {
                            isPlaying = false;
                            btnPlay.setImageResource(R.drawable.ic_play);
                        }

                        @Override
                        public void onPlaybackResumed() {
                            isPlaying = true;
                            btnPlay.setImageResource(R.drawable.ic_pause);
                        }

                        @Override
                        public void onPlaybackComplete() {
                            isPlaying = false;
                            btnPlay.setImageResource(R.drawable.ic_play);
                            progressPlayback.setProgress(100);
                            tvDuration.setText(formatDuration(message.getVoiceDuration()));
                            currentlyPlayingPosition = -1;
                        }

                        @Override
                        public void onError(String error) {
                            isPlaying = false;
                            btnPlay.setImageResource(R.drawable.ic_play);
                            com.visiboard.app.utils.UiHelper.showError(itemView, error);
                            currentlyPlayingPosition = -1;
                        }
                    });
                }
            });
        }
    }

    // --- Image Message ViewHolder ---
    class ImageMessageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivImage;
        private final TextView tvTime;
        private final ImageView ivReadStatus;
        private final RecyclerView rvReactions;

        ImageMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.iv_image);
            tvTime = itemView.findViewById(R.id.tv_time);
            ivReadStatus = itemView.findViewById(R.id.iv_read_status);
            rvReactions = itemView.findViewById(R.id.rv_reactions);
        }

        void bind(ChatMessage message) {
            tvTime.setText(formatMessageTime(message.getTimestamp()));
            
            // Show read status for sent messages
            if (ivReadStatus != null && message.getSenderId() != null && message.getSenderId().equals(currentUserId)) {
                ivReadStatus.setVisibility(View.VISIBLE);
                if (message.isRead()) {
                    // Double check - message has been read
                    ivReadStatus.setImageResource(R.drawable.ic_check_double);
                    ivReadStatus.setColorFilter(itemView.getContext().getResources().getColor(R.color.accent, null));
                } else {
                    // Single check - message sent but not read
                    ivReadStatus.setImageResource(R.drawable.ic_check);
                    ivReadStatus.setColorFilter(0xAAFFFFFF); // Light white
                }
            } else if (ivReadStatus != null) {
                ivReadStatus.setVisibility(View.GONE);
            }

            // Reactions
            if (rvReactions != null) {
                bindReactions(rvReactions, message);
            }
            
            itemView.setOnLongClickListener(v -> {
                showReactionPopup(v, message);
                return true;
            });
            
            // Allow long press on the image as well, since it intercepts clicks
            ivImage.setOnLongClickListener(v -> {
               showReactionPopup(v, message);
               return true; 
            });
            
            // Load image using existing ImageCache or Glide (implied usage in ImageCache)
            // But ImageCache expects a user directory structure for "base64" usually, 
            // Here we have a URL. We should use Glide directly or check if ImageCache supports URLs.
            // Looking at previous files, ImageCache seems to handle "chat_userID" and base64.
            // Since we added Glide to build.gradle, we can use it here.
            
            com.bumptech.glide.Glide.with(itemView.getContext())
                .load(message.getImageUrl())
                .placeholder(R.drawable.placeholder_image) // Expecting a placeholder
                .error(R.drawable.ic_error_outline)  // Expecting an error drawable
                .into(ivImage);
                
            ivImage.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(itemView.getContext(), 
                    com.visiboard.app.ui.common.ImageViewerActivity.class);
                intent.putExtra(com.visiboard.app.ui.common.ImageViewerActivity.EXTRA_IMAGE_URL, 
                    message.getImageUrl());
                itemView.getContext().startActivity(intent);
            });
        }
    }

    // --- Reaction Logic ---

    private void bindReactions(RecyclerView rvReactions, ChatMessage message) {
        if (message.getReactions() == null || message.getReactions().isEmpty()) {
            rvReactions.setVisibility(View.GONE);
            return;
        }

        rvReactions.setVisibility(View.VISIBLE);
        rvReactions.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
            rvReactions.getContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        
        // Group reactions by type
        java.util.Map<String, Integer> reactionCounts = new java.util.HashMap<>();
        for (String reaction : message.getReactions().values()) {
            reactionCounts.put(reaction, reactionCounts.getOrDefault(reaction, 0) + 1);
        }
        
        // Check if I reacted
        String myReaction = message.getReactions().get(currentUserId);

        ReactionsAdapter adapter = new ReactionsAdapter(reactionCounts, myReaction, message);
        rvReactions.setAdapter(adapter);
    }
    
    private void showReactionPopup(View anchorView, ChatMessage message) {
        Context context = anchorView.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        View popupView = inflater.inflate(R.layout.popup_reactions, null);
        
        android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(
            popupView, 
            ViewGroup.LayoutParams.WRAP_CONTENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT, 
            true
        );
        popupWindow.setElevation(10);
        
        // Setup click listeners
        View.OnClickListener reactionListener = v -> {
            String reaction = "";
            int id = v.getId();
            if (id == R.id.tv_reaction_haha) reaction = "😂";
            else if (id == R.id.tv_reaction_love) reaction = "❤️";
            else if (id == R.id.tv_reaction_wow) reaction = "😮";
            else if (id == R.id.tv_reaction_sad) reaction = "😢";
            else if (id == R.id.tv_reaction_angry) reaction = "😡";
            
            // Toggle reaction
            String currentReaction = message.getReactions() != null ? message.getReactions().get(currentUserId) : null;
            if (reaction.equals(currentReaction)) {
                ChatManager.getInstance().removeReaction(chatId, message.getId(), currentUserId, null);
            } else {
                ChatManager.getInstance().addReaction(chatId, message.getId(), currentUserId, reaction, null);
            }
            popupWindow.dismiss();
        };
        
        popupView.findViewById(R.id.tv_reaction_haha).setOnClickListener(reactionListener);
        popupView.findViewById(R.id.tv_reaction_love).setOnClickListener(reactionListener);
        popupView.findViewById(R.id.tv_reaction_wow).setOnClickListener(reactionListener);
        popupView.findViewById(R.id.tv_reaction_sad).setOnClickListener(reactionListener);
        popupView.findViewById(R.id.tv_reaction_angry).setOnClickListener(reactionListener);
        
        ImageView btnCopy = popupView.findViewById(R.id.btn_copy);
        View divider = popupView.findViewById(R.id.divider_copy);
        
        if (!message.isVoice() && !message.isImage()) { // Text message
            btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Message", message.getText());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show();
                popupWindow.dismiss();
            });
        } else {
            btnCopy.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
        }
        
        popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        
        // Show above the anchor view
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupHeight = popupView.getMeasuredHeight();
        
        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);
        
        popupWindow.showAtLocation(anchorView, android.view.Gravity.NO_GRAVITY, 
            location[0], location[1] - popupHeight - 10);
    }

    private class ReactionsAdapter extends RecyclerView.Adapter<ReactionsAdapter.ViewHolder> {
        private final List<String> reactions;
        private final java.util.Map<String, Integer> counts;
        private final String myReaction;
        
        ReactionsAdapter(java.util.Map<String, Integer> counts, String myReaction, ChatMessage message) {
            this.counts = counts;
            this.reactions = new ArrayList<>(counts.keySet());
            this.myReaction = myReaction;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setBackgroundResource(R.drawable.bg_reaction_chip); 
            tv.setPadding(16, 8, 16, 8);
            tv.setTextSize(12);
            tv.setTextColor(android.graphics.Color.WHITE);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(4, 0, 4, 0);
            tv.setLayoutParams(lp);
            return new ViewHolder(tv);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String reaction = reactions.get(position);
            int count = counts.get(reaction);
            
            holder.text.setText(reaction + (count > 1 ? " " + count : ""));
            
            // Highlight if I reacted
            if (reaction.equals(myReaction)) {
                holder.text.setBackgroundResource(R.drawable.bg_reaction_chip_selected); 
            } else {
                holder.text.setBackgroundResource(R.drawable.bg_reaction_chip);
            }
        }
        
        @Override
        public int getItemCount() {
            return reactions.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            ViewHolder(View itemView) {
                super(itemView);
                text = (TextView) itemView;
            }
        }
    }
}
