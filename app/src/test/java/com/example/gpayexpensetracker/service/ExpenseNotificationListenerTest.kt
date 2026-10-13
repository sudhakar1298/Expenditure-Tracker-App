package com.example.gpayexpensetracker.service

import org.junit.Assert.*
import org.junit.Test

class ExpenseNotificationListenerTest {

    @Test
    fun testParseTransaction_PaidRupeeSymbol() {
        val rawText = "Paid ₹150 to Ramen Shop"
        val transaction = ExpenseNotificationListener.parseTransactionText(rawText)
        
        assertNotNull(transaction)
        assertEquals(150.0, transaction!!.amount, 0.0)
        assertEquals("Ramen Shop", transaction.merchant)
        assertEquals("Food & Dining", transaction.category)
        assertEquals("GPay", transaction.sourceApp)
        assertTrue(transaction.isPending)
    }

    @Test
    fun testParseTransaction_YouSentRs() {
        val rawText = "You sent Rs. 500 to Ramesh"
        val transaction = ExpenseNotificationListener.parseTransactionText(rawText)
        
        assertNotNull(transaction)
        assertEquals(500.0, transaction!!.amount, 0.0)
        assertEquals("Ramesh", transaction.merchant)
        assertEquals("Other", transaction.category)
    }

    @Test
    fun testParseTransaction_YouPaidWithCommas() {
        val rawText = "You paid ₹2,500.50 to Bescom Electricity"
        val transaction = ExpenseNotificationListener.parseTransactionText(rawText)
        
        assertNotNull(transaction)
        assertEquals(2500.50, transaction!!.amount, 0.0)
        assertEquals("Bescom Electricity", transaction.merchant)
        assertEquals("Bills & Utilities", transaction.category)
    }

    @Test
    fun testParseTransaction_ReceivedText_ShouldBeNull() {
        val rawText = "Received ₹100 from Mom"
        val transaction = ExpenseNotificationListener.parseTransactionText(rawText)
        
        assertNull(transaction)
    }

    @Test
    fun testParseTransaction_RandomNotification_ShouldBeNull() {
        val rawText = "Your order from Amazon is shipped"
        val transaction = ExpenseNotificationListener.parseTransactionText(rawText)
        
        assertNull(transaction)
    }

    @Test
    fun testCategorizeMerchant() {
        // We test the private categorization logic via parsing
        val foodTrans = ExpenseNotificationListener.parseTransactionText("Paid ₹50 to Swiggy")
        assertEquals("Food & Dining", foodTrans?.category)

        val travelTrans = ExpenseNotificationListener.parseTransactionText("Paid ₹250 to Uber")
        assertEquals("Transport", travelTrans?.category)

        val shoppingTrans = ExpenseNotificationListener.parseTransactionText("Paid ₹1200 to Amazon Store")
        assertEquals("Shopping", shoppingTrans?.category)

        val billsTrans = ExpenseNotificationListener.parseTransactionText("Paid ₹800 to Jio Recharge")
        assertEquals("Bills & Utilities", billsTrans?.category)

        val entTrans = ExpenseNotificationListener.parseTransactionText("Paid ₹199 to Netflix India")
        assertEquals("Entertainment", entTrans?.category)
    }
}
