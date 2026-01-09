package com.visiboard.app.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

/**
 * TextWatcher that detects @ triggers and manages mention autocomplete.
 * Monitors text changes to find when user is typing a mention.
 */
public class MentionTextWatcher implements TextWatcher {

    private final EditText editText;
    private final MentionCallback callback;
    private boolean isInternalChange = false;

    /**
     * Callback interface for mention events
     */
    public interface MentionCallback {
        /**
         * Called when the mention query changes.
         * @param query The text after @, or empty string to hide popup
         */
        void onMentionQueryChanged(String query);

        /**
         * Called to get the current cursor position for mention operations
         */
        int getCursorPosition();
    }

    public MentionTextWatcher(EditText editText, MentionCallback callback) {
        this.editText = editText;
        this.callback = callback;
    }

    /**
     * Call this before making programmatic text changes to avoid triggering the watcher
     */
    public void setInternalChange(boolean internalChange) {
        this.isInternalChange = internalChange;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Not used
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Not used
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (isInternalChange) {
            return;
        }

        String text = s.toString();
        int cursorPos = editText.getSelectionStart();

        // Find if we're currently in a mention
        String query = MentionHelper.findMentionQuery(text, cursorPos);

        if (query != null) {
            // We're in a mention, notify with the query
            callback.onMentionQueryChanged(query);
        } else {
            // Not in a mention, hide popup
            callback.onMentionQueryChanged("");
        }
    }

    /**
     * Insert a mention at the current position, replacing the @ and query.
     *
     * @param userName The user name to insert (without @)
     */
    public void insertMention(String userName) {
        String text = editText.getText().toString();
        int cursorPos = editText.getSelectionStart();

        int mentionStart = MentionHelper.getMentionStartPos(text, cursorPos);
        if (mentionStart < 0) {
            // Fallback: just append
            isInternalChange = true;
            editText.append("@" + userName + " ");
            isInternalChange = false;
            return;
        }

        // Replace from @ to cursor with the full mention
        String before = text.substring(0, mentionStart);
        String after = cursorPos < text.length() ? text.substring(cursorPos) : "";
        String newText = before + "@" + userName + " " + after;

        isInternalChange = true;
        editText.setText(newText);
        // Position cursor after the mention
        int newCursorPos = mentionStart + userName.length() + 2; // +2 for @ and space
        editText.setSelection(Math.min(newCursorPos, newText.length()));
        isInternalChange = false;

        // Hide popup
        callback.onMentionQueryChanged("");
    }
}
