package com.example.musickeyboard.model

import com.google.gson.annotations.SerializedName

// Gerçek API'n: https://mp3-apisi.onrender.com

data class InstantResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("job_id") val jobId: String,
    @SerializedName("durum_url") val durumUrl: String,
    @SerializedName("format") val format: String?,
    @SerializedName("kalite") val kalite: String?,
    @SerializedName("secilen") val secilen: Secilen?,
    @SerializedName("not_") val not: String?
)

data class Secilen(
    @SerializedName("baslik") val baslik: String,
    @SerializedName("kaynak") val kaynak: String,
    @SerializedName("sure") val sure: Int
)

data class StatusResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("durum") val durum: String, // "indiriliyor", "bitti", "hata"
    @SerializedName("yuzde") val yuzde: Int,
    @SerializedName("mesaj") val mesaj: String?,
    @SerializedName("baslik") val baslik: String?,
    @SerializedName("dosya") val dosya: String?,
    @SerializedName("dosya_url") val dosyaUrl: String?,
    @SerializedName("hata") val hata: String?
)

data class SearchApiResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("q") val q: String,
    @SerializedName("adet") val adet: Int,
    @SerializedName("sonuclar") val sonuclar: List<SearchResult>?
)

data class SearchResult(
    @SerializedName("baslik") val baslik: String,
    @SerializedName("kanal") val kanal: String?,
    @SerializedName("sure") val sure: Int,
    @SerializedName("url") val url: String,
    @SerializedName("kapak") val kapak: String?,
    @SerializedName("id") val id: Int
)
