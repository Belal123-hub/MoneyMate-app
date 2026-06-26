package com.example.data.database.mapper

import com.example.data.database.entity.CategoryEntity
import com.example.data.database.entity.GoalEntity
import com.example.data.database.entity.TagEntity
import com.example.data.database.entity.TransactionEntity
import com.example.data.database.entity.WalletEntity
import com.example.data.database.entity.WalletMemberEntity
import com.example.data.network.wallet.model.WalletMemberResponse
import com.example.domain.category.model.Category
import com.example.domain.goal.model.Goal
import com.example.domain.tag.model.Tag
import com.example.domain.transaction.model.TransactionEntity as DomainTransactionEntity
import com.example.domain.wallet.model.Wallet
import com.example.domain.wallet.model.WalletMember
import com.example.domain.wallet.model.WalletCreateRequest
import com.example.domain.wallet.model.WalletUpdateRequest

// ============================================================
// TRANSACTION MAPPERS
// ============================================================

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

// ============================================================
// WALLET MAPPERS (ADD THESE - THEY ARE MISSING!)
// ============================================================

fun WalletEntity.toDomain(memberCount: Int = 0): Wallet = Wallet(
    id = id,
    name = name,
    currency = currency,
    walletType = walletType,
    initialBalance = initialBalance,
    cardNumber = cardNumber,
    color = color,
    balance = balance,
    userId = userId,
    ownerUserId = ownerUserId,
    isShared = isShared,
    myRole = myRole,
    memberCount = memberCount,
    createdAt = createdAt,
    isSynced = isSynced
)

fun Wallet.toLocalEntity(
    updatedAt: Long = System.currentTimeMillis(),
    isSynced: Boolean = true
): WalletEntity {
    return WalletEntity(
        id = this.id,
        name = this.name,
        currency = this.currency,
        walletType = this.walletType,
        initialBalance = this.initialBalance,
        cardNumber = this.cardNumber,
        color = this.color,
        balance = this.balance,
        userId = this.userId,
        ownerUserId = this.ownerUserId ?: 0,
        isShared = this.isShared,
        myRole = this.myRole,
        createdAt = this.createdAt,
        updatedAt = updatedAt,
        isSynced = isSynced
    )
}

// ============================================================
// WALLET MEMBER MAPPERS
// ============================================================

fun WalletMemberEntity.toDomain(): WalletMember = WalletMember(
    id = id,
    walletId = walletId,
    userId = userId,
    userEmail = userEmail,
    userName = userName,
    role = role,
    joinedAt = joinedAt
)

fun WalletMember.toLocalEntity(
    walletId: Int,
    isSynced: Boolean = true
): WalletMemberEntity = WalletMemberEntity(
    id = id,
    walletId = walletId,
    userId = userId,
    userEmail = userEmail,
    userName = userName,
    role = role,
    joinedAt = joinedAt,
    isSynced = isSynced
)

fun WalletMemberResponse.toLocalEntity(
    walletId: Int,
    isSynced: Boolean = true
): WalletMemberEntity = WalletMemberEntity(
    id = this.userId,
    walletId = walletId,
    userId = this.userId,
    userEmail = this.userEmail,
    userName = this.userName,
    role = this.role,
    joinedAt = this.joinedAt,
    isSynced = isSynced
)

fun WalletMemberResponse.toDomain(walletId: Int): WalletMember = WalletMember(
    id = this.userId,
    walletId = walletId,
    userId = this.userId,
    userEmail = this.userEmail,
    userName = this.userName,
    role = this.role,
    joinedAt = this.joinedAt
)

// ============================================================
// CATEGORY MAPPERS
// ============================================================

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

// ============================================================
// GOAL MAPPERS
// ============================================================

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

// ============================================================
// TAG MAPPERS
// ============================================================

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

// ============================================================
// HELPER
// ============================================================

fun toDomainTransaction(entity: TransactionEntity): DomainTransactionEntity {
    return DomainTransactionEntity(
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