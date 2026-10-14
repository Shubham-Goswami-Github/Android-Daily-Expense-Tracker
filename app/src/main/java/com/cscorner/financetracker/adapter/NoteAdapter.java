package com.cscorner.financetracker.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
    private OnNoteListener onNoteListener;

    public interface OnNoteListener {
        void onEditClick(Note note, int position);
        void onDeleteClick(Note note, int position);
    }

    public NoteAdapter(List<Note> noteList, OnNoteListener onNoteListener) {
        this.noteList = noteList;
        this.onNoteListener = onNoteListener;
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

        String rawType = note.getType();
        String displayType;
        if (rawType != null) {
            if (rawType.equalsIgnoreCase("Money lending")) {
                displayType = "💰 Money Lending";
            } else if (rawType.equalsIgnoreCase("Money borrowed")) {
                displayType = "💸 Money Borrowed";
            } else if (rawType.equalsIgnoreCase("General Reminder")) {
                displayType = "🔔 General Reminder";
            } else {
                displayType = rawType;
            }
        } else {
            displayType = "Note";
        }
        holder.tvNoteType.setText(displayType);
        holder.tvNoteSummary.setText(note.getSummary());

        SimpleDateFormat timeStampFormat = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
        holder.tvNoteDate.setText(timeStampFormat.format(new Date(note.getCreatedAt())));

        if (note.isReminderEnabled() && note.getReminderDate() > 0) {
            holder.reminderContainer.setVisibility(View.VISIBLE);
            holder.tvReminderDate.setText("Remind on: " + timeStampFormat.format(new Date(note.getReminderDate())));
        } else {
            holder.reminderContainer.setVisibility(View.GONE);
        }

        holder.btnEdit.setOnClickListener(v -> {
            if (onNoteListener != null) {
                onNoteListener.onEditClick(note, holder.getAdapterPosition());
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (onNoteListener != null) {
                onNoteListener.onDeleteClick(note, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvNoteType, tvNoteDate, tvNoteSummary, tvReminderDate;
        LinearLayout reminderContainer;
        Button btnEdit, btnDelete;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNoteType = itemView.findViewById(R.id.tvNoteType);
            tvNoteDate = itemView.findViewById(R.id.tvNoteDate);
            tvNoteSummary = itemView.findViewById(R.id.tvNoteSummary);
            tvReminderDate = itemView.findViewById(R.id.tvReminderDate);
            reminderContainer = itemView.findViewById(R.id.reminderContainer);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
