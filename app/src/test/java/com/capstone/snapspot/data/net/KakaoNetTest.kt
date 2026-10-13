package com.capstone.snapspot.data.net

import com.capstone.snapspot.domain.nearbyBranches
import com.capstone.snapspot.domain.memoryCache
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.PlaceQuery
import com.capstone.snapspot.domain.searchPlace
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class KakaoNetTest {
    private lateinit var server: MockWebServer
    private val key = "test-rest-key-123"

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun stop() {
        server.close()
    }

    private fun api(restKey: String = key, client: OkHttpClient = OkHttpClient()) =
        RetrofitKakaoApi(
            buildRetrofit(server.url("/").toString(), client).create(KakaoService::class.java),
            ApiKeys(geminiKey = "", geminiModel = "m", kakaoRestKey = restKey),
        )

    private fun json(body: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json;charset=UTF-8").body(body).build()

    private val docJson = """
        {"meta":{"total_count":1},"documents":[{"id":"26338954","place_name":"스타벅스 성수역점","category_name":"음식점 > 카페 > 커피전문점 > 스타벅스",
        "category_group_code":"CE7","phone":"1522-3232","address_name":"서울 성동구 성수동2가 273-1","road_address_name":"서울 성동구 아차산로 100",
        "x":"127.0553","y":"37.5445","place_url":"http://place.map.kakao.com/26338954","distance":"12"}]}
    """.trimIndent()

    @Test
    fun `keyword search sends the key, UTF-8 Korean query and only the options given`() = runTest {
        server.enqueue(json(docJson))
        api().keyword("스타벅스 성수", size = 3)
        val req = server.takeRequest()
        assertEquals("/v2/local/search/keyword.json", req.url.encodedPath)
        assertEquals("KakaoAK $key", req.headers["Authorization"])
        assertEquals("스타벅스 성수", req.url.queryParameter("query"))
        assertTrue(req.url.encodedQuery!!.contains("query=%EC%8A%A4%ED%83%80%EB%B2%85%EC%8A%A4%20%EC%84%B1%EC%88%98"))
        assertEquals("3", req.url.queryParameter("size"))
        assertEquals("1", req.url.queryParameter("page"))
        for (absent in listOf("x", "y", "radius", "sort")) assertNull("$absent should be omitted", req.url.queryParameter(absent))
    }

    @Test
    fun `area options are sent when given`() = runTest {
        server.enqueue(json(docJson))
        api().keyword("스타벅스", size = 15, x = 127.055, y = 37.545, radius = 2500, sort = "distance", page = 2)
        val url = server.takeRequest().url
        assertEquals("127.055", url.queryParameter("x"))
        assertEquals("37.545", url.queryParameter("y"))
        assertEquals("2500", url.queryParameter("radius"))
        assertEquals("distance", url.queryParameter("sort"))
        assertEquals("2", url.queryParameter("page"))
    }

    @Test
    fun `documents are mapped and unknown fields are ignored`() = runTest {
        server.enqueue(json(docJson))
        val d = api().keyword("스타벅스", size = 3).single()
        assertEquals("26338954", d.id)
        assertEquals("스타벅스 성수역점", d.placeName)
        assertEquals("음식점 > 카페 > 커피전문점 > 스타벅스", d.categoryName)
        assertEquals("CE7", d.categoryGroupCode)
        assertEquals("서울 성동구 아차산로 100", d.roadAddressName)
        assertEquals("127.0553", d.x)
        assertEquals("37.5445", d.y)
        assertEquals("http://place.map.kakao.com/26338954", d.placeUrl)
    }

    @Test
    fun `a response without documents is an empty list`() = runTest {
        server.enqueue(json("""{"meta":{"total_count":0}}"""))
        assertEquals(emptyList<Any>(), api().keyword("없음", size = 3))
    }

    @Test
    fun `address search maps the road address, and null when there is none`() = runTest {
        server.enqueue(json("""{"documents":[{"address_name":"서울 성동구 성수동2가 277-135","x":"127.05","y":"37.54","road_address":{"address_name":"서울 성동구 아차산로9길 8"}}]}"""))
        server.enqueue(json("""{"documents":[{"address_name":"서울 성동구 성수동","x":"127.05","y":"37.54","road_address":null}]}"""))
        val withRoad = api().address("서울 성동구 아차산로9길 8", 1).single()
        assertEquals("서울 성동구 아차산로9길 8", withRoad.roadAddressName)
        assertEquals("/v2/local/search/address.json", server.takeRequest().url.encodedPath)
        assertNull(api().address("성수동", 1).single().roadAddressName)
    }

    @Test
    fun `an HTTP error becomes the Korean message with the code and the first 200 characters`() = runTest {
        val body = "x".repeat(300)
        server.enqueue(MockResponse.Builder().code(403).body(body).build())
        try {
            api().keyword("스타벅스", size = 3)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("카카오 검색 실패 (403): ${"x".repeat(200)}", e.message)
        }
    }

    @Test
    fun `a missing key fails before any request is made`() = runTest {
        try {
            api(restKey = "").keyword("스타벅스", size = 3)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertTrue(e.message!!.contains("KAKAO_REST_KEY"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the key never reaches the log, even when headers are logged`() = runTest {
        val lines = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor(HttpLoggingInterceptor.Level.HEADERS) { lines += it })
            .build()
        server.enqueue(json(docJson))
        api(client = client).keyword("스타벅스", size = 3)
        assertTrue(lines.isNotEmpty())
        assertFalse(lines.any { it.contains(key) })
        assertTrue(lines.any { it.startsWith("Authorization: ") })
    }

    @Test
    fun `searchPlace works end to end over HTTP`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val q = request.url.queryParameter("query")
                return when {
                    request.url.encodedPath.endsWith("address.json") -> json("""{"documents":[]}""")
                    q == "성동구 어니언 성수" -> json(
                        """{"documents":[{"id":"1","place_name":"어니언 성수","category_name":"음식점 > 카페","category_group_code":"CE7",
                        "address_name":"서울 성동구 성수동2가 277-135","road_address_name":"서울 성동구 아차산로9길 8","x":"127.0582","y":"37.5447","place_url":"u"}]}""",
                    )
                    else -> json("""{"documents":[]}""")
                }
            }
        }
        val out = searchPlace(PlaceQuery("어니언 성수", "서울 성동구 아차산로9길 8", "카페"), api())
        assertEquals("어니언 성수", out.first().name)
        assertTrue(out.first().score >= 0.95)
        assertEquals(37.5447, out.first().latitude, 1e-9)
    }

    @Test
    fun `nearbyBranches pages through HTTP and stops on a short page`() = runTest {
        val cat = "음식점 > 카페 > 커피전문점 > 스타벅스"
        fun page(n: Int, from: Int) = json(
            """{"documents":[${(from until from + n).joinToString(",") { """{"id":"$it","place_name":"스타벅스 ${it}호점","category_name":"$cat","category_group_code":"","address_name":"","road_address_name":"서울","x":"127.05","y":"37.54","place_url":""}""" }}]}""",
        )
        server.enqueue(page(15, 0))
        server.enqueue(page(4, 15))
        val r = nearbyBranches("스타벅스", Coords(37.5446, 127.0565), api(), memoryCache())
        assertEquals(19, r.branches.size)
        assertEquals(2, r.requests)
        assertEquals("1", server.takeRequest().url.queryParameter("page"))
        val second = server.takeRequest().url
        assertEquals("2", second.queryParameter("page"))
        assertEquals("2500", second.queryParameter("radius"))
        assertEquals("distance", second.queryParameter("sort"))
    }
}
