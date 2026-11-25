package com.maxim.currency

import com.google.gson.JsonObject
import com.maxim.currency.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RatesRepositoryTest {
    @get:Rule val folder=TemporaryFolder()
    private var now=1_790_000_100L
    private fun payload(time: Long=1_790_000_000): String = JsonObject().apply {
        addProperty("result","success");addProperty("base_code","USD");addProperty("time_last_update_unix",time);addProperty("time_next_update_unix",time+86400)
        add("rates",JsonObject().apply {addProperty("USD",1);repeat(100) {i->addProperty("A"+('A'+i/26)+('A'+i%26),"1.25")}})
    }.toString()
    @Test fun offlineRestartRetainsSuccessfulSnapshot() = runBlocking {
        var online=true;var requests=0
        val client=OkHttpClient.Builder().addInterceptor {chain->
            requests++;if(!online) throw IOException("offline")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(payload().toResponseBody()).build()
        }.build()
        val first=RatesRepository(folder.root,client) {now}
        first.refresh();val saved=first.state.value.snapshot!!
        first.refresh();assertEquals(1,requests)
        now+=86401;online=false
        val reopened=RatesRepository(folder.root,client) {now}
        reopened.refresh()
        assertEquals(saved,reopened.state.value.snapshot)
        assertEquals(RateError.NETWORK,reopened.state.value.error)
        assertFalse(reopened.state.value.refreshing)
    }
    @Test fun invalidResponseCannotReplaceGoodCache() = runBlocking {
        var body=payload()
        val client=OkHttpClient.Builder().addInterceptor {chain->Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()}.build()
        val repository=RatesRepository(folder.root,client) {now}
        repository.refresh();val saved=repository.state.value.snapshot
        now+=120;body="""{"result":"error"}"""
        repository.refresh(force=true)
        assertEquals(saved,repository.state.value.snapshot);assertEquals(RateError.INVALID,repository.state.value.error)
        val reopened=RatesRepository(folder.root,client) {now}
        reopened.refresh();assertEquals(saved,reopened.state.value.snapshot)
    }
    @Test fun reconnectionRetriesNetworkFailureAndManualRequestsAreThrottled() = runBlocking {
        var online=false;var requests=0
        val client=OkHttpClient.Builder().addInterceptor {chain->requests++;if(!online) throw IOException();Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(payload().toResponseBody()).build()}.build()
        val repository=RatesRepository(folder.root,client) {now}
        repository.refresh();repository.refresh(force=true);assertEquals(1,requests)
        now+=61;online=true;repository.refresh(reconnected=true)
        assertEquals(2,requests);assertNotNull(repository.state.value.snapshot)
    }
}
