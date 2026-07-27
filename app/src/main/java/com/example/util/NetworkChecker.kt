package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkChecker {
    @Volatile
    private var lastCheckTime = 0L
    @Volatile
    private var lastResult = true

    fun isInternetAvailable(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime < 3000L) {
            return lastResult
        }
        return try {
            val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: run {
                    lastCheckTime = now
                    lastResult = true
                    return true
                }

            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (networkCapabilities != null) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        lastCheckTime = now
                        lastResult = true
                        return true
                    }
                }
            }

            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            if (networkInfo != null && networkInfo.isConnected) {
                lastCheckTime = now
                lastResult = true
                return true
            }

            lastCheckTime = now
            lastResult = true
            true
        } catch (e: Throwable) {
            lastCheckTime = now
            lastResult = true
            true
        }
    }

    fun isOnline(context: Context): Boolean {
        return isInternetAvailable(context)
    }
}
