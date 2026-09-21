package com.example.musickeyboard.keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.musickeyboard.R
import com.example.musickeyboard.model.SearchResult

class SongAdapter(
    private var songs: List<SearchResult>,
    private val onDownloadClick: (SearchResult) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvChannel: TextView = itemView.findViewById(R.id.tvChannel)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val btnDownload: TextView = itemView.findViewById(R.id.btnDownloadItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
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

        holder.btnDownload.setOnClickListener {
            onDownloadClick(song)
        }

        holder.itemView.setOnClickListener {
            onDownloadClick(song)
        }
    }

    override fun getItemCount() = songs.size

    fun updateList(newSongs: List<SearchResult>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }
}
