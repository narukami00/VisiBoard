package com.visiboard.app.utils;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.visiboard.app.R;

public class LoadingHelper {
    
    private Dialog loadingDialog;
    private Context context;
    
    public LoadingHelper(@NonNull Context context) {
        this.context = context;
    }
    
    public void showLoading(String message) {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            return;
        }
        
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null);
        TextView tvMessage = view.findViewById(R.id.tv_loading_message);
        
        if (message != null && !message.isEmpty()) {
            tvMessage.setText(message);
            tvMessage.setVisibility(View.VISIBLE);
        } else {
            tvMessage.setVisibility(View.GONE);
        }
        
        loadingDialog = new Dialog(context);
        loadingDialog.setContentView(view);
        loadingDialog.setCancelable(false);
        
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        loadingDialog.show();
    }
    
    public void showLoading() {
        showLoading(null);
    }
    
    public void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }
    
    public boolean isShowing() {
        return loadingDialog != null && loadingDialog.isShowing();
    }
}
