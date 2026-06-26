package com.example.moneymate.ui.screens.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moneymate.utils.CurrencyUtils

@Composable
fun TotalBalanceCard(
    initialBalance: String,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    val currencySymbol = remember(currencyCode) {
        CurrencyUtils.getCurrencySymbol(currencyCode)
    }
    val displayBalance = if (initialBalance.isEmpty()) "0.00" else
        String.format("%.2f", initialBalance.toDoubleOrNull() ?: 0.0)

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF666666)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$currencySymbol$displayBalance",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF1A1A1A),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CardNumberPreview(
    cardNumber: String,
    modifier: Modifier = Modifier
) {
    val displayNumber = if (cardNumber.isEmpty()) {
        "5485  7381  3758  2321"
    } else {
        cardNumber.chunked(4).joinToString("  ")
    }

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4D6BFA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = displayNumber,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Card number",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletTypeDropdown(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // FIXED: Match backend WalletType enum exactly
    val walletTypes = listOf(
        "cash" to "Cash",
        "card" to "Card",
        "e_wallet" to "E-Wallet",
        "savings" to "Savings",
        "investment" to "Investment",
        "other" to "Other"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = walletTypes.find { it.first == selectedType }?.second ?: "Cash",
            onValueChange = { },
            label = { Text("Type") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4D6BFA),
                focusedLabelColor = Color(0xFF4D6BFA)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            walletTypes.forEach { (type, displayName) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val currencies = CurrencyUtils.walletCurrencyOptions
    val selectedCode = CurrencyUtils.parseCurrencyCode(selectedCurrency)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currencies.find { it.first == selectedCode }?.second
                ?: CurrencyUtils.toDisplayFormat(selectedCurrency),
            onValueChange = { },
            label = { Text("Currency") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4D6BFA),
                focusedLabelColor = Color(0xFF4D6BFA)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            currencies.forEach { (currency, displayName) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        onCurrencySelected(currency)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ColorSelectionSection(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Color",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF1A1A1A),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ColorSelectionGrid(
            selectedColor = selectedColor,
            onColorSelected = onColorSelected
        )
    }
}