package com.example.musickeyboard.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.musickeyboard.Constants
import com.example.musickeyboard.R
import com.example.musickeyboard.api.MusicApiClient
import com.example.musickeyboard.service.MusicDownloadService
import kotlinx.coroutines.*

class MyCustomKeyboard : InputMethodService() {

    private var keyboardView: View? = null
    private var etSearch: EditText? = null
    private var rvSongs: RecyclerView? = null
    private var songAdapter: SongAdapter? = null
    private var isCaps = false
    private var isSearchFocused = false
    private var isMusicMode = false
    private val apiKey = "sk-71c69f4de1f4b912957fed45"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val row1Keys = listOf("q","w","e","r","t","y","u","ı","o","p")
    private val row2Keys = listOf("a","s","d","f","g","h","j","k","l")
    private val row3Keys = listOf("SHIFT","z","x","c","v","b","n","m","DEL")
    private val row4Keys = listOf("?123",",","SPACE",".","ENTER")

    // Türkçe özel karakterler - uzun basma için
    private val altCharsMap = mapOf(
        "a" to listOf("a","â","á","à","ä","ã"),
        "e" to listOf("e","é","è","ê","ë"),
        "i" to listOf("i","ı","î","ï","í"),
        "o" to listOf("o","ö","ô","ó","ò"),
        "u" to listOf("u","ü","û","ú","ù"),
        "s" to listOf("s","ş","ß"),
        "g" to listOf("g","ğ"),
        "c" to listOf("c","ç"),
        "q" to listOf("q","ğ"),
        "n" to listOf("n","ñ")
    )

    override fun onCreateInputView(): View {
        Log.d("MusicKeyboard", "onCreateInputView Gboard+Music mode")
        try {
            keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)

            // Views
            etSearch = keyboardView!!.findViewById(R.id.etSearch)
            rvSongs = keyboardView!!.findViewById(R.id.rvSongs)
            val btnDownload = keyboardView!!.findViewById<TextView>(R.id.btnDownload)
            val btnToggleMusic = keyboardView!!.findViewById<TextView>(R.id.btnToggleMusicMode)
            val btnSearchAction = keyboardView!!.findViewById<TextView>(R.id.btnSearchAction)
            val btnClear = keyboardView!!.findViewById<TextView>(R.id.btnClearSearch)
            val btnBackToKeyboard = keyboardView!!.findViewById<TextView>(R.id.btnBackToKeyboard)
            val tvStatus = keyboardView!!.findViewById<TextView>(R.id.tvSearchStatus)

            val row1 = keyboardView!!.findViewById<LinearLayout>(R.id.row1)
            val row2 = keyboardView!!.findViewById<LinearLayout>(R.id.row2)
            val row3 = keyboardView!!.findViewById<LinearLayout>(R.id.row3)
            val row4 = keyboardView!!.findViewById<LinearLayout>(R.id.row4)

            val searchBarContainer = keyboardView!!.findViewById<LinearLayout>(R.id.searchBarContainer)
            val keyboardTopBar = keyboardView!!.findViewById<LinearLayout>(R.id.keyboardTopBar)
            val musicContainer = keyboardView!!.findViewById<LinearLayout>(R.id.musicModeContainer)
            val keysContainer = keyboardView!!.findViewById<LinearLayout>(R.id.keysContainer)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                etSearch?.showSoftInputOnFocus = false
            }

            // RecyclerView setup
            rvSongs?.layoutManager = LinearLayoutManager(this)
            songAdapter = SongAdapter(emptyList()) { song ->
                // Şarkı kartının solundaki butona basıldı -> indir
                startDownloadService(song.baslik, song.url)
            }
            rvSongs?.adapter = songAdapter

