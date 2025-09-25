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
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<Transaction> transactionList;
    private FirebaseFirestore db;

    // Constructor
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

        // 🧪 Debug: Check incoming time format
        Log.d("TransactionAdapter", "Raw Time: " + transaction.getTime());

        // Format time to 12-hour format
        String formattedTime = formatTimeTo12Hour(transaction.getTime());

        // Set date and time
        holder.tvDateTime.setText(transaction.getDate() + " " + formattedTime);

        // Set transaction note
        holder.tvNote.setText(transaction.getNote());

        // Set amount with symbol and color
        if (transaction.isIncome()) {
            holder.tvAmount.setText("+ ₹" + transaction.getAmount());
            holder.tvAmount.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.green));
        } else {
            holder.tvAmount.setText("- ₹" + transaction.getAmount());
            holder.tvAmount.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.red));
        }

        // Set delete button listener
        holder.btnDelete.setOnClickListener(v -> {
            // Pass the context from the holder to the deleteTransaction method
            deleteTransaction(transaction, position, holder.itemView.getContext());
        });
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    // 🔁 Format time to 12-hour with AM/PM
    private String formatTimeTo12Hour(String time24) {
        try {
            SimpleDateFormat sdfInput;
            if (time24.matches("\\d{2}:\\d{2}:\\d{2}")) {
                sdfInput = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            } else if (time24.matches("\\d{2}:\\d{2}")) {
                sdfInput = new SimpleDateFormat("HH:mm", Locale.getDefault());
            } else {
                return time24; // fallback
            }

            SimpleDateFormat sdfOutput = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            Date dateObj = sdfInput.parse(time24);
            return sdfOutput.format(dateObj);
        } catch (Exception e) {
            e.printStackTrace();
            return time24;
        }
    }

    // Delete a transaction from Firestore
    private void deleteTransaction(Transaction transaction, int position, Context context) {
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (email == null || email.isEmpty()) {
            Toast.makeText(context, "User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Firestore reference to the specific document of the transaction
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Users")
                .document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(transaction.getMonth()) // Using the month from the transaction
                .document(transaction.getTransactionDateKey()) // Use transaction date key for the document id
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Remove transaction from the list and notify the adapter
                    transactionList.remove(position);
                    notifyItemRemoved(position);

                    // Recalculate totals after transaction deletion
                    recalculateTotals(email, transaction.getDate(), context);

                    Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Failed to delete transaction", Toast.LENGTH_SHORT).show();
                    Log.e("TransactionAdapter", "Delete failed", e);
                });
    }

    // Function to recalculate totals after deleting a transaction
    private void recalculateTotals(String email, String date, Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Fetch all transactions for the given date and month
        db.collection("Users")
                .document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(getMonthFromDate(date)) // Use the method to get the month
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double totalIncome = 0;
                    double totalExpense = 0;

                    // Calculate the totals based on the remaining transactions
                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        Transaction transaction = document.toObject(Transaction.class);
                        if (transaction != null) {
                            if (transaction.isIncome()) {
                                totalIncome += transaction.getAmount();
                            } else {
                                totalExpense += transaction.getAmount();
                            }
                        }
                    }

                    // Calculate the balance
                    double balance = totalIncome - totalExpense;

                    // Update Firestore with the new totals
                    db.collection("Users")
                            .document(email)
                            .collection("Financial Details")
                            .document("Monthly Expenditure")
                            .collection(getMonthFromDate(date)) // Same month where transactions are stored
                            .document("totals") // You can create a 'totals' document to store balance, income, and expense
                            .set(new Totals(totalIncome, totalExpense, balance))
                            .addOnSuccessListener(aVoid -> {
                                // Optionally update the UI (if needed)
                                Toast.makeText(context, "Totals updated", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(context, "Failed to update totals", Toast.LENGTH_SHORT).show();
                                Log.e("TransactionAdapter", "Failed to update totals", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Failed to fetch transactions", Toast.LENGTH_SHORT).show();
                    Log.e("TransactionAdapter", "Failed to fetch transactions", e);
                });
    }

    // Helper function to get month from date (formatted as yyyy-MM-dd)
    private String getMonthFromDate(String date) {
        if (date != null && date.length() >= 7) {
            return date.substring(5, 7); // Extract the month from date
        }
        return ""; // Return empty if date is invalid
    }

    // Totals class to store the balance, income, and expense
    public static class Totals {
        private double totalIncome;
        private double totalExpense;
        private double balance;

        public Totals(double totalIncome, double totalExpense, double balance) {
            this.totalIncome = totalIncome;
            this.totalExpense = totalExpense;
            this.balance = balance;
        }

        // Getters
        public double getTotalIncome() {
            return totalIncome;
        }

        public double getTotalExpense() {
            return totalExpense;
        }

        public double getBalance() {
            return balance;
        }
    }


    public static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateTime, tvNote, tvAmount;
        Button btnDelete;  // Delete button for each transaction item

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvNote = itemView.findViewById(R.id.tvNote);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            btnDelete = itemView.findViewById(R.id.btnDelete);  // Initialize delete button
        }
    }
}
