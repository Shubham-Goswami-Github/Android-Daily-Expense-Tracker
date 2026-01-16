package com.cscorner.financetracker;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout layoutPersonalDetails, layoutChangePassword, layoutDeleteAccount, layoutFeedback;
    private MaterialCardView layoutLogout; // Changed to MaterialCardView
    private ImageView btnBack;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdge();
        setContentView(R.layout.activity_settings);
        setupWindowInsets();

        auth = FirebaseAuth.getInstance();

        initializeViews();
        setupClickListeners();
    }

    private void setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false);
        }
    }

    private void setupWindowInsets() {
        View headerLayout = findViewById(R.id.headerLayout);
        if (headerLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(headerLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        v.getPaddingLeft(),
                        systemBars.top + 16,
                        v.getPaddingRight(),
                        v.getPaddingBottom()
                );
                return insets;
            });
        }
    }

    private void initializeViews() {
        btnBack = findViewById(R.id.btnBack);
        layoutPersonalDetails = findViewById(R.id.layoutPersonalDetails);
        layoutChangePassword = findViewById(R.id.layoutChangePassword);
        layoutDeleteAccount = findViewById(R.id.layoutDeleteAccount);
        layoutFeedback = findViewById(R.id.layoutFeedback);
        layoutLogout = findViewById(R.id.layoutLogout); // Now MaterialCardView
    }

    private void setupClickListeners() {
        // Back button
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }

        // Personal Details
        layoutPersonalDetails.setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, PersonalDetailsActivity.class))
        );

        // Change Password
        layoutChangePassword.setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, ChangePasswordActivity.class))
        );

        // Feedback
        layoutFeedback.setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, FeedbackActivity.class))
        );

        // Logout
        layoutLogout.setOnClickListener(v -> showLogoutDialog());

        // Delete Account
        layoutDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setIcon(R.drawable.ic_logout)
                .setPositiveButton("Logout", (dialog, which) -> {
                    auth.signOut();
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
                .setTitle("⚠️ Delete Account")
                .setMessage("Are you sure you want to permanently delete your account?\n\nThis action cannot be undone and all your data will be lost forever.")
                .setIcon(R.drawable.ic_delete)
                .setPositiveButton("Delete", (dialog, which) -> deleteAccount())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAccount() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            // Show loading
            AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle("Deleting Account")
                    .setMessage("Please wait...")
                    .setCancelable(false)
                    .create();
            loadingDialog.show();

            user.delete()
                    .addOnSuccessListener(aVoid -> {
                        loadingDialog.dismiss();
                        Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        // If delete fails, user may need to re-authenticate
                        if (e.getMessage() != null && e.getMessage().contains("recent")) {
                            showReAuthDialog();
                        } else {
                            Toast.makeText(this, "Failed to delete account: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
        }
    }

    private void showReAuthDialog() {
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
                .setTitle("Re-authentication Required")
                .setMessage("For security reasons, please logout and login again before deleting your account.")
                .setPositiveButton("Logout", (dialog, which) -> {
                    auth.signOut();
                    Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}