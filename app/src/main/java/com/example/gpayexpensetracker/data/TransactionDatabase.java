package com.example.gpayexpensetracker.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {TransactionEntity.class}, version = 2, exportSchema = false)
public abstract class TransactionDatabase extends RoomDatabase {
    public abstract TransactionDao getTransactionDao();

    private static volatile TransactionDatabase INSTANCE = null;

    public static TransactionDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (TransactionDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        TransactionDatabase.class,
                        "gpay_expense_tracker_db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
