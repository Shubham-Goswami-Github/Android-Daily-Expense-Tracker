package com.cscorner.financetracker.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cscorner.financetracker.R;
import com.cscorner.financetracker.model.Note;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private List<Note> noteList;

    public NoteAdapter(List<Note> noteList) {
        this.noteList = noteList;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = noteList.get(position);
        holder.tvNoteType.setText(note.getType());
        holder.tvNoteSummary.setText(note.getSummary());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvNoteDate.setText(sdf.format(new Date(note.getCreatedAt())));

        if (note.isReminderEnabled()) {
            holder.reminderContainer.setVisibility(View.VISIBLE);
            holder.tvReminderDate.setText("Remind on: " + sdf.format(new Date(note.getReminderDate())));
        } else {
            holder.reminderContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvNoteType, tvNoteDate, tvNoteSummary, tvReminderDate;
        LinearLayout reminderContainer;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNoteType = itemView.findViewById(R.id.tvNoteType);
            tvNoteDate = itemView.findViewById(R.id.tvNoteDate);
            tvNoteSummary = itemView.findViewById(R.id.tvNoteSummary);
            tvReminderDate = itemView.findViewById(R.id.tvReminderDate);
            reminderContainer = itemView.findViewById(R.id.reminderContainer);
        }
    }
}
