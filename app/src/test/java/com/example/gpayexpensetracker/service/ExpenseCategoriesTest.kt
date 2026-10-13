package com.example.gpayexpensetracker.service

import com.example.gpayexpensetracker.data.ExpenseCategories
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseCategoriesTest {
    @Test
    fun normalizesLegacyCategories() {
        assertEquals(ExpenseCategories.FOOD, ExpenseCategories.normalize("Food"))
        assertEquals(ExpenseCategories.TRANSPORT, ExpenseCategories.normalize("Travel & Cab"))
        assertEquals(ExpenseCategories.BILLS, ExpenseCategories.normalize("Bills"))
        assertEquals(ExpenseCategories.OTHER, ExpenseCategories.normalize("Others"))
    }

    @Test
    fun suggestsUsefulCategoriesFromDescriptions() {
        assertEquals(ExpenseCategories.FOOD, ExpenseCategories.suggest("Dinner from Swiggy"))
        assertEquals(ExpenseCategories.TRANSPORT, ExpenseCategories.suggest("Uber trip"))
        assertEquals(ExpenseCategories.HEALTH, ExpenseCategories.suggest("Apollo pharmacy"))
        assertEquals(ExpenseCategories.RENT, ExpenseCategories.suggest("Monthly house rent"))
    }
}
