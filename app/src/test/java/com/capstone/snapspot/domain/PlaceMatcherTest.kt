package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.PlaceQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMatcherTest {
    private val delta = 1e-9

    @Test
    fun `normalizeName drops spaces, punctuation and a trailing 점`() {
        assertEquals("어니언성수", normalizeName("어니언 성수점"))
        assertEquals("cafeonionseongsu", normalizeName("Cafe ONION (Seongsu)"))
    }

    @Test
    fun `normalizeName also drops full-width spaces like JS does`() {
        assertEquals("어니언성수", normalizeName("어니언　성수점"))
    }

    @Test
    fun `regionHint keeps 구 and 동 tokens only`() {
        assertEquals("성동구", regionHint("서울 성동구 아차산로9길 8"))
        assertEquals("성수동", regionHint("성수동"))
        assertEquals("", regionHint(""))
    }

    @Test
    fun `nameVariants drops trailing words, longest first`() {
        assertEquals(
            listOf("더현대 서울 B1 이벤트홀", "더현대 서울 B1", "더현대 서울", "더현대"),
            nameVariants("더현대 서울 B1 이벤트홀"),
        )
        assertEquals(listOf("어니언"), nameVariants("어니언"))
        assertEquals(emptyList<String>(), nameVariants("  "))
    }

    @Test
    fun `exact name, address and category score near 1`() {
        val s = scoreCandidate(kakaoDoc(), PlaceQuery("어니언 성수", "서울 성동구 아차산로9길 8", "카페"))
        assertTrue("got $s", s > 0.95)
    }

    @Test
    fun `same brand, other branch loses to the right branch via address`() {
        val ex = PlaceQuery("어니언", "서울 성동구 아차산로9길 8", "카페")
        val right = scoreCandidate(kakaoDoc(), ex)
        val wrong = scoreCandidate(
            kakaoDoc(placeName = "어니언 안국", roadAddressName = "서울 종로구 계동길 5", addressName = "서울 종로구 계동 146-1"),
            ex,
        )
        assertTrue("right $right wrong $wrong", right > wrong + 0.2)
    }

    @Test
    fun `venue name contained in a longer extracted name still matches`() {
        val s = scoreCandidate(
            kakaoDoc(placeName = "더현대 서울", categoryGroupCode = "", roadAddressName = "서울 영등포구 여의대로 108"),
            PlaceQuery("더현대 서울 B1 이벤트홀", "서울 영등포구 여의대로 108", "팝업"),
        )
        assertTrue("got $s", s > 0.8)
    }

    @Test
    fun `unrelated place scores low`() {
        val s = scoreCandidate(
            kakaoDoc(placeName = "김밥천국 성수점", categoryGroupCode = "FD6", roadAddressName = "서울 성동구 왕십리로 1"),
            PlaceQuery("어니언 성수", "서울 성동구 아차산로9길 8", "카페"),
        )
        assertTrue("got $s", s < 0.4)
    }

    @Test
    fun `missing address and category does not penalize a name match`() {
        val s = scoreCandidate(kakaoDoc(), PlaceQuery("어니언 성수", "", "기타"))
        assertTrue("got $s", s > 0.9)
    }

    @Test
    fun `address similarity is null without an address and partial when some tokens match`() {
        assertNull(addressSimilarity("", kakaoDoc()))
        assertNull(addressSimilarity("서울 대한민국", kakaoDoc())) // only noise tokens
        assertEquals(2.0 / 3, addressSimilarity("서울 성동구 아차산로9길 99", kakaoDoc())!!, delta)
    }

    // Expected values below were computed by the React Native implementation (src/match.ts).
    @Test
    fun `scores match the React Native implementation`() {
        assertEquals(1.0, nameSimilarity("어니언 성수", "어니언 성수점"), delta)
        assertEquals(0.9, nameSimilarity("어니언", "어니언 안국"), delta)
        assertEquals(0.9, nameSimilarity("더현대 서울 B1 이벤트홀", "더현대 서울"), delta)
        assertEquals(0.4444444444444444, nameSimilarity("메가커피", "메가MGC커피"), delta)
        assertEquals(1.0, nameSimilarity("스타벅스", "스타 벅스"), delta)
        assertEquals(0.9, nameSimilarity("Cafe ONION", "cafe onion (seongsu)"), delta)
        assertEquals(0.9, nameSimilarity("커피빈", "커피"), delta)
        assertEquals(0.9, nameSimilarity("올리브영", "올리브 영 성수"), delta)

        val ex = PlaceQuery("어니언", "서울 성동구 아차산로9길 8", "카페")
        assertEquals(
            0.6400000000000001,
            scoreCandidate(
                kakaoDoc(placeName = "어니언 안국", roadAddressName = "서울 종로구 계동길 5", addressName = "서울 종로구 계동 146-1"),
                ex,
            ),
            delta,
        )
        assertEquals(
            0.9333333333333335,
            scoreCandidate(
                kakaoDoc(placeName = "더현대 서울", categoryGroupCode = "", roadAddressName = "서울 영등포구 여의대로 108"),
                PlaceQuery("더현대 서울 B1 이벤트홀", "서울 영등포구 여의대로 108", "팝업"),
            ),
            delta,
        )
        assertEquals(
            0.23333333333333336,
            scoreCandidate(
                kakaoDoc(placeName = "김밥천국 성수점", categoryGroupCode = "FD6", roadAddressName = "서울 성동구 왕십리로 1"),
                PlaceQuery("어니언 성수", "서울 성동구 아차산로9길 8", "카페"),
            ),
            delta,
        )
    }
}
