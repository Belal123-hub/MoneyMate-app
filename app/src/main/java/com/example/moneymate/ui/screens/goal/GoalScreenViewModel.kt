package com.example.moneymate.ui.screens.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.dao.MonthlySavingsGoalDao
import com.example.data.database.dao.TransactionDao
import com.example.data.database.entity.MonthlySavingsGoalEntity
import com.example.domain.budget.model.Budget
import com.example.domain.budget.usecase.GetCurrentBudgetUseCase
import com.example.domain.budget.usecase.UpdateBudgetUseCase
import com.example.domain.categoryLimit.model.CategoryLimitOverview
import com.example.domain.categoryLimit.usecase.DeleteCategoryLimitUseCase
import com.example.domain.categoryLimit.usecase.GetCategoryLimitsUseCase
import com.example.domain.categoryLimit.usecase.UpdateCategoryLimitUseCase
import com.example.domain.goal.model.Goal
import com.example.domain.goal.usecase.GetGoalsUseCase
import com.example.domain.savingsGoal.model.SavingsGoal
import com.example.domain.savingsGoal.usecase.GetCurrentSavingsGoalUseCase
import com.example.domain.savingsGoal.usecase.UpdateSavingsGoalUseCase
import com.example.domain.transaction.model.ChartFilter
import com.example.domain.transaction.model.DailyData
import com.example.domain.transaction.model.DateRange
import com.example.domain.transaction.model.MonthlyChartData
import com.example.domain.transaction.model.MonthlyData
import com.example.domain.transaction.model.PeriodFilter
import com.example.domain.transaction.model.SavingsMonthlyData
import com.example.domain.transaction.model.SavingsTrendsData
import com.example.domain.transaction.usecase.GetSavingsForecastUseCase
import com.example.domain.transaction.usecase.GetSavingsTrendsUseCase
import com.example.domain.wallet.model.TotalBalance
import com.example.domain.wallet.usecase.GetTotalBalanceUseCase
import com.example.moneymate.utils.DataSyncManager
import com.example.moneymate.utils.ScreenState
import com.example.moneymate.ui.offline.SyncStatus
import com.example.moneymate.utils.network.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

