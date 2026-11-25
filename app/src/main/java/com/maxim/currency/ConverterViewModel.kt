package com.maxim.currency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxim.currency.domain.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.io.File
import com.maxim.currency.data.UpdateRelease
import com.maxim.currency.data.UpdateInstaller

sealed interface UpdateUiState {
    data object Idle: UpdateUiState
    data object Checking: UpdateUiState
    data object Latest: UpdateUiState
    data class Available(val release: UpdateRelease): UpdateUiState
    data class Downloading(val release: UpdateRelease,val percent: Int): UpdateUiState
    data class Ready(val release: UpdateRelease,val file: File): UpdateUiState
    data class Failure(val reason: Reason): UpdateUiState
    enum class Reason { OFFLINE, NOT_CONFIGURED, NETWORK, NO_APK, BAD_APK, WRONG_PACKAGE, WRONG_VERSION, WRONG_SIGNATURE }
}

data class InputState(val code: String = "USD", val raw: String = "1", val replace: Boolean = true, val ready: Boolean = false) {
    fun switchCurrency(to: String, rates: Map<String, BigDecimal>): InputState? {
        if (to == code) return this
        val converted = Conversion.convert(raw.toBigDecimalOrNull() ?: BigDecimal.ZERO, code, to, rates) ?: return null
        return InputState(to, Conversion.canonical(converted), true, true)
    }

