package com.cscorner.financetracker;

import android.animation.ObjectAnimator;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cscorner.financetracker.model.Note;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddNoteActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_NOTIFICATION = 101;

    private ImageView btnBackNote;

    private MaterialCardView cardNoteType, cardNoteDetails, cardReminder;
    private LinearLayout cardTypeMoneyLending, cardTypeMoneyBorrowed, cardTypeGeneral;
    private LinearLayout reminderDetailsContainer;
    private LinearLayout dateChipContainer, timeChipContainer;

    private EditText etNoteSummary;
    private TextView tvCharCount;
    private SwitchCompat switchRemindMe;
    private TextView tvSelectedDate, tvSelectedTime;
    private Button btnSubmitNote;

    private String selectedNoteType = "Money lending";
    private boolean isSaving = false;

    private boolean isEditMode = false;
    private String editNoteId = null;
    private long editCreatedAt = 0;

    private Calendar reminderCalendar;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, 0);
        setContentView(R.layout.activity_add_note);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        reminderCalendar = Calendar.getInstance();
        Calendar now = Calendar.getInstance();
        reminderCalendar.set(Calendar.HOUR_OF_DAY, 9);
        reminderCalendar.set(Calendar.MINUTE, 0);
        reminderCalendar.set(Calendar.SECOND, 0);
        if (reminderCalendar.before(now)) {
            reminderCalendar.setTimeInMillis(now.getTimeInMillis() + (60 * 60 * 1000));
        }

        createNotificationChannel();

        initializeViews();
        setupNoteTypeSelection();
        setupCharCounter();
        setupReminderToggle();
        setupDateTimeChips();
        setupSaveButton();

        checkForEditMode();

        playEntranceAnimations();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    // -------------------- SETUP --------------------

    private void initializeViews() {
        btnBackNote = findViewById(R.id.btnBackNote);
        btnBackNote.setOnClickListener(v -> {
            bounceView(v);
            finish();
        });

        cardNoteType = findViewById(R.id.cardNoteType);
        cardNoteDetails = findViewById(R.id.cardNoteDetails);
        cardReminder = findViewById(R.id.cardReminder);

        cardTypeMoneyLending = findViewById(R.id.cardTypeMoneyLending);
        cardTypeMoneyBorrowed = findViewById(R.id.cardTypeMoneyBorrowed);
        cardTypeGeneral = findViewById(R.id.cardTypeGeneral);

        etNoteSummary = findViewById(R.id.etNoteSummary);
        tvCharCount = findViewById(R.id.tvCharCount);

        switchRemindMe = findViewById(R.id.switchRemindMe);
        reminderDetailsContainer = findViewById(R.id.reminderDetailsContainer);
        dateChipContainer = findViewById(R.id.dateChipContainer);
        timeChipContainer = findViewById(R.id.timeChipContainer);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);

        btnSubmitNote = findViewById(R.id.btnSubmitNote);

        SimpleDateFormat sdfDate = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        tvSelectedDate.setText(sdfDate.format(reminderCalendar.getTime()));

        updateSelectedTimeLabel();
    }

    private void setupNoteTypeSelection() {
        // Default selection: Money Lending
        cardTypeMoneyLending.setOnClickListener(v -> {
            bounceView(v);
            selectedNoteType = "Money lending";
            selectTypeCard(cardTypeMoneyLending, cardTypeMoneyBorrowed, cardTypeGeneral);
        });

        cardTypeMoneyBorrowed.setOnClickListener(v -> {
            bounceView(v);
            selectedNoteType = "Money borrowed";
            selectTypeCard(cardTypeMoneyBorrowed, cardTypeMoneyLending, cardTypeGeneral);
        });

        cardTypeGeneral.setOnClickListener(v -> {
            bounceView(v);
            selectedNoteType = "General Reminder";
            selectTypeCard(cardTypeGeneral, cardTypeMoneyLending, cardTypeMoneyBorrowed);
        });
    }

    private void selectTypeCard(LinearLayout selected, LinearLayout... unselected) {
        selected.setBackgroundResource(R.drawable.bg_type_selected);
        TextView selectedLabel = (TextView) selected.getChildAt(1);
        selectedLabel.setTextColor(getResources().getColor(R.color.primary_blue));

        for (LinearLayout card : unselected) {
            card.setBackgroundResource(R.drawable.bg_type_unselected);
            TextView unselectedLabel = (TextView) card.getChildAt(1);
            unselectedLabel.setTextColor(Color.parseColor("#64748B"));
        }

        // Small pop animation for the newly selected card
        selected.animate()
                .scaleX(1.04f).scaleY(1.04f)
                .setDuration(100)
                .withEndAction(() -> selected.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
    }

    private void checkForEditMode() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("note_id")) {
            isEditMode = true;
            editNoteId = intent.getStringExtra("note_id");
            editCreatedAt = intent.getLongExtra("created_at", System.currentTimeMillis());

            String type = intent.getStringExtra("note_type");
            String summary = intent.getStringExtra("note_summary");
            long reminderDate = intent.getLongExtra("reminder_date", 0);
            boolean reminderEnabled = intent.getBooleanExtra("reminder_enabled", false);

            if (type != null) {
                selectedNoteType = type;
                if (type.equalsIgnoreCase("Money lending")) {
                    selectTypeCard(cardTypeMoneyLending, cardTypeMoneyBorrowed, cardTypeGeneral);
                } else if (type.equalsIgnoreCase("Money borrowed")) {
                    selectTypeCard(cardTypeMoneyBorrowed, cardTypeMoneyLending, cardTypeGeneral);
                } else if (type.equalsIgnoreCase("General Reminder")) {
                    selectTypeCard(cardTypeGeneral, cardTypeMoneyLending, cardTypeMoneyBorrowed);
                }
            }

            if (summary != null) {
                etNoteSummary.setText(summary);
                etNoteSummary.setSelection(summary.length());
            }

            if (reminderEnabled && reminderDate > 0) {
                reminderCalendar.setTimeInMillis(reminderDate);
                switchRemindMe.setChecked(true);
                reminderDetailsContainer.setVisibility(View.VISIBLE);
                reminderDetailsContainer.setAlpha(1f);
                reminderDetailsContainer.setTranslationY(0f);
            }

            SimpleDateFormat sdfDate = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            tvSelectedDate.setText(sdfDate.format(reminderCalendar.getTime()));
            updateSelectedTimeLabel();

            btnSubmitNote.setText("💾 Update Note");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "note_reminders",
                    "Note Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for scheduled note reminders");
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_NOTIFICATION
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_NOTIFICATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission is required for reminders", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupCharCounter() {
        etNoteSummary.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s.length();
                tvCharCount.setText(length + " / 500");
                if (length >= 480) {
                    tvCharCount.setTextColor(Color.parseColor("#EF4444"));
                } else {
                    tvCharCount.setTextColor(getResources().getColor(R.color.text_secondary));
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void setupReminderToggle() {
        switchRemindMe.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                checkAndRequestNotificationPermission();
                expandReminderDetails();
                // Give the expand animation a moment before prompting for a date.
                mainHandler.postDelayed(this::showDatePicker, 260);
            } else {
                collapseReminderDetails();
            }
        });
    }

    private void expandReminderDetails() {
        reminderDetailsContainer.setVisibility(View.VISIBLE);
        reminderDetailsContainer.setAlpha(0f);
        reminderDetailsContainer.setTranslationY(-12f);
        reminderDetailsContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void collapseReminderDetails() {
        reminderDetailsContainer.animate()
                .alpha(0f)
                .translationY(-12f)
                .setDuration(180)
                .withEndAction(() -> reminderDetailsContainer.setVisibility(View.GONE))
                .start();
    }

    private void setupDateTimeChips() {
        dateChipContainer.setOnClickListener(v -> {
            bounceView(v);
            showDatePicker();
        });
        timeChipContainer.setOnClickListener(v -> {
            bounceView(v);
            showTimePicker();
        });
    }

    private void setupSaveButton() {
        btnSubmitNote.setOnClickListener(v -> {
            if (isSaving) return;
            bounceView(v);
            saveNote();
        });
    }

    // -------------------- ENTRANCE ANIMATION --------------------

    private void playEntranceAnimations() {
        animateCardIn(cardNoteType, 80);
        animateCardIn(cardNoteDetails, 180);
        animateCardIn(cardReminder, 280);
        animateCardIn(btnSubmitNote, 380);
    }

    private void animateCardIn(View view, long delay) {
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(320)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** Simple press-feedback bounce animation for tappable views. */
    private void bounceView(View view) {
        view.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(90)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(
                view, "translationX",
                0f, -18f, 18f, -14f, 14f, -8f, 8f, 0f
        );
        shake.setDuration(420);
        shake.setInterpolator(new AccelerateDecelerateInterpolator());
        shake.start();
    }

    // -------------------- DATE & TIME PICKERS --------------------

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            reminderCalendar.set(Calendar.YEAR, year);
            reminderCalendar.set(Calendar.MONTH, month);
            reminderCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            tvSelectedDate.setText(sdf.format(reminderCalendar.getTime()));
            pulseView(tvSelectedDate);
        }, reminderCalendar.get(Calendar.YEAR),
                reminderCalendar.get(Calendar.MONTH),
                reminderCalendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            reminderCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            reminderCalendar.set(Calendar.MINUTE, minute);
            reminderCalendar.set(Calendar.SECOND, 0);

            updateSelectedTimeLabel();
            pulseView(tvSelectedTime);
        }, reminderCalendar.get(Calendar.HOUR_OF_DAY),
                reminderCalendar.get(Calendar.MINUTE),
                false).show();
    }

    private void updateSelectedTimeLabel() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        tvSelectedTime.setText(sdf.format(reminderCalendar.getTime()));
    }

    private void pulseView(View view) {
        view.animate()
                .scaleX(1.08f).scaleY(1.08f)
                .setDuration(100)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
    }

    // -------------------- SAVE / UPDATE NOTE --------------------

    private void saveNote() {
        String type = selectedNoteType;
        String summary = etNoteSummary.getText().toString().trim();
        boolean isReminderEnabled = switchRemindMe.isChecked();

        if (summary.isEmpty()) {
            Toast.makeText(this, "Please enter note summary", Toast.LENGTH_SHORT).show();
            shakeView(cardNoteDetails);
            etNoteSummary.requestFocus();
            return;
        }

        if (isReminderEnabled) {
            if (reminderCalendar.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, "Please select a future date and time for the reminder", Toast.LENGTH_LONG).show();
                shakeView(cardReminder);
                return;
            }
        }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = auth.getCurrentUser().getEmail();
        if (email == null) {
            Toast.makeText(this, "User email not found", Toast.LENGTH_SHORT).show();
            return;
        }

        setSavingState(true);

        String noteId;
        long createdAt;
        if (isEditMode && editNoteId != null) {
            noteId = editNoteId;
            createdAt = editCreatedAt;
        } else {
            noteId = db.collection("Users").document(email)
                    .collection("Personal Notes").document().getId();
            createdAt = System.currentTimeMillis();
        }

        Note note = new Note(
                noteId,
                email,
                type,
                summary,
                isReminderEnabled ? reminderCalendar.getTimeInMillis() : 0,
                isReminderEnabled,
                createdAt
        );

        db.collection("Users").document(email).collection("Personal Notes").document(noteId)
                .set(note)
                .addOnSuccessListener(aVoid -> {
                    cancelReminderAlarm(noteId);
                    if (isReminderEnabled) {
                        scheduleNotification(note);
                    }
                    showSuccessAndExit(isEditMode ? "Note updated successfully" : "Note saved successfully");
                })
                .addOnFailureListener(e -> {
                    setSavingState(false);
                    Toast.makeText(AddNoteActivity.this,
                            "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void cancelReminderAlarm(String noteId) {
        Intent intent = new Intent(this, NoteReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, noteId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void setSavingState(boolean saving) {
        isSaving = saving;
        btnSubmitNote.setEnabled(!saving);
        btnSubmitNote.setAlpha(saving ? 0.7f : 1f);
        if (isEditMode) {
            btnSubmitNote.setText(saving ? "Updating..." : "💾  Update Note");
        } else {
            btnSubmitNote.setText(saving ? "Saving..." : "💾  Save Note");
        }
    }

    private void showSuccessAndExit(String successMessage) {
        if (isEditMode) {
            btnSubmitNote.setText("✅  Updated!");
        } else {
            btnSubmitNote.setText("✅  Saved!");
        }
        btnSubmitNote.setBackgroundResource(R.drawable.bg_save_button_gradient);

        Toast.makeText(AddNoteActivity.this, successMessage, Toast.LENGTH_SHORT).show();

        mainHandler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, 500);
    }

    private void scheduleNotification(Note note) {
        Intent intent = new Intent(this, NoteReminderReceiver.class);
        intent.putExtra("title", "Reminder: " + note.getType());
        intent.putExtra("message", note.getSummary());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, note.getId().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
                }
            } catch (SecurityException e) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
                }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
