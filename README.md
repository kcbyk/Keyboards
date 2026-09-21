# Music Keyboard - Custom IME + Background Download

Android için kişisel kullanım amaçlı özel klavye. Klavye üzerinden şarkı adı girip arka plan servisi ile `/Music/MusicKeyboard/` klasörüne indirme yapar.

### Mimari Akış
```
[MyCustomKeyboard (InputMethodService)] 
      |
      | Intent { query = "Tarkan - Kuzu Kuzu" }
      v
[MusicDownloadService (Foreground Service)]
      |
      | 1. Retrofit -> API_SEARCH -> Track ID
      | 2. Retrofit -> API_RESOLVE -> Gerçek MP3 URL
      v
[OkHttp Streaming Download] -> MediaStore API -> /Music/MusicKeyboard/
      |
      v
[Notification + Toast]
```

### Kurulum Adımları

1. **Projeyi Android Studio'da aç**
   - `local.properties` dosyasına ekle:
     ```
     MUSIC_API_KEY=senin_gercek_api_anahtarin
     ```

2. **Build.gradle'da BASE_URL'i değiştir**
   - `Constants.kt` veya `build.gradle.kts` içindeki `MUSIC_API_BASE_URL` değerini kendi yasal API'ına göre ayarla.
   - Örnek yasal kaynaklar:
     - Jamendo API: `https://api.jamendo.com/v3.0/`
     - Kendi sunucun: `https://mydomain.com/api/`
     - Free Music Archive

3. **API Yanıt Modelini Uyarla**
   - `model/SearchResponse.kt` dosyasını kendi API'ının JSON'ına göre düzenle.

4. **İzinler**
   - Uygulamayı çalıştır -> MainActivity üzerinden:
     1. Klavyeyi etkinleştir
     2. Depolama izni (Android 11+ için MANAGE_ALL_FILES opsiyonel, MediaStore zaten çalışır)
     3. Bildirim izni

5. **Test**
   - Herhangi bir uygulamada (WhatsApp, Chrome) klavyeyi aç -> MusicBoard'u seç
   - Üstteki arama kutusuna "test" yaz -> İNDİR'e bas
   - Bildirim çubuğunda ilerlemeyi gör

### Performans İpuçları (RAM)

- `onCreateInputView()` sadece bir kez inflate ediliyor, cache'leniyor
- GridLayout ile dinamik tuşlar, ağır KeyboardView kullanılmadı
- Drawable yok, sadece sistem `btn_default`
- Servis `Dispatchers.IO` ile çalışıyor, Main Thread bloklanmıyor
- View'lar `onDestroy()`'da temizleniyor

### Android 11+ Depolama Stratejisi

- **Önerilen:** `MediaStore` API (kodda mevcut). `MANAGE_EXTERNAL_STORAGE` gerekmez.
- **Kişisel kullanım için alternatif:** `MANAGE_EXTERNAL_STORAGE` izni ile direkt `File` API.
  - Manifest'te var ama Play Store'a yüklersen reddedilir.
  - Kişisel APK için sorun değil.

### Güvenlik

- API anahtarı `BuildConfig` içinde, Git'e commit etme
- `local.properties` kullan
- ProGuard ile obfuscation eklenebilir

### Geliştirme Fikirleri

- [ ] WorkManager ile kuyruklu indirme
- [ ] Klavye içinde RecyclerView ile arama sonuçları listesi
- [ ] Gemini API entegrasyonu: "Bu şarkıya benzer telifsiz müzik bul"
- [ ] İndirme geçmişi (Room DB)

### Yasal Uyarı

Bu proje şablonu sadece eğitim ve kişisel, telifsiz içerikler içindir. Telifli müzikleri izinsiz indirmek için kullanmayın.
