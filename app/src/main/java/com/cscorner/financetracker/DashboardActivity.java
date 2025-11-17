package com.cscorner.financetracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cscorner.financetracker.adapter.TransactionAdapter;
import com.cscorner.financetracker.model.Transaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

// MPAndroidChart
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.Entry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Date;
import java.text.ParseException;
import java.util.Collections;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private Spinner spinnerMonth;
    private TextView tvMonthlySalary, tvTotalBalance, tvTotalIncome, tvTotalExpense;
    private RecyclerView rvTransactions;
    private TextView currentDate;
    private ImageView previousDateButton, nextDateButton;
    private Button btnToggleStats;
    private CardView chartsCard;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private TransactionAdapter transactionAdapter;
    private List<Transaction> transactionList;

    private double monthlySalary = 0;
    private double totalIncome = 0;
    private double totalExpense = 0;

    private Calendar currentCalendar;

    private ActivityResultLauncher<Intent> addTransactionLauncher;

    // Charts
    private LineChart lineChartDailyTotals;
    private BarChart barChartDailyAverage;

    // Monthly transactions used for charts
    private final List<Transaction> monthTransactionList = new ArrayList<>();

    // Colors
    private final int blueIncome = Color.parseColor("#1D4ED8");
    private final int redExpense = Color.parseColor("#DC2626");
    private final int textPrimary = Color.parseColor("#0F172A");
    private final int gridColor = Color.parseColor("#E5E7EB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // UI
        spinnerMonth = findViewById(R.id.spinnerMonth);
        tvMonthlySalary = findViewById(R.id.tvMonthlySalary);
        tvTotalBalance = findViewById(R.id.tvTotalBalance);
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpense = findViewById(R.id.tvTotalExpense);
        rvTransactions = findViewById(R.id.rvTransactions);
        currentDate = findViewById(R.id.currentDate);
        previousDateButton = findViewById(R.id.previousDateButton);
        nextDateButton = findViewById(R.id.nextDateButton);
        btnToggleStats = findViewById(R.id.btnToggleStats);
        chartsCard = findViewById(R.id.chartsCard);

        lineChartDailyTotals = findViewById(R.id.lineChartDailyTotals);
        barChartDailyAverage = findViewById(R.id.barChartDailyAverage);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setHasFixedSize(true);
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

        spinnerMonth.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                currentCalendar.set(Calendar.MONTH, position);
                updateCurrentDate();  // triggers loadDashboardData
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
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
            } else if (id == R.id.nav_settings) {
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

        // Charts appearance
        setupLineChart();
        setupBarChart();

        // Toggle button
        btnToggleStats.setOnClickListener(v -> toggleCharts());

        updateCurrentDate();  // Triggers loadDashboardData with date
        previousDateButton.setOnClickListener(v -> changeDate(-1));
        nextDateButton.setOnClickListener(v -> changeDate(1));
    }

    private void toggleCharts() {
        if (chartsCard.getVisibility() == View.VISIBLE) {
            chartsCard.setVisibility(View.GONE);
            btnToggleStats.setText("View Statistics");
        } else {
            chartsCard.setVisibility(View.VISIBLE);
            btnToggleStats.setText("Hide Statistics");
            // Rebuild charts when shown
            updateChartsForMonth(monthTransactionList);
        }
    }

    // Load transactions for selected month/day and prepare charts
    private long lastLoadTime = 0;
    private static final long LOAD_COOLDOWN_MS = 500;

    private void loadDashboardData(String month, String selectedDateOnly) {

        long now = System.currentTimeMillis();
        if (now - lastLoadTime < LOAD_COOLDOWN_MS) {
            Log.d("Dashboard", "Skipping duplicate LOAD...");
            return;
        }
        lastLoadTime = now;
        Log.d("Dashboard", "Loading data for month=" + month + " date=" + selectedDateOnly);

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

        transactionList.clear();
        transactionAdapter.notifyDataSetChanged();
        monthTransactionList.clear();
        totalIncome = 0;
        totalExpense = 0;

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

                    loadTransactions(email, month, selectedDateOnly);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching salary", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadTransactions(String email, String month, String selectedDateOnly) {
        db.collection("Users").document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(month)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                    Date selectedDate;
                    try {
                        selectedDate = sdf.parse(selectedDateOnly);
                    } catch (ParseException e) {
                        Toast.makeText(this, "Date parse error", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        try {
                            String date = doc.getString("date");
                            if (date == null) continue;

                            String type = doc.getString("type") != null ? doc.getString("type").trim() : "Expense";
                            String note = doc.getString("note") != null ? doc.getString("note") : "";
                            String time = doc.getString("time") != null ? doc.getString("time") : "";
                            double amount = 0.0;

                            Object amtObj = doc.get("amount");
                            if (amtObj instanceof Number) {
                                amount = ((Number) amtObj).doubleValue();
                            } else if (amtObj instanceof String) {
                                try { amount = Double.parseDouble((String) amtObj); } catch (Exception ex) { amount = 0.0; }
                            }

                            boolean isIncome = type.equalsIgnoreCase("Income") || type.equalsIgnoreCase("income");

                            Transaction txn = new Transaction(amount, type, date, time, note, isIncome);
                            txn.setTransactionId(doc.getId());

                            // Add to month list (for charts)
                            monthTransactionList.add(txn);

                            // For selected date only: list + totals
                            Date txnDate = null;
                            try { txnDate = sdf.parse(date); } catch (ParseException ignored) {}
                            if (txnDate != null && sdf.format(txnDate).equals(selectedDateOnly)) {
                                transactionList.add(txn);
                                if (isIncome) totalIncome += amount;
                                else totalExpense += amount;
                            }

                        } catch (Exception e) {
                            Log.e("Dashboard", "Error processing transaction doc", e);
                        }
                    }

                    // Compute balance up to selected date
                    double balance = computeBalanceUpToDate(monthTransactionList, selectedDate);

                    // Update UI
                    updateDashboardUI(balance);
                    transactionAdapter.notifyDataSetChanged();

                    // If charts visible, update them; otherwise they'll update when toggled
                    if (chartsCard.getVisibility() == View.VISIBLE) {
                        updateChartsForMonth(monthTransactionList);
                    }

                    if (transactionList.isEmpty()) {
                        Toast.makeText(this, "No Transactions Made for today", Toast.LENGTH_SHORT).show();
                    }

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch transactions", Toast.LENGTH_SHORT).show();
                    Log.e("Dashboard", "Transaction fetch error", e);
                });
    }

    // Balance starting from monthlySalary and applying transactions up to selected date
    private double computeBalanceUpToDate(List<Transaction> monthTxns, Date selectedDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        double balance = monthlySalary;

        // Sort by date
        try {
            Collections.sort(monthTxns, (a, b) -> {
                try {
                    Date da = sdf.parse(a.getDate());
                    Date db = sdf.parse(b.getDate());
                    long ra = da != null ? da.getTime() : 0;
                    long rb = db != null ? db.getTime() : 0;
                    return Long.compare(ra, rb);
                } catch (Exception e) {
                    return 0;
                }
            });
        } catch (Exception ignored) {}

        for (Transaction t : monthTxns) {
            try {
                Date d = sdf.parse(t.getDate());
                if (d == null) continue;
                if (!d.after(selectedDate)) {
                    if (t.isIncome()) balance += t.getAmount();
                    else balance -= t.getAmount();
                }
            } catch (ParseException ignored) {}
        }
        return balance;
    }

    // Charts setup
    private void setupLineChart() {
        lineChartDailyTotals.getDescription().setEnabled(false);
        lineChartDailyTotals.setDrawGridBackground(false);
        lineChartDailyTotals.setNoDataText("No daily data");
        lineChartDailyTotals.setNoDataTextColor(textPrimary);

        Legend l = lineChartDailyTotals.getLegend();
        l.setEnabled(true);
        l.setTextColor(textPrimary);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);

        XAxis x = lineChartDailyTotals.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setDrawGridLines(false);
        x.setTextColor(textPrimary);

        YAxis left = lineChartDailyTotals.getAxisLeft();
        left.setTextColor(textPrimary);
        left.setDrawGridLines(true);
        left.setGridColor(gridColor);
        left.setAxisMinimum(0f);
        lineChartDailyTotals.getAxisRight().setEnabled(false);
    }

    private void setupBarChart() {
        barChartDailyAverage.getDescription().setEnabled(false);
        barChartDailyAverage.setNoDataText("No average data");
        barChartDailyAverage.setNoDataTextColor(textPrimary);

        Legend l = barChartDailyAverage.getLegend();
        l.setEnabled(false);

        XAxis x = barChartDailyAverage.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setGranularity(1f);
        x.setTextColor(textPrimary);

        YAxis left = barChartDailyAverage.getAxisLeft();
        left.setTextColor(textPrimary);
        left.setDrawGridLines(true);
        left.setGridColor(gridColor);
        left.setAxisMinimum(0f);
        barChartDailyAverage.getAxisRight().setEnabled(false);
    }

    // Build daily totals + averages and refresh both charts
    private void updateChartsForMonth(List<Transaction> monthTxns) {
        if (monthTxns == null) return;

        // Determine days in current selected month
        Calendar cal = (Calendar) currentCalendar.clone();
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int selectedDay = cal.get(Calendar.DAY_OF_MONTH);

        double[] incomePerDay = new double[daysInMonth + 1];   // 1..days
        double[] expensePerDay = new double[daysInMonth + 1];

        double totalIncomeMonth = 0.0;
        double totalExpenseMonth = 0.0;

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        Calendar tcal = Calendar.getInstance();

        for (Transaction t : monthTxns) {
            String dateStr = t.getDate();
            if (dateStr == null) continue;
            try {
                Date d = sdf.parse(dateStr);
                if (d == null) continue;
                tcal.setTime(d);
                int day = tcal.get(Calendar.DAY_OF_MONTH);
                double amt = t.getAmount();
                if (t.isIncome()) {
                    incomePerDay[day] += amt;
                    totalIncomeMonth += amt;
                } else {
                    expensePerDay[day] += amt;
                    totalExpenseMonth += amt;
                }
            } catch (Exception ignored) {}
        }

        // Daily averages (upto selected day)
        int denomDays = Math.max(1, selectedDay);
        double avgIncomePerDay = totalIncomeMonth / denomDays;
        double avgExpensePerDay = totalExpenseMonth / denomDays;

        // 1) LineChart: daily totals line for income and expense + avg limit lines
        List<Entry> incomeEntries = new ArrayList<>();
        List<Entry> expenseEntries = new ArrayList<>();
        for (int day = 1; day <= daysInMonth; day++) {
            incomeEntries.add(new Entry(day, (float) incomePerDay[day]));
            expenseEntries.add(new Entry(day, (float) expensePerDay[day]));
        }

        if (incomeEntries.isEmpty() && expenseEntries.isEmpty()) {
            lineChartDailyTotals.clear();
        } else {
            LineDataSet setIncome = new LineDataSet(incomeEntries, "Income/day");
            setIncome.setColor(blueIncome);
            setIncome.setLineWidth(2f);
            setIncome.setCircleColor(blueIncome);
            setIncome.setCircleRadius(2.8f);
            setIncome.setDrawValues(false);
            setIncome.setMode(LineDataSet.Mode.LINEAR);

            LineDataSet setExpense = new LineDataSet(expenseEntries, "Expense/day");
            setExpense.setColor(redExpense);
            setExpense.setLineWidth(2f);
            setExpense.setCircleColor(redExpense);
            setExpense.setCircleRadius(2.8f);
            setExpense.setDrawValues(false);
            setExpense.setMode(LineDataSet.Mode.LINEAR);

            LineData lineData = new LineData(setIncome, setExpense);

            // X axis show day-of-month
            XAxis x = lineChartDailyTotals.getXAxis();
            x.setAxisMinimum(1f);
            x.setAxisMaximum(daysInMonth);
            x.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int v = (int) value;
                    return String.valueOf(v);
                }
            });

            // Average limit lines
            YAxis left = lineChartDailyTotals.getAxisLeft();
            left.removeAllLimitLines();

            LimitLine avgInc = new LimitLine((float) avgIncomePerDay, "Avg Income/day");
            avgInc.setLineColor(blueIncome);
            avgInc.enableDashedLine(12f, 8f, 0f);
            avgInc.setTextColor(textPrimary);
            avgInc.setTextSize(10f);

            LimitLine avgExp = new LimitLine((float) avgExpensePerDay, "Avg Expense/day");
            avgExp.setLineColor(redExpense);
            avgExp.enableDashedLine(12f, 8f, 0f);
            avgExp.setTextColor(textPrimary);
            avgExp.setTextSize(10f);

            left.addLimitLine(avgInc);
            left.addLimitLine(avgExp);

            lineChartDailyTotals.setData(lineData);
            lineChartDailyTotals.animateX(600);
            lineChartDailyTotals.invalidate();
        }

        // 2) BarChart: two bars for avg income and avg expense
        List<BarEntry> barEntries = new ArrayList<>();
        barEntries.add(new BarEntry(0f, (float) avgIncomePerDay));
        barEntries.add(new BarEntry(1f, (float) avgExpensePerDay));

        BarDataSet set = new BarDataSet(barEntries, "");
        List<Integer> colors = new ArrayList<>();
        colors.add(blueIncome);
        colors.add(redExpense);
        set.setColors(colors);
        set.setDrawValues(false);

        BarData barData = new BarData(set);
        barData.setBarWidth(0.6f);

        barChartDailyAverage.setData(barData);
        XAxis bx = barChartDailyAverage.getXAxis();
        bx.setAxisMinimum(-0.5f);
        bx.setAxisMaximum(1.5f);
        bx.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int i = (int) value;
                if (i == 0) return "Avg Income";
                if (i == 1) return "Avg Expense";
                return "";
            }
        });
        barChartDailyAverage.animateY(600);
        barChartDailyAverage.invalidate();
    }

    private void updateDashboardUI(double currentBalance) {
        tvMonthlySalary.setText("Monthly Budget: ₹" + String.format(Locale.getDefault(), "%.2f", monthlySalary));
        tvTotalIncome.setText("Income: ₹" + String.format(Locale.getDefault(), "%.2f", totalIncome));
        tvTotalExpense.setText("Expense: ₹" + String.format(Locale.getDefault(), "%.2f", totalExpense));
        tvTotalBalance.setText("Balance: ₹" + String.format(Locale.getDefault(), "%.2f", currentBalance));
    }

    // Update date and month and load the data for that month
    private void updateCurrentDate() {
        SimpleDateFormat displayFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        SimpleDateFormat queryFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String displayDate = displayFormat.format(currentCalendar.getTime());
        String selectedDateOnly = queryFormat.format(currentCalendar.getTime());
        currentDate.setText(displayDate);

        String selectedMonth = spinnerMonth.getSelectedItem().toString();
        loadDashboardData(selectedMonth, selectedDateOnly);
    }

    // Change date (previous/next)
    private void changeDate(int offset) {
        currentCalendar.add(Calendar.DAY_OF_MONTH, offset);
        updateCurrentDate();
    }

    private int getMonthIndex(String monthName) {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        return java.util.Arrays.asList(months).indexOf(monthName);
    }
}