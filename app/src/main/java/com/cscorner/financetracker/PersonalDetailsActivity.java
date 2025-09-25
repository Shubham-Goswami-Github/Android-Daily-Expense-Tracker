package com.cscorner.financetracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class PersonalDetailsActivity extends AppCompatActivity {

    private TextView tvName, tvAge, tvMobile, tvEmail, tvMonthlySalary, tvGender;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private ImageView ivEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_details);

        tvName = findViewById(R.id.tvName);
        tvAge = findViewById(R.id.tvAge);
        tvMobile = findViewById(R.id.tvMobile);
        tvEmail = findViewById(R.id.tvEmail);
        tvMonthlySalary = findViewById(R.id.tvMonthlySalary);
        tvGender = findViewById(R.id.tvGender);
        ivEdit = findViewById(R.id.ivEdit);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();

        if (user != null && user.getEmail() != null) {
            String email = user.getEmail();
            tvEmail.setText("Email: " + email);
            loadUserDetails(email);

            ivEdit.setOnClickListener(v -> {
                Intent intent = new Intent(PersonalDetailsActivity.this, EditPersonalDetailsActivity.class);
                startActivity(intent);
            });
        } else {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadUserDetails(String email) {
        firestore.collection("Users").document(email)
                .collection("Personal Details").document("Info")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        updateUIWithDetails(documentSnapshot);
                    } else {
                        Toast.makeText(this, "No personal details found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to fetch user details", Toast.LENGTH_SHORT).show()
                );
    }

    private void updateUIWithDetails(DocumentSnapshot doc) {
        tvName.setText("Name: " + getValueOrPlaceholder(doc, "Name"));
        tvAge.setText("Age: " + getValueOrPlaceholder(doc, "Age"));
        tvMobile.setText("Mobile: " + getValueOrPlaceholder(doc, "Mobile No"));
        tvMonthlySalary.setText("Monthly Salary: ₹" + getValueOrPlaceholder(doc, "Monthly Salary"));
        tvGender.setText("Gender: " + getValueOrPlaceholder(doc, "Gender"));
    }

    private String getValueOrPlaceholder(DocumentSnapshot doc, String field) {
        String value = doc.getString(field);
        return value != null ? value : "N/A";
    }
}
