package com.example.gpayexpensetracker.service

import org.junit.Assert.*
import org.junit.Test

class SmsReceiverTest {

    @Test
    fun testParseSms_ExactUserFormat() {
        val smsText = "A/c *5276 debited Rs. 78.00 on 02-06-26 to CHITHRA AGEN. UPI:615300893676. Not you? SMS BLOCK to 9289592895, Dial 1930 for Cyber Fraud - Indian Bank"
        val transaction = SmsReceiver.parseSmsText(smsText)
        
        assertNotNull("Transaction should not be null", transaction)
        assertEquals(78.00, transaction!!.amount, 0.0)
        assertEquals("CHITHRA AGEN", transaction.merchant)
        assertEquals("Others", transaction.category) // Always defaults to Others for confirmation
        assertEquals("SMS", transaction.sourceApp)
        assertTrue(transaction.isPending) // Must be pending details
    }

    @Test
    fun testParseSms_IndianBankFormat2() {
        val smsText = "Your A/c *5276 is debited for INR 1,250.50 to AMAZON PAY on 02-06-26."
        val transaction = SmsReceiver.parseSmsText(smsText)
        
        assertNotNull("Transaction should not be null", transaction)
        assertEquals(1250.50, transaction!!.amount, 0.0)
        assertEquals("AMAZON PAY", transaction.merchant)
        assertTrue(transaction.isPending)
    }

    @Test
    fun testParseSms_CreditSms_ShouldBeNull() {
        val smsText = "Dear Customer, A/c *5276 is credited for Rs 5,000.00 by salary."
        val transaction = SmsReceiver.parseSmsText(smsText)
        
        assertNull("Credit SMS should be ignored", transaction)
    }

    @Test
    fun testParseSms_OTP_ShouldBeNull() {
        val smsText = "OTP for transaction on your Indian Bank card is 123456. Do not share."
        val transaction = SmsReceiver.parseSmsText(smsText)
        
        assertNull("OTP SMS should be ignored", transaction)
    }
}
