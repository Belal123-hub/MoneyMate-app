package com.example.moneymate.utils

import android.util.Log

object AvatarDiagnostics {
    const val TAG = "MoneyMateAvatar"

    fun log(stage: String, detail: String) {
        Log.i(TAG, "[$stage] $detail")
    }

    fun logError(stage: String, detail: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "[$stage] $detail", throwable)
        } else {
            Log.e(TAG, "[$stage] $detail")
        }
    }
}
