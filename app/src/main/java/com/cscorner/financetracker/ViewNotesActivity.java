package com.cscorner.financetracker;

import android.animation.ObjectAnimator;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cscorner.financetracker.adapter.NoteAdapter;
import com.cscorner.financetracker.model.Note;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class ViewNotesActivity extends AppCompatActivity {

    private RecyclerView rvNotes;
    private NoteAdapter adapter;
    private List<Note> noteList;
    private LinearLayout emptyNotesState;
    private ProgressBar progressLoadingNotes;
    private TextView tvNotesCount;

    private ImageView btnBackViewNotes;
    private ImageView btnRefreshNotes;
    private FloatingActionButton fabAddNote;
    private android.widget.Button btnAddFirstNote;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // Avoid double-loading right after onCreate's initial load.
    private boolean isFirstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, 0);
        setContentView(R.layout.activity_view_notes);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        initializeViews();
        setupRecyclerView();
        setupClickListeners();

        loadNotes();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private void initializeViews() {
        btnBackViewNotes = findViewById(R.id.btnBackViewNotes);
        btnRefreshNotes = findViewById(R.id.btnRefreshNotes);
        rvNotes = findViewById(R.id.rvNotes);
        emptyNotesState = findViewById(R.id.emptyNotesState);
        progressLoadingNotes = findViewById(R.id.progressLoadingNotes);
        tvNotesCount = findViewById(R.id.tvNotesCount);
        fabAddNote = findViewById(R.id.fabAddNote);
        btnAddFirstNote = findViewById(R.id.btnAddFirstNote);
    }

    private void setupRecyclerView() {
        noteList = new ArrayList<>();
        adapter = new NoteAdapter(noteList, new NoteAdapter.OnNoteListener() {
            @Override
            public void onEditClick(Note note, int position) {
                Intent intent = new Intent(ViewNotesActivity.this, AddNoteActivity.class);
                intent.putExtra("note_id", note.getId());
                intent.putExtra("note_type", note.getType());
                intent.putExtra("note_summary", note.getSummary());
                intent.putExtra("reminder_date", note.getReminderDate());
                intent.putExtra("reminder_enabled", note.isReminderEnabled());
                intent.putExtra("created_at", note.getCreatedAt());
                startActivity(intent);
            }

            @Override
            public void onDeleteClick(Note note, int position) {
                deleteNote(note, position);
            }
        });
        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        rvNotes.setAdapter(adapter);
        rvNotes.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        rvNotes.setLayoutAnimation(
                AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_notes));
    }

    private void deleteNote(Note note, int position) {
        if (auth.getCurrentUser() == null || auth.getCurrentUser().getEmail() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = auth.getCurrentUser().getEmail();
        String noteId = note.getId();

        if (noteId == null || noteId.isEmpty()) {
            Toast.makeText(this, "Note ID missing", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("Users").document(email).collection("Personal Notes").document(noteId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    cancelReminderAlarm(note);

                    if (position >= 0 && position < noteList.size()) {
                        noteList.remove(position);
                        adapter.notifyItemRemoved(position);
                    } else {
                        loadNotes();
                    }

                    updateNotesCount();
                    if (noteList.isEmpty()) {
                        showEmptyState();
                    }

                    Toast.makeText(ViewNotesActivity.this, "Note deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ViewNotesActivity.this, "Failed to delete note: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void cancelReminderAlarm(Note note) {
        Intent intent = new Intent(this, NoteReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, note.getId().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void setupClickListeners() {
        btnBackViewNotes.setOnClickListener(v -> {
            bounceView(v);
            finish();
        });

        btnRefreshNotes.setOnClickListener(v -> {
            bounceView(v);
            rotateRefreshIcon(v);
            loadNotes();
        });

        fabAddNote.setOnClickListener(v -> {
            bounceView(v);
            startActivity(new Intent(ViewNotesActivity.this, AddNoteActivity.class));
        });

        if (btnAddFirstNote != null) {
            btnAddFirstNote.setOnClickListener(v -> {
                bounceView(v);
                startActivity(new Intent(ViewNotesActivity.this, AddNoteActivity.class));
            });
        }

        FloatingActionButton btnAiAssistant = findViewById(R.id.btnAiAssistant);
        if (btnAiAssistant != null) {
            btnAiAssistant.setOnClickListener(v -> {
                bounceView(v);
                startActivity(new Intent(ViewNotesActivity.this, AiAssistantActivity.class));
            });
        }
    }

    private void loadNotes() {
        if (auth.getCurrentUser() == null || auth.getCurrentUser().getEmail() == null) return;

        String email = auth.getCurrentUser().getEmail();

        showLoadingState(true);

        db.collection("Users").document(email).collection("Personal Notes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    noteList.clear();
                    noteList.addAll(queryDocumentSnapshots.toObjects(Note.class));
                    adapter.notifyDataSetChanged();

                    showLoadingState(false);
                    updateNotesCount();

                    if (noteList.isEmpty()) {
                        showEmptyState();
                    } else {
                        showNotesList();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoadingState(false);
                    Toast.makeText(ViewNotesActivity.this,
                            "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateNotesCount() {
        int count = noteList.size();
        if (count == 0) {
            tvNotesCount.setText("No notes saved yet");
        } else if (count == 1) {
            tvNotesCount.setText("1 note saved");
        } else {
            tvNotesCount.setText(count + " notes saved");
        }
    }

    private void showLoadingState(boolean loading) {
        progressLoadingNotes.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            rvNotes.setVisibility(View.GONE);
            emptyNotesState.setVisibility(View.GONE);
        }
    }

    private void showEmptyState() {
        rvNotes.setVisibility(View.GONE);

        if (emptyNotesState.getVisibility() != View.VISIBLE) {
            emptyNotesState.setVisibility(View.VISIBLE);
            emptyNotesState.setAlpha(0f);
            emptyNotesState.setScaleX(0.92f);
            emptyNotesState.setScaleY(0.92f);
            emptyNotesState.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start();
        }
    }

    private void showNotesList() {
        emptyNotesState.setVisibility(View.GONE);
        rvNotes.setVisibility(View.VISIBLE);
        rvNotes.scheduleLayoutAnimation();
    }

    private void bounceView(View view) {
        view.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(90)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
    }

    private void rotateRefreshIcon(View view) {
        ObjectAnimator rotate = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f);
        rotate.setDuration(500);
        rotate.setInterpolator(new AccelerateDecelerateInterpolator());
        rotate.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh notes whenever returning to this screen (e.g. after adding a
        // new note), but skip the very first onResume right after onCreate
        // since loadNotes() has already run there.
        if (isFirstResume) {
            isFirstResume = false;
        } else {
            loadNotes();
        }
    }
}
