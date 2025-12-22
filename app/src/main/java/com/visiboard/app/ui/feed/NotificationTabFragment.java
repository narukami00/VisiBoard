package com.visiboard.app.ui.feed;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.visiboard.app.R;
import com.visiboard.app.data.Notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

public class NotificationTabFragment extends Fragment {
    
    private static final String TAG = "NotificationTabFragment";

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvNotifications;
    private Button btnClearAll;
    private TextView tvNoNotifications;
    private ProgressBar pbLoading;
    
    private NotificationAdapter notificationAdapter;
    private List<Notification> allNotifications = new ArrayList<>();
    
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration notificationListener;
    
    public interface NotificationActionListener {
        void onNotificationClick(Notification notification);
        void onReplyClick(Notification notification);
        void onUnreadCountChanged(int count);
    }
    
    private NotificationActionListener actionListener;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (getParentFragment() instanceof NotificationActionListener) {
            actionListener = (NotificationActionListener) getParentFragment();
        } else if (context instanceof NotificationActionListener) {
            actionListener = (NotificationActionListener) context;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notification_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        swipeRefresh = view.findViewById(R.id.swipe_refresh_notifications);
        rvNotifications = view.findViewById(R.id.rv_notifications_tab);
        btnClearAll = view.findViewById(R.id.btn_clear_notifications_tab);
        tvNoNotifications = view.findViewById(R.id.tv_no_notifications_tab);
        pbLoading = view.findViewById(R.id.pb_loading_notifications);
        
        setupRecyclerView();
        setupSwipeRefresh();
        setupClearButton();
        loadingNotifications();
    }
    
    private void setupRecyclerView() {
        rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        notificationAdapter = new NotificationAdapter(new NotificationAdapter.OnNotificationClickListener() {
            @Override
            public void onNotificationClick(Notification notification) {
                if (actionListener != null) actionListener.onNotificationClick(notification);
            }

            @Override
            public void onReplyClick(Notification notification) {
                if (actionListener != null) actionListener.onReplyClick(notification);
            }
        });
        rvNotifications.setAdapter(notificationAdapter);
        setupSwipeToDelete();
    }
    
    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.secondary, R.color.accent);
        swipeRefresh.setOnRefreshListener(this::loadingNotifications);
    }
    
    private void setupClearButton() {
        btnClearAll.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Clear Notifications")
                .setMessage("Delete all notifications?")
                .setPositiveButton("Clear", (d, w) -> clearAllNotifications())
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void loadingNotifications() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        
        // Use SnapshotListener for real-time updates and badges
        if (notificationListener != null) notificationListener.remove();
        
        swipeRefresh.setRefreshing(true);
        
        notificationListener = db.collection("notifications")
            .whereEqualTo("toUserId", uid)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener((snapshots, e) -> {
                // Critical safety check: Don't process if fragment is destroyed or detached
                if (!isAdded() || getContext() == null || swipeRefresh == null) {
                    Log.d(TAG, "Fragment detached, ignoring notification update");
                    return;
                }
                
                swipeRefresh.setRefreshing(false);
                if (e != null) {
                    Log.e(TAG, "Listen failed.", e);
                    return;
                }
                
                if (snapshots != null && notificationAdapter != null) {
                    List<Notification> newNotifications = new ArrayList<>();
                    int unreadCount = 0;
                    
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Notification n = doc.toObject(Notification.class);
                        if (n != null) {
                            n.setId(doc.getId());
                            newNotifications.add(n);
                            if (!n.isRead()) unreadCount++;
                        }
                    }
                    
                    allNotifications = newNotifications;
                    notificationAdapter.setNotifications(allNotifications);
                    
                    updateEmptyState();
                    
                    if (actionListener != null) {
                        actionListener.onUnreadCountChanged(unreadCount);
                    }
                }
            });
    }
    
    private void updateEmptyState() {
        // Safety check for null views
        if (tvNoNotifications == null || rvNotifications == null || btnClearAll == null) {
            return;
        }
        
        if (allNotifications.isEmpty()) {
            tvNoNotifications.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
            btnClearAll.setVisibility(View.GONE);
        } else {
            tvNoNotifications.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);
            btnClearAll.setVisibility(View.VISIBLE);
        }
    }
    
    private void clearAllNotifications() {
         if (auth.getCurrentUser() == null) return;
         // Batch delete
         com.google.firebase.firestore.WriteBatch batch = db.batch();
         for (Notification n : allNotifications) {
             batch.delete(db.collection("notifications").document(n.getId()));
         }
         batch.commit().addOnSuccessListener(aVoid -> {
             Toast.makeText(getContext(), "Cleared", Toast.LENGTH_SHORT).show();
         }).addOnFailureListener(e -> {
             Toast.makeText(getContext(), "Failed to clear", Toast.LENGTH_SHORT).show();
         });
    }

    private void setupSwipeToDelete() {
        // Only allow swiping to the LEFT (end to start)
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v, @NonNull RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Haptic feedback for deletion
                viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                
                int position = viewHolder.getAdapterPosition();
                Notification item = notificationAdapter.getItem(position);
                notificationAdapter.removeItem(position); // Optimistic removal
                
                Snackbar.make(rvNotifications, "Deleted", Snackbar.LENGTH_LONG)
                        .setAction("Undo", v -> notificationAdapter.restoreItem(item, position))
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar transientBottomBar, int event) {
                                if (event != DISMISS_EVENT_ACTION) {
                                     db.collection("notifications").document(item.getId()).delete();
                                }
                            }
                        }).show();
            }
            
            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                // Return a lower threshold (default is usually 0.5) to make swipe easier/snappier
                return 0.35f;
            }
            
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                     View itemView = viewHolder.itemView;
                    Paint p = new Paint();
                    p.setColor(Color.parseColor("#EF5350")); // Red
                    
                     if (dX < 0) { // Left swipe
                         // Draw red background from right edge to swipe position
                         c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom(), p);
                     }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(rvNotifications);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Remove Firestore listener to prevent memory leaks and crashes
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
        
        // Null out views to prevent accessing them after destruction
        swipeRefresh = null;
        rvNotifications = null;
        btnClearAll = null;
        tvNoNotifications = null;
        pbLoading = null;
        notificationAdapter = null;
    }
}
