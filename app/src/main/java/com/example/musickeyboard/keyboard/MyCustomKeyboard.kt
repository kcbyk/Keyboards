package com.example.musickeyboard.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
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

    override fun onCreate() {
        super.onCreate()
        Log.d("MusicKeyboard", "Service onCreate")
    }

    override fun onCreateInputView(): View {
        Log.d("MusicKeyboard", "onCreateInputView called")
        try {
            keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)
            etSearch = keyboardView!!.findViewById(R.id.etSearch)
            val btnDownload = keyboardView!!.findViewById<Button>(R.id.btnDownload)
            val btnSearchAndType = keyboardView!!.findViewById<Button>(R.id.btnSearchAndType)
            val keyGrid = keyboardView!!.findViewById<GridLayout>(R.id.keyGrid)

            // EditText'in kendi klavyesini açmasını engelle - yoksa sonsuz döngü olur
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                etSearch?.showSoftInputOnFocus = false
            }

            // Focus takibi - arama kutusuna mı yazıyoruz, ana uygulamaya mı?
            etSearch?.setOnFocusChangeListener { _, hasFocus ->
                isSearchFocused = hasFocus
                Log.d("MusicKeyboard", "Search focused: $hasFocus")
            }
            etSearch?.setOnClickListener {
                isSearchFocused = true
                etSearch?.requestFocus()
                Log.d("MusicKeyboard", "Search clicked, focused=true")
            }

            setupKeys(keyGrid)

            btnDownload.setOnClickListener {
                val query = etSearch?.text?.toString()?.trim()
                if (query.isNullOrEmpty()) {
                    Toast.makeText(this, "Önce şarkı adı gir", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Yazıldı: $query", Toast.LENGTH_SHORT).show()
                }
            }

            // Varsayılan olarak arama kutusu odaklı başlasın ki kullanıcı direkt şarkı yazabilsin
            etSearch?.post {
                etSearch?.requestFocus()
                isSearchFocused = true
            }

            Log.d("MusicKeyboard", "Keyboard view created successfully")
            return keyboardView!!
        } catch (e: Exception) {
            Log.e("MusicKeyboard", "Error creating view", e)
            val fallback = Button(this).apply { text = "HATA: ${e.message}" }
            return fallback
        }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        Log.d("MusicKeyboard", "onStartInput: ${info?.packageName}")
        // Her yeni input başladığında arama odaklı değil, normal yazma odaklı ol
        isSearchFocused = false
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d("MusicKeyboard", "onStartInputView")
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
            Toast.makeText(this, "\"$query\" kuyruğa alındı", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MusicKeyboard", "Service start error", e)
            Toast.makeText(this, "Servis hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupKeys(grid: GridLayout) {
        grid.removeAllViews()
        grid.columnCount = 10

        val keys = listOf(
            listOf("q","w","e","r","t","y","u","ı","o","p"),
            listOf("ğ","ü","a","s","d","f","g","h","j","k"),
            listOf("l","ş","i","z","x","c","v","b","n","m"),
            listOf("ö","ç","CAPS","SPACE","DEL","ENTER")
        )

        for (row in keys) {
            for (key in row) {
                val btn = Button(this).apply {
                    text = when(key) {
                        "SPACE" -> if(isSearchFocused) "BOŞLUK (Arama)" else "BOŞLUK"
                        "DEL" -> "⌫ SİL"
                        "ENTER" -> "↵"
                        "CAPS" -> if(isCaps) "⇧ A" else "⇧ a"
                        else -> if (isCaps) key.uppercase() else key
                    }
                    textSize = 12f
                    isAllCaps = false
                    setPadding(2,12,2,12)
                    setTextColor(0xFFFFFFFF.toInt())
                    if (key == "SPACE") {
                        setBackgroundColor(if(isSearchFocused) 0xFF1DB954.toInt() else 0xFF333333.toInt())
                    } else {
                        setBackgroundResource(android.R.drawable.btn_default)
                    }
                    setOnClickListener { onKeyPressed(key) }
                }
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, if(key=="SPACE") 4f else 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(3,3,3,3)
                }
                grid.addView(btn, params)
            }
        }
    }

    private fun onKeyPressed(key: String) {
        Log.d("MusicKeyboard", "Key pressed: $key, searchFocused: $isSearchFocused")

        // Eğer arama kutusu odaklıysa, oraya yaz
        if (isSearchFocused && etSearch != null) {
            when (key) {
                "DEL" -> {
                    val text = etSearch!!.text
                    if (text.isNotEmpty()) {
                        etSearch!!.text.delete(text.length - 1, text.length)
                    }
                }
                "SPACE" -> etSearch!!.text.append(" ")
                "ENTER" -> {
                    // Enter'a basınca indir
                    val query = etSearch?.text?.toString()?.trim()
                    if (!query.isNullOrEmpty()) {
                        startDownloadService(query)
                        etSearch?.text?.clear()
                    }
                }
                "CAPS" -> {
                    isCaps = !isCaps
                    keyboardView?.findViewById<GridLayout>(R.id.keyGrid)?.let { setupKeys(it) }
                }
                else -> {
                    val charToCommit = if (isCaps) key.uppercase() else key
                    etSearch!!.text.append(charToCommit)
                }
            }
            return
        }

        // Değilse normal uygulamaya yaz
        val ic = currentInputConnection
        if (ic == null) {
            Log.e("MusicKeyboard", "InputConnection null!")
            // InputConnection yoksa arama kutusuna yazmayı dene
            if (etSearch != null && key != "DEL" && key != "SPACE" && key != "ENTER" && key != "CAPS") {
                val charToCommit = if (isCaps) key.uppercase() else key
                etSearch!!.text.append(charToCommit)
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
            "CAPS" -> {
                isCaps = !isCaps
                keyboardView?.findViewById<GridLayout>(R.id.keyGrid)?.let { setupKeys(it) }
            }
            else -> {
                val charToCommit = if (isCaps) key.uppercase() else key
                ic.commitText(charToCommit, 1)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        keyboardView = null
        Log.d("MusicKeyboard", "Service destroyed")
    }
}
