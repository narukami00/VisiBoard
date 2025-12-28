package com.visiboard.app.ui.feed;

import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.DocumentSnapshot;
import com.visiboard.app.data.NearbyNote;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeedViewModel extends ViewModel {
    private List<NearbyNote> allPinterestNotes = new ArrayList<>();
    private Set<String> loadedNoteIds = new HashSet<>();
    private DocumentSnapshot lastVisible;
    private boolean isLastPage = false;
    private boolean isLoading = false;
    private boolean isDataLoaded = false;
    
    // Buffer for Randomization Strategy
    private List<NearbyNote> noteBuffer = new ArrayList<>();

    public List<NearbyNote> getAllPinterestNotes() {
        return allPinterestNotes;
    }
    
    public List<NearbyNote> getNoteBuffer() {
        return noteBuffer;
    }
    
    public void addToBuffer(List<NearbyNote> newNotes) {
        noteBuffer.addAll(newNotes);
    }
    
    public List<NearbyNote> popFromBuffer(int count) {
        List<NearbyNote> popped = new ArrayList<>();
        int toRemove = Math.min(count, noteBuffer.size());
        for (int i = 0; i < toRemove; i++) {
            // Always pop from 0 since we shuffle the buffer when adding
            popped.add(noteBuffer.remove(0));
        }
        return popped;
    }
    
    public void shuffleBuffer() {
        java.util.Collections.shuffle(noteBuffer);
    }

    public void setAllPinterestNotes(List<NearbyNote> allPinterestNotes) {
        this.allPinterestNotes = allPinterestNotes;
    }

    public Set<String> getLoadedNoteIds() {
        return loadedNoteIds;
    }

    public void setLoadedNoteIds(Set<String> loadedNoteIds) {
        this.loadedNoteIds = loadedNoteIds;
    }

    public DocumentSnapshot getLastVisible() {
        return lastVisible;
    }

    public void setLastVisible(DocumentSnapshot lastVisible) {
        this.lastVisible = lastVisible;
    }

    public boolean isLastPage() {
        return isLastPage;
    }

    public void setLastPage(boolean lastPage) {
        isLastPage = lastPage;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
    }

    public boolean isDataLoaded() {
        return isDataLoaded;
    }

    public void setDataLoaded(boolean dataLoaded) {
        isDataLoaded = dataLoaded;
    }
    
    public void clear() {
        allPinterestNotes.clear();
        loadedNoteIds.clear();
        noteBuffer.clear();
        lastVisible = null;
        isLastPage = false;
        isLoading = false;
        isDataLoaded = false;
    }
}
