package com.capstone.snapspot.data.db

import com.capstone.snapspot.domain.EventKind
import com.capstone.snapspot.domain.LogEvent
import com.capstone.snapspot.domain.model.Branch
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    @Test
    fun `a place survives the trip to the database and back`() {
        val place = Place(
            id = "p_1", name = "어니언 성수", address = "서울 성동구 아차산로9길 8", category = "카페", note = "소금빵 맛집",
            endDate = "2026-10-31", latitude = 37.5447, longitude = 127.0582, radius = 200, imageUri = "/data/x.jpg",
            kakaoId = "12345", placeUrl = "http://place.map.kakao.com/12345", createdAt = 1_700_000_000_000, lastNotifiedAt = 99,
        )
        assertEquals(place, place.toEntity().toDomain())
        val bare = place.copy(endDate = null, imageUri = null, kakaoId = null, placeUrl = null, lastNotifiedAt = null)
        assertEquals(bare, bare.toEntity().toDomain())
    }

    @Test
    fun `a coupon survives the trip to the database and back`() {
        val coupon = Coupon("c_1", "스타벅스", "아메리카노 Tall", "2026-12-31", "/data/c.png", 5, 7, 8)
        assertEquals(coupon, coupon.toEntity().toDomain())
        val bare = Coupon("c_2", "올리브영", "쿠폰", null, null, 5, null, null)
        assertEquals(bare, bare.toEntity().toDomain())
    }

    @Test
    fun `an event keeps its kind, id and detail values`() {
        val event = LogEvent(
            t = 1_700_000_000_123,
            kind = EventKind.NOTIFY_SHOWN,
            id = "b|스타벅스|42",
            detail = mapOf("type" to "branch", "forced" to false, "branch" to null, "requests" to 3L, "ratio" to 0.5),
        )
        assertEquals(event, event.toEntity().toDomain())
        assertEquals(EventKind.NOTIFY_SHOWN.wire, event.toEntity().kind)
    }

    @Test
    fun `an event without detail has no detail column`() {
        val e = LogEvent(1, EventKind.REPLAN).toEntity()
        assertNull(e.detailJson)
        assertNull(e.toDomain()!!.detail)
    }

    @Test
    fun `a row of a kind this version does not know is skipped`() {
        assertNull(EventEntity(t = 1, kind = "from_the_future", refId = null, detailJson = null).toDomain())
    }

    @Test
    fun `an unreadable detail column is dropped, not fatal`() {
        val e = EventEntity(t = 1, kind = "replan", refId = null, detailJson = "{broken").toDomain()!!
        assertNull(e.detail)
    }

    @Test
    fun `branches survive JSON, and corrupt JSON is a cache miss`() {
        val branches = listOf(Branch(37.5445, 127.0553, "26338954", "스타벅스 성수역점", "서울 성동구 아차산로 100"))
        assertEquals(branches, branchesFromJson(branches.toJson()))
        assertEquals(emptyList<Branch>(), branchesFromJson(emptyList<Branch>().toJson()))
        assertNull(branchesFromJson("not json"))
        assertNull(branchesFromJson("""[{"latitude":"x"}]"""))
    }
}
