package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.database.converter.DatabaseConverters
import com.example.data.database.dao.CategoryDao
import com.example.data.database.dao.GoalDao
import com.example.data.database.dao.MonthlySavingsGoalDao
import com.example.data.database.dao.PendingOperationDao
import com.example.data.database.dao.SyncMetadataDao
import com.example.data.database.dao.TransactionDao
import com.example.data.database.dao.WalletDao
import com.example.data.database.entity.CategoryEntity
import com.example.data.database.entity.GoalEntity
import com.example.data.database.entity.MonthlySavingsGoalEntity
import com.example.data.database.entity.PendingOperation
import com.example.data.database.entity.SyncMetadata
import com.example.data.database.entity.TransactionEntity
import com.example.data.database.entity.WalletEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Drop existing table if it exists (clean slate)
        database.execSQL("DROP TABLE IF EXISTS monthly_savings_goals")

        // Create table with EXACT schema Room expects
        database.execSQL("""
            CREATE TABLE monthly_savings_goals (
                id INTEGER NOT NULL PRIMARY KEY,
                month INTEGER NOT NULL,
                year INTEGER NOT NULL,
                target_amount REAL NOT NULL,
                current_saved REAL NOT NULL,
                updated_at INTEGER NOT NULL,
                is_synced INTEGER NOT NULL
            )
        """)

        // Create index
        database.execSQL("""
            CREATE INDEX idx_monthly_savings_goals_year_month 
            ON monthly_savings_goals(year, month)
        """)
    }
}

@Database(
    entities = [
        TransactionEntity::class,
        WalletEntity::class,
        CategoryEntity::class,
        GoalEntity::class,
        SyncMetadata::class,
        PendingOperation::class,
        MonthlySavingsGoalEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class MoneyMateDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun goalDao(): GoalDao
    abstract fun monthlySavingsGoalDao(): MonthlySavingsGoalDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun pendingOperationDao(): PendingOperationDao
}
