package com.example.gpayexpensetracker.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

@Entity(tableName = "transactions")
public class TransactionEntity {
    @PrimaryKey(autoGenerate = true)
    private long id = 0;
    
    private double amount;
    private String merchant;
    private String category;
    private long timestamp;
    private String sourceApp;
    private String rawText;
    private boolean isPending = false;

    // Default Constructor for Room
    public TransactionEntity() {}

    // Constructor with arguments (defaults to isPending = false)
    @Ignore
    public TransactionEntity(double amount, String merchant, String category, long timestamp, String sourceApp, String rawText) {
        this(amount, merchant, category, timestamp, sourceApp, rawText, false);
    }

    // Constructor with arguments including isPending
    @Ignore
    public TransactionEntity(double amount, String merchant, String category, long timestamp, String sourceApp, String rawText, boolean isPending) {
        this.amount = amount;
        this.merchant = merchant;
        this.category = category;
        this.timestamp = timestamp;
        this.sourceApp = sourceApp;
        this.rawText = rawText;
        this.isPending = isPending;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    
    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getSourceApp() { return sourceApp; }
    public void setSourceApp(String sourceApp) { this.sourceApp = sourceApp; }
    
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public boolean isPending() { return isPending; }
    public void setPending(boolean pending) { isPending = pending; }
}
