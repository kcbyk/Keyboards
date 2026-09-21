package com.example.musickeyboard

object Constants {
    // BuildConfig + Secrets
    var API_KEY = BuildConfig.MUSIC_API_KEY // default, intent ile override edilebilir
    const val BASE_URL = "https://mp3-apisi.onrender.com/"

    const val DOWNLOAD_FOLDER_NAME = "MusicKeyboard"
    const val CHANNEL_ID_DOWNLOAD = "music_download_channel"
    const val NOTIFICATION_ID_DOWNLOAD = 1001
    const val INTENT_EXTRA_QUERY = "extra_song_query"
}
