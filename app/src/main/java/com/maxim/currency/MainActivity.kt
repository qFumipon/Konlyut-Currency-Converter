package com.maxim.currency

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.maxim.currency.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<Pair<String, String>?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as CurrencyApplication).graph
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ConverterViewModel(graph) as T
        })[ConverterViewModel::class.java]
        if (savedInstanceState == null) accept(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val input by vm.input.collectAsStateWithLifecycle()
            ForegroundRefresh(graph)
            LaunchedEffect(incoming.value, input.ready) {
                if (input.ready) incoming.value?.let { (code, amount) -> vm.importAmount(code, amount); incoming.value=null }
            }
            settings?.let { CurrencyTheme(it) { ConverterApp(vm, it) } }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); accept(intent) }
    private fun accept(intent: Intent) {
        val code=intent.getStringExtra("currency") ?: return
        val amount=intent.getStringExtra("amount") ?: return
        incoming.value=code to amount
        intent.removeExtra("currency"); intent.removeExtra("amount")
    }
}

@Composable
fun ForegroundRefresh(graph: AppGraph) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(graph, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { while (true) { graph.rates.refresh(); delay(15 * 60 * 1000L) } }
            launch { graph.connected.collect { if (it) graph.rates.refresh(reconnected = true) } }
        }
    }
}
