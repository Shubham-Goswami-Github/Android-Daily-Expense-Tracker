package com.cscorner.financetracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cscorner.financetracker.adapter.TransactionAdapter;
import com.cscorner.financetracker.model.Transaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Date;
import java.text.ParseException;

public class DashboardActivity extends AppCompatActivity {

    private Spinner spinnerMonth;
    private TextView tvMonthlySalary, tvTotalBalance, tvTotalIncome, tvTotalExpense;
    private RecyclerView rvTransactions;
    private TextView currentDate;
    private ImageView previousDateButton, nextDateButton;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private TransactionAdapter transactionAdapter;
    private List<Transaction> transactionList;

    private double monthlySalary = 0;
    private double totalIncome = 0;
    private double totalExpense = 0;
    private double totalBalance = 0;

    private GestureDetector gestureDetector;
    private Calendar currentCalendar;
    private boolean balanceUpdated = false;  // Flag to track if balance has been updated for the day

    private ActivityResultLauncher<Intent> addTransactionLauncher;

    public interface OnBalanceFetchedListener {
        void onBalanceFetched(double balance);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // UI Initialization
        spinnerMonth = findViewById(R.id.spinnerMonth);
        tvMonthlySalary = findViewById(R.id.tvMonthlySalary);
        tvTotalBalance = findViewById(R.id.tvTotalBalance);
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpense = findViewById(R.id.tvTotalExpense);
        rvTransactions = findViewById(R.id.rvTransactions);
        currentDate = findViewById(R.id.currentDate);
        previousDateButton = findViewById(R.id.previousDateButton);
        nextDateButton = findViewById(R.id.nextDateButton);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        transactionList = new ArrayList<>();
        transactionAdapter = new TransactionAdapter(transactionList);
        rvTransactions.setAdapter(transactionAdapter);

        currentCalendar = Calendar.getInstance();

        // Spinner setup
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, months);
        spinnerMonth.setAdapter(monthAdapter);

