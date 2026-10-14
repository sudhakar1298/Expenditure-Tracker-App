package com.example.gpayexpensetracker.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ReminderScheduleTest {
    @Test
    fun nextReminderUsesTodayWhenTimeHasNotPassed() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 14, 11, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expected = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }

        assertEquals(expected.timeInMillis, nextReminderTime(now.timeInMillis, 12))
    }

    @Test
    fun nextReminderMovesToTomorrowAfterTimePassed() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 14, 21, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expected = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
        }

        assertEquals(expected.timeInMillis, nextReminderTime(now.timeInMillis, 21))
    }
}
