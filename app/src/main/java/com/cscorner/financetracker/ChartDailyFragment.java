package com.cscorner.financetracker.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cscorner.financetracker.R;
import com.cscorner.financetracker.model.Transaction;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChartDailyFragment extends Fragment {

    private LineChart lineChartDaily;
    private TextView tvDailyIncome, tvDailyExpense;

    private final int blueIncome = Color.parseColor("#2563EB");
    private final int redExpense = Color.parseColor("#DC2626");
    private final int textPrimary = Color.parseColor("#0F172A");
    private final int gridColor = Color.parseColor("#E5E7EB");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chart_daily, container, false);

        lineChartDaily = view.findViewById(R.id.lineChartDaily);
        tvDailyIncome = view.findViewById(R.id.tvDailyIncome);
        tvDailyExpense = view.findViewById(R.id.tvDailyExpense);

        setupLineChart();

        return view;
    }

    private void setupLineChart() {
        lineChartDaily.getDescription().setEnabled(false);
        lineChartDaily.setDrawGridBackground(false);
        lineChartDaily.setNoDataText("No daily data available");
        lineChartDaily.setNoDataTextColor(textPrimary);
        lineChartDaily.setTouchEnabled(true);
        lineChartDaily.setDragEnabled(true);
        lineChartDaily.setScaleEnabled(false);
        lineChartDaily.setPinchZoom(false);
        lineChartDaily.setExtraBottomOffset(10f);

        Legend legend = lineChartDaily.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(textPrimary);
        legend.setTextSize(11f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setDrawInside(false);

        XAxis xAxis = lineChartDaily.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(textPrimary);
        xAxis.setTextSize(10f);

        YAxis leftAxis = lineChartDaily.getAxisLeft();
        leftAxis.setTextColor(textPrimary);
        leftAxis.setTextSize(10f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(gridColor);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawZeroLine(true);

        lineChartDaily.getAxisRight().setEnabled(false);
    }

    public void updateChart(List<Transaction> monthTxns, double todayIncome, double todayExpense, Calendar currentCalendar) {
        if (!isAdded() || getContext() == null) return;

        // Update summary cards
        tvDailyIncome.setText(String.format(Locale.getDefault(), "₹%.0f", todayIncome));
        tvDailyExpense.setText(String.format(Locale.getDefault(), "₹%.0f", todayExpense));

        if (monthTxns == null || monthTxns.isEmpty()) {
            lineChartDaily.clear();
            return;
        }

        Calendar cal = (Calendar) currentCalendar.clone();
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        double[] incomePerDay = new double[daysInMonth + 1];
        double[] expensePerDay = new double[daysInMonth + 1];

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
                } else {
                    expensePerDay[day] += amt;
                }
            } catch (Exception ignored) {
            }
        }

        List<Entry> incomeEntries = new ArrayList<>();
        List<Entry> expenseEntries = new ArrayList<>();

        for (int day = 1; day <= daysInMonth; day++) {
            incomeEntries.add(new Entry(day, (float) incomePerDay[day]));
            expenseEntries.add(new Entry(day, (float) expensePerDay[day]));
        }

        LineDataSet setIncome = new LineDataSet(incomeEntries, "Income");
        setIncome.setColor(blueIncome);
        setIncome.setLineWidth(2.5f);
        setIncome.setCircleColor(blueIncome);
        setIncome.setCircleRadius(3f);
        setIncome.setDrawCircleHole(true);
        setIncome.setCircleHoleRadius(1.5f);
        setIncome.setDrawValues(false);
        setIncome.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        setIncome.setDrawFilled(true);
        setIncome.setFillColor(blueIncome);
        setIncome.setFillAlpha(30);

        LineDataSet setExpense = new LineDataSet(expenseEntries, "Expense");
        setExpense.setColor(redExpense);
        setExpense.setLineWidth(2.5f);
        setExpense.setCircleColor(redExpense);
        setExpense.setCircleRadius(3f);
        setExpense.setDrawCircleHole(true);
        setExpense.setCircleHoleRadius(1.5f);
        setExpense.setDrawValues(false);
        setExpense.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        setExpense.setDrawFilled(true);
        setExpense.setFillColor(redExpense);
        setExpense.setFillAlpha(30);

        LineData lineData = new LineData(setIncome, setExpense);

        XAxis xAxis = lineChartDaily.getXAxis();
        xAxis.setAxisMinimum(1f);
        xAxis.setAxisMaximum(daysInMonth);
        xAxis.setLabelCount(Math.min(daysInMonth, 10), false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        lineChartDaily.setData(lineData);
        lineChartDaily.animateX(800);
        lineChartDaily.invalidate();
    }
}