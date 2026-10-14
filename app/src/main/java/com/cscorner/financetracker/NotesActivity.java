package com.cscorner.financetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class NotesActivity extends AppCompatActivity {

    private MaterialCardView cardAddNote;
    private MaterialCardView cardViewNotes;
    private View footerHint;
    private View btnBackNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, 0);
        setContentView(R.layout.activity_notes);

        initializeViews();
        setupClickListeners();
        playEntranceAnimations();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private void initializeViews() {
        btnBackNotes = findViewById(R.id.btnBackNotes);
        cardAddNote = findViewById(R.id.cardAddNote);
        cardViewNotes = findViewById(R.id.cardViewNotes);
        footerHint = findViewById(R.id.footerHint);
    }

    private void setupClickListeners() {
        btnBackNotes.setOnClickListener(v -> {
            bounceView(v);
            finish();
        });

        cardAddNote.setOnClickListener(v -> {
            bounceView(v, () ->
                    startActivity(new Intent(NotesActivity.this, AddNoteActivity.class)));
        });

        cardViewNotes.setOnClickListener(v -> {
            bounceView(v, () ->
                    startActivity(new Intent(NotesActivity.this, ViewNotesActivity.class)));
        });

        FloatingActionButton btnAiAssistant = findViewById(R.id.btnAiAssistant);
        if (btnAiAssistant != null) {
            btnAiAssistant.setOnClickListener(v -> {
                bounceView(v);
                startActivity(new Intent(NotesActivity.this, AiAssistantActivity.class));
            });
        }
    }

    // -------------------- ANIMATIONS --------------------

    private void playEntranceAnimations() {
        animateCardIn(cardAddNote, 80);
        animateCardIn(cardViewNotes, 180);
        animateCardIn(footerHint, 320);
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

    /** Press-feedback bounce animation for the back button. */
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

    /** Press-feedback bounce animation followed by a navigation action. */
    private void bounceView(View view, Runnable onComplete) {
        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(90)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .withEndAction(onComplete)
                        .start())
                .start();
    }
}