    fun applyKey(key: String): InputState? {
        // The full-precision value is kept for conversion. The first edit after a
        // currency switch starts from the rounded amount actually shown on screen.
        val editable = if (replace) Conversion.editable(raw.toBigDecimalOrNull() ?: BigDecimal.ZERO) else raw
        val next = when (key) {
            "C" -> "0"
            "⌫" -> editable.dropLast(1).takeUnless { it.isEmpty() || it == "-" } ?: "0"
            "." -> if (replace) "0." else if ('.' in raw) raw else raw + "."
            else -> if (key.singleOrNull()?.isDigit() != true) return null else if (replace || raw == "0") key else raw + key
        }
        if (key !in listOf("C", "⌫") && (next.count(Char::isDigit) > 36 || next.substringAfter('.', "").length > 12)) return null
        return copy(raw = next, replace = false)
    }
}
class ConverterViewModel(val graph: AppGraph) : ViewModel() {
    val settings = graph.settings
    val rates = graph.rates.state
    val connected = graph.connected
    private val mutableInput = MutableStateFlow(InputState())
    val input = mutableInput.asStateFlow()
    val storageError = MutableStateFlow(false)
    private val mutableUpdate=MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState=mutableUpdate.asStateFlow()
    private var checkJob: Job?=null
    private var downloadJob: Job?=null
    private val edits = Channel<(UserSettings) -> UserSettings>(Channel.UNLIMITED)
    init {
        viewModelScope.launch {
            val saved = settings.filterNotNull().first()
            mutableInput.value = InputState(saved.active, saved.amount, true, true)
        }
        // Application scope finishes already accepted writes even when an Activity is closed.
        graph.scope.launch {
            for (transform in edits) {
                runCatching { graph.store.update(transform) }.onFailure { storageError.value = true }
            }
        }
    }
    override fun onCleared() { edits.close() }
    fun edit(transform: (UserSettings) -> UserSettings) { edits.trySend(transform) }
    private fun persistInput() {
        val value = input.value
        edit { it.copy(active = value.code, amount = value.raw, selected = if (value.code in it.selected) it.selected else it.selected + value.code) }
    }
    fun choose(code: String): Boolean {
        val old = input.value
        val next = old.switchCurrency(code, rates.value.snapshot?.rates.orEmpty()) ?: return false
        mutableInput.value = next
        if (code != old.code) persistInput()
        return true
    }
    fun importAmount(code: String, amount: String) {
        if (graph.catalog.supported(rates.value.snapshot).none { it.code == code } ||
            !Regex("[+-]?[0-9]{1,36}(\\.[0-9]{0,12})?").matches(amount) || amount.toBigDecimalOrNull() == null) return
        mutableInput.value = InputState(code, amount, true, true)
        persistInput()
    }
    fun key(key: String) {
        mutableInput.value = input.value.applyKey(key) ?: return
        persistInput()
    }
    fun remove(code: String) {
        val current = settings.value ?: return
        if (current.selected.size <= 1) return
        val next = current.selected.filterNot { it == code }
        if (input.value.code == code && !choose(next.first())) {
            mutableInput.value = InputState(next.first(), "0", true, true)
            persistInput()
        }
        edit { it.copy(selected = next, active = input.value.code, amount = input.value.raw) }
    }
    fun reorder(from: String, to: String) = edit { s ->
        val list = s.selected.toMutableList()
        val a = list.indexOf(from); val b = list.indexOf(to)
        if (a >= 0 && b >= 0) { list.removeAt(a); list.add(b, from) }
        s.copy(selected = list)
    }
    fun refresh() { viewModelScope.launch { graph.rates.refresh(true) } }
    fun checkUpdates(manual: Boolean) {
        if(checkJob?.isActive==true || downloadJob?.isActive==true) return
        val current=settings.value ?: return
        if(!manual && !com.maxim.currency.data.UpdateCheckPolicy.autoDue(current.updateMode,current.lastUpdateCheck,
                System.currentTimeMillis()/1000,connected.value,graph.updates.configured)) return
        if(!graph.updates.configured) {
            if(manual) mutableUpdate.value=UpdateUiState.Failure(UpdateUiState.Reason.NOT_CONFIGURED)
            return
        }
        if(!connected.value) {
            if(manual) mutableUpdate.value=UpdateUiState.Failure(UpdateUiState.Reason.OFFLINE)
            return
        }
        checkJob=viewModelScope.launch {
            mutableUpdate.value=UpdateUiState.Checking
            try {
                val release=graph.updates.latest()
                edit {it.copy(lastUpdateCheck=System.currentTimeMillis()/1000)}
                mutableUpdate.value=if(com.maxim.currency.data.ReleaseCodec.compare(release.version,BuildConfig.VERSION_NAME)>0)
                    UpdateUiState.Available(release) else UpdateUiState.Latest
            } catch(e: CancellationException) {throw e}
            catch(_: com.maxim.currency.data.MissingUpdateApk) {mutableUpdate.value=UpdateUiState.Failure(UpdateUiState.Reason.NO_APK)}
            catch(_: Exception) {mutableUpdate.value=UpdateUiState.Failure(UpdateUiState.Reason.NETWORK)}
        }
    }
    fun dismissUpdate() {
        checkJob?.cancel();downloadJob?.cancel();mutableUpdate.value=UpdateUiState.Idle
    }
    fun downloadUpdate(release: UpdateRelease, context: android.content.Context) {
        if(downloadJob?.isActive==true) return
        downloadJob=viewModelScope.launch {
            mutableUpdate.value=UpdateUiState.Downloading(release,0)
            try {
                val file=graph.updates.download(release) {percent ->
                    mutableUpdate.value=UpdateUiState.Downloading(release,percent)
                }
                val verdict=withContext(Dispatchers.IO) {UpdateInstaller.verify(context,file,release)}
                mutableUpdate.value=when(verdict) {
                    UpdateInstaller.Verdict.VALID -> UpdateUiState.Ready(release,file)
                    UpdateInstaller.Verdict.WRONG_PACKAGE -> UpdateUiState.Failure(UpdateUiState.Reason.WRONG_PACKAGE)
                    UpdateInstaller.Verdict.WRONG_VERSION -> UpdateUiState.Failure(UpdateUiState.Reason.WRONG_VERSION)
                    UpdateInstaller.Verdict.WRONG_SIGNATURE -> UpdateUiState.Failure(UpdateUiState.Reason.WRONG_SIGNATURE)
                    UpdateInstaller.Verdict.INVALID_APK -> UpdateUiState.Failure(UpdateUiState.Reason.BAD_APK)
                }
                if(verdict!=UpdateInstaller.Verdict.VALID) file.delete()
            } catch(e: CancellationException) {
                File(context.cacheDir,"updates/Konlyut-${release.tag}.apk").delete()
                throw e
            }
            catch(_: com.maxim.currency.data.DownloadIntegrityException) {mutableUpdate.value=UpdateUiState.Failure(UpdateUiState.Reason.BAD_APK)}
            catch(_: Exception) {mutableUpdate.value=UpdateUiState.Failure(UpdateUiState.Reason.NETWORK)}
        }
    }
    fun cancelDownload() {
        val release=(mutableUpdate.value as? UpdateUiState.Downloading)?.release ?: return
        downloadJob?.cancel();mutableUpdate.value=UpdateUiState.Available(release)
    }
}
