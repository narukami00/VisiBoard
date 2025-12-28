package com.visiboard.app.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class UploadNoteWorker extends Worker {
    public UploadNoteWorker(@NonNull Context ctx, @NonNull WorkerParameters params) { super(ctx, params); }
    @NonNull
    @Override
    public Result doWork() {
        // placeholder for upload logic
        Log.d("UploadNoteWorker","upload stub");
        return Result.success();
    }
}
