package com.example.gpayexpensetracker.data

import androidx.lifecycle.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

interface DataRepository {
    val allTransactions: Flow<List<TransactionEntity>>
    suspend fun insert(transaction: TransactionEntity)
    suspend fun insertAll(transactions: List<TransactionEntity>)
    suspend fun replaceAll(transactions: List<TransactionEntity>)
    suspend fun delete(transaction: TransactionEntity)
    suspend fun deleteById(id: Long)
    suspend fun update(transaction: TransactionEntity)
    fun getTotalSpentBetween(startTime: Long, endTime: Long): Flow<Double?>
}

class DefaultDataRepository(private val transactionDao: TransactionDao) : DataRepository {
    override val allTransactions: Flow<List<TransactionEntity>> = transactionDao.allTransactions.asFlow()

    override suspend fun insert(transaction: TransactionEntity) {
        withContext(Dispatchers.IO) {
            transactionDao.insertTransaction(transaction)
        }
    }

    override suspend fun insertAll(transactions: List<TransactionEntity>) {
        withContext(Dispatchers.IO) {
            transactionDao.insertAllTransactions(transactions)
        }
    }

    override suspend fun replaceAll(transactions: List<TransactionEntity>) {
        withContext(Dispatchers.IO) {
            transactionDao.deleteAllTransactions()
            transactionDao.insertAllTransactions(transactions)
        }
    }

    override suspend fun delete(transaction: TransactionEntity) {
        withContext(Dispatchers.IO) {
            transactionDao.deleteTransaction(transaction)
        }
    }

    override suspend fun deleteById(id: Long) {
        withContext(Dispatchers.IO) {
            transactionDao.deleteTransactionById(id)
        }
    }

    override suspend fun update(transaction: TransactionEntity) {
        withContext(Dispatchers.IO) {
            transactionDao.updateTransaction(transaction)
        }
    }

    override fun getTotalSpentBetween(startTime: Long, endTime: Long): Flow<Double?> {
        return flow {
            allTransactions.collect { list ->
                val sum = list.filter { it.timestamp in startTime..endTime }.sumOf { it.amount }
                emit(sum)
            }
        }
    }
}