            // Search focus
            etSearch?.setOnFocusChangeListener { _, hasFocus ->
                isSearchFocused = hasFocus
                updateClearButton()
            }
            etSearch?.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateClearButton() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            etSearch?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    performSearch()
                    true
                } else false
            }

            btnClear?.setOnClickListener { etSearch?.text?.clear() }
            btnSearchAction?.setOnClickListener { performSearch() }

            // Mod değiştirme butonu - sağdaki güzel icon
            btnToggleMusic?.setOnClickListener {
                toggleMusicMode()
            }

            btnBackToKeyboard?.setOnClickListener {
                toggleMusicMode() // Geri dön
            }

            btnDownload?.setOnClickListener {
                val query = etSearch?.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    startDownloadService(query)
                }
            }

            // Klavye satırları
            createRow(row1, row1Keys)
            createRow(row2, row2Keys)
            createRow(row3, row3Keys)
            createRow(row4, row4Keys)

            // Başlangıçta klavye modunda
            setMusicMode(false)

            return keyboardView!!
        } catch (e: Exception) {
            Log.e("MusicKeyboard", "Error", e)
            return TextView(this).apply { text = "HATA: ${e.message}" }
        }
    }

    private fun toggleMusicMode() {
        isMusicMode = !isMusicMode
        setMusicMode(isMusicMode)
        Log.d("MusicKeyboard", "Music mode: $isMusicMode")
    }

    private fun setMusicMode(enabled: Boolean) {
        val searchBarContainer = keyboardView?.findViewById<LinearLayout>(R.id.searchBarContainer)
        val keyboardTopBar = keyboardView?.findViewById<LinearLayout>(R.id.keyboardTopBar)
        val musicContainer = keyboardView?.findViewById<LinearLayout>(R.id.musicModeContainer)
        val keysContainer = keyboardView?.findViewById<LinearLayout>(R.id.keysContainer)
        val btnToggle = keyboardView?.findViewById<TextView>(R.id.btnToggleMusicMode)

        if (enabled) {
            // Müzik moduna geç
            searchBarContainer?.visibility = View.VISIBLE
            keyboardTopBar?.visibility = View.GONE
            musicContainer?.visibility = View.VISIBLE
            keysContainer?.visibility = View.VISIBLE // Klavye de kalsın arama için
            btnToggle?.text = "⌨"
            btnToggle?.setBackgroundResource(R.drawable.key_bg_enter)
            etSearch?.post {
                etSearch?.requestFocus()
                isSearchFocused = true
            }
        } else {
            // Klavye moduna dön
            searchBarContainer?.visibility = View.GONE
            keyboardTopBar?.visibility = View.VISIBLE
            musicContainer?.visibility = View.GONE
            keysContainer?.visibility = View.VISIBLE
            btnToggle?.text = "🎵"
            btnToggle?.setBackgroundResource(R.drawable.key_bg_normal)
            isSearchFocused = false
        }
    }

    private fun performSearch() {
        val query = etSearch?.text?.toString()?.trim()
        if (query.isNullOrEmpty()) {
            Toast.makeText(this, "Şarkı adı gir", Toast.LENGTH_SHORT).show()
            return
        }

        val tvStatus = keyboardView?.findViewById<TextView>(R.id.tvSearchStatus)
        tvStatus?.text = "🔍 Aranıyor: $query..."

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    MusicApiClient.service.search(query, apiKey)
                }
                if (result.ok && !result.sonuclar.isNullOrEmpty()) {
                    songAdapter?.updateList(result.sonuclar)
                    tvStatus?.text = "✅ ${result.sonuclar.size} sonuç bulundu - kaydır ve indir"
                    Toast.makeText(this@MyCustomKeyboard, "${result.sonuclar.size} şarkı bulundu", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatus?.text = "❌ Sonuç bulunamadı"
                    songAdapter?.updateList(emptyList())
                }
            } catch (e: Exception) {
                Log.e("MusicKeyboard", "Search error", e)
                tvStatus?.text = "❌ Hata: ${e.message}"
                Toast.makeText(this@MyCustomKeyboard, "Arama hatası: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateClearButton() {
        val clearBtn = keyboardView?.findViewById<TextView>(R.id.btnClearSearch)
        clearBtn?.visibility = if (etSearch?.text?.isNotEmpty() == true) View.VISIBLE else View.GONE
    }

    private fun createRow(container: LinearLayout, keys: List<String>) {
        container.removeAllViews()
        for (key in keys) {
            val keyView = createKeyView(key)
            val params = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
                if (key == "SPACE") weight = 4f
                if (key == "SHIFT" || key == "DEL" || key == "?123" || key == "ENTER") weight = 1.3f
            }
            container.addView(keyView, params)
        }
    }

    private fun createKeyView(key: String): TextView {
        return TextView(this).apply {
            text = when(key) {
                "SPACE" -> "Boşluk"
                "DEL" -> "⌫"
                "ENTER" -> "↵"
                "SHIFT" -> if(isCaps) "⇧" else "⇧"
                "?123" -> "?123"
                "," -> ","
                "." -> "."
                else -> if(isCaps) key.uppercase() else key
            }
            textSize = when(key) {
                "SPACE" -> 13f
                "DEL", "ENTER", "SHIFT" -> 16f
                "?123" -> 11f
                else -> 15f
            }
            gravity = Gravity.CENTER
            setTextColor(if(key=="ENTER") 0xFF202124.toInt() else 0xFFE8EAED.toInt())
            background = when(key) {
                "SPACE" -> ContextCompat.getDrawable(context, R.drawable.key_bg_space)
                "ENTER" -> ContextCompat.getDrawable(context, R.drawable.key_bg_enter)
                "DEL", "SHIFT", "?123", ",", "." -> ContextCompat.getDrawable(context, R.drawable.key_bg_action)
                else -> ContextCompat.getDrawable(context, R.drawable.key_bg_normal)
            }
            if (key == "SHIFT" && isCaps) {
                setBackgroundColor(0xFF8AB4F8.toInt())
                setTextColor(0xFF202124.toInt())
            }
            setOnClickListener { onKeyPressed(key) }

            // Uzun basma - özel karakterler
            setOnLongClickListener {
                showAltCharsPopup(this, key)
                true
            }
        }
    }

    private fun showAltCharsPopup(anchor: View, baseKey: String) {
        val alts = altCharsMap[baseKey.lowercase()] ?: return

        // PopupWindow ile özel karakterleri göster
        val popupView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.search_bg)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        for (alt in alts) {
            val tv = TextView(this).apply {
                text = alt
                textSize = 18f
                setTextColor(0xFFE8EAED.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = ContextCompat.getDrawable(context, R.drawable.key_bg_normal)
                setOnClickListener {
                    if (isSearchFocused && etSearch != null) {
                        etSearch!!.text.append(alt)
                    } else {
                        currentInputConnection?.commitText(alt, 1)
                    }
                    popupWindow?.dismiss()
                }
            }
            val params = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                setMargins(dp(2),0,dp(2),0)
            }
            popupView.addView(tv, params)
        }

        val popupWindow = android.widget.PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 8f
        popupWindow.showAsDropDown(anchor, 0, -dp(60))

        // 2 saniye sonra otomatik kapan
        anchor.postDelayed({ popupWindow.dismiss() }, 2500)
    }

    private var popupWindow: android.widget.PopupWindow? = null

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun startDownloadService(query: String, directUrl: String? = null) {
        val intent = Intent(this, MusicDownloadService::class.java).apply {
            putExtra(Constants.INTENT_EXTRA_QUERY, query)
            putExtra("api_key", apiKey)
            if (directUrl != null) putExtra("direct_url", directUrl)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else startService(intent)
            Toast.makeText(this, "\"$query\" indiriliyor...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun onKeyPressed(key: String) {
        Log.d("MusicKeyboard", "Key: $key, searchFocused: $isSearchFocused, musicMode: $isMusicMode")

        // Müzik modunda ve arama odaklıysa arama kutusuna yaz
        if (isMusicMode && isSearchFocused && etSearch != null) {
            when (key) {
                "DEL" -> {
                    val text = etSearch!!.text
                    if (text.isNotEmpty()) text.delete(text.length - 1, text.length)
                }
                "SPACE" -> etSearch!!.text.append(" ")
                "ENTER" -> performSearch()
                "SHIFT" -> {
                    isCaps = !isCaps
                    refreshAllRows()
                }
                "?123" -> {}
                "," -> etSearch!!.text.append(",")
                "." -> etSearch!!.text.append(".")
                else -> {
                    val c = if(isCaps) key.uppercase() else key
                    etSearch!!.text.append(c)
                }
            }
            updateClearButton()
            return
        }

        // Normal mod - ana uygulamaya yaz
        val ic = currentInputConnection
        if (ic == null) {
            if (key.length == 1) {
                etSearch?.text?.append(if(isCaps) key.uppercase() else key)
                isSearchFocused = true
                if (!isMusicMode) toggleMusicMode()
            }
            return
        }

        when (key) {
            "DEL" -> ic.deleteSurroundingText(1, 0)
            "SPACE" -> ic.commitText(" ", 1)
            "ENTER" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
            }
            "SHIFT" -> {
                isCaps = !isCaps
                refreshAllRows()
            }
            "?123" -> {}
            "," -> ic.commitText(",", 1)
            "." -> ic.commitText(".", 1)
            else -> {
                val c = if(isCaps) key.uppercase() else key
                ic.commitText(c, 1)
            }
        }
    }

    private fun refreshAllRows() {
        keyboardView?.let { view ->
            val row1 = view.findViewById<LinearLayout>(R.id.row1)
            val row2 = view.findViewById<LinearLayout>(R.id.row2)
            val row3 = view.findViewById<LinearLayout>(R.id.row3)
            val row4 = view.findViewById<LinearLayout>(R.id.row4)
            createRow(row1, row1Keys)
            createRow(row2, row2Keys)
            createRow(row3, row3Keys)
            createRow(row4, row4Keys)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        keyboardView = null
    }
}