class GoalScreenViewModel(
    private val getCurrentBudgetUseCase: GetCurrentBudgetUseCase,
    private val updateBudgetUseCase: UpdateBudgetUseCase,
    private val getCategoryLimitsUseCase: GetCategoryLimitsUseCase,
    private val updateCategoryLimitUseCase: UpdateCategoryLimitUseCase,
    private val deleteCategoryLimitUseCase: DeleteCategoryLimitUseCase,
    private val getCurrentSavingsGoalUseCase: GetCurrentSavingsGoalUseCase,
    private val updateSavingsGoalUseCase: UpdateSavingsGoalUseCase,
    private val getSavingsTrendsUseCase: GetSavingsTrendsUseCase,
    private val getSavingsForecastUseCase: GetSavingsForecastUseCase,
    private val getGoalsUseCase: GetGoalsUseCase,
    private val getTotalBalanceUseCase: GetTotalBalanceUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val transactionDao: TransactionDao,
    private val monthlySavingsGoalDao: MonthlySavingsGoalDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalScreenState())
    val uiState: StateFlow<GoalScreenState> = _uiState.asStateFlow()

    init {
        loadAllData()
        setupDataChangeListener()
        observeConnectivity()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { isOnline ->
                _uiState.update {
                    it.copy(syncStatus = if (isOnline) SyncStatus.IDLE else SyncStatus.OFFLINE)
                }
            }
        }
    }

    private fun loadAllData() {
        loadBudgetData()
        loadCategoryLimits()
        loadSavingsGoal()
        loadSavingsTrends()
        loadSavingsForecast()
        loadGoals()
        loadTotalBalance()
    }

    private fun setupDataChangeListener() {
        viewModelScope.launch {
            DataSyncManager.dataChangeEvents.collect { event ->
                when (event) {
                    is DataSyncManager.DataChangeEvent.BudgetUpdated,
                    is DataSyncManager.DataChangeEvent.TransactionsUpdated -> {
                        loadBudgetData()
                        loadSavingsTrends()
                        loadSavingsGoal()
                        loadTotalBalance()
                    }
                    DataSyncManager.DataChangeEvent.CategoryLimitsUpdated -> {
                        loadCategoryLimits()
                    }
                    DataSyncManager.DataChangeEvent.GoalsUpdated -> {
                        loadGoals()
                    }
                    DataSyncManager.DataChangeEvent.WalletsUpdated -> {
                        loadTotalBalance()
                    }
                    else -> Unit
                }
            }
        }
    }

    // --- BALANCE FUNCTIONS ---
    fun loadTotalBalance() {
        viewModelScope.launch {
            _uiState.update { it.copy(balanceState = ScreenState.Loading) }
            try {
                val result = getTotalBalanceUseCase()
                if (result.isSuccess) {
                    val balance = result.getOrNull()
                    _uiState.update {
                        it.copy(
                            balanceState = ScreenState.Success(balance),
                            totalBalance = balance
                        )
                    }
                } else {
                    val exception = result.exceptionOrNull() ?: Exception("Failed to load balance")
                    _uiState.update {
                        it.copy(
                            balanceState = ScreenState.Error(
                                com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(exception),
                                retryAction = { loadTotalBalance() }
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        balanceState = ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                            retryAction = { loadTotalBalance() }
                        )
                    )
                }
            }
        }
    }

    // --- GOALS FUNCTIONS ---
    fun loadGoals() {
        viewModelScope.launch {
            _uiState.update { it.copy(goalsState = ScreenState.Loading) }
            try {
                val result = getGoalsUseCase()
                if (result.isSuccess) {
                    val goals = result.getOrThrow()
                    _uiState.update {
                        it.copy(
                            goalsState = if (goals.isEmpty()) ScreenState.Empty else ScreenState.Success(goals),
                            goals = goals
                        )
                    }
                } else {
                    val exception = result.exceptionOrNull() ?: Exception("Failed to load goals")
                    _uiState.update {
                        it.copy(
                            goalsState = ScreenState.Error(
                                com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(exception),
                                retryAction = { loadGoals() }
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        goalsState = ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                            retryAction = { loadGoals() }
                        )
                    )
                }
            }
        }
    }

    // --- UI INTERACTION FUNCTIONS ---

    fun toggleNotifications() {
        _uiState.update { it.copy(isNotificationsEnabled = !it.isNotificationsEnabled) }
    }

    fun toggleDateRangePicker(show: Boolean) {
        _uiState.update { it.copy(showDateRangePicker = show) }
    }

    fun onDateRangeSelected(startDate: Long?, endDate: Long?) {
        _uiState.update {
            it.copy(
                selectedStartDate = startDate,
                selectedEndDate = endDate,
                showDateRangePicker = false
            )
        }
    }

    fun onChartMonthSelected(month: String) {
        _uiState.update { it.copy(selectedChartMonth = month) }
    }

    // --- SAVINGS TRENDS DATA LOADING ---

    private fun loadSavingsTrends() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingsTrendsLoading = true) }
            try {
                println("📊 DEBUG: Loading savings trends for ${uiState.value.selectedPeriod} months...")
                val result = getSavingsTrendsUseCase(months = uiState.value.selectedPeriod)
                
                if (result.isSuccess) {
                    val trendsData = result.getOrThrow()
                    println("✅ DEBUG: Savings trends loaded successfully - ${trendsData.monthlyTrends.size} months")

                    // Generate months list for dropdown
                    val availableMonths = generateMonthLabels(trendsData.monthlyTrends)

                    // Create chart data from savings trends
                    val monthlyChartData = createChartDataFromSavingsTrends(trendsData.monthlyTrends)

                    _uiState.update {
                        it.copy(
                            savingsTrendsData = trendsData,
                            monthlyChartData = monthlyChartData,
                            availableMonths = availableMonths,
                            selectedChartMonth = availableMonths.firstOrNull() ?: "Jan 2024",
                            isSavingsTrendsLoading = false,
                            savingsTrendsError = null
                        )
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to load savings trends"
                    println("❌ DEBUG: Savings trends failed - $error")
                    _uiState.update {
                        it.copy(
                            isSavingsTrendsLoading = false,
                            savingsTrendsError = error
                        )
                    }
                }
            } catch (e: Exception) {
                println("❌ DEBUG: Savings trends exception - ${e.message}")
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isSavingsTrendsLoading = false,
                        savingsTrendsError = e.message ?: "Error loading savings data"
                    )
                }
            }
        }
    }

    private fun generateMonthLabels(savingsData: List<SavingsMonthlyData>): List<String> {
        return savingsData.map { it.displayName }
    }

    private fun createChartDataFromSavingsTrends(savingsData: List<SavingsMonthlyData>): MonthlyChartData {
        // Create DailyData for the chart (one per month)
        val dailyData = savingsData.map { monthlyData ->
            DailyData(
                date = "${monthlyData.year}-${monthlyData.month.toString().padStart(2, '0')}-15",
                dayLabel = monthlyData.displayName,
                income = 0.0,
                expenses = 0.0,
                savings = monthlyData.savedAmount
            )
        }

        // Create MonthlyData list
        val monthlyDataList = savingsData.map { savings ->
            MonthlyData(
                month = savings.displayName,
                income = 0.0,
                expenses = 0.0,
                monthNumber = savings.month
            )
        }

        return MonthlyChartData(
            months = monthlyDataList,
            days = dailyData,
            savingsData = savingsData,
            selectedFilter = ChartFilter.EXPENSES,
            selectedPeriod = PeriodFilter.MONTH,
            dateRange = DateRange(
                startDate = getStartDateFromSavingsData(savingsData),
                endDate = getEndDateFromSavingsData(savingsData)
            )
        )
    }

    private fun getStartDateFromSavingsData(data: List<SavingsMonthlyData>): String {
        if (data.isEmpty()) return getDefaultStartDate()
        val earliest = data.minByOrNull { it.year * 100 + it.month }!!
        return "${earliest.year}-${earliest.month.toString().padStart(2, '0')}-01"
    }

    private fun getEndDateFromSavingsData(data: List<SavingsMonthlyData>): String {
        if (data.isEmpty()) return getDefaultEndDate()
        val latest = data.maxByOrNull { it.year * 100 + it.month }!!
        return "${latest.year}-${latest.month.toString().padStart(2, '0')}-28"
    }

    // --- BUDGET DATA LOADING ---

    fun loadBudgetData() {
        viewModelScope.launch {
            _uiState.update { it.copy(budgetState = ScreenState.Loading) }
            try {
                val result = getCurrentBudgetUseCase()
                if (result.isSuccess) {
                    val budgetData = result.getOrThrow()
                    _uiState.update {
                        it.copy(
                            budgetState = ScreenState.Success(budgetData),
                            budget = budgetData
                        )
                    }
                } else {
                    loadOfflineBudgetFallback()
                }
            } catch (e: Exception) {
                loadOfflineBudgetFallback()
            }
        }
    }

    fun updateBudget(monthlyAmount: Double?, dailyAmount: Double?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            try {
                val result = updateBudgetUseCase(monthlyAmount, dailyAmount)
                if (result.isSuccess) {
                    val updatedBudget = result.getOrThrow()
                    _uiState.update {
                        it.copy(
                            budgetState = ScreenState.Success(updatedBudget),
                            budget = updatedBudget,
                            isUpdating = false
                        )
                    }
                    DataSyncManager.notifyBudgetUpdated()
                } else {
                    _uiState.update { it.copy(isUpdating = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUpdating = false) }
            }
        }
    }

    // --- SAVINGS GOAL ---

    fun loadSavingsGoal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingsGoalLoading = true) }
            try {
                val result = getCurrentSavingsGoalUseCase()
                if (result.isSuccess) {
                    val goal = result.getOrThrow()
                    _uiState.update {
                        it.copy(
                            savingsGoal = goal,
                            isSavingsGoalLoading = false
                        )
                    }
                } else {
                    loadOfflineSavingsGoalFallback()
                }
            } catch (e: Exception) {
                loadOfflineSavingsGoalFallback()
            }
        }
    }

    fun updateSavingsGoal(targetAmount: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingsGoalUpdating = true) }
            try {
                val result = updateSavingsGoalUseCase(targetAmount)
                if (result.isSuccess) {
                    val updatedGoal = result.getOrThrow()
                    upsertSavingsGoalLocally(updatedGoal, isSynced = true)
                    _uiState.update { it.copy(savingsGoal = updatedGoal, isSavingsGoalUpdating = false) }
                } else {
                    saveSavingsGoalOffline(targetAmount)
                }
            } catch (e: Exception) {
                saveSavingsGoalOffline(targetAmount)
            }
        }
    }

    // --- CATEGORY LIMITS ---

    fun loadCategoryLimits() {
        viewModelScope.launch {
            _uiState.update { it.copy(categoryLimitsState = ScreenState.Loading, isCategoryLimitsLoading = true) }
            try {
                val result = getCategoryLimitsUseCase()
                if (result.isSuccess) {
                    val categoryLimits = result.getOrThrow()
                    _uiState.update {
                        it.copy(
                            categoryLimitsState = ScreenState.Success(categoryLimits),
                            overBudgetCategories = calculateOverBudgetCategories(categoryLimits),
                            isCategoryLimitsLoading = false,
                            isUpdatingCategoryLimit = false,
                            isDeletingCategoryLimit = false
                        )
                    }
                } else {
                    val exception = result.exceptionOrNull() ?: Exception("Failed to load category limits")
                    _uiState.update {
                        it.copy(
                            categoryLimitsState = ScreenState.Error(
                                com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(exception),
                                retryAction = { loadCategoryLimits() }
                            ),
                            isCategoryLimitsLoading = false,
                            isUpdatingCategoryLimit = false,
                            isDeletingCategoryLimit = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        categoryLimitsState = ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                            retryAction = { loadCategoryLimits() }
                        ),
                        isCategoryLimitsLoading = false,
                        isUpdatingCategoryLimit = false,
                        isDeletingCategoryLimit = false
                    )
                }
            }
        }
    }

    fun updateCategoryLimit(categoryId: Int, monthlyLimit: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingCategoryLimit = true) }
            try {
                val result = updateCategoryLimitUseCase(categoryId, monthlyLimit)
                if (result.isSuccess) {
                    loadCategoryLimits()
                } else {
                    _uiState.update {
                        it.copy(
                            categoryLimitError = "Update failed",
                            isUpdatingCategoryLimit = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        categoryLimitError = "Update failed",
                        isUpdatingCategoryLimit = false
                    )
                }
            }
        }
    }

    private fun calculateOverBudgetCategories(categoryLimits: List<CategoryLimitOverview>): List<CategoryLimitOverview> {
        return categoryLimits.filter { limit ->
            (limit.monthlySpent.toDoubleOrNull() ?: 0.0) > (limit.monthlyLimit.toDoubleOrNull() ?: 0.0)
        }
    }

    fun deleteCategoryLimit(categoryId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingCategoryLimit = true) }
            try {
                val result = deleteCategoryLimitUseCase(categoryId)
                if (result.isSuccess) loadCategoryLimits()
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeletingCategoryLimit = false) }
            }
        }
    }

    fun refreshOnScreenFocus() {
        loadAllData()
    }

    private fun getDefaultStartDate(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -6)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }

    private fun getDefaultEndDate(): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }

    private suspend fun loadOfflineBudgetFallback() {
        try {
            val now = LocalDate.now()
            val transactions = transactionDao.getTransactions()

            val monthlyExpense = transactions
                .filter { parseLocalDate(it.transactionDate)?.let { date ->
                    date.year == now.year && date.monthValue == now.monthValue
                } ?: false }
                .filter { it.type.equals("expense", ignoreCase = true) }
                .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

            val todayExpense = transactions
                .filter { parseLocalDate(it.transactionDate) == now }
                .filter { it.type.equals("expense", ignoreCase = true) }
                .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

            val fallbackBudget = Budget(
                id = 0,
                month = now.monthValue,
                year = now.year,
                monthlyLimit = 0.0,
                dailyLimit = 0.0,
                monthlySpent = monthlyExpense,
                dailySpent = todayExpense,
                lastUpdatedDate = now.toString(),
                createdAt = ""
            )

            _uiState.update {
                it.copy(
                    budgetState = ScreenState.Success(fallbackBudget),
                    budget = fallbackBudget
                )
            }
        } catch (e: Exception) {
            val now = LocalDate.now()
            val safeDefaultBudget = Budget(
                id = 0,
                month = now.monthValue,
                year = now.year,
                monthlyLimit = 0.0,
                dailyLimit = 0.0,
                monthlySpent = 0.0,
                dailySpent = 0.0,
                lastUpdatedDate = now.toString(),
                createdAt = ""
            )
            _uiState.update {
                it.copy(
                    budgetState = ScreenState.Success(safeDefaultBudget),
                    budget = safeDefaultBudget
                )
            }
        }
    }

    private suspend fun loadOfflineSavingsGoalFallback() {
        val now = LocalDate.now()
        val monthGoal = monthlySavingsGoalDao.getMonthlySavingsGoalByMonth(now.year, now.monthValue)
        val fallbackGoal = when {
            monthGoal != null -> SavingsGoal(
                id = monthGoal.id,
                month = monthGoal.month,
                year = monthGoal.year,
                targetAmount = monthGoal.targetAmount,
                currentSaved = monthGoal.currentSaved
            )
            else -> null
        }

        _uiState.update {
            it.copy(
                savingsGoal = fallbackGoal,
                isSavingsGoalLoading = false,
                savingsGoalError = null
            )
        }
    }

    private suspend fun saveSavingsGoalOffline(targetAmount: Double) {
        val now = LocalDate.now()
        val existing = monthlySavingsGoalDao.getMonthlySavingsGoalByMonth(now.year, now.monthValue)
        val goalId = existing?.id ?: generateOfflineGoalId()
        val currentSaved = existing?.currentSaved ?: _uiState.value.savingsGoal?.currentSaved ?: 0.0

        val localGoal = SavingsGoal(
            id = goalId,
            month = now.monthValue,
            year = now.year,
            targetAmount = targetAmount,
            currentSaved = currentSaved
        )

        upsertSavingsGoalLocally(localGoal, isSynced = false)
        _uiState.update {
            it.copy(
                savingsGoal = localGoal,
                isSavingsGoalUpdating = false,
                savingsGoalError = null
            )
        }
    }

    private suspend fun upsertSavingsGoalLocally(goal: SavingsGoal, isSynced: Boolean) {
        monthlySavingsGoalDao.upsertMonthlySavingsGoal(
            MonthlySavingsGoalEntity(
                id = goal.id,
                month = goal.month,
                year = goal.year,
                targetAmount = goal.targetAmount,
                currentSaved = goal.currentSaved,
                updatedAt = System.currentTimeMillis(),
                isSynced = isSynced
            )
        )
    }

    private fun generateOfflineGoalId(): Int {
        return -((System.currentTimeMillis() % Int.MAX_VALUE).toInt().coerceAtLeast(1))
    }

    fun onPeriodSelected(period: Int) {
        _uiState.update { it.copy(selectedPeriod = period) }
        loadSavingsTrends()
    }

    // NEW: Load savings forecast
    private fun loadSavingsForecast() {
        viewModelScope.launch {
            try {
                val result = getSavingsForecastUseCase(3)
                if (result.isSuccess) {
                    _uiState.update { it.copy(savingsForecast = result.getOrNull()) }
                }
            } catch (e: Exception) {
                // Silently fail - forecast is optional
            }
        }
    }
}

