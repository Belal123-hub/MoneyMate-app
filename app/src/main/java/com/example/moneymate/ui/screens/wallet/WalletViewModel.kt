package com.example.moneymate.ui.screens.wallet

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.offline.OfflineSyncOrchestrator
import com.example.data.offline.OfflineSyncStatusDataSource
import com.example.domain.transaction.model.TransactionEntity
import com.example.domain.transaction.usecase.DeleteTransactionUseCase
import com.example.domain.transaction.usecase.GetWalletTransactionsUseCase
import com.example.domain.wallet.model.Wallet
import com.example.domain.wallet.model.WalletCreateRequest
import com.example.domain.wallet.model.WalletUpdateRequest
import com.example.domain.wallet.usecase.CreateWalletUseCase
import com.example.domain.wallet.usecase.DeleteWalletUseCase
import com.example.domain.wallet.usecase.GetWalletDetailUseCase
import com.example.domain.wallet.usecase.GetWalletsUseCase
import com.example.domain.wallet.usecase.UpdateWalletUseCase
import com.example.domain.wallet.usecase.ShareWalletUseCase
import com.example.domain.wallet.usecase.GetWalletMembersUseCase
import com.example.domain.wallet.usecase.UpdateMemberRoleUseCase
import com.example.domain.wallet.usecase.RemoveMemberUseCase
import com.example.domain.wallet.model.WalletMember
import com.example.moneymate.utils.DataSyncManager
import com.example.moneymate.utils.ScreenState
import com.example.moneymate.ui.offline.SyncStatus
import com.example.moneymate.utils.network.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WalletViewModel(
    private val getWalletsUseCase: GetWalletsUseCase,
    private val createWalletUseCase: CreateWalletUseCase,
    private val getWalletDetailUseCase: GetWalletDetailUseCase,
    private val deleteWalletUseCase: DeleteWalletUseCase,
    private val updateWalletUseCase: UpdateWalletUseCase,
    private val getWalletTransactionsUseCase: GetWalletTransactionsUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val offlineSyncStatusDataSource: OfflineSyncStatusDataSource,
    private val connectivityObserver: ConnectivityObserver,
    private val offlineSyncOrchestrator: OfflineSyncOrchestrator,
    private val shareWalletUseCase: ShareWalletUseCase,
    private val getWalletMembersUseCase: GetWalletMembersUseCase,
    private val updateMemberRoleUseCase: UpdateMemberRoleUseCase,
    private val removeMemberUseCase: RemoveMemberUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletScreenState())
    val uiState: StateFlow<WalletScreenState> = _uiState.asStateFlow()

    init {
        println("DEBUG: WalletViewModel init - loading wallets")
        _uiState.update {
            it.copy(
                syncStatus = if (connectivityObserver.isOnline.value) {
                    SyncStatus.IDLE
                } else {
                    SyncStatus.OFFLINE
                }
            )
        }
        loadWallets()
        observeConnectivity()
        observeUnsyncedTransactions()
        observeUnsyncedWallets()

        // Listen for data change events
        setupDataChangeListener()
    }

    private fun observeUnsyncedTransactions() {
        viewModelScope.launch {
            offlineSyncStatusDataSource.observeUnsyncedTransactionIds().collect { ids ->
                _uiState.value = _uiState.value.copy(unsyncedTransactionIds = ids)
            }
        }
    }

    private fun observeUnsyncedWallets() {
        viewModelScope.launch {
            offlineSyncStatusDataSource.observeUnsyncedWalletIds().collect { ids ->
                _uiState.value = _uiState.value.copy(unsyncedWalletIds = ids)
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { isOnline ->
                _uiState.value = _uiState.value.copy(
                    syncStatus = if (isOnline) SyncStatus.IDLE else SyncStatus.OFFLINE
                )
                if (isOnline) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        runCatching { offlineSyncOrchestrator.runSync("wallets") }
                    }
                    loadWallets()
                }
            }
        }
    }

    private fun setupDataChangeListener() {
        viewModelScope.launch {
            // Listen for data change events
            DataSyncManager.dataChangeEvents.collect { event ->
                when (event) {
                    is DataSyncManager.DataChangeEvent.TransactionsUpdated -> {
                        println("🔄 DEBUG: WalletViewModel - Transaction update detected, refreshing wallets and transactions...")
                        loadWallets()
                        uiState.value.selectedWallet?.id?.let { walletId ->
                            loadTransactions(walletId)
                        }
                    }
                    is DataSyncManager.DataChangeEvent.WalletsUpdated -> {
                        println("🔄 DEBUG: WalletViewModel - Wallet update detected, refreshing wallets...")
                        loadWallets()
                    }
                    is DataSyncManager.DataChangeEvent.WalletSpecificUpdated -> {
                        println("🔄 DEBUG: WalletViewModel - Specific wallet update detected, refreshing wallet detail...")
                        uiState.value.selectedWallet?.id?.let { walletId ->
                            loadWalletDetail(walletId)
                            loadTransactions(walletId)
                        }
                    }
                    else -> {
                        // Handle other events if needed
                    }
                }
            }
        }
    }

    // Add refresh function for screen focus
    fun refreshOnScreenFocus() {
        viewModelScope.launch {
            println("🔄 DEBUG: WalletViewModel - Screen focused, refreshing data...")
            connectivityObserver.refresh()
            refreshUnsyncedWalletIds()
            loadWallets()
            uiState.value.selectedWallet?.id?.let { walletId ->
                loadTransactions(walletId)
            }
        }
    }

    private suspend fun refreshUnsyncedWalletIds() {
        _uiState.value = _uiState.value.copy(
            unsyncedWalletIds = offlineSyncStatusDataSource.getUnsyncedWalletIds()
        )
    }

    fun loadWallets() {
        viewModelScope.launch {
            val previousSelectedId = _uiState.value.selectedWallet?.id
            _uiState.value = _uiState.value.copy(walletsState = ScreenState.Loading)

            try {
                println("DEBUG: Loading wallets...")
                val result = getWalletsUseCase()
                if (result.isSuccess) {
                    val walletsList = result.getOrNull() ?: emptyList()
                    refreshUnsyncedWalletIds()
                    _uiState.value = _uiState.value.copy(
                        walletsState = ScreenState.Success(walletsList)
                    )
                    println("DEBUG: Loaded ${walletsList.size} wallets")

                    val walletToSelect = previousSelectedId?.let { id ->
                        walletsList.find { it.id == id }
                    } ?: walletsList.firstOrNull()
                    walletToSelect?.let { selectWallet(it) }
                } else {
                    val exception = result.exceptionOrNull() ?: Exception("Failed to load wallets")
                    _uiState.value = _uiState.value.copy(
                        walletsState = ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(exception),
                            retryAction = { loadWallets() }
                        )
                    )
                }
            } catch (e: Exception) {
                println("DEBUG: Error loading wallets: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    walletsState = ScreenState.Error(
                        com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                        retryAction = { loadWallets() }
                    )
                )
            }
        }
    }

    fun createWallet(walletRequest: WalletCreateRequest) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(createWalletState = ScreenState.Loading)

            try {
                println("DEBUG: Creating wallet...")
                val result = createWalletUseCase(walletRequest)
                if (result.isSuccess) {
                    val created = result.getOrThrow()
                    refreshUnsyncedWalletIds()
                    _uiState.value = _uiState.value.copy(
                        createWalletState = ScreenState.Success(Unit)
                    )
                    println(
                        "DEBUG: Wallet created successfully id=${created.id} isSynced=${created.isSynced}"
                    )
                    loadWallets()

                    // Notify other screens about wallet update
                    DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.WalletsUpdated)
                    // Also notify about user data update for HomeScreen balance refresh
                    DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.UserDataUpdated())
                } else {
                    val exception = result.exceptionOrNull() ?: Exception("Failed to create wallet")
                    _uiState.value = _uiState.value.copy(
                        createWalletState = ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(exception),
                            retryAction = { createWallet(walletRequest) }
                        )
                    )
                }
            } catch (e: Exception) {
                println("DEBUG: Error creating wallet: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    createWalletState = ScreenState.Error(
                        com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                        retryAction = { createWallet(walletRequest) }
                    )
                )
            }
        }
    }

    fun selectWallet(wallet: Wallet) {
        _uiState.value = _uiState.value.copy(selectedWallet = wallet)
        wallet.id?.let { walletId ->
            loadTransactions(walletId)
        }
    }

    fun deleteTransaction(transactionId: Int, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val walletId = _uiState.value.selectedWallet?.id
            val result = deleteTransactionUseCase(transactionId)
            if (result.isSuccess) {
                DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.TransactionsUpdated)
                DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.WalletsUpdated)
                walletId?.let { loadTransactions(it) }
                loadWallets()
            } else {
                onError(
                    result.exceptionOrNull()?.message ?: "Could not delete transaction"
                )
            }
        }
    }

    fun loadTransactions(walletId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(transactionsState = ScreenState.Loading)

            try {
                println("DEBUG: Loading transactions for wallet: $walletId")
                val result = getWalletTransactionsUseCase(walletId)
                if (result.isSuccess) {
                    val transactions = result.getOrNull() ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        transactionsState = ScreenState.Success(transactions)
                    )
                    println("DEBUG: Loaded ${transactions.size} transactions")
                } else {
                    val exception = result.exceptionOrNull() ?: Exception("Failed to load transactions")
                    _uiState.value = _uiState.value.copy(
                        transactionsState = ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(exception),
                            retryAction = { loadTransactions(walletId) }
                        )
                    )
                }
            } catch (e: Exception) {
                println("DEBUG: Error loading transactions: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    transactionsState = ScreenState.Error(
                        com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                        retryAction = { loadTransactions(walletId) }
                    )
                )
            }
        }
    }

    fun loadWalletMembers(walletId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(walletMembersState = ScreenState.Loading) }
            val result = getWalletMembersUseCase(walletId)
            _uiState.update {
                it.copy(
                    walletMembersState = if (result.isSuccess) {
                        ScreenState.Success(result.getOrNull() ?: emptyList())
                    } else {
                        ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(
                                result.exceptionOrNull() ?: Exception("Failed to load members")
                            ),
                            retryAction = { loadWalletMembers(walletId) }
                        )
                    }
                )
            }
        }
    }

    fun showShareWalletDialog() {
        _uiState.update { it.copy(showShareDialog = true) }
    }

    fun dismissShareWalletDialog() {
        _uiState.update { it.copy(showShareDialog = false, shareMessage = null) }
    }

    fun shareWallet(walletId: Int, email: String, role: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(shareInProgress = true, shareMessage = null) }
            val result = shareWalletUseCase(walletId, email, role)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        shareInProgress = false,
                        showShareDialog = false,
                        shareMessage = "Invitation sent"
                    )
                }
                loadWalletMembers(walletId)
                loadWalletDetail(walletId)
            } else {
                _uiState.update {
                    it.copy(
                        shareInProgress = false,
                        shareMessage = result.exceptionOrNull()?.message ?: "Share failed"
                    )
                }
            }
        }
    }

    fun updateMemberRole(walletId: Int, userId: Int, role: String) {
        viewModelScope.launch {
            updateMemberRoleUseCase(walletId, userId, role)
            loadWalletMembers(walletId)
        }
    }

    fun removeMember(walletId: Int, userId: Int) {
        viewModelScope.launch {
            removeMemberUseCase(walletId, userId)
            loadWalletMembers(walletId)
        }
    }

    fun loadWalletDetail(walletId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(walletDetailState = ScreenState.Loading)

            try {
                println("DEBUG: Loading wallet detail for ID: $walletId")
                getWalletDetailUseCase(walletId).collect { wallet ->
                    _uiState.value = _uiState.value.copy(
                        walletDetailState = ScreenState.Success(wallet)
                    )
                    loadWalletMembers(walletId)
                    println("DEBUG: Loaded wallet detail: $wallet")
                }
            } catch (e: Exception) {
                println("DEBUG: Error loading wallet detail: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    walletDetailState = ScreenState.Error(
                        com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                        retryAction = { loadWalletDetail(walletId) }
                    )
                )
            }
        }
    }

    fun deleteWallet(walletId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deleteWalletState = ScreenState.Loading)

            try {
                println("DEBUG: Deleting wallet: $walletId")
                val result = deleteWalletUseCase(walletId)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        deleteWalletState = ScreenState.Success(Unit),
                        showDeleteDialog = false
                    )
                    println("DEBUG: Wallet deleted successfully")
                    loadWallets() // Reload wallets to reflect deletion

                    // Notify other screens about wallet update
                    DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.WalletsUpdated)
                    // Also notify about user data update for HomeScreen balance refresh
                    DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.UserDataUpdated())
                } else {
                    val exception = result.exceptionOrNull() ?: Exception("Failed to delete wallet")
                    _uiState.value = _uiState.value.copy(
                        deleteWalletState = ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(exception),
                            retryAction = { deleteWallet(walletId) }
                        ),
                        showDeleteDialog = false
                    )
                }
            } catch (e: Exception) {
                println("DEBUG: Error deleting wallet: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    deleteWalletState = ScreenState.Error(
                        com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                        retryAction = { deleteWallet(walletId) }
                    ),
                    showDeleteDialog = false
                )
            }
        }
    }

    fun updateWallet(walletId: Int, walletRequest: WalletUpdateRequest) {
        viewModelScope.launch {
            val walletDetail = (_uiState.value.walletDetailState as? ScreenState.Success)?.data
            if (walletDetail != null && !walletDetail.canEditWallet()) {
                _uiState.value = _uiState.value.copy(
                    updateWalletState = ScreenState.Error(
                        com.example.moneymate.utils.AppError.ValidationError(
                            "You have view-only access to this wallet"
                        )
                    )
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(updateWalletState = ScreenState.Loading)

            try {
                println("DEBUG: Updating wallet: $walletId")
                val result = updateWalletUseCase(walletId, walletRequest)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        updateWalletState = ScreenState.Success(Unit)
                    )
                    println("DEBUG: Wallet updated successfully")
                    loadWallets() // Reload wallets to reflect changes
                    loadWalletDetail(walletId) // Reload the wallet detail

                    // Notify other screens about wallet update
                    DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.WalletsUpdated)
                    DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.WalletSpecificUpdated)
                    // Also notify about user data update for HomeScreen balance refresh
                    DataSyncManager.notifyDataChanged(DataSyncManager.DataChangeEvent.UserDataUpdated())
                } else {
                    val exception = result.exceptionOrNull() ?: Exception("Failed to update wallet")
                    _uiState.value = _uiState.value.copy(
                        updateWalletState = ScreenState.Error(
                            com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(exception),
                            retryAction = { updateWallet(walletId, walletRequest) }
                        )
                    )
                }
            } catch (e: Exception) {
                println("DEBUG: Error updating wallet: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    updateWalletState = ScreenState.Error(
                        com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(e),
                        retryAction = { updateWallet(walletId, walletRequest) }
                    )
                )
            }
        }
    }

    // UI state management functions
    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = true)
    }

    fun dismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
    }

    fun resetCreateWalletState() {
        _uiState.value = _uiState.value.copy(createWalletState = ScreenState.Empty)
    }

    fun resetUpdateWalletState() {
        _uiState.value = _uiState.value.copy(updateWalletState = ScreenState.Empty)
    }

    fun resetDeleteWalletState() {
        _uiState.value = _uiState.value.copy(deleteWalletState = ScreenState.Empty)
    }

    // Helper function for calculations
    fun getWalletIncomeExpense(walletId: Int): Pair<Double, Double> {
        val transactions = when (val state = _uiState.value.transactionsState) {
            is ScreenState.Success -> state.data
            else -> emptyList()
        }
        val walletTransactions = transactions.filter { it.walletId == walletId }
        return calculateIncomeExpense(walletTransactions)
    }
}

