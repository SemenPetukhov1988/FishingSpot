package ru.semen.fishingspot.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.LiveData

class NetworkMonitor(context: Context) : LiveData<Boolean>(true) { // ✅ Инициализируем как true (оптимистично)

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val TAG = "NETWORK_MONITOR"

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "✅ onAvailable: Сеть появилась!")
            postValue(true)
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "❌ onLost: Сеть пропала!")
            postValue(false)
        }
    }

    override fun onActive() {
        super.onActive()
        Log.d(TAG, "👂 onActive: Появился наблюдатель (Observer). Регистрируем callback.")
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Сразу проверяем текущий статус и принудительно обновляем значение
        val currentStatus = isNetworkAvailable()
        Log.d(TAG, "🔍 Первоначальная проверка сети: $currentStatus")
        value = currentStatus
    }

    override fun onInactive() {
        super.onInactive()
        Log.d(TAG, "💤 onInactive: Наблюдателей нет. Отменяем регистрацию callback.")
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            Log.d(TAG, "⚠️ activeNetwork == null")
            return false
        }
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (capabilities == null) {
            Log.d(TAG, "⚠️ capabilities == null")
            return false
        }
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Log.d(TAG, "🌐 hasCapability(INTERNET): $hasInternet")
        return hasInternet
    }

    companion object {
        @Volatile
        private var instance: NetworkMonitor? = null

        fun getInstance(context: Context): NetworkMonitor {
            return instance ?: synchronized(this) {
                instance ?: NetworkMonitor(context.applicationContext).also { instance = it }
            }
        }
    }
}