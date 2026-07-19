package com.cscorner.financetracker;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.cscorner.financetracker.model.Note;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddNoteActivity extends AppCompatActivity {

    private Spinner spinnerNoteType;
    private EditText etNoteSummary;
    private CheckBox cbRemindMe;
    private TextView tvSelectedDate;
    private Button btnSubmitNote;

    private Calendar reminderCalendar;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_note);

        Toolbar toolbar = findViewById(R.id.toolbarAddNote);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        reminderCalendar = Calendar.getInstance();

        spinnerNoteType = findViewById(R.id.spinnerNoteType);
        etNoteSummary = findViewById(R.id.etNoteSummary);
        cbRemindMe = findViewById(R.id.cbRemindMe);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        btnSubmitNote = findViewById(R.id.btnSubmitNote);

        String[] noteTypes = {"Money lending", "General Reminder"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, noteTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNoteType.setAdapter(adapter);

        cbRemindMe.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                tvSelectedDate.setVisibility(View.VISIBLE);
                showDatePicker();
            } else {
                tvSelectedDate.setVisibility(View.GONE);
            }
        });

        tvSelectedDate.setOnClickListener(v -> showDatePicker());

        btnSubmitNote.setOnClickListener(v -> saveNote());
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            reminderCalendar.set(Calendar.YEAR, year);
            reminderCalendar.set(Calendar.MONTH, month);
            reminderCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            reminderCalendar.set(Calendar.HOUR_OF_DAY, 9); // Default to 9 AM
            reminderCalendar.set(Calendar.MINUTE, 0);
            reminderCalendar.set(Calendar.SECOND, 0);

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            tvSelectedDate.setText(sdf.format(reminderCalendar.getTime()));
        }, reminderCalendar.get(Calendar.YEAR), reminderCalendar.get(Calendar.MONTH), reminderCalendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveNote() {
        String type = spinnerNoteType.getSelectedItem().toString();
        String summary = etNoteSummary.getText().toString().trim();
        boolean isReminderEnabled = cbRemindMe.isChecked();

        if (summary.isEmpty()) {
            Toast.makeText(this, "Please enter note summary", Toast.LENGTH_SHORT).show();
            return;
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
        String noteId = db.collection("Users").document(email).collection("Personal Notes").document().getId();

        Note note = new Note(
                noteId,
                email,
                type,
                summary,
                isReminderEnabled ? reminderCalendar.getTimeInMillis() : 0,
                isReminderEnabled,
                System.currentTimeMillis()
        );

        db.collection("Users").document(email).collection("Personal Notes").document(noteId)
                .set(note)
                .addOnSuccessListener(aVoid -> {
                    if (isReminderEnabled) {
                        scheduleNotification(note);
                    }
                    Toast.makeText(AddNoteActivity.this, "Note saved successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(AddNoteActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void scheduleNotification(Note note) {
        Intent intent = new Intent(this, NoteReminderReceiver.class);
        intent.putExtra("title", "Reminder: " + note.getType());
        intent.putExtra("message", note.getSummary());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, note.getId().hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
                }
            } catch (SecurityException e) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
            }
        }
    }
}
