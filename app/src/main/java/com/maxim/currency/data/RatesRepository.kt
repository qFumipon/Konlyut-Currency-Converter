package com.maxim.currency.data

import android.util.AtomicFile
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.TimeUnit

data class RateSnapshot(val rates: Map<String, BigDecimal>, val rateTime: Long, val nextUpdate: Long, val fetchedAt: Long)
enum class RateError { NETWORK, INVALID, CACHE, LIMITED }
data class RateState(val snapshot: RateSnapshot? = null, val refreshing: Boolean = false, val error: RateError? = null)

object RatesCodec {
    fun parse(json: String, fetchedAt: Long): RateSnapshot {
        require(json.length < 1_000_000)
        val root = JsonParser.parseString(json).asJsonObject
        require(root["result"].asString == "success" && root["base_code"].asString == "USD")
        val rates = root.getAsJsonObject("rates").entrySet().associate { (code, value) ->
            require(Regex("[A-Z]{3}").matches(code))
            val decimal = value.asBigDecimal
            require(decimal.signum() > 0 && decimal.precision() <= 36 && decimal.scale() in -12..18)
            code to decimal
        }
        require(rates.size >= 100 && rates["USD"]?.compareTo(BigDecimal.ONE) == 0)
        val date = root["time_last_update_unix"].asLong
        val next = root["time_next_update_unix"].asLong
        require(date > 1_500_000_000 && date <= fetchedAt + 86_400 && next > date)
        return RateSnapshot(rates, date, next, fetchedAt)
    }
}

/** A single coherent USD snapshot, committed atomically only after validation. No bundled rates. */
class RatesRepository(directory: File,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS).build(),
    private val clock: () -> Long = { Instant.now().epochSecond }
) {
    private val file = AtomicFile(File(directory, "rates-v1.json"))
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(RateState())
    val state = mutable.asStateFlow()
    private var loaded = false
    private var lastAttempt = 0L
    suspend fun refresh(force: Boolean = false, reconnected: Boolean = false) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!loaded) {
                loaded = true
                if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
                    runCatching {
                        val cached = file.openRead().bufferedReader().use { it.readText() }
                        val root = JsonParser.parseString(cached).asJsonObject
                        RatesCodec.parse(root["payload"].asString, root["fetchedAt"].asLong)
                    }.onSuccess { mutable.value = RateState(it) }
                        .onFailure { mutable.value = RateState(error = RateError.CACHE) }
                }
            }
            val now = clock()
            val current = mutable.value.snapshot
            if (now - lastAttempt in 0..59) return@withContext
            if (!force && current != null && now < current.nextUpdate && now - current.fetchedAt in 0..86_399) return@withContext
            if (!force && !(reconnected && mutable.value.error == RateError.NETWORK) && now - lastAttempt in 0..899) return@withContext
            lastAttempt = now
            mutable.value = mutable.value.copy(refreshing = true, error = null)
            try {
                client.newCall(Request.Builder().url("https://open.er-api.com/v6/latest/USD").build()).execute().use { response ->
                    if (response.code == 429) { mutable.value = mutable.value.copy(error = RateError.LIMITED); return@use }
                    check(response.isSuccessful)
                    val body = response.body.string()
                    val snapshot = try { RatesCodec.parse(body, now) } catch (_: Exception) {
                        mutable.value = mutable.value.copy(error = RateError.INVALID); return@use
                    }
                    if (current != null && snapshot.rateTime < current.rateTime) {
                        mutable.value = mutable.value.copy(error = RateError.INVALID); return@use
                    }
                    val json = com.google.gson.JsonObject().apply { addProperty("fetchedAt", now); addProperty("payload", body) }.toString()
                    val output = file.startWrite()
                    try { output.write(json.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
                    catch (e: Exception) { file.failWrite(output); throw e }
                    mutable.value = RateState(snapshot, refreshing = true)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: java.io.IOException) { mutable.value = mutable.value.copy(error = RateError.NETWORK) }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = RateError.INVALID) }
            finally { mutable.value = mutable.value.copy(refreshing = false) }
        }
    }
}
