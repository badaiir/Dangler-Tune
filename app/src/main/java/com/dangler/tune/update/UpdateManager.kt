package com.dangler.tune.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Самообновление из GitHub Releases — без Google Play.
 *
 * Релизы собираются workflow release.yml по тегу v*. Версия APK внутри релиза
 * совпадает с тегом, иначе после установки приложение снова увидит «обновление».
 *
 * Безопасность: GitHub отдаёт SHA-256 ассета в поле `digest`; скачивание сверяет
 * его, а перед установкой проверяются packageName, versionCode и подпись APK.
 * Так мы не запускаем системный установщик для битого или чужого файла.
 */
object UpdateManager {

    private const val REPO = "badaiir/Dangler-Tune"
    private const val USER_AGENT = "DanglerTune-Updater/1.0 (+https://github.com/$REPO)"
    private const val APK_MIME = "application/vnd.android.package-archive"

    data class ReleaseInfo(
        val version: String,   // tag_name, напр. "v1.13"
        val apkUrl: String,
        val name: String,
        val notes: String,
        val sizeBytes: Long = -1L,
        /** SHA-256 из GitHub asset digest; null, если GitHub его не отдал. */
        val sha256: String? = null,
    )

    /** null = релизов пока нет */
    fun checkLatest(): ReleaseInfo? {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection()
                as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode != 200) throw RuntimeException("GitHub: HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val assets = json.optJSONArray("assets") ?: return null
            val apk = findApkAsset(assets) ?: return null
            val url = apk.optString("browser_download_url")
            if (url.isBlank()) return null

            val digest = apk.optString("digest")
            val sha = digest
                .takeIf { it.startsWith("sha256:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.lowercase()
                ?.takeIf { it.length == 64 }

            return ReleaseInfo(
                version = json.optString("tag_name"),
                apkUrl = url,
                name = json.optString("name").ifBlank { json.optString("tag_name") },
                notes = json.optString("body"),
                sizeBytes = apk.optLong("size", -1L),
                sha256 = sha,
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Первый .apk, а не assets[0]: в релизе могут лежать notes/checksums/исходники. */
    private fun findApkAsset(assets: JSONArray): JSONObject? {
        var byMime: JSONObject? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) return asset
            if (byMime == null && asset.optString("content_type") == APK_MIME) byMime = asset
        }
        return byMime
    }

    fun normalize(v: String) = v.trim().removePrefix("v").removePrefix("V")

    /** Численное сравнение версий: 1.10 > 1.2 */
    fun isNewer(latest: String, current: String): Boolean {
        val l = normalize(latest).split(".").map { it.toIntOrNull() ?: 0 }
        val c = normalize(current).split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** versionCode из тега: v1.13 → 11300, v1.13.2 → 11302. */

    fun versionCodeFromTag(tag: String): Int {
        val parts = normalize(tag).split(".").map { it.toIntOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 0 }.coerceAtLeast(0)
        val minor = parts.getOrElse(1) { 0 }.coerceAtLeast(0)
        val patch = parts.getOrElse(2) { 0 }.coerceAtLeast(0)
        return major * 10_000 + minor * 100 + patch
    }

    /**
     * Скачивание в *.part с докачкой прогресса и SHA-256. Файл появляется в [dest]
     * только после успешной проверки, поэтому недокачанный APK не улетит в установщик.
     */
    fun downloadApk(
        url: String,
        dest: File,
        expectedSha256: String? = null,
        onProgress: (Float) -> Unit,
    ) {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        part.delete()

        val md = MessageDigest.getInstance("SHA-256")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (conn.responseCode !in 200..299) throw RuntimeException("Download: HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            var done = 0L
            var lastPercent = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (done == 0L) throw RuntimeException("пустой ответ")
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256 != null && !actual.equals(expectedSha256, ignoreCase = true)) {
                throw RuntimeException("SHA-256 не совпал")
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            onProgress(1f)
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Проверка скачанного APK до запуска системного установщика.
     * null = всё хорошо, иначе текст ошибки для UI.
     */
    fun verifyApk(context: Context, apk: File): String? {
        if (!apk.isFile || apk.length() == 0L) return "APK не скачан"
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return "APK битый или это не APK"
        if (archive.packageName != context.packageName) return "В релизе чужое приложение"

        val installed = try {
            pm.getPackageInfo(context.packageName, flags)
        } catch (_: Exception) {
            return "Не вижу установленное приложение"
        }

        val archiveCode = archive.longVersionCodeCompat()
        val installedCode = installed.longVersionCodeCompat()
        if (archiveCode <= installedCode) {
            return "В релизе APK не новее установленного"
        }

        val archiveSigners = archive.signerHashes()
        val installedSigners = installed.signerHashes()
        if (archiveSigners.isEmpty() || installedSigners.isEmpty()) return "Не смог проверить подпись"
        if (archiveSigners != installedSigners) {
            return "Подпись не совпадает — удали старую версию и поставь APK вручную"
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun PackageInfo.signerHashes(): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
        } else {
            signatures?.map { it.toByteArray() } ?: emptyList()
        }
        return signatures.map { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