private fun parseLocalDate(rawDate: String?): LocalDate? {
    if (rawDate.isNullOrBlank()) return null
    return try {
        LocalDate.parse(rawDate.take(10))
    } catch (e: Exception) {
        null
    }
}

// --- UI STATE MODEL ---

data class GoalScreenState(
    val budgetState: ScreenState<Budget> = ScreenState.Loading,
    val budget: Budget? = null,
    val categoryLimitsState: ScreenState<List<CategoryLimitOverview>> = ScreenState.Loading,
    val overBudgetCategories: List<CategoryLimitOverview> = emptyList(),
    val isNotificationsEnabled: Boolean = true,
    val isUpdating: Boolean = false,
    val isUpdatingCategoryLimit: Boolean = false,
    val isDeletingCategoryLimit: Boolean = false,
    val isCategoryLimitsLoading: Boolean = false,
    val categoryLimitError: String? = null,

    // Savings Goal
    val savingsGoal: SavingsGoal? = null,
    val isSavingsGoalLoading: Boolean = false,
    val isSavingsGoalUpdating: Boolean = false,
    val savingsGoalError: String? = null,

    // Savings Trends
    val savingsTrendsData: SavingsTrendsData? = null,
    val isSavingsTrendsLoading: Boolean = false,
    val savingsTrendsError: String? = null,

    // Chart Data
    val monthlyChartData: MonthlyChartData? = null,

    // NEW Forecast Data
    val savingsForecast: com.example.domain.transaction.model.SavingsForecastData? = null,

    // Chart Controls
    val selectedChartMonth: String = "Jan 2024",
    val availableMonths: List<String> = emptyList(),
    val selectedPeriod: Int = 6, // Default to 6 months
    val availablePeriods: List<Int> = listOf(3, 6, 12), // 3M, 6M, 1Y
    val selectedStartDate: Long? = null,
    val selectedEndDate: Long? = null,
    val showDateRangePicker: Boolean = false,

    // Balance Metrics
    val balanceState: ScreenState<TotalBalance?> = ScreenState.Loading,
    val totalBalance: TotalBalance? = null,
    val balanceComparison: String = "+2.5% vs Last month",

    // Goals
    val goalsState: ScreenState<List<Goal>> = ScreenState.Loading,
    val goals: List<Goal> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.IDLE
)