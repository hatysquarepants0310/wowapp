package com.azeroth.companion.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.azeroth.companion.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GithubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0,
)

sealed interface UpdateStatus {
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
    ) : UpdateStatus
    data class Downloading(val version: String, val percent: Int) : UpdateStatus
    /** Falta el permiso "instalar apps desconocidas"; hay que enviar al usuario a ajustes. */
    data class NeedsInstallPermission(val version: String, val apkUrl: String) : UpdateStatus
    data object ReadyToInstall : UpdateStatus
    data class Error(val reason: String) : UpdateStatus
}

/**
 * Actualización dentro de la app (§0.2, sin depender de tiendas): consulta el
 * último release de GitHub, descarga el APK y lanza el instalador del sistema.
 *
 * Robustez aprendida de fallos reales de instalación:
 * - Cliente HTTP propio con timeout largo: el APK pesa ~21 MB y el timeout
 *   corto del cliente general truncaba la descarga → "error de paquete".
 * - Verificación de tamaño contra Content-Length: nunca se lanza el instalador
 *   con un archivo incompleto.
 * - Comprobación de REQUEST_INSTALL_PACKAGES: en Android 8+ el usuario debe
 *   autorizar "instalar apps desconocidas" o el sistema rechaza el paquete.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val latestUrl =
        "https://api.github.com/repos/hatysquarepants0310/wowapp/releases/latest"

    /** Descargas grandes: sin timeout de lectura corto (el general es de 30 s). */
    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .callTimeout(15, TimeUnit.MINUTES)
            .build()
    }

    val canInstallPackages: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

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
                    UpdateStatus.Available(latest, release.body.take(400), apk.browser_download_url, apk.size)
                } else {
                    UpdateStatus.UpToDate
                }
            }
        }.getOrElse { UpdateStatus.Error(it.message ?: "Fallo al consultar actualizaciones") }
    }

    /** Abre los ajustes del sistema para autorizar la instalación de APKs. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Descarga el APK verificando integridad y lanza el instalador.
     * [onProgress] recibe 0..100 para que la UI muestre avance.
     */
    suspend fun downloadAndInstall(
        apkUrl: String,
        version: String,
        onProgress: (Int) -> Unit = {},
    ): UpdateStatus = withContext(Dispatchers.IO) {
        if (!canInstallPackages) {
            return@withContext UpdateStatus.NeedsInstallPermission(version, apkUrl)
        }
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val apkFile = File(dir, "azeroth-update.apk")

            downloadClient.newCall(Request.Builder().url(apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateStatus.Error("Descarga fallida: HTTP ${response.code}")
                }
                val body = response.body
                    ?: return@withContext UpdateStatus.Error("Respuesta vacía al descargar el APK.")
                val expected = body.contentLength()
                var written = 0L
                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            written += read
                            if (expected > 0) {
                                onProgress(((written * 100) / expected).toInt().coerceIn(0, 100))
                            }
                        }
                        output.flush()
                    }
                }
                // Nunca lanzar el instalador con un archivo truncado.
                if (expected > 0 && written != expected) {
                    apkFile.delete()
                    return@withContext UpdateStatus.Error(
                        "Descarga incompleta (${written / 1024} KB de ${expected / 1024} KB). Reintenta con mejor conexión.",
                    )
                }
            }

            if (apkFile.length() < 1_000_000) {
                apkFile.delete()
                return@withContext UpdateStatus.Error("El archivo descargado no es un APK válido.")
            }

            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apkFile,
            )
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                )
            }
            context.startActivity(install)
            UpdateStatus.ReadyToInstall
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
