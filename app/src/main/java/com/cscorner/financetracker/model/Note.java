package com.cscorner.financetracker.model;

public class Note {
    private String id;
    private String userId;
    private String type;
    private String summary;
    private long reminderDate;
    private boolean isReminderEnabled;
    private long createdAt;

    public Note() {}

    public Note(String id, String userId, String type, String summary, long reminderDate, boolean isReminderEnabled, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.summary = summary;
        this.reminderDate = reminderDate;
        this.isReminderEnabled = isReminderEnabled;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public long getReminderDate() { return reminderDate; }
    public void setReminderDate(long reminderDate) { this.reminderDate = reminderDate; }

    public boolean isReminderEnabled() { return isReminderEnabled; }
    public void setReminderEnabled(boolean reminderEnabled) { isReminderEnabled = reminderEnabled; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
