package com.example.musickeyboard.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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

    // Genişletilmiş Türkçe + tüm harfler için özel karakterler
    private val altCharsMap = mapOf(
        "a" to listOf("a","á","à","â","ä","ã","å","æ"),
        "e" to listOf("e","é","è","ê","ë","ē"),
        "i" to listOf("i","í","ì","î","ï","ī"),
        "ı" to listOf("ı","i","î","í"),
        "o" to listOf("o","ó","ò","ô","ö","õ","ø"),
        "ö" to listOf("ö","o","ó","ô"),
        "u" to listOf("u","ú","ù","û","ü","ū"),
        "ü" to listOf("ü","u","ú","û"),
        "s" to listOf("s","ş","ß","ś"),
        "ş" to listOf("ş","s","ß"),
        "g" to listOf("g","ğ","ǧ"),
        "ğ" to listOf("ğ","g"),
        "c" to listOf("c","ç","ć","ĉ"),
        "ç" to listOf("ç","c","ć"),
        "q" to listOf("q","ğ"),
        "n" to listOf("n","ñ","ń"),
        "z" to listOf("z","ž","ź","ż"),
        "y" to listOf("y","ý","ÿ"),
        "k" to listOf("k","ķ"),
        "l" to listOf("l","ł","ļ"),
        "r" to listOf("r","ř"),
        "d" to listOf("d","ð"),
        "t" to listOf("t","ť","ţ"),
        "b" to listOf("b","ß"),
        "m" to listOf("m","μ"),
        "p" to listOf("p","þ"),
        "h" to listOf("h","ħ"),
        "j" to listOf("j","ĵ"),
        "f" to listOf("f","ƒ"),
        "v" to listOf("v","w"),
        "w" to listOf("w","v"),
        "x" to listOf("x","×"),
        "," to listOf(",", ";", ":", "!", "?", "'", "\""),
        "." to listOf(".", "…", "·"),
        "?" to listOf("?","¿","!","¡")
    )

    private var currentPopup: PopupWindow? = null
    private var currentPopupAlts: List<String> = emptyList()
    private var selectedAltIndex = -1
    private var popupAnchorView: View? = null

    override fun onCreateInputView(): View {
        Log.d("MusicKeyboard", "onCreateInputView Gboard+Music v2 - fixed popup")
        try {
            keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)

            etSearch = keyboardView!!.findViewById(R.id.etSearch)
            rvSongs = keyboardView!!.findViewById(R.id.rvSongs)
            val btnToggleMusic = keyboardView!!.findViewById<TextView>(R.id.btnToggleMusicMode)
            val btnSearchAction = keyboardView!!.findViewById<TextView>(R.id.btnSearchAction)
            val btnClear = keyboardView!!.findViewById<TextView>(R.id.btnClearSearch)
            val btnBackToKeyboard = keyboardView!!.findViewById<TextView>(R.id.btnBackToKeyboard)

            val row1 = keyboardView!!.findViewById<LinearLayout>(R.id.row1)
            val row2 = keyboardView!!.findViewById<LinearLayout>(R.id.row2)
            val row3 = keyboardView!!.findViewById<LinearLayout>(R.id.row3)
            val row4 = keyboardView!!.findViewById<LinearLayout>(R.id.row4)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                etSearch?.showSoftInputOnFocus = false
            }

            rvSongs?.layoutManager = LinearLayoutManager(this)
            songAdapter = SongAdapter(emptyList()) { song ->
                // Kartın solundaki butona basıldı -> indirme animasyonu + servis
                songAdapter?.setDownloading(song)
                startDownloadService(song.baslik, song.url)
                // 3 saniye sonra tamamlandı göster
                scope.launch {
                    delay(3000)
                    songAdapter?.setDownloaded(song)
                }
            }
            rvSongs?.adapter = songAdapter

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
            btnToggleMusic?.setOnClickListener { toggleMusicMode() }
            btnBackToKeyboard?.setOnClickListener { toggleMusicMode() }

            createRow(row1, row1Keys)
            createRow(row2, row2Keys)
            createRow(row3, row3Keys)
            createRow(row4, row4Keys)

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
    }

    private fun setMusicMode(enabled: Boolean) {
        val searchBarContainer = keyboardView?.findViewById<LinearLayout>(R.id.searchBarContainer)
        val keyboardTopBar = keyboardView?.findViewById<LinearLayout>(R.id.keyboardTopBar)
        val musicContainer = keyboardView?.findViewById<LinearLayout>(R.id.musicModeContainer)
        val keysContainer = keyboardView?.findViewById<LinearLayout>(R.id.keysContainer)
        val btnToggle = keyboardView?.findViewById<TextView>(R.id.btnToggleMusicMode)

        if (enabled) {
            searchBarContainer?.visibility = View.VISIBLE
            keyboardTopBar?.visibility = View.GONE
            musicContainer?.visibility = View.VISIBLE
            keysContainer?.visibility = View.VISIBLE
            btnToggle?.text = "⌨"
            btnToggle?.setBackgroundResource(R.drawable.key_bg_enter)
            etSearch?.post {
                etSearch?.requestFocus()
                isSearchFocused = true
            }
        } else {
            searchBarContainer?.visibility = View.GONE
            keyboardTopBar?.visibility = View.VISIBLE
            musicContainer?.visibility = View.GONE
            keysContainer?.visibility = View.VISIBLE
            btnToggle?.text = "🎵"
            btnToggle?.setBackgroundResource(R.drawable.key_bg_normal)
            isSearchFocused = false
            dismissAltPopup()
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
                    tvStatus?.text = "✅ ${result.sonuclar.size} sonuç - kaydır, ⬇ ile indir"
                } else {
                    tvStatus?.text = "❌ Sonuç bulunamadı"
                    songAdapter?.updateList(emptyList())
                }
            } catch (e: Exception) {
                Log.e("MusicKeyboard", "Search error", e)
                tvStatus?.text = "❌ Hata: ${e.message}"
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
                "SHIFT" -> "⇧"
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

            // Gboard gibi hızlı yazma - basılı tut + kaydır
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Uzun basma için handler başlat
                        v.postDelayed({
                            if (v.isPressed) {
                                showAltCharsPopupGboard(v, key)
                            }
                        }, 400) // 400ms sonra popup göster
                        v.isPressed = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Popup açıksa, kaydırarak seçim yap
                        if (currentPopup != null && currentPopup?.isShowing == true) {
                            handlePopupMove(event)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        v.removeCallbacks(null)
                        if (currentPopup != null && currentPopup?.isShowing == true) {
                            // Seçili alt karakteri yaz
                            if (selectedAltIndex >= 0 && selectedAltIndex < currentPopupAlts.size) {
                                val selectedChar = currentPopupAlts[selectedAltIndex]
                                commitAltChar(selectedChar)
                            }
                            dismissAltPopup()
                            // Up event'i tüket ki normal click çalışmasın
                            if (selectedAltIndex >= 0) {
                                return@setOnTouchListener true
                            }
                        }
                    }
                }
                false // Normal click de çalışsın
            }

            setOnClickListener { onKeyPressed(key) }
        }
    }

    private fun handlePopupMove(event: MotionEvent) {
        // Popup içindeki hangi karakterin üstünde olduğunu bul
        val popupView = currentPopup?.contentView as? LinearLayout ?: return
        val x = event.rawX

        // Basit hesaplama - popup içindeki çocukların pozisyonuna göre
        for (i in 0 until popupView.childCount) {
            val child = popupView.getChildAt(i)
            val location = IntArray(2)
            child.getLocationOnScreen(location)
            val childLeft = location[0]
            val childRight = childLeft + child.width

            if (x >= childLeft && x <= childRight) {
                if (selectedAltIndex != i) {
                    // Önceki seçimi temizle
                    if (selectedAltIndex >= 0 && selectedAltIndex < popupView.childCount) {
                        popupView.getChildAt(selectedAltIndex).setBackgroundResource(R.drawable.key_bg_normal)
                    }
                    // Yeni seçimi vurgula
                    child.setBackgroundResource(R.drawable.key_bg_enter)
                    selectedAltIndex = i
                    // Haptic feedback
                    child.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
                break
            }
        }
    }

    private fun showAltCharsPopupGboard(anchor: View, baseKey: String) {
        val alts = altCharsMap[baseKey.lowercase()] ?: altCharsMap[baseKey] ?: return
        if (alts.isEmpty()) return

        dismissAltPopup() // Önceki popup varsa kapat

        currentPopupAlts = alts
        selectedAltIndex = 0 // İlk karakter seçili başlasın
        popupAnchorView = anchor

        val popupView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.search_bg)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            elevation = 12f
        }

        for ((index, alt) in alts.withIndex()) {
            val tv = TextView(this).apply {
                text = alt
                textSize = 18f
                setTextColor(0xFFE8EAED.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = if (index == 0) ContextCompat.getDrawable(context, R.drawable.key_bg_enter) else ContextCompat.getDrawable(context, R.drawable.key_bg_normal)
                // Dokunma ile seçim için
                setOnTouchListener { _, ev ->
                    if (ev.action == MotionEvent.ACTION_UP) {
                        commitAltChar(alt)
                        dismissAltPopup()
                    }
                    true
                }
            }
            val params = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                setMargins(dp(3),0,dp(3),0)
            }
            popupView.addView(tv, params)
        }

        // PopupWindow - KLAVYE KAPANMASIN DİYE focusable=false ve INPUT_METHOD_NOT_NEEDED
        val popup = android.widget.PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false // focusable false -> klavye kapanmaz
        )
        popup.inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
        popup.isClippingEnabled = true
        popup.elevation = 12f
        // Dışarı dokununca kapanmasın, biz kontrol edeceğiz
        popup.isOutsideTouchable = false

        try {
            // Anchor'ın üstünde göster
            popup.showAsDropDown(anchor, 0, -dp(70))
            currentPopup = popup

            // Haptic feedback
            anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

            // 4 saniye sonra otomatik kapan
            anchor.postDelayed({
                dismissAltPopup()
            }, 4000)

        } catch (e: Exception) {
            Log.e("MusicKeyboard", "Popup show error", e)
        }
    }

    private fun commitAltChar(alt: String) {
        if (isSearchFocused && etSearch != null) {
            etSearch!!.text.append(alt)
        } else {
            currentInputConnection?.commitText(alt, 1)
        }
        // Haptic
        keyboardView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun dismissAltPopup() {
        try {
            currentPopup?.dismiss()
        } catch (e: Exception) {}
        currentPopup = null
        selectedAltIndex = -1
        currentPopupAlts = emptyList()
    }

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
        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun onKeyPressed(key: String) {
        // Eğer popup açıksa normal tuşa basmayı engelle
        if (currentPopup?.isShowing == true) {
            dismissAltPopup()
            return
        }

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

        val ic = currentInputConnection
        if (ic == null) {
            if (key.length == 1) {
                etSearch?.text?.append(if(isCaps) key.uppercase() else key)
                isSearchFocused = true
                if (!isMusicMode) setMusicMode(true)
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
        dismissAltPopup()
        scope.cancel()
        keyboardView = null
    }
}
