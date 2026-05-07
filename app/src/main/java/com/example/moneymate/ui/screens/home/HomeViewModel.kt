package com.example.moneymate.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.dao.BudgetDao
import com.example.data.database.dao.MonthlySavingsGoalDao
import com.example.data.database.dao.TransactionDao
import com.example.data.database.dao.WalletDao
import com.example.data.database.entity.BudgetEntity
import com.example.data.database.entity.MonthlySavingsGoalEntity
import com.example.domain.budget.model.Budget
import com.example.domain.budget.usecase.GetCurrentBudgetUseCase
import com.example.domain.savingsGoal.model.SavingsGoal
import com.example.domain.savingsGoal.usecase.GetCurrentSavingsGoalUseCase
import com.example.domain.transaction.model.TransactionEntity
import com.example.domain.transaction.usecase.GetTransactionsUseCase
import com.example.domain.user.model.UserDetailedData
import com.example.domain.user.usecase.GetUserDetailedUseCase
import com.example.domain.wallet.model.TotalBalance
import com.example.domain.wallet.usecase.GetTotalBalanceUseCase
import com.example.moneymate.utils.DataSyncManager
import com.example.moneymate.utils.ScreenState
import com.example.moneymate.utils.network.ConnectivityObserver
import com.example.moneymate.ui.offline.SyncStatus
import com.example.domain.user.model.StatsData
import com.example.domain.user.model.User
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(
    private val getUserDetailedUseCase: GetUserDetailedUseCase,
    private val getTotalBalanceUseCase: GetTotalBalanceUseCase,
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val getBudgetUseCase: GetCurrentBudgetUseCase,
    private val getCurrentSavingsGoalUseCase: GetCurrentSavingsGoalUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao,
    private val monthlySavingsGoalDao: MonthlySavingsGoalDao,  // ← NEW
    private val budgetDao: BudgetDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeScreenState())
    val uiState: StateFlow<HomeScreenState> = _uiState.asStateFlow()

    init {
        println("DEBUG: HomeViewModel init - loading data from Room FIRST")
        loadFromRoomWhenOffline()
        loadAllData()
        setupDataChangeListener()
        observeConnectivity()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { isOnline ->
                _uiState.update { it.copy(syncStatus = if (isOnline) SyncStatus.SYNCING else SyncStatus.OFFLINE) }
                if (isOnline) {
                    refreshFromRemote()
                }
            }
        }
    }

    private fun setupDataChangeListener() {
        viewModelScope.launch {
            DataSyncManager.dataChangeEvents.collect { event ->
                when (event) {
                    is DataSyncManager.DataChangeEvent.TransactionsUpdated,
                    is DataSyncManager.DataChangeEvent.WalletsUpdated -> {
                        println("🔄 DEBUG: HomeViewModel - Data changed, reloading from Room")
                        loadFromRoomWhenOffline()
                    }
                    else -> {}
                }
            }
        }
    }

    fun loadAllData() {
        viewModelScope.launch {
            val isOnline = connectivityObserver.isOnline.first()
            if (isOnline) {
                refreshFromRemote()
            }
        }
    }

    private fun refreshFromRemote() {
        viewModelScope.launch {
            println("📱 DEBUG: HomeViewModel - Refreshing from remote")
            loadUserData()
            loadTotalBalance()
            loadFinancialOverview()
            loadRecentTransactions()
            loadBudgetData()
            loadSavingsData()
        }
    }

    fun loadBudgetData() {
        viewModelScope.launch {
            try {
                val result = getBudgetUseCase()
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
                        println("📦 Home Budget: Cached budget to Room for ${budgetData.month}/${budgetData.year}")
                    } catch (e: Exception) {
                        println("❌ Home Budget: Failed to cache budget to Room: ${e.message}")
                        e.printStackTrace()
                    }
                    _uiState.update { it.copy(budgetState = ScreenState.Success(budgetData)) }
                }
            } catch (e: Exception) {
                println("DEBUG: HomeViewModel - Error loading budget: ${e.message}")
            }
        }
    }

    fun loadUserData() {
        viewModelScope.launch {
            try {
                val data = getUserDetailedUseCase()
                _uiState.update { it.copy(userDataState = ScreenState.Success(data)) }
            } catch (e: Exception) {
                println("DEBUG: HomeViewModel - Error loading user: ${e.message}")
            }
        }
    }

    fun loadTotalBalance() {
        viewModelScope.launch {
            try {
                val result = getTotalBalanceUseCase()
                if (result.isSuccess) {
                    val balance = result.getOrNull()
                    _uiState.update { it.copy(balanceState = ScreenState.Success(balance)) }
                }
            } catch (e: Exception) {
                println("DEBUG: HomeViewModel - Error loading balance: ${e.message}")
            }
        }
    }

    fun loadFinancialOverview() {
        viewModelScope.launch {
            try {
                val result = getTransactionsUseCase()
                if (result.isSuccess) {
                    val transactions = result.getOrThrow()
                    val (totalIncome, totalExpense) = calculateFinancialTotals(transactions)
                    _uiState.update {
                        it.copy(
                            financialOverviewState = ScreenState.Success(
                                FinancialOverviewData(totalIncome = totalIncome, totalExpense = totalExpense)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: HomeViewModel - Error loading financial overview: ${e.message}")
            }
        }
    }

    fun loadRecentTransactions() {
        viewModelScope.launch {
            try {
                val result = getTransactionsUseCase()
                if (result.isSuccess) {
                    val allTransactions = result.getOrThrow()
                    val sortedTransactions = allTransactions.sortedWith(
                        compareByDescending<TransactionEntity> {
                            try {
                                java.time.LocalDate.parse(it.transactionDate)
                            } catch (e: Exception) {
                                java.time.LocalDate.MIN
                            }
                        }.thenByDescending { it.id ?: 0 }
                    )
                    val recentTransactions = sortedTransactions.take(5)
                    _uiState.update {
                        it.copy(recentTransactionsState = ScreenState.Success(recentTransactions))
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: HomeViewModel - Error loading recent transactions: ${e.message}")
            }
        }
    }

    fun refreshOnScreenFocus() {
        viewModelScope.launch {
            println("🔄 DEBUG: HomeViewModel - Screen focused")
            loadFromRoomWhenOffline()
            val isOnline = connectivityObserver.isOnline.first()
            if (isOnline) {
                refreshFromRemote()
            }
        }
    }

    private fun calculateFinancialTotals(transactions: List<TransactionEntity>): Pair<Double, Double> {
        var totalIncome = 0.0
        var totalExpense = 0.0
        transactions.forEach { transaction ->
            val amount = try {
                transaction.amount.toDouble()
            } catch (e: NumberFormatException) {
                0.0
            }
            when (transaction.type.lowercase()) {
                "income" -> totalIncome += amount
                "expense" -> totalExpense += amount
            }
        }
        return Pair(totalIncome, totalExpense)
    }

    fun loadSavingsData() {
        viewModelScope.launch {
            try {
                val result = getCurrentSavingsGoalUseCase()
                if (result.isSuccess) {
                    val savingsGoal = result.getOrNull()
                    if (savingsGoal != null) {
                        try {
                            monthlySavingsGoalDao.upsertMonthlySavingsGoal(
                                MonthlySavingsGoalEntity(
                                    id = savingsGoal.id,
                                    month = savingsGoal.month,
                                    year = savingsGoal.year,
                                    targetAmount = savingsGoal.targetAmount,
                                    currentSaved = savingsGoal.currentSaved,
                                    updatedAt = System.currentTimeMillis(),
                                    isSynced = true
                                )
                            )
                            println("📦 Home Savings: Cached savings goal to Room for ${savingsGoal.month}/${savingsGoal.year}")
                        } catch (e: Exception) {
                            println("❌ Home Savings: Failed to cache savings goal to Room: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    _uiState.update { it.copy(savingsGoalState = ScreenState.Success(savingsGoal)) }
                }
            } catch (e: Exception) {
                println("DEBUG: HomeViewModel - Error loading savings: ${e.message}")
            }
        }
    }

    // ============================================================
    // MAIN OFFLINE LOADER - Complete version
    // ============================================================
    private fun loadFromRoomWhenOffline() {
        viewModelScope.launch {
            println("📱 DEBUG: HomeViewModel - Loading from Room (offline-first)")

            try {
                // 1. Load wallets and calculate balance
                val wallets = walletDao.getWallets()
                val totalBalanceValue = wallets.sumOf { (it.balance ?: it.initialBalance).toDouble() }

                _uiState.update {
                    it.copy(
                        balanceState = ScreenState.Success(
                            TotalBalance(
                                totalBalance = totalBalanceValue,
                                currency = "USD",
                                breakdown = emptyMap()
                            )
                        )
                    )
                }
                println("📱 DEBUG: HomeViewModel - Balance loaded: $totalBalanceValue from ${wallets.size} wallets")

                // 2. Load transactions
                val allTransactions = transactionDao.getTransactions()
                val domainTransactions = allTransactions.map { convertTransactionEntityToDomain(it) }

                // 3. Recent transactions
                val sortedTransactions = domainTransactions.sortedWith(
                    compareByDescending<TransactionEntity> {
                        try {
                            java.time.LocalDate.parse(it.transactionDate)
                        } catch (e: Exception) {
                            java.time.LocalDate.MIN
                        }
                    }.thenByDescending { it.id ?: 0 }
                )
                val recentTransactions = sortedTransactions.take(5)

                _uiState.update {
                    it.copy(
                        recentTransactionsState = if (recentTransactions.isEmpty()) ScreenState.Empty
                        else ScreenState.Success(recentTransactions)
                    )
                }
                println("📱 DEBUG: HomeViewModel - Recent transactions: ${recentTransactions.size}")

                // 4. Financial overview
                var totalIncome = 0.0
                var totalExpense = 0.0
                allTransactions.forEach { t ->
                    val amount = t.amount.toDouble()
                    when (t.type.lowercase()) {
                        "income" -> totalIncome += amount
                        "expense" -> totalExpense += amount
                    }
                }

                _uiState.update {
                    it.copy(
                        financialOverviewState = ScreenState.Success(
                            FinancialOverviewData(totalIncome = totalIncome, totalExpense = totalExpense)
                        )
                    )
                }
                println("📱 DEBUG: HomeViewModel - Financial overview: Income=$totalIncome, Expense=$totalExpense")

                // 5. Load Monthly Savings Goal (from new entity)
                try {
                    val currentDate = java.time.LocalDate.now()
                    val currentMonth = currentDate.monthValue
                    val currentYear = currentDate.year

                    val savingsGoalEntity = monthlySavingsGoalDao.getMonthlySavingsGoalByMonth(currentYear, currentMonth)

                    if (savingsGoalEntity != null) {
                        val savingsGoal = SavingsGoal(
                            id = savingsGoalEntity.id,
                            month = savingsGoalEntity.month,
                            year = savingsGoalEntity.year,
                            targetAmount = savingsGoalEntity.targetAmount,
                            currentSaved = savingsGoalEntity.currentSaved
                        )
                        _uiState.update {
                            it.copy(savingsGoalState = ScreenState.Success(savingsGoal))
                        }
                        println("📱 DEBUG: HomeViewModel - Loaded monthly savings goal: ${savingsGoal.month}/${savingsGoal.year}")
                    } else {
                        // Try to get most recent goal
                        val allGoals = monthlySavingsGoalDao.getMonthlySavingsGoals()
                        val mostRecent = allGoals.firstOrNull()
                        if (mostRecent != null) {
                            val savingsGoal = SavingsGoal(
                                id = mostRecent.id,
                                month = mostRecent.month,
                                year = mostRecent.year,
                                targetAmount = mostRecent.targetAmount,
                                currentSaved = mostRecent.currentSaved
                            )
                            _uiState.update {
                                it.copy(savingsGoalState = ScreenState.Success(savingsGoal))
                            }
                            println("📱 DEBUG: HomeViewModel - Loaded most recent savings goal: ${savingsGoal.month}/${savingsGoal.year}")
                        } else {
                            _uiState.update { it.copy(savingsGoalState = ScreenState.Empty) }
                            println("📱 DEBUG: HomeViewModel - No monthly savings goals found in Room")
                        }
                    }
                } catch (e: Exception) {
                    println("📱 ERROR: HomeViewModel - Failed to load monthly savings goal: ${e.message}")
                    _uiState.update { it.copy(savingsGoalState = ScreenState.Empty) }
                }

                // 6. User data (fallback)
                _uiState.update {
                    it.copy(
                        userDataState = ScreenState.Success(
                            UserDetailedData(
                                user = User(
                                    id = "offline-user",
                                    email = "",
                                    fullName = "Offline Mode",
                                    phoneNumber = null,
                                    dateOfBirth = null,
                                    avatarUrl = null,
                                    defaultCurrency = "USD",
                                    createdAt = ""
                                ),
                                stats = StatsData(
                                    walletCount = wallets.size,
                                    totalTransactions = allTransactions.size,
                                    expenseCount = allTransactions.count { it.type.lowercase() == "expense" },
                                    incomeCount = allTransactions.count { it.type.lowercase() == "income" }
                                )
                            )
                        )
                    )
                }

                // 7. Budget state (offline fallback computed from local transactions)
                val now = LocalDate.now()
                val cachedBudget = try {
                    budgetDao.getBudgetByMonth(now.year, now.monthValue)
                } catch (e: Exception) {
                    println("❌ Home Budget: Failed to read budget from Room: ${e.message}")
                    e.printStackTrace()
                    null
                }

                if (cachedBudget != null) {
                    _uiState.update {
                        it.copy(
                            budgetState = ScreenState.Success(
                                Budget(
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
                            )
                        )
                    }
                    println("📦 Home Budget: Loaded cached budget from Room for ${cachedBudget.month}/${cachedBudget.year}")
                } else {
                    // Last-resort fallback: compute spending only (no budget limit).
                    val monthlyExpense = allTransactions
                        .filter { transaction ->
                            parseLocalDate(transaction.transactionDate)?.let { date ->
                                date.year == now.year && date.monthValue == now.monthValue
                            } ?: false
                        }
                        .filter { it.type.equals("expense", ignoreCase = true) }
                        .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

                    val todayExpense = allTransactions
                        .filter { transaction ->
                            parseLocalDate(transaction.transactionDate) == now
                        }
                        .filter { it.type.equals("expense", ignoreCase = true) }
                        .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

                    _uiState.update {
                        it.copy(
                            budgetState = ScreenState.Success(
                                Budget(
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
                            )
                        )
                    }
                    println("📦 Home Budget: No cached budget found; using local spending fallback")
                }

            } catch (e: Exception) {
                println("📱 ERROR: HomeViewModel - Failed to load from Room: ${e.message}")
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        balanceState = ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                            retryAction = { loadFromRoomWhenOffline() }
                        )
                    )
                }
            }
        }
    }
}

// ============================================================
// DATA CLASSES
// ============================================================

data class HomeScreenState(
    val userDataState: ScreenState<UserDetailedData> = ScreenState.Loading,
    val balanceState: ScreenState<TotalBalance?> = ScreenState.Loading,
    val financialOverviewState: ScreenState<FinancialOverviewData> = ScreenState.Loading,
    val recentTransactionsState: ScreenState<List<TransactionEntity>> = ScreenState.Loading,
    val budgetState: ScreenState<Budget?> = ScreenState.Loading,
    val savingsGoalState: ScreenState<SavingsGoal?> = ScreenState.Loading,
    val syncStatus: SyncStatus = SyncStatus.IDLE
) {
    val isLoading: Boolean
        get() = userDataState is ScreenState.Loading &&
                balanceState is ScreenState.Loading &&
                financialOverviewState is ScreenState.Loading &&
                budgetState is ScreenState.Loading

    val isTotalBalanceLoading: Boolean
        get() = balanceState is ScreenState.Loading

    val userData: UserDetailedData?
        get() = when (userDataState) {
            is ScreenState.Success -> userDataState.data
            else -> null
        }

    val totalBalance: TotalBalance?
        get() = when (balanceState) {
            is ScreenState.Success -> balanceState.data
            else -> null
        }

    val financialOverview: FinancialOverviewData?
        get() = when (financialOverviewState) {
            is ScreenState.Success -> financialOverviewState.data
            else -> null
        }

    val recentTransactions: List<TransactionEntity>?
        get() = when (recentTransactionsState) {
            is ScreenState.Success -> recentTransactionsState.data
            else -> null
        }

    val budgetData: Budget?
        get() = when (budgetState) {
            is ScreenState.Success -> budgetState.data
            else -> null
        }

    val savingsGoal: SavingsGoal?
        get() = when (savingsGoalState) {
            is ScreenState.Success -> savingsGoalState.data
            else -> null
        }
}

data class FinancialOverviewData(
    val totalIncome: Double,
    val totalExpense: Double
)

// ============================================================
// CONVERTER FUNCTIONS
// ============================================================
private fun convertTransactionEntityToDomain(entity: com.example.data.database.entity.TransactionEntity): TransactionEntity {
    return TransactionEntity(
        id = entity.id,
        name = entity.name,
        amount = entity.amount,
        type = entity.type,
        categoryId = entity.categoryId,
        walletId = entity.walletId,
        userId = entity.userId,
        transactionDate = entity.transactionDate,
        note = entity.note,
        createdAt = entity.createdAt
    )
}

private fun parseLocalDate(rawDate: String?): LocalDate? {
    if (rawDate.isNullOrBlank()) return null
    return try {
        LocalDate.parse(rawDate.take(10))
    } catch (e: Exception) {
        null
    }
}