data class WalletScreenState(
    // Data states
    val walletsState: ScreenState<List<Wallet>> = ScreenState.Loading,
    val transactionsState: ScreenState<List<TransactionEntity>> = ScreenState.Empty,
    val walletDetailState: ScreenState<Wallet> = ScreenState.Empty,

    // Operation states
    val createWalletState: ScreenState<Unit> = ScreenState.Empty,
    val updateWalletState: ScreenState<Unit> = ScreenState.Empty,
    val deleteWalletState: ScreenState<Unit> = ScreenState.Empty,

    // UI states
    val selectedWallet: Wallet? = null,
    val showDeleteDialog: Boolean = false,
    val unsyncedTransactionIds: Set<Int> = emptySet(),
    val unsyncedWalletIds: Set<Int> = emptySet(),
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val walletMembersState: ScreenState<List<WalletMember>> = ScreenState.Empty,
    val showShareDialog: Boolean = false,
    val shareInProgress: Boolean = false,
    val shareMessage: String? = null
) {
    val walletMembers: List<WalletMember>
        get() = when (walletMembersState) {
            is ScreenState.Success -> walletMembersState.data
            else -> emptyList()
        }
    // Helper properties for backward compatibility
    val isLoading: Boolean
        get() = walletsState is ScreenState.Loading ||
                walletDetailState is ScreenState.Loading ||
                createWalletState is ScreenState.Loading ||
                updateWalletState is ScreenState.Loading ||
                deleteWalletState is ScreenState.Loading

    val wallets: List<Wallet>
        get() = when (walletsState) {
            is ScreenState.Success -> walletsState.data
            else -> emptyList()
        }

    val transactions: List<TransactionEntity>
        get() = when (transactionsState) {
            is ScreenState.Success -> transactionsState.data
            else -> emptyList()
        }

    val walletDetail: Wallet?
        get() = when (walletDetailState) {
            is ScreenState.Success -> walletDetailState.data
            else -> null
        }

    val walletCreated: Boolean
        get() = createWalletState is ScreenState.Success

    val walletUpdated: Boolean
        get() = updateWalletState is ScreenState.Success

    val walletDeleted: Boolean
        get() = deleteWalletState is ScreenState.Success
}