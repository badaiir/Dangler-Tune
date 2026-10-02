package com.dangler.tune.update

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Самообновление из GitHub Releases — без Google Play.
 * Релизы собираются workflow release.yml по тегу v* (внутри debug-APK).
 */
object UpdateManager {

    private const val REPO = "badaiir/Dangler-Tune"

    data class ReleaseInfo(
        val version: String,   // tag_name, напр. "v1.2"
        val apkUrl: String,
        val name: String,
        val notes: String,
    )

    /** null = релизов пока нет */
    fun checkLatest(): ReleaseInfo? {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection()
                as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode != 200) throw RuntimeException("GitHub: HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val assets = json.optJSONArray("assets") ?: return null
            if (assets.length() == 0) return null
            val apk = assets.getJSONObject(0)
            val url = apk.optString("browser_download_url")
            if (url.isBlank()) return null
            return ReleaseInfo(
                version = json.optString("tag_name"),
                apkUrl = url,
                name = json.optString("name"),
                notes = json.optString("body"),
            )
        } finally {
            conn.disconnect()
        }
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

    fun downloadApk(url: String, dest: File, onProgress: (Float) -> Unit) {
        if (dest.exists()) dest.delete()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (conn.responseCode !in 200..299) throw RuntimeException("Download: HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            var done = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
        } finally {
            conn.disconnect()
        }
    }
}
