package com.cscorner.financetracker;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditPersonalDetailsActivity extends AppCompatActivity {

    private EditText etName, etAge, etMobile, etGender, etSalary;
    private Button btnSave;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_personal_details);

        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        etMobile = findViewById(R.id.etMobile);
        etGender = findViewById(R.id.etGender);
        etSalary = findViewById(R.id.etSalary);
        btnSave = findViewById(R.id.btnSave);

        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        String email = auth.getCurrentUser().getEmail();

        // Load existing details
        firestore.collection("Users").document(email)
                .collection("Personal Details").document("Info")
                .get()
                .addOnSuccessListener(doc -> {
                    etName.setText(doc.getString("Name"));
                    etAge.setText(doc.getString("Age"));
                    etMobile.setText(doc.getString("Mobile No"));
                    etGender.setText(doc.getString("Gender"));
                    etSalary.setText(doc.getString("Monthly Salary"));
                });

        btnSave.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Confirm")
                    .setMessage("Are you sure you want to save these changes?")
                    .setPositiveButton("Yes", (dialog, which) -> updateDetails(email))
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    private void updateDetails(String email) {
        String name = etName.getText().toString();
        String age = etAge.getText().toString();
        String mobile = etMobile.getText().toString();
        String gender = etGender.getText().toString();
        String salary = etSalary.getText().toString();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(age) || TextUtils.isEmpty(mobile)
                || TextUtils.isEmpty(gender) || TextUtils.isEmpty(salary)) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updated = new HashMap<>();
        updated.put("Name", name);
        updated.put("Age", age);
        updated.put("Mobile No", mobile);
        updated.put("Gender", gender);
        updated.put("Monthly Salary", salary);

        firestore.collection("Users").document(email)
                .collection("Personal Details").document("Info")
                .set(updated)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Details updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update details", Toast.LENGTH_SHORT).show()
                );
    }
}
