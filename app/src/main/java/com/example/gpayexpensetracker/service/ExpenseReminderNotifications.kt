package com.example.gpayexpensetracker.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.gpayexpensetracker.MainActivity
import com.example.gpayexpensetracker.R
import com.example.gpayexpensetracker.data.DefaultDataRepository
import com.example.gpayexpensetracker.data.TransactionDatabase
import com.example.gpayexpensetracker.data.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

object ExpenseReminderNotifications {
    private const val CHANNEL_ID = "pending_expense_reminders"
    private const val REMINDER_NOTIFICATION_ID = 9001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Expense reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders to add a reason and confirm pending expenses"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showNewExpense(context: Context, transaction: TransactionEntity) {
        show(
            context,
            "Add details for this expense",
            "₹%,.2f needs a reason and category. Tap to review it.".format(transaction.amount),
            transaction.timestamp.hashCode()
        )
    }

    fun showPendingReminder(context: Context, count: Int) {
        if (count <= 0) return
        val noun = if (count == 1) "expense needs" else "expenses need"
        show(
            context,
            "Pending expense reminder",
            "$count $noun a reason. Tap to finish categorizing.",
            REMINDER_NOTIFICATION_ID
        )
    }

    private fun show(context: Context, title: String, message: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}

object PendingReminderScheduler {
    private val reminderHours = intArrayOf(12, 16, 21)

    fun scheduleAll(context: Context) {
        reminderHours.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, hour: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PendingExpenseReminderReceiver::class.java)
            .putExtra(PendingExpenseReminderReceiver.EXTRA_HOUR, hour)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            hour,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextReminderTime(System.currentTimeMillis(), hour),
            pendingIntent
        )
    }
}

fun nextReminderTime(nowMillis: Long, hour: Int): Long {
    val next = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
    }
    return next.timeInMillis
}

class PendingExpenseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(EXTRA_HOUR, 21)
        PendingReminderScheduler.schedule(context.applicationContext, hour)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val repository = DefaultDataRepository(
                    TransactionDatabase.getDatabase(context.applicationContext).transactionDao
                )
                val count = repository.allTransactions.first().count { it.isPending }
                ExpenseReminderNotifications.showPendingReminder(context.applicationContext, count)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_HOUR = "reminder_hour"
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PendingReminderScheduler.scheduleAll(context.applicationContext)
        }
    }
}
