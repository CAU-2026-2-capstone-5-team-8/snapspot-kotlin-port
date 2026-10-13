package com.capstone.snapspot.data.net

import com.capstone.snapspot.domain.model.Kind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Base64

class GeminiNetTest {
    private lateinit var server: MockWebServer
    private val key = "test-gemini-key-456"

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun stop() {
        server.close()
    }

    private fun analyzer(geminiKey: String = key, model: String = "gemini-test") =
        GeminiAnalyzer(
            buildRetrofit(server.url("/").toString(), OkHttpClient()).create(GeminiService::class.java),
            ApiKeys(geminiKey, model, kakaoRestKey = ""),
        )

    private fun reply(modelText: String): MockResponse {
        val escaped = modelText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
            .body("""{"candidates":[{"content":{"role":"model","parts":[{"text":"$escaped"}]},"finishReason":"STOP"}],"usageMetadata":{}}""")
            .build()
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 5, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun `the request has the key header, model path, prompt, image and strict JSON settings`() = runTest {
        server.enqueue(reply("""{"kind":"place","brand":"","benefit":"","isPlace":true,"name":"어니언 성수","address":"","category":"카페","note":"","endDate":null}"""))
        analyzer().analyze(jpeg)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1beta/models/gemini-test:generateContent", req.url.encodedPath)
        assertEquals(key, req.headers["x-goog-api-key"])

        val body = req.body!!.utf8()
        assertFalse("the key must not be in the body", body.contains(key))
        val root = NetworkJson.parseToJsonElement(body).jsonObject
        val content = root["contents"]!!.jsonArray.single().jsonObject
        assertEquals("user", content["role"]!!.jsonPrimitive.content)
        val parts = content["parts"]!!.jsonArray
        assertEquals(2, parts.size)
        assertEquals(GEMINI_PROMPT, parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        val image = parts[1].jsonObject
        assertNull("the image part has no text key", image["text"])
        val inline = image["inline_data"]!!.jsonObject
        assertEquals("image/jpeg", inline["mime_type"]!!.jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(jpeg), inline["data"]!!.jsonPrimitive.content)

        val config = root["generationConfig"]!!.jsonObject
        assertEquals(0.0, config["temperature"]!!.jsonPrimitive.double, 0.0)
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
        val schema = config["responseSchema"]!!.jsonObject
        assertEquals("OBJECT", schema["type"]!!.jsonPrimitive.content)
        val props = schema["properties"]!!.jsonObject
        assertEquals(setOf("kind", "brand", "benefit", "isPlace", "name", "address", "category", "note", "endDate"), props.keys)
        assertEquals(listOf("place", "coupon"), props["kind"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("BOOLEAN", props["isPlace"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(props["endDate"]!!.jsonObject["nullable"]!!.jsonPrimitive.boolean)
        assertEquals(
            listOf("kind", "brand", "benefit", "isPlace", "name", "address", "category", "note"),
            schema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `the prompt keeps the instructions the React Native app used`() {
        for (phrase in listOf("프랜차이즈 쿠폰", "\"coupon\"", "YYYY-MM-DD", "추측으로 주소를 지어내지 마.", "카페, 식당, 술집, 디저트, 전시, 팝업, 쇼핑, 관광, 기타")) {
            assertTrue("missing: $phrase", GEMINI_PROMPT.contains(phrase))
        }
    }

    @Test
    fun `a coupon is read, cleaned and returned`() = runTest {
        server.enqueue(
            reply(""" {"kind":"coupon","brand":" 스타벅스 ","benefit":"아메리카노 Tall ","isPlace":false,"name":"","address":"","category":"","note":"선물 받음","endDate":"2026-12-31"} """),
        )
        val e = analyzer().analyze(jpeg)
        assertEquals(Kind.COUPON, e.kind)
        assertEquals("스타벅스", e.brand)
        assertEquals("아메리카노 Tall", e.benefit)
        assertEquals("기타", e.category)
        assertEquals("2026-12-31", e.endDate)
        assertFalse(e.isPlace)
    }

    @Test
    fun `a place with a malformed date and missing fields still comes back clean`() = runTest {
        server.enqueue(reply("""{"kind":"place","isPlace":true,"name":"더현대 서울 B1 이벤트홀","endDate":"10월 31일"}"""))
        val e = analyzer().analyze(jpeg)
        assertEquals(Kind.PLACE, e.kind)
        assertEquals("더현대 서울 B1 이벤트홀", e.name)
        assertEquals("", e.address)
        assertEquals("기타", e.category)
        assertNull(e.endDate)
    }

    @Test
    fun `an empty answer says so`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"candidates":[]}""").build())
        server.enqueue(MockResponse.Builder().code(200).body("""{"candidates":[{"content":{"parts":[{"text":""}]}}]}""").build())
        repeat(2) {
            try {
                analyzer().analyze(jpeg)
                fail("expected ApiException")
            } catch (e: ApiException) {
                assertEquals("Gemini 응답이 비어 있어요.", e.message)
            }
        }
    }

    @Test
    fun `an answer that is not JSON is reported, not crashed on`() = runTest {
        server.enqueue(reply("죄송하지만 읽을 수 없어요"))
        try {
            analyzer().analyze(jpeg)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("Gemini 응답을 읽지 못했어요.", e.message)
        }
    }

    @Test
    fun `an HTTP error becomes the Korean message with the code and the first 200 characters`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body("e".repeat(250)).build())
        try {
            analyzer().analyze(jpeg)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("Gemini 요청 실패 (400): ${"e".repeat(200)}", e.message)
        }
    }

    @Test
    fun `a missing key fails before any request is made`() = runTest {
        try {
            analyzer(geminiKey = "").analyze(jpeg)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertTrue(e.message!!.contains("GEMINI_API_KEY"))
        }
        assertEquals(0, server.requestCount)
    }
}
