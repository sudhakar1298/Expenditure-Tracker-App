package com.example.gpayexpensetracker.data

object ExpenseCategories {
    const val FOOD = "Food & Dining"
    const val GROCERIES = "Groceries"
    const val TRANSPORT = "Transport"
    const val SHOPPING = "Shopping"
    const val BILLS = "Bills & Utilities"
    const val ENTERTAINMENT = "Entertainment"
    const val HEALTH = "Health"
    const val EDUCATION = "Education"
    const val RENT = "Rent & Housing"
    const val TRAVEL = "Travel"
    const val OTHER = "Other"

    val all = listOf(
        FOOD, GROCERIES, TRANSPORT, SHOPPING, BILLS, ENTERTAINMENT,
        HEALTH, EDUCATION, RENT, TRAVEL, OTHER
    )

    fun normalize(category: String?): String = when (category?.trim()) {
        "Food", FOOD -> FOOD
        GROCERIES -> GROCERIES
        "Travel & Cab", TRANSPORT -> TRANSPORT
        "Bills", "Utility Bills", BILLS -> BILLS
        "Others", OTHER, null, "" -> OTHER
        else -> category.trim()
    }

    fun suggest(description: String): String {
        val text = description.lowercase()
        return when {
            has(text, "restaurant", "cafe", "coffee", "tea", "swiggy", "zomato", "pizza", "burger", "ramen", "lunch", "dinner", "breakfast", "bakery", "canteen", "food", "snack", "sweet") -> FOOD
            has(text, "grocery", "groceries", "supermarket", "vegetable", "milk", "fruit", "mart") -> GROCERIES
            has(text, "uber", "ola", "rapido", "metro", "auto", "cab", "taxi", "bus", "petrol", "fuel", "fastag", "toll") -> TRANSPORT
            has(text, "amazon", "flipkart", "myntra", "shopping", "clothing", "apparel", "retail", "store", "mall") -> SHOPPING
            has(text, "electricity", "recharge", "airtel", "jio", "broadband", "water bill", "gas bill", "insurance", "dth") -> BILLS
            has(text, "netflix", "prime", "hotstar", "spotify", "bookmyshow", "cinema", "movie", "gaming", "steam", "club", "pub") -> ENTERTAINMENT
            has(text, "hospital", "clinic", "doctor", "medicine", "medical", "pharmacy", "health") -> HEALTH
            has(text, "school", "college", "course", "udemy", "book", "tuition", "exam", "education") -> EDUCATION
            has(text, "rent", "housing", "hostel", "pg ", "maintenance") -> RENT
            has(text, "flight", "hotel", "railway", "irctc", "trip", "travel") -> TRAVEL
            else -> OTHER
        }
    }

    private fun has(text: String, vararg keywords: String) = keywords.any(text::contains)
}
