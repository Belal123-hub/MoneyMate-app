package com.example.moneymate.utils

object CurrencyUtils {

    /** Options shown in profile / signup currency pickers (`CODE - symbol`). */
    /** ISO code to picker label for wallet create/edit dropdowns. */
    val walletCurrencyOptions: List<Pair<String, String>> by lazy {
        profileCurrencyOptions.map { label ->
            parseCurrencyCode(label) to label
        }
    }

    val profileCurrencyOptions: List<String> = listOf(
        "YER - ﷼",
        "SAR - ﷼",
        "USD - $",
        "EUR - €",
        "GBP - £",
        "JPY - ¥",
        "CAD - C$",
        "AUD - A$",
        "CHF - CHF",
        "CNY - ¥",
        "INR - ₹",
        "RUB - ₽",
        "BRL - R$",
        "MXN - $",
        "KRW - ₩"
    )

    /**
     * ISO code from API value (`YER`) or picker label (`YER - ﷼`).
     */
    fun parseCurrencyCode(currency: String?): String {
        if (currency.isNullOrBlank()) return "USD"
        val trimmed = currency.trim()
        if (trimmed.contains(" - ")) {
            return trimmed.substringBefore(" - ").trim().uppercase()
        }
        return trimmed.uppercase()
    }

    /** Picker label for a stored ISO code. */
    fun toDisplayFormat(currencyCode: String): String {
        val code = parseCurrencyCode(currencyCode)
        val symbol = symbolForCode(code)
        return "$code - $symbol"
    }

    fun getCurrencySymbol(currency: String): String {
        return symbolForCode(parseCurrencyCode(currency))
    }

    private fun symbolForCode(currencyCode: String): String {
        return when (currencyCode.uppercase()) {
            "YER" -> "﷼"
            "SAR" -> "﷼"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "CAD" -> "C$"
            "AUD" -> "A$"
            "CHF" -> "CHF"
            "CNY" -> "¥"
            "INR" -> "₹"
            "RUB" -> "₽"
            "BRL" -> "R$"
            "MXN" -> "$"
            "KRW" -> "₩"
            else -> currencyCode.uppercase()
        }
    }

    /**
     * Convert amount from source currency to target currency
     * Note: This uses approximate exchange rates. Actual conversion will be done by backend.
     */
    fun convertCurrency(
        amount: Double,
        fromCurrency: String,
        toCurrency: String
    ): Double {
        val fromCode = parseCurrencyCode(fromCurrency)
        val toCode = parseCurrencyCode(toCurrency)
        if (fromCode == toCode) return amount

        val amountInUSD = amount / getExchangeRateToUSD(fromCode)
        return amountInUSD * getExchangeRateToUSD(toCode)
    }

    private fun getExchangeRateToUSD(currencyCode: String): Double {
        return when (parseCurrencyCode(currencyCode)) {
            "USD" -> 1.0
            "EUR" -> 0.92
            "GBP" -> 0.79
            "JPY" -> 149.0
            "CAD" -> 1.36
            "AUD" -> 1.52
            "CHF" -> 0.88
            "CNY" -> 7.24
            "INR" -> 83.0
            "RUB" -> 92.0
            "BRL" -> 5.0
            "MXN" -> 17.0
            "KRW" -> 1320.0
            else -> 1.0
        }
    }
}
