package com.capstone.snapspot.data.net

import com.capstone.snapspot.domain.RawExtraction
import com.capstone.snapspot.domain.ScreenshotAnalyzer
import com.capstone.snapspot.domain.model.Extracted
import com.capstone.snapspot.domain.sanitize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.Base64
import javax.inject.Inject

@Serializable
data class InlineData(@SerialName("mime_type") val mimeType: String, val data: String)

@Serializable
data class Part(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: InlineData? = null,
)

@Serializable
data class Content(val role: String? = null, val parts: List<Part> = emptyList())

@Serializable
data class GenerationConfig(
    val temperature: Double,
    val responseMimeType: String,
    val responseSchema: JsonObject,
)

@Serializable
data class GenerateRequest(val contents: List<Content>, val generationConfig: GenerationConfig)

@Serializable
data class GeminiCandidate(val content: Content? = null)

@Serializable
data class GenerateResponse(val candidates: List<GeminiCandidate> = emptyList())

/** What the model is asked to return, before [sanitize] cleans it. */
@Serializable
data class GeminiExtraction(
    val kind: String? = null,
    val brand: String? = null,
    val benefit: String? = null,
    val isPlace: Boolean? = null,
    val name: String? = null,
    val address: String? = null,
    val category: String? = null,
    val note: String? = null,
    val endDate: String? = null,
)

interface GeminiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generate(
        @Header("x-goog-api-key") key: String,
        @Path("model") model: String,
        @Body body: GenerateRequest,
    ): Response<GenerateResponse>
}

internal const val GEMINI_PROMPT = """너는 스마트폰 스크린샷에서 "나중에 가 보고 싶은 장소"나 "프랜차이즈 쿠폰"을 뽑아내는 도우미야.
이미지(인스타그램 게시물, 지도 앱, 블로그, 팝업스토어·전시 포스터, 기프티콘·모바일 쿠폰 등)를 보고 아래 JSON만 출력해.
- kind: 여러 지점이 있는 브랜드(스타벅스, 올리브영, 메가커피 등)의 쿠폰·기프티콘·교환권이면 "coupon", 그 밖에는 "place"
- brand: coupon일 때 브랜드 이름만 (예: "스타벅스", "올리브영"). 지점명은 빼. place면 빈 문자열
- benefit: coupon일 때 받을 수 있는 것 (예: "아메리카노 Tall", "5,000원 할인"). place면 빈 문자열
- isPlace: 특정 오프라인 장소(가게, 카페, 식당, 전시, 팝업, 관광지 등)가 보이면 true
- name: 가게/장소 이름 (지점명 포함, 모르면 빈 문자열)
- address: 이미지에 보이는 주소나 위치 단서 (예: "서울 성동구 연무장길 12", "성수동"). 없으면 빈 문자열
- category: 카페, 식당, 술집, 디저트, 전시, 팝업, 쇼핑, 관광, 기타 중 하나
- note: 사용자가 왜 저장했을지 한 문장 (예: "소금빵 맛집", "10월까지 하는 사진전")
- endDate: 운영 종료일이나 쿠폰 유효기간 마지막 날이 보이면 YYYY-MM-DD, 아니면 null
추측으로 주소를 지어내지 마."""

internal val GEMINI_SCHEMA: JsonObject = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
        putJsonObject("kind") {
            put("type", "STRING")
            putJsonArray("enum") {
                add(JsonPrimitive("place"))
                add(JsonPrimitive("coupon"))
            }
        }
        for (name in listOf("brand", "benefit", "name", "address", "category", "note")) {
            putJsonObject(name) { put("type", "STRING") }
        }
        putJsonObject("isPlace") { put("type", "BOOLEAN") }
        putJsonObject("endDate") {
            put("type", "STRING")
            put("nullable", true)
        }
    }
    putJsonArray("required") {
        for (name in listOf("kind", "brand", "benefit", "isPlace", "name", "address", "category", "note")) {
            add(JsonPrimitive(name))
        }
    }
}

/** Reads a screenshot with Gemini. Replaceable by another [ScreenshotAnalyzer] in the DI module. */
class GeminiAnalyzer @Inject constructor(
    private val service: GeminiService,
    private val keys: ApiKeys,
) : ScreenshotAnalyzer {

    override suspend fun analyze(jpeg: ByteArray): Extracted {
        if (keys.geminiKey.isBlank()) {
            throw ApiException("Gemini API 키가 없어요. local.properties의 GEMINI_API_KEY를 설정하세요.")
        }
        val request = GenerateRequest(
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(
                        Part(text = GEMINI_PROMPT),
                        Part(inlineData = InlineData("image/jpeg", Base64.getEncoder().encodeToString(jpeg))),
                    ),
                ),
            ),
            generationConfig = GenerationConfig(temperature = 0.0, responseMimeType = "application/json", responseSchema = GEMINI_SCHEMA),
        )
        val res = service.generate(keys.geminiKey, keys.geminiModel, request)
        if (!res.isSuccessful) {
            val text = res.errorBody()?.string().orEmpty()
            throw ApiException("Gemini 요청 실패 (${res.code()}): ${text.take(200)}")
        }
        val text = res.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (text.isNullOrEmpty()) throw ApiException("Gemini 응답이 비어 있어요.")
        val parsed = try {
            NetworkJson.decodeFromString<GeminiExtraction>(text)
        } catch (_: Exception) {
            throw ApiException("Gemini 응답을 읽지 못했어요.")
        }
        return sanitize(
            RawExtraction(
                kind = parsed.kind,
                brand = parsed.brand,
                benefit = parsed.benefit,
                isPlace = parsed.isPlace,
                name = parsed.name,
                address = parsed.address,
                category = parsed.category,
                note = parsed.note,
                endDate = parsed.endDate,
            ),
        )
    }
}
