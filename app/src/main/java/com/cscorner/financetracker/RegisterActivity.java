package com.cscorner.financetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private AutoCompleteTextView editTextGender;
    private MaterialButton btnRegister;

    private TextView editTextName, editTextMobile, editTextSalary,
            editTextEmail, editTextPassword, editTextAge, textLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // findViewById (Match with XML)
        editTextName = findViewById(R.id.editTextName);
        editTextGender = findViewById(R.id.editTextGender);
        editTextMobile = findViewById(R.id.editTextMobile);
        editTextSalary = findViewById(R.id.editTextSalary);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextAge = findViewById(R.id.editTextAge);

        btnRegister = findViewById(R.id.btnRegister);
        textLogin = findViewById(R.id.textLogin);

        setupGenderDropdown();
        setupListeners();
    }

    private void setupListeners() {

        btnRegister.setOnClickListener(v -> registerUser());

        textLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    // Gender dropdown with icons
    private void setupGenderDropdown() {

        String[] labels = {"Male", "Female", "Other"};
        Integer[] icons = {R.drawable.ic_male, R.drawable.ic_female, R.drawable.ic_other};

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                labels
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {

                LinearLayout layout = new LinearLayout(getContext());
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setPadding(20, 20, 20, 20);

                ImageView icon = new ImageView(getContext());
                icon.setImageResource(icons[position]);
                icon.setLayoutParams(new LinearLayout.LayoutParams(60, 60));

                TextView text = new TextView(getContext());
                text.setText(labels[position]);
                text.setPadding(20, 0, 0, 0);
                text.setTextSize(16);

                layout.addView(icon);
                layout.addView(text);

                return layout;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return getView(position, convertView, parent);
            }
        };

        editTextGender.setAdapter(adapter);

        editTextGender.setOnItemClickListener((parent, view, position, id) ->
                editTextGender.setText(labels[position])
        );
    }

    private void registerUser() {
        String name = editTextName.getText().toString().trim();
        String gender = editTextGender.getText().toString().trim();
        String mobile = editTextMobile.getText().toString().trim();
        String salary = editTextSalary.getText().toString().trim();
        String age = editTextAge.getText().toString().trim();
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (name.isEmpty() || gender.isEmpty() || mobile.isEmpty() ||
                salary.isEmpty() || age.isEmpty() || email.isEmpty() || password.isEmpty()) {

            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Register in Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {

                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserData(user.getEmail(), name, gender, mobile, age, salary);
                        }

                    } else {
                        Toast.makeText(RegisterActivity.this,
                                "Registration Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserData(String email, String name, String gender,
                              String mobile, String age, String salary) {

        DocumentReference userRef = db.collection("Users").document(email);

        Map<String, Object> personalDetails = new HashMap<>();
        personalDetails.put("Name", name);
        personalDetails.put("Gender", gender);
        personalDetails.put("Mobile No", mobile);
        personalDetails.put("Age", age);
        personalDetails.put("Monthly Salary", salary);

        userRef.set(new HashMap<>())  // create base doc
                .addOnSuccessListener(aVoid -> {

                    userRef.collection("Personal Details")
                            .document("Info")
                            .set(personalDetails);

                    Toast.makeText(RegisterActivity.this,
                            "Registration Successful!", Toast.LENGTH_SHORT).show();

                    mAuth.signOut();

                    Intent intent = new Intent(RegisterActivity.this, WelcomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);

                })
                .addOnFailureListener(e ->
                        Toast.makeText(RegisterActivity.this,
                                "Error saving details: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }
}
