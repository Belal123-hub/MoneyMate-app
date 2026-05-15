package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.database.converter.DatabaseConverters
import com.example.data.database.dao.BudgetDao
import com.example.data.database.dao.CategoryDao
import com.example.data.database.dao.GoalDao
import com.example.data.database.dao.MonthlySavingsGoalDao
import com.example.data.database.dao.PendingOperationDao
import com.example.data.database.dao.SyncMetadataDao
import com.example.data.database.dao.TagDao
import com.example.data.database.dao.TransactionDao
import com.example.data.database.dao.WalletDao
import com.example.data.database.entity.BudgetEntity
import com.example.data.database.entity.CategoryEntity
import com.example.data.database.entity.GoalEntity
import com.example.data.database.entity.MonthlySavingsGoalEntity
import com.example.data.database.entity.PendingOperation
import com.example.data.database.entity.SyncMetadata
import com.example.data.database.entity.TagEntity
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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                user_id INTEGER,
                updated_at INTEGER NOT NULL,
                is_synced INTEGER NOT NULL
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS budgets (
                id INTEGER NOT NULL PRIMARY KEY,
                month INTEGER NOT NULL,
                year INTEGER NOT NULL,
                monthly_limit REAL NOT NULL,
                daily_limit REAL NOT NULL,
                monthly_spent REAL NOT NULL,
                daily_spent REAL NOT NULL,
                last_updated_date TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                is_synced INTEGER NOT NULL
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_budgets_year_month ON budgets(year, month)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE monthly_savings_goals ADD COLUMN savings_tx_net_anchor REAL DEFAULT NULL"
        )
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
        MonthlySavingsGoalEntity::class,
        TagEntity::class,
        BudgetEntity::class
    ],
    version = 5,
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
    abstract fun tagDao(): TagDao
    abstract fun budgetDao(): BudgetDao
}
