package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.AddressDoc
import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.KakaoDoc
import com.capstone.snapspot.domain.model.Place
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBranchesTest {
    private val now = seoulMillis(2026, 10, 13, 12)
    private val here = Coords(37.5446, 127.0565)

    private fun ref(name: String, brand: String = "스타벅스") = BranchRef(name, brandKey(brand), 37.54, 127.05)

    private val fromWindow = mapOf("b|${brandKey("스타벅스")}|w1" to ref("스타벅스 성수점"))
    private val fromMap = mapOf(
        "b|${brandKey("스타벅스")}|m1" to ref("스타벅스 뚝섬점"),
        "b|${brandKey("올리브영")}|o1" to ref("올리브영 성수점", "올리브영"),
    )

    private fun visible(vararg coupons: com.capstone.snapspot.domain.model.Coupon) =
        visibleBranches(fromMap, fromWindow, coupons.toList(), now, SEOUL)

    // --- visibleBranches: a badge stays only while its brand has an active coupon -------------------------------

    @Test
    fun `badges of a brand with an active coupon stay, from both sources`() {
        val v = visible(coupon(id = "c1", brand = "스타벅스"))
        assertEquals(setOf("스타벅스 성수점", "스타벅스 뚝섬점"), v.values.map { it.name }.toSet())
    }

    @Test
    fun `a brand without any coupon has no badge`() {
        val v = visible(coupon(id = "c1", brand = "스타벅스"))
        assertTrue(v.values.none { it.brandKey == brandKey("올리브영") })
    }

    @Test
    fun `using the last coupon of a brand removes its badges`() {
        assertTrue(visible(coupon(id = "c1", brand = "스타벅스", usedAt = 1L)).isEmpty())
    }

    @Test
    fun `an expired coupon alone does not keep a badge`() {
        assertTrue(visible(coupon(id = "c1", brand = "스타벅스", expiresAt = "2026-10-12")).isEmpty())
    }

    @Test
    fun `a brand with only used and expired coupons has no badge, while another brand's stay`() {
        val v = visible(
            coupon(id = "c1", brand = "스타벅스", usedAt = 1L),
            coupon(id = "c2", brand = "스타벅스", expiresAt = "2026-09-30"),
            coupon(id = "c3", brand = "올리브영"),
        )
        assertEquals(listOf("올리브영 성수점"), v.values.map { it.name })
    }

    @Test
    fun `an expiring coupon still counts through its last day`() {
        assertEquals(2, visible(coupon(id = "c1", brand = "스타벅스", expiresAt = "2026-10-13")).size)
    }

    @Test
    fun `deleting every coupon leaves no badge`() {
        assertTrue(visible().isEmpty())
    }

    @Test
    fun `one unused coupon among used ones is enough`() {
        val v = visible(coupon(id = "c1", brand = "스타벅스", usedAt = 1L), coupon(id = "c2", brand = "스타벅스"))
        assertEquals(2, v.size)
    }

    @Test
    fun `a branch known to both sources is drawn once`() {
        val id = "b|${brandKey("스타벅스")}|same"
        val v = visibleBranches(mapOf(id to ref("지도 쪽")), mapOf(id to ref("계획 쪽")), listOf(coupon()), now, SEOUL)
        assertEquals(1, v.size)
    }

    // --- mapPlaces: ended places are not on the map ----------------------------------------------------------------

    private fun place(id: String, endDate: String? = null, last: Long? = null) = Place(
        id = id, name = id, address = "", category = "카페", note = "", endDate = endDate,
        latitude = 37.5, longitude = 127.0, radius = 200, imageUri = null, createdAt = 0, lastNotifiedAt = last,
    )

    @Test
    fun `an ended place is left off the map`() {
        val shown = mapPlaces(listOf(place("open"), place("ended", endDate = "2026-10-12"), place("today", endDate = "2026-10-13")), now, SEOUL)
        assertEquals(listOf("open", "today"), shown.map { it.id })
    }

    @Test
    fun `a place that only alerted recently stays on the map`() {
        assertEquals(listOf("recent"), mapPlaces(listOf(place("recent", last = now - 1000)), now, SEOUL).map { it.id })
    }

    // --- loadMapBranches ----------------------------------------------------------------------------------------------

    private val starbucks = listOf(
        KakaoDoc("1", "스타벅스 성수점", "음식점 > 카페 > 커피전문점 > 스타벅스", "", "", "서울 성동구", "127.05", "37.54", ""),
        KakaoDoc("2", "스타벅스 뚝섬점", "음식점 > 카페 > 커피전문점 > 스타벅스", "", "", "서울 성동구", "127.06", "37.55", ""),
    )

    @Test
    fun `looks up each brand with an active coupon once and ids them like the watch plan`() = runTest {
        val api = FakeKakao(byQuery = mapOf("스타벅스" to starbucks))
        val r = loadMapBranches(
            listOf(coupon(id = "a", brand = "스타벅스"), coupon(id = "b", brand = "스타벅스"), coupon(id = "c", brand = "스타벅스", usedAt = 1L)),
            here, api, memoryCache(), now, SEOUL,
        )
        assertEquals(1, api.keywordCalls)
        assertEquals(setOf("b|${brandKey("스타벅스")}|1", "b|${brandKey("스타벅스")}|2"), r.branches.keys)
        assertEquals(setOf(brandKey("스타벅스")), r.brandKeys)
    }

    @Test
    fun `a second lookup in the same area is served from the cache`() = runTest {
        val api = FakeKakao(byQuery = mapOf("스타벅스" to starbucks))
        val cache = memoryCache()
        loadMapBranches(listOf(coupon()), here, api, cache, now, SEOUL)
        val again = loadMapBranches(listOf(coupon()), here, api, cache, now, SEOUL)
        assertEquals(1, api.keywordCalls)
        assertEquals(0, again.requests)
        assertEquals(2, again.branches.size)
    }

    @Test
    fun `a brand whose lookup fails is skipped and the others still load`() = runTest {
        val api = object : KakaoLocalApi {
            override suspend fun keyword(query: String, size: Int, x: Double?, y: Double?, radius: Int?, sort: String?, page: Int): List<KakaoDoc> =
                if (query == "올리브영") throw IllegalStateException("boom") else starbucks

            override suspend fun address(query: String, size: Int): List<AddressDoc> = emptyList()
        }
        val r = loadMapBranches(listOf(coupon(id = "a", brand = "올리브영"), coupon(id = "b", brand = "스타벅스")), here, api, memoryCache(), now, SEOUL)
        assertEquals(2, r.branches.size)
        assertTrue(r.branches.values.all { it.brandKey == brandKey("스타벅스") })
    }

    @Test
    fun `without an active coupon nothing is looked up`() = runTest {
        val api = FakeKakao(byQuery = mapOf("스타벅스" to starbucks))
        val r = loadMapBranches(listOf(coupon(usedAt = 1L)), here, api, memoryCache(), now, SEOUL)
        assertEquals(0, api.keywordCalls)
        assertTrue(r.branches.isEmpty())
    }

    // --- area and zoom -----------------------------------------------------------------------------------------------

    @Test
    fun `leaving the loaded area is judged by distance from where the lookup was made`() {
        val east = Coords(here.latitude, here.longitude + 0.01) // about 0.9 km
        val farEast = Coords(here.latitude, here.longitude + 0.03) // about 2.7 km
        assertFalse(leftLoadedArea(east, here))
        assertTrue(leftLoadedArea(farEast, here))
        assertTrue(leftLoadedArea(east, here, radiusM = 500))
    }

    @Test
    fun `a map zoomed out too far cannot look for branches`() {
        assertEquals(ZoomState.SEARCHABLE, zoomState(MIN_SEARCH_ZOOM))
        assertEquals(ZoomState.SEARCHABLE, zoomState(15))
        assertEquals(ZoomState.TOO_FAR, zoomState(MIN_SEARCH_ZOOM - 1))
    }
}
