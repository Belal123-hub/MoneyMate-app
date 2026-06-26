package com.example.data.network.goal.model

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.domain.goal.model.Goal
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class GoalResponse(
    val id: Int,
    val title: String,
    val goal_amount: Double,
    val current_amount: Double = 0.0,
    val currency: String,
    val description: String? = null,
    val deadline: String? = null,
    val image_url: String? = null,
    val user_id: Int? = null,
    val created_at: String? = null
)

@Serializable
data class GoalUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val deadline: String? = null,
    val goal_amount: String? = null,
    val current_amount: Double? = null
)

// Note: For create, we use multipart form data, so no separate request model needed

@RequiresApi(Build.VERSION_CODES.O)
fun GoalResponse.toDomain(): Goal {
    return Goal(
        id = id,
        title = title,
        description = description,
        image = image_url,
        deadline = deadline?.let { 
            // Handle both date-only and datetime formats
            if (it.contains("T")) {
                LocalDate.parse(it.substring(0, 10))
            } else {
                LocalDate.parse(it)
            }
        },
        goalAmount = goal_amount,
        amountSaved = current_amount,
        walletId = user_id ?: 0,
        currency = currency
    )
}