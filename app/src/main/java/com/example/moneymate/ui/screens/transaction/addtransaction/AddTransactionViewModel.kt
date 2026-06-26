package com.example.moneymate.ui.screens.transaction.addtransaction

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.category.model.Category
import com.example.domain.category.usecase.GetExpenseCategoriesUseCase
import com.example.domain.category.usecase.GetIncomeCategoriesUseCase
import com.example.domain.tag.model.Tag
import com.example.domain.tag.usecase.CreateTagUseCase
import com.example.domain.tag.usecase.GetTagsUseCase
import com.example.domain.transaction.model.CreateTransaction
import com.example.domain.transaction.model.TransferPreview
import com.example.domain.transaction.usecase.CreateTransactionUseCase
import com.example.domain.transaction.usecase.CreateTransferUseCase
import com.example.domain.transaction.usecase.GetTransferPreviewUseCase
import com.example.domain.wallet.model.Wallet
import com.example.domain.wallet.usecase.GetWalletsUseCase
import com.example.moneymate.utils.AppError
import com.example.moneymate.utils.CurrencyUtils
import com.example.moneymate.utils.DataSyncManager
import com.example.moneymate.utils.ErrorHandler
import com.example.moneymate.utils.FileUtils
import com.example.moneymate.utils.ScreenState
import com.example.moneymate.utils.network.ConnectivityObserver
import com.example.moneymate.ui.offline.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddTransactionViewModel(
    private val createTransactionUseCase: CreateTransactionUseCase,
    private val createTransferUseCase: CreateTransferUseCase,
    private val getTransferPreviewUseCase: GetTransferPreviewUseCase,
    private val getWalletsUseCase: GetWalletsUseCase,
    private val getIncomeCategoriesUseCase: GetIncomeCategoriesUseCase,
    private val getExpenseCategoriesUseCase: GetExpenseCategoriesUseCase,
    private val getTagsUseCase: GetTagsUseCase,
    private val createTagUseCase: CreateTagUseCase,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionState())
    val uiState: StateFlow<AddTransactionState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    fun onAttachmentsSelected(uris: List<String>) {
        _uiState.value = _uiState.value.copy(attachments = uris)
    }

    init {
        observeConnectivity()
        loadWallets()
        loadTags()

        // Handle categories with offline-first approach
        viewModelScope.launch {
            val isOnline = connectivityObserver.isOnline.first()

            if (isOnline) {
                // Preload categories to cache when online
                println("📦 INIT: Online - preloading categories")
                preloadCategoriesForOffline()
                // Then load categories (will use cache)
                loadCategories()

                // DEBUG: Check Room after 3 seconds
                delay(3000)
                debugRoomCategories()
            } else {
                // Offline - just try to load from cache
                println("📦 INIT: Offline - loading from cache")
                loadCategories()

                // DEBUG: Check Room immediately
                debugRoomCategories()
            }
        }
    }

    // Add this function to AddTransactionViewModel
    private fun debugRoomCategories() {
        viewModelScope.launch {
            delay(2000) // Wait for any pending operations
            println("🔍 DEBUG: Checking Room categories...")

            // You'll need to access categoryDao. Since you don't have it directly,
            // we'll use the repository and check what it returns
            val expenseResult = getExpenseCategoriesUseCase()
            if (expenseResult.isSuccess) {
                val categories = expenseResult.getOrThrow()
                println("🔍 DEBUG: getExpenseCategoriesUseCase returned ${categories.size} categories")
            } else {
                println("🔍 DEBUG: getExpenseCategoriesUseCase failed: ${expenseResult.exceptionOrNull()?.message}")
            }

            val incomeResult = getIncomeCategoriesUseCase()
            if (incomeResult.isSuccess) {
                val categories = incomeResult.getOrThrow()
                println("🔍 DEBUG: getIncomeCategoriesUseCase returned ${categories.size} categories")
            } else {
                println("🔍 DEBUG: getIncomeCategoriesUseCase failed: ${incomeResult.exceptionOrNull()?.message}")
            }
        }
    }
    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { isOnline ->
                _uiState.value = _uiState.value.copy(
                    syncStatus = if (isOnline) SyncStatus.IDLE else SyncStatus.OFFLINE
                )
                // When coming back online, ensure categories are cached
                if (isOnline) {
                    preloadCategoriesForOffline()
                }
            }
        }
    }

    // NEW: Preload categories to Room for offline access
    private fun preloadCategoriesForOffline() {
        viewModelScope.launch {
            val isOnline = connectivityObserver.isOnline.first()
            if (!isOnline) {
                println("📦 OFFLINE: Device offline, skipping preload")
                return@launch
            }

            println("📦 OFFLINE: Preloading categories for offline use...")

            // Force fetch and cache both types
            val expenseResult = getExpenseCategoriesUseCase()
            val incomeResult = getIncomeCategoriesUseCase()

            if (expenseResult.isSuccess) {
                val expenseCategories = expenseResult.getOrThrow()
                println("✅ OFFLINE: Preloaded ${expenseCategories.size} expense categories")
            } else {
                println("⚠️ OFFLINE: Failed to preload expense categories: ${expenseResult.exceptionOrNull()?.message}")
            }

            if (incomeResult.isSuccess) {
                val incomeCategories = incomeResult.getOrThrow()
                println("✅ OFFLINE: Preloaded ${incomeCategories.size} income categories")
            } else {
                println("⚠️ OFFLINE: Failed to preload income categories: ${incomeResult.exceptionOrNull()?.message}")
            }
        }
    }

    fun loadWallets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(walletsState = ScreenState.Loading)

            try {
                val result = getWalletsUseCase()
                if (result.isSuccess) {
                    val wallets = result.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        walletsState = ScreenState.Success(wallets)
                    )
                    val defaultWallet = wallets.firstOrNull { it.canAddTransactions() }
                        ?: wallets.firstOrNull()
                    if (defaultWallet != null) {
                        applyWalletSelection(defaultWallet, setDestinationToo = true)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(walletsState = ScreenState.Success(emptyList()))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(walletsState = ScreenState.Success(emptyList()))
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(categoriesState = ScreenState.Loading)

            try {
                val result = when (_uiState.value.selectedType) {
                    TransactionType.INCOME -> getIncomeCategoriesUseCase()
                    TransactionType.EXPENSE -> getExpenseCategoriesUseCase()
                    TransactionType.TRANSFER -> getExpenseCategoriesUseCase()
                }

                if (result.isSuccess) {
                    val categories = result.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        categoriesState = ScreenState.Success(categories)
                    )
                    val firstCategory = categories.firstOrNull()
                    if (firstCategory != null) {
                        _uiState.value = _uiState.value.copy(
                            selectedCategoryId = firstCategory.id,
                            selectedCategoryName = firstCategory.name
                        )
                    }
                    println("✅ Loaded ${categories.size} categories for type: ${_uiState.value.selectedType}")
                } else {
                    _uiState.value = _uiState.value.copy(categoriesState = ScreenState.Success(emptyList()))
                }
            } catch (e: Exception) {
                println("⚠️ Error loading categories: ${e.message}")
                _uiState.value = _uiState.value.copy(categoriesState = ScreenState.Success(emptyList()))
            }
        }
    }

    fun loadTags() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tagsState = ScreenState.Loading)

            try {
                val result = getTagsUseCase()
                if (result.isSuccess) {
                    val tags = result.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        availableTags = tags,
                        tagsState = ScreenState.Success(tags)
                    )
                } else {
                    val exception = result.exceptionOrNull() ?: Exception("Error loading tags")
                    _uiState.value = _uiState.value.copy(
                        tagsState = ScreenState.Error(
                            ErrorHandler.mapExceptionToAppError(exception),
                            retryAction = { loadTags() }
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    tagsState = ScreenState.Error(
                        ErrorHandler.mapExceptionToAppError(e),
                        retryAction = { loadTags() }
                    )
                )
            }
        }
    }

    fun onTransactionTypeSelected(type: TransactionType) {
        _uiState.value = _uiState.value.copy(selectedType = type)
        loadCategories()
    }

    fun onWalletSelected(walletId: Int, walletName: String) {
        val wallets = _uiState.value.walletsList()
        val selectedWallet = wallets.find { it.id == walletId }
        if (selectedWallet != null) {
            applyWalletSelection(selectedWallet)
        } else {
            _uiState.value = _uiState.value.copy(
                selectedWalletId = walletId,
                selectedWalletName = walletName,
                selectedWalletMyRole = null
            )
        }
    }

    private fun applyWalletSelection(wallet: Wallet, setDestinationToo: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            selectedWalletId = wallet.id,
            selectedWalletName = wallet.name,
            sourceWalletCurrency = wallet.currency,
            selectedWalletMyRole = wallet.myRole,
            destinationWalletId = if (setDestinationToo) wallet.id else _uiState.value.destinationWalletId,
            destinationWalletName = if (setDestinationToo) wallet.name else _uiState.value.destinationWalletName,
            destinationWalletCurrency = if (setDestinationToo) wallet.currency else _uiState.value.destinationWalletCurrency
        )
    }

    fun onDestinationWalletSelected(walletId: Int, walletName: String) {
        // Find the wallet currency
        val wallets = when (val state = _uiState.value.walletsState) {
            is ScreenState.Success -> state.data
            else -> emptyList()
        }
        val selectedWallet = wallets.find { it.id == walletId }

        _uiState.value = _uiState.value.copy(
            destinationWalletId = walletId,
            destinationWalletName = walletName,
            destinationWalletCurrency = selectedWallet?.currency ?: "USD"
        )

        // Load transfer preview if we have all required data
        loadTransferPreviewIfNeeded()
    }

    fun onNumberPressed(number: String) {
        val currentAmount = _uiState.value.amount
        val newAmount = if (currentAmount == "0") number else currentAmount + number
        _uiState.value = _uiState.value.copy(amount = newAmount)

        // Load preview for transfers
        if (_uiState.value.selectedType == TransactionType.TRANSFER) {
            loadTransferPreviewIfNeeded()
        }
    }

    fun onBackspacePressed() {
        val currentAmount = _uiState.value.amount
        if (currentAmount.isNotEmpty()) {
            val newAmount = currentAmount.dropLast(1)
            _uiState.value = _uiState.value.copy(
                amount = if (newAmount.isEmpty()) "0" else newAmount
            )

            // Load preview for transfers
            if (_uiState.value.selectedType == TransactionType.TRANSFER) {
                loadTransferPreviewIfNeeded()
            }
        }
    }

    fun onDecimalPressed() {
        val currentAmount = _uiState.value.amount
        if (!currentAmount.contains(".")) {
            _uiState.value = _uiState.value.copy(amount = "$currentAmount.")
        }
    }

    private fun loadTransferPreviewIfNeeded() {
        val state = _uiState.value

        // Only load if we have valid wallets and amount
        if (state.selectedWalletId > 0 &&
            state.destinationWalletId > 0 &&
            state.selectedWalletId != state.destinationWalletId &&
            state.amount.isNotEmpty() &&
            state.amount != "0" &&
            state.amount != "0.") {

            loadTransferPreview(
                sourceWalletId = state.selectedWalletId,
                destinationWalletId = state.destinationWalletId,
                amount = state.amount
            )
        } else {
            // Clear preview if conditions aren't met
            _uiState.value = _uiState.value.copy(transferPreview = null)
        }
    }

    private fun loadTransferPreview(
        sourceWalletId: Int,
        destinationWalletId: Int,
        amount: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPreview = true)

            val result = getTransferPreviewUseCase(
                sourceWalletId = sourceWalletId,
                destinationWalletId = destinationWalletId,
                amount = amount
            )

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    transferPreview = result.getOrNull(),
                    isLoadingPreview = false
                )
            } else {
                // Failed to load preview, clear it
                _uiState.value = _uiState.value.copy(
                    transferPreview = null,
                    isLoadingPreview = false
                )
            }
        }
    }

    fun onCategorySelected(categoryId: Int, categoryName: String) {
        _uiState.value = _uiState.value.copy(
            selectedCategoryId = categoryId,
            selectedCategoryName = categoryName
        )
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onNoteChanged(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun onTagSelected(tagId: Int) {
        val currentTags = _uiState.value.selectedTagIds.toMutableList()
        if (currentTags.contains(tagId)) {
            currentTags.remove(tagId)
        } else {
            currentTags.add(tagId)
        }
        _uiState.value = _uiState.value.copy(selectedTagIds = currentTags)
    }

    fun onCreateTag(name: String) {
        viewModelScope.launch {
            val tagName = name.trim().removePrefix("#")

            if (tagName.isBlank()) {
                return@launch
            }

            val result = createTagUseCase(tagName)
            if (result.isSuccess) {
                val newTag = result.getOrThrow()
                val currentTags = _uiState.value.availableTags.toMutableList()
                currentTags.add(newTag)
                _uiState.value = _uiState.value.copy(
                    availableTags = currentTags
                )
                onTagSelected(newTag.id)
            } else {
                val exception = result.exceptionOrNull() ?: Exception("Failed to create tag")
                println("Failed to create tag: ${exception.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createTransaction(context: Context) {
        if (!validateInputs()) {
            return
        }

        if (!validateWalletBalance()) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(transactionState = ScreenState.Loading)

            try {
                when (_uiState.value.selectedType) {
                    TransactionType.TRANSFER -> {
                        handleTransferCreation(context)
                    }
                    else -> {
                        handleTransactionCreation(context)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transactionState = ScreenState.Error(
                        ErrorHandler.mapExceptionToAppError(e),
                        retryAction = { createTransaction(context) }
                    )
                )
            }
        }
    }

    private fun validateWalletBalance(): Boolean {
        if (_uiState.value.selectedType == TransactionType.EXPENSE) {
            val wallets = when (val state = _uiState.value.walletsState) {
                is ScreenState.Success -> state.data
                else -> emptyList()
            }
            val selectedWallet = wallets.find { it.id == _uiState.value.selectedWalletId }
            val walletBalance = selectedWallet?.balance?.toDoubleOrNull() ?: 0.0
            val expenseAmount = _uiState.value.amount.toDoubleOrNull() ?: 0.0

            if (walletBalance < expenseAmount) {
                val symbol = CurrencyUtils.getCurrencySymbol(
                    selectedWallet?.currency ?: _uiState.value.sourceWalletCurrency
                )
                _uiState.value = _uiState.value.copy(
                    transactionState = ScreenState.Error(
                        AppError.ValidationError(
                            "Insufficient balance. Your wallet has $symbol${"%.2f".format(walletBalance)} but you're trying to spend $symbol${"%.2f".format(expenseAmount)}"
                        )
                    )
                )
                return false
            }
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun handleTransactionCreation(context: Context) {
        // Convert first attachment URI to File if available
        val receiptFile = _uiState.value.attachments.firstOrNull()?.let { uriString ->
            try {
                val uri = Uri.parse(uriString)
                FileUtils.getFileFromUri(context, uri)
            } catch (e: Exception) {
                println("⚠️ Failed to convert URI to File: ${e.message}")
                null
            }
        }

        val createTransaction = CreateTransaction(
            name = _uiState.value.name,
            amount = _uiState.value.amount,
            note = _uiState.value.note,
            type = _uiState.value.selectedType.apiValue,
            transactionDate = LocalDate.now().toString(),
            walletId = _uiState.value.selectedWalletId,
            categoryId = _uiState.value.selectedCategoryId,
            tags = _uiState.value.selectedTagIds,
            receiptFile = receiptFile
        )

        val result = createTransactionUseCase(createTransaction)

        if (result.isSuccess) {
            _uiState.value = _uiState.value.copy(transactionState = ScreenState.Success(Unit))

            DataSyncManager.notifyDataChangedFromVM(
                DataSyncManager.DataChangeEvent.TransactionsUpdated
            ) { error ->
                println("⚠️ DEBUG: Failed to notify transaction update: ${error.message}")
            }
            when (_uiState.value.selectedType) {
                TransactionType.INCOME, TransactionType.EXPENSE -> {
                    DataSyncManager.notifyDataChangedFromVM(
                        DataSyncManager.DataChangeEvent.WalletsUpdated
                    )
                }
                TransactionType.TRANSFER -> {
                    DataSyncManager.notifyDataChangedFromVM(
                        DataSyncManager.DataChangeEvent.WalletsUpdated
                    )
                }
            }

            _navigationEvent.value = NavigationEvent.NavigateHome
        } else {
            val exception = result.exceptionOrNull() ?: Exception("Failed to create transaction")
            _uiState.value = _uiState.value.copy(
                transactionState = ScreenState.Error(
                    ErrorHandler.mapExceptionToAppError(exception),
                    retryAction = { createTransaction(context) }
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun handleTransferCreation(context: Context) {
        if (_uiState.value.selectedWalletId == _uiState.value.destinationWalletId) {
            _uiState.value = _uiState.value.copy(
                transactionState = ScreenState.Error(
                    AppError.ValidationError("Cannot transfer to the same wallet")
                )
            )
            return
        }

        val result = createTransferUseCase(
            sourceWalletId = _uiState.value.selectedWalletId,
            destinationWalletId = _uiState.value.destinationWalletId,
            amount = _uiState.value.amount,
            note = _uiState.value.note
        )

        if (result.isSuccess) {
            _uiState.value = _uiState.value.copy(transactionState = ScreenState.Success(Unit))
            DataSyncManager.notifyDataChangedFromVM(
                DataSyncManager.DataChangeEvent.TransactionsUpdated
            )
            DataSyncManager.notifyDataChangedFromVM(
                DataSyncManager.DataChangeEvent.WalletsUpdated
            )

            _navigationEvent.value = NavigationEvent.NavigateHome
        } else {
            val exception = result.exceptionOrNull() ?: Exception("Failed to create transfer")
            _uiState.value = _uiState.value.copy(
                transactionState = ScreenState.Error(
                    ErrorHandler.mapExceptionToAppError(exception),
                    retryAction = { createTransaction(context) }
                )
            )
        }
    }

    private fun validateInputs(): Boolean {
        val amount = _uiState.value.amount
        if (amount == "0" || amount == "0." || amount.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                transactionState = ScreenState.Error(
                    AppError.ValidationError("Please enter a valid amount")
                )
            )
            return false
        }

        if (_uiState.value.selectedWalletId == 0) {
            _uiState.value = _uiState.value.copy(
                transactionState = ScreenState.Error(
                    AppError.ValidationError("Please select a wallet")
                )
            )
            return false
        }

        val wallets = _uiState.value.walletsList()
        val sourceWallet = wallets.find { it.id == _uiState.value.selectedWalletId }
        if (sourceWallet != null && !sourceWallet.canAddTransactions()) {
            _uiState.value = _uiState.value.copy(
                transactionState = ScreenState.Error(
                    AppError.ValidationError("You have view-only access to this wallet")
                )
            )
            return false
        }

        if (_uiState.value.selectedType == TransactionType.TRANSFER) {
            if (_uiState.value.destinationWalletId == 0) {
                _uiState.value = _uiState.value.copy(
                    transactionState = ScreenState.Error(
                        AppError.ValidationError("Please select a destination wallet")
                    )
                )
                return false
            }
            if (_uiState.value.selectedWalletId == _uiState.value.destinationWalletId) {
                _uiState.value = _uiState.value.copy(
                    transactionState = ScreenState.Error(
                        AppError.ValidationError("Cannot transfer to the same wallet")
                    )
                )
                return false
            }
            val destinationWallet = wallets.find { it.id == _uiState.value.destinationWalletId }
            if (destinationWallet != null && !destinationWallet.canAddTransactions()) {
                _uiState.value = _uiState.value.copy(
                    transactionState = ScreenState.Error(
                        AppError.ValidationError("You have view-only access to the destination wallet")
                    )
                )
                return false
            }
        }

        return true
    }

    fun removeAttachment(uri: String) {
        val currentAttachments = _uiState.value.attachments.toMutableList()
        currentAttachments.remove(uri)
        _uiState.value = _uiState.value.copy(attachments = currentAttachments)
    }

    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }

    // Add to AddTransactionViewModel
    fun forceCacheCategories() {
        viewModelScope.launch {
            println("📦 FORCE CACHE: Starting forced category cache...")

            // Force fetch and cache expense categories
            val expenseResult = getExpenseCategoriesUseCase()
            if (expenseResult.isSuccess) {
                val expenseCategories = expenseResult.getOrThrow()
                println("📦 FORCE CACHE: Got ${expenseCategories.size} expense categories")
            } else {
                println("📦 FORCE CACHE: Failed to get expense categories: ${expenseResult.exceptionOrNull()?.message}")
            }

            // Force fetch and cache income categories
            val incomeResult = getIncomeCategoriesUseCase()
            if (incomeResult.isSuccess) {
                val incomeCategories = incomeResult.getOrThrow()
                println("📦 FORCE CACHE: Got ${incomeCategories.size} income categories")
            } else {
                println("📦 FORCE CACHE: Failed to get income categories: ${incomeResult.exceptionOrNull()?.message}")
            }
        }
    }
}

sealed class NavigationEvent {
    object NavigateHome : NavigationEvent()
}

data class AddTransactionState(
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val amount: String = "0",
    val selectedWalletId: Int = 0,
    val selectedWalletName: String = "Select Wallet",
    val destinationWalletId: Int = 0,
    val destinationWalletName: String = "Select Wallet",
    val selectedCategoryId: Int = 1,
    val selectedCategoryName: String = "Foods & Drinks",
    val name: String = "",
    val note: String = "",
    val selectedTagIds: List<Int> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val attachments: List<String> = emptyList(),
    val walletsState: ScreenState<List<Wallet>> = ScreenState.Loading,
    val categoriesState: ScreenState<List<Category>> = ScreenState.Loading,
    val tagsState: ScreenState<List<Tag>> = ScreenState.Loading,
    val transactionState: ScreenState<Unit> = ScreenState.Empty,
    // Transfer-specific fields
    val sourceWalletCurrency: String = "USD",
    val destinationWalletCurrency: String = "USD",
    val transferPreview: TransferPreview? = null,
    val isLoadingPreview: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val selectedWalletMyRole: String? = null
) {
    fun walletsList(): List<Wallet> = when (val state = walletsState) {
        is ScreenState.Success -> state.data
        else -> emptyList()
    }

    val canAddTransactions: Boolean
        get() {
            if (selectedWalletId == 0) return false
            val wallet = walletsList().find { it.id == selectedWalletId } ?: return false
            return wallet.canAddTransactions()
        }

    val isViewOnlyWallet: Boolean
        get() = !canAddTransactions
}

enum class TransactionType(val displayName: String, val apiValue: String) {
    INCOME("INCOME", "income"),
    EXPENSE("EXPENSE", "expense"),
    TRANSFER("TRANSFER", "transfer")
}