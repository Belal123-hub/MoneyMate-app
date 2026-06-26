package com.example.data.offline

import com.example.data.database.dao.WalletDao
import com.example.data.database.dao.WalletMemberDao
import com.example.data.database.entity.WalletEntity
import com.example.data.database.mapper.toDomain
import com.example.domain.auth.dataStore.DataStoreDataSource
import com.example.domain.wallet.model.Wallet

/**
 * Resolves wallet sharing role from Room (API field + wallet_members for current user).
 */
class WalletPermissionHelper(
    private val walletDao: WalletDao,
    private val walletMemberDao: WalletMemberDao,
    private val dataStore: DataStoreDataSource
) {
    suspend fun resolveWallet(walletId: Int): Wallet? {
        val entity = walletDao.getWalletById(walletId) ?: return null
        return enrichWallet(entity)
    }

    suspend fun enrichWallet(entity: WalletEntity): Wallet {
        val count = walletMemberDao.getMemberCount(entity.id)
        var wallet = entity.toDomain(count)
        val currentUserId = dataStore.getUserId()?.toIntOrNull()
        if (currentUserId != null && wallet.myRole.isNullOrBlank()) {
            walletMemberDao.getRoleForMember(entity.id, currentUserId)?.let { role ->
                wallet = wallet.withResolvedRole(role)
            }
        }
        return wallet
    }

    suspend fun enrichWallets(entities: List<WalletEntity>): List<Wallet> =
        entities.map { enrichWallet(it) }
}
