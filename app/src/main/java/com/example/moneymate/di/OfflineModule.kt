package com.example.moneymate.di

import androidx.room.Room
import com.example.data.database.MIGRATION_1_2
import com.example.data.database.MIGRATION_2_3
import com.example.data.database.MIGRATION_3_4
import com.example.data.database.MIGRATION_4_5
import com.example.data.database.MIGRATION_5_6
import com.example.data.database.MoneyMateDatabase
import com.example.data.network.common.Network
import com.example.data.network.category.CategoryRepositoryImpl
import com.example.data.network.goal.GoalRepositoryImpl
import com.example.data.network.tag.TagRepositoryImpl
import com.example.data.network.sync.OfflineSyncApi
import com.example.data.network.transaction.TransactionRepositoryImpl
import com.example.data.network.wallet.WalletRepositoryImpl
import com.example.data.offline.MonthlySavingsLocalRecalculator
import com.example.data.offline.OfflineSyncOrchestrator
import com.example.data.offline.WalletBalanceRecalculator
import com.example.data.offline.WalletPermissionHelper
import com.example.data.offline.OfflineSyncStatusDataSource
import com.example.data.offline.repository.OfflineCategoryRepositoryImpl
import com.example.data.offline.repository.OfflineGoalRepositoryImpl
import com.example.data.offline.repository.OfflineTagRepositoryImpl
import com.example.data.offline.repository.OfflineTransactionRepositoryImpl
import com.example.data.offline.repository.OfflineWalletRepositoryImpl
import com.example.domain.category.CategoryRepository
import com.example.domain.goal.GoalRepository
import com.example.domain.tag.TagRepository
import com.example.domain.transaction.TransactionRepository
import com.example.domain.wallet.WalletRepository
import com.example.moneymate.utils.network.ConnectivityObserver
import org.koin.dsl.module

val offlineModule = module {
    single { ConnectivityObserver(get()) }

    single {
        Room.databaseBuilder(
            get(),
            MoneyMateDatabase::class.java,
            "moneymate_offline.db"
        )
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .build()
    }

    single { get<MoneyMateDatabase>().transactionDao() }
    single { get<MoneyMateDatabase>().walletDao() }
    single { get<MoneyMateDatabase>().walletMemberDao() }
    single { get<MoneyMateDatabase>().categoryDao() }
    single { get<MoneyMateDatabase>().goalDao() }
    single { get<MoneyMateDatabase>().monthlySavingsGoalDao() }  // ← ADD THIS LINE
    single { get<MoneyMateDatabase>().syncMetadataDao() }
    single { get<MoneyMateDatabase>().pendingOperationDao() }
    single { get<MoneyMateDatabase>().tagDao() }
    single { get<MoneyMateDatabase>().budgetDao() }

    single<OfflineSyncApi> { Network.getApi(get()) }

    single { WalletBalanceRecalculator(get(), get()) }
    single { MonthlySavingsLocalRecalculator(get(), get()) }
    single { WalletPermissionHelper(get(), get(), get()) }

    single {
        OfflineSyncOrchestrator(
            syncApi = get(),
            transactionApi = get(),
            walletApi = get(),
            goalApi = get(),
            transactionDao = get(),
            walletDao = get(),
            walletMemberDao = get(),
            categoryDao = get(),
            goalDao = get(),
            tagDao = get(),
            syncMetadataDao = get(),
            pendingOperationDao = get(),
            monthlySavingsGoalDao = get(),
            monthlySavingsLocalRecalculator = get(),
            walletBalanceRecalculator = get()
        )
    }
    single { OfflineSyncStatusDataSource(get(), get()) }

    single<WalletRepository> {
        OfflineWalletRepositoryImpl(
            remoteRepository = get<WalletRepositoryImpl>(),
            walletDao = get(),
            walletMemberDao = get(),
            transactionDao = get(),
            syncOrchestrator = get(),
            walletBalanceRecalculator = get(),
            pendingOperationDao = get(),
            walletPermissionHelper = get()
        )
    }
    single<TransactionRepository> {
        OfflineTransactionRepositoryImpl(
            remoteRepository = get<TransactionRepositoryImpl>(),
            transactionDao = get(),
            syncOrchestrator = get(),
            pendingOperationDao = get(),
            monthlySavingsLocalRecalculator = get(),
            savingsGoalRepository = get(),
            monthlySavingsGoalDao = get(),
            walletBalanceRecalculator = get(),
            walletPermissionHelper = get()
        )
    }
    single<CategoryRepository> {
        OfflineCategoryRepositoryImpl(
            remoteRepository = get<CategoryRepositoryImpl>(),
            categoryDao = get(),
            syncOrchestrator = get()
        )
    }
    single<GoalRepository> {
        OfflineGoalRepositoryImpl(
            remoteRepository = get<GoalRepositoryImpl>(),
            goalDao = get(),
            pendingOperationDao = get(),
            syncOrchestrator = get()
        )
    }

    single<TagRepository> {
        OfflineTagRepositoryImpl(
            remoteRepository = get<TagRepositoryImpl>(),
            tagDao = get(),
            syncOrchestrator = get()
        )
    }
}