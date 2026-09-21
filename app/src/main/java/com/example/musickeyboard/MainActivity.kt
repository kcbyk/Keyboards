package com.example.musickeyboard

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Bildirim izni verildi", Toast.LENGTH_SHORT).show()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
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
        val btnStoragePerm = findViewById<Button>(R.id.btnStoragePerm)
        val btnNotificationPerm = findViewById<Button>(R.id.btnNotificationPerm)

        btnEnableKeyboard.setOnClickListener {
            // Klavye ayarlarına yönlendir
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        btnStoragePerm.setOnClickListener {
            checkAndRequestStoragePermission()
        }

        btnNotificationPerm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "Bu Android sürümünde gerek yok", Toast.LENGTH_SHORT).show()
            }
        }

        // Durum güncelle
        tvStatus.text = buildStatusText()
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.tvStatus).text = buildStatusText()
    }

    private fun buildStatusText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Kurulum Durumu ===")
        
        // Klavye etkin mi?
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        sb.appendLine("Klavye Etkin: ${if(enabled) "✅" else "❌"}")

        // Depolama izni
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sb.appendLine("Tüm Dosya Erişimi: ${if(Environment.isExternalStorageManager()) "✅" else "❌ (Opsiyonel, MediaStore kullanılıyor)"}")
        }

        // Bildirim izni
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            sb.appendLine("Bildirim İzni: ${if(hasNotif) "✅" else "❌"}")
        }

        sb.appendLine("\nİndirilenler: /Music/MusicKeyboard/")
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
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            } else {
                Toast.makeText(this, "Zaten izniniz var", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 10 ve altı için klasik izin
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
