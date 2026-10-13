package com.cscorner.financetracker;
import android.content.Intent;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
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
    private ImageView btnRefreshDashboard; // Refresh button near balance
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
    private ActivityResultLauncher<Intent> aiAssistantLauncher; // for chatbot refresh

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

    // NEW: Request tokens to prevent duplicate/stale concurrent Firestore loads
    // from corrupting shared lists & totals (fixes the "double transactions" bug
    // that happened when the AI chatbot/settings/etc. triggered refresh twice
    // in quick succession).
    private final AtomicInteger dashboardRequestId = new AtomicInteger(0);
    private final AtomicInteger allTransactionsRequestId = new AtomicInteger(0);

    // NEW: Debounce handle for refreshAllData() so multiple rapid refresh
    // triggers (e.g. onActivityResult + onResume firing back-to-back) only
    // result in a single actual refresh.
    private Runnable pendingRefreshRunnable;

    // Chart Fragments
    private ChartDailyFragment chartDailyFragment;
    private ChartMonthlyFragment chartMonthlyFragment;
    private ChartOverallFragment chartOverallFragment;

    // Balance visibility
    private boolean isBalanceVisible = true;
    private String actualBalanceText = "₹ 0.00";

    // Animation flag
    private boolean isAnimating = false;

    // Flag to avoid double-loading data on very first onResume (onCreate already loads)
    private boolean isFirstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdge();
        setContentView(R.layout.activity_dashboard);
        setupWindowInsets();
        animateAiAssistant();
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

    private void animateAiAssistant() {
        FloatingActionButton aiButton =
                findViewById(R.id.btnAiAssistant);

        if (aiButton == null) return;

        aiButton.animate()
                .translationX(7f)
                .setDuration(650)
                .setInterpolator(
                        new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> aiButton.animate()
                        .translationX(-7f)
                        .setDuration(650)
                        .setInterpolator(
                                new android.view.animation.AccelerateDecelerateInterpolator())
                        .withEndAction(() -> aiButton.animate()
                                .translationX(0f)
                                .setDuration(650)
                                .setInterpolator(
                                        new android.view.animation.AccelerateDecelerateInterpolator())
                                .withEndAction(this::animateAiAssistant)
                                .start())
                        .start())
                .start();
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
        btnRefreshDashboard = findViewById(R.id.btnRefreshDashboard);

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

        // AI Assistant (chatbot) launcher -> refresh when it closes
        aiAssistantLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> refreshAllData()
        );
    }

    /**
     * FIXED: Debounced refresh.
     *
     * Previously, closing the AI chatbot (or returning from Settings/AddTransaction)
     * could trigger refreshAllData() twice in very quick succession - once from the
     * ActivityResultLauncher callback and once from onResume(). Because this method
     * force-reset "isLoading = false", both calls were able to start their own
     * concurrent Firestore loads, and both would write into the SAME shared lists
     * (transactionList, monthTransactionList, allTransactionsList) and the SAME
     * running totals (totalIncome, totalExpense, etc.) - causing every transaction
     * to appear twice and every total to be doubled.
     *
     * By debouncing here, multiple refresh calls that happen within a very short
     * window collapse into a single actual refresh. Combined with the request-token
     * guards added in loadDashboardData()/loadTransactions()/loadAllTransactions(),
     * this completely eliminates the duplication, even if a refresh is ever
     * triggered multiple times for any other reason in the future.
     */
    private void refreshAllData() {
        if (pendingRefreshRunnable != null) {
            mainHandler.removeCallbacks(pendingRefreshRunnable);
        }

        pendingRefreshRunnable = () -> {
            isLoading = false; // Reset loading flag to force refresh
            updateCurrentDate();
            loadAllTransactions();
            updateGreeting();
            pendingRefreshRunnable = null;
        };

        mainHandler.postDelayed(pendingRefreshRunnable, 80);
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

        // AI Finance Assistant button
        FloatingActionButton btnAiAssistant = findViewById(R.id.btnAiAssistant);

        if (btnAiAssistant != null) {
            btnAiAssistant.setOnClickListener(v -> {
                animateClick(v);
                Intent intent = new Intent(
                        DashboardActivity.this,
                        AiAssistantActivity.class
                );
                aiAssistantLauncher.launch(intent);
            });
        }

        // Add Important Notes button listener
        LinearLayout btnImportantNotes = findViewById(R.id.btnImportantNotes);
        if (btnImportantNotes != null) {
            btnImportantNotes.setOnClickListener(v -> {
                animateClick(v);
                startActivity(new Intent(DashboardActivity.this, NotesActivity.class));
            });
        }

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

        // Refresh button near balance
        if (btnRefreshDashboard != null) {
            btnRefreshDashboard.setOnClickListener(v -> {
                animateClick(v);
                rotateRefreshIcon(v);
                refreshAllData();
                Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
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

    private void rotateRefreshIcon(View view) {
        ObjectAnimator rotate = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f);
        rotate.setDuration(500);
        rotate.setInterpolator(new AccelerateDecelerateInterpolator());
        rotate.start();
    }

    private void showDatePicker() {
        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth);
                    if (isFutureDate(picked)) {
                        Toast.makeText(this, "Cannot select a future date", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentCalendar.set(Calendar.YEAR, year);
                    currentCalendar.set(Calendar.MONTH, month);
                    currentCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateCurrentDate();
                },
                currentCalendar.get(Calendar.YEAR),
                currentCalendar.get(Calendar.MONTH),
                currentCalendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
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
                return Math.abs(distanceX) > Math.abs(distanceY);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                if (isAnimating) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            animateDateChange(-1);
                        } else {
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
                return Math.abs(distanceX) > Math.abs(distanceY);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                if (isAnimating) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            animateBalanceCardSwipe(-1);
                        } else {
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
                    touchStartX = event.getX();
                    touchStartY = event.getY();
                    isHorizontalSwipe = false;

                    v.setAlpha(0.95f);

                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = Math.abs(event.getX() - touchStartX);
                    float deltaY = Math.abs(event.getY() - touchStartY);

                    if (deltaX > 10 || deltaY > 10) {
                        if (deltaX > deltaY) {
                            isHorizontalSwipe = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        } else {
                            isHorizontalSwipe = false;
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    v.animate().alpha(1f).setDuration(100).start();
                    v.getParent().requestDisallowInterceptTouchEvent(false);

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

            gestureDetector.onTouchEvent(event);

            return true;
        });
    }

    private boolean isFutureDate(Calendar cal) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 23);
        today.set(Calendar.MINUTE, 59);
        today.set(Calendar.SECOND, 59);
        today.set(Calendar.MILLISECOND, 999);

        Calendar target = (Calendar) cal.clone();
        return target.getTimeInMillis() > today.getTimeInMillis();
    }

    private boolean isToday(Calendar cal) {
        Calendar today = Calendar.getInstance();
        return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private void updateNextButtonState() {
        if (nextDateButton == null) return;
        if (isToday(currentCalendar)) {
            nextDateButton.setAlpha(0.3f);
            nextDateButton.setEnabled(false);
        } else {
            nextDateButton.setAlpha(1f);
            nextDateButton.setEnabled(true);
        }
    }

    private void animateBalanceCardSwipe(int direction) {
        if (isAnimating) return;

        if (direction > 0) {
            Calendar temp = (Calendar) currentCalendar.clone();
            temp.add(Calendar.DAY_OF_MONTH, 1);
            if (isFutureDate(temp)) {
                Toast.makeText(this, "Cannot navigate to future dates", Toast.LENGTH_SHORT).show();
                return;
            }
        }

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

        if (direction > 0) {
            Calendar temp = (Calendar) currentCalendar.clone();
            temp.add(Calendar.DAY_OF_MONTH, 1);
            if (isFutureDate(temp)) {
                Toast.makeText(this, "Cannot navigate to future dates", Toast.LENGTH_SHORT).show();
                return;
            }
        }

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
            if (chartDailyFragment != null && chartDailyFragment.isAdded()) {
                chartDailyFragment.updateChart(monthTransactionList, totalIncome, totalExpense, currentCalendar);
            }

            if (chartMonthlyFragment != null && chartMonthlyFragment.isAdded()) {
                chartMonthlyFragment.updateChart(db, user.getEmail());
            }

            if (chartOverallFragment != null && chartOverallFragment.isAdded()) {
                chartOverallFragment.updateChart(allTransactionsList);
            }
        });
    }

    /**
     * FIXED: Uses a request-token guard so that if this method is ever called
     * again before a previous call finishes (e.g. two refreshes fired close
     * together), only the LATEST call's results get committed to
     * allTransactionsList. Older/stale results are silently discarded instead
     * of being appended on top of newer data - this is what previously caused
     * every transaction in the "Overall" chart data to appear twice.
     */
    private void loadAllTransactions() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) return;

        String email = currentUser.getEmail();

        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        final int requestId = allTransactionsRequestId.incrementAndGet();
        final List<Transaction> tempTransactions = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger completedMonths = new AtomicInteger(0);
        final int totalMonths = months.length;

        for (String month : months) {
            db.collection("Users").document(email)
                    .collection("Financial Details")
                    .document("Monthly Expenditure")
                    .collection(month)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        // Ignore results from a superseded (stale) request.
                        if (requestId != allTransactionsRequestId.get()) return;

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            try {
                                Transaction txn = parseTransaction(doc);
                                if (txn != null) {
                                    tempTransactions.add(txn);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing transaction", e);
                            }
                        }

                        finishAllTransactionsLoadIfDone(
                                requestId, completedMonths, totalMonths, tempTransactions);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading month: " + month, e);
                        if (requestId != allTransactionsRequestId.get()) return;

                        finishAllTransactionsLoadIfDone(
                                requestId, completedMonths, totalMonths, tempTransactions);
                    });
        }
    }

    /**
     * Commits the loaded "all transactions" data to the shared list only once
     * every month has responded AND only if this request is still the most
     * recent one (requestId still matches). This prevents a stale/older load
     * from overwriting or duplicating data from a newer load.
     */
    private void finishAllTransactionsLoadIfDone(
            int requestId,
            AtomicInteger completedMonths,
            int totalMonths,
            List<Transaction> tempTransactions
    ) {
        if (completedMonths.incrementAndGet() != totalMonths) return;
        if (requestId != allTransactionsRequestId.get()) return; // Superseded - discard.

        List<Transaction> finalized;
        synchronized (tempTransactions) {
            finalized = new ArrayList<>(tempTransactions);
        }
        sortTransactionsByDate(finalized);

        allTransactionsList.clear();
        allTransactionsList.addAll(finalized);

        if (chartsCard.getVisibility() == View.VISIBLE &&
                chartOverallFragment != null && chartOverallFragment.isAdded()) {
            mainHandler.post(() -> chartOverallFragment.updateChart(allTransactionsList));
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

    /**
     * FIXED: Now generates a request token (dashboardRequestId) for each call,
     * and passes it through to loadTransactions(). Any stale/superseded call
     * will be ignored instead of writing into the shared transaction lists.
     */
    private void loadDashboardData(String month, String selectedDateOnly) {
        if (isLoading) {
            Log.d(TAG, "Already loading, skipping...");
            return;
        }

        isLoading = true;
        final int requestId = dashboardRequestId.incrementAndGet();
        Log.d(TAG, "Loading data for month=" + month + " date=" + selectedDateOnly
                + " requestId=" + requestId);

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
                    // A newer load has already started - abandon this one.
                    if (requestId != dashboardRequestId.get()) return;

                    if (doc.exists()) {

                        Object salaryObj = doc.get("Monthly Salary");

                        if (salaryObj instanceof Number) {
                            monthlySalary = ((Number) salaryObj).doubleValue();
                        } else if (salaryObj instanceof String) {
                            try {
                                monthlySalary = Double.parseDouble((String) salaryObj);
                            } catch (Exception e) {
                                monthlySalary = 0.0;
                            }
                        } else {
                            monthlySalary = 0.0;
                        }

                    } else {
                        monthlySalary = 0.0;
                    }


                    loadTransactions(email, month, selectedDateOnly, requestId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching salary", e);
                    if (requestId != dashboardRequestId.get()) return;
                    monthlySalary = 0.0;
                    loadTransactions(email, month, selectedDateOnly, requestId);
                });
    }

    private void showLoadingState() {
        tvTotalBalance.setAlpha(0.5f);
        tvTotalIncome.setAlpha(0.5f);
        tvTotalExpense.setAlpha(0.5f);
    }

    private void hideLoadingState() {
        ObjectAnimator.ofFloat(tvTotalBalance, "alpha", 0.5f, 1f).setDuration(200).start();
        ObjectAnimator.ofFloat(tvTotalIncome, "alpha", 0.5f, 1f).setDuration(200).start();
        ObjectAnimator.ofFloat(tvTotalExpense, "alpha", 0.5f, 1f).setDuration(200).start();
    }

    /**
     * FIXED: This is the core of the duplication bug fix.
     *
     * Previously, this method immediately cleared the shared lists/totals at
     * the start, then populated them directly inside the async Firestore
     * callback. If two calls overlapped (which was happening due to the
     * refresh race described above), BOTH callbacks would append into the
     * SAME shared lists/totals without re-clearing right before their own
     * writes - resulting in every transaction appearing twice and every total
     * being doubled.
     *
     * Now, all parsing/summing happens into LOCAL variables first. The shared
     * instance fields (transactionList, monthTransactionList, totalIncome,
     * totalExpense, totalMonthIncome, totalMonthExpense) are only overwritten
     * once, right before the UI update - and only if this call's requestId is
     * still the latest (i.e. no newer refresh/load has started in the
     * meantime). Stale calls are discarded completely, guaranteeing accurate,
     * non-duplicated, real-time data every time.
     */
    private void loadTransactions(String email, String month, String selectedDateOnly, int requestId) {
        if (requestId != dashboardRequestId.get()) {
            // A newer request has already taken over - don't touch anything.
            return;
        }

        db.collection("Users").document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(month)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (requestId != dashboardRequestId.get()) return; // stale - ignore entirely

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

                    // Use fresh local containers so concurrent/overlapping calls
                    // can never interfere with each other's data.
                    List<Transaction> newDayList = new ArrayList<>();
                    List<Transaction> newMonthList = new ArrayList<>();
                    double dayIncome = 0;
                    double dayExpense = 0;
                    double monthIncome = 0;
                    double monthExpense = 0;

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Transaction txn = parseTransaction(doc);
                        if (txn == null) continue;

                        // Add to month list
                        newMonthList.add(txn);

                        // Track monthly totals
                        if (txn.isIncome()) {
                            monthIncome += txn.getAmount();
                        } else {
                            monthExpense += txn.getAmount();
                        }

                        // Check if transaction is for selected date
                        try {
                            Date txnDate = sdf.parse(txn.getDate());
                            if (txnDate != null && sdf.format(txnDate).equals(selectedDateOnly)) {
                                newDayList.add(txn);
                                if (txn.isIncome()) {
                                    dayIncome += txn.getAmount();
                                } else {
                                    dayExpense += txn.getAmount();
                                }
                            }
                        } catch (ParseException ignored) {
                        }
                    }

                    if (requestId != dashboardRequestId.get()) return; // became stale mid-processing

                    // Sort transactions by time (newest first)
                    sortTransactionsByTime(newDayList);
                    sortTransactionsByDate(newMonthList);

                    // Compute balance
                    double balance = computeBalanceUpToDate(newMonthList, selectedDate);

                    final double finalDayIncome = dayIncome;
                    final double finalDayExpense = dayExpense;
                    final double finalMonthIncome = monthIncome;
                    final double finalMonthExpense = monthExpense;

                    // Update UI on main thread
                    mainHandler.post(() -> {
                        if (requestId != dashboardRequestId.get()) return; // stale by the time we commit

                        // Commit to shared state only now, all at once.
                        transactionList.clear();
                        transactionList.addAll(newDayList);

                        monthTransactionList.clear();
                        monthTransactionList.addAll(newMonthList);

                        totalIncome = finalDayIncome;
                        totalExpense = finalDayExpense;
                        totalMonthIncome = finalMonthIncome;
                        totalMonthExpense = finalMonthExpense;

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
                    if (requestId != dashboardRequestId.get()) return;
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

        updateNextButtonState();

        int currentMonth = currentCalendar.get(Calendar.MONTH);
        if (spinnerMonth.getSelectedItemPosition() != currentMonth) {
            spinnerMonth.setSelection(currentMonth, false);
        }

        String selectedMonth = spinnerMonth.getSelectedItem().toString();
        isLoading = false; // Reset loading flag
        loadDashboardData(selectedMonth, selectedDateOnly);
    }

    private void changeDate(int offset) {
        Calendar temp = (Calendar) currentCalendar.clone();
        temp.add(Calendar.DAY_OF_MONTH, offset);
        if (isFutureDate(temp)) {
            Toast.makeText(this, "Cannot navigate to future dates", Toast.LENGTH_SHORT).show();
            return;
        }
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

        if (isFirstResume) {
            isFirstResume = false;
        } else {
            refreshAllData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
    }
}