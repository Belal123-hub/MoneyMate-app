package com.example.moneymate

import android.app.Application
import com.example.moneymate.di.appComponent
import com.example.moneymate.utils.network.ConnectivityObserver
import com.example.moneymate.work.SyncWorkScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent

class MoneyMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@MoneyMateApplication)
            allowOverride(true)
            modules(appComponent)
        }

        // FIX 1: Get ConnectivityObserver from Koin properly
        val connectivityObserver: ConnectivityObserver = KoinJavaComponent.get(ConnectivityObserver::class.java)
        connectivityObserver.start()

        SyncWorkScheduler.enqueuePeriodicSync(this)
    }
}