package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.KakaoDoc
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocatePlaceTest {
    private val geocoded = Coords(37.0, 127.0)

    private class FakeGeocoder(private val result: Coords?) : GeocodePort {
        var calls = 0
        override suspend fun geocode(name: String, address: String): Coords? {
            calls++
            return result
        }
    }

    private fun onion(): KakaoDoc = kakaoDoc(x = "127.0582", y = "37.5447")

    private val query = "성동구 어니언 성수"

    @Test
    fun `a confident Kakao match places the pin and skips the device geocoder`() = runTest {
        val api = FakeKakao(mapOf(query to listOf(onion())))
        val geo = FakeGeocoder(geocoded)
        val out = locatePlace("어니언 성수", "서울 성동구 아차산로9길 8", "카페", api, geo)
        assertEquals(Coords(37.5447, 127.0582), out.coords)
        assertEquals("어니언 성수", out.matched?.name)
        assertEquals(0, geo.calls)
    }

    @Test
    fun `a weak Kakao match is offered as a choice but the device geocoder places the pin`() = runTest {
        val weak = kakaoDoc(placeName = "김밥천국", categoryGroupCode = "FD6", roadAddressName = "서울 마포구 어딘가 1")
        val api = FakeKakao(mapOf(query to listOf(weak), "어니언 성수" to listOf(weak), "어니언" to listOf(weak)))
        val geo = FakeGeocoder(geocoded)
        val out = locatePlace("어니언 성수", "서울 성동구 아차산로9길 8", "카페", api, geo)
        assertEquals(geocoded, out.coords)
        assertNull(out.matched)
        assertTrue("candidates kept for the user to pick", out.candidates.isNotEmpty())
        assertEquals(1, geo.calls)
    }

    @Test
    fun `without a Kakao key the device geocoder is used`() = runTest {
        val geo = FakeGeocoder(geocoded)
        val out = locatePlace("어니언 성수", "", "카페", api = null, geocoder = geo)
        assertEquals(geocoded, out.coords)
        assertEquals(emptyList<Any>(), out.candidates)
    }

    @Test
    fun `a failed Kakao search falls back to the device geocoder`() = runTest {
        val broken = object : KakaoLocalApi {
            override suspend fun keyword(query: String, size: Int, x: Double?, y: Double?, radius: Int?, sort: String?, page: Int): List<KakaoDoc> =
                error("카카오 검색 실패 (500)")

            override suspend fun address(query: String, size: Int) = emptyList<com.capstone.snapspot.domain.model.AddressDoc>()
        }
        val out = locatePlace("어니언 성수", "서울 성동구", "카페", broken, FakeGeocoder(geocoded))
        assertEquals(geocoded, out.coords)
    }

    @Test
    fun `nothing is found anywhere gives no coordinates`() = runTest {
        val out = locatePlace("없는 곳", "", "기타", FakeKakao(), FakeGeocoder(null))
        assertNull(out.coords)
        assertNull(out.matched)
    }

    @Test
    fun `at most four candidates are returned`() = runTest {
        val docs = List(8) { i -> kakaoDoc(id = "$i", placeName = "어니언 성수 $i") }
        val api = FakeKakao(everyKeyword = docs)
        val out = locatePlace("어니언 성수", "", "카페", api, FakeGeocoder(null))
        assertEquals(4, out.candidates.size)
    }
}
