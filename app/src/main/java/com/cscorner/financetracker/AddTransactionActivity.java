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
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
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
    private RadioButton radioIncome, radioExpense;
    private EditText editTextAmount, editTextNote, editTextDateTime;
    private Button btnSubmitTransaction;
    private MaterialCardView cardIncome, cardExpense;
    private ImageView btnBack;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Calendar calendar;

    // Track selected type
    private String selectedType = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);

        // Initialize Firebase and Calendar
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        calendar = Calendar.getInstance();

        // Initialize UI elements
        initializeViews();

        // Setup
        setupMonthDropdown();
        updateDateTimeField();
        setupCardClickListeners();
        setupBackButton();

        // Set default transaction type based on intent
        setDefaultTransactionType();

        // Open datetime picker on click
        editTextDateTime.setOnClickListener(view -> openDateTimePicker());

        // Handle transaction submission
        btnSubmitTransaction.setOnClickListener(view -> saveTransactionToFirestore());
    }

    private void initializeViews() {
        editTextMonth = findViewById(R.id.editTextMonth);
        radioGroupType = findViewById(R.id.radioGroupType);
        radioIncome = findViewById(R.id.radioIncome);
        radioExpense = findViewById(R.id.radioExpense);
        editTextAmount = findViewById(R.id.editTextAmount);
        editTextNote = findViewById(R.id.editTextNote);
        editTextDateTime = findViewById(R.id.editTextDateTime);
        btnSubmitTransaction = findViewById(R.id.btnSubmitTransaction);
        cardIncome = findViewById(R.id.cardIncome);
        cardExpense = findViewById(R.id.cardExpense);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupBackButton() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    private void setupCardClickListeners() {
        // Income card click
        cardIncome.setOnClickListener(v -> {
            selectIncomeType();
        });

        // Expense card click
        cardExpense.setOnClickListener(v -> {
            selectExpenseType();
        });

        // Radio button clicks (for compatibility)
        radioIncome.setOnClickListener(v -> selectIncomeType());
        radioExpense.setOnClickListener(v -> selectExpenseType());
    }

    private void selectIncomeType() {
        selectedType = "Income";
        radioIncome.setChecked(true);
        radioExpense.setChecked(false);

        // Update card appearance
        cardIncome.setStrokeColor(ContextCompat.getColor(this, R.color.primary_blue));
        cardIncome.setStrokeWidth(4);
        cardExpense.setStrokeColor(ContextCompat.getColor(this, R.color.text_secondary));
        cardExpense.setStrokeWidth(0);
    }

    private void selectExpenseType() {
        selectedType = "Expense";
        radioExpense.setChecked(true);
        radioIncome.setChecked(false);

        // Update card appearance
        cardExpense.setStrokeColor(ContextCompat.getColor(this, R.color.primary_blue));
        cardExpense.setStrokeWidth(4);
        cardIncome.setStrokeColor(ContextCompat.getColor(this, R.color.text_secondary));
        cardIncome.setStrokeWidth(0);
    }

    private void setDefaultTransactionType() {
        String transactionType = getIntent().getStringExtra("transactionType");

        if (transactionType != null) {
            switch (transactionType) {
                case "Income":
                    selectIncomeType();
                    break;
                case "Expense":
                    selectExpenseType();
                    break;
            }
        }
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
        editTextMonth.setThreshold(1);

        editTextMonth.setOnClickListener(v -> {
            editTextMonth.showDropDown();
        });

        if (editTextMonth.getText().toString().isEmpty()) {
            String currentMonth = new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
            editTextMonth.setText(currentMonth, false);
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
        String amountStr = editTextAmount.getText().toString().trim();
        String note = editTextNote.getText().toString().trim();
        String dateTime = editTextDateTime.getText().toString().trim();

        // Validate fields
        if (month.isEmpty() || amountStr.isEmpty() || dateTime.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if transaction type is selected
        if (selectedType == null) {
            Toast.makeText(this, "Please select Income or Expense!", Toast.LENGTH_SHORT).show();
            return;
        }

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
        transactionMap.put("type", selectedType);
        transactionMap.put("amount", Double.parseDouble(amountStr));
        transactionMap.put("note", note);
        transactionMap.put("category", note.isEmpty() ? "Uncategorized" : note);
        transactionMap.put("date", date);
        transactionMap.put("time", time);
        transactionMap.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        transactionMap.put("transactionId", docId);

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