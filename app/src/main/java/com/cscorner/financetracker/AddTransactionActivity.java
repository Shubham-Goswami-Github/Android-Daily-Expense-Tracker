package com.cscorner.financetracker;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddTransactionActivity extends AppCompatActivity {

    private AutoCompleteTextView editTextMonth;
    private RadioGroup radioGroupType;
    private EditText editTextAmount, editTextNote, editTextDateTime;
    private Button btnSubmitTransaction;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Calendar calendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);

        // Initialize Firebase and Calendar
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        calendar = Calendar.getInstance();

        // Initialize UI elements
        editTextMonth = findViewById(R.id.editTextMonth);
        radioGroupType = findViewById(R.id.radioGroupType);
        editTextAmount = findViewById(R.id.editTextAmount);
        editTextNote = findViewById(R.id.editTextNote);
        editTextDateTime = findViewById(R.id.editTextDateTime);
        btnSubmitTransaction = findViewById(R.id.btnSubmitTransaction);

        // Setup dropdown and current values
        setupMonthDropdown();
        updateDateTimeField();

        // Open datetime picker on click
        editTextDateTime.setOnClickListener(view -> openDateTimePicker());

        // Handle transaction submission
        btnSubmitTransaction.setOnClickListener(view -> saveTransactionToFirestore());
    }

    private void setupMonthDropdown() {
        String[] months = {
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
        };

        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                months
        );

        editTextMonth.setAdapter(monthAdapter);

        // 👇 Threshold 1 is better for performance
        editTextMonth.setThreshold(1);

        // 👇 Show dropdown when clicked
        editTextMonth.setOnClickListener(v -> {
            editTextMonth.showDropDown();  // Forcefully show dropdown
        });

        // 👇 Set default current month only if not already set
        if (editTextMonth.getText().toString().isEmpty()) {
            String currentMonth = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
            editTextMonth.setText(currentMonth, false);  // 👈 false = don't filter dropdown
        }
    }


    private void openDateTimePicker() {
        DatePickerDialog datePicker = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth);
            new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                updateDateTimeField();
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePicker.show();
    }

    private void updateDateTimeField() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        editTextDateTime.setText(dateFormat.format(calendar.getTime()));
    }

    private void saveTransactionToFirestore() {
        String month = editTextMonth.getText().toString().trim();
        int selectedRadioId = radioGroupType.getCheckedRadioButtonId();
        String amountStr = editTextAmount.getText().toString().trim();
        String note = editTextNote.getText().toString().trim();
        String dateTime = editTextDateTime.getText().toString().trim();

        // Validate fields
        if (month.isEmpty() || selectedRadioId == -1 || amountStr.isEmpty() || dateTime.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields!", Toast.LENGTH_SHORT).show();
            return;
        }

        String transactionType = ((RadioButton) findViewById(selectedRadioId)).getText().toString();
        String email = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : null;

        if (email == null) {
            Toast.makeText(this, "Error: No user logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Split date and time
        String[] parts = dateTime.split(" ");
        String date = parts.length > 0 ? parts[0] : "";
        String time = parts.length > 1 ? parts[1] : "";

        // Unique doc ID
        String docId = date + " " + time + " " + System.currentTimeMillis();

        // Build transaction data
        Map<String, Object> transactionMap = new HashMap<>();
        transactionMap.put("type", transactionType);
        transactionMap.put("amount", Double.parseDouble(amountStr));
        transactionMap.put("note", note);
        transactionMap.put("category", note.isEmpty() ? "Uncategorized" : note);
        transactionMap.put("date", date);
        transactionMap.put("time", time);
        transactionMap.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());

        // Save to Firestore
        DocumentReference docRef = db.collection("Users").document(email)
                .collection("Financial Details").document("Monthly Expenditure")
                .collection(month).document(docId);

        Log.d("FirestoreDebug", "Firestore Path: " + docRef.getPath());

        docRef.set(transactionMap)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(AddTransactionActivity.this, "Transaction Added Successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e("FirestoreError", "Error adding transaction", e);
                    Toast.makeText(AddTransactionActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
