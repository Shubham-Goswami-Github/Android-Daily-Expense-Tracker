package com.cscorner.financetracker;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.card.MaterialCardView;

public class NotesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        Toolbar toolbar = findViewById(R.id.toolbarNotes);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        MaterialCardView cardAddNote = findViewById(R.id.cardAddNote);
        MaterialCardView cardViewNotes = findViewById(R.id.cardViewNotes);

        cardAddNote.setOnClickListener(v -> {
            startActivity(new Intent(NotesActivity.this, AddNoteActivity.class));
        });

        cardViewNotes.setOnClickListener(v -> {
            startActivity(new Intent(NotesActivity.this, ViewNotesActivity.class));
        });
    }
}
