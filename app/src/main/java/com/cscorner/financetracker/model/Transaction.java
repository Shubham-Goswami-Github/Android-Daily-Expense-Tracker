package com.cscorner.financetracker.model;

public class Transaction {
    private double amount;
    private String category;
    private String note;
    private String date;
    private String time;
    private boolean isExpense;
    private String transactionDateKey; // New field to help group transactions by date

    // 🔹 Default constructor (Required for Firebase)
    public Transaction() {}

    // 🔹 Constructor for manual creation (separate date & time)
    public Transaction(double amount, String category, String date, String time, String note, boolean isIncome) {
        this.amount = amount;
        this.category = category;
        this.date = date;
        this.time = time;
        this.note = note;
        this.isExpense = !isIncome;

        // Generate a key based on the date (used for grouping)
        this.transactionDateKey = generateTransactionDateKey(date);
    }

    // 🔹 Getter for the new field
    public String getTransactionDateKey() {
        return transactionDateKey;
    }

    // 🔹 Generate a unique key based on date to group transactions
    private String generateTransactionDateKey(String date) {
        // Format the date to make it suitable for grouping
        return date.replace("/", "-"); // Example: "12/04/2025" becomes "12-04-2025"
    }

    // 🔹 Getters
    public double getAmount() { return amount; }
    public String getCategory() { return category; }
    public String getNote() { return note; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public boolean isExpense() { return isExpense; }
    public boolean isIncome() { return !isExpense; }

    // 🔹 Added getMonth() method to extract the month
    public String getMonth() {
        if (date != null && date.length() >= 7) {
            // Extract the first 7 characters (yyyy-MM) from the date string
            return date.substring(5, 7); // Assuming the date format is yyyy-MM-dd
        }
        return ""; // Return empty string if the date is not valid
    }

    // 🔹 Setters (Required for Firebase deserialization)
    public void setAmount(double amount) { this.amount = amount; }
    public void setCategory(String category) { this.category = category; }
    public void setNote(String note) { this.note = note; }
    public void setDate(String date) {
        this.date = date;
        this.transactionDateKey = generateTransactionDateKey(date); // Update the transaction key when date changes
    }
    public void setTime(String time) { this.time = time; }
    public void setExpense(boolean isExpense) { this.isExpense = isExpense; }

    // 🔹 Optional: Set by string type (for Firestore string types)
    public void setType(String type) {
        this.isExpense = type != null && type.equalsIgnoreCase("Expense");
    }
}
