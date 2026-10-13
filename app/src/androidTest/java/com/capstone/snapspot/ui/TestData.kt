package com.capstone.snapspot.ui

import com.capstone.snapspot.domain.brandKey
import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.WindowState
import java.time.LocalDateTime
import java.time.ZoneId

/** A fixed "now" in the phone's own time zone, so day counts do not depend on where the tests run. */
val NOW: Long = LocalDateTime.of(2026, 10, 10, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

val HERE = Coords(37.5446, 127.0565)

fun place(
    id: String = "p1",
    name: String = "어니언 성수",
    lat: Double = 37.5460, // about 160 m from HERE
    radius: Int = 200,
    address: String = "서울 성동구 아차산로9길 8",
    note: String = "소금빵 맛집",
    endDate: String? = null,
    placeUrl: String? = "http://place.map.kakao.com/1",
    lastNotifiedAt: Long? = null,
) = Place(
    id = id, name = name, address = address, category = "카페", note = note, endDate = endDate, latitude = lat, longitude = 127.0565,
    radius = radius, imageUri = null, kakaoId = "1", placeUrl = placeUrl, createdAt = 0, lastNotifiedAt = lastNotifiedAt,
)

fun coupon(
    id: String = "c1",
    brand: String = "스타벅스",
    benefit: String = "아메리카노 Tall",
    expiresAt: String? = "2026-10-31",
    usedAt: Long? = null,
) = Coupon(id, brand, benefit, expiresAt, imageUri = null, createdAt = 0, usedAt = usedAt, lastNotifiedAt = null)

fun branchRef(name: String, lat: Double, brand: String = "스타벅스") = BranchRef(name, brandKey(brand), lat, 127.0565)

fun windowState(
    vararg branches: Pair<String, BranchRef>,
    byGroup: Map<String, Int> = emptyMap(),
    here: Coords? = HERE,
    at: Long = NOW - 5 * 60_000,
    requests: Int = 0,
) = WindowState(at = at, here = here, slots = 90, byGroup = byGroup, branches = mapOf(*branches), requests = requests)
