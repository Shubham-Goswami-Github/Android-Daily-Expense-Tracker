package com.cscorner.financetracker;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FeedbackActivity extends AppCompatActivity {

    private EditText etFeedback;
    private Button btnSubmitFeedback;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        etFeedback = findViewById(R.id.etFeedback);
        btnSubmitFeedback = findViewById(R.id.btnSubmitFeedback);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnSubmitFeedback.setOnClickListener(view -> {
            String feedback = etFeedback.getText().toString().trim();

            if (feedback.isEmpty()) {
                etFeedback.setError("Please enter your feedback");
                return;
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
                return;
            }

            String email = user.getEmail();
            String uid = user.getUid();

            Map<String, Object> feedbackData = new HashMap<>();
            feedbackData.put("email", email);
            feedbackData.put("feedback", feedback);
            feedbackData.put("timestamp", Timestamp.now());
            feedbackData.put("uid", uid);

            db.collection("Users")
                    .document("Feedback") // parent document
                    .collection("Feedback") // subcollection under 'Users/Feedback'
                    .add(feedbackData)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Feedback submitted", Toast.LENGTH_SHORT).show();
                        etFeedback.setText("");
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to submit feedback", Toast.LENGTH_SHORT).show());
        });
    }
}
