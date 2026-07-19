package com.cscorner.financetracker;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cscorner.financetracker.adapter.NoteAdapter;
import com.cscorner.financetracker.model.Note;
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

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_notes);

        Toolbar toolbar = findViewById(R.id.toolbarViewNotes);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        rvNotes = findViewById(R.id.rvNotes);
        emptyNotesState = findViewById(R.id.emptyNotesState);

        noteList = new ArrayList<>();
        adapter = new NoteAdapter(noteList);
        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        rvNotes.setAdapter(adapter);

        loadNotes();
    }

    private void loadNotes() {
        if (auth.getCurrentUser() == null || auth.getCurrentUser().getEmail() == null) return;

        String email = auth.getCurrentUser().getEmail();

        db.collection("Users").document(email).collection("Personal Notes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    noteList.clear();
                    noteList.addAll(queryDocumentSnapshots.toObjects(Note.class));
                    adapter.notifyDataSetChanged();

                    if (noteList.isEmpty()) {
                        emptyNotesState.setVisibility(View.VISIBLE);
                        rvNotes.setVisibility(View.GONE);
                    } else {
                        emptyNotesState.setVisibility(View.GONE);
                        rvNotes.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(ViewNotesActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
