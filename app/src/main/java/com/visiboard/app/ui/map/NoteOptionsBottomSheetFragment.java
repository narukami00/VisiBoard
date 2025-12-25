package com.visiboard.app.ui.map;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.visiboard.app.R;

public class NoteOptionsBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_NOTE_ID = "note_id";
    private static final String ARG_IS_OWNER = "is_owner";
    private static final String ARG_IS_SAVED = "is_saved";
    private static final String ARG_IS_COMMENTS_DISABLED = "is_comments_disabled";

    private String noteId;
    private boolean isOwner;
    private boolean isSaved;
    private boolean isCommentsDisabled;
    private NoteOptionsListener listener;

    public interface NoteOptionsListener {
        void onSaveNote(String noteId);
        void onHideNote(String noteId);
        void onEditNote(String noteId);
        void onDeleteNote(String noteId);
        void onReportNote(String noteId);
        void onToggleComments(String noteId);
    }

    public static NoteOptionsBottomSheetFragment newInstance(String noteId, boolean isOwner) {
        NoteOptionsBottomSheetFragment fragment = new NoteOptionsBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NOTE_ID, noteId);
        args.putBoolean(ARG_IS_OWNER, isOwner);
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(NoteOptionsListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            noteId = getArguments().getString(ARG_NOTE_ID);
            isOwner = getArguments().getBoolean(ARG_IS_OWNER);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note_options_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout optionSave = view.findViewById(R.id.option_save);
        LinearLayout optionHide = view.findViewById(R.id.option_hide);
        LinearLayout optionEdit = view.findViewById(R.id.option_edit);
        LinearLayout optionComments = view.findViewById(R.id.option_comments);
        LinearLayout optionDelete = view.findViewById(R.id.option_delete);
        LinearLayout optionReport = view.findViewById(R.id.option_report);

        ImageView iconSave = view.findViewById(R.id.icon_save);
        TextView textSave = view.findViewById(R.id.text_save);
        
        ImageView iconComments = view.findViewById(R.id.icon_comments);
        TextView textComments = view.findViewById(R.id.text_comments);

        // Initial Loading State
        textSave.setText("Loading...");
        optionSave.setEnabled(false);
        
        textComments.setText("Loading...");
        optionComments.setEnabled(false);

        // FETCH DATA ASYNC
        if (noteId != null) {
            com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) {
                String myId = auth.getCurrentUser().getUid();
                com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();

                // 1. Check Saved Status
                db.collection("users").document(myId).collection("saved_notes").document(noteId).get()
                    .addOnSuccessListener(snap -> {
                        isSaved = snap.exists();
                        if (isSaved) {
                            iconSave.setImageResource(R.drawable.ic_bookmark);
                            textSave.setText("Unsave Note");
                        } else {
                            iconSave.setImageResource(R.drawable.ic_bookmark_outline);
                            textSave.setText("Save Note");
                        }
                        optionSave.setEnabled(true);
                    });

                // 2. Check Comments Status
                db.collection("notes").document(noteId).get()
                    .addOnSuccessListener(snap -> {
                        isCommentsDisabled = snap.getBoolean("commentsDisabled") != null && snap.getBoolean("commentsDisabled");
                         if (isCommentsDisabled) {
                             textComments.setText("Turn on comments");
                        } else {
                             textComments.setText("Turn off comments");
                        }
                        optionComments.setEnabled(true);
                    });
            }
        }

        // Visibility Logic
        if (isOwner) {
            optionEdit.setVisibility(View.VISIBLE);
            optionDelete.setVisibility(View.VISIBLE);
            optionComments.setVisibility(View.VISIBLE);
            optionReport.setVisibility(View.GONE);
        } else {
            optionEdit.setVisibility(View.GONE);
            optionDelete.setVisibility(View.GONE);
            optionComments.setVisibility(View.GONE);
            optionReport.setVisibility(View.VISIBLE);
        }

        optionSave.setOnClickListener(v -> {
            if (listener != null) listener.onSaveNote(noteId);
            dismiss();
        });

        optionHide.setOnClickListener(v -> {
            if (listener != null) listener.onHideNote(noteId);
            dismiss();
        });

        optionEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEditNote(noteId);
            dismiss();
        });

        optionComments.setOnClickListener(v -> {
            if (listener != null) listener.onToggleComments(noteId);
            dismiss();
        });

        optionDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteNote(noteId);
            dismiss();
        });

        optionReport.setOnClickListener(v -> {
            if (listener != null) listener.onReportNote(noteId);
            dismiss();
        });
    }
}
