@file:Suppress("UNUSED_EXPRESSION")
package com.example.moneymate.ui.screens.transaction

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.domain.transaction.model.TransactionEntity
import com.example.moneymate.ui.components.DeleteTransactionDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.moneymate.ui.components.states.FullScreenError
import com.example.moneymate.ui.components.states.FullScreenLoading
import com.example.moneymate.ui.components.states.SectionStateManager
import com.example.moneymate.ui.navigation.BottomNavigationBar
import com.example.moneymate.ui.offline.OfflineSnackbarHost
import com.example.moneymate.ui.offline.SyncStatus
import com.example.moneymate.ui.offline.SyncStatusIndicator
import com.example.moneymate.ui.offline.PendingSyncIndicator
import com.example.moneymate.ui.screens.transaction.component.SwipeableChartContainer
import com.example.moneymate.ui.screens.transaction.component.TransactionListItem
import com.example.moneymate.ui.screens.transaction.component.TransactionTopBar
import org.koin.androidx.compose.koinViewModel

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    onBackClick: () -> Unit,
    onNavigationItemSelected: (String) -> Unit,
    currentScreen: String = "transactions",
    viewModel: TransactionScreenViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var transactionPendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh data when screen comes into focus
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    println("🔄 DEBUG: TransactionScreen - Screen resumed, refreshing data...")
                    viewModel.refreshOnScreenFocus()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        // Clean up when the composable leaves the composition
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Show error snackbar for transaction errors
    LaunchedEffect(uiState.transactionsState) {
        if (uiState.transactionsState is com.example.moneymate.utils.ScreenState.Error) {
            val error = (uiState.transactionsState as com.example.moneymate.utils.ScreenState.Error).error
            snackbarHostState.showSnackbar(
                message = error.getUserFriendlyMessage(),
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        topBar = {
            TransactionTopBar(
                onBackClick = onBackClick,
                onExportClick = {
                    // TODO: Implement export functionality
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentScreen = currentScreen,
                onNavigationItemSelected = onNavigationItemSelected
            )
        },
        snackbarHost = { OfflineSnackbarHost(isOffline = uiState.syncStatus == SyncStatus.OFFLINE, snackbarHostState = snackbarHostState) }
    ) { paddingValues ->
        // Main content with state management
        when {
            // Show full screen loading if both charts and transactions are loading
            uiState.chartsState is com.example.moneymate.utils.ScreenState.Loading &&
                    uiState.transactionsState is com.example.moneymate.utils.ScreenState.Loading -> {
                FullScreenLoading(message = "Loading transaction data...")
            }
            // Show full screen error if charts failed to load (critical data)
            uiState.chartsState is com.example.moneymate.utils.ScreenState.Error -> {
                FullScreenError(
                    error = (uiState.chartsState as com.example.moneymate.utils.ScreenState.Error).error,
                    onRetry = { viewModel.loadData() }
                )
            }
            // Show normal content
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    // Charts section with state management
                    item {
                        SyncStatusIndicator(
                            status = uiState.syncStatus,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    item {
                        SectionStateManager(
                            state = uiState.chartsState,
                            onRetry = { viewModel.loadAllChartData() }
                        ) { chartsData ->
                            SwipeableChartContainer(
                                chartsData = chartsData,
                                currentChartType = chartsData.currentChartType,
                                onChartTypeChanged = { chartType ->
                                    viewModel.updateCurrentChartType(chartType)
                                },
                                onFilterChanged = { filter ->  // ← ADD THIS LINE
                                    viewModel.updateChartFilter(filter)  // ← ADD THIS LINE
                                },  // ← ADD THIS LINE
                                onPeriodChanged = { period ->
                                    viewModel.updatePeriodFilter(period)
                                },
                                onDateRangeChanged = { dateRange ->
                                    viewModel.updateDateRange(dateRange)
                                },
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // NEW FORECAST CHARTS
                            chartsData.spendingForecast?.let { forecast ->
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                                com.example.moneymate.ui.screens.transaction.component.SpendingForecastChart(
                                    spendingForecast = forecast,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            chartsData.savingsSuggestions?.let { suggestions ->
                                if (suggestions.suggestions.isNotEmpty()) {
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                                    com.example.moneymate.ui.screens.transaction.component.AISuggestionsCard(
                                        suggestionsData = suggestions,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Recent Transactions Header
                    item {
                        Text(
                            text = "Recent Transactions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                top = 24.dp,
                                bottom = 16.dp
                            )
                        )
                    }

                    // Transactions section with state management
                    item {
                        SectionStateManager(
                            state = uiState.transactionsState,
                            onRetry = { viewModel.loadRecentTransactions() }
                        ) { transactions ->
                            if (transactions.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    Text(
                                        text = "No recent transactions",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            } else {
                                // This will be handled in the items below
                            }
                        }
                    }

                    // Transaction List Items (only when we have success state)
                    when (uiState.transactionsState) {
                        is com.example.moneymate.utils.ScreenState.Success -> {
                            val transactions = (uiState.transactionsState as com.example.moneymate.utils.ScreenState.Success).data
                            if (transactions.isNotEmpty()) {
                                items(transactions) { transaction ->
                                    TransactionListItem(
                                        transaction = transaction,
                                        // Prefer Room truth: if it's in unsynced ids, show pending.
                                        // Fallback: treat negative temp IDs as pending.
                                        isSynced = !uiState.unsyncedTransactionIds.contains(transaction.id) && transaction.id >= 0,
                                        onDelete = { transactionPendingDelete = transaction }
                                    )
                                }
                            }
                        }
                        else -> {
                            // Loading/Error states are handled by SectionStateManager above
                        }
                    }
                }
            }
        }
    }

    transactionPendingDelete?.let { transaction ->
        DeleteTransactionDialog(
            transactionName = transaction.name,
            onConfirm = {
                viewModel.deleteTransaction(transaction.id) { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                transactionPendingDelete = null
            },
            onDismiss = { transactionPendingDelete = null }
        )
    }
}