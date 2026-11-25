package com.maxim.currency.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import com.maxim.currency.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateRelease(val version: String, val tag: String, val notes: String,
    val apkUrl: String, val size: Long, val sha256: String)
class MissingUpdateApk : IllegalStateException("Release has no compatible APK")
class DownloadIntegrityException : java.io.IOException("APK size or SHA-256 mismatch")

object UpdateCheckPolicy {
    fun autoDue(mode: String,lastSuccessfulCheck: Long,now: Long,connected: Boolean,configured: Boolean): Boolean =
        mode=="auto" && connected && configured && (lastSuccessfulCheck==0L || now-lastSuccessfulCheck>=86_400)
}

object ReleaseCodec {
    private val versionPattern=Regex("(?:v)?([0-9]+)\\.([0-9]+)\\.([0-9]+)")
    fun compare(a: String,b: String): Int {
        fun parts(value: String): List<Long> {
            val match=versionPattern.matchEntire(value) ?: error("Invalid version")
            return match.groupValues.drop(1).map {it.toLong()}
        }
        val left=parts(a);val right=parts(b)
        for(i in 0..2) if(left[i]!=right[i]) return left[i].compareTo(right[i])
        return 0
    }
    fun parse(json: String, repo: String): UpdateRelease {
        require(Regex("[A-Za-z0-9-]+/Konlyut-Currency-Converter").matches(repo))
        require(json.length<=256_000)
        val root=JsonParser.parseString(json).asJsonObject
        require(!root["draft"].asBoolean && !root["prerelease"].asBoolean)
        val tag=root["tag_name"].asString
        val version=versionPattern.matchEntire(tag)?.groupValues?.drop(1)?.joinToString(".") ?: error("Invalid release tag")
        val assetName="Konlyut-v$version.apk"
        val assets=root.getAsJsonArray("assets") ?: error("Missing assets")
        val asset=assets.firstOrNull {it.asJsonObject.get("name")?.asString==assetName}?.asJsonObject ?: throw MissingUpdateApk()
        require(asset.get("state")?.asString=="uploaded")
        val size=asset["size"].asLong
        require(size in 1..100_000_000)
        val digest=asset.get("digest")?.asString ?: error("Missing SHA-256")
        require(Regex("sha256:[0-9a-fA-F]{64}").matches(digest))
        val url=asset["browser_download_url"].asString
        val uri=URI(url)
        require(uri.scheme=="https" && uri.host=="github.com" && uri.port==-1 && uri.userInfo==null && uri.query==null && uri.fragment==null)
        require(uri.path=="/$repo/releases/download/$tag/$assetName")
        return UpdateRelease(version,tag,root.get("body")?.takeUnless {it.isJsonNull}?.asString.orEmpty().take(5000),
            url,size,digest.substringAfter(':').lowercase())
    }
}

