package com.example.musickeyboard.service

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.musickeyboard.Constants
import com.example.musickeyboard.api.MusicApiClient
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File

class MusicDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rawQuery = intent?.getStringExtra(Constants.INTENT_EXTRA_QUERY) ?: run {
            stopSelf(); return START_NOT_STICKY
        }
        val directUrl = intent.getStringExtra("direct_url")
        val query = if (!directUrl.isNullOrEmpty() && directUrl.contains("youtube.com")) directUrl else rawQuery
        val apiKey = intent.getStringExtra("api_key") ?: Constants.API_KEY

        val notif = buildNotif("İstek alındı: $rawQuery", 0, true)
        startForeground(Constants.NOTIFICATION_ID_DOWNLOAD, notif)

        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MusicDownloadService, "Aranıyor: $rawQuery", Toast.LENGTH_SHORT).show()
                }

                updateNotif("YouTube'da aranıyor: $rawQuery", 5)
                val instant = MusicApiClient.service.instantConvert(query, apiKey)
                if (!instant.ok) throw Exception("API instant hatası: $instant")

                val jobId = instant.jobId
                val title = instant.secilen?.baslik ?: rawQuery

                var status: com.example.musickeyboard.model.StatusResponse
                var attempts = 0
                do {
                    delay(3000)
                    attempts++
                    status = MusicApiClient.service.checkStatus(jobId, apiKey)
                    updateNotif("${status.mesaj ?: status.durum} - $title", status.yuzde)
                    if (status.durum == "hata") throw Exception(status.hata ?: "Dönüştürme hatası")
                    if (attempts > 80) throw Exception("Timeout: 4dk içinde bitmedi")
                } while (status.durum != "bitti" || status.dosyaUrl == null)

                // Dosya URL'sini düzgün encode et - boşluk ve özel karakterler için
                val rawFileUrl = status.dosyaUrl ?: throw Exception("Dosya URL yok")
                val fileUrl = buildEncodedFileUrl(rawFileUrl, apiKey)
                val fileName = sanitize(status.dosya ?: "$title.mp3")

                updateNotif("İndiriliyor: $fileName", 90)
                downloadToMusic(fileUrl, fileName)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MusicDownloadService, "✅ Tamamlandı: $fileName", Toast.LENGTH_LONG).show()
                }
                updateNotif("Tamamlandı: $fileName", 100)
                delay(3000)
                stopSelf()

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MusicDownloadService, "❌ Hata: ${e.message}", Toast.LENGTH_LONG).show()
                }
                updateNotif("Hata: ${e.message}", 0)
                delay(5000)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildEncodedFileUrl(rawUrl: String, apiKey: String): String {
        // rawUrl örnek: /api/v1/file/TARKAN - Kuzu Kuzu (Official).mp3
        // veya https://.../file/...
        return try {
            if (rawUrl.startsWith("http")) {
                // Zaten tam URL ise, query param ekle ve encode et
                val httpUrl = rawUrl.toHttpUrlOrNull()?.newBuilder()?.addQueryParameter("key", apiKey)?.build()
                httpUrl?.toString() ?: "$rawUrl?key=$apiKey"
            } else {
                // Relative URL ise - dosya adını encode et
                val fileName = rawUrl.substringAfterLast("/")
                val encodedFileName = Uri.encode(fileName)
                val basePath = rawUrl.substringBeforeLast("/") + "/"
                "${MusicApiClient.DOWNLOAD_BASE.trimEnd('/')}$basePath$encodedFileName?key=$apiKey"
            }
        } catch (e: Exception) {
            // Fallback: basit replace
            val encoded = rawUrl.replace(" ", "%20")
            if (encoded.startsWith("http")) "$encoded?key=$apiKey"
            else "${MusicApiClient.DOWNLOAD_BASE.trimEnd('/')}$encoded?key=$apiKey"
        }
    }

    private suspend fun downloadToMusic(fileUrl: String, fileName: String) = withContext(Dispatchers.IO) {
        // URL'yi logla
        android.util.Log.d("MusicDownload", "Downloading from: $fileUrl")

        val request = Request.Builder()
            .url(fileUrl)
            .addHeader("User-Agent", "MusicKeyboard/1.0")
            .build()

        val response = MusicApiClient.downloadClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()?.take(500)
            throw Exception("Dosya indirilemedi: ${response.code} - $errorBody")
        }

        val body = response.body ?: throw Exception("Boş body")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/${Constants.DOWNLOAD_FOLDER_NAME}")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = contentResolver.insert(collection, values) ?: throw Exception("MediaStore insert fail")

            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), Constants.DOWNLOAD_FOLDER_NAME)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName)
            outFile.outputStream().use { out ->
                body.byteStream().use { it.copyTo(out) }
            }
        }
    }

    private fun sanitize(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(Constants.CHANNEL_ID_DOWNLOAD, "Müzik İndirme", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String, progress: Int, indeterminate: Boolean = false): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_ID_DOWNLOAD)
            .setContentTitle("Music Keyboard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .build()
    }

    private fun updateNotif(text: String, progress: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Constants.NOTIFICATION_ID_DOWNLOAD, buildNotif(text, progress, progress <= 5))
    }

    override fun onBind(intent: Intent?) = null
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
