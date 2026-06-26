package com.example.data.offline

import com.example.data.database.dao.MonthlySavingsGoalDao
import com.example.data.database.dao.TransactionDao

/**
 * Keeps [com.example.data.database.entity.MonthlySavingsGoalEntity.currentSaved] aligned with
 * local savings-wallet transactions **without** discarding the server-provided baseline.
 *
 * Uses a per-month **transaction net anchor** so updates are idempotent:
 * - First run with `savings_tx_net_anchor == null`: lock anchor to current tx net, leave `current_saved` unchanged.
 * - Later: `new_saved = current_saved + (txNet - anchor)`, then store the new anchor `txNet`.
 */
class MonthlySavingsLocalRecalculator(
    private val transactionDao: TransactionDao,
    private val monthlySavingsGoalDao: MonthlySavingsGoalDao
) {

    suspend fun recalculateForYearMonth(year: Int, month: Int) {
        val goal = monthlySavingsGoalDao.getMonthlySavingsGoalByMonth(year, month)
        if (goal == null) {
            println("SAVINGS_PROGRESS: skip — no monthly_savings_goals row for year=$year month=$month")
            return
        }

        val txNet = transactionDao.sumSavingsWalletIncomeMinusExpenseForMonth(year, month)
        val now = System.currentTimeMillis()
        val anchor = goal.savingsTxNetAnchor
        val target = goal.targetAmount
        val beforeSaved = goal.currentSaved
        val progressBefore = if (target > 1e-12) (beforeSaved / target * 100.0) else 0.0

        println(
            "SAVINGS_PROGRESS: month=$year-$month txNet=$txNet anchor=${anchor ?: "null"} " +
                "saved=$beforeSaved target=$target (${"%.1f".format(progressBefore)}%)"
        )

        if (anchor == null) {
            if (goal.currentSaved <= 1e-9 && txNet > 1e-9) {
                val rows = monthlySavingsGoalDao.updateCurrentSavedAndSavingsTxNetAnchor(
                    year = year,
                    month = month,
                    currentSaved = txNet,
                    anchor = txNet,
                    updatedAt = now
                )
                println(
                    "SAVINGS_RECALC: bootstrap year=$year month=$month " +
                        "seeded current_saved from txNet=$txNet (was 0) rows=$rows"
                )
            } else {
                val rows = monthlySavingsGoalDao.updateSavingsTxNetAnchorOnly(year, month, txNet, now)
                println(
                    "SAVINGS_RECALC: bootstrap year=$year month=$month " +
                        "lockedAnchorToTxNet=$txNet current_saved_unchanged=${goal.currentSaved} rows=$rows"
                )
            }
            logProgressOut(year, month, target)
            return
        }

        var newSaved = goal.currentSaved + (txNet - anchor)
        var rows = monthlySavingsGoalDao.updateCurrentSavedAndSavingsTxNetAnchor(
            year = year,
            month = month,
            currentSaved = newSaved,
            anchor = txNet,
            updatedAt = now
        )
        println(
            "SAVINGS_RECALC: year=$year month=$month " +
                "txNet=$txNet anchorWas=$anchor deltaTxNet=${txNet - anchor} " +
                "savedWas=${goal.currentSaved} savedNow=$newSaved rows=$rows"
        )

        // Repair: ledger shows savings activity but rolling delta left saved at ~0 (bad anchor/API baseline).
        if (newSaved <= 1e-9 && txNet > 1e-9) {
            println(
                "SAVINGS_PROGRESS: repair — forcing current_saved=txNet=$txNet (was newSaved=$newSaved)"
            )
            rows = monthlySavingsGoalDao.updateCurrentSavedAndSavingsTxNetAnchor(
                year = year,
                month = month,
                currentSaved = txNet,
                anchor = txNet,
                updatedAt = now
            )
            println("SAVINGS_RECALC: repair rows=$rows")
        }

        logProgressOut(year, month, target)
    }

    private suspend fun logProgressOut(year: Int, month: Int, targetFallback: Double) {
        val updated = monthlySavingsGoalDao.getMonthlySavingsGoalByMonth(year, month) ?: return
        val t = if (updated.targetAmount > 1e-12) updated.targetAmount else targetFallback
        val pct = if (t > 1e-12) (updated.currentSaved / t * 100.0) else 0.0
        println(
            "SAVINGS_PROGRESS: after recalc month=$year-$month saved=${updated.currentSaved} " +
                "target=$t (${"%.1f".format(pct)}%)"
        )
    }

    suspend fun recalculateAllCachedMonths() {
        val keys = monthlySavingsGoalDao.getMonthlySavingsGoals().map { it.year to it.month }.distinct()
        println("SAVINGS_RECALC: recalculateAllCachedMonths — ${keys.size} distinct month(s)")
        keys.forEach { (y, m) -> recalculateForYearMonth(y, m) }
    }
}
