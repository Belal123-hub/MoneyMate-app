package com.example.data.database.mapper

import com.example.data.database.entity.CategoryEntity
import com.example.data.database.entity.GoalEntity
import com.example.data.database.entity.TagEntity
import com.example.data.database.entity.TransactionEntity
import com.example.data.database.entity.WalletEntity
import com.example.domain.category.model.Category
import com.example.domain.goal.model.Goal
import com.example.domain.tag.model.Tag
import com.example.domain.transaction.model.TransactionEntity as DomainTransactionEntity
import com.example.domain.wallet.model.Wallet

fun DomainTransactionEntity.toLocalEntity(
    updatedAt: Long = System.currentTimeMillis(),
    isSynced: Boolean = true
): TransactionEntity = TransactionEntity(
    id = id,
    name = name,
    amount = amount,
    note = note,
    type = type,
    transactionDate = transactionDate,
    walletId = walletId,
    categoryId = categoryId,
    userId = userId,
    createdAt = createdAt,
    tags = tags,
    receiptUrl = receiptUrl,
    updatedAt = updatedAt,
    isSynced = isSynced
)

fun TransactionEntity.toDomain(): DomainTransactionEntity = DomainTransactionEntity(
    id = id,
    name = name,
    amount = amount,
    note = note,
    type = type,
    transactionDate = transactionDate,
    walletId = walletId,
    categoryId = categoryId,
    userId = userId,
    createdAt = createdAt,
    tags = tags,
    receiptUrl = receiptUrl
)

fun Wallet.toLocalEntity(
    updatedAt: Long = System.currentTimeMillis(),
    isSynced: Boolean = true
): WalletEntity = WalletEntity(
    id = id,
    name = name,
    currency = currency,
    walletType = walletType,
    initialBalance = initialBalance,
    cardNumber = cardNumber,
    color = color,
    balance = balance,
    userId = userId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSynced = isSynced
)

fun WalletEntity.toDomain(): Wallet = Wallet(
    id = id,
    name = name,
    currency = currency,
    walletType = walletType,
    initialBalance = initialBalance,
    cardNumber = cardNumber,
    color = color,
    balance = balance,
    userId = userId,
    createdAt = createdAt
)

fun Category.toLocalEntity(
    updatedAt: Long = System.currentTimeMillis(),
    isSynced: Boolean = true
): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type,
    color = color,
    icon = icon,
    userId = userId,
    updatedAt = updatedAt,
    isSynced = isSynced
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = type,
    color = color,
    icon = icon,
    userId = userId
)

fun Goal.toLocalEntity(
    updatedAt: Long = System.currentTimeMillis(),
    isSynced: Boolean = true
): GoalEntity = GoalEntity(
    id = id,
    title = title,
    description = description,
    image = image,
    deadline = deadline,
    goalAmount = goalAmount,
    amountSaved = amountSaved,
    walletId = walletId,
    currency = currency,
    updatedAt = updatedAt,
    isSynced = isSynced
)

fun GoalEntity.toDomain(): Goal = Goal(
    id = id,
    title = title,
    description = description,
    image = image,
    deadline = deadline,
    goalAmount = goalAmount,
    amountSaved = amountSaved,
    walletId = walletId,
    currency = currency
)

fun Tag.toLocalEntity(
    updatedAt: Long = System.currentTimeMillis(),
    isSynced: Boolean = true
): TagEntity = TagEntity(
    id = id,
    name = name,
    userId = userId,
    updatedAt = updatedAt,
    isSynced = isSynced
)

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    userId = userId ?: 0
)

fun toDomainTransaction(entity: TransactionEntity): com.example.domain.transaction.model.TransactionEntity {
    return com.example.domain.transaction.model.TransactionEntity(
        id = entity.id,
        userId = entity.userId,
        name = entity.name,
        amount = entity.amount,
        type = entity.type,
        categoryId = entity.categoryId,
        walletId = entity.walletId,
        transactionDate = entity.transactionDate,
        note = entity.note,
        createdAt = entity.createdAt,
    )
}
