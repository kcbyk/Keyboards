/**
 * API Anahtarı Test Scripti - Bunu MainActivity'de geçici olarak çalıştır
 * veya Android Studio'da bir unit test olarak kullan
 */
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request

// Kendi API base URL'ini buraya yaz
const val TEST_BASE_URL = "https://your-legal-api.example.com/"
const val TEST_API_KEY = "sk-212601df4dcd2036cb5b4da3" // Test edilecek anahtar

fun main() = runBlocking {
    val client = OkHttpClient()
    
    // 1. Basit ping testi
    val endpointsToTry = listOf(
        "${TEST_BASE_URL}v1/search?q=test",
        "${TEST_BASE_URL}health",
        "${TEST_BASE_URL}ping"
    )
    
    for (url in endpointsToTry) {
        try {
            println("Testing: $url")
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $TEST_API_KEY")
                .addHeader("x-api-key", TEST_API_KEY) // bazı API'lar böyle ister
                .build()
            
            val response = client.newCall(request).execute()
            println("-> Status: ${response.code}")
            println("-> Body: ${response.body?.string()?.take(500)}")
            println("---")
        } catch (e: Exception) {
            println("-> HATA: ${e.message}")
        }
    }
}

// Android içinde test için:
/*
class ApiKeyTester {
    fun testKey(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = MusicApiClient.service.searchSong("test")
                Log.d("API_TEST", "BAŞARILI: ${result.results?.size} sonuç bulundu - Key çalışıyor!")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "API Key ÇALIŞIYOR!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("API_TEST", "BAŞARISIZ: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "API Key HATASI: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
*/
