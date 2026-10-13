package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewRulesTest {
    private val here = Coords(37.5446, 127.0565)
    private val now = seoulMillis(2026, 10, 10, 12)

    // --- dates ---

    @Test
    fun `dDay says D-n, today, or past`() {
        assertEquals("D-21", dDay(21))
        assertEquals("D-1", dDay(1))
        assertEquals("오늘까지", dDay(0))
        assertEquals("기간 지남", dDay(-1))
        assertEquals("기간 지남", dDay(-40))
        assertNull(dDay(null))
    }

    @Test
    fun `date labels drop leading zeros`() {
        assertEquals("10월 31일까지", endLabel("2026-10-31"))
        assertEquals("1월 5일까지", endLabel("2026-01-05"))
        assertNull(endLabel(null))
        assertEquals("2026년 12월 31일까지", expiresLabel(coupon(expiresAt = "2026-12-31")))
        assertEquals("2026년 3월 9일까지", expiresLabel(coupon(expiresAt = "2026-03-09")))
        assertNull(expiresLabel(coupon()))
    }

    @Test
    fun `a date that is not three parts gives no label instead of failing`() {
        assertNull(endLabel("곧"))
        assertNull(expiresLabel(coupon(expiresAt = "2026/12/31")))
    }

    // --- branches ---

    private fun win(vararg refs: Pair<String, BranchRef>, here: Coords? = this.here, byGroup: Map<String, Int> = emptyMap()) =
        WindowState(at = now, here = here, slots = 90, byGroup = byGroup, branches = mapOf(*refs), requests = 0)

    private fun ref(name: String, lat: Double, brand: String = "스타벅스") = BranchRef(name, brandKey(brand), lat, 127.0565)

    @Test
    fun `branches of the coupon's brand come back nearest first, others left out`() {
        val w = win(
            "b|스타벅스|1" to ref("먼점", 37.5546),
            "b|스타벅스|2" to ref("가까운점", 37.5448),
            "b|올리브영|3" to ref("올리브영 가까운점", 37.5447, "올리브영"),
            "b|스타벅스|4" to ref("중간점", 37.5476),
        )
        val list = branchesOf(coupon(), w, here)
        assertEquals(listOf("가까운점", "중간점", "먼점"), list.map { it.name })
        assertEquals("가까운점", nearestBranch(coupon(), w, here)!!.name)
        assertEquals(2, branchesOf(coupon(), w, here, limit = 2).size)
    }

    @Test
    fun `a brand spelled with spaces still finds its branches`() {
        val w = win("b|스타벅스|1" to ref("성수점", 37.5448))
        assertEquals(1, branchesOf(coupon(brand = "스타 벅스"), w, here).size)
    }

    @Test
    fun `no plan or no position means no branches`() {
        assertTrue(branchesOf(coupon(), null, here).isEmpty())
        assertTrue(branchesOf(coupon(), win("b|스타벅스|1" to ref("성수점", 37.5448)), null).isEmpty())
        assertNull(nearestBranch(coupon(), null, here))
    }

    @Test
    fun `near means inside the radius, edge included`() {
        assertTrue(isNear(200.0, 200))
        assertTrue(isNear(0.0, 100))
        assertFalse(isNear(200.1, 200))
        assertFalse(isNear(null, 200))
    }

    // --- places ---

    private fun place(id: String, lat: Double, radius: Int = 200, endDate: String? = null, last: Long? = null) = Place(
        id = id, name = id, address = "", category = "카페", note = "", endDate = endDate, latitude = lat, longitude = 127.0565,
        radius = radius, imageUri = null, createdAt = 0, lastNotifiedAt = last,
    )

    @Test
    fun `places sort nearest first with a position, and keep their order without one`() {
        val list = listOf(place("far", 37.6), place("near", 37.5447), place("mid", 37.56))
        assertEquals(listOf("near", "mid", "far"), sortedByDistance(list, here).map { it.id })
        assertEquals(listOf("far", "near", "mid"), sortedByDistance(list, null).map { it.id })
    }

    @Test
    fun `place facts list the radius, the end date and why it would stay quiet`() {
        assertEquals(listOf("반경 300m"), placeFacts(place("a", 37.5, radius = 300), now, SEOUL))
        assertEquals(listOf("반경 200m", "10월 31일까지"), placeFacts(place("a", 37.5, endDate = "2026-10-31"), now, SEOUL))
        assertEquals(listOf("반경 200m", "10월 9일까지", "운영 종료"), placeFacts(place("a", 37.5, endDate = "2026-10-09"), now, SEOUL))
        assertEquals(listOf("반경 200m", "최근에 알림"), placeFacts(place("a", 37.5, last = now - 1000), now, SEOUL))
    }

    // --- coupons ---

    @Test
    fun `coupons split into active, soonest first, and past`() {
        val list = listOf(
            coupon(id = "late", expiresAt = "2026-12-01"),
            coupon(id = "soon", expiresAt = "2026-10-20"),
            coupon(id = "none"),
            coupon(id = "expired", expiresAt = "2026-10-01"),
            coupon(id = "used", usedAt = 1),
        )
        val (active, past) = splitCoupons(list, now, SEOUL)
        assertEquals(listOf("soon", "late", "none"), active.map { it.id })
        assertEquals(setOf("expired", "used"), past.map { it.id }.toSet())
    }

    // --- status ---

    @Test
    fun `ago rounds to minutes then hours`() {
        assertEquals("방금", ago(now - 10_000, now))
        assertEquals("방금", ago(now - 29_000, now))
        assertEquals("1분 전", ago(now - 31_000, now))
        assertEquals("12분 전", ago(now - 12 * 60_000, now))
        assertEquals("59분 전", ago(now - 59 * 60_000, now))
        assertEquals("1시간 전", ago(now - 60 * 60_000, now))
        assertEquals("3시간 전", ago(now - 3 * 3_600_000, now))
    }

    @Test
    fun `alerts need location, background location and notifications`() {
        assertTrue(AlertPermissions(notif = true, fg = true, bg = true).alertsOn)
        assertFalse(AlertPermissions(notif = false, fg = true, bg = true).alertsOn)
        assertFalse(AlertPermissions(notif = true, fg = false, bg = false).alertsOn)
        assertTrue(AlertPermissions(notif = true, fg = true, bg = false).missingAny)
    }

    @Test
    fun `slot parts name the places, each brand and the refresh circle`() {
        val w = win(here = here, byGroup = mapOf("place" to 2, "스타벅스" to 30, "올리브영" to 10))
        val parts = slotParts(w, mapOf("스타벅스" to "스타벅스", "올리브영" to "올리브영"))
        assertEquals(
            listOf("저장한 장소" to 2, "스타벅스 지점" to 30, "올리브영 지점" to 10, "갱신 구역 (반경 1km)" to 1),
            parts.map { it.label to it.count },
        )
        assertEquals(listOf(SlotKind.PLACE, SlotKind.BRAND, SlotKind.BRAND, SlotKind.REFRESH), parts.map { it.kind })
        assertEquals(listOf(0, 1), parts.filter { it.kind == SlotKind.BRAND }.map { it.brandIndex })
        assertEquals(43, parts.sumOf { it.count })
    }

    @Test
    fun `slot parts without a plan show only an empty places row`() {
        assertEquals(listOf(SlotPart("저장한 장소", 0, SlotKind.PLACE)), slotParts(null, emptyMap()))
    }

    @Test
    fun `without a known position there is no refresh circle, and an unknown brand shows its key`() {
        val w = win(here = null, byGroup = mapOf("place" to 1, "메가커피" to 3))
        val parts = slotParts(w, emptyMap())
        assertEquals(listOf("저장한 장소", "메가커피 지점"), parts.map { it.label })
    }

    // --- the "알림 테스트" button ---

    @Test
    fun `the test alert of a coupon uses its nearest registered branch`() {
        val w = win(
            "b|스타벅스|1" to ref("먼점", 37.5546),
            "b|스타벅스|2" to ref("가까운점", 37.5448),
            "b|올리브영|3" to ref("올리브영 점", 37.5447, "올리브영"),
        )
        assertEquals("b|스타벅스|2", testBranchId(coupon(), w, here))
    }

    @Test
    fun `without a position the test alert takes any registered branch of the brand`() {
        val w = win("b|스타벅스|1" to ref("먼점", 37.5546), "b|스타벅스|2" to ref("가까운점", 37.5448))
        assertEquals("b|스타벅스|1", testBranchId(coupon(), w, null))
    }

    @Test
    fun `with no registered branch the test alert pretends one, named after the brand's key`() {
        assertEquals("b|스타벅스|test", testBranchId(coupon(), null, here))
        assertEquals("b|스타벅스|test", testBranchId(coupon(), win("b|올리브영|3" to ref("올리브영 점", 37.5447, "올리브영")), here))
    }
}
