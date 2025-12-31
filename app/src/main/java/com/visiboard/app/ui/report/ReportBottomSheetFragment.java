package com.visiboard.app.ui.report;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioButton;
import com.google.android.material.textfield.TextInputEditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.visiboard.app.R;
import com.visiboard.app.data.Report;
import com.visiboard.app.data.ReportRepository;

public class ReportBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_TARGET_ID = "target_id";
    private static final String ARG_TARGET_DETAILS = "target_details";
    private static final String ARG_TYPE = "type";
    private static final String ARG_LAT = "lat";
    private static final String ARG_LNG = "lng";

    private String targetId;
    private String targetDetails;
    private String type;
    private double lat;
    private double lng;

    private RadioGroup rgReportReason;
    private TextInputEditText etDescription;
    private Button btnSubmit;
    private ReportRepository repository;

    public static ReportBottomSheetFragment newInstance(String targetId, String targetDetails, String type, double lat, double lng) {
        ReportBottomSheetFragment fragment = new ReportBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TARGET_ID, targetId);
        args.putString(ARG_TARGET_DETAILS, targetDetails);
        args.putString(ARG_TYPE, type);
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            targetId = getArguments().getString(ARG_TARGET_ID);
            targetDetails = getArguments().getString(ARG_TARGET_DETAILS);
            type = getArguments().getString(ARG_TYPE);
            lat = getArguments().getDouble(ARG_LAT);
            lng = getArguments().getDouble(ARG_LNG);
        }
        repository = new ReportRepository();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_report_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rgReportReason = view.findViewById(R.id.rgReportReason);
        etDescription = view.findViewById(R.id.etReportDescription);
        btnSubmit = view.findViewById(R.id.btnSubmitReport);

        rgReportReason.setOnCheckedChangeListener((group, checkedId) -> {
            btnSubmit.setEnabled(true);
        });

        btnSubmit.setOnClickListener(v -> submitReport());
    }

    private void submitReport() {
        int selectedId = rgReportReason.getCheckedRadioButtonId();
        if (selectedId == -1) return;

        RadioButton selectedBtn = getView().findViewById(selectedId);
        String category = selectedBtn.getText().toString().toUpperCase();
        String description = etDescription.getText() != null ? etDescription.getText().toString() : "";
        String reporterId = FirebaseAuth.getInstance().getUid();

        if (reporterId == null) {
            com.visiboard.app.utils.UiHelper.showError(getDialog().getWindow().getDecorView(), "You must be logged in to report.");
            dismiss();
            return;
        }
        Report report = new Report(reporterId, targetId, targetDetails, type, category, description, lat, lng);

        btnSubmit.setEnabled(false);
        btnSubmit.setText("Submitting...");

        repository.submitReport(report)
                .addOnSuccessListener(aVoid -> {
                    if (getContext() != null)
                        com.visiboard.app.utils.UiHelper.showSuccess(getActivity().findViewById(android.R.id.content), "Report submitted. Thank you.");
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null)
                        com.visiboard.app.utils.UiHelper.showError(getActivity().findViewById(android.R.id.content), "Failed to submit report.");
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Submit Report");
                });
    }
}
