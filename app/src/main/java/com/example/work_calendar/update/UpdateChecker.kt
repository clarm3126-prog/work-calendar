package com.example.work_calendar.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class GhAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0,
)

@Serializable
private data class GhRelease(
    val tag_name: String = "",
    val name: String = "",
    val html_url: String = "",
    val assets: List<GhAsset> = emptyList(),
)

data class UpdateInfo(
    val latestVersionCode: Int,
    val releaseName: String,
    val apkUrl: String,
    val apkSize: Long,
)

object UpdateChecker {
    private const val API_URL =
        "https://api.github.com/repos/clarm3126-prog/work-calendar/releases/latest"
    private const val APK_FILENAME = "work-calendar.apk"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLatestUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching { request(currentVersionCode) }
            .onFailure { Log.w("UpdateChecker", "fetch failed", it) }
            .getOrNull()
    }

    private fun request(currentVersionCode: Int): UpdateInfo? {
        val conn = URL(API_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "work-calendar-app")
        try {
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val release = json.decodeFromString<GhRelease>(body)
            val latest = release.tag_name.removePrefix("build-").toIntOrNull() ?: return null
            if (latest <= currentVersionCode) return null
            val asset = release.assets.firstOrNull { it.name == APK_FILENAME } ?: return null
            return UpdateInfo(
                latestVersionCode = latest,
                releaseName = release.name.ifBlank { release.tag_name },
                apkUrl = asset.browser_download_url,
                apkSize = asset.size,
            )
        } finally {
            conn.disconnect()
        }
    }
}
