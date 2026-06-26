package com.example.moneymate.ui.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.offline.OfflineSyncStatusDataSource
import com.example.domain.transaction.model.CategorySummaryData
import com.example.domain.transaction.model.ChartFilter
import com.example.domain.transaction.model.ChartType
import com.example.domain.transaction.model.DateRange
import com.example.domain.transaction.model.MonthlyChartData
import com.example.domain.transaction.model.MonthlyComparisonData
import com.example.domain.transaction.model.PeriodFilter
import com.example.domain.transaction.model.TransactionChartsData
import com.example.domain.transaction.model.TransactionEntity
import com.example.domain.transaction.model.CategoryData
import com.example.domain.transaction.usecase.GetAverageSpendingUseCase
import com.example.domain.transaction.usecase.GetCategorySummaryUseCase
import com.example.domain.transaction.usecase.GetMonthlyChartDataUseCase
import com.example.domain.transaction.usecase.GetMonthlyComparisonUseCase
import com.example.domain.transaction.usecase.DeleteTransactionUseCase
import com.example.domain.transaction.usecase.GetRecentTransactionsUseCase
import com.example.domain.transaction.usecase.GetSavingsForecastUseCase
import com.example.domain.transaction.usecase.GetSavingsSuggestionsUseCase
import com.example.domain.transaction.usecase.GetSpendingForecastUseCase
import com.example.domain.transaction.usecase.GetTopCategoriesCurrentMonthUseCase
import com.example.moneymate.utils.DataSyncManager
import com.example.moneymate.utils.ScreenState
import com.example.moneymate.utils.network.ConnectivityObserver
import com.example.moneymate.ui.offline.SyncStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransactionScreenViewModel(
    private val getMonthlyChartDataUseCase: GetMonthlyChartDataUseCase,
    private val getCategorySummaryUseCase: GetCategorySummaryUseCase,
    private val getMonthlyComparisonUseCase: GetMonthlyComparisonUseCase,
    private val getRecentTransactionsUseCase: GetRecentTransactionsUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val getTopCategoriesCurrentMonthUseCase: GetTopCategoriesCurrentMonthUseCase,
    private val getAverageSpendingUseCase: GetAverageSpendingUseCase,
    private val getSavingsForecastUseCase: GetSavingsForecastUseCase,
    private val getSpendingForecastUseCase: GetSpendingForecastUseCase,
    private val getSavingsSuggestionsUseCase: GetSavingsSuggestionsUseCase,
    private val offlineSyncStatusDataSource: OfflineSyncStatusDataSource,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionScreenState())
    val uiState: StateFlow<TransactionScreenState> = _uiState.asStateFlow()

    init {
        println("DEBUG: TransactionScreenViewModel init - loading data")
        loadData()
        observeUnsyncedTransactions()
        observeConnectivity()

        // Listen for data change events
        setupDataChangeListener()
    }

    private fun observeUnsyncedTransactions() {
        viewModelScope.launch {
            offlineSyncStatusDataSource.observeUnsyncedTransactionIds().collect { ids ->
                _uiState.value = _uiState.value.copy(
                    unsyncedTransactionIds = ids,
                    syncStatus = if (ids.isEmpty()) _uiState.value.syncStatus else SyncStatus.SYNCING
                )
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { isOnline ->
                _uiState.value = _uiState.value.copy(
                    syncStatus = if (!isOnline) SyncStatus.OFFLINE else if (_uiState.value.unsyncedTransactionIds.isEmpty()) SyncStatus.IDLE else SyncStatus.SYNCING
                )
            }
        }
    }

    private fun setupDataChangeListener() {
        viewModelScope.launch {
            // Listen for transaction updates
            DataSyncManager.dataChangeEvents.collect { event ->
                when (event) {
                    is DataSyncManager.DataChangeEvent.TransactionsUpdated -> {
                        println("🔄 DEBUG: TransactionScreenViewModel - Transaction update detected, refreshing...")
                        refreshTransactionData()
                    }
                    is DataSyncManager.DataChangeEvent.WalletsUpdated -> {
                        println("🔄 DEBUG: TransactionScreenViewModel - Wallet update detected, may affect transactions...")
                        // If transactions are wallet-specific, refresh them
                        refreshTransactionData()
                    }
                    is DataSyncManager.DataChangeEvent.CategoriesUpdated -> {
                        println("🔄 DEBUG: TransactionScreenViewModel - Category update detected, refreshing charts...")
                        refreshChartData()
                    }
                    is DataSyncManager.DataChangeEvent.BudgetUpdated -> {
                        println("🔄 DEBUG: TransactionScreenViewModel - Budget update detected, may affect charts...")
                        // Budget changes might affect transaction analysis
                        refreshChartData()
                    }
                    else -> {
                        // Handle other events if needed
                    }
                }
            }
        }
    }

    // Add refresh functions
    fun refreshTransactionData() {
        viewModelScope.launch {
            println("🔄 DEBUG: TransactionScreenViewModel - Refreshing transaction data...")
            loadRecentTransactions()
            loadAllChartData()
        }
    }

    fun refreshChartData() {
        viewModelScope.launch {
            println("🔄 DEBUG: TransactionScreenViewModel - Refreshing chart data...")
            loadAllChartData()
        }
    }

    // Add refresh on screen focus
    fun refreshOnScreenFocus() {
        viewModelScope.launch {
            println("🔄 DEBUG: TransactionScreenViewModel - Screen focused, refreshing data...")
            loadData()
        }
    }

    // Also update your existing loadData() function to be more efficient:
    fun loadData() {
        // Load both in parallel for better performance
        viewModelScope.launch {
            launch { loadAllChartData() }
            launch { loadRecentTransactions() }
        }
    }

    fun loadAllChartData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(chartsState = ScreenState.Loading)

            try {
                println("📊 DEBUG: Loading all chart data...")

                // Load all chart data in parallel
                val monthlyChartDeferred = async { 
                    println("  ⏳ Loading monthly chart...")
                    getMonthlyChartDataUseCase.execute(months = 12) 
                }
                val defaultDateRange = DateRange(getDefaultStartDate(), getDefaultEndDate())
                val lineChartDataDeferred = async { 
                    println("  ⏳ Loading line chart data...")
                    getMonthlyChartDataUseCase.executeForLineChart(defaultDateRange) 
                }
                val categorySummaryDeferred = async { 
                    println("  ⏳ Loading category summary...")
                    println("  📊 DEBUG: getCategorySummaryUseCase instance: ${getCategorySummaryUseCase != null}")
                    println("  📊 DEBUG: Category summary dateRange=${defaultDateRange.startDate}..${defaultDateRange.endDate}")
                    val result = getCategorySummaryUseCase.execute(
                        startDate = defaultDateRange.startDate,
                        endDate = defaultDateRange.endDate
                    )
                    println("  📊 DEBUG: Category summary result: ${result.expenses.size} expenses")
                    result
                }
                val monthlyComparisonDeferred = async { 
                    println("  ⏳ Loading monthly comparison...")
                    getMonthlyComparisonUseCase.execute() 
                }
                val topCategoriesDeferred = async { 
                    println("  ⏳ Loading top categories...")
                    getTopCategoriesCurrentMonthUseCase() 
                }
                val averageSpendingDeferred = async { 
                    println("  ⏳ Loading average spending...")
                    getAverageSpendingUseCase(PeriodFilter.MONTH) 
                }
                // NEW FORECAST DATA
                val savingsForecastDeferred = async { getSavingsForecastUseCase(3) }
                val spendingForecastDeferred = async { getSpendingForecastUseCase() }
                val savingsSuggestionsDeferred = async { getSavingsSuggestionsUseCase() }

                // Await all results
                val monthlyChart = monthlyChartDeferred.await()
                println("  ✅ Monthly chart loaded: ${monthlyChart.months.size} months, ${monthlyChart.days.size} days")
                
                val lineChartData = lineChartDataDeferred.await()
                println("  ✅ Line chart loaded: ${lineChartData.days.size} days")
                
                val categorySummary = categorySummaryDeferred.await()
                println("  ✅ Category summary: ${categorySummary.expenses.size} expense categories")
                
                val monthlyComparison = monthlyComparisonDeferred.await()
                println("  ✅ Monthly comparison: ${monthlyComparison.categories.size} categories")
                
                val topCategories = topCategoriesDeferred.await().getOrElse { emptyList() }
                println("  ✅ Top categories: ${topCategories.size} categories")
                
                val averageSpending = averageSpendingDeferred.await().getOrElse { emptyList() }
                println("  ✅ Average spending: ${averageSpending.size} categories")
                
                // NEW FORECAST DATA
                val savingsForecast = savingsForecastDeferred.await().getOrNull()
                val spendingForecast = spendingForecastDeferred.await().getOrNull()
                val savingsSuggestions = savingsSuggestionsDeferred.await().getOrNull()
                println("  ✅ Forecasts loaded - Savings: ${savingsForecast != null}, Spending: ${spendingForecast != null}, Suggestions: ${savingsSuggestions?.suggestions?.size ?: 0}")

                val chartsData = TransactionChartsData(
                    monthlyChart = monthlyChart.copy(
                        days = lineChartData.days,
                        dateRange = defaultDateRange,
                        selectedFilter = ChartFilter.EXPENSES
                    ),
                    categorySummary = categorySummary.takeIf { it.expenses.isNotEmpty() && it.totalExpenses > 0.0 }
                        ?: run {
                            // Fallback: if backend category-summary is empty, derive pie data from top categories
                            // (still useful for the "Expense Distribution" pie chart).
                            if (topCategories.isNotEmpty()) {
                                val expenses = topCategories.map {
                                    CategoryData(
                                        categoryId = it.categoryId,
                                        categoryName = it.categoryName,
                                        categoryType = "expense",
                                        totalAmount = it.totalAmount,
                                        transactionCount = 0
                                    )
                                }
                                val total = expenses.sumOf { it.totalAmount }
                                println("  ⚠️ Category summary empty; using topCategories fallback: ${expenses.size} categories, total=$total")
                                CategorySummaryData(
                                    expenses = expenses,
                                    incomes = emptyList(),
                                    totalExpenses = total,
                                    totalIncomes = 0.0,
                                    netFlow = -total
                                )
                            } else {
                                categorySummary
                            }
                        },
                    monthlyComparison = monthlyComparison,
                    topCategories = topCategories,
                    averageSpending = averageSpending,
                    currentChartType = ChartType.MONTHLY_TRENDS,
                    currentPeriod = PeriodFilter.MONTH,
                    savingsForecast = savingsForecast,
                    spendingForecast = spendingForecast,
                    savingsSuggestions = savingsSuggestions
                )

                _uiState.value = _uiState.value.copy(
                    chartsState = ScreenState.Success(chartsData)
                )
                println("DEBUG: All chart data loaded successfully")

            } catch (e: Exception) {
                println("DEBUG: Chart data error: ${e.message}")
                // On error, use empty/default data
                val fallbackChartsData = TransactionChartsData(
                    monthlyChart = MonthlyChartData(
                        months = emptyList(),
                        days = emptyList(),
                        dateRange = DateRange(getDefaultStartDate(), getDefaultEndDate()),
                        selectedFilter = ChartFilter.EXPENSES
                    ),
                    categorySummary = CategorySummaryData(
                        expenses = emptyList(),
                        incomes = emptyList(),
                        totalExpenses = 0.0,
                        totalIncomes = 0.0,
                        netFlow = 0.0
                    ),
                    monthlyComparison = MonthlyComparisonData(
                        categories = emptyList(),
                        selectedMonth = "Current Month"
                    ),
                    currentChartType = ChartType.MONTHLY_TRENDS
                )

                _uiState.value = _uiState.value.copy(
                    chartsState = ScreenState.Error(
                        com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                        retryAction = { loadAllChartData() }
                    )
                )
            }
        }
    }

    fun loadAverageSpending(period: PeriodFilter = PeriodFilter.MONTH) {
        viewModelScope.launch {
            try {
                val currentCharts = when (val state = _uiState.value.chartsState) {
                    is ScreenState.Success -> state.data
                    else -> return@launch
                }

                val result = getAverageSpendingUseCase(period)
                result.fold(
                    onSuccess = { averageSpending ->
                        _uiState.value = _uiState.value.copy(
                            chartsState = ScreenState.Success(
                                currentCharts.copy(
                                    averageSpending = averageSpending,
                                    currentPeriod = period
                                )
                            )
                        )
                        println("DEBUG: Average spending loaded for period: $period")
                    },
                    onFailure = { error ->
                        println("DEBUG: Failed to load average spending: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                println("DEBUG: Error loading average spending: ${e.message}")
            }
        }
    }

    fun loadAverageSpendingData(currentPeriod: PeriodFilter = PeriodFilter.MONTH) {
        viewModelScope.launch {
            try {
                val currentCharts = when (val state = _uiState.value.chartsState) {
                    is ScreenState.Success -> state.data
                    else -> return@launch
                }

                // Load average spending for the period
                val result = getAverageSpendingUseCase(currentPeriod)
                result.fold(
                    onSuccess = { averageSpending ->
                        _uiState.value = _uiState.value.copy(
                            chartsState = ScreenState.Success(
                                currentCharts.copy(
                                    averageSpending = averageSpending,
                                    currentChartType = ChartType.AVERAGE_SPENDING_LIST,
                                    currentPeriod = currentPeriod
                                )
                            )
                        )
                        println("DEBUG: Average spending loaded for period: $currentPeriod")
                    },
                    onFailure = { error ->
                        println("DEBUG: Failed to load average spending: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                println("DEBUG: Error loading average spending: ${e.message}")
            }
        }
    }

    fun deleteTransaction(transactionId: Int, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = deleteTransactionUseCase(transactionId)
            if (result.isSuccess) {
                DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.TransactionsUpdated)
                DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.WalletsUpdated)
                loadRecentTransactions()
                loadAllChartData()
            } else {
                onError(
                    result.exceptionOrNull()?.message ?: "Could not delete transaction"
                )
            }
        }
    }

    fun loadRecentTransactions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(transactionsState = ScreenState.Loading)

            try {
                println("DEBUG: Loading recent transactions...")
                val result = getRecentTransactionsUseCase.execute(limit = 20)

                if (result.isSuccess) {
                    val transactions = result.getOrNull() ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        transactionsState = ScreenState.Success(transactions),
                        unsyncedTransactionIds = offlineSyncStatusDataSource.getUnsyncedTransactionIds().toSet()
                    )
                    println("DEBUG: Loaded ${transactions.size} transactions")
                } else {
                    _uiState.value = _uiState.value.copy(
                        transactionsState = ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(
                                result.exceptionOrNull() ?: Exception("Failed to load transactions")
                            ),
                            retryAction = { loadRecentTransactions() }
                        )
                    )
                }
            } catch (e: Exception) {
                println("DEBUG: Recent transactions error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    transactionsState = ScreenState.Error(
                        com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                        retryAction = { loadRecentTransactions() }
                    )
                )
            }
        }
    }

    // Update filters for charts - ADD EXTENSIVE DEBUGGING
    fun updateCurrentChartType(chartType: ChartType) {
        println("DEBUG: updateCurrentChartType called with: $chartType")

        when (chartType) {
            ChartType.AVERAGE_SPENDING_RADAR, ChartType.AVERAGE_SPENDING_LIST -> {
                // Load average spending data when switching to either average spending chart type
                val currentCharts = when (val state = _uiState.value.chartsState) {
                    is ScreenState.Success -> state.data
                    else -> _uiState.value.chartsData
                }

                // Check if we already have average spending data
                if (currentCharts.averageSpending.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        chartsState = ScreenState.Success(
                            currentCharts.copy(currentChartType = chartType)
                        )
                    )
                } else {
                    // Load average spending data
                    loadAverageSpendingData(currentCharts.currentPeriod)
                }
            }
            else -> {
                val currentCharts = when (val state = _uiState.value.chartsState) {
                    is ScreenState.Success -> {
                        println("DEBUG: Current charts state: SUCCESS")
                        state.data
                    }
                    else -> {
                        println("DEBUG: Current charts state: ${_uiState.value.chartsState}")
                        _uiState.value.chartsData // fallback
                    }
                }

                _uiState.value = _uiState.value.copy(
                    chartsState = ScreenState.Success(
                        currentCharts.copy(currentChartType = chartType)
                    )
                )
            }
        }
        println("DEBUG: Chart type updated to: $chartType")
    }

    // Update filters for charts - ADD EXTENSIVE DEBUGGING
    fun updateChartFilter(filter: ChartFilter) {
        println("🚀 DEBUG: updateChartFilter CALLED with: $filter")

        val currentCharts = when (val state = _uiState.value.chartsState) {
            is ScreenState.Success -> {
                println("✅ DEBUG: Charts state is SUCCESS")
                println("📊 DEBUG: Current chart type: ${state.data.currentChartType}")
                println("🎛️ DEBUG: Current monthly filter: ${state.data.monthlyChart.selectedFilter}")
                state.data
            }
            else -> {
                println("❌ DEBUG: Charts state is NOT SUCCESS: ${_uiState.value.chartsState}")
                return
            }
        }

        when (currentCharts.currentChartType) {
            ChartType.MONTHLY_TRENDS -> {
                println("📈 DEBUG: Updating MONTHLY_TRENDS chart filter from ${currentCharts.monthlyChart.selectedFilter} to $filter")

                _uiState.value = _uiState.value.copy(
                    chartsState = ScreenState.Success(
                        currentCharts.copy(
                            monthlyChart = currentCharts.monthlyChart.copy(selectedFilter = filter)
                        )
                    )
                )

                // Verify the update
                when (val newState = _uiState.value.chartsState) {
                    is ScreenState.Success -> {
                        println("✅ DEBUG: SUCCESS - Monthly chart filter updated to: ${newState.data.monthlyChart.selectedFilter}")
                    }
                    else -> {
                        println("❌ DEBUG: FAILED - Charts state after update: $newState")
                    }
                }
            }
            ChartType.CATEGORY_BREAKDOWN -> {
                println("📊 DEBUG: Updating CATEGORY_BREAKDOWN chart filter")
                _uiState.value = _uiState.value.copy(
                    chartsState = ScreenState.Success(
                        currentCharts.copy(
                            categorySummary = currentCharts.categorySummary.copy(selectedFilter = filter)
                        )
                    )
                )
                println("✅ DEBUG: Category summary filter updated to: $filter")
            }
            ChartType.MONTHLY_COMPARISON -> {
                println("📅 DEBUG: MONTHLY_COMPARISON filter change (no action needed)")
            }
            ChartType.TOP_CATEGORIES -> {
                println("📊 DEBUG: TOP_CATEGORIES filter change (no action needed for donut chart)")
            }
            ChartType.AVERAGE_SPENDING_RADAR, ChartType.AVERAGE_SPENDING_LIST -> {
                println("📊 DEBUG: AVERAGE_SPENDING filter change (no action needed for average spending chart)")
            }
        }
    }

    // Update period filter and reload data for charts
    fun updatePeriodFilter(period: PeriodFilter) {
        println("DEBUG: updatePeriodFilter called with: $period")
        viewModelScope.launch {
            try {
                val currentCharts = when (val state = _uiState.value.chartsState) {
                    is ScreenState.Success -> {
                        println("DEBUG: Current charts available for period update")
                        state.data
                    }
                    else -> {
                        println("DEBUG: No charts available for period update")
                        return@launch
                    }
                }

                when (currentCharts.currentChartType) {
                    ChartType.MONTHLY_TRENDS -> {
                        val months = when (period) {
                            PeriodFilter.YEAR -> 12
                            PeriodFilter.MONTH -> 1
                            else -> 12 // Default for other periods in bar chart
                        }
                        val result = getMonthlyChartDataUseCase.execute(months = months)
                        _uiState.value = _uiState.value.copy(
                            chartsState = ScreenState.Success(
                                currentCharts.copy(
                                    monthlyChart = result.copy(
                                        selectedPeriod = period,
                                        selectedFilter = currentCharts.monthlyChart.selectedFilter // Preserve current filter
                                    )
                                )
                            )
                        )
                        println("DEBUG: Monthly chart period updated to: $period")
                    }
                    ChartType.CATEGORY_BREAKDOWN -> {
                        val dateRange = when (period) {
                            PeriodFilter.DAYS_7 -> DateRange(getDateDaysAgo(7), getDefaultEndDate())
                            PeriodFilter.DAYS_15 -> DateRange(getDateDaysAgo(15), getDefaultEndDate())
                            PeriodFilter.DAYS_30 -> DateRange(getDateDaysAgo(30), getDefaultEndDate())
                            PeriodFilter.DAYS_90 -> DateRange(getDateDaysAgo(90), getDefaultEndDate())
                            else -> currentCharts.monthlyChart.dateRange
                        }
                        updateDateRange(dateRange)
                    }
                    ChartType.MONTHLY_COMPARISON -> {
                        // Handle comparison chart period changes if needed
                    }
                    ChartType.TOP_CATEGORIES -> {
                        // For top categories donut chart, load average spending for the new period
                        loadAverageSpending(period)
                    }
                    ChartType.AVERAGE_SPENDING_RADAR, ChartType.AVERAGE_SPENDING_LIST -> {
                        // For average spending charts, reload data for new period
                        loadAverageSpendingData(period)
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Period filter error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // Update date range for line chart
    fun updateDateRange(dateRange: DateRange) {
        println("DEBUG: updateDateRange called with: $dateRange")
        viewModelScope.launch {
            try {
                val currentCharts = when (val state = _uiState.value.chartsState) {
                    is ScreenState.Success -> state.data
                    else -> return@launch
                }

                val lineChartData = getMonthlyChartDataUseCase.executeForLineChart(dateRange)
                _uiState.value = _uiState.value.copy(
                    chartsState = ScreenState.Success(
                        currentCharts.copy(
                            monthlyChart = currentCharts.monthlyChart.copy(
                                days = lineChartData.days,
                                dateRange = dateRange,
                                selectedPeriod = getPeriodFilterForDateRange(dateRange)
                            )
                        )
                    )
                )
                println("DEBUG: Line chart updated with date range: ${dateRange.startDate} to ${dateRange.endDate}")
            } catch (e: Exception) {
                println("DEBUG: Date range update error: ${e.message}")
            }
        }
    }

    // Refresh line chart data with current date range
    fun refreshLineChartData() {
        viewModelScope.launch {
            try {
                val currentCharts = when (val state = _uiState.value.chartsState) {
                    is ScreenState.Success -> state.data
                    else -> return@launch
                }

                val currentDateRange = currentCharts.monthlyChart.dateRange
                val lineChartData = getMonthlyChartDataUseCase.executeForLineChart(currentDateRange)
                _uiState.value = _uiState.value.copy(
                    chartsState = ScreenState.Success(
                        currentCharts.copy(
                            monthlyChart = currentCharts.monthlyChart.copy(
                                days = lineChartData.days
                            )
                        )
                    )
                )
                println("DEBUG: Line chart data refreshed")
            } catch (e: Exception) {
                println("DEBUG: Line chart refresh error: ${e.message}")
            }
        }
    }

    // Refresh category summary data with custom date range
    fun refreshCategorySummary(startDate: String, endDate: String) {
        viewModelScope.launch {
            try {
                val currentCharts = when (val state = _uiState.value.chartsState) {
                    is ScreenState.Success -> state.data
                    else -> return@launch
                }

                val categorySummary = getCategorySummaryUseCase.execute(startDate, endDate)
                _uiState.value = _uiState.value.copy(
                    chartsState = ScreenState.Success(
                        currentCharts.copy(
                            categorySummary = categorySummary
                        )
                    )
                )
                println("DEBUG: Category summary refreshed with custom date range")
            } catch (e: Exception) {
                println("DEBUG: Category summary refresh error: ${e.message}")
            }
        }
    }

    // Refresh monthly comparison data
    fun refreshMonthlyComparison(month: String) {
        viewModelScope.launch {
            try {
                val currentCharts = when (val state = _uiState.value.chartsState) {
                    is ScreenState.Success -> state.data
                    else -> return@launch
                }

                val monthlyComparison = getMonthlyComparisonUseCase.execute(month)
                _uiState.value = _uiState.value.copy(
                    chartsState = ScreenState.Success(
                        currentCharts.copy(
                            monthlyComparison = monthlyComparison
                        )
                    )
                )
                println("DEBUG: Monthly comparison refreshed")
            } catch (e: Exception) {
                println("DEBUG: Monthly comparison refresh error: ${e.message}")
            }
        }
    }

    // Refresh specific chart data
    fun refreshChartData(chartType: ChartType) {
        viewModelScope.launch {
            val currentCharts = when (val state = _uiState.value.chartsState) {
                is ScreenState.Success -> state.data
                else -> return@launch
            }

            when (chartType) {
                ChartType.MONTHLY_TRENDS -> {
                    try {
                        val monthlyChart = getMonthlyChartDataUseCase.execute(months = 12)
                        _uiState.value = _uiState.value.copy(
                            chartsState = ScreenState.Success(
                                currentCharts.copy(
                                    monthlyChart = monthlyChart.copy(
                                        dateRange = currentCharts.monthlyChart.dateRange,
                                        days = currentCharts.monthlyChart.days,
                                        selectedFilter = currentCharts.monthlyChart.selectedFilter // Preserve filter
                                    )
                                )
                            )
                        )
                    } catch (e: Exception) {
                        println("DEBUG: Monthly chart refresh error: ${e.message}")
                    }
                }
                ChartType.CATEGORY_BREAKDOWN -> {
                    try {
                        refreshLineChartData()
                    } catch (e: Exception) {
                        println("DEBUG: Line chart refresh error: ${e.message}")
                    }
                }
                ChartType.MONTHLY_COMPARISON -> {
                    try {
                        val monthlyComparison = getMonthlyComparisonUseCase.execute()
                        _uiState.value = _uiState.value.copy(
                            chartsState = ScreenState.Success(
                                currentCharts.copy(
                                    monthlyComparison = monthlyComparison
                                )
                            )
                        )
                    } catch (e: Exception) {
                        println("DEBUG: Monthly comparison refresh error: ${e.message}")
                    }
                }
                ChartType.TOP_CATEGORIES -> {
                    try {
                        // Refresh both top categories and average spending
                        val topCategoriesResult = getTopCategoriesCurrentMonthUseCase()
                        val averageSpendingResult = getAverageSpendingUseCase(currentCharts.currentPeriod)

                        topCategoriesResult.fold(
                            onSuccess = { topCategories ->
                                averageSpendingResult.fold(
                                    onSuccess = { averageSpending ->
                                        _uiState.value = _uiState.value.copy(
                                            chartsState = ScreenState.Success(
                                                currentCharts.copy(
                                                    topCategories = topCategories,
                                                    averageSpending = averageSpending
                                                )
                                            )
                                        )
                                    },
                                    onFailure = { error ->
                                        println("DEBUG: Failed to refresh average spending: ${error.message}")
                                    }
                                )
                            },
                            onFailure = { error ->
                                println("DEBUG: Failed to refresh top categories: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        println("DEBUG: Top categories refresh error: ${e.message}")
                    }
                }
                ChartType.AVERAGE_SPENDING_RADAR, ChartType.AVERAGE_SPENDING_LIST -> {
                    try {
                        // Refresh average spending data
                        val result = getAverageSpendingUseCase(currentCharts.currentPeriod)
                        result.fold(
                            onSuccess = { averageSpending ->
                                _uiState.value = _uiState.value.copy(
                                    chartsState = ScreenState.Success(
                                        currentCharts.copy(
                                            averageSpending = averageSpending
                                        )
                                    )
                                )
                                println("DEBUG: Average spending refreshed")
                            },
                            onFailure = { error ->
                                println("DEBUG: Failed to refresh average spending: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        println("DEBUG: Average spending refresh error: ${e.message}")
                    }
                }
            }
        }
    }

    // Helper functions for date calculations
    private fun getDefaultStartDate(): String {
        // Default to current month (matches backend expectations & Swagger usage)
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return dateFormat.format(calendar.time)
    }

    private fun getDefaultEndDate(): String {
        val calendar = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return dateFormat.format(calendar.time)
    }

    private fun getDateDaysAgo(days: Int): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -days)
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return dateFormat.format(calendar.time)
    }

    private fun getPeriodFilterForDateRange(dateRange: DateRange): PeriodFilter {
        val start = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateRange.startDate)
        val end = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateRange.endDate)

        val diff = end.time - start.time
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            days <= 7 -> PeriodFilter.DAYS_7
            days <= 15 -> PeriodFilter.DAYS_15
            days <= 30 -> PeriodFilter.DAYS_30
            else -> PeriodFilter.DAYS_90
        }
    }
}

data class TransactionScreenState(
    val chartsState: ScreenState<TransactionChartsData> = ScreenState.Loading,
    val transactionsState: ScreenState<List<TransactionEntity>> = ScreenState.Loading,
    val unsyncedTransactionIds: Set<Int> = emptySet(),
    val syncStatus: SyncStatus = SyncStatus.IDLE
) {
    // Helper properties for backward compatibility
    val isLoading: Boolean
        get() = chartsState is ScreenState.Loading && transactionsState is ScreenState.Loading

    val chartsData: TransactionChartsData
        get() = when (chartsState) {
            is ScreenState.Success -> {
                println("🔄 DEBUG: chartsData getter - SUCCESS state, filter: ${chartsState.data.monthlyChart.selectedFilter}")
                chartsState.data
            }
            else -> {
                println("🔄 DEBUG: chartsData getter - FALLBACK state")
                TransactionChartsData(
                    monthlyChart = MonthlyChartData(
                        months = emptyList(),
                        days = emptyList(),
                        dateRange = DateRange(getDefaultStartDate(), getDefaultEndDate()),
                        selectedPeriod = PeriodFilter.DAYS_30,
                        selectedFilter = ChartFilter.EXPENSES
                    ),
                    categorySummary = CategorySummaryData(
                        expenses = emptyList(),
                        incomes = emptyList(),
                        totalExpenses = 0.0,
                        totalIncomes = 0.0,
                        netFlow = 0.0
                    ),
                    monthlyComparison = MonthlyComparisonData(
                        categories = emptyList(),
                        selectedMonth = "Current Month"
                    ),
                    currentChartType = ChartType.MONTHLY_TRENDS
                )
            }
        }

    val recentTransactions: List<TransactionEntity>
        get() = when (transactionsState) {
            is ScreenState.Success -> transactionsState.data
            else -> emptyList()
        }
}

// Helper functions for default dates
private fun getDefaultStartDate(): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    return dateFormat.format(calendar.time)
}

private fun getDefaultEndDate(): String {
    val calendar = java.util.Calendar.getInstance()
    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    return dateFormat.format(calendar.time)
}