package com.example.moneymate.ui.screens.wallet

import com.example.domain.transaction.model.TransactionEntity

// Format card number
fun formatCardNumber(cardNumber: String?): String {
    if (cardNumber.isNullOrEmpty()) return "****  ****  ****  ****"
    return cardNumber.chunked(4).joinToString("  ")
}

// Get card type display
fun getCardTypeDisplay(walletType: String): String {
    return when (walletType) {
        "debit_card", "credit_card" -> "VISA"
        "cash" -> "CASH"
        "digital" -> "DIGITAL"
        else -> walletType.replace("_", " ").uppercase()
    }
}

// Get expiry display
fun getExpiryDisplay(walletType: String): String {
    return when (walletType) {
        "debit_card", "credit_card" -> "09/25" // You can make this dynamic later
        else -> "-"
    }
}

// Get currency name
fun getCurrencyName(currencyCode: String): String {
    return when (com.example.moneymate.utils.CurrencyUtils.parseCurrencyCode(currencyCode)) {
        "YER" -> "Yemeni Rial"
        "SAR" -> "Saudi Riyal"
        "USD" -> "US Dollar"
        "EUR" -> "Euro"
        "GBP" -> "British Pound"
        "JPY" -> "Japanese Yen"
        "CAD" -> "Canadian Dollar"
        "AUD" -> "Australian Dollar"
        "CHF" -> "Swiss Franc"
        "CNY" -> "Chinese Yuan"
        "INR" -> "Indian Rupee"
        "RUB" -> "Russian Ruble"
        "BRL" -> "Brazilian Real"
        "MXN" -> "Mexican Peso"
        "KRW" -> "South Korean Won"
        else -> currencyCode
    }
}

// Format wallet type
fun formatWalletType(walletType: String): String {
    return when (walletType) {
        "debit_card" -> "Debit Card"
        "credit_card" -> "Credit Card"
        "cash" -> "Cash"
        "digital" -> "Digital Wallet"
        else -> walletType.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}

// Format created date
fun formatCreatedDate(createdAt: String): String {
    return try {
        if (createdAt.contains("T")) {
            createdAt.substring(0, 10) // Show YYYY-MM-DD
        } else {
            createdAt
        }
    } catch (e: Exception) {
        createdAt
    }
}

// Calculate income and expense totals from transactions
fun calculateIncomeExpense(transactions: List<TransactionEntity>): Pair<Double, Double> {
    var income = 0.0
    var expense = 0.0

    transactions.forEach { transaction ->
        val amount = transaction.amount.toDoubleOrNull() ?: 0.0
        when (transaction.type.trim().lowercase()) {
            "income" -> income += amount
            "expense" -> expense += amount
            // Note: transfer transactions might need special handling
        }
    }

    return Pair(income, expense)
}