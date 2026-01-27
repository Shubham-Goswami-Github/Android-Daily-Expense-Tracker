package com.cscorner.financetracker.fragments;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.cscorner.financetracker.R;
import com.cscorner.financetracker.model.Transaction;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.MPPointF;
import com.google.android.material.card.MaterialCardView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ChartOverallFragment extends Fragment {

    private static final String TAG = "ChartOverallFragment";

    // Views - Main
    private PieChart pieChartOverall;
    private TextView tvOverallIncome, tvOverallExpense, tvOverallBalance;
    private TextView tvTotalTransactions, tvAvgDailyExpense;
    private TextView tvIncomeCount, tvExpenseCount;
    private TextView tvSavingsRate, tvSavingsPercent;

    // Views - Enhanced
    private TextView tvDateRange, tvActiveDays, tvTotalDays;
    private TextView tvHighestIncome, tvHighestExpense;
    private TextView tvHighestIncomeDate, tvHighestExpenseDate;
    private TextView tvAvgTransaction, tvBiggestCategory;
    private TextView tvFinancialHealthScore, tvHealthStatus;
    private TextView tvSpendingVelocity, tvVelocityStatus;
    private TextView tvIncomePercentage, tvExpensePercentage;
    private TextView tvSelectedSliceInfo;

    private ProgressBar progressFinancialHealth, progressSavingsRate;
    private ProgressBar progressIncomeRatio, progressExpenseRatio;

    private ImageView ivHealthEmoji, ivVelocityIcon;
    private ImageView ivIncomeArrow, ivExpenseArrow;

    private LinearLayout selectedSliceContainer;
    private View healthIndicator, velocityIndicator;

    private MaterialCardView cardFinancialHealth;

    // Colors
    private final int COLOR_INCOME = Color.parseColor("#10B981");
    private final int COLOR_INCOME_LIGHT = Color.parseColor("#D1FAE5");
    private final int COLOR_EXPENSE = Color.parseColor("#EF4444");
    private final int COLOR_EXPENSE_LIGHT = Color.parseColor("#FEE2E2");
    private final int COLOR_BALANCE_POSITIVE = Color.parseColor("#4ADE80");
    private final int COLOR_BALANCE_NEGATIVE = Color.parseColor("#F87171");
    private final int COLOR_PURPLE = Color.parseColor("#8B5CF6");
    private final int COLOR_BLUE = Color.parseColor("#3B82F6");
    private final int COLOR_AMBER = Color.parseColor("#F59E0B");
    private final int TEXT_PRIMARY = Color.parseColor("#0F172A");
    private final int TEXT_SECONDARY = Color.parseColor("#64748B");

    // Cache + pending update
    private List<Transaction> cachedAllTxns = new ArrayList<>();
    private boolean hasPendingUpdate = false;

    // Calculated values for display
    private double totalIncome = 0;
    private double totalExpense = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chart_overall, container, false);

        initializeViews(view);
        setupPieChart();

        if (hasPendingUpdate) {
            renderOverall(cachedAllTxns);
            hasPendingUpdate = false;
        }

        return view;
    }

    private void initializeViews(View view) {
        // Main views
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

        // Enhanced views
        tvDateRange = view.findViewById(R.id.tvDateRange);
        tvActiveDays = view.findViewById(R.id.tvActiveDays);
        tvTotalDays = view.findViewById(R.id.tvTotalDays);

        tvHighestIncome = view.findViewById(R.id.tvHighestIncome);
        tvHighestExpense = view.findViewById(R.id.tvHighestExpense);
        tvHighestIncomeDate = view.findViewById(R.id.tvHighestIncomeDate);
        tvHighestExpenseDate = view.findViewById(R.id.tvHighestExpenseDate);

        tvAvgTransaction = view.findViewById(R.id.tvAvgTransaction);
        tvBiggestCategory = view.findViewById(R.id.tvBiggestCategory);

        tvFinancialHealthScore = view.findViewById(R.id.tvFinancialHealthScore);
        tvHealthStatus = view.findViewById(R.id.tvHealthStatus);
        ivHealthEmoji = view.findViewById(R.id.ivHealthEmoji);
        progressFinancialHealth = view.findViewById(R.id.progressFinancialHealth);
        healthIndicator = view.findViewById(R.id.healthIndicator);
        cardFinancialHealth = view.findViewById(R.id.cardFinancialHealth);

        tvSpendingVelocity = view.findViewById(R.id.tvSpendingVelocity);
        tvVelocityStatus = view.findViewById(R.id.tvVelocityStatus);
        ivVelocityIcon = view.findViewById(R.id.ivVelocityIcon);
        velocityIndicator = view.findViewById(R.id.velocityIndicator);

        tvIncomePercentage = view.findViewById(R.id.tvIncomePercentage);
        tvExpensePercentage = view.findViewById(R.id.tvExpensePercentage);
        progressIncomeRatio = view.findViewById(R.id.progressIncomeRatio);
        progressExpenseRatio = view.findViewById(R.id.progressExpenseRatio);

        ivIncomeArrow = view.findViewById(R.id.ivIncomeArrow);
        ivExpenseArrow = view.findViewById(R.id.ivExpenseArrow);

        progressSavingsRate = view.findViewById(R.id.progressSavingsRate);

        tvSelectedSliceInfo = view.findViewById(R.id.tvSelectedSliceInfo);
        selectedSliceContainer = view.findViewById(R.id.selectedSliceContainer);
    }

    private void setupPieChart() {
        pieChartOverall.getDescription().setEnabled(false);
        pieChartOverall.setUsePercentValues(true);
        pieChartOverall.setDrawHoleEnabled(true);
        pieChartOverall.setHoleColor(Color.WHITE);
        pieChartOverall.setHoleRadius(58f);
        pieChartOverall.setTransparentCircleRadius(62f);
        pieChartOverall.setTransparentCircleColor(Color.WHITE);
        pieChartOverall.setTransparentCircleAlpha(100);
        pieChartOverall.setDrawCenterText(true);
        pieChartOverall.setCenterTextSize(14f);
        pieChartOverall.setCenterTextColor(TEXT_PRIMARY);
        pieChartOverall.setCenterTextTypeface(Typeface.DEFAULT_BOLD);
        pieChartOverall.setRotationEnabled(true);
        pieChartOverall.setRotationAngle(270f);
        pieChartOverall.setHighlightPerTapEnabled(true);
        pieChartOverall.setEntryLabelColor(Color.WHITE);
        pieChartOverall.setEntryLabelTextSize(12f);
        pieChartOverall.setEntryLabelTypeface(Typeface.DEFAULT_BOLD);
        pieChartOverall.setNoDataText("No transaction data available");
        pieChartOverall.setNoDataTextColor(TEXT_SECONDARY);
        pieChartOverall.setExtraOffsets(20f, 10f, 20f, 10f);
        pieChartOverall.setDragDecelerationFrictionCoef(0.95f);

        Legend legend = pieChartOverall.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(TEXT_PRIMARY);
        legend.setTextSize(12f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setXEntrySpace(24f);
        legend.setYEntrySpace(8f);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(12f);
        legend.setYOffset(10f);

        // Value selected listener
        pieChartOverall.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (e instanceof PieEntry) {
                    showSelectedSliceInfo((PieEntry) e);
                }
            }

            @Override
            public void onNothingSelected() {
                hideSelectedSliceInfo();
            }
        });
    }

    /**
     * Called from DashboardActivity to update the chart
     */
    public void updateChart(List<Transaction> allTransactions) {
        cachedAllTxns = (allTransactions != null) ? new ArrayList<>(allTransactions) : new ArrayList<>();

        if (!isAdded() || getView() == null || pieChartOverall == null) {
            hasPendingUpdate = true;
            return;
        }

        requireActivity().runOnUiThread(() -> renderOverall(cachedAllTxns));
    }

    // Extracted renderer (actual work)
    private void renderOverall(List<Transaction> allTransactions) {
        if (!isAdded() || getContext() == null || pieChartOverall == null) return;

        if (allTransactions == null || allTransactions.isEmpty()) {
            showEmptyState();
            return;
        }

        totalIncome = 0;
        totalExpense = 0;
        int incomeCount = 0;
        int expenseCount = 0;

        Set<String> uniqueDays = new HashSet<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

        // For highest transactions
        double highestIncomeAmount = 0;
        double highestExpenseAmount = 0;
        String highestIncomeDate = "";
        String highestExpenseDate = "";

        // For date range
        Date firstDate = null;
        Date lastDate = null;

        // For category analysis
        Map<String, Double> categoryExpenses = new HashMap<>();

        for (Transaction txn : allTransactions) {
            double amount = txn.getAmount();
            String dateStr = txn.getDate();
            String category = txn.getCategory();

            if (dateStr != null && !dateStr.isEmpty()) {
                uniqueDays.add(dateStr);

                try {
                    Date txnDate = sdf.parse(dateStr);
                    if (txnDate != null) {
                        if (firstDate == null || txnDate.before(firstDate)) {
                            firstDate = txnDate;
                        }
                        if (lastDate == null || txnDate.after(lastDate)) {
                            lastDate = txnDate;
                        }
                    }
                } catch (ParseException ignored) {
                }
            }

            if (txn.isIncome()) {
                totalIncome += amount;
                incomeCount++;

                if (amount > highestIncomeAmount) {
                    highestIncomeAmount = amount;
                    highestIncomeDate = dateStr;
                }
            } else {
                totalExpense += amount;
                expenseCount++;

                if (amount > highestExpenseAmount) {
                    highestExpenseAmount = amount;
                    highestExpenseDate = dateStr;
                }

                // Track category expenses
                if (category != null) {
                    categoryExpenses.merge(category, amount, Double::sum);
                }
            }
        }

        double netBalance = totalIncome - totalExpense;
        int totalTransactions = incomeCount + expenseCount;
        int totalDays = uniqueDays.size();
        double avgDailyExpense = totalDays > 0 ? totalExpense / totalDays : 0;
        double avgTransaction = totalTransactions > 0 ? (totalIncome + totalExpense) / totalTransactions : 0;

        double savingsRate = 0;
        if (totalIncome > 0) savingsRate = ((totalIncome - totalExpense) / totalIncome) * 100;

        // Find biggest expense category
        String biggestCategory = "N/A";
        double biggestCategoryAmount = 0;
        for (Map.Entry<String, Double> entry : categoryExpenses.entrySet()) {
            if (entry.getValue() > biggestCategoryAmount) {
                biggestCategoryAmount = entry.getValue();
                biggestCategory = entry.getKey();
            }
        }

        // Calculate days span
        int daySpan = 0;
        if (firstDate != null && lastDate != null) {
            long diffMillis = lastDate.getTime() - firstDate.getTime();
            daySpan = (int) (diffMillis / (24 * 60 * 60 * 1000)) + 1;
        }

        // Update all UI
        updateSummaryCards(totalIncome, totalExpense, netBalance);
        updateDateRange(firstDate, lastDate, totalDays, daySpan);
        updateStatsCards(totalTransactions, avgDailyExpense, incomeCount, expenseCount);
        updateHighestTransactions(highestIncomeAmount, highestIncomeDate, highestExpenseAmount, highestExpenseDate);
        updateAdvancedStats(avgTransaction, biggestCategory, biggestCategoryAmount);
        updateFinancialHealth(savingsRate, netBalance, totalIncome);
        updateSpendingVelocity(avgDailyExpense, totalExpense, daySpan);
        updateIncomeExpenseRatio(totalIncome, totalExpense);
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

        if (tvDateRange != null) tvDateRange.setText("No transactions yet");
        if (tvActiveDays != null) tvActiveDays.setText("0");
        if (tvTotalDays != null) tvTotalDays.setText("0 days");

        if (tvHighestIncome != null) tvHighestIncome.setText("₹0");
        if (tvHighestExpense != null) tvHighestExpense.setText("₹0");
        if (tvHighestIncomeDate != null) tvHighestIncomeDate.setText("--");
        if (tvHighestExpenseDate != null) tvHighestExpenseDate.setText("--");

        if (tvAvgTransaction != null) tvAvgTransaction.setText("₹0");
        if (tvBiggestCategory != null) tvBiggestCategory.setText("N/A");

        if (tvFinancialHealthScore != null) tvFinancialHealthScore.setText("--");
        if (tvHealthStatus != null) tvHealthStatus.setText("Start tracking to see your score");
        if (progressFinancialHealth != null) progressFinancialHealth.setProgress(0);

        if (tvSpendingVelocity != null) tvSpendingVelocity.setText("₹0/day");
        if (tvVelocityStatus != null) tvVelocityStatus.setText("No spending data");

        if (progressIncomeRatio != null) progressIncomeRatio.setProgress(0);
        if (progressExpenseRatio != null) progressExpenseRatio.setProgress(0);
        if (tvIncomePercentage != null) tvIncomePercentage.setText("0%");
        if (tvExpensePercentage != null) tvExpensePercentage.setText("0%");

        if (progressSavingsRate != null) progressSavingsRate.setProgress(0);

        pieChartOverall.clear();
        pieChartOverall.setCenterText("No Data\nStart Tracking!");
        pieChartOverall.notifyDataSetChanged();
        pieChartOverall.invalidate();
    }

    private void updateSummaryCards(double income, double expense, double netBalance) {
        animateValueChange(tvOverallIncome, income, "₹", COLOR_INCOME);
        animateValueChange(tvOverallExpense, expense, "₹", COLOR_EXPENSE);

        String prefix = netBalance >= 0 ? "+₹" : "-₹";
        int balanceColor = netBalance >= 0 ? COLOR_BALANCE_POSITIVE : COLOR_BALANCE_NEGATIVE;
        animateValueChange(tvOverallBalance, Math.abs(netBalance), prefix, balanceColor);
    }

    private void updateDateRange(Date firstDate, Date lastDate, int activeDays, int totalDays) {
        SimpleDateFormat displayFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        if (tvDateRange != null) {
            if (firstDate != null && lastDate != null) {
                String dateRange = displayFormat.format(firstDate) + " - " + displayFormat.format(lastDate);
                tvDateRange.setText(dateRange);
            } else {
                tvDateRange.setText("No data");
            }
        }

        if (tvActiveDays != null) {
            tvActiveDays.setText(String.valueOf(activeDays));
        }

        if (tvTotalDays != null) {
            tvTotalDays.setText(totalDays + " days span");
        }
    }

    private void updateStatsCards(int totalTransactions, double avgDailyExpense, int incomeCount, int expenseCount) {
        if (tvTotalTransactions != null) {
            animateIntValue(tvTotalTransactions, totalTransactions);
        }

        if (tvAvgDailyExpense != null) {
            animateValueChange(tvAvgDailyExpense, avgDailyExpense, "₹", TEXT_PRIMARY);
        }

        if (tvIncomeCount != null) {
            animateIntValue(tvIncomeCount, incomeCount);
        }

        if (tvExpenseCount != null) {
            animateIntValue(tvExpenseCount, expenseCount);
        }

        // Update arrows based on count comparison
        if (ivIncomeArrow != null && ivExpenseArrow != null) {
            if (incomeCount > expenseCount) {
                ivIncomeArrow.setColorFilter(COLOR_INCOME);
                ivExpenseArrow.setColorFilter(TEXT_SECONDARY);
            } else if (expenseCount > incomeCount) {
                ivIncomeArrow.setColorFilter(TEXT_SECONDARY);
                ivExpenseArrow.setColorFilter(COLOR_EXPENSE);
            } else {
                ivIncomeArrow.setColorFilter(COLOR_BLUE);
                ivExpenseArrow.setColorFilter(COLOR_BLUE);
            }
        }
    }

    private void updateHighestTransactions(double highestIncome, String incomeDate,
                                           double highestExpense, String expenseDate) {
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());

        if (tvHighestIncome != null) {
            tvHighestIncome.setText(formatCurrency(highestIncome));
        }

        if (tvHighestExpense != null) {
            tvHighestExpense.setText(formatCurrency(highestExpense));
        }

        if (tvHighestIncomeDate != null && incomeDate != null && !incomeDate.isEmpty()) {
            try {
                Date date = inputFormat.parse(incomeDate);
                if (date != null) {
                    tvHighestIncomeDate.setText(outputFormat.format(date));
                }
            } catch (ParseException e) {
                tvHighestIncomeDate.setText(incomeDate);
            }
        }

        if (tvHighestExpenseDate != null && expenseDate != null && !expenseDate.isEmpty()) {
            try {
                Date date = inputFormat.parse(expenseDate);
                if (date != null) {
                    tvHighestExpenseDate.setText(outputFormat.format(date));
                }
            } catch (ParseException e) {
                tvHighestExpenseDate.setText(expenseDate);
            }
        }
    }

    private void updateAdvancedStats(double avgTransaction, String biggestCategory, double biggestCategoryAmount) {
        if (tvAvgTransaction != null) {
            tvAvgTransaction.setText(formatCurrency(avgTransaction));
        }

        if (tvBiggestCategory != null) {
            if (biggestCategoryAmount > 0) {
                tvBiggestCategory.setText(biggestCategory + "\n" + formatCurrency(biggestCategoryAmount));
            } else {
                tvBiggestCategory.setText("N/A");
            }
        }
    }

    private void updateFinancialHealth(double savingsRate, double netBalance, double totalIncome) {
        if (tvFinancialHealthScore == null || tvHealthStatus == null) return;

        // Calculate health score (0-100)
        int healthScore = calculateHealthScore(savingsRate, netBalance, totalIncome);

        // Animate progress
        if (progressFinancialHealth != null) {
            animateProgress(progressFinancialHealth, healthScore);
        }

        // Update score display
        tvFinancialHealthScore.setText(String.valueOf(healthScore));

        // Update status and emoji based on score
        String status;
        int emojiRes;
        int statusColor;

        if (healthScore >= 80) {
            status = "Excellent! Keep it up! 🌟";
            emojiRes = R.drawable.ic_emoji_excellent;
            statusColor = COLOR_INCOME;
        } else if (healthScore >= 60) {
            status = "Good financial health";
            emojiRes = R.drawable.ic_emoji_happy;
            statusColor = COLOR_INCOME;
        } else if (healthScore >= 40) {
            status = "Room for improvement";
            emojiRes = R.drawable.ic_emoji_neutral;
            statusColor = COLOR_AMBER;
        } else if (healthScore >= 20) {
            status = "Needs attention";
            emojiRes = R.drawable.ic_emoji_sad;
            statusColor = COLOR_AMBER;
        } else {
            status = "Critical - Review spending";
            emojiRes = R.drawable.ic_emoji_very_sad;
            statusColor = COLOR_EXPENSE;
        }

        tvHealthStatus.setText(status);
        tvHealthStatus.setTextColor(statusColor);
        tvFinancialHealthScore.setTextColor(statusColor);

        if (ivHealthEmoji != null) {
            ivHealthEmoji.setImageResource(emojiRes);
            ivHealthEmoji.setColorFilter(statusColor);
        }

        if (healthIndicator != null) {
            healthIndicator.setBackgroundColor(statusColor);
        }

        // Update card background tint based on health
        if (cardFinancialHealth != null) {
            int bgColor;
            if (healthScore >= 60) {
                bgColor = Color.parseColor("#F0FDF4");
            } else if (healthScore >= 40) {
                bgColor = Color.parseColor("#FFFBEB");
            } else {
                bgColor = Color.parseColor("#FEF2F2");
            }
            cardFinancialHealth.setCardBackgroundColor(bgColor);
        }
    }

    private int calculateHealthScore(double savingsRate, double netBalance, double totalIncome) {
        int score = 50; // Base score

        // Savings rate contribution (up to 30 points)
        if (savingsRate >= 30) score += 30;
        else if (savingsRate >= 20) score += 25;
        else if (savingsRate >= 10) score += 15;
        else if (savingsRate >= 0) score += (int) savingsRate;
        else score += (int) Math.max(-20, savingsRate / 2); // Negative savings reduces score

        // Net balance contribution (up to 20 points)
        if (netBalance > 0) {
            double balanceRatio = totalIncome > 0 ? (netBalance / totalIncome) * 100 : 0;
            if (balanceRatio >= 50) score += 20;
            else if (balanceRatio >= 30) score += 15;
            else if (balanceRatio >= 10) score += 10;
            else score += 5;
        } else {
            score -= 10;
        }

        return Math.max(0, Math.min(100, score));
    }

    private void updateSpendingVelocity(double avgDailyExpense, double totalExpense, int days) {
        if (tvSpendingVelocity == null || tvVelocityStatus == null) return;

        tvSpendingVelocity.setText(formatCurrency(avgDailyExpense) + "/day");

        // Calculate velocity status
        String status;
        int statusColor;
        int iconRes;

        // Determine spending velocity level
        double velocityScore = totalExpense > 0 && days > 0 ?
                (avgDailyExpense / (totalExpense / Math.max(days, 1))) : 0;

        if (avgDailyExpense < 500) {
            status = "Low spending rate";
            statusColor = COLOR_INCOME;
            iconRes = R.drawable.ic_trending_down;
        } else if (avgDailyExpense < 2000) {
            status = "Moderate spending";
            statusColor = COLOR_BLUE;
            iconRes = R.drawable.ic_trending_neutral;
        } else if (avgDailyExpense < 5000) {
            status = "High spending rate";
            statusColor = COLOR_AMBER;
            iconRes = R.drawable.ic_trending_up;
        } else {
            status = "Very high spending";
            statusColor = COLOR_EXPENSE;
            iconRes = R.drawable.ic_warning;
        }

        tvVelocityStatus.setText(status);
        tvVelocityStatus.setTextColor(statusColor);
        tvSpendingVelocity.setTextColor(statusColor);

        if (ivVelocityIcon != null) {
            ivVelocityIcon.setImageResource(iconRes);
            ivVelocityIcon.setColorFilter(statusColor);
        }

        if (velocityIndicator != null) {
            velocityIndicator.setBackgroundColor(statusColor);
        }
    }

    private void updateIncomeExpenseRatio(double income, double expense) {
        double total = income + expense;

        int incomePercent = total > 0 ? (int) ((income / total) * 100) : 0;
        int expensePercent = total > 0 ? (int) ((expense / total) * 100) : 0;

        if (tvIncomePercentage != null) {
            tvIncomePercentage.setText(incomePercent + "%");
        }

        if (tvExpensePercentage != null) {
            tvExpensePercentage.setText(expensePercent + "%");
        }

        if (progressIncomeRatio != null) {
            animateProgress(progressIncomeRatio, incomePercent);
        }

        if (progressExpenseRatio != null) {
            animateProgress(progressExpenseRatio, expensePercent);
        }
    }

    private void updateSavingsRate(double savingsRate, double netBalance) {
        if (tvSavingsRate == null || tvSavingsPercent == null) return;

        int displayRate = (int) Math.max(-100, Math.min(100, savingsRate));

        // Animate savings progress
        if (progressSavingsRate != null) {
            int progress = (int) Math.max(0, Math.min(100, savingsRate));
            animateProgress(progressSavingsRate, progress);
        }

        if (savingsRate > 0) {
            tvSavingsRate.setText(String.format(Locale.getDefault(),
                    "You saved %s this period", formatCurrency(netBalance)));
            tvSavingsPercent.setText(String.format(Locale.getDefault(), "+%d%%", displayRate));
            tvSavingsPercent.setTextColor(Color.parseColor("#16A34A"));
        } else if (savingsRate < 0) {
            tvSavingsRate.setText(String.format(Locale.getDefault(),
                    "Overspent by %s", formatCurrency(Math.abs(netBalance))));
            tvSavingsPercent.setText(String.format(Locale.getDefault(), "%d%%", displayRate));
            tvSavingsPercent.setTextColor(Color.parseColor("#DC2626"));
        } else {
            tvSavingsRate.setText("No savings or expenses recorded");
            tvSavingsPercent.setText("0%");
            tvSavingsPercent.setTextColor(Color.parseColor("#4F46E5"));
        }
    }

    private void updatePieChart(double income, double expense, double netBalance) {
        if (income == 0 && expense == 0) {
            pieChartOverall.clear();
            pieChartOverall.setCenterText("No Data\nStart Tracking!");
            pieChartOverall.notifyDataSetChanged();
            pieChartOverall.invalidate();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        if (income > 0) entries.add(new PieEntry((float) income, "Income"));
        if (expense > 0) entries.add(new PieEntry((float) expense, "Expense"));

        if (entries.isEmpty()) {
            pieChartOverall.clear();
            pieChartOverall.setCenterText("No Data");
            pieChartOverall.notifyDataSetChanged();
            pieChartOverall.invalidate();
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");

        ArrayList<Integer> colors = new ArrayList<>();
        for (PieEntry entry : entries) {
            if ("Income".equals(entry.getLabel())) {
                colors.add(COLOR_INCOME);
            } else {
                colors.add(COLOR_EXPENSE);
            }
        }
        dataSet.setColors(colors);

        dataSet.setSliceSpace(4f);
        dataSet.setSelectionShift(12f);
        dataSet.setValueLinePart1OffsetPercentage(85f);
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.5f);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLineColor(TEXT_SECONDARY);
        dataSet.setValueLineWidth(1.5f);
        dataSet.setUsingSliceColorAsValueLineColor(true);

        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new PercentFormatter(pieChartOverall));
        pieData.setValueTextSize(12f);
        pieData.setValueTextColor(TEXT_PRIMARY);
        pieData.setValueTypeface(Typeface.DEFAULT_BOLD);

        // Update center text
        String centerText = "Net Balance\n" + formatCurrencyShort(netBalance);
        pieChartOverall.setCenterText(centerText);

        pieChartOverall.setData(pieData);

        pieChartOverall.notifyDataSetChanged();
        pieChartOverall.invalidate();
        pieChartOverall.animateY(800, Easing.EaseOutCubic);
    }

    private void showSelectedSliceInfo(PieEntry entry) {
        if (selectedSliceContainer == null || tvSelectedSliceInfo == null) return;

        String label = entry.getLabel();
        double value = entry.getValue();
        double total = totalIncome + totalExpense;
        double percentage = total > 0 ? (value / total) * 100 : 0;

        String info = String.format(Locale.getDefault(),
                "%s: %s (%.1f%% of total)",
                label, formatCurrency(value), percentage);

        tvSelectedSliceInfo.setText(info);

        // Set color based on type
        int bgColor = "Income".equals(label) ? Color.parseColor("#F0FDF4") : Color.parseColor("#FEF2F2");
        int textColor = "Income".equals(label) ? COLOR_INCOME : COLOR_EXPENSE;
        tvSelectedSliceInfo.setTextColor(textColor);

        selectedSliceContainer.setVisibility(View.VISIBLE);
        selectedSliceContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    private void hideSelectedSliceInfo() {
        if (selectedSliceContainer == null) return;
        selectedSliceContainer.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(150)
                .withEndAction(() -> selectedSliceContainer.setVisibility(View.GONE))
                .start();
    }

    // Animation helpers
    private void animateValueChange(TextView textView, double value, String prefix, int color) {
        if (textView == null) return;

        textView.setTextColor(color);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, (float) value);
        animator.setDuration(1000);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            textView.setText(prefix + formatNumber(animatedValue));
        });
        animator.start();
    }

    private void animateIntValue(TextView textView, int value) {
        if (textView == null) return;

        ValueAnimator animator = ValueAnimator.ofInt(0, value);
        animator.setDuration(800);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            textView.setText(String.valueOf(animatedValue));
        });
        animator.start();
    }

    private void animateProgress(ProgressBar progressBar, int targetProgress) {
        if (progressBar == null) return;

        ValueAnimator animator = ValueAnimator.ofInt(0, targetProgress);
        animator.setDuration(1000);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            progressBar.setProgress(progress);
        });
        animator.start();
    }

    // Formatting helpers
    private String formatNumber(double value) {
        if (Math.abs(value) >= 10000000) {
            return String.format(Locale.getDefault(), "%.2fCr", value / 10000000);
        } else if (Math.abs(value) >= 100000) {
            return String.format(Locale.getDefault(), "%.2fL", value / 100000);
        } else if (Math.abs(value) >= 1000) {
            return String.format(Locale.getDefault(), "%.1fK", value / 1000);
        }
        return String.format(Locale.getDefault(), "%.0f", value);
    }

    private String formatCurrency(double amount) {
        if (Math.abs(amount) >= 10000000) {
            return String.format(Locale.getDefault(), "₹%.2fCr", amount / 10000000);
        } else if (Math.abs(amount) >= 100000) {
            return String.format(Locale.getDefault(), "₹%.2fL", amount / 100000);
        } else if (Math.abs(amount) >= 1000) {
            return String.format(Locale.getDefault(), "₹%.1fK", amount / 1000);
        }
        return String.format(Locale.getDefault(), "₹%.0f", amount);
    }

    private String formatCurrencyShort(double amount) {
        String prefix = amount >= 0 ? "+" : "";
        if (Math.abs(amount) >= 10000000) {
            return String.format(Locale.getDefault(), "%s₹%.1fCr", prefix, amount / 10000000);
        } else if (Math.abs(amount) >= 100000) {
            return String.format(Locale.getDefault(), "%s₹%.1fL", prefix, amount / 100000);
        } else if (Math.abs(amount) >= 1000) {
            return String.format(Locale.getDefault(), "%s₹%.0fK", prefix, amount / 1000);
        }
        return String.format(Locale.getDefault(), "%s₹%.0f", prefix, amount);
    }
}