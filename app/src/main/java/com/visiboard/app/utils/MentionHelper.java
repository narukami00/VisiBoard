package com.visiboard.app.utils;

import android.content.Context;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.visiboard.app.R;
import com.visiboard.app.data.Mention;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static utilities for mention handling - parsing, formatting, and display.
 */
public class MentionHelper {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w\\s]+)");

    /**
     * Listener for mention clicks
     */
    public interface OnMentionClickListener {
        void onMentionClick(String userId);
    }

    /**
     * Convert plain text + mentions list to styled SpannableString.
     * Mentions are colored with the primary color and made clickable (except @following).
     *
     * @param context  Context for color resources
     * @param text     The raw text containing mentions
     * @param mentions List of Mention objects with position info
     * @param listener Callback for when a mention is clicked
     * @return Styled SpannableString with clickable mentions
     */
    public static SpannableString formatMentions(Context context, String text,
                                                  List<Mention> mentions, OnMentionClickListener listener) {
        if (text == null || text.isEmpty()) {
            return new SpannableString("");
        }

        SpannableString spannableString = new SpannableString(text);

        if (mentions == null || mentions.isEmpty()) {
            return spannableString;
        }

        int primaryColor = ContextCompat.getColor(context, R.color.primary);
        int followingColor = ContextCompat.getColor(context, R.color.primaryLight);

        // Sort mentions by startIndex to process them correctly
        List<Mention> sortedMentions = new ArrayList<>(mentions);
        sortedMentions.sort(Comparator.comparingInt(Mention::getStartIndex));

        for (Mention mention : sortedMentions) {
            int start = mention.getStartIndex();
            int end = mention.getEndIndex();

            // Validate indices
            if (start < 0 || end > text.length() || start >= end) {
                continue;
            }

            if (mention.isFollowingMention()) {
                // @following - just color it, no click action
                ForegroundColorSpan colorSpan = new ForegroundColorSpan(followingColor);
                spannableString.setSpan(colorSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // Regular mention - make it clickable and colored
                final String userId = mention.getUserId();
                
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        if (listener != null && userId != null) {
                            listener.onMentionClick(userId);
                        }
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(primaryColor);
                        ds.setUnderlineText(false);
                    }
                };

                spannableString.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return spannableString;
    }

    /**
     * Apply formatted mentions to a TextView and enable link clicking.
     */
    public static void applyMentionsToTextView(Context context, TextView textView, 
                                                String text, List<Mention> mentions,
                                                OnMentionClickListener listener) {
        SpannableString styled = formatMentions(context, text, mentions, listener);
        textView.setText(styled);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Build the display text with mention placeholders replaced by actual names.
     * This is used when inserting a mention into the EditText.
     *
     * @param originalText The current text
     * @param cursorPos    Current cursor position (after @)
     * @param queryLength  Length of the partial query typed
     * @param userName     The user name to insert
     * @return The new text with the mention inserted
     */
    public static String insertMention(String originalText, int cursorPos, 
                                        int queryLength, String userName) {
        if (originalText == null) return "@" + userName + " ";
        
        // Find the @ before cursor
        int atPos = originalText.lastIndexOf('@', cursorPos - 1);
        if (atPos < 0) {
            return originalText + "@" + userName + " ";
        }
        
        String before = originalText.substring(0, atPos);
        String after = cursorPos < originalText.length() ? originalText.substring(cursorPos) : "";
        
        return before + "@" + userName + " " + after;
    }

    /**
     * Find the current mention query based on cursor position.
     * Returns the query string after @ (without the @), or null if not in a mention.
     *
     * @param text      The current text
     * @param cursorPos Current cursor position
     * @return The query string or null
     */
    public static String findMentionQuery(String text, int cursorPos) {
        if (text == null || cursorPos <= 0 || cursorPos > text.length()) {
            return null;
        }

        // Look backwards for @
        int atPos = -1;
        for (int i = cursorPos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '@') {
                atPos = i;
                break;
            }
            // Stop if we hit whitespace before finding @
            if (c == '\n') {
                return null;
            }
        }

        if (atPos < 0) {
            return null;
        }

        // Check if @ is at start or preceded by whitespace (valid mention start)
        if (atPos > 0) {
            char prevChar = text.charAt(atPos - 1);
            if (!Character.isWhitespace(prevChar)) {
                return null;  // @ is in middle of word, not a mention
            }
        }

        // Extract query (text between @ and cursor)
        return text.substring(atPos + 1, cursorPos);
    }

    /**
     * Get the start position of the current mention query (the @ position).
     */
    public static int getMentionStartPos(String text, int cursorPos) {
        if (text == null || cursorPos <= 0) return -1;

        for (int i = cursorPos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '@') {
                // Validate it's a real mention start
                if (i == 0 || Character.isWhitespace(text.charAt(i - 1))) {
                    return i;
                }
                return -1;
            }
            if (c == '\n') {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Create Mention objects from the final text before posting.
     * This parses the text to find @mentions and matches them with known users.
     *
     * @param text          The final text to parse
     * @param selectedUsers List of user IDs and names that were selected during editing
     * @return List of Mention objects with correct positions
     */
    public static List<Mention> createMentionsForPosting(String text, 
                                                          List<SelectedMention> selectedUsers) {
        List<Mention> result = new ArrayList<>();
        if (text == null || selectedUsers == null || selectedUsers.isEmpty()) {
            return result;
        }

        for (SelectedMention selected : selectedUsers) {
            String searchText = "@" + selected.userName;
            int index = text.indexOf(searchText);
            
            while (index >= 0) {
                // Check if this is a complete mention (followed by space or end of text)
                int endIndex = index + searchText.length();
                if (endIndex >= text.length() || 
                    Character.isWhitespace(text.charAt(endIndex)) ||
                    text.charAt(endIndex) == '@') {
                    
                    Mention mention;
                    if (selected.isFollowing) {
                        mention = Mention.forFollowing(index, endIndex);
                    } else {
                        mention = Mention.forUser(selected.userId, selected.userName, index, endIndex);
                    }
                    result.add(mention);
                }
                
                // Look for next occurrence
                index = text.indexOf(searchText, endIndex);
            }
        }

        return result;
    }

    /**
     * Simple data class to track selected mentions during editing
     */
    public static class SelectedMention {
        public final String userId;
        public final String userName;
        public final boolean isFollowing;

        public SelectedMention(String userId, String userName, boolean isFollowing) {
            this.userId = userId;
            this.userName = userName;
            this.isFollowing = isFollowing;
        }
    }
}
