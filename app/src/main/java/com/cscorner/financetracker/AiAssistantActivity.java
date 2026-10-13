package com.cscorner.financetracker;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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

        // Present the assistant as an in-app floating chat window, not a full-screen page.
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int popupWidth = (int) (displayMetrics.widthPixels * 0.94f);
        int popupHeight = (int) (displayMetrics.heightPixels * 0.80f);
        getWindow().setLayout(popupWidth, popupHeight);
        getWindow().setDimAmount(0.45f);

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

        loadChatHistory();
    }

    @Override
    public void finish() {
        super.finish();
        // Smooth exit transition for the popup-style chat window
        overridePendingTransition(0, R.anim.popup_exit);
    }

    // -------------------- CHAT STORAGE --------------------

    /*
     * Reliable, queryable chat schema:
     * Users/{email}/AI Chat History/Messages/{messageId}
     *
     * Each message stores role, content, timestamp, month and date.
     * A fixed "Messages" collection lets Android query and clear messages.
     * Firestore Android SDK cannot enumerate arbitrary subcollections, so
     * date-named subcollections cannot be discovered automatically.
     */
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
                        + "and help you add, edit, or delete transactions.\n\n"
                        + "Try asking:\n"
                        + "• What is my overall income?\n"
                        + "• Give me my monthly summary\n"
                        + "• How much did I spend today?\n"
                        + "• Compare this month with last month\n"
                        + "• Give me saving tips\n"
                        + "• Add an expense of ₹100 today for groceries",
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
                callAI(message, buildFinanceSummary(records), loadingView);
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

    private void callAI(String message, String financeSummary, View loadingView) {
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
                                executeTransactionAction(action, reply);
                            } else {
                                showBotMessage(reply);
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

    /**
     * Animated "typing..." bubble shown while the AI/finance records are loading.
     * Replaces the old plain-text "Checking your financial records..." message.
     */
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

    private void scrollToBottom() {
        if (chatScrollView != null) {
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        }
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

    /** Simple press-feedback bounce animation for buttons. */
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

    // Close AiBot after a confirmed successful transaction action.
    // If opened from the launcher, Android returns to the home screen; if opened
    // from another app screen, it returns to that previous screen.
    private void completeActionAndExit(String successMessage) {
        showBotMessage(successMessage);
        Toast.makeText(this, "Transaction successful!", Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, 900);
    }

    // -------------------- TRANSACTION ACTIONS --------------------

    private void executeTransactionAction(JSONObject action, String reply) {
        String type = action.optString("type", "none");

        if (auth.getCurrentUser() == null
                || auth.getCurrentUser().getEmail() == null) {
            showBotMessage("Please log in again before changing transactions.");
            return;
        }

        switch (type) {
            case "add_transaction":
                addTransactionFromAI(action);
                break;
            case "edit_transaction":
                findAndModifyTransaction(action, false);
                break;
            case "delete_transaction":
                findAndModifyTransaction(action, true);
                break;
            default:
                showBotMessage(reply);
                break;
        }
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

        // Preserve the document ID, but avoid overwriting a destination record.
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