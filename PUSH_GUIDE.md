# GitHub'a Push ve APK Build Rehberi

Repo: https://github.com/kcbyk/Keyboards (şu an boş)

## 1. GÜVENLİK - API KEY'İNİ HEMEN KORU

Paylaştığın key: `sk-2126...da3`
Bu key artık public oldu. Yapman gerekenler:

1.  Eğer bu key'i bir servisten aldıysan (OpenAI, kendi sunucun vb) hemen panelden **revoke / delete** et ve yenisini oluştur.
2.  Asla GitHub'a direkt commit yapma. Aşağıdaki yöntemi kullan.

## 2. Lokal'den GitHub'a İlk Push

Terminal'de proje klasöründe:

```bash
cd MusicKeyboardIME
git init
git add .
git commit -m "Initial: Music Keyboard IME + Download Service"

# Repo'yu bağla
git remote add origin https://github.com/kcbyk/Keyboards.git
git branch -M main
git push -u origin main
```

GitHub username/password isteyecek. Artık şifreli push için Personal Access Token (PAT) kullanmalısın:
GitHub -> Settings -> Developer Settings -> Personal Access Tokens -> Generate.

## 3. APK'yı GitHub Actions ile Otomatik Build Etme (ÖNERİLEN)

APK'yı direkt `git push` ile yüklemek kötü pratik. Bunun yerine Actions kullanıyoruz.

### Adım A: Secret Ekle
1. GitHub repo sayfana git: `https://github.com/kcbyk/Keyboards`
2. Settings -> Secrets and variables -> Actions -> New repository secret
3. Name: `MUSIC_API_KEY`
4. Value: `sk-212601df4dcd2036cb5b4da3` (yeni oluşturduğun key'i gir)

### Adım B: Workflow Dosyası Zaten Hazır
`.github/workflows/build-apk.yml` dosyası projede mevcut. Push yaptığında otomatik çalışacak.

### Adım C: Release'den APK İndir
Push sonrası:
- Repo -> Actions tab -> Build işlemi bitsin (2-3 dk)
- Sonra Releases tab'ında APK hazır olacak
- Veya Actions -> Artifacts kısmından indirebilirsin

## 4. Lokal'de APK Build Alma

Android Studio'da:
```
Build -> Build Bundle(s) / APK(s) -> Build APK(s)
```
APK şurada oluşur: `app/build/outputs/apk/debug/app-debug.apk`

Komut satırından:
```bash
./gradlew assembleDebug
```

## 5. API Key Çalışıyor mu Test Etme

Senin key'in formatı `sk-...` - Bu genelde OpenAI veya custom API'lerde kullanılır.

### En hızlı test - MainActivity'e geçici kod ekle:

```kotlin
// MainActivity onCreate içine ekle ve bir kez çalıştır
lifecycleScope.launch {
    try {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://your-api.com/v1/search?q=test") // BURAYI KENDİ BASE URL'İNLE DEĞİŞTİR
            .addHeader("Authorization", "Bearer sk-212601df4dcd2036cb5b4da3")
            .build()
        val resp = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        Log.d("API_TEST", "Code: ${resp.code} Body: ${resp.body?.string()}")
        Toast.makeText(this@MainActivity, "Kod: ${resp.code}", Toast.LENGTH_LONG).show()
    } catch(e: Exception) {
        Toast.makeText(this@MainActivity, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
```

Eğer 200 dönerse key çalışıyor. 401/403 dönerse key geçersiz.

Bana API'nin BASE URL'ini söylersen (örn: `https://api.senin-siten.com`) ben sana direkt test eden curl komutunu da yazabilirim:

```bash
curl -H "Authorization: Bearer sk-212601df4dcd2036cb5b4da3" https://your-api.com/v1/search?q=test
```

## 6. Önemli: .gitignore

`local.properties` ve `*.apk` dosyaları gitignore'da, bu yüzden key'in yanlışlıkla pushlanmaz.

Eğer daha önce key'i commitlediysen:
```bash
git rm --cached local.properties
git commit -m "Remove api key"
git push
```
Ve hemen key'i yenile.
