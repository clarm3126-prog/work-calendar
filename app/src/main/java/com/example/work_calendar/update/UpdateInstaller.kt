package com.example.work_calendar.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadEvent {
    data class Progress(val downloaded: Long, val total: Long) : DownloadEvent
    data class Complete(val file: File) : DownloadEvent
}

object UpdateInstaller {

    private const val APK_FILE = "work-calendar-update.apk"
    private const val FILE_PROVIDER_AUTHORITY = "com.example.work_calendar.fileprovider"

    fun download(app: Application, url: String): Flow<DownloadEvent> = flow {
        val out = File(app.cacheDir, APK_FILE)
        if (out.exists()) out.delete()

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            conn.connect()
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            conn.inputStream.use { input ->
                FileOutputStream(out).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastEmit = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        // 진행률 이벤트는 너무 자주 보내지 않도록 256KB마다
                        if (downloaded - lastEmit >= 256 * 1024 || downloaded == total) {
                            emit(DownloadEvent.Progress(downloaded, total))
                            lastEmit = downloaded
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        emit(DownloadEvent.Complete(out))
    }.flowOn(Dispatchers.IO)

    fun launchInstall(app: Application, file: File) {
        val uri: Uri = FileProvider.getUriForFile(app, FILE_PROVIDER_AUTHORITY, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(intent)
    }
}
