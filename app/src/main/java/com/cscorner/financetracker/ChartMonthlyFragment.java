package com.cscorner.financetracker.fragments;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.cscorner.financetracker.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.MPPointF;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ChartMonthlyFragment extends Fragment {

    private static final String TAG = "ChartMonthlyFragment";

    // Views
    private BarChart barChartMonthly;
    private TextView tvMonthlyIncome, tvMonthlyExpense, tvMonthlySavings;
    private TextView tvYearIncome, tvYearExpense, tvYearSavings;
    private TextView tvBestMonth, tvWorstMonth;
    private TextView tvCurrentYearLabel, tvMonthsWithData;
    private TextView tvIncomeChange, tvExpenseChange, tvSavingsChange;
    private TextView tvSavingsRate, tvAvgMonthlyExpense, tvAvgMonthlySavings;
    private TextView tvSelectedMonthInfo;
    private ImageView ivIncomeArrow, ivExpenseArrow, ivSavingsArrow;
    private ImageView ivSavingsRateIcon;
    private ProgressBar progressYearComplete;
    private LinearLayout selectedMonthContainer;
    private CardView cardIncome, cardExpense, cardSavings;
    private View savingsRateIndicator;

    // Colors
    private final int COLOR_INCOME = Color.parseColor("#10B981");
    private final int COLOR_INCOME_LIGHT = Color.parseColor("#D1FAE5");
    private final int COLOR_EXPENSE = Color.parseColor("#EF4444");
    private final int COLOR_EXPENSE_LIGHT = Color.parseColor("#FEE2E2");
    private final int COLOR_SAVINGS_POSITIVE = Color.parseColor("#3B82F6");
    private final int COLOR_SAVINGS_NEGATIVE = Color.parseColor("#F59E0B");
    private final int TEXT_PRIMARY = Color.parseColor("#0F172A");
    private final int TEXT_SECONDARY = Color.parseColor("#64748B");
    private final int GRID_COLOR = Color.parseColor("#E2E8F0");
    private final int HIGHLIGHT_COLOR = Color.parseColor("#6366F1");
    private final int COLOR_PURPLE = Color.parseColor("#8B5CF6");

    private final String[] MONTHS = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};

    private final String[] MONTH_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private final double[] monthlyIncome = new double[12];
    private final double[] monthlyExpense = new double[12];
    private int loadedMonthsCount = 0;
    private int currentMonthIndex = 0;
    private int currentYear = 2025;

    // Cache + pending refresh
    private FirebaseFirestore cachedDb;
    private String cachedEmail;
    private boolean pendingRefresh = false;
    private boolean refreshInProgress = false;
    private boolean queuedRefresh = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chart_monthly, container, false);

        initializeViews(view);
        setupBarChart();
        updateCurrentYearLabel();

        if (pendingRefresh && cachedDb != null && cachedEmail != null) {
            pendingRefresh = false;
            refreshNow(cachedDb, cachedEmail);
        }

        return view;
    }

    private void initializeViews(View view) {
        // Chart
        barChartMonthly = view.findViewById(R.id.barChartMonthly);

        // This Month Cards
        tvMonthlyIncome = view.findViewById(R.id.tvMonthlyIncome);
        tvMonthlyExpense = view.findViewById(R.id.tvMonthlyExpense);
        tvMonthlySavings = view.findViewById(R.id.tvMonthlySavings);

        // Change Indicators
        tvIncomeChange = view.findViewById(R.id.tvIncomeChange);
        tvExpenseChange = view.findViewById(R.id.tvExpenseChange);
        tvSavingsChange = view.findViewById(R.id.tvSavingsChange);
        ivIncomeArrow = view.findViewById(R.id.ivIncomeArrow);
        ivExpenseArrow = view.findViewById(R.id.ivExpenseArrow);
        ivSavingsArrow = view.findViewById(R.id.ivSavingsArrow);

        // Cards
        cardIncome = view.findViewById(R.id.cardIncome);
        cardExpense = view.findViewById(R.id.cardExpense);
        cardSavings = view.findViewById(R.id.cardSavings);

        // Year Summary
        tvYearIncome = view.findViewById(R.id.tvYearIncome);
        tvYearExpense = view.findViewById(R.id.tvYearExpense);
        tvYearSavings = view.findViewById(R.id.tvYearSavings);
        tvCurrentYearLabel = view.findViewById(R.id.tvCurrentYearLabel);
        tvMonthsWithData = view.findViewById(R.id.tvMonthsWithData);
        progressYearComplete = view.findViewById(R.id.progressYearComplete);

        // Statistics
        tvBestMonth = view.findViewById(R.id.tvBestMonth);
        tvWorstMonth = view.findViewById(R.id.tvWorstMonth);
        tvSavingsRate = view.findViewById(R.id.tvSavingsRate);
        tvAvgMonthlyExpense = view.findViewById(R.id.tvAvgMonthlyExpense);
        tvAvgMonthlySavings = view.findViewById(R.id.tvAvgMonthlySavings);
        ivSavingsRateIcon = view.findViewById(R.id.ivSavingsRateIcon);
        savingsRateIndicator = view.findViewById(R.id.savingsRateIndicator);

        // Selected Month Info
        tvSelectedMonthInfo = view.findViewById(R.id.tvSelectedMonthInfo);
        selectedMonthContainer = view.findViewById(R.id.selectedMonthContainer);
    }

    private void setupBarChart() {
        barChartMonthly.getDescription().setEnabled(false);
        barChartMonthly.setDrawGridBackground(false);
        barChartMonthly.setNoDataText("Loading monthly data...");
        barChartMonthly.setNoDataTextColor(TEXT_SECONDARY);
        barChartMonthly.setTouchEnabled(true);
        barChartMonthly.setDragEnabled(true);
        barChartMonthly.setScaleEnabled(false);
        barChartMonthly.setPinchZoom(false);
        barChartMonthly.setDoubleTapToZoomEnabled(false);
        barChartMonthly.setDrawBarShadow(false);
        barChartMonthly.setDrawValueAboveBar(true);
        barChartMonthly.setMaxVisibleValueCount(60);
        barChartMonthly.setExtraBottomOffset(12f);
        barChartMonthly.setExtraTopOffset(8f);
        barChartMonthly.setExtraLeftOffset(4f);
        barChartMonthly.setExtraRightOffset(4f);
        barChartMonthly.setFitBars(true);
        barChartMonthly.setHighlightFullBarEnabled(false);

        // Legend
        Legend legend = barChartMonthly.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(TEXT_PRIMARY);
        legend.setTextSize(11f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setDrawInside(false);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(8f);
        legend.setXEntrySpace(24f);

        // X-Axis
        XAxis xAxis = barChartMonthly.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(TEXT_SECONDARY);
        xAxis.setTextSize(10f);
        xAxis.setLabelRotationAngle(-45f);
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(GRID_COLOR);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < MONTH_SHORT.length) return MONTH_SHORT[index];
                return "";
            }
        });

        // Y-Axis
        YAxis leftAxis = barChartMonthly.getAxisLeft();
        leftAxis.setTextColor(TEXT_SECONDARY);
        leftAxis.setTextSize(10f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(GRID_COLOR);
        leftAxis.setGridLineWidth(0.5f);
        leftAxis.enableGridDashedLine(8f, 4f, 0f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawZeroLine(false);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value >= 100000) return String.format(Locale.getDefault(), "%.1fL", value / 100000);
                if (value >= 1000) return String.format(Locale.getDefault(), "%.0fK", value / 1000);
                return String.format(Locale.getDefault(), "%.0f", value);
            }
        });

        barChartMonthly.getAxisRight().setEnabled(false);

        // Value selected listener
        barChartMonthly.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                showSelectedMonthInfo((int) e.getX());
            }

            @Override
            public void onNothingSelected() {
                hideSelectedMonthInfo();
            }
        });

        // Set custom marker
        if (getContext() != null) {
            CustomMarkerView marker = new CustomMarkerView(getContext());
            marker.setChartView(barChartMonthly);
            barChartMonthly.setMarker(marker);
        }
    }

    private void updateCurrentYearLabel() {
        Calendar cal = Calendar.getInstance();
        currentYear = cal.get(Calendar.YEAR);
        currentMonthIndex = cal.get(Calendar.MONTH);

        if (tvCurrentYearLabel != null) {
            tvCurrentYearLabel.setText(String.valueOf(currentYear));
        }
    }

    /**
     * Called from DashboardActivity after add/delete/date/month change
     */
    public void updateChart(FirebaseFirestore db, String email) {
        cachedDb = db;
        cachedEmail = email;

        if (db == null || email == null || email.isEmpty()) {
            Log.e(TAG, "Invalid db or email");
            return;
        }

        if (!isAdded() || getView() == null || barChartMonthly == null) {
            pendingRefresh = true;
            return;
        }

        if (refreshInProgress) {
            queuedRefresh = true;
            return;
        }

        refreshNow(db, email);
    }

    private void refreshNow(FirebaseFirestore db, String email) {
        refreshInProgress = true;
        queuedRefresh = false;

        resetData();

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

                            if (type.equalsIgnoreCase("Income")) income += amount;
                            else expense += amount;

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing doc in " + month, e);
                        }
                    }

                    monthlyIncome[monthIndex] = income;
                    monthlyExpense[monthIndex] = expense;

                    onMonthLoaded();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load " + month, e);
                    onMonthLoaded();
                });
    }

    private void onMonthLoaded() {
        loadedMonthsCount++;

        if (loadedMonthsCount >= 12) {
            if (isAdded() && getContext() != null) {
                requireActivity().runOnUiThread(this::updateUI);
            }
            refreshInProgress = false;

            if (queuedRefresh && cachedDb != null && cachedEmail != null) {
                refreshNow(cachedDb, cachedEmail);
            }
        }
    }

    private void updateUI() {
        if (!isAdded() || getContext() == null) return;

        // This Month Values
        double thisMonthIncome = monthlyIncome[currentMonthIndex];
        double thisMonthExpense = monthlyExpense[currentMonthIndex];
        double thisMonthSavings = thisMonthIncome - thisMonthExpense;

        // Previous Month Values (for comparison)
        int prevMonthIndex = currentMonthIndex > 0 ? currentMonthIndex - 1 : 11;
        double prevMonthIncome = monthlyIncome[prevMonthIndex];
        double prevMonthExpense = monthlyExpense[prevMonthIndex];
        double prevMonthSavings = prevMonthIncome - prevMonthExpense;

        // Animate this month values
        animateValueChange(tvMonthlyIncome, thisMonthIncome, "₹");
        animateValueChange(tvMonthlyExpense, thisMonthExpense, "₹");
        animateValueChange(tvMonthlySavings, Math.abs(thisMonthSavings), thisMonthSavings >= 0 ? "+₹" : "-₹");

        // Update savings card color
        updateSavingsCard(thisMonthSavings);

        // Calculate changes
        calculateMonthComparison(thisMonthIncome, prevMonthIncome, tvIncomeChange, ivIncomeArrow, true);
        calculateMonthComparison(thisMonthExpense, prevMonthExpense, tvExpenseChange, ivExpenseArrow, false);
        calculateMonthComparison(thisMonthSavings, prevMonthSavings, tvSavingsChange, ivSavingsArrow, true);

        // Year totals
        double yearTotalIncome = 0;
        double yearTotalExpense = 0;
        int monthsWithData = 0;

        for (int i = 0; i < 12; i++) {
            yearTotalIncome += monthlyIncome[i];
            yearTotalExpense += monthlyExpense[i];
            if (monthlyIncome[i] > 0 || monthlyExpense[i] > 0) {
                monthsWithData++;
            }
        }

        double yearSavings = yearTotalIncome - yearTotalExpense;

        // Update year summary with animation
        animateValueChange(tvYearIncome, yearTotalIncome, "₹");
        animateValueChange(tvYearExpense, yearTotalExpense, "₹");
        animateValueChange(tvYearSavings, Math.abs(yearSavings), yearSavings >= 0 ? "+₹" : "-₹");

        // Update months with data
        if (tvMonthsWithData != null) {
            tvMonthsWithData.setText(String.format(Locale.getDefault(), "%d/12 months", monthsWithData));
        }

        // Update year progress
        if (progressYearComplete != null) {
            int yearProgress = (int) (((currentMonthIndex + 1) / 12.0) * 100);
            animateProgress(progressYearComplete, yearProgress);
        }

        // Calculate statistics
        calculateStatistics(yearTotalIncome, yearTotalExpense, monthsWithData);

        // Find best and worst months
        findBestAndWorstMonths();

        // Update bar chart
        updateBarChart();
    }

    private void updateSavingsCard(double savings) {
        if (cardSavings == null || tvMonthlySavings == null) return;

        int bgColor, textColor;
        if (savings >= 0) {
            bgColor = Color.parseColor("#EFF6FF");
            textColor = COLOR_SAVINGS_POSITIVE;
        } else {
            bgColor = Color.parseColor("#FFFBEB");
            textColor = COLOR_SAVINGS_NEGATIVE;
        }

        cardSavings.setCardBackgroundColor(bgColor);
        tvMonthlySavings.setTextColor(textColor);
    }

    private void calculateMonthComparison(double current, double previous, TextView tvChange, ImageView ivArrow, boolean higherIsGood) {
        if (tvChange == null || ivArrow == null) return;

        double change = current - previous;
        double percentChange = previous > 0 ? ((change / previous) * 100) : (current > 0 ? 100 : 0);

        String changeText;
        int arrowDrawable;
        int textColor;

        if (Math.abs(change) < 0.01) {
            changeText = "No change";
            arrowDrawable = R.drawable.ic_arrow_neutral;
            textColor = TEXT_SECONDARY;
        } else if (change > 0) {
            changeText = String.format(Locale.getDefault(), "+%.0f%%", percentChange);
            arrowDrawable = R.drawable.ic_arrow_up_small;
            textColor = higherIsGood ? COLOR_INCOME : COLOR_EXPENSE;
        } else {
            changeText = String.format(Locale.getDefault(), "%.0f%%", percentChange);
            arrowDrawable = R.drawable.ic_arrow_down_small;
            textColor = higherIsGood ? COLOR_EXPENSE : COLOR_INCOME;
        }

        tvChange.setText(changeText);
        tvChange.setTextColor(textColor);
        ivArrow.setImageResource(arrowDrawable);
        ivArrow.setColorFilter(textColor);
    }

    private void calculateStatistics(double yearIncome, double yearExpense, int monthsWithData) {
        // Savings Rate
        double savingsRate = yearIncome > 0 ? ((yearIncome - yearExpense) / yearIncome) * 100 : 0;
        if (tvSavingsRate != null) {
            tvSavingsRate.setText(String.format(Locale.getDefault(), "%.1f%%", savingsRate));

            // Update color based on savings rate
            int rateColor;
            int rateIcon;
            if (savingsRate >= 30) {
                rateColor = COLOR_INCOME;
                rateIcon = R.drawable.ic_emoji_happy;
            } else if (savingsRate >= 15) {
                rateColor = COLOR_SAVINGS_POSITIVE;
                rateIcon = R.drawable.ic_emoji_neutral;
            } else if (savingsRate >= 0) {
                rateColor = COLOR_SAVINGS_NEGATIVE;
                rateIcon = R.drawable.ic_emoji_sad;
            } else {
                rateColor = COLOR_EXPENSE;
                rateIcon = R.drawable.ic_emoji_very_sad;
            }

            tvSavingsRate.setTextColor(rateColor);
            if (ivSavingsRateIcon != null) {
                ivSavingsRateIcon.setImageResource(rateIcon);
                ivSavingsRateIcon.setColorFilter(rateColor);
            }
            if (savingsRateIndicator != null) {
                savingsRateIndicator.setBackgroundColor(rateColor);
            }
        }

        // Average Monthly Expense
        if (tvAvgMonthlyExpense != null) {
            double avgExpense = monthsWithData > 0 ? yearExpense / monthsWithData : 0;
            tvAvgMonthlyExpense.setText(formatCurrency(avgExpense));
        }

        // Average Monthly Savings
        if (tvAvgMonthlySavings != null) {
            double avgSavings = monthsWithData > 0 ? (yearIncome - yearExpense) / monthsWithData : 0;
            tvAvgMonthlySavings.setText(formatCurrencyWithSign(avgSavings));
            tvAvgMonthlySavings.setTextColor(avgSavings >= 0 ? COLOR_SAVINGS_POSITIVE : COLOR_EXPENSE);
        }
    }

    private String formatCurrency(double amount) {
        if (Math.abs(amount) >= 100000) return String.format(Locale.getDefault(), "₹%.1fL", amount / 100000);
        if (Math.abs(amount) >= 1000) return String.format(Locale.getDefault(), "₹%.1fK", amount / 1000);
        return String.format(Locale.getDefault(), "₹%.0f", amount);
    }

    private String formatCurrencyWithSign(double amount) {
        String prefix = amount >= 0 ? "+₹" : "-₹";
        double absAmount = Math.abs(amount);
        if (absAmount >= 100000) return String.format(Locale.getDefault(), "%s%.1fL", prefix, absAmount / 100000);
        if (absAmount >= 1000) return String.format(Locale.getDefault(), "%s%.1fK", prefix, absAmount / 1000);
        return String.format(Locale.getDefault(), "%s%.0f", prefix, absAmount);
    }

    private void findBestAndWorstMonths() {
        double bestSavings = Double.MIN_VALUE;
        double highestExpense = Double.MIN_VALUE;
        int bestMonthIndex = -1;
        int worstMonthIndex = -1;

        for (int i = 0; i < 12; i++) {
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

        if (tvBestMonth != null) {
            if (bestMonthIndex >= 0) {
                tvBestMonth.setText(String.format(Locale.getDefault(), "%s\n%s",
                        MONTH_SHORT[bestMonthIndex], formatCurrencyWithSign(bestSavings)));
                tvBestMonth.setTextColor(bestSavings >= 0 ? COLOR_INCOME : COLOR_EXPENSE);
            } else {
                tvBestMonth.setText("N/A");
                tvBestMonth.setTextColor(TEXT_SECONDARY);
            }
        }

        if (tvWorstMonth != null) {
            if (worstMonthIndex >= 0) {
                tvWorstMonth.setText(String.format(Locale.getDefault(), "%s\n%s",
                        MONTH_SHORT[worstMonthIndex], formatCurrency(highestExpense)));
                tvWorstMonth.setTextColor(COLOR_EXPENSE);
            } else {
                tvWorstMonth.setText("N/A");
                tvWorstMonth.setTextColor(TEXT_SECONDARY);
            }
        }
    }

    private void updateBarChart() {
        if (!isAdded() || getContext() == null) return;

        List<BarEntry> incomeEntries = new ArrayList<>(12);
        List<BarEntry> expenseEntries = new ArrayList<>(12);

        for (int i = 0; i < 12; i++) {
            incomeEntries.add(new BarEntry(i, (float) monthlyIncome[i]));
            expenseEntries.add(new BarEntry(i, (float) monthlyExpense[i]));
        }

        BarDataSet incomeDataSet = new BarDataSet(incomeEntries, "Income");
        incomeDataSet.setColor(COLOR_INCOME);
        incomeDataSet.setDrawValues(false);
        incomeDataSet.setHighLightColor(Color.parseColor("#059669"));
        incomeDataSet.setHighLightAlpha(180);

        BarDataSet expenseDataSet = new BarDataSet(expenseEntries, "Expense");
        expenseDataSet.setColor(COLOR_EXPENSE);
        expenseDataSet.setDrawValues(false);
        expenseDataSet.setHighLightColor(Color.parseColor("#DC2626"));
        expenseDataSet.setHighLightAlpha(180);

        float groupSpace = 0.24f;
        float barSpace = 0.03f;
        float barWidth = 0.35f;

        BarData barData = new BarData(incomeDataSet, expenseDataSet);
        barData.setBarWidth(barWidth);

        XAxis xAxis = barChartMonthly.getXAxis();
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(12f);
        xAxis.setCenterAxisLabels(true);
        xAxis.setLabelCount(12, false);

        // Add current month indicator
        addCurrentMonthIndicator();

        barChartMonthly.setData(barData);
        barChartMonthly.groupBars(0f, groupSpace, barSpace);

        barChartMonthly.notifyDataSetChanged();
        barChartMonthly.invalidate();
        barChartMonthly.animateY(800, com.github.mikephil.charting.animation.Easing.EaseOutCubic);

        // Highlight current month
        highlightCurrentMonth();
    }

    private void addCurrentMonthIndicator() {
        YAxis leftAxis = barChartMonthly.getAxisLeft();
        leftAxis.removeAllLimitLines();

        // We can't add vertical limit lines to bar chart easily
        // So we'll just use highlighting instead
    }

    private void highlightCurrentMonth() {
        // Highlight current month bars
        if (barChartMonthly.getData() != null) {
            // Slight delay for smooth animation
            barChartMonthly.postDelayed(() -> {
                barChartMonthly.highlightValue(currentMonthIndex + 0.5f, 0, false);
            }, 900);
        }
    }

    private void showSelectedMonthInfo(int monthIndex) {
        if (selectedMonthContainer == null || tvSelectedMonthInfo == null) return;
        if (monthIndex < 0 || monthIndex >= 12) return;

        double income = monthlyIncome[monthIndex];
        double expense = monthlyExpense[monthIndex];
        double savings = income - expense;

        String info = String.format(Locale.getDefault(),
                "%s: Income %s | Expense %s | Savings %s",
                MONTHS[monthIndex],
                formatCurrency(income),
                formatCurrency(expense),
                formatCurrencyWithSign(savings));

        tvSelectedMonthInfo.setText(info);
        selectedMonthContainer.setVisibility(View.VISIBLE);
        selectedMonthContainer.animate().alpha(1f).setDuration(200).start();
    }

    private void hideSelectedMonthInfo() {
        if (selectedMonthContainer == null) return;
        selectedMonthContainer.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> selectedMonthContainer.setVisibility(View.GONE))
                .start();
    }

    private void animateValueChange(TextView textView, double value, String prefix) {
        if (textView == null) return;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, (float) value);
        animator.setDuration(800);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            if (animatedValue >= 100000) {
                textView.setText(String.format(Locale.getDefault(), "%s%.1fL", prefix, animatedValue / 100000));
            } else if (animatedValue >= 1000) {
                textView.setText(String.format(Locale.getDefault(), "%s%.1fK", prefix, animatedValue / 1000));
            } else {
                textView.setText(String.format(Locale.getDefault(), "%s%.0f", prefix, animatedValue));
            }
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

    // Custom Marker View for bar chart tooltip
    private class CustomMarkerView extends MarkerView {

        private TextView tvContent;

        public CustomMarkerView(Context context) {
            super(context, R.layout.marker_view_monthly);
            tvContent = findViewById(R.id.tvMarkerContent);
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            int monthIndex = (int) e.getX();
            if (monthIndex < 0 || monthIndex >= 12) return;

            double income = monthlyIncome[monthIndex];
            double expense = monthlyExpense[monthIndex];
            double savings = income - expense;

            String content = String.format(Locale.getDefault(),
                    "%s %d\n💰 Income: %s\n💸 Expense: %s\n📊 Savings: %s",
                    MONTH_SHORT[monthIndex], currentYear,
                    formatCurrency(income),
                    formatCurrency(expense),
                    formatCurrencyWithSign(savings));

            if (tvContent != null) {
                tvContent.setText(content);
            }

            super.refreshContent(e, highlight);
        }

        @Override
        public MPPointF getOffset() {
            return new MPPointF(-(getWidth() / 2f), -getHeight() - 10);
        }
    }
}