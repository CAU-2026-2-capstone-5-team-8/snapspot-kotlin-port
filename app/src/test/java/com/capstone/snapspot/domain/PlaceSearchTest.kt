package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.AddressDoc
import com.capstone.snapspot.domain.model.CandidateSource
import com.capstone.snapspot.domain.model.PlaceQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchTest {
    private fun doc(id: String, name: String, road: String, code: String = "") = kakaoDoc(
        id = id, placeName = name, categoryName = "", categoryGroupCode = code,
        addressName = road, roadAddressName = road, x = "126.9", y = "37.5",
        placeUrl = "http://place.map.kakao.com/$id",
    )

    @Test
    fun `keeps shortening past noisy hits until a confident match (더현대 서울 B1 regression)`() = runTest {
        val api = FakeKakao(
            mapOf(
                // Real Kakao behavior on 2026-10-10: "B1" matches unrelated places.
                "더현대 서울 B1" to listOf(
                    doc("9", "페어몬트앰배서더서울웨딩", "서울 영등포구 여의대로 108"),
                    doc("8", "B1 홍대", "서울 마포구 와우산로 1"),
                ),
                "영등포구 더현대 서울" to listOf(doc("1", "더현대 서울", "서울 영등포구 여의대로 108")),
            ),
        )
        val out = searchPlace(PlaceQuery("더현대 서울 B1 이벤트홀", "서울 영등포구 여의대로 108", "팝업"), api)
        assertEquals("더현대 서울", out[0].name)
        assertTrue("score ${out[0].score}", out[0].score >= 0.7)
        assertFalse("should stop once confident", "kw:더현대" in api.asked)
    }

    @Test
    fun `stops at the full name when it already matches`() = runTest {
        val api = FakeKakao(mapOf("성동구 어니언 성수" to listOf(doc("1", "어니언 성수", "서울 성동구 아차산로9길 8", "CE7"))))
        val out = searchPlace(PlaceQuery("어니언 성수", "서울 성동구 아차산로9길 8", "카페"), api)
        assertEquals("어니언 성수", out[0].name)
        assertEquals(listOf("kw:성동구 어니언 성수", "kw:어니언 성수"), api.asked.filter { it.startsWith("kw:") })
    }

    @Test
    fun `an exact address becomes a 0·65 candidate even when the shop is not listed`() = runTest {
        val api = object : KakaoLocalApi by FakeKakao() {
            override suspend fun address(query: String, size: Int) =
                listOf(AddressDoc("서울 성동구 성수동2가 277-135", "127.05", "37.54", "서울 성동구 아차산로9길 8"))
        }
        val out = searchPlace(PlaceQuery("없는 가게", "서울 성동구 아차산로9길 8", "카페"), api)
        assertEquals(1, out.size)
        assertEquals(CandidateSource.ADDRESS, out[0].source)
        assertEquals(0.65, out[0].score, 0.0)
        assertEquals("없는 가게", out[0].name)
        assertEquals("서울 성동구 아차산로9길 8", out[0].address)
        assertEquals(null, out[0].kakaoId)
    }

    @Test
    fun `no address means the address search is skipped`() = runTest {
        val api = FakeKakao()
        searchPlace(PlaceQuery("어니언", "", "카페"), api)
        assertTrue(api.asked.none { it.startsWith("addr:") })
    }
}
