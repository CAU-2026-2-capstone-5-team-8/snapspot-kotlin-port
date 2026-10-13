package com.capstone.snapspot.data.net

import com.capstone.snapspot.domain.GeocodePort
import com.capstone.snapspot.domain.locatePlace
import com.capstone.snapspot.domain.model.PlaceQuery
import com.capstone.snapspot.domain.memoryCache
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Kind
import com.capstone.snapspot.domain.nearbyBranches
import com.capstone.snapspot.domain.searchPlace
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Talks to the real Gemini and Kakao servers with the keys from local.properties, so it only runs with
 * `./gradlew :app:testDebugUnitTest -PliveApi=true`. The normal test run skips it. Keys are never printed.
 */
class LiveApiSmokeTest {
    private val live = System.getProperty("live.api") == "true"
    private val keys by lazy {
        ApiKeys(
            geminiKey = System.getProperty("live.gemini.key").orEmpty(),
            geminiModel = System.getProperty("live.gemini.model").orEmpty().ifEmpty { "gemini-3.5-flash-lite" },
            kakaoRestKey = System.getProperty("live.kakao.rest.key").orEmpty(),
        )
    }
    private val client = OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).build()

    private fun gemini() = GeminiAnalyzer(buildRetrofit(GEMINI_BASE_URL, client).create(GeminiService::class.java), keys)

    private fun kakao() = RetrofitKakaoApi(buildRetrofit(KAKAO_BASE_URL, client).create(KakaoService::class.java), keys)

    /** A screenshot-like image with large Korean text, drawn here so no private picture is needed. */
    private fun card(vararg lines: String): ByteArray {
        System.setProperty("java.awt.headless", "true")
        val img = BufferedImage(1000, 140 + lines.size * 110, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, img.width, img.height)
        g.color = Color.BLACK
        g.font = Font("Malgun Gothic", Font.BOLD, 64)
        lines.forEachIndexed { i, line -> g.drawString(line, 50, 130 + i * 110) }
        g.dispose()
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(img, "jpg", out)
            out.toByteArray()
        }
    }

    @Test
    fun geminiReadsACouponImage() = runTest {
        assumeTrue("live tests are off (run with -PliveApi=true)", live)
        assumeTrue("GEMINI_API_KEY is empty", keys.geminiKey.isNotBlank())
        val e = gemini().analyze(card("스타벅스", "아메리카노 Tall 1잔", "유효기간 2026-12-31", "모바일 교환권"))
        println("coupon -> kind=${e.kind} brand='${e.brand}' benefit='${e.benefit}' endDate=${e.endDate}")
        assertEquals(Kind.COUPON, e.kind)
        assertTrue("brand was '${e.brand}'", e.brand.contains("스타벅스"))
        assertEquals("2026-12-31", e.endDate)
    }

    @Test
    fun geminiReadsAPlaceImage() = runTest {
        assumeTrue("live tests are off (run with -PliveApi=true)", live)
        assumeTrue("GEMINI_API_KEY is empty", keys.geminiKey.isNotBlank())
        val e = gemini().analyze(card("어니언 성수", "서울 성동구 아차산로9길 8", "카페 · 베이커리"))
        println("place -> kind=${e.kind} name='${e.name}' address='${e.address}' category='${e.category}'")
        assertEquals(Kind.PLACE, e.kind)
        assertTrue("name was '${e.name}'", e.name.contains("어니언"))
    }

    @Test
    fun kakaoKeywordSearchWorks() = runTest {
        assumeTrue("live tests are off (run with -PliveApi=true)", live)
        assumeTrue("KAKAO_REST_KEY is empty", keys.kakaoRestKey.isNotBlank())
        val docs = kakao().keyword("스타벅스 성수", size = 3)
        println("keyword -> ${docs.map { it.placeName }}")
        assertTrue(docs.isNotEmpty())
        assertTrue(docs.all { it.x.toDoubleOrNull() != null && it.y.toDoubleOrNull() != null })
    }

    @Test
    fun placeSearchFindsOnionSeongsuConfidently() = runTest {
        assumeTrue("live tests are off (run with -PliveApi=true)", live)
        assumeTrue("KAKAO_REST_KEY is empty", keys.kakaoRestKey.isNotBlank())
        val out = searchPlace(PlaceQuery("어니언 성수", "서울 성동구 아차산로9길 8", "카페"), kakao())
        println("searchPlace -> ${out.take(3).map { "${it.name} (${"%.2f".format(it.score)}, ${it.source})" }}")
        assertTrue("top score was ${out.firstOrNull()?.score}", (out.firstOrNull()?.score ?: 0.0) >= 0.7)
        assertTrue(out.first().name.contains("어니언"))
    }

    @Test
    fun locatePlaceUsesKakaoWithoutTheDeviceGeocoder() = runTest {
        assumeTrue("live tests are off (run with -PliveApi=true)", live)
        assumeTrue("KAKAO_REST_KEY is empty", keys.kakaoRestKey.isNotBlank())
        val neverCalled = object : GeocodePort {
            override suspend fun geocode(name: String, address: String): Coords? = error("device geocoder should not be needed")
        }
        val located = locatePlace("어니언 성수", "서울 성동구 아차산로9길 8", "카페", kakao(), neverCalled)
        println("locate -> ${located.coords}")
        val c = located.coords!!
        assertTrue("not in Seongsu: $c", c.latitude in 37.53..37.56 && c.longitude in 127.04..127.07)
    }

    @Test
    fun nearbyBranchesOfAFranchiseAreFound() = runTest {
        assumeTrue("live tests are off (run with -PliveApi=true)", live)
        assumeTrue("KAKAO_REST_KEY is empty", keys.kakaoRestKey.isNotBlank())
        val r = nearbyBranches("스타벅스", Coords(37.5446, 127.0565), kakao(), memoryCache())
        println("branches -> ${r.branches.size} found in ${r.requests} request(s); first: ${r.branches.firstOrNull()?.name}")
        assertTrue("only ${r.branches.size} branches", r.branches.size >= 3)
        assertTrue(r.requests in 1..3)
        assertTrue(r.branches.all { it.name.contains("스타벅스") })
    }
}
