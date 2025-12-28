package com.visiboard.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.visiboard.app.R;
import com.visiboard.app.ui.admin.AdminDashboardActivity;
import com.visiboard.app.utils.AdminCredentials;

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
            Toast.makeText(this, "Enter credentials", Toast.LENGTH_SHORT).show();
            return;
        }

        if (id.equals(AdminCredentials.ADMIN_ID) && pass.equals(AdminCredentials.ADMIN_PASS)) {
            Toast.makeText(this, "Access Granted", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, AdminDashboardActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Access Denied", Toast.LENGTH_SHORT).show();
        }
    }
}
