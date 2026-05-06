package com.example.moneymate.ui.screens.goal

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.goal.model.Goal
import com.example.moneymate.utils.Config
import com.example.moneymate.utils.ScreenState
import org.koin.androidx.compose.koinViewModel

@Composable
fun GoalDetailsScreen(
    viewModel: GoalDetailViewModel = koinViewModel(),
    onBack: () -> Unit,
    onEditClick: (Int) -> Unit = {} // Pass goal ID for editing
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Dialog states
    var showAddMoneyDialog by remember { mutableStateOf(false) }
    var addMoneyAmount by remember { mutableStateOf("") }

    // Clear error when dialog opens
    LaunchedEffect(showAddMoneyDialog) {
        if (showAddMoneyDialog) {
            viewModel.clearAddMoneyError()
        }
    }

    Scaffold(containerColor = Color.White) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val state = uiState) {
                is ScreenState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is ScreenState.Success -> {
                    GoalDetailContent(
                        goal = state.data,
                        onBack = onBack,
                        onEditClick = { state.data.id?.let { onEditClick(it) } },
                        onAddMoneyClick = { showAddMoneyDialog = true }
                    )
                }

                is ScreenState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error.getUserFriendlyMessage(),
                            color = Color.Red,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(onClick = { state.retryAction?.invoke() }) {
                            Text("Retry")
                        }
                    }
                }

                else -> Unit
            }

            // ADD MONEY DIALOG
            if (showAddMoneyDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showAddMoneyDialog = false
                        addMoneyAmount = ""
                        viewModel.clearAddMoneyError()
                    },
                    title = {
                        Text("Add Money to Goal", color = Color.Black)
                    },
                    text = {
                        Column {
                            Text(
                                text = "Enter amount to save towards this goal:",
                                color = Color.Black.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = addMoneyAmount,
                                onValueChange = {
                                    addMoneyAmount = it
                                    viewModel.clearAddMoneyError()
                                },
                                label = { Text("Amount ($)") },
                                singleLine = true,
                                isError = viewModel.addMoneyError != null,
                                supportingText = {
                                    viewModel.addMoneyError?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val amount = addMoneyAmount.toDoubleOrNull()
                                if (amount != null && amount > 0) {
                                    viewModel.addMoneyToGoal(amount)
                                    // Dismiss if no error
                                    if (viewModel.addMoneyError == null) {
                                        showAddMoneyDialog = false
                                        addMoneyAmount = ""
                                    }
                                }
                            },
                            enabled = addMoneyAmount.isNotBlank() &&
                                    addMoneyAmount.toDoubleOrNull() != null &&
                                    addMoneyAmount.toDoubleOrNull()!! > 0 &&
                                    !viewModel.isAddingMoney,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A66FF))
                        ) {
                            if (viewModel.isAddingMoney) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Add Money", color = Color.White)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showAddMoneyDialog = false
                            addMoneyAmount = ""
                            viewModel.clearAddMoneyError()
                        }) {
                            Text("Cancel")
                        }
                    },
                    containerColor = Color.White
                )
            }
        }
    }
}

@Composable
private fun GoalDetailContent(
    goal: Goal,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onAddMoneyClick: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Enhanced header with image
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            // FIXED: Use ImageRequest builder for Coil AsyncImage
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(
                        goal.image?.let { imagePath ->
                            Config.buildImageUrl(imagePath)
                        } ?: "https://placehold.co/600x400/4A66FF/FFFFFF?text=${goal.title.replace(" ", "+")}"
                    )
                    .crossfade(true)
                    .build(),
                contentDescription = goal.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark gradient overlay for better text contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            // Top navigation row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color.White, CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        null,
                        tint = Color.Black
                    )
                }
            }

            // Goal title at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Text(
                    text = goal.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = goal.deadline?.toString() ?: "No deadline",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp
                )
            }
        }

        // Content section
        Column(modifier = Modifier.padding(20.dp)) {
            // Description card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Description",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF374151)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = goal.description ?: "No description provided",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Goal stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Goal Progress",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Progress numbers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = String.format("$%.0f", goal.amountSaved),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4A66FF)
                            )
                            Text(
                                text = "Saved",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format("$%.0f", goal.goalAmount),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF374151)
                            )
                            Text(
                                text = "Target",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { goal.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(CircleShape),
                        color = Color(0xFF4A66FF),
                        trackColor = Color(0xFFE5E7EB)
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "${(goal.progress * 100).toInt()}% completed",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4A66FF),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Goal")
                }

                Button(
                    onClick = onAddMoneyClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A66FF))
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Add Money", color = Color.White)
                }
            }
        }
    }
}