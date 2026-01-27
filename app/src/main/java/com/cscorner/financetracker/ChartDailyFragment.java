package com.cscorner.financetracker.fragments;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.cscorner.financetracker.R;
import com.cscorner.financetracker.model.Transaction;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChartDailyFragment extends Fragment {

    // Views
    private LineChart lineChartDaily;
    private TextView tvDailyIncome, tvDailyExpense, tvNetBalance;
    private TextView tvIncomeChange, tvExpenseChange, tvNetChange;
    private TextView tvHighestDay, tvAverageSpending, tvSelectedDayInfo;
    private TextView tvMonthYearLabel, tvTotalTransactions;
    private ImageView ivIncomeArrow, ivExpenseArrow, ivNetArrow;
    private LinearLayout selectedDayContainer;
    private CardView cardIncome, cardExpense, cardNet;

    // Colors
    private final int COLOR_INCOME = Color.parseColor("#10B981");
    private final int COLOR_INCOME_LIGHT = Color.parseColor("#D1FAE5");
    private final int COLOR_EXPENSE = Color.parseColor("#EF4444");
    private final int COLOR_EXPENSE_LIGHT = Color.parseColor("#FEE2E2");
    private final int COLOR_NET_POSITIVE = Color.parseColor("#3B82F6");
    private final int COLOR_NET_NEGATIVE = Color.parseColor("#F59E0B");
    private final int TEXT_PRIMARY = Color.parseColor("#0F172A");
    private final int TEXT_SECONDARY = Color.parseColor("#64748B");
    private final int GRID_COLOR = Color.parseColor("#E2E8F0");
    private final int HIGHLIGHT_COLOR = Color.parseColor("#6366F1");

    // Data
    private double[] incomePerDay;
    private double[] expensePerDay;
    private int currentDay = 0;
    private int daysInMonth = 30;
    private Calendar currentCalendar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chart_daily, container, false);
        initializeViews(view);
        setupLineChart();
        return view;
    }

    private void initializeViews(View view) {
        // Chart
        lineChartDaily = view.findViewById(R.id.lineChartDaily);

        // Summary Cards
        tvDailyIncome = view.findViewById(R.id.tvDailyIncome);
        tvDailyExpense = view.findViewById(R.id.tvDailyExpense);
        tvNetBalance = view.findViewById(R.id.tvNetBalance);

        // Change indicators
        tvIncomeChange = view.findViewById(R.id.tvIncomeChange);
        tvExpenseChange = view.findViewById(R.id.tvExpenseChange);
        tvNetChange = view.findViewById(R.id.tvNetChange);

        // Arrows
        ivIncomeArrow = view.findViewById(R.id.ivIncomeArrow);
        ivExpenseArrow = view.findViewById(R.id.ivExpenseArrow);
        ivNetArrow = view.findViewById(R.id.ivNetArrow);

        // Stats
        tvHighestDay = view.findViewById(R.id.tvHighestDay);
        tvAverageSpending = view.findViewById(R.id.tvAverageSpending);
        tvTotalTransactions = view.findViewById(R.id.tvTotalTransactions);

        // Labels
        tvMonthYearLabel = view.findViewById(R.id.tvMonthYearLabel);
        tvSelectedDayInfo = view.findViewById(R.id.tvSelectedDayInfo);
        selectedDayContainer = view.findViewById(R.id.selectedDayContainer);

        // Cards
        cardIncome = view.findViewById(R.id.cardIncome);
        cardExpense = view.findViewById(R.id.cardExpense);
        cardNet = view.findViewById(R.id.cardNet);
    }

    private void setupLineChart() {
        lineChartDaily.getDescription().setEnabled(false);
        lineChartDaily.setDrawGridBackground(false);
        lineChartDaily.setNoDataText("No transactions this month");
        lineChartDaily.setNoDataTextColor(TEXT_SECONDARY);
        lineChartDaily.setTouchEnabled(true);
        lineChartDaily.setDragEnabled(true);
        lineChartDaily.setScaleEnabled(false);
        lineChartDaily.setPinchZoom(false);
        lineChartDaily.setDoubleTapToZoomEnabled(false);
        lineChartDaily.setExtraBottomOffset(12f);
        lineChartDaily.setExtraTopOffset(8f);
        lineChartDaily.setExtraLeftOffset(8f);
        lineChartDaily.setExtraRightOffset(8f);

        // Set background
        lineChartDaily.setBackgroundColor(Color.TRANSPARENT);

        // Legend
        Legend legend = lineChartDaily.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(TEXT_PRIMARY);
        legend.setTextSize(11f);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(8f);
        legend.setXEntrySpace(20f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setDrawInside(false);

        // X-Axis
        XAxis xAxis = lineChartDaily.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(TEXT_SECONDARY);
        xAxis.setTextSize(10f);
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(GRID_COLOR);
        xAxis.setAxisLineWidth(1f);

        // Y-Axis
        YAxis leftAxis = lineChartDaily.getAxisLeft();
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
                if (value >= 1000) {
                    return String.format(Locale.getDefault(), "%.1fK", value / 1000);
                }
                return String.format(Locale.getDefault(), "%.0f", value);
            }
        });

        lineChartDaily.getAxisRight().setEnabled(false);

        // Chart value selected listener
        lineChartDaily.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                showSelectedDayInfo((int) e.getX());
            }

            @Override
            public void onNothingSelected() {
                hideSelectedDayInfo();
            }
        });

        // Set custom marker
        if (getContext() != null) {
            CustomMarkerView marker = new CustomMarkerView(getContext());
            marker.setChartView(lineChartDaily);
            lineChartDaily.setMarker(marker);
        }
    }

    public void updateChart(List<Transaction> monthTxns, double todayIncome, double todayExpense, Calendar calendar) {
        if (!isAdded() || getContext() == null) return;

        this.currentCalendar = (Calendar) calendar.clone();
        this.currentDay = calendar.get(Calendar.DAY_OF_MONTH);

        // Update month/year label
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        if (tvMonthYearLabel != null) {
            tvMonthYearLabel.setText(monthYearFormat.format(calendar.getTime()));
        }

        // Calculate net balance
        double netBalance = todayIncome - todayExpense;

        // Update summary cards with animation
        animateValueChange(tvDailyIncome, todayIncome, "₹");
        animateValueChange(tvDailyExpense, todayExpense, "₹");
        animateValueChange(tvNetBalance, Math.abs(netBalance), netBalance >= 0 ? "+₹" : "-₹");

        // Update net balance card color
        updateNetBalanceCard(netBalance);

        if (monthTxns == null || monthTxns.isEmpty()) {
            lineChartDaily.clear();
            resetStats();
            return;
        }

        // Calculate daily data
        daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        incomePerDay = new double[daysInMonth + 1];
        expensePerDay = new double[daysInMonth + 1];
        int[] transactionCount = new int[daysInMonth + 1];

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        Calendar tcal = Calendar.getInstance();

        int totalTransactions = 0;
        double totalMonthExpense = 0;
        int highestSpendingDay = 1;
        double highestSpending = 0;

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
                } else {
                    expensePerDay[day] += amt;
                    totalMonthExpense += amt;

                    if (expensePerDay[day] > highestSpending) {
                        highestSpending = expensePerDay[day];
                        highestSpendingDay = day;
                    }
                }
                transactionCount[day]++;
                totalTransactions++;

            } catch (Exception ignored) {
            }
        }

        // Calculate previous day's values for comparison
        calculateDayComparison(todayIncome, todayExpense, netBalance);

        // Update statistics
        updateStatistics(highestSpendingDay, highestSpending, totalMonthExpense, totalTransactions);

        // Create chart entries
        List<Entry> incomeEntries = new ArrayList<>();
        List<Entry> expenseEntries = new ArrayList<>();

        for (int day = 1; day <= daysInMonth; day++) {
            incomeEntries.add(new Entry(day, (float) incomePerDay[day]));
            expenseEntries.add(new Entry(day, (float) expensePerDay[day]));
        }

        // Create datasets
        LineDataSet setIncome = createIncomeDataSet(incomeEntries);
        LineDataSet setExpense = createExpenseDataSet(expenseEntries);

        LineData lineData = new LineData(setIncome, setExpense);

        // Configure X-Axis
        XAxis xAxis = lineChartDaily.getXAxis();
        xAxis.setAxisMinimum(0.5f);
        xAxis.setAxisMaximum(daysInMonth + 0.5f);
        xAxis.setLabelCount(Math.min(daysInMonth, 8), false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int day = (int) value;
                if (day < 1 || day > daysInMonth) return "";
                return String.valueOf(day);
            }
        });

        // Add current day indicator line
        addCurrentDayIndicator(xAxis);

        lineChartDaily.setData(lineData);
        lineChartDaily.animateX(1000, com.github.mikephil.charting.animation.Easing.EaseOutCubic);
        lineChartDaily.invalidate();

        // Highlight current day
        highlightCurrentDay();
    }

    private LineDataSet createIncomeDataSet(List<Entry> entries) {
        LineDataSet dataSet = new LineDataSet(entries, "Income");
        dataSet.setColor(COLOR_INCOME);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(COLOR_INCOME);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleRadius(2f);
        dataSet.setCircleHoleColor(Color.WHITE);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.15f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(COLOR_INCOME);
        dataSet.setFillAlpha(40);
        dataSet.setHighLightColor(COLOR_INCOME);
        dataSet.setHighlightLineWidth(1.5f);
        dataSet.setDrawHorizontalHighlightIndicator(false);
        return dataSet;
    }

    private LineDataSet createExpenseDataSet(List<Entry> entries) {
        LineDataSet dataSet = new LineDataSet(entries, "Expense");
        dataSet.setColor(COLOR_EXPENSE);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(COLOR_EXPENSE);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleRadius(2f);
        dataSet.setCircleHoleColor(Color.WHITE);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.15f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(COLOR_EXPENSE);
        dataSet.setFillAlpha(40);
        dataSet.setHighLightColor(COLOR_EXPENSE);
        dataSet.setHighlightLineWidth(1.5f);
        dataSet.setDrawHorizontalHighlightIndicator(false);
        return dataSet;
    }

    private void addCurrentDayIndicator(XAxis xAxis) {
        // Remove existing limit lines
        xAxis.removeAllLimitLines();

        // Add current day indicator
        LimitLine todayLine = new LimitLine(currentDay, "Today");
        todayLine.setLineWidth(1f);
        todayLine.setLineColor(HIGHLIGHT_COLOR);
        todayLine.enableDashedLine(8f, 6f, 0f);
        todayLine.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        todayLine.setTextSize(9f);
        todayLine.setTextColor(HIGHLIGHT_COLOR);
        xAxis.addLimitLine(todayLine);
    }

    private void highlightCurrentDay() {
        if (lineChartDaily.getData() != null && currentDay > 0 && currentDay <= daysInMonth) {
            lineChartDaily.highlightValue(currentDay, 0, false);
        }
    }

    private void calculateDayComparison(double todayIncome, double todayExpense, double netBalance) {
        if (currentDay <= 1) {
            // First day of month - no comparison
            setChangeIndicator(tvIncomeChange, ivIncomeArrow, 0, true);
            setChangeIndicator(tvExpenseChange, ivExpenseArrow, 0, false);
            setChangeIndicator(tvNetChange, ivNetArrow, 0, true);
            return;
        }

        // Get yesterday's values
        double yesterdayIncome = incomePerDay != null && currentDay > 1 ? incomePerDay[currentDay - 1] : 0;
        double yesterdayExpense = expensePerDay != null && currentDay > 1 ? expensePerDay[currentDay - 1] : 0;
        double yesterdayNet = yesterdayIncome - yesterdayExpense;

        // Calculate changes
        double incomeChange = todayIncome - yesterdayIncome;
        double expenseChange = todayExpense - yesterdayExpense;
        double netChange = netBalance - yesterdayNet;

        setChangeIndicator(tvIncomeChange, ivIncomeArrow, incomeChange, true);
        setChangeIndicator(tvExpenseChange, ivExpenseArrow, expenseChange, false);
        setChangeIndicator(tvNetChange, ivNetArrow, netChange, true);
    }

    private void setChangeIndicator(TextView tvChange, ImageView ivArrow, double change, boolean positiveIsGood) {
        if (tvChange == null || ivArrow == null) return;

        String changeText;
        int arrowDrawable;
        int textColor;

        if (Math.abs(change) < 0.01) {
            changeText = "No change";
            arrowDrawable = R.drawable.ic_arrow_neutral;
            textColor = TEXT_SECONDARY;
        } else if (change > 0) {
            changeText = String.format(Locale.getDefault(), "+₹%.0f", change);
            arrowDrawable = R.drawable.ic_arrow_up_small;
            textColor = positiveIsGood ? COLOR_INCOME : COLOR_EXPENSE;
        } else {
            changeText = String.format(Locale.getDefault(), "-₹%.0f", Math.abs(change));
            arrowDrawable = R.drawable.ic_arrow_down_small;
            textColor = positiveIsGood ? COLOR_EXPENSE : COLOR_INCOME;
        }

        tvChange.setText(changeText);
        tvChange.setTextColor(textColor);
        ivArrow.setImageResource(arrowDrawable);
        ivArrow.setColorFilter(textColor);
    }

    private void updateNetBalanceCard(double netBalance) {
        if (cardNet == null || tvNetBalance == null) return;

        int bgColor, textColor;
        String prefix;

        if (netBalance >= 0) {
            bgColor = Color.parseColor("#EFF6FF"); // Light blue
            textColor = COLOR_NET_POSITIVE;
            prefix = "+₹";
        } else {
            bgColor = Color.parseColor("#FFFBEB"); // Light amber
            textColor = COLOR_NET_NEGATIVE;
            prefix = "-₹";
        }

        cardNet.setCardBackgroundColor(bgColor);
        tvNetBalance.setTextColor(textColor);
    }

    private void updateStatistics(int highestDay, double highestSpending, double totalExpense, int totalTransactions) {
        if (tvHighestDay != null) {
            if (highestSpending > 0) {
                String ordinal = getOrdinalSuffix(highestDay);
                tvHighestDay.setText(String.format(Locale.getDefault(), "%d%s (₹%.0f)", highestDay, ordinal, highestSpending));
            } else {
                tvHighestDay.setText("No expenses yet");
            }
        }

        if (tvAverageSpending != null) {
            double avgSpending = currentDay > 0 ? totalExpense / currentDay : 0;
            tvAverageSpending.setText(String.format(Locale.getDefault(), "₹%.0f/day", avgSpending));
        }

        if (tvTotalTransactions != null) {
            tvTotalTransactions.setText(String.valueOf(totalTransactions));
        }
    }

    private void resetStats() {
        if (tvHighestDay != null) tvHighestDay.setText("--");
        if (tvAverageSpending != null) tvAverageSpending.setText("₹0/day");
        if (tvTotalTransactions != null) tvTotalTransactions.setText("0");
    }

    private String getOrdinalSuffix(int day) {
        if (day >= 11 && day <= 13) return "th";
        switch (day % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }

    private void showSelectedDayInfo(int day) {
        if (selectedDayContainer == null || tvSelectedDayInfo == null) return;
        if (day < 1 || day > daysInMonth) return;

        double income = incomePerDay != null ? incomePerDay[day] : 0;
        double expense = expensePerDay != null ? expensePerDay[day] : 0;
        double net = income - expense;

        String info = String.format(Locale.getDefault(),
                "Day %d: Income ₹%.0f | Expense ₹%.0f | Net %s₹%.0f",
                day, income, expense, net >= 0 ? "+" : "-", Math.abs(net));

        tvSelectedDayInfo.setText(info);
        selectedDayContainer.setVisibility(View.VISIBLE);
        selectedDayContainer.animate().alpha(1f).setDuration(200).start();
    }

    private void hideSelectedDayInfo() {
        if (selectedDayContainer == null) return;
        selectedDayContainer.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> selectedDayContainer.setVisibility(View.GONE))
                .start();
    }

    private void animateValueChange(TextView textView, double value, String prefix) {
        if (textView == null) return;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, (float) value);
        animator.setDuration(800);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            textView.setText(String.format(Locale.getDefault(), "%s%.0f", prefix, animatedValue));
        });
        animator.start();
    }

    // Custom Marker View for chart tooltip
    private class CustomMarkerView extends MarkerView {

        private TextView tvContent;

        public CustomMarkerView(Context context) {
            super(context, R.layout.marker_view_daily);
            tvContent = findViewById(R.id.tvMarkerContent);
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            int day = (int) e.getX();
            if (day < 1 || day > daysInMonth) return;

            double income = incomePerDay != null ? incomePerDay[day] : 0;
            double expense = expensePerDay != null ? expensePerDay[day] : 0;

            String content;
            if (highlight.getDataSetIndex() == 0) {
                content = String.format(Locale.getDefault(), "Day %d\nIncome: ₹%.0f", day, income);
            } else {
                content = String.format(Locale.getDefault(), "Day %d\nExpense: ₹%.0f", day, expense);
            }

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