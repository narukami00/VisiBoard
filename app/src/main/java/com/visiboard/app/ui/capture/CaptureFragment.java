package com.visiboard.app.ui.capture;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.visiboard.app.R;
import com.visiboard.app.capture.CaptureViewModel;

public class CaptureFragment extends Fragment {

    private CaptureViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_capture, container, false);
        viewModel = new ViewModelProvider(this).get(CaptureViewModel.class);

        TextView textOutput = v.findViewById(R.id.text_output);
        Button testButton = v.findViewById(R.id.btn_test_ocr);

        // Observe text result
        viewModel.getExtractedText().observe(getViewLifecycleOwner(), textOutput::setText);

        testButton.setOnClickListener(view -> runLocalOcrTest());

        return v;
    }

    private void runLocalOcrTest() {
        // Load demo image from drawable folder (add a test.png)
        InputImage image = InputImage.fromBitmap(
                BitmapFactory.decodeResource(getResources(), R.drawable.test), 0);

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener(result -> {
                    String extracted = result.getText();
                    viewModel.setExtractedText(extracted.isEmpty() ? "No text found" : extracted);
                })
                .addOnFailureListener(e -> viewModel.setExtractedText("Error: " + e.getMessage()));
    }
}
