package com.example.moneymate

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.example.moneymate.di.appComponent
import com.example.moneymate.utils.AuthenticatedOkHttpFetcher
import com.example.moneymate.utils.AvatarDiagnostics
import com.example.moneymate.utils.network.ConnectivityObserver
import com.example.moneymate.work.SyncWorkScheduler
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent

class MoneyMateApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@MoneyMateApplication)
            allowOverride(true)
            modules(appComponent)
        }

        val connectivityObserver: ConnectivityObserver = KoinJavaComponent.get(ConnectivityObserver::class.java)
        connectivityObserver.start()

        SyncWorkScheduler.enqueuePeriodicSync(this)
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient: OkHttpClient = KoinJavaComponent.getOrNull(
            OkHttpClient::class.java,
            named("coilOkHttp")
        ) ?: run {
            AvatarDiagnostics.logError(
                "ImageLoader",
                "coilOkHttp missing — falling back to main OkHttp (image Accept may differ)",
                null
            )
            KoinJavaComponent.get(OkHttpClient::class.java)
        }

        AvatarDiagnostics.log("ImageLoader", "Using authenticated coilOkHttp for image requests")

        return ImageLoader.Builder(this)
            .components {
                add(AuthenticatedOkHttpFetcher.Factory(okHttpClient))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .logger(DebugLogger())
            .build()
    }
}