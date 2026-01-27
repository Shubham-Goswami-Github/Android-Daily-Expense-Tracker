package com.cscorner.financetracker;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.cscorner.financetracker.adapter.TransactionAdapter;
import com.cscorner.financetracker.fragments.ChartDailyFragment;
import com.cscorner.financetracker.fragments.ChartMonthlyFragment;
import com.cscorner.financetracker.fragments.ChartOverallFragment;
import com.cscorner.financetracker.model.Transaction;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.text.ParseException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import de.hdodenhof.circleimageview.CircleImageView;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";

    // UI Elements
    private Spinner spinnerMonth;
    private TextView tvMonthlySalary, tvTotalBalance, tvTotalIncome, tvTotalExpense;
    private TextView tvBudgetProgress, tvSpendingPercent;
    private ProgressBar progressBudget;
    private RecyclerView rvTransactions;
    private TextView currentDate;
    private ImageView previousDateButton, nextDateButton;
    private CardView chartsCard;
    private MaterialCardView dateNavigationCard, balanceCard;
    private ConstraintLayout balanceCardInner;
    private LinearLayout dateSwipeContainer, emptyStateContainer, swipeHintContainer;
    private TextView tvGreeting;
    private ImageView btnCollapseCharts, btnToggleBalance;
    private View topBar;
    private CircleImageView imgProfile;
    // Swipe tracking variables
    private float touchStartX = 0f;
    private float touchStartY = 0f;
    private boolean isHorizontalSwipe = false;

    // ViewPager and TabLayout for Charts
    private ViewPager2 viewPagerCharts;
    private TabLayout tabLayoutCharts;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // Adapter & Data
    private TransactionAdapter transactionAdapter;
    private List<Transaction> transactionList;

    // Values
    private double monthlySalary = 0;
    private double totalIncome = 0;
    private double totalExpense = 0;
    private double totalMonthExpense = 0;
    private double totalMonthIncome = 0;

    // Calendar
    private Calendar currentCalendar;

    // Activity Result Launcher
    private ActivityResultLauncher<Intent> addTransactionLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;

    // Monthly transactions for charts
    private final List<Transaction> monthTransactionList = new ArrayList<>();

    // All transactions for overall chart
    private final List<Transaction> allTransactionsList = new ArrayList<>();

    // Swipe Gesture Detectors
    private GestureDetector dateGestureDetector;
    private GestureDetector balanceCardGestureDetector;

    // Swipe Configuration
    private static final int SWIPE_THRESHOLD = 80;
    private static final int SWIPE_VELOCITY_THRESHOLD = 80;

    // Load throttle
    private volatile boolean isLoading = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Chart Fragments
    private ChartDailyFragment chartDailyFragment;
    private ChartMonthlyFragment chartMonthlyFragment;
    private ChartOverallFragment chartOverallFragment;

    // Balance visibility
    private boolean isBalanceVisible = true;
    private String actualBalanceText = "₹ 0.00";

    // Animation flag
    private boolean isAnimating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdge();
        setContentView(R.layout.activity_dashboard);
        setupWindowInsets();

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI
        initializeViews();
        setupRecyclerView();
        setupSpinner();
        setupActivityResultLauncher();
        setupChartViewPager();
        setupClickListeners();
        setupSwipeGestures();
        updateGreeting();

        // Initial load
        currentCalendar = Calendar.getInstance();
        updateCurrentDate();

        // Load all transactions for overall stats
        loadAllTransactions();

        // Hide swipe hint after 3 seconds
        hideSwipeHintAfterDelay();
    }

    private void setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#4F46E5"));
        }

        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false);
        }
    }

    private void setupWindowInsets() {
        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (topBar != null) {
                topBar.setPadding(
                        topBar.getPaddingLeft(),
                        systemBars.top + 16,
                        topBar.getPaddingRight(),
                        topBar.getPaddingBottom()
                );
            }

            return insets;
        });
    }

    private void initializeViews() {
        topBar = findViewById(R.id.topBar);
        spinnerMonth = findViewById(R.id.spinnerMonth);
        tvMonthlySalary = findViewById(R.id.tvMonthlySalary);
        tvTotalBalance = findViewById(R.id.tvTotalBalance);
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpense = findViewById(R.id.tvTotalExpense);
        tvBudgetProgress = findViewById(R.id.tvBudgetProgress);
        tvSpendingPercent = findViewById(R.id.tvSpendingPercent);
        progressBudget = findViewById(R.id.progressBudget);
        rvTransactions = findViewById(R.id.rvTransactions);
        currentDate = findViewById(R.id.currentDate);
        previousDateButton = findViewById(R.id.previousDateButton);
        nextDateButton = findViewById(R.id.nextDateButton);
        chartsCard = findViewById(R.id.chartsCard);
        dateNavigationCard = findViewById(R.id.dateNavigationCard);
        dateSwipeContainer = findViewById(R.id.dateSwipeContainer);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvGreeting = findViewById(R.id.tvGreeting);
        btnCollapseCharts = findViewById(R.id.btnCollapseCharts);
        imgProfile = findViewById(R.id.imgProfile);
        btnToggleBalance = findViewById(R.id.btnToggleBalance);
        swipeHintContainer = findViewById(R.id.swipeHintContainer);

        // Balance card views
        balanceCard = findViewById(R.id.balanceCard);
        balanceCardInner = findViewById(R.id.balanceCardInner);

        // Chart ViewPager and TabLayout
        viewPagerCharts = findViewById(R.id.viewPagerCharts);
        tabLayoutCharts = findViewById(R.id.tabLayoutCharts);

        if (topBar != null) {
            topBar.post(() -> ViewCompat.requestApplyInsets(topBar));
        }
    }

    private void setupRecyclerView() {
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setHasFixedSize(false);
        rvTransactions.setNestedScrollingEnabled(false);
        transactionList = new ArrayList<>();
        transactionAdapter = new TransactionAdapter(transactionList);
        rvTransactions.setAdapter(transactionAdapter);

        // Add item animation
        rvTransactions.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
    }

    private void setupSpinner() {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, months);
        spinnerMonth.setAdapter(monthAdapter);

        currentCalendar = Calendar.getInstance();
        String currentMonth = new SimpleDateFormat("MMMM", Locale.getDefault())
                .format(currentCalendar.getTime());
        int currentMonthIndex = java.util.Arrays.asList(months).indexOf(currentMonth);
        spinnerMonth.setSelection(currentMonthIndex);

        spinnerMonth.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            private boolean isFirstSelection = true;

            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (isFirstSelection) {
                    isFirstSelection = false;
                    return;
                }
                currentCalendar.set(Calendar.MONTH, position);
                currentCalendar.set(Calendar.DAY_OF_MONTH, 1);
                updateCurrentDate();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void setupActivityResultLauncher() {
        addTransactionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // Force refresh all data
                        refreshAllData();
                    }
                }
        );

        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Refresh data when returning from settings (salary might have changed)
                    refreshAllData();
                }
        );
    }

    private void refreshAllData() {
        isLoading = false; // Reset loading flag to force refresh
        updateCurrentDate();
        loadAllTransactions();
        updateGreeting();
    }

    private void setupChartViewPager() {
        // Initialize fragments
        chartDailyFragment = new ChartDailyFragment();
        chartMonthlyFragment = new ChartMonthlyFragment();
        chartOverallFragment = new ChartOverallFragment();

        // Setup adapter
        ChartsPagerAdapter adapter = new ChartsPagerAdapter(this);
        viewPagerCharts.setAdapter(adapter);
        viewPagerCharts.setOffscreenPageLimit(3);

        // Link TabLayout with ViewPager2
        new TabLayoutMediator(tabLayoutCharts, viewPagerCharts, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("📅 Daily");
                    break;
                case 1:
                    tab.setText("📊 Monthly");
                    break;
                case 2:
                    tab.setText("📈 Overall");
                    break;
            }
        }).attach();

        viewPagerCharts.setUserInputEnabled(true);
    }

    private class ChartsPagerAdapter extends FragmentStateAdapter {

        public ChartsPagerAdapter(AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return chartDailyFragment;
                case 1:
                    return chartMonthlyFragment;
                case 2:
                    return chartOverallFragment;
                default:
                    return chartDailyFragment;
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    private void setupClickListeners() {
        // Profile icon -> Settings
        imgProfile.setOnClickListener(v -> {
            animateClick(v);
            settingsLauncher.launch(new Intent(DashboardActivity.this, SettingsActivity.class));
        });

        // Collapse charts button
        if (btnCollapseCharts != null) {
            btnCollapseCharts.setOnClickListener(v -> {
                animateClick(v);
                hideChartsWithAnimation();
            });
        }

        // Balance visibility toggle
        if (btnToggleBalance != null) {
            btnToggleBalance.setOnClickListener(v -> {
                animateClick(v);
                toggleBalanceVisibility();
            });
        }

        // Date navigation buttons
        previousDateButton.setOnClickListener(v -> {
            animateClick(v);
            if (!isAnimating) {
                animateDateChange(-1);
            }
        });

        nextDateButton.setOnClickListener(v -> {
            animateClick(v);
            if (!isAnimating) {
                animateDateChange(1);
            }
        });

        // Date display container click to show date picker (optional)
        View dateDisplayContainer = findViewById(R.id.dateDisplayContainer);
        if (dateDisplayContainer != null) {
            dateDisplayContainer.setOnClickListener(v -> showDatePicker());
        }

        // Quick action buttons
        findViewById(R.id.btnQuickIncome).setOnClickListener(v -> {
            animateClick(v);
            Intent intent = new Intent(DashboardActivity.this, AddTransactionActivity.class);
            intent.putExtra("transactionType", "Income");
            intent.putExtra("selectedMonth", spinnerMonth.getSelectedItem().toString());
            addTransactionLauncher.launch(intent);
        });

        findViewById(R.id.btnQuickExpense).setOnClickListener(v -> {
            animateClick(v);
            Intent intent = new Intent(DashboardActivity.this, AddTransactionActivity.class);
            intent.putExtra("transactionType", "Expense");
            intent.putExtra("selectedMonth", spinnerMonth.getSelectedItem().toString());
            addTransactionLauncher.launch(intent);
        });

        findViewById(R.id.btnQuickStats).setOnClickListener(v -> {
            animateClick(v);
            toggleCharts();
        });

        // Add first transaction button
        findViewById(R.id.btnAddFirstTransaction).setOnClickListener(v -> {
            animateClick(v);
            Intent intent = new Intent(DashboardActivity.this, AddTransactionActivity.class);
            intent.putExtra("selectedMonth", spinnerMonth.getSelectedItem().toString());
            addTransactionLauncher.launch(intent);
        });

        // See all transactions
        TextView btnSeeAll = findViewById(R.id.btnSeeAllTransactions);
        if (btnSeeAll != null) {
            btnSeeAll.setOnClickListener(v -> {
                animateClick(v);
                // Navigate to all transactions activity if you have one
                // Intent intent = new Intent(this, AllTransactionsActivity.class);
                // startActivity(intent);
                Toast.makeText(this, "Coming soon!", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void animateClick(View view) {
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f);
        scaleDownX.setDuration(80);
        scaleDownY.setDuration(80);

        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 0.9f, 1f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.9f, 1f);
        scaleUpX.setDuration(80);
        scaleUpY.setDuration(80);

        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.playTogether(scaleDownX, scaleDownY);

        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.playTogether(scaleUpX, scaleUpY);

        AnimatorSet bounce = new AnimatorSet();
        bounce.playSequentially(scaleDown, scaleUp);
        bounce.start();
    }

    private void showDatePicker() {
        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    currentCalendar.set(Calendar.YEAR, year);
                    currentCalendar.set(Calendar.MONTH, month);
                    currentCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateCurrentDate();
                },
                currentCalendar.get(Calendar.YEAR),
                currentCalendar.get(Calendar.MONTH),
                currentCalendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void toggleBalanceVisibility() {
        isBalanceVisible = !isBalanceVisible;

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(tvTotalBalance, "alpha", 1f, 0f);
        fadeOut.setDuration(150);

        fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (isBalanceVisible) {
                    tvTotalBalance.setText(actualBalanceText);
                    btnToggleBalance.setImageResource(R.drawable.ic_eye_visible);
                } else {
                    tvTotalBalance.setText("₹ ••••••");
                    btnToggleBalance.setImageResource(R.drawable.ic_eye_hidden);
                }

                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(tvTotalBalance, "alpha", 0f, 1f);
                fadeIn.setDuration(150);
                fadeIn.start();
            }
        });

        fadeOut.start();
    }

    private void setupSwipeGestures() {
        // Date Navigation Swipe Gesture
        dateGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // Return true if horizontal scroll
                return Math.abs(distanceX) > Math.abs(distanceY);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                if (isAnimating) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                // Check if horizontal swipe
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe Right -> Previous date
                            animateDateChange(-1);
                        } else {
                            // Swipe Left -> Next date
                            animateDateChange(1);
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        // Balance Card Swipe Gesture
        balanceCardGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // Return true if horizontal scroll
                return Math.abs(distanceX) > Math.abs(distanceY);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                if (isAnimating) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                // Check if horizontal swipe
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe Right -> Previous date
                            animateBalanceCardSwipe(-1);
                        } else {
                            // Swipe Left -> Next date
                            animateBalanceCardSwipe(1);
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        // Apply touch listeners
        setupTouchListener(dateNavigationCard, dateGestureDetector);
        setupTouchListener(dateSwipeContainer, dateGestureDetector);

        View dateDisplayContainer = findViewById(R.id.dateDisplayContainer);
        if (dateDisplayContainer != null) {
            setupTouchListener(dateDisplayContainer, dateGestureDetector);
        }

        setupTouchListener(balanceCard, balanceCardGestureDetector);
        setupTouchListener(balanceCardInner, balanceCardGestureDetector);
    }
    private void setupTouchListener(View view, GestureDetector gestureDetector) {
        if (view == null) return;

        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Store initial touch position
                    touchStartX = event.getX();
                    touchStartY = event.getY();
                    isHorizontalSwipe = false;

                    // Visual feedback
                    v.setAlpha(0.95f);

                    // Initially request parent to not intercept
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = Math.abs(event.getX() - touchStartX);
                    float deltaY = Math.abs(event.getY() - touchStartY);

                    // Determine if this is a horizontal swipe
                    if (deltaX > 10 || deltaY > 10) {
                        if (deltaX > deltaY) {
                            // Horizontal swipe detected - don't let parent intercept
                            isHorizontalSwipe = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        } else {
                            // Vertical scroll - let parent (NestedScrollView) handle
                            isHorizontalSwipe = false;
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    v.animate().alpha(1f).setDuration(100).start();
                    v.getParent().requestDisallowInterceptTouchEvent(false);

                    // Check for tap/click
                    float upDeltaX = Math.abs(event.getX() - touchStartX);
                    float upDeltaY = Math.abs(event.getY() - touchStartY);
                    if (upDeltaX < 10 && upDeltaY < 10) {
                        v.performClick();
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                    v.animate().alpha(1f).setDuration(100).start();
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    isHorizontalSwipe = false;
                    break;
            }

            // Pass event to gesture detector
            gestureDetector.onTouchEvent(event);

            // Always return true to handle the touch
            return true;
        });
    }
    private void animateBalanceCardSwipe(int direction) {
        if (isAnimating) return;
        isAnimating = true;

        float slideDistance = 60f;
        float startX = 0f;
        float endX = direction > 0 ? -slideDistance : slideDistance;

        ObjectAnimator slideOut = ObjectAnimator.ofFloat(balanceCardInner, "translationX", startX, endX);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(balanceCardInner, "alpha", 1f, 0.7f);
        slideOut.setDuration(120);
        fadeOut.setDuration(120);
        slideOut.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet outSet = new AnimatorSet();
        outSet.playTogether(slideOut, fadeOut);
        outSet.start();

        mainHandler.postDelayed(() -> {
            changeDate(direction);

            float returnStartX = direction > 0 ? slideDistance : -slideDistance;
            ObjectAnimator slideIn = ObjectAnimator.ofFloat(balanceCardInner, "translationX", returnStartX, 0f);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(balanceCardInner, "alpha", 0.7f, 1f);
            slideIn.setDuration(180);
            fadeIn.setDuration(180);
            slideIn.setInterpolator(new OvershootInterpolator(1.2f));

            AnimatorSet inSet = new AnimatorSet();
            inSet.playTogether(slideIn, fadeIn);
            inSet.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    isAnimating = false;
                }
            });
            inSet.start();
        }, 120);
    }

    private void animateDateChange(int direction) {
        if (isAnimating) return;
        isAnimating = true;

        float slideDistance = 30f;
        float endX = direction > 0 ? -slideDistance : slideDistance;

        ObjectAnimator slideOut = ObjectAnimator.ofFloat(currentDate, "translationX", 0f, endX);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(currentDate, "alpha", 1f, 0f);
        slideOut.setDuration(100);
        fadeOut.setDuration(100);

        AnimatorSet outSet = new AnimatorSet();
        outSet.playTogether(slideOut, fadeOut);
        outSet.start();

        mainHandler.postDelayed(() -> {
            changeDate(direction);

            float returnStartX = direction > 0 ? slideDistance : -slideDistance;
            ObjectAnimator slideIn = ObjectAnimator.ofFloat(currentDate, "translationX", returnStartX, 0f);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(currentDate, "alpha", 0f, 1f);
            slideIn.setDuration(150);
            fadeIn.setDuration(150);
            slideIn.setInterpolator(new OvershootInterpolator(1.5f));

            AnimatorSet inSet = new AnimatorSet();
            inSet.playTogether(slideIn, fadeIn);
            inSet.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    isAnimating = false;
                }
            });
            inSet.start();
        }, 100);
    }

    private void hideSwipeHintAfterDelay() {
        if (swipeHintContainer != null) {
            mainHandler.postDelayed(() -> {
                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(swipeHintContainer, "alpha", 0.7f, 0f);
                fadeOut.setDuration(500);
                fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        swipeHintContainer.setVisibility(View.GONE);
                    }
                });
                fadeOut.start();
            }, 4000);
        }
    }

    private void updateGreeting() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        String greeting;
        if (hour >= 5 && hour < 12) {
            greeting = "Good Morning! ☀️";
        } else if (hour >= 12 && hour < 17) {
            greeting = "Good Afternoon! 🌤️";
        } else if (hour >= 17 && hour < 21) {
            greeting = "Good Evening! 🌅";
        } else {
            greeting = "Good Night! 🌙";
        }

        if (tvGreeting != null) {
            tvGreeting.setText(greeting);
        }
    }

    private void toggleCharts() {
        if (chartsCard.getVisibility() == View.VISIBLE) {
            hideChartsWithAnimation();
        } else {
            showChartsWithAnimation();
        }
    }

    private void showChartsWithAnimation() {
        chartsCard.setVisibility(View.VISIBLE);
        chartsCard.setAlpha(0f);
        chartsCard.setTranslationY(-50f);

        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(chartsCard, "alpha", 0f, 1f);
        ObjectAnimator slideIn = ObjectAnimator.ofFloat(chartsCard, "translationY", -50f, 0f);
        fadeIn.setDuration(300);
        slideIn.setDuration(300);
        slideIn.setInterpolator(new OvershootInterpolator(1.2f));

        AnimatorSet animSet = new AnimatorSet();
        animSet.playTogether(fadeIn, slideIn);
        animSet.start();

        // Update chart data
        updateChartFragments();
    }

    private void hideChartsWithAnimation() {
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(chartsCard, "alpha", 1f, 0f);
        ObjectAnimator slideOut = ObjectAnimator.ofFloat(chartsCard, "translationY", 0f, -30f);
        fadeOut.setDuration(200);
        slideOut.setDuration(200);

        AnimatorSet animSet = new AnimatorSet();
        animSet.playTogether(fadeOut, slideOut);
        animSet.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                chartsCard.setVisibility(View.GONE);
                chartsCard.setTranslationY(0f);
                chartsCard.setAlpha(1f);
            }
        });
        animSet.start();
    }

    private void updateChartFragments() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        mainHandler.post(() -> {
            // Update Daily Chart Fragment
            if (chartDailyFragment != null && chartDailyFragment.isAdded()) {
                chartDailyFragment.updateChart(monthTransactionList, totalIncome, totalExpense, currentCalendar);
            }

            // Update Monthly Chart Fragment
            if (chartMonthlyFragment != null && chartMonthlyFragment.isAdded()) {
                chartMonthlyFragment.updateChart(db, user.getEmail());
            }

            // Update Overall Chart Fragment
            if (chartOverallFragment != null && chartOverallFragment.isAdded()) {
                chartOverallFragment.updateChart(allTransactionsList);
            }
        });
    }

    private void loadAllTransactions() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) return;

        String email = currentUser.getEmail();

        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        // Clear existing data
        allTransactionsList.clear();

        AtomicInteger completedMonths = new AtomicInteger(0);
        int totalMonths = months.length;

        for (String month : months) {
            db.collection("Users").document(email)
                    .collection("Financial Details")
                    .document("Monthly Expenditure")
                    .collection(month)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            try {
                                Transaction txn = parseTransaction(doc);
                                if (txn != null) {
                                    synchronized (allTransactionsList) {
                                        allTransactionsList.add(txn);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing transaction", e);
                            }
                        }

                        // Check if all months are loaded
                        if (completedMonths.incrementAndGet() == totalMonths) {
                            // Sort all transactions by date
                            sortTransactionsByDate(allTransactionsList);

                            // Update overall chart if visible
                            if (chartsCard.getVisibility() == View.VISIBLE &&
                                    chartOverallFragment != null && chartOverallFragment.isAdded()) {
                                mainHandler.post(() -> chartOverallFragment.updateChart(allTransactionsList));
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading month: " + month, e);
                        completedMonths.incrementAndGet();
                    });
        }
    }

    private Transaction parseTransaction(QueryDocumentSnapshot doc) {
        try {
            String date = doc.getString("date");
            if (date == null) return null;

            String type = doc.getString("type") != null ? doc.getString("type").trim() : "Expense";
            String note = doc.getString("note") != null ? doc.getString("note") : "";
            String time = doc.getString("time") != null ? doc.getString("time") : "";

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

            boolean isIncome = type.equalsIgnoreCase("Income");
            Transaction txn = new Transaction(amount, type, date, time, note, isIncome);
            txn.setTransactionId(doc.getId());
            return txn;

        } catch (Exception e) {
            Log.e(TAG, "Error parsing transaction", e);
            return null;
        }
    }

    private void sortTransactionsByDate(List<Transaction> transactions) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        Collections.sort(transactions, (a, b) -> {
            try {
                Date da = sdf.parse(a.getDate());
                Date db = sdf.parse(b.getDate());
                if (da == null || db == null) return 0;
                return db.compareTo(da); // Descending order (newest first)
            } catch (Exception e) {
                return 0;
            }
        });
    }

    private void loadDashboardData(String month, String selectedDateOnly) {
        if (isLoading) {
            Log.d(TAG, "Already loading, skipping...");
            return;
        }

        isLoading = true;
        Log.d(TAG, "Loading data for month=" + month + " date=" + selectedDateOnly);

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No user logged in!", Toast.LENGTH_SHORT).show();
            isLoading = false;
            return;
        }

        String email = currentUser.getEmail();
        if (email == null || email.isEmpty()) {
            Toast.makeText(this, "User email not found", Toast.LENGTH_SHORT).show();
            isLoading = false;
            return;
        }

        // Show loading state
        showLoadingState();

        // Fetch salary first
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
                    Log.e(TAG, "Error fetching salary", e);
                    monthlySalary = 0.0;
                    loadTransactions(email, month, selectedDateOnly);
                });
    }

    private void showLoadingState() {
        // Optional: Add shimmer or loading indicator
        tvTotalBalance.setAlpha(0.5f);
        tvTotalIncome.setAlpha(0.5f);
        tvTotalExpense.setAlpha(0.5f);
    }

    private void hideLoadingState() {
        ObjectAnimator.ofFloat(tvTotalBalance, "alpha", 0.5f, 1f).setDuration(200).start();
        ObjectAnimator.ofFloat(tvTotalIncome, "alpha", 0.5f, 1f).setDuration(200).start();
        ObjectAnimator.ofFloat(tvTotalExpense, "alpha", 0.5f, 1f).setDuration(200).start();
    }

    private void loadTransactions(String email, String month, String selectedDateOnly) {
        // Clear previous data
        transactionList.clear();
        monthTransactionList.clear();
        totalIncome = 0;
        totalExpense = 0;
        totalMonthExpense = 0;
        totalMonthIncome = 0;

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
                        isLoading = false;
                        hideLoadingState();
                        return;
                    }

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Transaction txn = parseTransaction(doc);
                        if (txn == null) continue;

                        // Add to month list
                        monthTransactionList.add(txn);

                        // Track monthly totals
                        if (txn.isIncome()) {
                            totalMonthIncome += txn.getAmount();
                        } else {
                            totalMonthExpense += txn.getAmount();
                        }

                        // Check if transaction is for selected date
                        try {
                            Date txnDate = sdf.parse(txn.getDate());
                            if (txnDate != null && sdf.format(txnDate).equals(selectedDateOnly)) {
                                transactionList.add(txn);
                                if (txn.isIncome()) {
                                    totalIncome += txn.getAmount();
                                } else {
                                    totalExpense += txn.getAmount();
                                }
                            }
                        } catch (ParseException ignored) {
                        }
                    }

                    // Sort transactions by time (newest first)
                    sortTransactionsByTime(transactionList);
                    sortTransactionsByDate(monthTransactionList);

                    // Compute balance
                    double balance = computeBalanceUpToDate(monthTransactionList, selectedDate);

                    // Update UI on main thread
                    mainHandler.post(() -> {
                        updateDashboardUI(balance);
                        transactionAdapter.notifyDataSetChanged();
                        updateEmptyState();
                        hideLoadingState();

                        // Update charts if visible
                        if (chartsCard.getVisibility() == View.VISIBLE) {
                            updateChartFragments();
                        }

                        isLoading = false;
                    });

                })
                .addOnFailureListener(e -> {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Failed to fetch transactions", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Transaction fetch error", e);
                        updateEmptyState();
                        hideLoadingState();
                        isLoading = false;
                    });
                });
    }

    private void sortTransactionsByTime(List<Transaction> transactions) {
        Collections.sort(transactions, (a, b) -> {
            String timeA = a.getTime() != null ? a.getTime() : "";
            String timeB = b.getTime() != null ? b.getTime() : "";
            return timeB.compareTo(timeA); // Descending order (newest first)
        });
    }

    private double computeBalanceUpToDate(List<Transaction> monthTxns, Date selectedDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        double balance = monthlySalary;

        // Sort by date ascending for correct calculation
        List<Transaction> sortedTxns = new ArrayList<>(monthTxns);
        Collections.sort(sortedTxns, (a, b) -> {
            try {
                Date da = sdf.parse(a.getDate());
                Date db = sdf.parse(b.getDate());
                if (da == null || db == null) return 0;
                return da.compareTo(db);
            } catch (Exception e) {
                return 0;
            }
        });

        for (Transaction t : sortedTxns) {
            try {
                Date d = sdf.parse(t.getDate());
                if (d == null) continue;
                if (!d.after(selectedDate)) {
                    if (t.isIncome()) {
                        balance += t.getAmount();
                    } else {
                        balance -= t.getAmount();
                    }
                }
            } catch (ParseException ignored) {
            }
        }
        return balance;
    }

    private void updateDashboardUI(double currentBalance) {
        // Update salary
        tvMonthlySalary.setText(String.format(Locale.getDefault(), "Monthly Salary: ₹%.0f", monthlySalary));

        // Update balance with animation
        actualBalanceText = String.format(Locale.getDefault(), "₹ %.2f", currentBalance);
        if (isBalanceVisible) {
            animateTextChange(tvTotalBalance, actualBalanceText);
        }

        // Update income with animation
        String incomeText = String.format(Locale.getDefault(), "+ ₹%.0f", totalIncome);
        animateTextChange(tvTotalIncome, incomeText);

        // Update expense with animation
        String expenseText = String.format(Locale.getDefault(), "- ₹%.0f", totalExpense);
        animateTextChange(tvTotalExpense, expenseText);

        // Update spending progress
        updateSpendingProgress();
    }

    private void animateTextChange(TextView textView, String newText) {
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(textView, "alpha", 1f, 0f);
        fadeOut.setDuration(100);

        fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                textView.setText(newText);
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(textView, "alpha", 0f, 1f);
                fadeIn.setDuration(150);
                fadeIn.start();
            }
        });

        fadeOut.start();
    }

    private void updateSpendingProgress() {
        if (monthlySalary > 0) {
            int percentage = (int) ((totalMonthExpense / monthlySalary) * 100);
            percentage = Math.min(percentage, 100);

            // Animate progress bar
            ObjectAnimator progressAnimator = ObjectAnimator.ofInt(progressBudget, "progress", progressBudget.getProgress(), percentage);
            progressAnimator.setDuration(500);
            progressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            progressAnimator.start();

            tvBudgetProgress.setText(String.format(Locale.getDefault(),
                    "₹%.0f / ₹%.0f", totalMonthExpense, monthlySalary));

            tvSpendingPercent.setText(String.format(Locale.getDefault(),
                    "%d%% of salary spent", percentage));

            // Update progress bar color based on percentage
            if (percentage >= 90) {
                progressBudget.setProgressDrawable(getResources().getDrawable(R.drawable.progress_budget_danger));
            } else if (percentage >= 70) {
                progressBudget.setProgressDrawable(getResources().getDrawable(R.drawable.progress_budget_warning));
            } else {
                progressBudget.setProgressDrawable(getResources().getDrawable(R.drawable.progress_budget));
            }
        } else {
            progressBudget.setProgress(0);
            tvBudgetProgress.setText("₹0 / ₹0");
            tvSpendingPercent.setText("Set your salary in settings");
        }
    }

    private void updateEmptyState() {
        if (transactionList.isEmpty()) {
            // Show empty state with animation
            if (emptyStateContainer.getVisibility() != View.VISIBLE) {
                emptyStateContainer.setVisibility(View.VISIBLE);
                emptyStateContainer.setAlpha(0f);
                emptyStateContainer.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start();
            }
            rvTransactions.setVisibility(View.GONE);
        } else {
            emptyStateContainer.setVisibility(View.GONE);
            if (rvTransactions.getVisibility() != View.VISIBLE) {
                rvTransactions.setVisibility(View.VISIBLE);
                rvTransactions.setAlpha(0f);
                rvTransactions.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start();
            }
        }
    }

    private void updateCurrentDate() {
        SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        SimpleDateFormat queryFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String displayDate = displayFormat.format(currentCalendar.getTime());
        String selectedDateOnly = queryFormat.format(currentCalendar.getTime());
        currentDate.setText(displayDate);

        int currentMonth = currentCalendar.get(Calendar.MONTH);
        if (spinnerMonth.getSelectedItemPosition() != currentMonth) {
            spinnerMonth.setSelection(currentMonth, false);
        }

        String selectedMonth = spinnerMonth.getSelectedItem().toString();
        isLoading = false; // Reset loading flag
        loadDashboardData(selectedMonth, selectedDateOnly);
    }

    private void changeDate(int offset) {
        currentCalendar.add(Calendar.DAY_OF_MONTH, offset);
        updateCurrentDate();
    }

    private int getMonthIndex(String monthName) {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        return java.util.Arrays.asList(months).indexOf(monthName);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGreeting();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
    }
}