package com.gm.tftwatch

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class Champ(val name: String, val cost: Int? = null, val items: List<String> = emptyList())

@Serializable
data class EarlyStep(val label: String, val units: List<String> = emptyList())

@Serializable
data class Deck(
    val rank: Int = 0,
    val name: String = "",
    val tier: String = "",
    val units: List<Champ> = emptyList(),
    val carries: List<Champ> = emptyList(),
    val early: List<EarlyStep> = emptyList(),
)

@Serializable
data class DeckData(val updatedAt: String = "", val decks: List<Deck> = emptyList())

/** GitHub Pages의 decks.json을 받아오고, 실패하면 마지막으로 받은 캐시를 써요. */
object DeckRepository {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private const val CACHE = "decks.json"

    fun cached(ctx: Context): DeckData? = runCatching {
        json.decodeFromString<DeckData>(File(ctx.filesDir, CACHE).readText())
    }.getOrNull()

    suspend fun load(ctx: Context): DeckData? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(BuildConfig.DECKS_URL + "?t=" + System.currentTimeMillis() / 600_000)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()
            val data = json.decodeFromString<DeckData>(text)
            File(ctx.filesDir, CACHE).writeText(text)
            data
        }.getOrNull() ?: cached(ctx)
    }
}
