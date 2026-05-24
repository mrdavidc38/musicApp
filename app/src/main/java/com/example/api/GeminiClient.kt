package com.example.api

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini Request/Response Models ---

@JsonClass(generateAdapter = true)
data class Part(val text: String)

@JsonClass(generateAdapter = true)
data class Content(val parts: List<Part>)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(val content: Content)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(val candidates: List<Candidate>?)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

data class PlaylistRecommendation(
    val explanation: String,
    val recommendedOrder: List<Int>
)

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun getMoodPlaylist(userMood: String, apiKey: String): PlaylistRecommendation {
        val systemInstructionText = """
            Eres 'El DJ Inteligente de la App'. El usuario te dirá cómo se siente, su estado mental o lo que está haciendo.
            Tu trabajo es recomendar un orden idóneo para sonar las siguientes 5 canciones disponibles en nuestro catálogo:
            0: 'Chill Horizon' (música lofi instrumental relajante, nocturna, estudio, calma)
            1: 'Electro Rhythm' (electro/pop bailable, energético, motivador, ejercicio)
            2: 'Summer Breeze' (acústico alegre y veraniego, optimista, viaje por carretera)
            3: 'Synthwave Dreamer' (neón retro futurista de los 80, ideal para conducir de noche o jugar)
            4: 'Cosmic Resonance' (sonidos espaciales profundos de meditación, descanso profundo, yoga)

            Debes retornar tu respuesta ESTRICTAMENTE en formato JSON plano con esta estructura exacta de forma que podamos parsearla en nuestra app móvil Android:
            {
              "explanation": "Una frase poética, descriptiva e inspiradora en español explicando por qué este orden se adapta a su estado de ánimo.",
              "recommended_order": [2, 0, 4] 
            }
            El arreglo recommended_order debe ser una lista de enteros en el rango [0, 1, 2, 3, 4] (puedes sugerir de 2 a 5 canciones según lo que pida).
            No agregues texto fuera del bloque JSON. Evita el marcado markdown de código ```json o ```. Devuelve únicamente el string de JSON puro.
        """.trimIndent()

        val prompt = "El usuario se siente o está haciendo: '$userMood'. Genera su playlist personalizada en JSON."

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText)))
        )

        return try {
            val response = apiService.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("No se recibió respuesta de IA")

            parseRecommendation(jsonText)
        } catch (e: Exception) {
            // Return fallback recommendation on error
            PlaylistRecommendation(
                explanation = "Sinfonía seleccionada de respaldo. ¡Disfruta de la música clásica de la aplicación!",
                recommendedOrder = listOf(0, 2, 3, 4, 1)
            )
        }
    }

    private fun parseRecommendation(rawJson: String): PlaylistRecommendation {
        // Clean markdown tags if the model didn't respect instructions completely
        val cleaned = rawJson
            .trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val adapter = moshi.adapter(Map::class.java)
            val map = adapter.fromJson(cleaned)
            val explanation = map?.get("explanation") as? String ?: "Selección especial para ti."
            val rawOrder = map?.get("recommended_order") as? List<*>
            val recommendedOrder = rawOrder?.mapNotNull {
                when (it) {
                    is Double -> it.toInt()
                    is Int -> it
                    is String -> it.toIntOrNull()
                    else -> null
                }
            } ?: listOf(0, 1, 2, 3, 4)

            PlaylistRecommendation(explanation, recommendedOrder)
        } catch (e: Exception) {
            // Regex parsing as fallback in case standard JSON parsing failed due to unexpected structural tweaks
            val explanationRegex = "\"explanation\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val orderRegex = "\"recommended_order\"\\s*:\\s*\\[([^\\]]+)\\]".toRegex()

            val explanation = explanationRegex.find(cleaned)?.groupValues?.get(1) ?: "Playlist sugerida para tu estado de ánimo."
            val orderString = orderRegex.find(cleaned)?.groupValues?.get(1) ?: ""
            val order = orderString.split(",")
                .map { it.trim().toIntOrNull() }
                .filterNotNull()
                .ifEmpty { listOf(0, 1, 2, 3, 4) }

            PlaylistRecommendation(explanation, order)
        }
    }
}
