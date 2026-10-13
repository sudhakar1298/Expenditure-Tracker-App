package com.example.gpayexpensetracker.service

import com.example.gpayexpensetracker.ui.main.resolveManualMerchant
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualExpenseInputTest {
    @Test
    fun emptyOptionalFieldsUseSafeDescription() {
        assertEquals("Manual expense", resolveManualMerchant("", ""))
    }

    @Test
    fun merchantOrReasonCanDescribeExpense() {
        assertEquals("Local shop", resolveManualMerchant("  Local shop  ", ""))
        assertEquals("Lunch with team", resolveManualMerchant("", "  Lunch with team  "))
    }
}
