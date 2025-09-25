package com.cscorner.financetracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private EditText editTextEmail, editTextPassword, editTextName, editTextMobile, editTextSalary, editTextAge;
    private AutoCompleteTextView editTextGender;
    private Button btnRegister;
    private TextView textLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        editTextName = findViewById(R.id.editTextName);
        editTextGender = findViewById(R.id.editTextGender);
        editTextMobile = findViewById(R.id.editTextMobile);
        editTextSalary = findViewById(R.id.editTextSalary);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextAge = findViewById(R.id.editTextAge);
        btnRegister = findViewById(R.id.btnRegister);
        textLogin = findViewById(R.id.textLogin);

        // Set Gender Dropdown Options
        String[] genders = {"Male", "Female", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, genders);
        editTextGender.setAdapter(adapter);

        btnRegister.setOnClickListener(view -> registerUser());
        TextView titleText = findViewById(R.id.textLogin);
        String styledText = "<font color='#000000'>Already have an account?</font> <font color='#B9160A'>Login</font>";
        titleText.setText(Html.fromHtml(styledText));
        textLogin.setOnClickListener(view -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String name = editTextName.getText().toString().trim();
        String gender = editTextGender.getText().toString().trim();
        String mobile = editTextMobile.getText().toString().trim();
        String salary = editTextSalary.getText().toString().trim();
        String age = editTextAge.getText().toString().trim();
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (name.isEmpty() || gender.isEmpty() || mobile.isEmpty() || salary.isEmpty() || age.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Firebase Authentication - Register User
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserData(user.getEmail(), name, gender, mobile, age, salary);
                        }
                    } else {
                        Toast.makeText(RegisterActivity.this, "Registration Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserData(String email, String name, String gender, String mobile, String age, String salary) {
        DocumentReference userRef = db.collection("Users").document(email);

        // Personal Details
        Map<String, Object> personalDetails = new HashMap<>();
        personalDetails.put("Name", name);
        personalDetails.put("Gender", gender);
        personalDetails.put("Mobile No", mobile);
        personalDetails.put("Age", age);
        personalDetails.put("Monthly Salary", salary);

        // Create base User document and nested collections
        userRef.set(new HashMap<>())
                .addOnSuccessListener(aVoid -> {
                    userRef.collection("Personal Details").document("Info").set(personalDetails);

                    // Dummy transaction removed here ✅

                    // Redirect to WelcomeActivity after saving personal details
                    mAuth.signOut(); // Sign out after registration
                    Toast.makeText(RegisterActivity.this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(RegisterActivity.this, WelcomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(RegisterActivity.this, "Error saving personal details: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
