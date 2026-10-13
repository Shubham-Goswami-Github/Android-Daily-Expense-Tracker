
package com.cscorner.financetracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class HomeActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private Button btnLogout;
    private Button btnGoToDashboard;
    private Button btnAiAssistant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();

        // Logout button
        btnLogout = findViewById(R.id.btnLogout);

        btnLogout.setOnClickListener(view -> {
            mAuth.signOut();

            Intent intent = new Intent(
                    HomeActivity.this,
                    WelcomeActivity.class
            );

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
            finish();
        });

        // Dashboard button
        btnGoToDashboard = findViewById(R.id.btnGoToDashboard);

        btnGoToDashboard.setOnClickListener(view -> {
            Intent intent = new Intent(
                    HomeActivity.this,
                    DashboardActivity.class
            );

            startActivity(intent);
        });

        // AI Assistant button
        btnAiAssistant = findViewById(R.id.btnAiAssistant);

        btnAiAssistant.setOnClickListener(view -> {
            // Only logged-in users can open the assistant
            if (mAuth.getCurrentUser() == null) {
                Toast.makeText(
                        HomeActivity.this,
                        "Please log in to use AI Assistant.",
                        Toast.LENGTH_SHORT
                ).show();

                Intent intent = new Intent(
                        HomeActivity.this,
                        WelcomeActivity.class
                );

                startActivity(intent);
                finish();
                return;
            }

            Intent intent = new Intent(
                    HomeActivity.this,
                    AiAssistantActivity.class
            );

            startActivity(intent);
        });
    }
}
