package com.example.musickeyboard.keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.musickeyboard.R
import com.example.musickeyboard.model.SearchResult

class SongAdapter(
    private var songs: List<SearchResult>,
    private val onDownloadClick: (SearchResult) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    // İndirme durumları: key = video url veya title
    private val downloadingSet = mutableSetOf<String>()
    private val downloadedSet = mutableSetOf<String>()

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvChannel: TextView = itemView.findViewById(R.id.tvChannel)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val btnDownload: TextView = itemView.findViewById(R.id.btnDownloadItem)
        val progressDownload: ProgressBar = itemView.findViewById(R.id.progressDownload)
        val tvDownloadStatus: TextView = itemView.findViewById(R.id.tvDownloadStatus)
        val tvDownloadProgress: TextView = itemView.findViewById(R.id.tvDownloadProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        val id = song.url // unique id olarak url kullan

        holder.tvTitle.text = song.baslik
        holder.tvChannel.text = "${song.kanal ?: "YouTube"} • ${formatDuration(song.sure)}"
        holder.tvDuration.text = formatDuration(song.sure)

        // Kapak yükle
        if (!song.kapak.isNullOrEmpty()) {
            Glide.with(holder.ivCover.context)
                .load(song.kapak)
                .placeholder(R.drawable.key_bg_normal)
                .error(R.drawable.key_bg_normal)
                .centerCrop()
                .into(holder.ivCover)
        }

        // Duruma göre UI güncelle - ANIMASYON
        when {
            downloadedSet.contains(id) -> {
                // İndirildi
                holder.btnDownload.visibility = View.GONE
                holder.progressDownload.visibility = View.GONE
                holder.tvDownloadStatus.visibility = View.VISIBLE
                holder.tvDownloadStatus.text = "✓"
                holder.tvDownloadStatus.setBackgroundColor(0xFF1DB954.toInt())
                holder.tvDownloadProgress.visibility = View.VISIBLE
                holder.tvDownloadProgress.text = "✅ İndi: /Music/MusicKeyboard/"
            }
            downloadingSet.contains(id) -> {
                // İniyor - ANIMASYON
                holder.btnDownload.visibility = View.INVISIBLE
                holder.progressDownload.visibility = View.VISIBLE
                holder.tvDownloadStatus.visibility = View.GONE
                holder.tvDownloadProgress.visibility = View.VISIBLE
                holder.tvDownloadProgress.text = "⏳ İndiriliyor... YouTube → MP3"
                // Kartı hafif vurgula
                holder.itemView.alpha = 0.8f
            }
            else -> {
                // Normal - indirilebilir
                holder.btnDownload.visibility = View.VISIBLE
                holder.btnDownload.text = "⬇"
                holder.progressDownload.visibility = View.GONE
                holder.tvDownloadStatus.visibility = View.GONE
                holder.tvDownloadProgress.visibility = View.GONE
                holder.itemView.alpha = 1f
            }
        }

        holder.btnDownload.setOnClickListener {
            if (!downloadingSet.contains(id) && !downloadedSet.contains(id)) {
                onDownloadClick(song)
            }
        }

        holder.itemView.setOnClickListener {
            if (!downloadingSet.contains(id) && !downloadedSet.contains(id)) {
                onDownloadClick(song)
            }
        }
    }

    override fun getItemCount() = songs.size

    fun updateList(newSongs: List<SearchResult>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    fun setDownloading(song: SearchResult) {
        val id = song.url
        downloadingSet.add(id)
        val index = songs.indexOfFirst { it.url == id }
        if (index >= 0) notifyItemChanged(index)
    }

    fun setDownloaded(song: SearchResult) {
        val id = song.url
        downloadingSet.remove(id)
        downloadedSet.add(id)
        val index = songs.indexOfFirst { it.url == id }
        if (index >= 0) notifyItemChanged(index)

        // 5 saniye sonra normale dön
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            downloadedSet.remove(id)
            val idx = songs.indexOfFirst { it.url == id }
            if (idx >= 0) notifyItemChanged(idx)
        }, 5000)
    }

    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }
}
