package com.example.moneymate.ui.screens.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.dao.GoalDao
import com.example.data.database.dao.BudgetDao
import com.example.data.database.dao.MonthlySavingsGoalDao
import com.example.data.offline.MonthlySavingsLocalRecalculator
import com.example.data.database.dao.TransactionDao
import com.example.data.database.dao.WalletDao
import com.example.data.database.entity.BudgetEntity
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
    private val walletDao: WalletDao,
    private val goalDao: GoalDao,
    private val monthlySavingsGoalDao: MonthlySavingsGoalDao,
    private val monthlySavingsLocalRecalculator: MonthlySavingsLocalRecalculator,
    private val budgetDao: BudgetDao
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
                if (isOnline) {
                    syncPendingSavingsGoalUpdates()
                    syncPendingBudgetUpdates()
                }
            }
        }
    }

    private fun syncPendingSavingsGoalUpdates() {
        viewModelScope.launch {
            try {
                val pending = monthlySavingsGoalDao.getUnsyncedGoals()
                if (pending.isEmpty()) return@launch

                println("📤 GOALS: Syncing ${pending.size} offline savings goal update(s)")
                pending.forEach { local ->
                    val target = local.targetAmount
                    val result = updateSavingsGoalUseCase(target)
                    if (result.isSuccess) {
                        val updated = result.getOrThrow()
                        upsertSavingsGoalLocally(updated, isSynced = true)
                        monthlySavingsGoalDao.markGoalsSynced(listOf(local.id), System.currentTimeMillis())
                        monthlySavingsLocalRecalculator.recalculateAllCachedMonths()
                        println("✅ GOALS: Synced savings goal target=$target (month=${local.month}, year=${local.year})")
                    } else {
                        println("⚠️ GOALS: Failed to sync savings goal target=$target: ${result.exceptionOrNull()?.message}")
                    }
                }
                // Refresh from backend after attempting push
                loadSavingsGoal()
            } catch (e: Exception) {
                println("❌ GOALS: Exception syncing offline savings goals: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun syncPendingBudgetUpdates() {
        viewModelScope.launch {
            try {
                val pending = budgetDao.getUnsyncedBudgets()
                if (pending.isEmpty()) return@launch

                println("📤 GOALS: Syncing ${pending.size} offline budget update(s)")
                pending.forEach { local ->
                    val result = updateBudgetUseCase(local.monthlyLimit, local.dailyLimit)
                    if (result.isSuccess) {
                        val updated = result.getOrThrow()
                        // Cache returned authoritative values as synced
                        budgetDao.upsertBudget(
                            BudgetEntity(
                                id = updated.id,
                                month = updated.month,
                                year = updated.year,
                                monthlyLimit = updated.monthlyLimit,
                                dailyLimit = updated.dailyLimit,
                                monthlySpent = updated.monthlySpent,
                                dailySpent = updated.dailySpent,
                                lastUpdatedDate = updated.lastUpdatedDate,
                                createdAt = updated.createdAt,
                                updatedAt = System.currentTimeMillis(),
                                isSynced = true
                            )
                        )
                        budgetDao.markBudgetsSynced(listOf(local.id), System.currentTimeMillis())
                        println("✅ GOALS: Synced budget update (month=${local.month}, year=${local.year})")
                    } else {
                        println("⚠️ GOALS: Failed to sync budget update: ${result.exceptionOrNull()?.message}")
                    }
                }
                loadBudgetData()
            } catch (e: Exception) {
                println("❌ GOALS: Exception syncing offline budgets: ${e.message}")
                e.printStackTrace()
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
                    loadOfflineBalanceFallback(exception)
                }
            } catch (e: Exception) {
                loadOfflineBalanceFallback(e)
            }
        }
    }

    private suspend fun loadOfflineBalanceFallback(cause: Throwable) {
        try {
            val wallets = walletDao.getWallets()
            val totalBalanceValue = wallets.sumOf { (it.balance ?: it.initialBalance).toDoubleOrNull() ?: 0.0 }
            val offlineBalance = TotalBalance(
                totalBalance = totalBalanceValue,
                currency = wallets.firstOrNull()?.currency ?: "USD",
                breakdown = emptyMap()
            )
            _uiState.update {
                it.copy(
                    balanceState = ScreenState.Success(offlineBalance),
                    totalBalance = offlineBalance
                )
            }
            println("📱 GOALS: Loaded balance from Room fallback due to: ${cause.message}")
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    balanceState = ScreenState.Error(
                        com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(cause),
                        retryAction = { loadTotalBalance() }
                    )
                )
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
                    val unsyncedIds = try {
                        goalDao.getUnsyncedGoals().map { it.id }.toSet()
                    } catch (_: Exception) {
                        emptySet()
                    }
                    _uiState.update {
                        it.copy(
                            goalsState = if (goals.isEmpty()) ScreenState.Empty else ScreenState.Success(goals),
                            goals = goals,
                            unsyncedGoalIds = unsyncedIds
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
                    try {
                        budgetDao.upsertBudget(
                            BudgetEntity(
                                id = budgetData.id,
                                month = budgetData.month,
                                year = budgetData.year,
                                monthlyLimit = budgetData.monthlyLimit,
                                dailyLimit = budgetData.dailyLimit,
                                monthlySpent = budgetData.monthlySpent,
                                dailySpent = budgetData.dailySpent,
                                lastUpdatedDate = budgetData.lastUpdatedDate,
                                createdAt = budgetData.createdAt,
                                updatedAt = System.currentTimeMillis(),
                                isSynced = true
                            )
                        )
                        println("📦 GOALS: Cached budget to Room for ${budgetData.month}/${budgetData.year}")
                    } catch (e: Exception) {
                        println("❌ GOALS: Failed to cache budget to Room: ${e.message}")
                        e.printStackTrace()
                    }
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
                // IMPORTANT: Backend appears to require both fields, not partial updates.
                // So when user edits only one value, we send the other from current state/cache.
                val current = _uiState.value.budget
                val effectiveMonthly = monthlyAmount ?: current?.monthlyLimit
                val effectiveDaily = dailyAmount ?: current?.dailyLimit

                val result = updateBudgetUseCase(effectiveMonthly, effectiveDaily)
                if (result.isSuccess) {
                    val updatedBudget = result.getOrThrow()
                    try {
                        budgetDao.upsertBudget(
                            BudgetEntity(
                                id = updatedBudget.id,
                                month = updatedBudget.month,
                                year = updatedBudget.year,
                                monthlyLimit = updatedBudget.monthlyLimit,
                                dailyLimit = updatedBudget.dailyLimit,
                                monthlySpent = updatedBudget.monthlySpent,
                                dailySpent = updatedBudget.dailySpent,
                                lastUpdatedDate = updatedBudget.lastUpdatedDate,
                                createdAt = updatedBudget.createdAt,
                                updatedAt = System.currentTimeMillis(),
                                isSynced = true
                            )
                        )
                    } catch (_: Exception) {
                        // caching failure shouldn't block UI
                    }
                    _uiState.update {
                        it.copy(
                            budgetState = ScreenState.Success(updatedBudget),
                            budget = updatedBudget,
                            isUpdating = false
                        )
                    }
                    DataSyncManager.notifyBudgetUpdated()
                } else {
                    saveBudgetOffline(effectiveMonthly, effectiveDaily)
                }
            } catch (e: Exception) {
                val current = _uiState.value.budget
                val effectiveMonthly = monthlyAmount ?: current?.monthlyLimit
                val effectiveDaily = dailyAmount ?: current?.dailyLimit
                saveBudgetOffline(effectiveMonthly, effectiveDaily)
            }
        }
    }

    private suspend fun saveBudgetOffline(monthlyAmount: Double?, dailyAmount: Double?) {
        try {
            val now = LocalDate.now()
            val existing = budgetDao.getBudgetByMonth(now.year, now.monthValue)
            val current = existing ?: BudgetEntity(
                id = 0,
                month = now.monthValue,
                year = now.year,
                monthlyLimit = 0.0,
                dailyLimit = 0.0,
                monthlySpent = 0.0,
                dailySpent = 0.0,
                lastUpdatedDate = now.toString(),
                createdAt = "",
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )

            val updatedLocal = current.copy(
                monthlyLimit = monthlyAmount ?: current.monthlyLimit,
                dailyLimit = dailyAmount ?: current.dailyLimit,
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
            budgetDao.upsertBudget(updatedLocal)

            val updatedDomain = Budget(
                id = updatedLocal.id,
                month = updatedLocal.month,
                year = updatedLocal.year,
                monthlyLimit = updatedLocal.monthlyLimit,
                dailyLimit = updatedLocal.dailyLimit,
                monthlySpent = updatedLocal.monthlySpent,
                dailySpent = updatedLocal.dailySpent,
                lastUpdatedDate = updatedLocal.lastUpdatedDate,
                createdAt = updatedLocal.createdAt
            )
            _uiState.update {
                it.copy(
                    budgetState = ScreenState.Success(updatedDomain),
                    budget = updatedDomain,
                    isUpdating = false
                )
            }
            println("📦 GOALS: Saved budget update offline (monthly=${updatedLocal.monthlyLimit}, daily=${updatedLocal.dailyLimit})")
            DataSyncManager.notifyBudgetUpdated()
        } catch (e: Exception) {
            _uiState.update { it.copy(isUpdating = false) }
            println("❌ GOALS: Failed to save budget offline: ${e.message}")
            e.printStackTrace()
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
                    val roomGoal =
                        monthlySavingsGoalDao.getMonthlySavingsGoalByMonth(goal.year, goal.month)
                    val mergedGoal =
                        if (roomGoal != null) {
                            val mergedSaved = maxOf(goal.currentSaved, roomGoal.currentSaved)
                            if (mergedSaved > goal.currentSaved + 1e-6) {
                                println(
                                    "📦 GOALS: Savings progress using max(API=${goal.currentSaved}, Room=${roomGoal.currentSaved})=$mergedSaved"
                                )
                            }
                            goal.copy(currentSaved = mergedSaved)
                        } else {
                            goal
                        }
                    _uiState.update {
                        it.copy(
                            savingsGoal = mergedGoal,
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
                    monthlySavingsLocalRecalculator.recalculateAllCachedMonths()
                    applyRoomSavingsToUi()
                    _uiState.update { it.copy(isSavingsGoalUpdating = false) }
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
            val cachedBudget = try {
                budgetDao.getBudgetByMonth(now.year, now.monthValue)
            } catch (e: Exception) {
                println("❌ GOALS: Failed to read cached budget from Room: ${e.message}")
                e.printStackTrace()
                null
            }

            if (cachedBudget != null) {
                val budgetFromCache = Budget(
                    id = cachedBudget.id,
                    month = cachedBudget.month,
                    year = cachedBudget.year,
                    monthlyLimit = cachedBudget.monthlyLimit,
                    dailyLimit = cachedBudget.dailyLimit,
                    monthlySpent = cachedBudget.monthlySpent,
                    dailySpent = cachedBudget.dailySpent,
                    lastUpdatedDate = cachedBudget.lastUpdatedDate,
                    createdAt = cachedBudget.createdAt
                )
                _uiState.update {
                    it.copy(
                        budgetState = ScreenState.Success(budgetFromCache),
                        budget = budgetFromCache
                    )
                }
                println("📦 GOALS: Loaded cached budget from Room for ${cachedBudget.month}/${cachedBudget.year}")
                return
            }

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
            println("📦 GOALS: No cached budget found; using local spending fallback")
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
        monthlySavingsLocalRecalculator.recalculateAllCachedMonths()
        applyRoomSavingsToUi()
        _uiState.update {
            it.copy(
                isSavingsGoalUpdating = false,
                savingsGoalError = null
            )
        }
    }

    private suspend fun applyRoomSavingsToUi() {
        val now = LocalDate.now()
        val row = monthlySavingsGoalDao.getMonthlySavingsGoalByMonth(now.year, now.monthValue) ?: return
        val pct = if (row.targetAmount > 1e-12) row.currentSaved / row.targetAmount * 100.0 else 0.0
        println(
            "SAVINGS_PROGRESS: GoalScreen UI month=${row.year}-${row.month} saved=${row.currentSaved} " +
                "target=${row.targetAmount} (${"%.1f".format(pct)}%)"
        )
        _uiState.update {
            it.copy(
                savingsGoal = SavingsGoal(
                    id = row.id,
                    month = row.month,
                    year = row.year,
                    targetAmount = row.targetAmount,
                    currentSaved = row.currentSaved
                )
            )
        }
    }

    private suspend fun upsertSavingsGoalLocally(goal: SavingsGoal, isSynced: Boolean) {
        val existing = monthlySavingsGoalDao.getMonthlySavingsGoalByMonth(goal.year, goal.month)
        val anchorToStore = if (isSynced) {
            null
        } else {
            existing?.savingsTxNetAnchor
        }
        monthlySavingsGoalDao.upsertMonthlySavingsGoal(
            MonthlySavingsGoalEntity(
                id = goal.id,
                month = goal.month,
                year = goal.year,
                targetAmount = goal.targetAmount,
                currentSaved = goal.currentSaved,
                savingsTxNetAnchor = anchorToStore,
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
    val unsyncedGoalIds: Set<Int> = emptySet(),
    val syncStatus: SyncStatus = SyncStatus.IDLE
)