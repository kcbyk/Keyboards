package com.example.musickeyboard.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
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

    // API key'i buradan da override edebilirsin - BuildConfig'ten geliyor
    private val apiKey = "sk-71c69f4de1f4b912957fed45"

    override fun onCreateInputView(): View {
        if (keyboardView != null) return keyboardView!!
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
            if (!query.isNullOrEmpty()) currentInputConnection?.commitText(query, 1)
        }
        return keyboardView!!
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
            Toast.makeText(this, "Servis hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupKeys(grid: GridLayout) {
        grid.removeAllViews()
        val rows = listOf(
            listOf("q","w","e","r","t","y","u","ı","o","p","ğ","ü"),
            listOf("a","s","d","f","g","h","j","k","l","ş","i"),
            listOf("CAPS","z","x","c","v","b","n","m","ö","ç","DEL"),
            listOf("123","SPACE","ENTER")
        )
        for (row in rows) {
            for (key in row) {
                val btn = Button(this).apply {
                    text = when(key) {
                        "SPACE" -> "Boşluk"; "DEL" -> "⌫"; "ENTER" -> "↵"; "CAPS" -> "⇧"
                        else -> if (isCaps) key.uppercase() else key
                    }
                    textSize = 14f; isAllCaps = false
                    setPadding(4,4,4,4)
                    setBackgroundResource(android.R.drawable.btn_default)
                    setOnClickListener { onKeyPressed(key) }
                }
                val params = GridLayout.LayoutParams().apply {
                    width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, if(key=="SPACE") 3f else 1f)
                    setMargins(2,2,2,2)
                }
                grid.addView(btn, params)
            }
        }
    }

    private fun onKeyPressed(key: String) {
        val ic = currentInputConnection ?: return
        when (key) {
            "DEL" -> ic.deleteSurroundingText(1, 0)
            "SPACE" -> ic.commitText(" ", 1)
            "ENTER" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
            }
            "CAPS" -> { isCaps = !isCaps; keyboardView?.findViewById<GridLayout>(R.id.keyGrid)?.let { setupKeys(it) } }
            "123" -> Toast.makeText(this, "Sayı modu yakında", Toast.LENGTH_SHORT).show()
            else -> ic.commitText(if (isCaps) key.uppercase() else key, 1)
        }
    }

    override fun onDestroy() { super.onDestroy(); keyboardView = null }
}
