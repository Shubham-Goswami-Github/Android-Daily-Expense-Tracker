package com.cscorner.financetracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class DeleteAccountActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private Button btnDeleteAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_account);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        btnDeleteAccount.setOnClickListener(view -> {
            // Show confirmation dialog before deleting the account
            new AlertDialog.Builder(this)
                    .setTitle("Delete Account")
                    .setMessage("Are you sure you want to permanently delete your account?")
                    .setPositiveButton("Yes", (dialog, which) -> deleteUser())
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    private void deleteUser() {
        FirebaseUser user = auth.getCurrentUser();

        if (user != null) {
            String email = user.getEmail();

            // Step 1: Delete the user's data from Firestore
            db.collection("Users").document(email)
                    .delete()
                    .addOnSuccessListener(unused -> {
                        // Step 2: Delete the user's Firebase Authentication account
                        user.delete()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(DeleteAccountActivity.this, "Account deleted successfully", Toast.LENGTH_SHORT).show();
                                        // Redirect to the main activity or login screen
                                        startActivity(new Intent(DeleteAccountActivity.this, MainActivity.class));
                                        finish();
                                    } else {
                                        Toast.makeText(DeleteAccountActivity.this, "Failed to delete Firebase account", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    // Handle Firebase Authentication account deletion failure
                                    Toast.makeText(DeleteAccountActivity.this, "Failed to delete Firebase Authentication account", Toast.LENGTH_SHORT).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        // Handle Firestore document deletion failure
                        Toast.makeText(DeleteAccountActivity.this, "Failed to delete account data", Toast.LENGTH_SHORT).show();
                    });
        } else {
            Toast.makeText(DeleteAccountActivity.this, "No user is logged in!", Toast.LENGTH_SHORT).show();
        }
    }
}
