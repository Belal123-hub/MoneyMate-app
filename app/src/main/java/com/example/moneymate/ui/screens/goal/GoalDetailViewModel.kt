package com.example.moneymate.ui.screens.goal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.goal.model.Goal
import com.example.domain.goal.model.GoalUpdate
import com.example.domain.goal.usecase.*
import com.example.moneymate.utils.DataSyncManager
import com.example.moneymate.utils.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class GoalDetailViewModel(
    private val getGoalUseCase: GetGoalUseCase,
    private val createGoalUseCase: CreateGoalUseCase,
    private val updateGoalUseCase: UpdateGoalUseCase,
    private val deleteGoalUseCase: DeleteGoalUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScreenState<Goal>>(ScreenState.Loading)
    val uiState: StateFlow<ScreenState<Goal>> = _uiState.asStateFlow()

    // Internal ID to track if we are editing or creating
    private var currentGoalId: Int? = null

    // Form States
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var goalAmount by mutableStateOf("")
    var deadline by mutableStateOf<LocalDate>(LocalDate.now().plusYears(1))
    var imagePath by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)
    var isAddingMoney by mutableStateOf(false)
    var addMoneyError by mutableStateOf<String?>(null)

    /**
     * Called from the UI/Navigation to start the screen logic
     */
    fun initialize(id: Int?) {
        if (id == null || id == 0) {
            currentGoalId = null
            _uiState.value = ScreenState.Empty // Mode: Create
        } else {
            currentGoalId = id
            loadGoal(id) // Mode: Detail/Edit
        }
    }

    private fun loadGoal(id: Int) {
        viewModelScope.launch {
            _uiState.value = ScreenState.Loading
            val result = getGoalUseCase(id)
            if (result.isSuccess) {
                val goal = result.getOrThrow()
                _uiState.value = ScreenState.Success(goal)
                // Pre-fill fields
                title = goal.title
                description = goal.description ?: ""
                goalAmount = goal.goalAmount.toString()
                deadline = goal.deadline ?: LocalDate.now()
                // Fix: Use the correct field name from your Goal model
                imagePath = goal.image  // Changed from imageUrl to image
            } else {
                val exception = result.exceptionOrNull()
                _uiState.value = ScreenState.Error(
                    error = com.example.moneymate.utils.ErrorHandler.mapExceptionToAppError(exception ?: Exception("Failed")),
                    retryAction = { loadGoal(id) }
                )
            }
        }
    }

    fun saveGoal(onSuccess: () -> Unit) {
        val amount = goalAmount.toDoubleOrNull() ?: 0.0
        isSaving = true
        viewModelScope.launch {
            val result = if (currentGoalId == null) {
                createGoalUseCase(
                    title = title,
                    goalAmount = amount,
                    currency = "USD",
                    description = description,
                    deadline = deadline,
                    imagePath = imagePath
                )
            } else {
                // Fix: Pass parameters correctly to match your existing UpdateGoalUseCase signature
                updateGoalUseCase(
                    goalId = currentGoalId!!,
                    title = title,
                    description = description,
                    deadline = deadline,
                    goalAmount = amount
                )
            }

            if (result.isSuccess) {
                DataSyncManager.notifyGoalsUpdated()
                onSuccess()
            } else {
                isSaving = false
            }
        }
    }

    fun addMoneyToGoal(amount: Double) {
        if (amount <= 0) {
            addMoneyError = "Amount must be greater than zero"
            return
        }

        isAddingMoney = true
        addMoneyError = null

        viewModelScope.launch {
            try {
                val goalId = currentGoalId ?: run {
                    addMoneyError = "Goal not found"
                    isAddingMoney = false
                    return@launch
                }

                val currentGoal = (uiState.value as? ScreenState.Success<Goal>)?.data

                if (currentGoal != null) {
                    // Calculate new total amount saved
                    val newCurrentAmount = currentGoal.amountSaved + amount

                    // Don't allow exceeding goal amount
                    if (newCurrentAmount > currentGoal.goalAmount) {
                        addMoneyError = "Cannot exceed goal amount of $${String.format("%.2f", currentGoal.goalAmount)}"
                        isAddingMoney = false
                        return@launch
                    }

                    // Fix: Create GoalUpdate object and pass it correctly
                    val goalUpdate = GoalUpdate(
                        currentAmount = newCurrentAmount
                    )

                    val result = updateGoalUseCase(
                        goalId = goalId,
                        request = goalUpdate  // Match your use case parameter name
                    )

                    isAddingMoney = false

                    if (result.isSuccess) {
                        // Refresh goal data
                        loadGoal(goalId)
                        DataSyncManager.notifyGoalsUpdated()
                    } else {
                        val exception = result.exceptionOrNull()
                        addMoneyError = exception?.message ?: "Failed to add money"
                    }
                } else {
                    addMoneyError = "Could not get current goal data"
                    isAddingMoney = false
                }
            } catch (e: Exception) {
                addMoneyError = e.message ?: "An error occurred"
                isAddingMoney = false
                e.printStackTrace()
            }
        }
    }

    /**
     * Delete goal
     */
    fun deleteGoal(onSuccess: () -> Unit) {
        val goalId = currentGoalId ?: return

        viewModelScope.launch {
            val result = deleteGoalUseCase(goalId)
            if (result.isSuccess) {
                DataSyncManager.notifyGoalsUpdated()
                onSuccess()
            }
        }
    }

    /**
     * Clear add money error
     */
    fun clearAddMoneyError() {
        addMoneyError = null
    }
}