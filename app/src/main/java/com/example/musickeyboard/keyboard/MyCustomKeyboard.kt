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

            setupKeys(keyGrid)

            btnDownload.setOnClickListener {
                val query = etSearch?.text?.toString()?.trim()
                if (query.isNullOrEmpty()) {
                    Toast.makeText(this, "Önce şarkı adı gir", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startDownloadService(query)
                etSearch?.text?.clear()
            }

            btnSearchAndType.setOnClickListener {
                val query = etSearch?.text?.toString()
                if (!query.isNullOrEmpty()) {
                    currentInputConnection?.commitText(query, 1)
                }
            }

            Log.d("MusicKeyboard", "Keyboard view created successfully")
            return keyboardView!!
        } catch (e: Exception) {
            Log.e("MusicKeyboard", "Error creating view", e)
            // Fallback: basit view
            val fallback = Button(this).apply { text = "HATA: ${e.message}" }
            return fallback
        }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        Log.d("MusicKeyboard", "onStartInput: ${info?.packageName}")
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
                        "SPACE" -> "BOŞLUK"
                        "DEL" -> "⌫ SİL"
                        "ENTER" -> "↵"
                        "CAPS" -> if(isCaps) "⇧ A" else "⇧ a"
                        else -> if (isCaps) key.uppercase() else key
                    }
                    textSize = 13f
                    isAllCaps = false
                    setPadding(2,8,2,8)
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundResource(android.R.drawable.btn_default)
                    // Daha görünür yapmak için
                    if (key == "SPACE") {
                        setBackgroundColor(0xFF1DB954.toInt())
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
        val ic = currentInputConnection
        if (ic == null) {
            Log.e("MusicKeyboard", "InputConnection null!")
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
