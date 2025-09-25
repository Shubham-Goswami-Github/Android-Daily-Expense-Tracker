package com.cscorner.financetracker;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout layoutPersonalDetails, layoutChangePassword, layoutDeleteAccount, layoutFeedback, layoutLogout;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        auth = FirebaseAuth.getInstance();

        layoutPersonalDetails = findViewById(R.id.layoutPersonalDetails);
        layoutChangePassword = findViewById(R.id.layoutChangePassword);
        layoutDeleteAccount = findViewById(R.id.layoutDeleteAccount);
        layoutFeedback = findViewById(R.id.layoutFeedback);
        layoutLogout = findViewById(R.id.layoutLogout);

        layoutPersonalDetails.setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, PersonalDetailsActivity.class))
        );

        layoutChangePassword.setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, ChangePasswordActivity.class))
        );

        layoutFeedback.setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, FeedbackActivity.class))
        );

        layoutLogout.setOnClickListener(v -> {
            auth.signOut();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(SettingsActivity.this, LoginActivity.class));
            finish();
        });

        layoutDeleteAccount.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Account")
                    .setMessage("Are you sure you want to permanently delete your account?")
                    .setPositiveButton("Yes", (dialog, which) -> deleteAccount())
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    private void deleteAccount() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            user.delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SettingsActivity.this, LoginActivity.class));
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to delete account: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
        }
    }
}
