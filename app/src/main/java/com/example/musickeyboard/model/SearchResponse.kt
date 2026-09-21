package com.example.musickeyboard.model

// API'nizin JSON yapısına göre bunu uyarlayın
data class SearchResponse(
    val results: List<Track>?,
    val status: String?
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val downloadUrl: String?, // Yasal indirme URL'i
    val streamUrl: String?,
    val duration: Int?,
    val artwork: String?
)

data class ResolveResponse(
    val url: String, // Gerçek mp3/m4a linki
    val filename: String
)
