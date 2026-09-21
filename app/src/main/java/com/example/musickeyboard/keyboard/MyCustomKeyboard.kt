package com.example.musickeyboard.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())

    private val row1Keys = listOf("q","w","e","r","t","y","u","ı","o","p")
    private val row2Keys = listOf("a","s","d","f","g","h","j","k","l")
    private val row3Keys = listOf("SHIFT","z","x","c","v","b","n","m","DEL")
    private val row4Keys = listOf("?123",",","SPACE",".","ENTER")

    private val altCharsMap = mapOf(
        "a" to listOf("a","á","à","â","ä","ã","å","æ","ā"),
        "e" to listOf("e","é","è","ê","ë","ē","ė"),
        "i" to listOf("i","í","ì","î","ï","ī","ı"),
        "ı" to listOf("ı","i","î","í","ï"),
        "o" to listOf("o","ó","ò","ô","ö","õ","ø","ō"),
        "ö" to listOf("ö","o","ó","ô","õ"),
        "u" to listOf("u","ú","ù","û","ü","ū"),
        "ü" to listOf("ü","u","ú","û","ū"),
        "s" to listOf("s","ş","ß","ś","š"),
        "ş" to listOf("ş","s","ß","ś"),
        "g" to listOf("g","ğ","ǧ","ģ"),
        "ğ" to listOf("ğ","g","ǧ"),
        "c" to listOf("c","ç","ć","ĉ","ċ"),
        "ç" to listOf("ç","c","ć","ĉ"),
        "q" to listOf("q","ğ"),
        "n" to listOf("n","ñ","ń","ň"),
        "z" to listOf("z","ž","ź","ż"),
        "y" to listOf("y","ý","ÿ","ŷ"),
        "k" to listOf("k","ķ","ƙ"),
        "l" to listOf("l","ł","ļ","ľ"),
        "r" to listOf("r","ř","ŗ"),
        "d" to listOf("d","ð","ď","đ"),
        "t" to listOf("t","ť","ţ","ț"),
        "," to listOf(",", ";", ":", "!", "?", "'", "\"", "-", "_"),
        "." to listOf(".", "…", "·", "•"),
        "?" to listOf("?","¿","!","¡","!"),
        "!" to listOf("!","¡","?")
    )

    private var currentPopup: android.widget.PopupWindow? = null
    private var currentPopupAlts: List<String> = emptyList()
    private var selectedAltIndex = -1
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false

    override fun onCreateInputView(): View {
        Log.d("MusicKeyboard", "onCreateInputView Gboard v3 - fixed typing + download")
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
                songAdapter?.setDownloading(song)
                startDownloadService(song.baslik, song.url)
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

            // Gboard gibi: normal basma + uzun basma + kaydırma seçimi
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPressTriggered = false
                        v.isPressed = true
                        // 400ms sonra uzun basma popup göster
                        longPressRunnable = Runnable {
                            if (v.isPressed) {
                                isLongPressTriggered = true
                                showAltCharsPopupGboard(v, key)
                                v.isPressed = false
                            }
                        }
                        handler.postDelayed(longPressRunnable!!, 400)
                        true // touch'u biz handle ediyoruz
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (currentPopup?.isShowing == true) {
                            handlePopupMove(event)
                            true
                        } else {
                            // Eğer parmak çok kaydıysa long press'i iptal et
                            if (Math.abs(event.x) > dp(20) || Math.abs(event.y) > dp(20)) {
                                handler.removeCallbacks(longPressRunnable!!)
                                v.isPressed = false
                            }
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPressRunnable!!)
                        v.isPressed = false

                        if (currentPopup?.isShowing == true) {
                            // Popup açıksa - seçili karakteri yaz
                            if (selectedAltIndex >= 0 && selectedAltIndex < currentPopupAlts.size) {
                                val selectedChar = currentPopupAlts[selectedAltIndex]
                                commitAltChar(selectedChar)
                            } else {
                                // Hiç seçim yoksa base karakteri yazma, sadece popup kapat
                            }
                            dismissAltPopup()
                            true // event tüketildi
                        } else {
                            // Popup yoksa - normal kısa basma
                            if (!isLongPressTriggered) {
                                onKeyPressed(key)
                            }
                            isLongPressTriggered = false
                            true
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable!!)
                        v.isPressed = false
                        dismissAltPopup()
                        isLongPressTriggered = false
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun handlePopupMove(event: MotionEvent) {
        val popupView = currentPopup?.contentView as? LinearLayout ?: return
        val x = event.rawX

        for (i in 0 until popupView.childCount) {
            val child = popupView.getChildAt(i)
            val location = IntArray(2)
            child.getLocationOnScreen(location)
            val childLeft = location[0]
            val childRight = childLeft + child.width

            if (x >= childLeft && x <= childRight) {
                if (selectedAltIndex != i) {
                    if (selectedAltIndex >= 0 && selectedAltIndex < popupView.childCount) {
                        popupView.getChildAt(selectedAltIndex).setBackgroundResource(R.drawable.key_bg_normal)
                    }
                    child.setBackgroundResource(R.drawable.key_bg_enter)
                    selectedAltIndex = i
                    child.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
                break
            }
        }
    }

    private fun showAltCharsPopupGboard(anchor: View, baseKey: String) {
        val keyLower = baseKey.lowercase()
        val alts = altCharsMap[keyLower] ?: altCharsMap[baseKey] ?: listOf(baseKey)
        if (alts.isEmpty()) return

        dismissAltPopup()

        currentPopupAlts = alts
        selectedAltIndex = 0

        val popupView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.search_bg)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            elevation = 16f
        }

        for ((index, alt) in alts.withIndex()) {
            val tv = TextView(this).apply {
                text = alt
                textSize = 18f
                setTextColor(0xFFE8EAED.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = if (index == 0) ContextCompat.getDrawable(context, R.drawable.key_bg_enter) else ContextCompat.getDrawable(context, R.drawable.key_bg_normal)
                setOnClickListener {
                    commitAltChar(alt)
                    dismissAltPopup()
                }
            }
            val params = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                setMargins(dp(3),0,dp(3),0)
            }
            popupView.addView(tv, params)
        }

        val popup = android.widget.PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        )
        popup.inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
        popup.isClippingEnabled = true
        popup.elevation = 16f
        popup.isOutsideTouchable = true

        try {
            popup.showAsDropDown(anchor, 0, -dp(70))
            currentPopup = popup
            anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

            anchor.postDelayed({ dismissAltPopup() }, 4000)

        } catch (e: Exception) {
            Log.e("MusicKeyboard", "Popup error", e)
        }
    }

    private fun commitAltChar(alt: String) {
        if (isSearchFocused && etSearch != null) {
            etSearch!!.text.append(alt)
        } else {
            currentInputConnection?.commitText(alt, 1)
        }
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
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        keyboardView = null
    }
}
