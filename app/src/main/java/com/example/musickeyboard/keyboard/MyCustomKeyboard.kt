package com.example.musickeyboard.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.musickeyboard.Constants
import com.example.musickeyboard.R
import com.example.musickeyboard.service.MusicDownloadService

class MyCustomKeyboard : InputMethodService() {

    private var keyboardView: View? = null
    private var etSearch: EditText? = null
    private var isCaps = false
    private var isSearchFocused = false
    private val apiKey = "sk-71c69f4de1f4b912957fed45"

    // Gboard tarzı QWERTY - Türkçe karakterler dahil
    private val row1Keys = listOf("q","w","e","r","t","y","u","ı","o","p")
    private val row2Keys = listOf("a","s","d","f","g","h","j","k","l")
    private val row3Keys = listOf("SHIFT","z","x","c","v","b","n","m","DEL")
    private val row4Keys = listOf("?123",",","SPACE",".","ENTER")

    override fun onCreateInputView(): View {
        Log.d("MusicKeyboard", "onCreateInputView Gboard style")
        try {
            keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)
            etSearch = keyboardView!!.findViewById(R.id.etSearch)
            val btnDownload = keyboardView!!.findViewById<TextView>(R.id.btnDownload)
            val btnSearchAndType = keyboardView!!.findViewById<TextView>(R.id.btnSearchAndType)
            val btnClear = keyboardView!!.findViewById<TextView>(R.id.btnClearSearch)

            val row1 = keyboardView!!.findViewById<LinearLayout>(R.id.row1)
            val row2 = keyboardView!!.findViewById<LinearLayout>(R.id.row2)
            val row3 = keyboardView!!.findViewById<LinearLayout>(R.id.row3)
            val row4 = keyboardView!!.findViewById<LinearLayout>(R.id.row4)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                etSearch?.showSoftInputOnFocus = false
            }

            etSearch?.setOnFocusChangeListener { _, hasFocus ->
                isSearchFocused = hasFocus
                updateSearchClearButton()
            }
            etSearch?.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateSearchClearButton()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            btnClear?.setOnClickListener {
                etSearch?.text?.clear()
                isSearchFocused = true
            }

            // Klavye satırlarını oluştur - Gboard tarzı
            createRow(row1, row1Keys, isTopRow = true)
            createRow(row2, row2Keys, isMiddle = true)
            createRow(row3, row3Keys, isShiftRow = true)
            createRow(row4, row4Keys, isBottomRow = true)

            btnDownload.setOnClickListener {
                val query = etSearch?.text?.toString()?.trim()
                if (query.isNullOrEmpty()) {
                    Toast.makeText(this, "Şarkı adı gir", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startDownloadService(query)
                etSearch?.text?.clear()
                isSearchFocused = false
            }

            btnSearchAndType.setOnClickListener {
                val query = etSearch?.text?.toString()
                if (!query.isNullOrEmpty()) {
                    currentInputConnection?.commitText(query, 1)
                }
            }

            etSearch?.post {
                etSearch?.requestFocus()
                isSearchFocused = true
            }

            return keyboardView!!
        } catch (e: Exception) {
            Log.e("MusicKeyboard", "Error", e)
            val fallback = TextView(this).apply { text = "HATA: ${e.message}" }
            return fallback
        }
    }

    private fun updateSearchClearButton() {
        val clearBtn = keyboardView?.findViewById<TextView>(R.id.btnClearSearch)
        clearBtn?.visibility = if (etSearch?.text?.isNotEmpty() == true) View.VISIBLE else View.GONE
    }

