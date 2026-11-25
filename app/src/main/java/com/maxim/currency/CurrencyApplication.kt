package com.maxim.currency

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import com.maxim.currency.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.File

class CurrencyApplication : Application() {
    val graph by lazy { AppGraph(this) }
}
class AppGraph(application: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store = SettingsStore(PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }, scope = scope,
        produceFile = { File(application.filesDir, "settings.preferences_pb") }
    ))
    val settings = store.settings.map { it as com.maxim.currency.domain.UserSettings? }
        .stateIn(scope, SharingStarted.Eagerly, null)
    val rates = RatesRepository(application.filesDir)
    val updates = GitHubUpdates(application)
    val catalog = CurrencyCatalog(application)
    val connected = callbackFlow {
        val manager = application.getSystemService(ConnectivityManager::class.java)
        fun current() = manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(current()) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(current()) }
        }
        trySend(current())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(5000), false)
}
