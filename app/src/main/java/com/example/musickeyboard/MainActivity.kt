package com.example.musickeyboard

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Toast.makeText(this, "Bildirim izni verildi", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateStatus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Tüm dosyalara erişim verildi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnEnableKeyboard = findViewById<Button>(R.id.btnEnableKeyboard)
        val btnSwitchKeyboard = findViewById<Button>(R.id.btnSwitchKeyboard)
        val btnStoragePerm = findViewById<Button>(R.id.btnStoragePerm)
        val btnNotificationPerm = findViewById<Button>(R.id.btnNotificationPerm)
        val etTest = findViewById<EditText>(R.id.etTest)

        btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            Toast.makeText(this, "Listeden MusicBoard'u AÇ", Toast.LENGTH_LONG).show()
        }

        btnSwitchKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
            Toast.makeText(this, "Açılan listeden MusicBoard'u SEÇ", Toast.LENGTH_LONG).show()
        }

        btnStoragePerm.setOnClickListener { checkAndRequestStoragePermission() }

        btnNotificationPerm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "Bu sürümde gerek yok", Toast.LENGTH_SHORT).show()
            }
        }

        etTest.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) updateStatus()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        findViewById<TextView>(R.id.tvStatus).text = buildStatusText()
    }

    private fun buildStatusText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== KURULUM DURUMU ===")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledList = imm.enabledInputMethodList
        val isEnabled = enabledList.any { it.packageName == packageName }
        val isSelected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.contains(packageName) == true

        sb.appendLine("Klavyeler: ${enabledList.map { it.packageName }}")
        sb.appendLine("MusicBoard Etkin: ${if(isEnabled) "✅ EVET" else "❌ HAYIR - 1. butona bas"}")
        sb.appendLine("MusicBoard Seçili: ${if(isSelected) "✅ EVET" else "❌ HAYIR - 2. butona bas"}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sb.appendLine("Dosya Erişimi: ${if(Environment.isExternalStorageManager()) "✅" else "❌"}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            sb.appendLine("Bildirim: ${if(hasNotif) "✅" else "❌"}")
        }
        sb.appendLine("\nİndirilenler: /Music/MusicKeyboard/")
        if (!isEnabled) sb.appendLine("\n⚠️ ÖNCE klavyeyi etkinleştirmen lazım!")
        else if (!isSelected) sb.appendLine("\n⚠️ Şimdi klavyeyi SEÇ (2. buton)")
        else sb.appendLine("\n✅ Hazır! Test alanına dokun, klavye gelecek")

        return sb.toString()
    }

    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else Toast.makeText(this, "Zaten var", Toast.LENGTH_SHORT).show()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
