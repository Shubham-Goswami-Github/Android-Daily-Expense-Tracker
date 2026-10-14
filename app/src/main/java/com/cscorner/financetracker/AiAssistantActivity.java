package com.cscorner.financetracker;

import android.animation.ObjectAnimator;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.cscorner.financetracker.model.Note;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiAssistantActivity extends AppCompatActivity {

    private static final String TAG = "AiAssistantActivity";
    private static final String WORKER_URL =
            "https://finance-ai-backend.shubhamsuraj8993.workers.dev/";
    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private LinearLayout chatMessages;
    private ScrollView chatScrollView;
    private EditText etMessage;
    private ImageButton btnSend;
    private TextView btnClearChat;
    private TextView btnBack;

    private View rootLayoutForKeyboard;
    private int lastVisibleFrameHeight = -1;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private final int purple = Color.rgb(91, 80, 214);
    private final int darkText = Color.rgb(38, 38, 65);

    private final String[] months = {
            "January", "February", "March", "April",
            "May", "June", "July", "August",
            "September", "October", "November", "December"
    };

    private final OkHttpClient httpClient = new OkHttpClient();
    private final JSONArray conversationHistory = new JSONArray();

    private boolean loading = false;
    private boolean chatHistoryLoading = false;
    private boolean clearingChat = false;
    private String selectedLanguage = "Hinglish";

    private static class TransactionRecord {
        final double amount;
        final String type;
        final Date date;

        TransactionRecord(double amount, String type, Date date) {
            this.amount = amount;
            this.type = type == null ? "" : type.trim();
            this.date = date;
        }

        boolean isIncome() {
            return "Income".equalsIgnoreCase(type);
        }

        boolean isExpense() {
            return "Expense".equalsIgnoreCase(type);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Smooth entrance transition for the popup-style chat window
        overridePendingTransition(R.anim.popup_enter, 0);

        setContentView(R.layout.activity_ai_assistant);

        // Translucent popup window ke liye: humein khud IME (keyboard) insets
// handle karne hain, isliye system ko decor auto-fit nahi karne dete.
// Ye line WindowSoftInputMode=adjustResize ke sath milke keyboard aane/
// jaane par reliable resize deti hai, especially translucent activities
// ke liye jahan automatic adjustResize akela kaam nahi karta.
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        View rootLayout = findViewById(R.id.rootLayout);
        View chatCardContainer = findViewById(R.id.chatCardContainer);

        if (rootLayout != null) {
            rootLayout.setOnClickListener(v -> finish());
        }

        if (chatCardContainer != null) {
            chatCardContainer.setOnClickListener(v -> {
                // Consume click so tapping inside the chat card doesn't close it
            });

            if (rootLayout != null) {
                ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                    Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
                    int keyboardHeight = imeInsets.bottom;
                    boolean isImeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime());

                    ConstraintLayout.LayoutParams params =
                            (ConstraintLayout.LayoutParams) chatCardContainer.getLayoutParams();
                    float density = getResources().getDisplayMetrics().density;
                    int marginDp = (int) (16 * density);

                    if (isImeVisible && keyboardHeight > 0) {
                        int topMarginPx = (int) (24 * density);
                        int minHeightPx = (int) (320 * density);

                        int availableHeight = v.getHeight() - keyboardHeight - marginDp - topMarginPx;
                        if (availableHeight < minHeightPx) {
                            availableHeight = minHeightPx;
                        }

                        // Fixed height taaki keyboard ke upar poora card (including input box) fit ho jaaye
                        params.height = availableHeight;
                        params.topMargin = topMarginPx;
                        params.bottomMargin = keyboardHeight + marginDp;
                    } else {
                        // Wapas original percent-based (0.80) height restore karo
                        params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT;
                        params.topMargin = 0;
                        params.bottomMargin = 0;
                    }

                    chatCardContainer.setLayoutParams(params);
                    scrollToBottom();

                    return windowInsets;
                });
            }
        }
        chatMessages = findViewById(R.id.chatMessages);
        chatScrollView = findViewById(R.id.chatScrollView);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        btnClearChat = findViewById(R.id.btnClearChat);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // This screen is only available inside the signed-in app.
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please log in to use AI Assistant.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        SharedPreferences preferences =
                getSharedPreferences("FinanceTrackerPrefs", MODE_PRIVATE);
        selectedLanguage = preferences.getString("assistant_language", "Hinglish");

        btnBack.setOnClickListener(v -> {
            bounceView(v);
            finish();
        });

        btnSend.setOnClickListener(v -> {
            bounceView(v);
            sendMessage();
        });

        btnClearChat.setOnClickListener(v -> {
            bounceView(v);
            confirmClearChat();
        });
        setupKeyboardAutoScroll();

        loadChatHistory();
    }

    @Override
    public void finish() {
        super.finish();
        // Smooth exit transition for the popup-style chat window
        overridePendingTransition(0, R.anim.popup_exit);
    }

    // -------------------- CHAT STORAGE --------------------

    private CollectionReference chatCollection() {
        if (auth.getCurrentUser() == null
                || auth.getCurrentUser().getEmail() == null) {
            return null;
        }

        String email = auth.getCurrentUser().getEmail();
        return db.collection("Users")
                .document(email)
                .collection("AI Chat History")
                .document("Messages")
                .collection("items");
    }

    private void saveChatMessage(String role, String content) {
        CollectionReference collection = chatCollection();
        if (collection == null || content == null) return;

        Calendar calendar = Calendar.getInstance();
        String month = new SimpleDateFormat("MMMM", Locale.ENGLISH)
                .format(calendar.getTime());
        String date = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
                .format(calendar.getTime());

        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        message.put("timestamp", FieldValue.serverTimestamp());
        message.put("month", month);
        message.put("date", date);

        collection.add(message).addOnFailureListener(error ->
                Log.e(TAG, "Failed to save chat message", error));
    }

    private void loadChatHistory() {
        CollectionReference collection = chatCollection();

        if (collection == null) {
            showWelcomeMessage();
            return;
        }

        chatHistoryLoading = true;
        setChatControlsEnabled(false);

        collection.orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;

                    chatMessages.removeAllViews();
                    clearConversationHistory();

                    JSONArray restored = new JSONArray();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String role = doc.getString("role");
                        String content = doc.getString("content");

                        if (role == null || content == null) continue;
                        if (!"user".equals(role) && !"assistant".equals(role)) {
                            continue;
                        }

                        addMessageBubble(content, "user".equals(role));

                        try {
                            restored.put(new JSONObject()
                                    .put("role", role)
                                    .put("content", content));
                        } catch (Exception ignored) {
                            // Skip malformed history item.
                        }
                    }

                    restoreConversationHistory(restored);

                    if (snapshot.isEmpty()) {
                        showWelcomeMessage();
                    }

                    chatHistoryLoading = false;
                    setChatControlsEnabled(true);
                    scrollToBottom();
                })
                .addOnFailureListener(error -> {
                    if (isFinishing() || isDestroyed()) return;

                    chatHistoryLoading = false;
                    setChatControlsEnabled(true);
                    chatMessages.removeAllViews();
                    showWelcomeMessage();

                    Toast.makeText(
                            this,
                            "Couldn't load saved chat history. Check internet and Firestore rules.",
                            Toast.LENGTH_LONG
                    ).show();

                    Log.e(TAG, "Failed to load chat history", error);
                });
    }

    private void restoreConversationHistory(JSONArray restored) {
        clearConversationHistory();
        int start = Math.max(0, restored.length() - 10);

        for (int i = start; i < restored.length(); i++) {
            JSONObject item = restored.optJSONObject(i);
            if (item != null) conversationHistory.put(item);
        }
    }

    private void clearConversationHistory() {
        while (conversationHistory.length() > 0) {
            conversationHistory.remove(0);
        }
    }

    private void confirmClearChat() {
        if (loading || chatHistoryLoading || clearingChat) return;

        new AlertDialog.Builder(this)
                .setTitle("Clear chat history?")
                .setMessage("This permanently deletes your saved AI chat messages. Your income and expense transactions will not be deleted.")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Clear Chat", (dialog, which) -> clearChatHistory())
                .show();
    }

    private void clearChatHistory() {
        CollectionReference collection = chatCollection();

        if (collection == null) {
            Toast.makeText(this, "Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        clearingChat = true;
        setChatControlsEnabled(false);
        collection.get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> documents = snapshot.getDocuments();
                    if (documents.isEmpty()) {
                        finishClearingChat();
                    } else {
                        deleteChatBatch(documents, 0);
                    }
                })
                .addOnFailureListener(error -> {
                    clearingChat = false;
                    setChatControlsEnabled(true);
                    Toast.makeText(
                            this,
                            "Couldn't clear chat history. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                    Log.e(TAG, "Failed to fetch chat for deletion", error);
                });
    }

    private void deleteChatBatch(List<DocumentSnapshot> documents, int startIndex) {
        int endIndex = Math.min(startIndex + 450, documents.size());
        WriteBatch batch = db.batch();

        for (int i = startIndex; i < endIndex; i++) {
            batch.delete(documents.get(i).getReference());
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    if (endIndex < documents.size()) {
                        deleteChatBatch(documents, endIndex);
                    } else {
                        finishClearingChat();
                    }
                })
                .addOnFailureListener(error -> {
                    clearingChat = false;
                    setChatControlsEnabled(true);
                    Toast.makeText(
                            this,
                            "Some messages could not be deleted. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                    Log.e(TAG, "Failed to delete chat batch", error);
                    loadChatHistory();
                });
    }

    private void finishClearingChat() {
        chatMessages.removeAllViews();
        clearConversationHistory();
        clearingChat = false;
        loading = false;
        chatHistoryLoading = false;
        setChatControlsEnabled(true);
        showWelcomeMessage();

        Toast.makeText(
                this,
                "Chat history cleared successfully.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void setChatControlsEnabled(boolean enabled) {
        if (btnSend != null) btnSend.setEnabled(enabled && !loading);
        if (btnClearChat != null) {
            btnClearChat.setEnabled(enabled && !loading);
        }
    }

    // -------------------- WELCOME AND MESSAGES --------------------

    private void showWelcomeMessage() {
        addMessageBubble(
                "Hello! 👋 I'm your AI Finance Assistant.\n\n"
                        + "I can answer questions about your saved financial records "
                        + "and help you add, edit, or delete transactions and notes.\n\n"
                        + "Try asking:\n"
                        + "• What is my overall income?\n"
                        + "• Give me my monthly summary\n"
                        + "• How much did I spend today?\n"
                        + "• Aaj maine Ritesh se 200 rupay liye\n"
                        + "• Maine Suresh ko 500 diye\n"
                        + "• Mujhe kal doctor jaana hai, yaad dila dena\n"
                        + "• Give me saving tips",
                false
        );

        addQuickPrompt("Show my overall income");
        addQuickPrompt("Give me my monthly summary");
        addQuickPrompt("How much did I spend today?");
        addQuickPrompt("Compare this month with last month");
        addQuickPrompt("Give me saving tips");
    }

    private void sendMessage() {
        if (loading || chatHistoryLoading || clearingChat) return;

        String message = etMessage.getText().toString().trim();
        if (message.isEmpty()) {
            etMessage.setError("Please enter a message");
            return;
        }

        if (auth.getCurrentUser() == null
                || auth.getCurrentUser().getEmail() == null) {
            Toast.makeText(this, "Please log in to continue.", Toast.LENGTH_SHORT).show();
            return;
        }

        removeQuickPromptsAndWelcomeIfPresent();
        showUserMessage(message);
        saveChatMessage("user", message);
        etMessage.setText("");

        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
        }

        addToConversationHistoryUserOnly(message);
        answerFinanceQuestion(message);
    }

    private void addToConversationHistoryUserOnly(String message) {
        try {
            conversationHistory.put(new JSONObject()
                    .put("role", "user")
                    .put("content", message));
            while (conversationHistory.length() > 10) {
                conversationHistory.remove(0);
            }
        } catch (Exception ignored) {
        }
    }

    private void removeQuickPromptsAndWelcomeIfPresent() {
        String[] prompts = {
                "Show my overall income",
                "Give me my monthly summary",
                "How much did I spend today?",
                "Compare this month with last month",
                "Give me saving tips"
        };

        for (int i = chatMessages.getChildCount() - 1; i >= 0; i--) {
            View child = chatMessages.getChildAt(i);
            if (!(child instanceof TextView)) continue;

            String text = ((TextView) child).getText().toString();
            for (String prompt : prompts) {
                if (text.equals(prompt) || text.endsWith(prompt)) {
                    chatMessages.removeViewAt(i);
                    break;
                }
            }
        }
    }

    private void answerFinanceQuestion(String message) {
        loading = true;
        setChatControlsEnabled(false);

        View loadingView = addTypingIndicatorBubble();

        loadAllTransactions(new RecordsCallback() {
            @Override
            public void onSuccess(List<TransactionRecord> records) {
                loadAllNotes(notes -> {
                    String combinedSummary = buildFinanceSummary(records)
                            + "\n\n" + buildNotesSummary(notes)
                            + "\n\n" + buildNotesFinanceSummary(notes);
                    callAI(message, combinedSummary, loadingView, message);
                });
            }

            @Override
            public void onError(String error) {
                removeLoadingAndFinish(loadingView);
                showBotMessage(
                        "I couldn't load your financial records.\n\n"
                                + "Please check your internet connection and Firestore permissions.\n\n"
                                + "Details: " + error
                );
            }
        });
    }

    private interface NotesCallback {
        void onSuccess(List<Note> notes);
    }

    private void loadAllNotes(NotesCallback callback) {
        if (auth.getCurrentUser() == null || auth.getCurrentUser().getEmail() == null) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        String email = auth.getCurrentUser().getEmail();
        db.collection("Users").document(email).collection("Personal Notes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Note> notes = queryDocumentSnapshots.toObjects(Note.class);
                    callback.onSuccess(notes);
                })
                .addOnFailureListener(e -> callback.onSuccess(new ArrayList<>()));
    }

    private String buildNotesSummary(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return "USER'S PERSONAL NOTES\nNo saved notes found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("USER'S PERSONAL NOTES (Total: ").append(notes.size()).append("):\n");
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());

        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            sb.append(i + 1).append(". ID: ").append(n.getId())
                    .append(" | Type: ").append(n.getType())
                    .append(" | Summary: ").append(n.getSummary());

            if (n.isReminderEnabled() && n.getReminderDate() > 0) {
                sb.append(" | Reminder: ").append(sdf.format(new Date(n.getReminderDate())));
            } else {
                sb.append(" | Reminder: None");
            }
            sb.append(" | Created: ").append(sdf.format(new Date(n.getCreatedAt()))).append("\n");
        }

        return sb.toString();
    }
    /**
     * Extracts the first positive numeric amount found inside a note's free
     * text (e.g. "Ritesh se ₹200 liye" -> 200.0). Used to compute lending /
     * borrowing totals without needing a separate "amount" field in Firestore.
     */
    private double extractAmountFromText(String text) {
        if (text == null || text.trim().isEmpty()) return 0;

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)")
                        .matcher(text);

        while (matcher.find()) {
            try {
                String raw = matcher.group(1).replace(",", "");
                double value = Double.parseDouble(raw);
                if (value > 0) return value;
            } catch (NumberFormatException ignored) {
                // Keep scanning for the next number in the text.
            }
        }
        return 0;
    }

    /**
     * Builds a transaction-style financial summary for lending/borrowing
     * notes: total given, total taken, net position, and overdue/upcoming
     * reminders. Amounts are heuristically extracted from each note's text
     * since notes don't store a dedicated numeric amount field.
     */
    private String buildNotesFinanceSummary(List<Note> notes) {
        double totalLent = 0;
        double totalBorrowed = 0;
        int lendingCount = 0;
        int borrowedCount = 0;
        int generalCount = 0;
        int overdueCount = 0;
        int upcomingCount = 0;
        long now = System.currentTimeMillis();

        StringBuilder overdueList = new StringBuilder();
        StringBuilder upcomingList = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());

        if (notes != null) {
            for (Note n : notes) {
                String type = n.getType() == null ? "" : n.getType();
                double amount = extractAmountFromText(n.getSummary());

                if ("Money lending".equalsIgnoreCase(type)) {
                    lendingCount++;
                    totalLent += amount;
                } else if ("Money borrowed".equalsIgnoreCase(type)) {
                    borrowedCount++;
                    totalBorrowed += amount;
                } else {
                    generalCount++;
                }

                if (n.isReminderEnabled() && n.getReminderDate() > 0) {
                    if (n.getReminderDate() < now) {
                        overdueCount++;
                        overdueList.append("- ").append(n.getSummary())
                                .append(" (was due ")
                                .append(sdf.format(new Date(n.getReminderDate())))
                                .append(")\n");
                    } else {
                        upcomingCount++;
                        upcomingList.append("- ").append(n.getSummary())
                                .append(" (due ")
                                .append(sdf.format(new Date(n.getReminderDate())))
                                .append(")\n");
                    }
                }
            }
        }

        double net = totalLent - totalBorrowed;

        StringBuilder sb = new StringBuilder();
        sb.append("NOTES FINANCIAL SUMMARY (money lending/borrowing totals, from note text):\n");
        sb.append("Total money lent (given to others): ").append(money(totalLent))
                .append(" across ").append(lendingCount).append(" note(s)\n");
        sb.append("Total money borrowed (taken from others): ").append(money(totalBorrowed))
                .append(" across ").append(borrowedCount).append(" note(s)\n");
        sb.append("Net position: ").append(money(net))
                .append(net >= 0 ? " (others owe you more overall)" : " (you owe others more overall)")
                .append("\n");
        sb.append("General (non-money) reminders: ").append(generalCount).append("\n\n");

        if (overdueCount > 0) {
            sb.append("OVERDUE REMINDERS (").append(overdueCount).append("):\n").append(overdueList);
        } else {
            sb.append("OVERDUE REMINDERS: None\n");
        }

        sb.append("\n");

        if (upcomingCount > 0) {
            sb.append("UPCOMING REMINDERS (").append(upcomingCount).append("):\n").append(upcomingList);
        } else {
            sb.append("UPCOMING REMINDERS: None\n");
        }

        sb.append("\nNote: These totals are estimated by extracting the first number ")
                .append("found in each note's text. They may be inaccurate if a note ")
                .append("doesn't clearly state a numeric amount.");

        return sb.toString();
    }
    private interface RecordsCallback {
        void onSuccess(List<TransactionRecord> records);
        void onError(String error);
    }

    // -------------------- FINANCIAL RECORDS --------------------

    private CollectionReference monthCollection(String email, String month) {
        return db.collection("Users")
                .document(email)
                .collection("Financial Details")
                .document("Monthly Expenditure")
                .collection(month);
    }

    private void loadAllTransactions(RecordsCallback callback) {
        if (auth.getCurrentUser() == null
                || auth.getCurrentUser().getEmail() == null) {
            callback.onError("User session is unavailable.");
            return;
        }

        String email = auth.getCurrentUser().getEmail();
        List<Task<?>> tasks = new ArrayList<>();

        for (String month : months) {
            tasks.add(monthCollection(email, month).get());
        }

        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    List<TransactionRecord> records = new ArrayList<>();

                    for (Object result : results) {
                        QuerySnapshot snapshot = (QuerySnapshot) result;

                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            Object amountObject = document.get("amount");
                            String type = document.getString("type");
                            String dateText = document.getString("date");

                            if (!(amountObject instanceof Number)
                                    || type == null || dateText == null) continue;

                            if (!"Income".equalsIgnoreCase(type)
                                    && !"Expense".equalsIgnoreCase(type)) continue;

                            Date parsedDate = parseDate(dateText);
                            double amount = ((Number) amountObject).doubleValue();

                            if (parsedDate == null || !Double.isFinite(amount)
                                    || amount < 0) continue;

                            records.add(new TransactionRecord(amount, type, parsedDate));
                        }
                    }

                    callback.onSuccess(records);
                })
                .addOnFailureListener(error -> {
                    String details = error.getLocalizedMessage();
                    callback.onError(details == null ? "Unknown Firestore error" : details);
                });
    }

    private Date parseDate(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            SimpleDateFormat format =
                    new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH);
            format.setLenient(false);
            return format.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String monthFromDate(String dateText) {
        Date parsed = parseDate(dateText);
        if (parsed == null) return "";

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(parsed);
        return months[calendar.get(Calendar.MONTH)];
    }

    private String todayDate() {
        return new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
                .format(new Date());
    }

    private String currentTime() {
        return new SimpleDateFormat("HH:mm", Locale.ENGLISH)
                .format(new Date());
    }

    private String buildFinanceSummary(List<TransactionRecord> records) {
        Calendar now = Calendar.getInstance();

        Calendar todayStart = (Calendar) now.clone();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);

        Calendar tomorrow = (Calendar) todayStart.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);

        Calendar currentMonthStart = (Calendar) todayStart.clone();
        currentMonthStart.set(Calendar.DAY_OF_MONTH, 1);

        Calendar nextMonthStart = (Calendar) currentMonthStart.clone();
        nextMonthStart.add(Calendar.MONTH, 1);

        Calendar previousMonthStart = (Calendar) currentMonthStart.clone();
        previousMonthStart.add(Calendar.MONTH, -1);

        double allIncome = 0;
        double allExpense = 0;
        double todayExpense = 0;
        double currentIncome = 0;
        double currentExpense = 0;
        double previousIncome = 0;
        double previousExpense = 0;
        int transactionCount = 0;

        for (TransactionRecord record : records) {
            transactionCount++;
            if (record.isIncome()) allIncome += record.amount;
            if (record.isExpense()) allExpense += record.amount;

            if (inPeriod(record, todayStart, tomorrow) && record.isExpense()) {
                todayExpense += record.amount;
            }

            if (inPeriod(record, currentMonthStart, nextMonthStart)) {
                if (record.isIncome()) currentIncome += record.amount;
                if (record.isExpense()) currentExpense += record.amount;
            }

            if (inPeriod(record, previousMonthStart, currentMonthStart)) {
                if (record.isIncome()) previousIncome += record.amount;
                if (record.isExpense()) previousExpense += record.amount;
            }
        }

        String currentMonth =
                months[now.get(Calendar.MONTH)] + " " + now.get(Calendar.YEAR);
        Calendar previousCalendar = (Calendar) currentMonthStart.clone();
        previousCalendar.add(Calendar.MONTH, -1);
        String previousMonth = months[previousCalendar.get(Calendar.MONTH)]
                + " " + previousCalendar.get(Calendar.YEAR);

        StringBuilder summary = new StringBuilder();
        summary.append("Financial data from the user's saved records.\n");
        summary.append("Currency: INR (Indian Rupees).\n");
        summary.append("Transactions counted: ").append(transactionCount).append("\n\n");

        summary.append("ALL-TIME TOTALS\n");
        summary.append("Total income: ").append(money(allIncome)).append("\n");
        summary.append("Total expenses: ").append(money(allExpense)).append("\n");
        summary.append("Net difference: ").append(money(allIncome - allExpense)).append("\n\n");

        summary.append("TODAY: ").append(todayDate()).append("\n");
        summary.append("Expenses today: ").append(money(todayExpense)).append("\n\n");

        summary.append("CURRENT MONTH: ").append(currentMonth).append("\n");
        summary.append("Income: ").append(money(currentIncome)).append("\n");
        summary.append("Expenses: ").append(money(currentExpense)).append("\n");
        summary.append("Net difference: ").append(money(currentIncome - currentExpense)).append("\n\n");

        summary.append("PREVIOUS MONTH: ").append(previousMonth).append("\n");
        summary.append("Income: ").append(money(previousIncome)).append("\n");
        summary.append("Expenses: ").append(money(previousExpense)).append("\n\n");

        summary.append("Important: Totals include only readable transactions ")
                .append("with valid amounts, types, and dates. Net difference ")
                .append("is not necessarily the user's bank balance.");

        return summary.toString();
    }

    private boolean inPeriod(TransactionRecord record, Calendar start, Calendar end) {
        long time = record.date.getTime();
        return time >= start.getTimeInMillis() && time < end.getTimeInMillis();
    }

    // -------------------- AI REQUEST --------------------

    private void callAI(String message, String financeSummary, View loadingView, String originalUserMessage) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("message", message);
            requestJson.put("language", selectedLanguage);
            requestJson.put("history", conversationHistory);
            requestJson.put("financeSummary", financeSummary);

            RequestBody requestBody =
                    RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE);

            Request request = new Request.Builder()
                    .url(WORKER_URL)
                    .post(requestBody)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    runOnUiThread(() -> {
                        removeLoadingAndFinish(loadingView);
                        showBotMessage(
                                "Unable to connect to the AI service. "
                                        + "Please check your internet connection and try again."
                        );
                    });
                }

                @Override
                public void onResponse(Call call, Response response)
                        throws java.io.IOException {
                    String responseText =
                            response.body() != null ? response.body().string() : "";
                    boolean successful = response.isSuccessful();
                    response.close();

                    runOnUiThread(() -> {
                        removeLoadingAndFinish(loadingView);

                        try {
                            JSONObject result = new JSONObject(responseText);

                            if (!successful) {
                                String error = result.optString(
                                        "error", "The AI request failed.");
                                String details = result.optString("details", "");
                                showBotMessage(error
                                        + (details.isEmpty() ? "" : "\n" + details));
                                return;
                            }

                            String reply = result.optString(
                                    "reply",
                                    "Sorry, I could not generate a response."
                            );
                            JSONObject action = result.optJSONObject("action");

                            String actionType = action == null
                                    ? "none" : action.optString("type", "none");
                            String actionStatus = action == null
                                    ? "none" : action.optString("status", "none");

                            addToConversationHistoryAssistant(reply);

                            if (action != null
                                    && "ready".equalsIgnoreCase(actionStatus)
                                    && !"none".equalsIgnoreCase(actionType)) {
                                executeTransactionAction(action, reply, originalUserMessage);
                            } else {
                                executeTransactionAction(null, reply, originalUserMessage);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Could not parse AI response", e);
                            showBotMessage(
                                    "I couldn't read the AI response. Please try again."
                            );
                        }
                    });
                }
            });
        } catch (Exception e) {
            removeLoadingAndFinish(loadingView);
            showBotMessage("Unable to prepare your AI request.");
            Log.e(TAG, "Unable to prepare AI request", e);
        }
    }

    private void addToConversationHistoryAssistant(String reply) {
        try {
            conversationHistory.put(new JSONObject()
                    .put("role", "assistant")
                    .put("content", reply));
            while (conversationHistory.length() > 10) {
                conversationHistory.remove(0);
            }
        } catch (Exception ignored) {
        }
    }

    private void removeLoadingAndFinish(View loadingView) {
        if (loadingView != null) {
            stopTypingAnimation(loadingView);
            chatMessages.removeView(loadingView);
        }
        loading = false;
        setChatControlsEnabled(true);
    }

    // -------------------- CHAT UI --------------------

    private void addQuickPrompt(String prompt) {
        TextView promptView = new TextView(this);
        promptView.setText("💬  " + prompt);
        promptView.setTextSize(13.5f);
        promptView.setTextColor(purple);
        promptView.setPadding(dp(14), dp(10), dp(14), dp(10));
        promptView.setBackground(createBackground(
                Color.WHITE, Color.rgb(220, 218, 245)));
        promptView.setElevation(dp(1));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        promptView.setLayoutParams(params);
        promptView.setClickable(true);
        promptView.setFocusable(true);
        promptView.setTag("quick_prompt");

        promptView.setAlpha(0f);
        promptView.setTranslationX(-dp(16));

        promptView.setOnClickListener(v -> {
            bounceView(v);
            etMessage.setText(prompt);
            etMessage.setSelection(etMessage.length());
            sendMessage();
        });

        chatMessages.addView(promptView);

        promptView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void showUserMessage(String message) {
        addMessageBubble(message, true);
    }

    private TextView showBotMessage(String message) {
        TextView view = addMessageBubble(message, false);
        saveChatMessage("assistant", message);
        return view;
    }

    private TextView addMessageBubble(String message, boolean isUser) {
        TextView textView = new TextView(this);
        textView.setText(message);
        textView.setTextSize(15);
        textView.setTextColor(isUser ? Color.WHITE : darkText);
        textView.setLineSpacing(dp(2), 1f);
        textView.setPadding(dp(14), dp(11), dp(14), dp(11));
        textView.setMaxWidth((int) (
                getResources().getDisplayMetrics().widthPixels * 0.78f));

        int backgroundColor = isUser ? purple : Color.WHITE;
        int strokeColor = isUser ? purple : Color.rgb(235, 235, 243);
        textView.setBackground(createBackground(backgroundColor, strokeColor));
        textView.setElevation(isUser ? dp(1) : dp(2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = isUser ? Gravity.END : Gravity.START;
        params.setMargins(0, 0, 0, dp(12));
        textView.setLayoutParams(params);
        textView.setTag("message_bubble");

        // Smooth entrance animation for a premium feel
        textView.setAlpha(0f);
        textView.setTranslationY(dp(14));

        chatMessages.addView(textView);

        textView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(230)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        scrollToBottom();
        return textView;
    }

    private View addTypingIndicatorBubble() {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setPadding(dp(16), dp(14), dp(16), dp(14));
        bubble.setBackground(createBackground(Color.WHITE, Color.rgb(235, 235, 243)));
        bubble.setElevation(dp(2));
        bubble.setTag("typing_indicator");

        List<View> dots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            int size = dp(8);
            LinearLayout.LayoutParams dotParams =
                    new LinearLayout.LayoutParams(size, size);
            dotParams.setMargins(i == 0 ? 0 : dp(5), 0, 0, 0);
            dot.setLayoutParams(dotParams);

            GradientDrawable dotBackground = new GradientDrawable();
            dotBackground.setShape(GradientDrawable.OVAL);
            dotBackground.setColor(purple);
            dot.setBackground(dotBackground);

            bubble.addView(dot);
            dots.add(dot);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.START;
        params.setMargins(0, 0, 0, dp(12));
        bubble.setLayoutParams(params);

        bubble.setAlpha(0f);
        bubble.setScaleX(0.9f);
        bubble.setScaleY(0.9f);

        chatMessages.addView(bubble);

        bubble.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> animateTypingDots(dots))
                .start();

        scrollToBottom();
        return bubble;
    }

    private void animateTypingDots(List<View> dots) {
        for (int i = 0; i < dots.size(); i++) {
            View dot = dots.get(i);
            ObjectAnimator animator =
                    ObjectAnimator.ofFloat(dot, "translationY", 0f, -dp(6), 0f);
            animator.setDuration(650);
            animator.setStartDelay(i * 150L);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.start();
            dot.setTag(animator);
        }
    }

    private void stopTypingAnimation(View bubble) {
        if (!(bubble instanceof LinearLayout)) return;
        LinearLayout layout = (LinearLayout) bubble;
        for (int i = 0; i < layout.getChildCount(); i++) {
            Object tag = layout.getChildAt(i).getTag();
            if (tag instanceof ObjectAnimator) {
                ((ObjectAnimator) tag).cancel();
            }
        }
    }
    /**
     * WhatsApp-style floating chat behavior: whenever the keyboard opens,
     * closes, or the layout resizes for any reason, keep the latest message
     * visible by auto-scrolling to the bottom instead of making the user
     * scroll manually.
     */
    private void setupKeyboardAutoScroll() {
        // Keyboard show/hide par resize + scroll ab WindowInsets listener
        // (onCreate mein set kiya gaya) handle karta hai. Yahan sirf ek extra
        // safety-net rakha hai: jab EditText focus le, thoda delay ke baad
        // phir se scroll karo, taaki keyboard animation settle hone ke baad
        // bhi latest message hamesha visible rahe.
        etMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                chatScrollView.postDelayed(this::scrollToBottom, 150);
                chatScrollView.postDelayed(this::scrollToBottom, 300);
            }
        });
    }
    private void scrollToBottom() {
        if (chatScrollView == null) return;
        chatScrollView.post(() -> {
            chatScrollView.fullScroll(View.FOCUS_DOWN);
            // Double-post: keyboard resize animation sometimes finishes a frame
            // later, so scroll again right after to guarantee the latest
            // message stays visible above the keyboard.
            chatScrollView.postDelayed(
                    () -> chatScrollView.fullScroll(View.FOCUS_DOWN), 80);
        });
    }

    private GradientDrawable createBackground(int backgroundColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(backgroundColor);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String money(double amount) {
        return String.format(Locale.getDefault(), "₹%,.2f", amount);
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private void bounceView(View view) {
        view.animate()
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(90)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start())
                .start();
    }

    private void completeActionAndExit(String successMessage) {
        showBotMessage(successMessage);
        Toast.makeText(this, "Transaction successful!", Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, 900);
    }

    // -------------------- TRANSACTION & NOTE ACTIONS --------------------

    private void executeTransactionAction(JSONObject action, String reply, String userMessage) {
        String type = action != null ? action.optString("type", "none") : "none";

        if (auth.getCurrentUser() == null
                || auth.getCurrentUser().getEmail() == null) {
            showBotMessage("Please log in again before managing your data.");
            return;
        }

        switch (type.toLowerCase(Locale.ROOT)) {
            case "add_transaction":
                addTransactionFromAI(action);
                break;
            case "edit_transaction":
                findAndModifyTransaction(action, false);
                break;
            case "delete_transaction":
                findAndModifyTransaction(action, true);
                break;
            case "add_note":
            case "create_note":
            case "save_note":
                addNoteFromAI(action, userMessage);
                break;
            case "read_notes":
            case "get_notes":
            case "list_notes":
            case "show_notes":
            case "view_notes":
                readNotesFromAI();
                break;
            case "edit_note":
            case "update_note":
            case "modify_note":
                updateNoteFromAI(action, userMessage);
                break;
            case "delete_note":
            case "remove_note":
                deleteNoteFromAI(action, userMessage);
                break;
            default:
                if (!checkAndExecuteLocalNoteIntent(userMessage)) {
                    showBotMessage(reply);
                }
                break;
        }
    }

    /**
     * Local safety-net: if the AI backend fails to tag an action (returns
     * "none" or something unrecognized), try to detect common note/reminder
     * intents directly from the raw user text so money-borrow/lend/reminder
     * requests are never silently ignored.
     */
    private boolean checkAndExecuteLocalNoteIntent(String userMessage) {
        if (userMessage == null) return false;
        String lower = userMessage.trim().toLowerCase(Locale.ROOT);

        if (lower.matches(".*(show|read|list|view|get|dikhao|padho).*note.*")
                || lower.matches(".*note.*(show|read|list|view|get|dikhao|padho).*")) {
            readNotesFromAI();
            return true;
        }

        if (lower.startsWith("add note") || lower.startsWith("create note") || lower.startsWith("save note")
                || lower.startsWith("note add") || lower.startsWith("note create") || lower.startsWith("note save")
                || lower.startsWith("note banao") || lower.startsWith("note likho")) {
            addNoteFromAI(null, userMessage);
            return true;
        }

        if (lower.startsWith("delete note") || lower.startsWith("remove note")
                || lower.startsWith("note delete") || lower.startsWith("note remove")
                || lower.startsWith("note hatao")) {
            deleteNoteFromAI(null, userMessage);
            return true;
        }

        if (lower.startsWith("update note") || lower.startsWith("edit note")
                || lower.startsWith("note update") || lower.startsWith("note edit")
                || lower.startsWith("note badlo")) {
            updateNoteFromAI(null, userMessage);
            return true;
        }

        // Safety-net: debt settlement/repayment phrases should delete the note,
        // not create a new one.
        boolean settlementSignal = lower.contains("wapas kar diya")
                || lower.contains("wapas kiya") || lower.contains("laut diya")
                || lower.contains("clear ho gaya") || lower.contains("clear kar diya")
                || lower.contains("mil gaya") || lower.contains("chuka diya")
                || lower.contains("paid back") || lower.contains("returned");

        if (settlementSignal) {
            deleteNoteFromAI(null, userMessage);
            return true;
        }

        // Guard: pure questions about totals/history ("kitna diya hai ab tak",
        // "total kitna liya hai") must NEVER be treated as a new note. Genuine
        // new lending/borrowing events always state a specific digit amount;
        // questions about cumulative totals almost never do.
        boolean looksLikeQuestion = lower.matches(
                ".*\\b(kitna|kitne|kaun|kab|kya|how much|total|overall|ab\\s*tak|so\\s*far)\\b.*"
        ) || lower.trim().endsWith("?");
        boolean hasDigit = lower.matches(".*\\d.*");

        // Safety-net: natural-language money borrow/lend statements
        // ("Ritesh se 200 liye", "Suresh ko 500 diye") even without an
        // explicit action tagged by the AI backend. Skipped entirely if the
        // message looks like a question with no specific amount.
        if (!(looksLikeQuestion && !hasDigit)) {
            boolean borrowedSignal = lower.contains("liya") || lower.contains("liye")
                    || lower.contains("borrow") || lower.contains("udhaar")
                    || lower.contains("udhar");
            boolean lentSignal = lower.contains("diya") || lower.contains("diye")
                    || lower.contains("lend") || lower.contains("lent") || lower.contains("gave");

            if (borrowedSignal || lentSignal) {
                addNoteFromAI(null, userMessage);
                return true;
            }
        }

        return false;
    }
    // -------------------- NOTE CRUD FUNCTIONS FOR AI --------------------

    /**
     * Figures out the correct note category ("Money lending", "Money
     * borrowed", or "General Reminder") using, in priority order:
     * 1) the noteType value the AI backend explicitly provided,
     * 2) Hindi/Hinglish keyword detection from the note text / user message.
     */
    private String normalizeNoteType(String rawType, String summary, String userMessage) {
        if (rawType != null && !rawType.trim().isEmpty()) {
            String lowerType = rawType.toLowerCase(Locale.ROOT);
            if (lowerType.contains("lend")) return "Money lending";
            if (lowerType.contains("borrow")) return "Money borrowed";
            if (lowerType.contains("general") || lowerType.contains("remind")) return "General Reminder";
        }

        String lower = ((summary != null ? summary : "") + " "
                + (userMessage != null ? userMessage : "")).toLowerCase(Locale.ROOT);

        // Direction-aware fallback: "mujhe ... diya/diye" means someone gave
        // money TO the user (borrowed), while "ko ... diya/diye" or plain
        // "diya/diye" by the user means the user gave money OUT (lending).
        boolean otherGaveToMe = lower.contains("mujhe") &&
                (lower.contains("diya") || lower.contains("diye"));
        boolean otherTookFromMe = lower.contains("mujhse") &&
                (lower.contains("liya") || lower.contains("liye"));

        if (otherGaveToMe) return "Money borrowed";
        if (otherTookFromMe) return "Money lending";

        boolean borrowedSignal = lower.contains("liya") || lower.contains("liye")
                || lower.contains("borrow") || lower.contains("karz") || lower.contains("karza")
                || lower.contains("loan liya");
        boolean lentSignal = lower.contains("diya") || lower.contains("diye")
                || lower.contains("lend") || lower.contains("lent") || lower.contains("gave");

        if (borrowedSignal && !lentSignal) return "Money borrowed";
        if (lentSignal && !borrowedSignal) return "Money lending";

        if (lower.contains("udhaar") || lower.contains("udhar")) {
            if (borrowedSignal) return "Money borrowed";
            if (lentSignal) return "Money lending";
        }

        return "General Reminder";
    }
    private void addNoteFromAI(JSONObject action, String userMessage) {
        if (auth.getCurrentUser() == null || auth.getCurrentUser().getEmail() == null) {
            showBotMessage("Please log in again before adding notes.");
            return;
        }

        String summary = action != null ? action.optString("summary", action.optString("noteSummary", "")).trim() : "";
        // FIX: previously read action.optString("type", ...) first, which
        // collided with the action's own "type" field (e.g. "add_note") and
        // caused every note to fall through to "General Reminder".
        String rawType = action != null ? action.optString("noteType", "").trim() : "";

        if (summary.isEmpty() && userMessage != null) {
            summary = extractNoteSummaryFromText(userMessage);
        }

        if (summary.isEmpty()) {
            showBotMessage("Please specify what you want to write in the note.");
            return;
        }

        String type = normalizeNoteType(rawType, summary, userMessage);

        long reminderTimeMs = 0;
        boolean isReminderEnabled = false;

        if (action != null && (action.has("reminderDate") || action.has("date") || action.optBoolean("isReminderEnabled", false))) {
            String dateStr = action.optString("reminderDate", action.optString("date", "")).trim();
            String timeStr = action.optString("reminderTime", action.optString("time", "")).trim();
            reminderTimeMs = parseDateTimeToMs(dateStr, timeStr);
            if (reminderTimeMs > System.currentTimeMillis()) {
                isReminderEnabled = true;
            }
        }

        String email = auth.getCurrentUser().getEmail();
        String noteId = db.collection("Users").document(email)
                .collection("Personal Notes").document().getId();

        Note note = new Note(
                noteId,
                email,
                type,
                summary,
                reminderTimeMs,
                isReminderEnabled,
                System.currentTimeMillis()
        );

        db.collection("Users").document(email)
                .collection("Personal Notes").document(noteId)
                .set(note)
                .addOnSuccessListener(aVoid -> {
                    if (note.isReminderEnabled()) {
                        scheduleNoteNotification(note);
                    }
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
                    String reminderText = note.isReminderEnabled() ?
                            "\n⏰ Reminder set for: " + sdf.format(new Date(note.getReminderDate())) : "";

                    String followUp = "";
                    if (!note.isReminderEnabled()) {
                        if ("Money borrowed".equals(type)) {
                            followUp = "\n\n💬 Kab tak lautane hain paise? Bata dein to main reminder laga dunga.";
                        } else if ("Money lending".equals(type)) {
                            followUp = "\n\n💬 Kab tak wapas lene hain paise? Bata dein to main reminder laga dunga.";
                        } else {
                            followUp = "\n\n💬 Kab yaad dilau? Date/time bata dein to reminder laga dunga.";
                        }
                    }

                    showBotMessage("✅ Note added successfully!\n\n"
                            + "Type: " + note.getType() + "\n"
                            + "Note: " + note.getSummary()
                            + reminderText
                            + followUp);
                })
                .addOnFailureListener(e -> showBotMessage("❌ Could not save note: " + safeString(e.getLocalizedMessage())));
    }

    private void readNotesFromAI() {
        if (auth.getCurrentUser() == null || auth.getCurrentUser().getEmail() == null) {
            showBotMessage("Please log in to view your notes.");
            return;
        }

        String email = auth.getCurrentUser().getEmail();
        db.collection("Users").document(email)
                .collection("Personal Notes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Note> notes = queryDocumentSnapshots.toObjects(Note.class);
                    if (notes.isEmpty()) {
                        showBotMessage("📝 You don't have any notes saved yet.");
                        return;
                    }

                    StringBuilder sb = new StringBuilder("📝 Here are your saved notes:\n\n");
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());

                    for (int i = 0; i < notes.size(); i++) {
                        Note n = notes.get(i);
                        String icon = "💰";
                        if ("Money borrowed".equalsIgnoreCase(n.getType())) icon = "💸";
                        else if ("General Reminder".equalsIgnoreCase(n.getType())) icon = "🔔";

                        sb.append(i + 1).append(". ").append(icon).append(" ").append(n.getType()).append("\n")
                                .append("   ").append(n.getSummary()).append("\n")
                                .append("   📅 ").append(sdf.format(new Date(n.getCreatedAt())));

                        if (n.isReminderEnabled() && n.getReminderDate() > 0) {
                            sb.append("\n   ⏰ Remind on: ").append(sdf.format(new Date(n.getReminderDate())));
                        }
                        sb.append("\n\n");
                    }

                    showBotMessage(sb.toString().trim());
                })
                .addOnFailureListener(e -> showBotMessage("❌ Could not fetch notes: " + safeString(e.getLocalizedMessage())));
    }

    private void updateNoteFromAI(JSONObject action, String userMessage) {
        if (auth.getCurrentUser() == null || auth.getCurrentUser().getEmail() == null) {
            showBotMessage("Please log in to update notes.");
            return;
        }

        String email = auth.getCurrentUser().getEmail();
        String targetNoteId = action != null ? action.optString("targetNoteId", action.optString("id", "")).trim() : "";
        String targetSummary = action != null ? action.optString("targetSummary", action.optString("targetText", "")).trim() : "";

        if (targetSummary.isEmpty() && targetNoteId.isEmpty() && userMessage != null) {
            targetSummary = extractTargetSearchTerm(userMessage);
        }

        String newSummary = action != null ? action.optString("newSummary", action.optString("summary", "")).trim() : "";
        // FIX: previously fell back to action.optString("type", ...) which is
        // the action's own type (e.g. "edit_note"), wrongly overwriting the
        // note's category. Now only reads the dedicated "newType" field.
        String rawNewType = action != null ? action.optString("newType", "").trim() : "";

        final String searchKey = targetSummary;

        db.collection("Users").document(email).collection("Personal Notes")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<DocumentSnapshot> matches = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Note n = doc.toObject(Note.class);
                        if (n == null) continue;

                        if (!targetNoteId.isEmpty() && doc.getId().equalsIgnoreCase(targetNoteId)) {
                            matches.add(doc);
                            break;
                        }

                        if (!searchKey.isEmpty()) {
                            if (n.getSummary().toLowerCase(Locale.ROOT).contains(searchKey.toLowerCase(Locale.ROOT))) {
                                matches.add(doc);
                            }
                        }
                    }

                    if (matches.isEmpty()) {
                        showBotMessage("I couldn't find a matching note to update.");
                        return;
                    }

                    if (matches.size() > 1) {
                        showBotMessage("I found multiple matching notes. Please provide more specific text from the note.");
                        return;
                    }

                    DocumentSnapshot targetDoc = matches.get(0);
                    Note existingNote = targetDoc.toObject(Note.class);
                    if (existingNote == null) return;

                    Map<String, Object> updates = new HashMap<>();
                    if (!newSummary.isEmpty()) {
                        updates.put("summary", newSummary);
                        existingNote.setSummary(newSummary);
                    }
                    if (!rawNewType.isEmpty()) {
                        String normalizedType = normalizeNoteType(
                                rawNewType,
                                newSummary.isEmpty() ? existingNote.getSummary() : newSummary,
                                userMessage
                        );
                        updates.put("type", normalizedType);
                        existingNote.setType(normalizedType);
                    }

                    if (action != null && (action.has("reminderDate") || action.has("date"))) {
                        String dateStr = action.optString("reminderDate", action.optString("date", "")).trim();
                        String timeStr = action.optString("reminderTime", action.optString("time", "")).trim();
                        long reminderTimeMs = parseDateTimeToMs(dateStr, timeStr);
                        if (reminderTimeMs > System.currentTimeMillis()) {
                            updates.put("reminderDate", reminderTimeMs);
                            updates.put("isReminderEnabled", true);
                            existingNote.setReminderDate(reminderTimeMs);
                            existingNote.setReminderEnabled(true);
                            scheduleNoteNotification(existingNote);
                        }
                    }

                    if (updates.isEmpty()) {
                        showBotMessage("Please specify what you want to change in the note.");
                        return;
                    }

                    targetDoc.getReference().update(updates)
                            .addOnSuccessListener(unused -> {
                                String reminderMsg = "";
                                if (updates.containsKey("reminderDate")) {
                                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
                                    reminderMsg = "\n⏰ Reminder set for: "
                                            + sdf.format(new Date((Long) updates.get("reminderDate")));
                                }
                                showBotMessage("✅ Note updated successfully!" + reminderMsg);
                            })
                            .addOnFailureListener(e -> showBotMessage("❌ Update failed: " + safeString(e.getLocalizedMessage())));
                })
                .addOnFailureListener(e -> showBotMessage("❌ Could not search notes: " + safeString(e.getLocalizedMessage())));
    }

    private void deleteNoteFromAI(JSONObject action, String userMessage) {
        if (auth.getCurrentUser() == null || auth.getCurrentUser().getEmail() == null) {
            showBotMessage("Please log in to delete notes.");
            return;
        }

        String email = auth.getCurrentUser().getEmail();
        String targetNoteId = action != null ? action.optString("targetNoteId", action.optString("id", "")).trim() : "";
        String targetSummary = action != null ? action.optString("targetSummary", action.optString("targetText", "")).trim() : "";

        if (targetSummary.isEmpty() && targetNoteId.isEmpty() && userMessage != null) {
            targetSummary = extractTargetSearchTerm(userMessage);
        }

        final String finalSearchTerm = targetSummary;

        db.collection("Users").document(email).collection("Personal Notes")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<DocumentSnapshot> matches = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Note n = doc.toObject(Note.class);
                        if (n == null) continue;

                        if (!targetNoteId.isEmpty() && doc.getId().equalsIgnoreCase(targetNoteId)) {
                            matches.add(doc);
                            break;
                        }

                        if (!finalSearchTerm.isEmpty()) {
                            if (n.getSummary().toLowerCase(Locale.ROOT).contains(finalSearchTerm.toLowerCase(Locale.ROOT))) {
                                matches.add(doc);
                            }
                        }
                    }

                    if (matches.isEmpty()) {
                        showBotMessage("I couldn't find a matching note to delete.");
                        return;
                    }

                    if (matches.size() > 1) {
                        showBotMessage("Multiple notes match that description. Please specify the exact note text to delete.");
                        return;
                    }

                    DocumentSnapshot targetDoc = matches.get(0);
                    cancelNoteNotification(targetDoc.getId());

                    targetDoc.getReference().delete()
                            .addOnSuccessListener(unused -> showBotMessage("✅ Note deleted successfully."))
                            .addOnFailureListener(e -> showBotMessage("❌ Delete failed: " + safeString(e.getLocalizedMessage())));
                })
                .addOnFailureListener(e -> showBotMessage("❌ Could not search notes: " + safeString(e.getLocalizedMessage())));
    }

    private void scheduleNoteNotification(Note note) {
        if (!note.isReminderEnabled() || note.getReminderDate() <= 0) return;

        Intent intent = new Intent(this, NoteReminderReceiver.class);
        intent.putExtra("title", "Reminder: " + note.getType());
        intent.putExtra("message", note.getSummary());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, note.getId().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
                }
            } catch (SecurityException e) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, note.getReminderDate(), pendingIntent);
            }
        }
    }

    private void cancelNoteNotification(String noteId) {
        if (noteId == null) return;
        Intent intent = new Intent(this, NoteReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, noteId.hashCode(), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
            pendingIntent.cancel();
        }
    }

    private long parseDateTimeToMs(String dateStr, String timeStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return 0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH);
            Date date = sdf.parse(dateStr.trim());
            if (date == null) return 0;

            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            if (timeStr != null && !timeStr.trim().isEmpty()) {
                String[] parts = timeStr.trim().split(":");
                if (parts.length >= 2) {
                    int hour = Integer.parseInt(parts[0].trim());
                    int min = Integer.parseInt(parts[1].trim());
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, min);
                    cal.set(Calendar.SECOND, 0);
                }
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 9);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
            }

            return cal.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractNoteSummaryFromText(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("(?i)^(add note|save note|create note|note add|note save|note banao|note likho)[:\\s]*", "").trim();
        return clean.isEmpty() ? text : clean;
    }

    private String extractTargetSearchTerm(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("(?i)^(delete note|remove note|update note|edit note|note delete|note remove|note update|note hatao|note badlo)[:\\s]*", "").trim();
        return clean.isEmpty() ? text : clean;
    }

    private String createTransactionId(String date, String time) {
        return date + " " + time + " " + System.currentTimeMillis();
    }

    private void addTransactionFromAI(JSONObject action) {
        try {
            if (!action.has("amount") || action.isNull("amount")) {
                showBotMessage("Please provide the transaction amount.");
                return;
            }

            double amount = action.getDouble("amount");
            String type = action.optString("transactionType", "").trim();
            String date = action.optString("date", "").trim();
            String note = action.optString("note", "").trim();

            if (!Double.isFinite(amount) || amount <= 0) {
                showBotMessage("The amount must be greater than zero.");
                return;
            }

            if (!"Income".equals(type) && !"Expense".equals(type)) {
                showBotMessage("Please specify whether this is Income or Expense.");
                return;
            }

            if (parseDate(date) == null) {
                showBotMessage("A valid transaction date is required (DD-MM-YYYY).");
                return;
            }

            String email = auth.getCurrentUser().getEmail();
            String month = monthFromDate(date);
            String time = currentTime();
            String transactionId = createTransactionId(date, time);

            Map<String, Object> transaction = new HashMap<>();
            transaction.put("type", type);
            transaction.put("amount", amount);
            transaction.put("note", note);
            transaction.put("month", month);
            transaction.put("date", date);
            transaction.put("time", time);
            transaction.put("transactionId", transactionId);

            monthCollection(email, month).document(transactionId)
                    .set(transaction)
                    .addOnSuccessListener(unused ->
                            completeActionAndExit(
                                    "✅ Transaction saved successfully!\n\n"
                                            + "Type: " + type + "\n"
                                            + "Amount: " + money(amount) + "\n"
                                            + "Note: " + (note.isEmpty() ? "—" : note) + "\n"
                                            + "Date: " + date + "\n"
                                            + "Time: " + time + "\n"
                                            + "Transaction ID: " + transactionId
                            ))
                    .addOnFailureListener(error ->
                            showBotMessage("❌ Transaction could not be saved.\n"
                                    + safeString(error.getLocalizedMessage())));
        } catch (Exception e) {
            Log.e(TAG, "Failed to add transaction", e);
            showBotMessage(
                    "I couldn't add this transaction. Please check its amount, type, and date."
            );
        }
    }

    private void findAndModifyTransaction(JSONObject action, boolean delete) {
        if (auth.getCurrentUser() == null
                || auth.getCurrentUser().getEmail() == null) {
            showBotMessage("Please log in again.");
            return;
        }

        String email = auth.getCurrentUser().getEmail();
        String targetDate = action.optString("targetDate", "").trim();
        String targetNote = action.optString("targetNote", "").trim();
        String targetType = action.optString("targetType", "").trim();
        String targetTime = action.optString("targetTime", "").trim();

        boolean hasTargetAmount = action.has("targetAmount")
                && !action.isNull("targetAmount")
                && action.opt("targetAmount") instanceof Number;
        double targetAmount = hasTargetAmount
                ? action.optDouble("targetAmount", -1) : -1;

        if (targetDate.isEmpty() && targetNote.isEmpty()
                && targetType.isEmpty() && targetTime.isEmpty()
                && !hasTargetAmount) {
            showBotMessage(
                    "I need identifying details to find the transaction. "
                            + "Please provide its date, amount, note, or type."
            );
            return;
        }

        if (!targetDate.isEmpty() && parseDate(targetDate) == null) {
            showBotMessage("The target date must use DD-MM-YYYY format.");
            return;
        }

        List<Task<?>> tasks = new ArrayList<>();
        for (String month : months) tasks.add(monthCollection(email, month).get());

        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    List<DocumentSnapshot> matches = new ArrayList<>();

                    for (Object result : results) {
                        QuerySnapshot snapshot = (QuerySnapshot) result;
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            boolean match = true;

                            if (!targetDate.isEmpty()) {
                                match &= targetDate.equals(
                                        safeString(doc.getString("date")));
                            }
                            if (hasTargetAmount) {
                                Object value = doc.get("amount");
                                match &= value instanceof Number
                                        && Math.abs(((Number) value).doubleValue()
                                        - targetAmount) < 0.001;
                            }
                            if (!targetNote.isEmpty()) {
                                match &= targetNote.equalsIgnoreCase(
                                        safeString(doc.getString("note")));
                            }
                            if (!targetType.isEmpty()) {
                                match &= targetType.equalsIgnoreCase(
                                        safeString(doc.getString("type")));
                            }
                            if (!targetTime.isEmpty()) {
                                match &= targetTime.equalsIgnoreCase(
                                        safeString(doc.getString("time")));
                            }

                            if (match) matches.add(doc);
                        }
                    }

                    if (matches.isEmpty()) {
                        showBotMessage(
                                "I couldn't find a matching transaction. "
                                        + "Please check the date, amount, note, or type."
                        );
                        return;
                    }

                    if (matches.size() > 1) {
                        showBotMessage(
                                "I found multiple matching transactions, so I haven't changed anything. "
                                        + "Please provide more details, such as the exact date, amount, note, or time."
                        );
                        return;
                    }

                    DocumentSnapshot target = matches.get(0);
                    if (delete) {
                        target.getReference().delete()
                                .addOnSuccessListener(unused ->
                                        completeActionAndExit("✅ Transaction deleted successfully."))
                                .addOnFailureListener(error ->
                                        showBotMessage("❌ Delete failed: "
                                                + safeString(error.getLocalizedMessage())));
                    } else {
                        updateTransactionFromAI(target, action, email);
                    }
                })
                .addOnFailureListener(error ->
                        showBotMessage("Couldn't search your transactions: "
                                + safeString(error.getLocalizedMessage())));
    }

    private void updateTransactionFromAI(
            DocumentSnapshot target,
            JSONObject action,
            String email
    ) {
        try {
            Map<String, Object> updated = new HashMap<>();

            if (action.has("amount") && !action.isNull("amount")) {
                Object amountObject = action.get("amount");
                if (!(amountObject instanceof Number)) {
                    showBotMessage("The new amount is invalid.");
                    return;
                }

                double amount = ((Number) amountObject).doubleValue();
                if (!Double.isFinite(amount) || amount <= 0) {
                    showBotMessage("The new amount must be greater than zero.");
                    return;
                }
                updated.put("amount", amount);
            }

            if (action.has("transactionType") && !action.isNull("transactionType")) {
                String newType = action.getString("transactionType").trim();
                if (!"Income".equals(newType) && !"Expense".equals(newType)) {
                    showBotMessage("The transaction type must be Income or Expense.");
                    return;
                }
                updated.put("type", newType);
            }

            if (action.has("note") && !action.isNull("note")) {
                updated.put("note", action.getString("note").trim());
            }

            String oldDate = safeString(target.getString("date"));
            String newDate = oldDate;

            if (action.has("date") && !action.isNull("date")) {
                newDate = action.getString("date").trim();
                if (parseDate(newDate) == null) {
                    showBotMessage("Please use DD-MM-YYYY for the new date.");
                    return;
                }
                updated.put("date", newDate);
            }

            if (action.has("time") && !action.isNull("time")) {
                String newTime = action.getString("time").trim();
                if (newTime.isEmpty()) {
                    showBotMessage("The new time cannot be empty.");
                    return;
                }
                updated.put("time", newTime);
            }

            if (updated.isEmpty()) {
                showBotMessage("Please tell me what you want to change.");
                return;
            }

            String newMonth = monthFromDate(newDate);
            if (newMonth.isEmpty()) {
                showBotMessage("I couldn't determine the month for this transaction.");
                return;
            }

            String oldMonth = safeString(target.getString("month"));
            if (oldMonth.isEmpty()) oldMonth = monthFromDate(oldDate);
            updated.put("month", newMonth);

            if (!oldMonth.equalsIgnoreCase(newMonth)) {
                moveTransactionToAnotherMonth(target, updated, email, newMonth);
                return;
            }

            target.getReference().update(updated)
                    .addOnSuccessListener(unused ->
                            completeActionAndExit("✅ Transaction updated successfully."))
                    .addOnFailureListener(error ->
                            showBotMessage("❌ Update failed: "
                                    + safeString(error.getLocalizedMessage())));
        } catch (Exception e) {
            Log.e(TAG, "Failed to update transaction", e);
            showBotMessage("I couldn't apply the requested changes.");
        }
    }

    private void moveTransactionToAnotherMonth(
            DocumentSnapshot target,
            Map<String, Object> updates,
            String email,
            String newMonth
    ) {
        Map<String, Object> movedData = new HashMap<>();
        if (target.getData() != null) movedData.putAll(target.getData());
        movedData.putAll(updates);
        movedData.put("month", newMonth);

        String transactionId = target.getId();
        com.google.firebase.firestore.DocumentReference destination =
                monthCollection(email, newMonth).document(transactionId);

        destination.get()
                .addOnSuccessListener(existing -> {
                    if (existing.exists()) {
                        showBotMessage(
                                "⚠️ A transaction with the same ID already exists in "
                                        + newMonth + ". No record was moved."
                        );
                        return;
                    }

                    destination.set(movedData)
                            .addOnSuccessListener(unused ->
                                    target.getReference().delete()
                                            .addOnSuccessListener(deleted ->
                                                    completeActionAndExit(
                                                            "✅ Transaction updated and moved to "
                                                                    + newMonth + "."
                                                    ))
                                            .addOnFailureListener(error ->
                                                    showBotMessage(
                                                            "⚠️ The updated record was saved in "
                                                                    + newMonth + ", but the old record "
                                                                    + "could not be removed. Check for duplicates."
                                                    )))
                            .addOnFailureListener(error ->
                                    showBotMessage(
                                            "❌ The transaction could not be moved. "
                                                    + "The original record has been kept.\n"
                                                    + safeString(error.getLocalizedMessage())
                                    ));
                })
                .addOnFailureListener(error ->
                        showBotMessage("Couldn't verify the destination record: "
                                + safeString(error.getLocalizedMessage())));
    }
}