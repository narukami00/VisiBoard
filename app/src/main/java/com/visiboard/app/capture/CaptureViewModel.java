package com.capture;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class CaptureViewModel extends ViewModel {
    private MutableLiveData<String> extractedText = new MutableLiveData<>();
    public LiveData<String> getExtractedText() { return extractedText; }

    public void setExtractedText(String t) { extractedText.setValue(t); }

    // later: methods to save note, call summarizer adapter, enqueue upload
}

