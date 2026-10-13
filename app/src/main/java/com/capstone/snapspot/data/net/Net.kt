package com.capstone.snapspot.data.net

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** The keys the network clients send. Blank means "not set up"; the clients say so in Korean. */
data class ApiKeys(
    val geminiKey: String,
    val geminiModel: String,
    val kakaoRestKey: String,
)

/** A request that reached the server (or came back empty) but did not succeed. [message] is shown to the user. */
class ApiException(message: String) : Exception(message)

/** Servers add fields over time and sometimes send nulls; neither should break reading. */
val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

fun buildRetrofit(baseUrl: String, client: OkHttpClient): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(NetworkJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()

/**
 * Request logging for debug builds. Both key headers are always redacted, so a key never reaches the
 * log even at a level that prints headers.
 */
fun loggingInterceptor(
    level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC,
    logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
): HttpLoggingInterceptor =
    HttpLoggingInterceptor(logger).apply {
        this.level = level
        redactHeader("Authorization")
        redactHeader("x-goog-api-key")
    }

const val KAKAO_BASE_URL = "https://dapi.kakao.com/"
const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
