package com.cscorner.financetracker.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cscorner.financetracker.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ChartMonthlyFragment extends Fragment {

    private static final String TAG = "ChartMonthlyFragment";

    // UI Elements
    private BarChart barChartMonthly;
    private TextView tvMonthlyIncome, tvMonthlyExpense;
    private TextView tvYearIncome, tvYearExpense, tvYearSavings;
    private TextView tvBestMonth, tvWorstMonth;

    // Colors
    private final int blueIncome = Color.parseColor("#2563EB");
    private final int redExpense = Color.parseColor("#DC2626");
    private final int textPrimary = Color.parseColor("#0F172A");
    private final int gridColor = Color.parseColor("#E5E7EB");

    // Month names
    private final String[] MONTHS = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};

    private final String[] MONTH_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    // Data arrays
    private final double[] monthlyIncome = new double[12];
    private final double[] monthlyExpense = new double[12];
    private int loadedMonthsCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chart_monthly, container, false);

        initializeViews(view);
        setupBarChart();

        return view;
    }

    private void initializeViews(View view) {
        barChartMonthly = view.findViewById(R.id.barChartMonthly);
        tvMonthlyIncome = view.findViewById(R.id.tvMonthlyIncome);
        tvMonthlyExpense = view.findViewById(R.id.tvMonthlyExpense);
        tvYearIncome = view.findViewById(R.id.tvYearIncome);
        tvYearExpense = view.findViewById(R.id.tvYearExpense);
        tvYearSavings = view.findViewById(R.id.tvYearSavings);
        tvBestMonth = view.findViewById(R.id.tvBestMonth);
        tvWorstMonth = view.findViewById(R.id.tvWorstMonth);
    }

    private void setupBarChart() {
        barChartMonthly.getDescription().setEnabled(false);
        barChartMonthly.setDrawGridBackground(false);
        barChartMonthly.setNoDataText("No monthly data available");
        barChartMonthly.setNoDataTextColor(textPrimary);
        barChartMonthly.setTouchEnabled(true);
        barChartMonthly.setDragEnabled(true);
        barChartMonthly.setScaleEnabled(false);
        barChartMonthly.setPinchZoom(false);
        barChartMonthly.setDrawBarShadow(false);
        barChartMonthly.setDrawValueAboveBar(true);
        barChartMonthly.setMaxVisibleValueCount(60);
        barChartMonthly.setExtraBottomOffset(10f);

        // Legend setup
        Legend legend = barChartMonthly.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(textPrimary);
        legend.setTextSize(11f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setDrawInside(false);
        legend.setForm(Legend.LegendForm.CIRCLE);

        // X Axis setup
        XAxis xAxis = barChartMonthly.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(textPrimary);
        xAxis.setTextSize(9f);
        xAxis.setLabelRotationAngle(-45f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < MONTH_SHORT.length) {
                    return MONTH_SHORT[index];
                }
                return "";
            }
        });

        // Left Y Axis setup
        YAxis leftAxis = barChartMonthly.getAxisLeft();
        leftAxis.setTextColor(textPrimary);
        leftAxis.setTextSize(10f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(gridColor);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawZeroLine(true);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value >= 1000) {
                    return String.format(Locale.getDefault(), "%.0fK", value / 1000);
                }
                return String.format(Locale.getDefault(), "%.0f", value);
            }
        });

        // Disable right Y Axis
        barChartMonthly.getAxisRight().setEnabled(false);
    }

    /**
     * Called from DashboardActivity to update the chart
     * @param db FirebaseFirestore instance
     * @param email Current user's email
     */
    public void updateChart(FirebaseFirestore db, String email) {
        if (!isAdded() || getContext() == null) return;
        if (db == null || email == null || email.isEmpty()) {
            Log.e(TAG, "Invalid db or email");
            return;
        }

        // Reset data arrays
        resetData();

        // Load data for all months
        for (int i = 0; i < MONTHS.length; i++) {
            final int monthIndex = i;
            loadMonthData(db, email, MONTHS[i], monthIndex);
        }
    }

    private void resetData() {
        loadedMonthsCount = 0;
        for (int i = 0; i < 12; i++) {
            monthlyIncome[i] = 0;
            monthlyExpense[i] = 0;
        }
    }

    private void loadMonthData(FirebaseFirestore db, String email, String month, int monthIndex) {
        db.collection("Users").document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(month)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    double income = 0;
                    double expense = 0;

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        try {
                            String type = doc.getString("type");
                            if (type == null) type = "Expense";
                            type = type.trim();

                            double amount = 0.0;
                            Object amtObj = doc.get("amount");
                            if (amtObj instanceof Number) {
                                amount = ((Number) amtObj).doubleValue();
                            } else if (amtObj instanceof String) {
                                try {
                                    amount = Double.parseDouble((String) amtObj);
                                } catch (Exception ex) {
                                    amount = 0.0;
                                }
                            }

                            if (type.equalsIgnoreCase("Income")) {
                                income += amount;
                            } else {
                                expense += amount;
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing doc in " + month, e);
                        }
                    }

                    monthlyIncome[monthIndex] = income;
                    monthlyExpense[monthIndex] = expense;

                    loadedMonthsCount++;

                    // When all months are loaded, update UI
                    if (loadedMonthsCount >= 12) {
                        updateUI();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load " + month, e);
                    loadedMonthsCount++;

                    if (loadedMonthsCount >= 12) {
                        updateUI();
                    }
                });
    }

    private void updateUI() {
        if (!isAdded() || getContext() == null) return;

        // Get current month index
        int currentMonthIndex = Calendar.getInstance().get(Calendar.MONTH);

        // Update current month summary
        double thisMonthIncome = monthlyIncome[currentMonthIndex];
        double thisMonthExpense = monthlyExpense[currentMonthIndex];

        tvMonthlyIncome.setText(String.format(Locale.getDefault(), "₹%.0f", thisMonthIncome));
        tvMonthlyExpense.setText(String.format(Locale.getDefault(), "₹%.0f", thisMonthExpense));

        // Calculate year totals
        double yearTotalIncome = 0;
        double yearTotalExpense = 0;

        for (int i = 0; i < 12; i++) {
            yearTotalIncome += monthlyIncome[i];
            yearTotalExpense += monthlyExpense[i];
        }

        double yearSavings = yearTotalIncome - yearTotalExpense;

        // Update year summary
        tvYearIncome.setText(formatCurrency(yearTotalIncome));
        tvYearExpense.setText(formatCurrency(yearTotalExpense));
        tvYearSavings.setText(formatCurrency(yearSavings));

        // Find best savings month and highest expense month
        findBestAndWorstMonths();

        // Update bar chart
        updateBarChart();
    }

    private String formatCurrency(double amount) {
        if (Math.abs(amount) >= 100000) {
            return String.format(Locale.getDefault(), "₹%.1fL", amount / 100000);
        } else if (Math.abs(amount) >= 1000) {
            return String.format(Locale.getDefault(), "₹%.1fK", amount / 1000);
        } else {
            return String.format(Locale.getDefault(), "₹%.0f", amount);
        }
    }

    private void findBestAndWorstMonths() {
        double bestSavings = Double.MIN_VALUE;
        double highestExpense = Double.MIN_VALUE;
        int bestMonthIndex = -1;
        int worstMonthIndex = -1;

        for (int i = 0; i < 12; i++) {
            // Check if month has any data
            if (monthlyIncome[i] > 0 || monthlyExpense[i] > 0) {
                double savings = monthlyIncome[i] - monthlyExpense[i];

                if (savings > bestSavings) {
                    bestSavings = savings;
                    bestMonthIndex = i;
                }

                if (monthlyExpense[i] > highestExpense) {
                    highestExpense = monthlyExpense[i];
                    worstMonthIndex = i;
                }
            }
        }

        // Update UI
        if (bestMonthIndex >= 0) {
            tvBestMonth.setText(String.format(Locale.getDefault(), "%s\n₹%.0f",
                    MONTH_SHORT[bestMonthIndex], bestSavings));
        } else {
            tvBestMonth.setText("N/A");
        }

        if (worstMonthIndex >= 0) {
            tvWorstMonth.setText(String.format(Locale.getDefault(), "%s\n₹%.0f",
                    MONTH_SHORT[worstMonthIndex], highestExpense));
        } else {
            tvWorstMonth.setText("N/A");
        }
    }

    private void updateBarChart() {
        if (!isAdded() || getContext() == null) return;

        List<BarEntry> incomeEntries = new ArrayList<>();
        List<BarEntry> expenseEntries = new ArrayList<>();

        // Create bar entries for each month
        for (int i = 0; i < 12; i++) {
            incomeEntries.add(new BarEntry(i, (float) monthlyIncome[i]));
            expenseEntries.add(new BarEntry(i, (float) monthlyExpense[i]));
        }

        // Create datasets
        BarDataSet incomeDataSet = new BarDataSet(incomeEntries, "Income");
        incomeDataSet.setColor(blueIncome);
        incomeDataSet.setDrawValues(false);

        BarDataSet expenseDataSet = new BarDataSet(expenseEntries, "Expense");
        expenseDataSet.setColor(redExpense);
        expenseDataSet.setDrawValues(false);

        // Configure bar data
        float groupSpace = 0.3f;
        float barSpace = 0.05f;
        float barWidth = 0.3f;
        // (barWidth + barSpace) * 2 + groupSpace = 1.00 -> interval per group

        BarData barData = new BarData(incomeDataSet, expenseDataSet);
        barData.setBarWidth(barWidth);

        // Configure X axis
        XAxis xAxis = barChartMonthly.getXAxis();
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(12f);
        xAxis.setCenterAxisLabels(true);

        barChartMonthly.setData(barData);
        barChartMonthly.groupBars(0f, groupSpace, barSpace);

        // Refresh chart with animation
        barChartMonthly.animateY(800);
        barChartMonthly.invalidate();
    }

    /**
     * Alternative update method if you want to pass data directly
     * instead of loading from Firebase
     */
    public void updateChartWithData(double[] incomeData, double[] expenseData) {
        if (!isAdded() || getContext() == null) return;
        if (incomeData == null || expenseData == null) return;
        if (incomeData.length != 12 || expenseData.length != 12) return;

        // Copy data
        System.arraycopy(incomeData, 0, monthlyIncome, 0, 12);
        System.arraycopy(expenseData, 0, monthlyExpense, 0, 12);

        // Update UI
        updateUI();
    }
}