    private fun createRow(container: LinearLayout, keys: List<String>, isTopRow: Boolean = false, isMiddle: Boolean = false, isShiftRow: Boolean = false, isBottomRow: Boolean = false) {
        container.removeAllViews()
        for (key in keys) {
            val keyView = createKeyView(key, isBottomRow)
            val params = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
                // Space daha geniş
                if (key == "SPACE") weight = 4f
                if (key == "SHIFT" || key == "DEL" || key == "?123" || key == "ENTER") weight = 1.3f
            }
            container.addView(keyView, params)
        }
    }

    private fun createKeyView(key: String, isBottomRow: Boolean): TextView {
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
                "?123" -> 12f
                else -> 16f
            }
            gravity = Gravity.CENTER
            setTextColor(
                when(key) {
                    "ENTER" -> 0xFF202124.toInt()
                    else -> 0xFFE8EAED.toInt()
                }
            )
            // Gboard tarzı arka plan
            background = when(key) {
                "SPACE" -> ContextCompat.getDrawable(context, R.drawable.key_bg_space)
                "ENTER" -> ContextCompat.getDrawable(context, R.drawable.key_bg_enter)
                "DEL", "SHIFT", "?123", ",", "." -> ContextCompat.getDrawable(context, R.drawable.key_bg_action)
                else -> ContextCompat.getDrawable(context, R.drawable.key_bg_normal)
            }
            // Caps aktifse vurgula
            if (key == "SHIFT" && isCaps) {
                setBackgroundColor(0xFF8AB4F8.toInt())
                setTextColor(0xFF202124.toInt())
            }

            isAllCaps = false
            setPadding(0,0,0,0)
            elevation = 0f

            setOnClickListener { onKeyPressed(key) }

            // Uzun basma - Türkçe karakterler için
            if (key.length == 1 && key !in listOf(",",".")) {
                setOnLongClickListener {
                    showAltChars(key)
                    true
                }
            }
        }
    }

    private fun showAltChars(base: String) {
        // Basit alt karakter popup - geliştirilebilir
        val alts = when(base) {
            "a" -> listOf("a","â","á")
            "s" -> listOf("s","ş")
            "g" -> listOf("g","ğ")
            "u" -> listOf("u","ü")
            "o" -> listOf("o","ö")
            "c" -> listOf("c","ç")
            "i" -> listOf("i","ı","î")
            else -> listOf(base)
        }
        if (alts.size > 1) {
            // Şimdilik sadece ilk alternatifi yaz
            val alt = alts[1]
            if (isSearchFocused) etSearch?.text?.append(alt) else currentInputConnection?.commitText(alt, 1)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun startDownloadService(query: String) {
        val intent = Intent(this, MusicDownloadService::class.java).apply {
            putExtra(Constants.INTENT_EXTRA_QUERY, query)
            putExtra("api_key", apiKey)
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
        Log.d("MusicKeyboard", "Key: $key, searchFocused: $isSearchFocused")

        if (isSearchFocused && etSearch != null) {
            when (key) {
                "DEL" -> {
                    val text = etSearch!!.text
                    if (text.isNotEmpty()) text.delete(text.length - 1, text.length)
                }
                "SPACE" -> etSearch!!.text.append(" ")
                "ENTER" -> {
                    val query = etSearch?.text?.toString()?.trim()
                    if (!query.isNullOrEmpty()) {
                        startDownloadService(query)
                        etSearch?.text?.clear()
                    }
                }
                "SHIFT" -> {
                    isCaps = !isCaps
                    refreshAllRows()
                }
                "?123" -> Toast.makeText(this, "Sayı modu yakında", Toast.LENGTH_SHORT).show()
                "," -> etSearch!!.text.append(",")
                "." -> etSearch!!.text.append(".")
                else -> {
                    val c = if(isCaps) key.uppercase() else key
                    etSearch!!.text.append(c)
                }
            }
            updateSearchClearButton()
            return
        }

        val ic = currentInputConnection
        if (ic == null) {
            // Fallback arama kutusuna yaz
            if (key.length == 1) {
                etSearch?.text?.append(if(isCaps) key.uppercase() else key)
                isSearchFocused = true
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
            "?123" -> Toast.makeText(this, "Sayı modu yakında", Toast.LENGTH_SHORT).show()
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
            createRow(row1, row1Keys, isTopRow = true)
            createRow(row2, row2Keys, isMiddle = true)
            createRow(row3, row3Keys, isShiftRow = true)
            createRow(row4, row4Keys, isBottomRow = true)
        }
    }
}
