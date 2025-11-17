package com.cscorner.financetracker.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cscorner.financetracker.R;
import com.cscorner.financetracker.model.Transaction;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<Transaction> transactionList;
    private FirebaseFirestore db;

    public TransactionAdapter(List<Transaction> transactionList) {
        this.transactionList = transactionList;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {

        Transaction transaction = transactionList.get(position);

        String formattedTime = formatTimeTo12Hour(transaction.getTime());

        holder.tvDateTime.setText(transaction.getDate() + " " + formattedTime);
        holder.tvNote.setText(transaction.getNote());

        if (transaction.isIncome()) {
            holder.tvAmount.setText("+ ₹" + transaction.getAmount());
            holder.tvAmount.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.green));
        } else {
            holder.tvAmount.setText("- ₹" + transaction.getAmount());
            holder.tvAmount.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.red));
        }

        holder.btnDelete.setOnClickListener(v -> {
            deleteTransaction(transaction, holder.getAdapterPosition(), holder.itemView.getContext());
        });
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    private String formatTimeTo12Hour(String time24) {
        try {
            SimpleDateFormat sdfInput;
            if (time24.matches("\\d{2}:\\d{2}:\\d{2}")) {
                sdfInput = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            } else if (time24.matches("\\d{2}:\\d{2}")) {
                sdfInput = new SimpleDateFormat("HH:mm", Locale.getDefault());
            } else {
                return time24;
            }

            SimpleDateFormat sdfOutput = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            Date dateObj = sdfInput.parse(time24);
            return sdfOutput.format(dateObj);
        } catch (Exception e) {
            return time24;
        }
    }

    // 🔥 FIXED FIREBASE DELETE FUNCTION
    private void deleteTransaction(Transaction transaction, int position, Context context) {

        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (email == null) {
            Toast.makeText(context, "User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        String transactionId = transaction.getTransactionId();
        if (transactionId == null || transactionId.isEmpty()) {
            Toast.makeText(context, "Transaction ID missing!", Toast.LENGTH_SHORT).show();
            return;
        }

        String monthName = convertDateToMonthName(transaction.getDate()); // FIXED

        db.collection("Users")
                .document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(monthName)
                .document(transactionId)
                .delete()
                .addOnSuccessListener(aVoid -> {

                    transactionList.remove(position);
                    notifyItemRemoved(position);

                    recalculateTotals(email, monthName, context);

                    Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show();
                });
    }

    // Convert 17-11-2025 → November
    private String convertDateToMonthName(String date) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            Date d = input.parse(date);

            SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.getDefault());
            return monthFormat.format(d);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    // 🔥 FIXED TOTAL RECALCULATION
    private void recalculateTotals(String email, String monthName, Context context) {

        db.collection("Users")
                .document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(monthName)
                .get()
                .addOnSuccessListener(query -> {

                    double totalIncome = 0, totalExpense = 0;

                    for (DocumentSnapshot doc : query.getDocuments()) {

                        if (doc.getId().equals("totals")) continue;

                        Transaction t = doc.toObject(Transaction.class);
                        if (t == null) continue;

                        if (t.isIncome()) totalIncome += t.getAmount();
                        else totalExpense += t.getAmount();
                    }

                    double balance = totalIncome - totalExpense;

                    db.collection("Users")
                            .document(email)
                            .collection("Financial Details")
                            .document("Monthly Expenditure")
                            .collection(monthName)
                            .document("totals")
                            .set(new Totals(totalIncome, totalExpense, balance));
                });
    }

    public static class Totals {
        public double totalIncome;
        public double totalExpense;
        public double balance;

        public Totals(double totalIncome, double totalExpense, double balance) {
            this.totalIncome = totalIncome;
            this.totalExpense = totalExpense;
            this.balance = balance;
        }
    }

    public static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateTime, tvNote, tvAmount;
        Button btnDelete;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvNote = itemView.findViewById(R.id.tvNote);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
