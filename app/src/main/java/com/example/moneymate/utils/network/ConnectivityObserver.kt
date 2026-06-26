package com.example.moneymate.utils.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.moneymate.work.SyncWorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectivityObserver(
    private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(hasValidatedInternet())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var started = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateOnlineState()
        }

        override fun onLost(network: Network) {
            updateOnlineState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            updateOnlineState()
        }

        override fun onUnavailable() {
            if (_isOnline.value) {
                _isOnline.value = false
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        _isOnline.value = hasValidatedInternet()
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    /** Re-read system connectivity (e.g. after returning from system settings). */
    fun refresh() {
        updateOnlineState()
    }

    private fun updateOnlineState() {
        val online = hasValidatedInternet()
        if (_isOnline.value == online) return
        _isOnline.value = online
        if (online) {
            SyncWorkScheduler.enqueueImmediateSync(context)
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
