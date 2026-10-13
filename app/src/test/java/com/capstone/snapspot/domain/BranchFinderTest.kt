package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.KakaoDoc
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BranchFinderTest {
    private val here = Coords(37.5446, 127.0565)

    private fun docs(vararg nameAndCat: Pair<String, String>): List<KakaoDoc> =
        nameAndCat.mapIndexed { i, (name, cat) ->
            kakaoDoc(
                id = i.toString(), placeName = name, categoryName = cat, categoryGroupCode = "",
                addressName = "", roadAddressName = "서울 성동구", x = "127.05", y = "37.54", placeUrl = "",
            )
        }

    @Test
    fun `filters to real branches`() = runTest {
        val api = FakeKakao(
            everyKeyword = docs(
                "올리브영 성수역점" to "가정,생활 > 드럭스토어 > 올리브영",
                "하파크리스틴 올리브영N성수점" to "가정,생활 > 패션 > 하파크리스틴",
            ),
        )
        val r = nearbyBranches("올리브영", here, api, memoryCache())
        assertEquals(listOf("올리브영 성수역점"), r.branches.map { it.name })
        assertEquals(1, r.requests)
    }

    @Test
    fun `second lookup in the same cell is served from cache, and a stale entry is refetched`() = runTest {
        val api = FakeKakao(everyKeyword = docs("스타벅스 성수점" to "음식점 > 카페 > 커피전문점 > 스타벅스"))
        val cache = memoryCache()
        nearbyBranches("스타벅스", here, api, cache, now = 1_000)
        val nearby = Coords(here.latitude + 0.0001, here.longitude + 0.0001)
        assertEquals(cellOf(here).key, cellOf(nearby).key)
        val again = nearbyBranches("스타벅스", nearby, api, cache, now = 1_000)
        assertEquals(0, again.requests)
        assertEquals(1, api.keywordCalls)
        val later = nearbyBranches("스타벅스", here, api, cache, now = 1_000 + CACHE_TTL_MS + 1)
        assertEquals(1, later.requests)
    }

    @Test
    fun `stops paging when a page is not full`() = runTest {
        val api = FakeKakao(everyKeyword = docs("스타벅스 성수점" to "음식점 > 카페 > 커피전문점 > 스타벅스"))
        nearbyBranches("스타벅스", here, api, memoryCache())
        assertEquals(1, api.keywordCalls)
    }

    @Test
    fun `reads three full pages at most`() = runTest {
        val full = List(15) { i ->
            kakaoDoc(id = "$i", placeName = "스타벅스 $i", categoryName = "음식점 > 카페 > 커피전문점 > 스타벅스", categoryGroupCode = "")
        }
        val api = FakeKakao(everyKeyword = full)
        val r = nearbyBranches("스타벅스", here, api, memoryCache())
        assertEquals(3, api.keywordCalls)
        assertEquals(3, r.requests)
    }

    @Test
    fun `cells are keyed by a 0·01 degree grid`() {
        val c = cellOf(Coords(37.5446, 127.0565))
        assertEquals("3754:12705", c.key)
        assertEquals(37.545, c.center.latitude, 1e-9)
        assertEquals(127.055, c.center.longitude, 1e-9)
    }
}
