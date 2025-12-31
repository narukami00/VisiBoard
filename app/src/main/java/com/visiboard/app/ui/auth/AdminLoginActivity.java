package com.visiboard.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.visiboard.app.R;
import com.visiboard.app.ui.admin.AdminDashboardActivity;
import com.visiboard.app.utils.AdminCredentials;
import com.visiboard.app.utils.UiHelper;

public class AdminLoginActivity extends AppCompatActivity {

    private EditText idInput, passInput;
    private Button loginBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        idInput = findViewById(R.id.adminIdInput);
        passInput = findViewById(R.id.adminPassInput);
        loginBtn = findViewById(R.id.adminLoginBtn);

        loginBtn.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String id = idInput.getText().toString().trim();
        String pass = passInput.getText().toString().trim();

        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(pass)) {
            UiHelper.showWarning(findViewById(android.R.id.content), "Enter credentials");
            return;
        }

        // Disable button while validating
        loginBtn.setEnabled(false);
        loginBtn.setText("Verifying...");

        AdminCredentials.validateCredentials(id, pass, new AdminCredentials.ValidationCallback() {
            @Override
            public void onResult(boolean isValid) {
                runOnUiThread(() -> {
                    loginBtn.setEnabled(true);
                    loginBtn.setText("Login");
                    
                    if (isValid) {
                        UiHelper.showSuccess(findViewById(android.R.id.content), "Access Granted");
                        startActivity(new Intent(AdminLoginActivity.this, AdminDashboardActivity.class));
                        finish();
                    } else {
                        UiHelper.showError(findViewById(android.R.id.content), "Access Denied");
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    loginBtn.setEnabled(true);
                    loginBtn.setText("Login");
                    UiHelper.showError(findViewById(android.R.id.content), error);
                });
            }
        });
    }
}
