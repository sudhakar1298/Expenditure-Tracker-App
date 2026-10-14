package com.example.gpayexpensetracker.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    LiveData<List<TransactionEntity>> getAllTransactions();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertTransaction(TransactionEntity transaction);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAllTransactions(List<TransactionEntity> transactions);

    @Update
    void updateTransaction(TransactionEntity transaction);

    @Delete
    void deleteTransaction(TransactionEntity transaction);

    @Query("DELETE FROM transactions WHERE id = :id")
    void deleteTransactionById(long id);

    @Query("DELETE FROM transactions")
    void deleteAllTransactions();

    @Query("DELETE FROM transactions WHERE isPending = 1")
    void deletePendingTransactions();
}
