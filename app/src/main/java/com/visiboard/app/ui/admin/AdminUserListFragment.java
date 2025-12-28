package com.visiboard.app.ui.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.visiboard.app.R;
import com.visiboard.app.data.ReportRepository;
import com.visiboard.app.data.UserInfo;

public class AdminUserListFragment extends Fragment implements AdminUserAdapter.OnUserClickListener {

    private RecyclerView recyclerView;
    private AdminUserAdapter adapter;
    private EditText searchField;
    private Spinner filterSpinner;
    private FirebaseFirestore db;
    private ReportRepository repository;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private android.widget.ProgressBar progressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_users, container, false);
        
        db = FirebaseFirestore.getInstance();
        repository = new ReportRepository();

        recyclerView = view.findViewById(R.id.usersListRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        searchField = view.findViewById(R.id.searchUserField);
        filterSpinner = view.findViewById(R.id.filterSpinner);
        
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        progressBar = view.findViewById(R.id.progressBar);
        
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.accent);
            swipeRefresh.setOnRefreshListener(() -> {
                String filter = filterSpinner.getSelectedItem() != null ? filterSpinner.getSelectedItem().toString() : "All";
                setupAdapter(searchField.getText().toString(), filter);
            });
        }

        setupSpinner();
        setupSearch();
        progressBar.setVisibility(android.view.View.VISIBLE);
        setupAdapter("", "All"); // Initial load

        return view;
    }

    private void setupSpinner() {
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.admin_user_filters, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(spinnerAdapter);

        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String filter = parent.getItemAtPosition(position).toString();
                setupAdapter(searchField.getText().toString(), filter);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupSearch() {
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String filter = filterSpinner.getSelectedItem() != null ? filterSpinner.getSelectedItem().toString() : "All";
                setupAdapter(s.toString(), filter);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private com.google.firebase.firestore.ListenerRegistration userListener;

    private void setupAdapter(String searchText, String filter) {
        Query query = db.collection("users");

        // Apply Filter
        if (filter.equals("Banned")) {
            query = query.whereEqualTo("banned", true);
        } else if (filter.equals("Restricted")) {
            query = query.whereEqualTo("restricted", true);
        } else if (filter.equals("Warned")) {
            query = query.whereEqualTo("warned", true);
        }

        // Apply Search
        if (!searchText.isEmpty()) {
            query = query.orderBy("name").startAt(searchText).endAt(searchText + "\uf8ff");
        } else {
             if (searchText.isEmpty() && filter.equals("All")) {
                 query = query.orderBy("name");
             }
        }

        if (userListener != null) {
            userListener.remove();
        }

        if (adapter == null) {
            adapter = new AdminUserAdapter(this);
        }
        if (recyclerView.getAdapter() == null) {
            recyclerView.setAdapter(adapter);
        }

        userListener = query.addSnapshotListener((snapshots, e) -> {
            // Hide loading indicators
            if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            
            if (e != null) {
                Toast.makeText(getContext(), "Error loading users: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (snapshots != null) {
                java.util.List<UserInfo> userList = new java.util.ArrayList<>();
                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                    UserInfo user = doc.toObject(UserInfo.class);
                    user.setUserId(doc.getId()); // Set the document ID as userId
                    userList.add(user);
                }
                adapter.setUsers(userList);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        String filter = filterSpinner.getSelectedItem() != null ? filterSpinner.getSelectedItem().toString() : "All";
        String searchText = searchField.getText().toString();
        setupAdapter(searchText, filter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }

    @Override
    public void onUserClick(UserInfo user) {
        // Navigate to user detail fragment
        AdminUserDetailFragment detailFragment = AdminUserDetailFragment.newInstance(user.getUserId());
        requireActivity().getSupportFragmentManager().beginTransaction()
            .replace(R.id.adminFragmentContainer, detailFragment)
            .addToBackStack(null)
            .commit();
    }

    private void showActionDialog(UserInfo user) {
        String[] actions = {"View Profile", "Ban User", "Unban User", "Restrict User", "Unrestrict", "Warn User"};
        
        new AlertDialog.Builder(getContext())
                .setTitle("Manage User: " + user.getName())
                .setItems(actions, (dialog, which) -> {
                     ReportRepository.OnActionListener listener = new ReportRepository.OnActionListener() {
                        @Override public void onSuccess() { Toast.makeText(getContext(), "Success", Toast.LENGTH_SHORT).show(); }
                        @Override public void onFailure(String e) { Toast.makeText(getContext(), "Error: " + e, Toast.LENGTH_SHORT).show(); }
                    };

                    switch (which) {
                        case 0: // View Profile
                             new AlertDialog.Builder(getContext())
                                .setTitle("Profile Details")
                                .setMessage("ID: " + user.getUserId() + "\nEmail: " + user.getEmail() + "\nStatus: " + (user.isBanned() ? "Banned" : "Active"))
                                .setPositiveButton("OK", null)
                                .show();
                            break;
                        case 1: // Ban
                            repository.updateUserStatus(user.getUserId(), "banned", true, listener);
                            repository.notifyUser(user.getUserId(), "You have been banned.", "admin");
                            break;
                        case 2: // Unban
                            repository.updateUserStatus(user.getUserId(), "banned", false, listener);
                            repository.notifyUser(user.getUserId(), "Your ban has been lifted.", "admin");
                            break;
                        case 3: // Restrict
                            repository.updateUserStatus(user.getUserId(), "restricted", true, listener);
                            repository.notifyUser(user.getUserId(), "You are restricted.", "admin");
                            break;
                        case 4: // Unrestrict
                            repository.updateUserStatus(user.getUserId(), "restricted", false, listener);
                            repository.notifyUser(user.getUserId(), "Restriction lifted.", "admin");
                            break;
                        case 5: // Warn
                            repository.updateUserStatus(user.getUserId(), "warned", true, listener);
                            repository.notifyUser(user.getUserId(), "Warning issued.", "admin");
                            break;
                    }
                })
                .show();
    }
}
