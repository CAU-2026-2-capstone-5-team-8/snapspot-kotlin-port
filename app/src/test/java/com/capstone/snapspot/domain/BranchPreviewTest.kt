package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.AddressDoc
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.KakaoDoc
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BranchPreviewTest {
    private val here = Coords(37.5446, 127.0565)
    private val cat = "음식점 > 카페 > 커피전문점 > 스타벅스"

    private fun at(name: String, lat: Double, lng: Double) = kakaoDoc(
        id = name, placeName = name, categoryName = cat, categoryGroupCode = "", x = lng.toString(), y = lat.toString(),
    )

    @Test
    fun `branches come back nearest first`() = runTest {
        val api = FakeKakao(
            everyKeyword = listOf(
                at("스타벅스 먼점", 37.5546, 127.0565),
                at("스타벅스 가까운점", 37.5448, 127.0565),
                at("스타벅스 중간점", 37.5476, 127.0565),
            ),
        )
        val out = previewBranches("스타벅스", here, api, memoryCache())!!
        assertEquals(listOf("스타벅스 가까운점", "스타벅스 중간점", "스타벅스 먼점"), out.map { it.name })
    }

    @Test
    fun `unknown position or empty brand gives null without calling Kakao`() = runTest {
        val api = FakeKakao()
        assertNull(previewBranches("스타벅스", null, api, memoryCache()))
        assertNull(previewBranches("", here, api, memoryCache()))
        assertEquals(0, api.keywordCalls)
    }

    @Test
    fun `a failed lookup gives null instead of throwing`() = runTest {
        val broken = object : KakaoLocalApi {
            override suspend fun keyword(query: String, size: Int, x: Double?, y: Double?, radius: Int?, sort: String?, page: Int): List<KakaoDoc> =
                error("boom")

            override suspend fun address(query: String, size: Int) = emptyList<AddressDoc>()
        }
        assertNull(previewBranches("스타벅스", here, broken, memoryCache()))
    }
}
