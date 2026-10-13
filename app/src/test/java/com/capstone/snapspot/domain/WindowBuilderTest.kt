package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.AddressDoc
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.KakaoDoc
import com.capstone.snapspot.domain.model.Place
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowBuilderTest {
    private val here = Coords(37.5446, 127.0565)
    private val now = seoulMillis(2026, 10, 10, 12)

    private fun place(id: String, endDate: String? = null) = Place(
        id = id, name = id, address = "", category = "카페", note = "", endDate = endDate,
        latitude = 37.5450, longitude = 127.0570, radius = 200, imageUri = null, createdAt = 0, lastNotifiedAt = null,
    )

    /** [n] branches of [brand], going east from `here`, filed under the brand's own category. */
    private fun branches(brand: String, n: Int): List<KakaoDoc> = List(n) { i ->
        kakaoDoc(
            id = "$brand$i", placeName = "$brand ${i}호점", categoryName = "음식점 > 카페 > 커피전문점 > $brand",
            categoryGroupCode = "", x = (127.0565 + (i + 1) * 0.001).toString(), y = "37.5446",
        )
    }

    private suspend fun build(
        places: List<Place> = emptyList(),
        coupons: List<com.capstone.snapspot.domain.model.Coupon> = emptyList(),
        api: KakaoLocalApi = FakeKakao(),
        at: Coords? = here,
        max: Int = MAX_REGIONS,
    ) = buildWindow(places, coupons, at, api, memoryCache(), now, max, SEOUL)

    @Test
    fun `places and brand branches share the slots, with a refresh region on top`() = runTest {
        val api = FakeKakao(mapOf("스타벅스" to branches("스타벅스", 5), "올리브영" to branches("올리브영", 5)))
        val out = build(
            places = listOf(place("p1"), place("p2")),
            coupons = listOf(coupon(brand = "스타벅스"), coupon(id = "o", brand = "올리브영")),
            api = api,
            max = 7,
        )
        assertEquals(7, out.plan.regions.size)
        assertTrue(out.plan.regions.any { it.identifier == REFRESH_ID })
        // 6 usable slots over 3 groups, 2 each.
        assertEquals(mapOf("place" to 2, "스타벅스" to 2, "올리브영" to 2), out.state.byGroup)
        assertEquals(6, out.summary.active)
        assertEquals(2, out.summary.requests)
    }

    @Test
    fun `an ended place is left out, an open one is kept`() = runTest {
        val out = build(places = listOf(place("old", "2026-10-09"), place("open", "2026-10-10"), place("none")))
        val ids = out.plan.regions.map { it.identifier }
        assertFalse("old" in ids)
        assertTrue("open" in ids && "none" in ids)
    }

    @Test
    fun `expired and used coupons do not look up branches`() = runTest {
        val api = FakeKakao(everyKeyword = branches("스타벅스", 3))
        val out = build(
            coupons = listOf(coupon(id = "x", expiresAt = "2026-10-09"), coupon(id = "u", usedAt = 1)),
            api = api,
        )
        assertEquals(0, api.keywordCalls)
        assertEquals(emptyMap<String, Int>(), out.state.byGroup)
    }

    @Test
    fun `two coupons of one brand look the brand up once, even spelled differently`() = runTest {
        val api = FakeKakao(mapOf("스타벅스" to branches("스타벅스", 2), "스타 벅스" to branches("스타벅스", 2)))
        val out = build(coupons = listOf(coupon(id = "a", brand = "스타벅스"), coupon(id = "b", brand = "스타 벅스")), api = api)
        assertEquals(1, api.keywordCalls)
        assertEquals(2, out.summary.active)
    }

    @Test
    fun `a brand whose lookup fails is skipped and the others still get slots`() = runTest {
        val good = FakeKakao(mapOf("올리브영" to branches("올리브영", 3)))
        val api = object : KakaoLocalApi {
            override suspend fun keyword(query: String, size: Int, x: Double?, y: Double?, radius: Int?, sort: String?, page: Int): List<KakaoDoc> {
                if (query == "스타벅스") error("카카오 검색 실패 (500)")
                return good.keyword(query, size, x, y, radius, sort, page)
            }

            override suspend fun address(query: String, size: Int) = emptyList<AddressDoc>()
        }
        val out = build(coupons = listOf(coupon(brand = "스타벅스"), coupon(id = "o", brand = "올리브영")), api = api)
        assertEquals(mapOf("올리브영" to 3), out.state.byGroup)
    }

    @Test
    fun `without a position there is no refresh region and no branch lookup`() = runTest {
        val api = FakeKakao(everyKeyword = branches("스타벅스", 3))
        val out = build(places = listOf(place("p1")), coupons = listOf(coupon()), api = api, at = null)
        assertEquals(0, api.keywordCalls)
        assertEquals(listOf("p1"), out.plan.regions.map { it.identifier })
        assertNull(out.state.here)
    }

    @Test
    fun `only branches that got a slot are remembered, under their branch region id`() = runTest {
        val api = FakeKakao(mapOf("스타벅스" to branches("스타벅스", 10)))
        val out = build(coupons = listOf(coupon()), api = api, max = 4) // refresh + 3 branches
        assertEquals(3, out.state.branches.size)
        assertTrue(out.state.branches.keys.all { it.startsWith("b|스타벅스|") && isBranchRegion(it) })
        assertEquals(out.plan.regions.map { it.identifier }.filter { it != REFRESH_ID }.toSet(), out.state.branches.keys)
        assertEquals("스타벅스", out.state.branches.values.first().brandKey)
        assertEquals(4, out.state.slots)
    }

    @Test
    fun `branch regions use the 200 m alert radius and the refresh region 1000 m`() = runTest {
        val api = FakeKakao(mapOf("스타벅스" to branches("스타벅스", 2)))
        val out = build(coupons = listOf(coupon()), api = api)
        val refresh = out.plan.regions.single { it.identifier == REFRESH_ID }
        assertEquals(REFRESH_RADIUS_M, refresh.radius)
        assertTrue(out.plan.regions.filter { it.identifier != REFRESH_ID }.all { it.radius == BRANCH_RADIUS_M })
    }
}
