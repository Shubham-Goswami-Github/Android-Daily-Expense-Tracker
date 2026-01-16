package com.cscorner.financetracker.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
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
import com.cscorner.financetracker.model.Transaction;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChartOverallFragment extends Fragment {

    private static final String TAG = "ChartOverallFragment";

    // UI Elements
    private PieChart pieChartOverall;
    private TextView tvOverallIncome, tvOverallExpense, tvOverallBalance;
    private TextView tvTotalTransactions, tvAvgDailyExpense;
    private TextView tvIncomeCount, tvExpenseCount;
    private TextView tvSavingsRate, tvSavingsPercent;

    // Colors
    private final int blueIncome = Color.parseColor("#2563EB");
    private final int redExpense = Color.parseColor("#DC2626");
    private final int greenSavings = Color.parseColor("#16A34A");
    private final int textPrimary = Color.parseColor("#0F172A");
    private final int textSecondary = Color.parseColor("#64748B");

    // Pie Chart Colors
    private final int[] PIE_COLORS = {
            Color.parseColor("#4ADE80"),  // Green for Income
            Color.parseColor("#F87171")   // Red for Expense
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chart_overall, container, false);

        initializeViews(view);
        setupPieChart();

        return view;
    }

    private void initializeViews(View view) {
        pieChartOverall = view.findViewById(R.id.pieChartOverall);
        tvOverallIncome = view.findViewById(R.id.tvOverallIncome);
        tvOverallExpense = view.findViewById(R.id.tvOverallExpense);
        tvOverallBalance = view.findViewById(R.id.tvOverallBalance);
        tvTotalTransactions = view.findViewById(R.id.tvTotalTransactions);
        tvAvgDailyExpense = view.findViewById(R.id.tvAvgDailyExpense);
        tvIncomeCount = view.findViewById(R.id.tvIncomeCount);
        tvExpenseCount = view.findViewById(R.id.tvExpenseCount);
        tvSavingsRate = view.findViewById(R.id.tvSavingsRate);
        tvSavingsPercent = view.findViewById(R.id.tvSavingsPercent);
    }

    private void setupPieChart() {
        pieChartOverall.getDescription().setEnabled(false);
        pieChartOverall.setUsePercentValues(true);
        pieChartOverall.setDrawHoleEnabled(true);
        pieChartOverall.setHoleColor(Color.WHITE);
        pieChartOverall.setHoleRadius(55f);
        pieChartOverall.setTransparentCircleRadius(60f);
        pieChartOverall.setTransparentCircleColor(Color.WHITE);
        pieChartOverall.setTransparentCircleAlpha(100);
        pieChartOverall.setDrawCenterText(true);
        pieChartOverall.setCenterTextSize(14f);
        pieChartOverall.setCenterTextColor(textPrimary);
        pieChartOverall.setCenterTextTypeface(Typeface.DEFAULT_BOLD);
        pieChartOverall.setRotationEnabled(true);
        pieChartOverall.setHighlightPerTapEnabled(true);
        pieChartOverall.setEntryLabelColor(Color.WHITE);
        pieChartOverall.setEntryLabelTextSize(11f);
        pieChartOverall.setNoDataText("No transaction data available");
        pieChartOverall.setNoDataTextColor(textSecondary);
        pieChartOverall.setExtraOffsets(5f, 10f, 5f, 5f);

        // Legend setup
        Legend legend = pieChartOverall.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(textPrimary);
        legend.setTextSize(12f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setXEntrySpace(20f);
        legend.setYEntrySpace(5f);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(12f);
    }

    /**
     * Called from DashboardActivity to update the chart
     * @param allTransactions List of all transactions
     */
    public void updateChart(List<Transaction> allTransactions) {
        if (!isAdded() || getContext() == null) return;

        if (allTransactions == null || allTransactions.isEmpty()) {
            showEmptyState();
            return;
        }

        // Calculate totals
        double totalIncome = 0;
        double totalExpense = 0;
        int incomeCount = 0;
        int expenseCount = 0;

        // For calculating unique days (for average calculation)
        Set<String> uniqueDays = new HashSet<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

        for (Transaction txn : allTransactions) {
            double amount = txn.getAmount();
            String dateStr = txn.getDate();

            if (dateStr != null && !dateStr.isEmpty()) {
                uniqueDays.add(dateStr);
            }

            if (txn.isIncome()) {
                totalIncome += amount;
                incomeCount++;
            } else {
                totalExpense += amount;
                expenseCount++;
            }
        }

        // Calculate derived values
        double netBalance = totalIncome - totalExpense;
        int totalTransactions = incomeCount + expenseCount;
        int totalDays = uniqueDays.size();
        double avgDailyExpense = totalDays > 0 ? totalExpense / totalDays : 0;

        // Calculate savings rate
        double savingsRate = 0;
        if (totalIncome > 0) {
            savingsRate = ((totalIncome - totalExpense) / totalIncome) * 100;
        }

        // Update UI
        updateSummaryCards(totalIncome, totalExpense, netBalance);
        updateStatsCards(totalTransactions, avgDailyExpense, incomeCount, expenseCount);
        updateSavingsRate(savingsRate, netBalance);
        updatePieChart(totalIncome, totalExpense, netBalance);
    }

    private void showEmptyState() {
        tvOverallIncome.setText("₹0");
        tvOverallExpense.setText("₹0");
        tvOverallBalance.setText("₹0");
        tvTotalTransactions.setText("0");
        tvAvgDailyExpense.setText("₹0");

        if (tvIncomeCount != null) tvIncomeCount.setText("0");
        if (tvExpenseCount != null) tvExpenseCount.setText("0");
        if (tvSavingsRate != null) tvSavingsRate.setText("No data available");
        if (tvSavingsPercent != null) tvSavingsPercent.setText("0%");

        pieChartOverall.clear();
        pieChartOverall.invalidate();
    }

    private void updateSummaryCards(double totalIncome, double totalExpense, double netBalance) {
        tvOverallIncome.setText(formatCurrency(totalIncome));
        tvOverallExpense.setText(formatCurrency(totalExpense));
        tvOverallBalance.setText(formatCurrency(netBalance));

        // Change balance color based on positive/negative
        if (netBalance >= 0) {
            tvOverallBalance.setTextColor(Color.parseColor("#4ADE80")); // Green
        } else {
            tvOverallBalance.setTextColor(Color.parseColor("#F87171")); // Red
        }
    }

    private void updateStatsCards(int totalTransactions, double avgDailyExpense,
                                  int incomeCount, int expenseCount) {
        tvTotalTransactions.setText(String.valueOf(totalTransactions));
        tvAvgDailyExpense.setText(formatCurrency(avgDailyExpense));

        if (tvIncomeCount != null) {
            tvIncomeCount.setText(String.valueOf(incomeCount));
        }
        if (tvExpenseCount != null) {
            tvExpenseCount.setText(String.valueOf(expenseCount));
        }
    }

    private void updateSavingsRate(double savingsRate, double netBalance) {
        if (tvSavingsRate == null || tvSavingsPercent == null) return;

        // Clamp savings rate between -100 and 100 for display
        int displayRate = (int) Math.max(-100, Math.min(100, savingsRate));

        if (savingsRate > 0) {
            tvSavingsRate.setText(String.format(Locale.getDefault(),
                    "You saved %s this period", formatCurrency(netBalance)));
            tvSavingsPercent.setText(String.format(Locale.getDefault(), "%d%%", displayRate));
            tvSavingsPercent.setTextColor(Color.parseColor("#16A34A")); // Green
        } else if (savingsRate < 0) {
            tvSavingsRate.setText(String.format(Locale.getDefault(),
                    "Overspent by %s", formatCurrency(Math.abs(netBalance))));
            tvSavingsPercent.setText(String.format(Locale.getDefault(), "%d%%", displayRate));
            tvSavingsPercent.setTextColor(Color.parseColor("#DC2626")); // Red
        } else {
            tvSavingsRate.setText("No savings or expenses recorded");
            tvSavingsPercent.setText("0%");
            tvSavingsPercent.setTextColor(Color.parseColor("#4F46E5")); // Blue
        }
    }

    private void updatePieChart(double totalIncome, double totalExpense, double netBalance) {
        if (totalIncome == 0 && totalExpense == 0) {
            pieChartOverall.clear();
            pieChartOverall.setCenterText("No Data");
            pieChartOverall.invalidate();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();

        // Add entries only if they have value
        if (totalIncome > 0) {
            entries.add(new PieEntry((float) totalIncome, "Income"));
        }
        if (totalExpense > 0) {
            entries.add(new PieEntry((float) totalExpense, "Expense"));
        }

        if (entries.isEmpty()) {
            pieChartOverall.clear();
            pieChartOverall.setCenterText("No Data");
            pieChartOverall.invalidate();
            return;
        }

        // Create dataset
        PieDataSet dataSet = new PieDataSet(entries, "");

        // Set colors based on entries
        ArrayList<Integer> colors = new ArrayList<>();
        for (PieEntry entry : entries) {
            if (entry.getLabel().equals("Income")) {
                colors.add(Color.parseColor("#4ADE80")); // Green
            } else {
                colors.add(Color.parseColor("#F87171")); // Red
            }
        }
        dataSet.setColors(colors);

        // Configure dataset
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);
        dataSet.setValueLinePart1OffsetPercentage(80f);
        dataSet.setValueLinePart1Length(0.3f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLineColor(textSecondary);

        // Create pie data
        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new PercentFormatter(pieChartOverall));
        pieData.setValueTextSize(11f);
        pieData.setValueTextColor(textPrimary);
        pieData.setValueTypeface(Typeface.DEFAULT_BOLD);

        // Set center text
        String centerText = formatCurrencyShort(netBalance);
        if (netBalance >= 0) {
            pieChartOverall.setCenterText("Net\n" + centerText);
        } else {
            pieChartOverall.setCenterText("Net\n" + centerText);
        }

        // Apply data and animate
        pieChartOverall.setData(pieData);
        pieChartOverall.animateY(1000, Easing.EaseInOutQuad);
        pieChartOverall.invalidate();
    }

    /**
     * Format currency with full precision
     */
    private String formatCurrency(double amount) {
        if (Math.abs(amount) >= 10000000) { // 1 Crore
            return String.format(Locale.getDefault(), "₹%.2fCr", amount / 10000000);
        } else if (Math.abs(amount) >= 100000) { // 1 Lakh
            return String.format(Locale.getDefault(), "₹%.2fL", amount / 100000);
        } else if (Math.abs(amount) >= 1000) { // 1 Thousand
            return String.format(Locale.getDefault(), "₹%.1fK", amount / 1000);
        } else {
            return String.format(Locale.getDefault(), "₹%.0f", amount);
        }
    }

    /**
     * Format currency for center text (shorter version)
     */
    private String formatCurrencyShort(double amount) {
        String prefix = amount >= 0 ? "+" : "";
        if (Math.abs(amount) >= 10000000) {
            return String.format(Locale.getDefault(), "%s₹%.1fCr", prefix, amount / 10000000);
        } else if (Math.abs(amount) >= 100000) {
            return String.format(Locale.getDefault(), "%s₹%.1fL", prefix, amount / 100000);
        } else if (Math.abs(amount) >= 1000) {
            return String.format(Locale.getDefault(), "%s₹%.0fK", prefix, amount / 1000);
        } else {
            return String.format(Locale.getDefault(), "%s₹%.0f", prefix, amount);
        }
    }

    /**
     * Calculate statistics by category (for future use)
     */
    public void updateChartWithCategories(List<Transaction> transactions) {
        if (!isAdded() || getContext() == null) return;
        if (transactions == null || transactions.isEmpty()) {
            showEmptyState();
            return;
        }

        // This method can be extended to show category-wise breakdown
        // For now, it calls the main updateChart method
        updateChart(transactions);
    }

    /**
     * Get expense breakdown by category
     * Can be used for detailed analytics
     */
    private void calculateCategoryBreakdown(List<Transaction> transactions) {
        // Future implementation for category-wise pie chart
        // Map<String, Double> categoryTotals = new HashMap<>();
        // for (Transaction txn : transactions) {
        //     if (!txn.isIncome()) {
        //         String category = txn.getCategory(); // if you have category field
        //         categoryTotals.merge(category, txn.getAmount(), Double::sum);
        //     }
        // }
    }
}