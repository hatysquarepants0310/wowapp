package com.azeroth.companion.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.azeroth.companion.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

@Serializable
private data class GithubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(val name: String = "", val browser_download_url: String = "")

sealed interface UpdateStatus {
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val version: String, val notes: String, val apkUrl: String) : UpdateStatus
    data class Downloading(val version: String) : UpdateStatus
    data class Error(val reason: String) : UpdateStatus
}

/**
 * Actualización dentro de la app (§0.2, sin depender de tiendas): consulta el
 * último release de GitHub, compara la versión y, si hay una nueva, descarga el
 * APK y lanza el instalador del sistema. Cero backend propio.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val latestUrl =
        "https://api.github.com/repos/hatysquarepants0310/wowapp/releases/latest"

    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(latestUrl)
                .header("Accept", "application/vnd.github+json")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateStatus.Error("GitHub respondió HTTP ${response.code}")
                }
                val release = json.decodeFromString(
                    GithubRelease.serializer(), response.body?.string().orEmpty(),
                )
                val latest = release.tag_name.removePrefix("v")
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext UpdateStatus.Error("El release no trae APK adjunto.")
                if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                    UpdateStatus.Available(latest, release.body.take(400), apk.browser_download_url)
                } else {
                    UpdateStatus.UpToDate
                }
            }
        }.getOrElse { UpdateStatus.Error(it.message ?: "Fallo al consultar actualizaciones") }
    }

    /** Descarga el APK a la caché y lanza el instalador del sistema. */
    suspend fun downloadAndInstall(apkUrl: String): UpdateStatus = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val apkFile = File(dir, "azeroth-update.apk")
            val request = Request.Builder().url(apkUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateStatus.Error("Descarga fallida: HTTP ${response.code}")
                }
                response.body?.byteStream()?.use { input ->
                    apkFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext UpdateStatus.Error("Respuesta vacía al descargar el APK.")
            }
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apkFile,
            )
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(install)
            UpdateStatus.UpToDate
        }.getOrElse { UpdateStatus.Error(it.message ?: "Fallo al descargar o instalar") }
    }

    /** Compara versiones semánticas "a.b.c"; true si [latest] > [current]. */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
