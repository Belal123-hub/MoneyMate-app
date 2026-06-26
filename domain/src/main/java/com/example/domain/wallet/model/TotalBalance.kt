package com.example.domain.wallet.model

data class TotalBalance(
    val totalBalance: Double,
    val currency: String,
    val breakdown: Map<String, Double> = emptyMap()
)

data class BalanceBreakdown(
    val walletId: Int,
    val walletName: String,
    val walletType: String,
    val originalBalance: Double,
    val originalCurrency: String,
    val convertedBalance: Double,
    val convertedCurrency: String,
    val exchangeRateUsed: Double
)