        String currentMonth = new SimpleDateFormat("MMMM", Locale.getDefault()).format(currentCalendar.getTime());
        int currentMonthIndex = java.util.Arrays.asList(months).indexOf(currentMonth);
        spinnerMonth.setSelection(currentMonthIndex);

        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentCalendar.set(Calendar.MONTH, position);
                updateCurrentDate();  // This will also trigger loadDashboardData
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Bottom nav
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_add_transaction) {
                Intent intent = new Intent(DashboardActivity.this, AddTransactionActivity.class);
                addTransactionLauncher.launch(intent);
                return true;
            }  else if (id == R.id.nav_settings) {
                startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        bottomNavigationView.setSelectedItemId(R.id.nav_home);

        addTransactionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        String selectedMonth = result.getData() != null ? result.getData().getStringExtra("selectedMonth") : spinnerMonth.getSelectedItem().toString();
                        spinnerMonth.setSelection(getMonthIndex(selectedMonth));
                        updateCurrentDate();
                    }
                }
        );

        updateCurrentDate();  // Triggers loadDashboardData with date
        previousDateButton.setOnClickListener(v -> changeDate(-1));
        nextDateButton.setOnClickListener(v -> changeDate(1));
    }

    // Method to load transactions for the current day
    private void loadDashboardData(String month, String selectedDateOnly) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No user logged in!", Toast.LENGTH_SHORT).show();
            return;
        }
        String email = currentUser.getEmail();
        if (email == null || email.isEmpty()) {
            Toast.makeText(this, "User email not found", Toast.LENGTH_SHORT).show();
            return;
        }
        // Clear the existing data before loading fresh data
        transactionList.clear();
        transactionAdapter.notifyDataSetChanged(); // Make sure adapter is updated

        // Fetch user's monthly salary first
        db.collection("Users").document(email)
                .collection("Personal Details").document("Info")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String salaryStr = doc.getString("Monthly Salary");
                        try {
                            monthlySalary = salaryStr != null ? Double.parseDouble(salaryStr) : 0.0;
                        } catch (NumberFormatException e) {
                            monthlySalary = 0.0;
                        }
                    } else {
                        monthlySalary = 0.0;
                    }
                    // Once salary is fetched, proceed to load transactions
                    loadTransactions(email, month, selectedDateOnly);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching salary", Toast.LENGTH_SHORT).show();
                    Log.e("Dashboard", "Salary fetch failed", e);
                });
    }

    private void saveBalance(String email, String month, String date, double currentBalance) {
        db.collection("Users").document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(month)
                .document("BalanceHistory")  // BalanceHistory container
                .collection("ByDate")       // Collection for dates
                .document(date)             // Each date has its own balance
                .set(new HashMap<String, Object>() {{
                    put("balance", currentBalance);
                }});
    }

    private void getLastBalance(String email, String month, String date, OnBalanceFetchedListener listener) {
        db.collection("Users").document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(month)
                .document("BalanceHistory")
                .collection("ByDate")
                .document(date)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("balance")) {
                        double balance = documentSnapshot.getDouble("balance");
                        listener.onBalanceFetched(balance);
                    } else {
                        listener.onBalanceFetched(monthlySalary); // fallback
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Dashboard", "Error fetching balance", e);
                    listener.onBalanceFetched(monthlySalary); // fallback
                });
    }

    public interface BalanceCallback {
        void onCallback(double balance);
    }

    // Load transactions based on selected month and date
    private void loadTransactions(String email, String month, String selectedDateOnly) {
        db.collection("Users").document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(month)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    totalIncome = 0;
                    totalExpense = 0;

                    getLastBalance(email, month, selectedDateOnly, lastBalance -> {
                        double currentBalance = monthlySalary; // Start from salary
                        boolean hasTransactionsForDate = false;

                        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

                        Date selectedDate;
                        try {
                            selectedDate = sdf.parse(selectedDateOnly);
                        } catch (ParseException e) {
                            Toast.makeText(this, "Date parse error", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        transactionList.clear();  // Clear list before adding new items

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            try {
                                String date = doc.getString("date");
                                if (date == null) continue;

                                Date txnDate = sdf.parse(date);
                                if (txnDate == null) continue;

                                String type = doc.getString("type") != null ? doc.getString("type").trim().toLowerCase() : "expense";
                                String note = doc.getString("note") != null ? doc.getString("note") : "";
                                String time = doc.getString("time") != null ? doc.getString("time") : "";
                                double amount = 0.0;

                                Object amtObj = doc.get("amount");
                                if (amtObj instanceof Number) {
                                    amount = ((Number) amtObj).doubleValue();
                                } else if (amtObj instanceof String) {
                                    amount = Double.parseDouble((String) amtObj);
                                }

                                // Step 1: For current date display
                                if (sdf.format(txnDate).equals(selectedDateOnly)) {
                                    hasTransactionsForDate = true;

                                    boolean isIncome = type.equals("income");

                                    if (isIncome) totalIncome += amount;
                                    else totalExpense += amount;

                                    // --- MODIFIED PART START ---
                                    Transaction txn = new Transaction(amount, type, date, time, note, isIncome);
                                    txn.setTransactionId(doc.getId()); // Yeh line zaroori hai!
                                    transactionList.add(txn);
                                    // --- MODIFIED PART END ---
                                }

                                // Step 2: Update balance till current date (including earlier dates)
                                if (!txnDate.after(selectedDate)) {
                                    if (type.equals("income")) currentBalance += amount;
                                    else currentBalance -= amount;
                                }

                            } catch (Exception e) {
                                Log.e("Dashboard", "Error processing transaction", e);
                            }
                        }

                        // Final UI update
                        if (hasTransactionsForDate) {
                            saveBalance(email, month, selectedDateOnly, currentBalance);
                        }

                        updateDashboardUI(currentBalance);

                        if (!hasTransactionsForDate) {
                            Toast.makeText(this, "No Transactions Made for today", Toast.LENGTH_SHORT).show();
                        }

                        transactionAdapter.notifyDataSetChanged();
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch transactions", Toast.LENGTH_SHORT).show();
                    Log.e("Dashboard", "Transaction fetch error", e);
                });
    }

    private void updateDashboardUI(double currentBalance) {
        tvMonthlySalary.setText("Monthly Budget: ₹" + String.format("%.2f", monthlySalary));
        tvTotalIncome.setText("Total Income: ₹" + String.format("%.2f", totalIncome));
        tvTotalExpense.setText("Total Expense: ₹" + String.format("%.2f", totalExpense));
        tvTotalBalance.setText("Balance: ₹" + String.format("%.2f", currentBalance));
        transactionAdapter.notifyDataSetChanged();
    }

    // Update the current month when changing the date
    private void changeDate(int offset) {
        currentCalendar.add(Calendar.DAY_OF_MONTH, offset);  // Move the date by +1 or -1 day
        updateCurrentDate();  // Update the current date and fetch transactions for the new month
    }

    // Update the date and month and load the data for that month
    private void updateCurrentDate() {
        SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        SimpleDateFormat queryFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String displayDate = displayFormat.format(currentCalendar.getTime());  // Display formatted date
        String selectedDateOnly = queryFormat.format(currentCalendar.getTime());  // Get date in dd-MM-yyyy format
        currentDate.setText(displayDate);  // Set the formatted date in the UI
        // Reset the balance update flag when changing the month or date
        balanceUpdated = false;
        // Get the selected month from the spinner
        String selectedMonth = spinnerMonth.getSelectedItem().toString();
        // Load transactions for the selected month and date
        loadDashboardData(selectedMonth, selectedDateOnly);  // Pass the selected month and date
    }

    private int getMonthIndex(String monthName) {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        return java.util.Arrays.asList(months).indexOf(monthName);
    }
}