class GitHubUpdates(private val context: Context, private val repo: String = BuildConfig.UPDATE_REPOSITORY,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS)
        .readTimeout(20,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).followRedirects(false).build()) {
    val configured get() = repo.isNotBlank()
    suspend fun latest(): UpdateRelease = withContext(Dispatchers.IO) {
        require(configured)
        val url="https://api.github.com/repos/$repo/releases/latest"
        client.newCall(Request.Builder().url(url).header("Accept","application/vnd.github+json")
            .header("User-Agent","Konlyut-Android/${BuildConfig.VERSION_NAME}").build()).execute().use {response ->
            check(response.isSuccessful) {"GitHub API ${response.code}"}
            val output=ByteArrayOutputStream()
            response.body.byteStream().use {input ->
                val buffer=ByteArray(8192)
                while(true) {
                    val count=input.read(buffer)
                    if(count<0) break
                    require(output.size()+count<=256_000)
                    output.write(buffer,0,count)
                }
            }
            val body=output.toString(Charsets.UTF_8.name())
            ReleaseCodec.parse(body,repo)
        }
    }

    suspend fun download(release: UpdateRelease,onProgress: (Int)->Unit): File = withContext(Dispatchers.IO) {
        require(release.size in 1..100_000_000 && Regex("[0-9a-f]{64}").matches(release.sha256))
        require(URI(release.apkUrl).path=="/$repo/releases/download/${release.tag}/Konlyut-v${release.version}.apk")
        val directory=File(context.cacheDir,"updates").apply {mkdirs()}
        val part=File(directory,"download.part")
        val ready=File(directory,"Konlyut-${release.tag}.apk")
        part.delete()
        try {
            var url=release.apkUrl
            var redirects=0
            while(true) {
                val uri=URI(url)
                require(uri.scheme=="https" && uri.userInfo==null && uri.port==-1 &&
                    (uri.host=="github.com" || uri.host.endsWith(".githubusercontent.com")))
                val call=client.newCall(Request.Builder().url(url).header("User-Agent","Konlyut-Android/${BuildConfig.VERSION_NAME}").build())
                val cancellation=currentCoroutineContext()[Job]?.invokeOnCompletion {call.cancel()}
                try {call.execute().use {response ->
                    if(response.code in 300..399) {
                        require(++redirects<=5)
                        url=uri.resolve(response.header("Location") ?: error("Missing redirect")).toString()
                    } else {
                        check(response.isSuccessful) {"Download HTTP ${response.code}"}
                        val digest=MessageDigest.getInstance("SHA-256")
                        var count=0L
                        part.outputStream().use {output -> response.body.byteStream().use {input ->
                            val buffer=ByteArray(32_768)
                            while(true) {
                                currentCoroutineContext().ensureActive()
                                val n=input.read(buffer)
                                if(n<0) break
                                count+=n
                                if(count>release.size || count>100_000_000) throw DownloadIntegrityException()
                                digest.update(buffer,0,n);output.write(buffer,0,n)
                                onProgress((count*100/release.size).toInt())
                            }
                        }}
                        if(count!=release.size) throw DownloadIntegrityException()
                        val actual=digest.digest().joinToString("") {"%02x".format(it)}
                        if(!actual.equals(release.sha256,true)) throw DownloadIntegrityException()
                        ready.delete()
                        check(part.renameTo(ready))
                        return@withContext ready
                    }
                }} finally {cancellation?.dispose()}
            }
            error("Unreachable")
        } catch(e: Exception) {
            part.delete()
            throw e
        }
    }
}

object UpdateInstaller {
    enum class Verdict { VALID, WRONG_PACKAGE, WRONG_VERSION, WRONG_SIGNATURE, INVALID_APK }
    @Suppress("DEPRECATION")
    fun verify(context: Context,apk: File,release: UpdateRelease): Verdict {
        val pm=context.packageManager
        val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive=pm.getPackageArchiveInfo(apk.absolutePath,flags) ?: return Verdict.INVALID_APK
        val installed=pm.getPackageInfo(context.packageName,flags)
        if(archive.packageName!=context.packageName) return Verdict.WRONG_PACKAGE
        fun code(info: PackageInfo) = if(Build.VERSION.SDK_INT>=28) info.longVersionCode else info.versionCode.toLong()
        if(archive.versionName!=release.version || code(archive)<=code(installed)) return Verdict.WRONG_VERSION
        fun certificates(info: PackageInfo): List<ByteArray> = if(Build.VERSION.SDK_INT>=28)
            info.signingInfo?.apkContentsSigners?.map {it.toByteArray()} ?: emptyList()
            else info.signatures?.map {it.toByteArray()} ?: emptyList()
        val a=certificates(archive);val b=certificates(installed)
        if(a.isEmpty() || b.isEmpty() || a.size!=b.size || a.any {cert -> b.none {it.contentEquals(cert)}}) return Verdict.WRONG_SIGNATURE
        return Verdict.VALID
    }
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()
    fun permissionIntent(context: Context): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"))
    fun install(context: Context,apk: File) {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",apk)
        context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
