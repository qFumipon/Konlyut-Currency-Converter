package com.maxim.currency

import androidx.test.core.app.ApplicationProvider
import com.maxim.currency.data.GitHubUpdates
import com.maxim.currency.data.ReleaseCodec
import com.maxim.currency.data.UpdateInstaller
import com.maxim.currency.data.UpdateRelease
import com.maxim.currency.data.SettingsCodec
import com.maxim.currency.data.UpdateCheckPolicy
import com.maxim.currency.data.DownloadIntegrityException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class GitHubUpdatesTest {
    private val repo="owner/Konlyut-Currency-Converter"
    private fun payload(version: String="1.0.1",digest: String="sha256:"+"a".repeat(64),
        name: String="Konlyut-v$version.apk",prerelease: Boolean=false,
        repository: String=repo): String = """
        {"tag_name":"v$version","draft":false,"prerelease":$prerelease,"body":"Fixes",
         "assets":[{"name":"$name","state":"uploaded","size":4,"digest":"$digest",
         "browser_download_url":"https://github.com/$repository/releases/download/v$version/$name"}]}
    """.trimIndent()
    @Test fun configuredRepositoryTargetsTheExpectedGitHubApi() = runBlocking {
        val repository="qFumipon/Konlyut-Currency-Converter"
        assertEquals(repository,BuildConfig.UPDATE_REPOSITORY)
        var requested=""
        val client=OkHttpClient.Builder().addInterceptor {chain ->
            requested=chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(payload(repository=repository).toResponseBody()).build()
        }.build()
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("1.0.1",GitHubUpdates(context,BuildConfig.UPDATE_REPOSITORY,client).latest().version)
        assertEquals("https://api.github.com/repos/$repository/releases/latest",requested)
    }
    @Test fun semanticVersionsCompareNumerically() {
        assertTrue(ReleaseCodec.compare("1.0.10","1.0.9")>0)
        assertTrue(ReleaseCodec.compare("1.1.0","1.0.99")>0)
        assertEquals(0,ReleaseCodec.compare("v1.0.0","1.0.0"))
        assertTrue(ReleaseCodec.compare("1.0.0","1.0.1")<0)
    }
    @Test fun manualModeHasNoAutomaticChecksAndAutoChecksAtMostDaily() {
        assertFalse(UpdateCheckPolicy.autoDue("manual",0,100_000,true,true))
        assertFalse(UpdateCheckPolicy.autoDue("auto",0,100_000,false,true))
        assertFalse(UpdateCheckPolicy.autoDue("auto",0,100_000,true,false))
        assertTrue(UpdateCheckPolicy.autoDue("auto",0,100_000,true,true))
        assertFalse(UpdateCheckPolicy.autoDue("auto",100_000,186_399,true,true))
        assertTrue(UpdateCheckPolicy.autoDue("auto",100_000,186_400,true,true))
    }
    @Test fun updateModeDefaultsAndPersistsWithoutReplacingExistingSettings() {
        val old=SettingsCodec.decode("""{"selected":["EUR","USD"],"active":"EUR","amount":"12.5"}""")
        assertEquals("auto",old.updateMode)
        val manual=old.copy(updateMode="manual",lastUpdateCheck=1_000_000L)
        assertEquals(manual,SettingsCodec.decode(SettingsCodec.encode(manual)))
        assertEquals(listOf("EUR","USD"),manual.selected)
        assertEquals("12.5",manual.amount)
    }
    @Test fun stableReleaseRequiresExpectedApkAndDigest() {
        val release=ReleaseCodec.parse(payload(),repo)
        assertEquals("1.0.1",release.version)
        assertEquals(4,release.size)
        assertEquals("a".repeat(64),release.sha256)
        assertThrows(IllegalArgumentException::class.java) {ReleaseCodec.parse(payload(prerelease=true),repo)}
        assertThrows(IllegalStateException::class.java) {ReleaseCodec.parse(payload(name="other.apk"),repo)}
        assertThrows(IllegalArgumentException::class.java) {ReleaseCodec.parse(payload(digest="sha256:bad"),repo)}
        assertThrows(IllegalArgumentException::class.java) {ReleaseCodec.parse(payload().replace("https://github.com/","https://evil.example/"),repo)}
    }
    @Test fun apiFailureAndBrokenDownloadDoNotLeaveApk() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val responseClient=OkHttpClient.Builder().addInterceptor {chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404)
                .message("Not Found").body("".toResponseBody()).build()
        }.build()
        assertThrows(IllegalStateException::class.java) {runBlocking {GitHubUpdates(context,repo,responseClient).latest()}}
        val bytes="test".toByteArray()
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
        val release=UpdateRelease("1.0.1","v1.0.1","","https://github.com/$repo/releases/download/v1.0.1/Konlyut-v1.0.1.apk",4,digest)
        val dataClient=OkHttpClient.Builder().addInterceptor {chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body("bad".toResponseBody()).build()
        }.build()
        val updates=GitHubUpdates(context,repo,dataClient)
        assertThrows(DownloadIntegrityException::class.java) {runBlocking {updates.download(release) {}}}
        assertFalse(File(context.cacheDir,"updates/download.part").exists())
        val wrongDigestClient=OkHttpClient.Builder().addInterceptor {chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body("evil".toResponseBody()).build()
        }.build()
        assertThrows(DownloadIntegrityException::class.java) {
            runBlocking {GitHubUpdates(context,repo,wrongDigestClient).download(release) {}}
        }
        assertFalse(File(context.cacheDir,"updates/download.part").exists())
        assertEquals(UpdateInstaller.Verdict.INVALID_APK,
            UpdateInstaller.verify(context,File(context.cacheDir,"updates/missing.apk"),release))
    }
}
