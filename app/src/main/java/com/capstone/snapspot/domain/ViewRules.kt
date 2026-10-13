package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.WindowState
import java.time.ZoneId

/** What the screens say about dates, distances and the geofence plan. Pure, so they are tested without a screen. */

/** "D-21", "오늘까지", "기간 지남", or null without an expiry date. */
fun dDay(days: Long?): String? = when {
    days == null -> null
    days < 0 -> "기간 지남"
    days == 0L -> "오늘까지"
    else -> "D-$days"
}

private fun datePart(s: String): Triple<String, String, String>? {
    val p = s.split("-")
    return if (p.size == 3) Triple(p[0], p[1], p[2]) else null
}

private fun plain(n: String): String = n.toIntOrNull()?.toString() ?: n

/** "10월 31일까지", or null without an end date. */
fun endLabel(endDate: String?): String? {
    val (_, m, d) = datePart(endDate ?: return null) ?: return null
    return "${plain(m)}월 ${plain(d)}일까지"
}

/** "2026년 12월 31일까지", or null without an expiry date. */
fun expiresLabel(c: Coupon): String? {
    val (y, m, d) = datePart(c.expiresAt ?: return null) ?: return null
    return "${y}년 ${plain(m)}월 ${plain(d)}일까지"
}

/** A branch that is registered in the current plan, and how far it is from the user. */
data class BranchDistance(val name: String, val distance: Double)

/** Branches of the coupon's brand in the plan, nearest first. Empty when the plan or the position is unknown. */
fun branchesOf(c: Coupon, win: WindowState?, here: Coords?, limit: Int = Int.MAX_VALUE): List<BranchDistance> {
    if (win == null || here == null) return emptyList()
    val key = brandKey(c.brand)
    return win.branches.values
        .filter { it.brandKey == key }
        .map { BranchDistance(it.name, distanceMeters(here, Coords(it.latitude, it.longitude))) }
        .sortedBy { it.distance }
        .take(limit)
}

fun nearestBranch(c: Coupon, win: WindowState?, here: Coords?): BranchDistance? =
    branchesOf(c, win, here, 1).firstOrNull()

/** Within the alert radius right now. */
fun isNear(distance: Double?, radius: Int): Boolean = distance != null && distance <= radius

/** Nearest first when the position is known, otherwise as given. */
fun sortedByDistance(places: List<Place>, here: Coords?): List<Place> =
    if (here == null) places else places.sortedBy { distanceMeters(here, Coords(it.latitude, it.longitude)) }

/** The small chips on a place card: radius, end date, and why it would not alert right now. */
fun placeFacts(p: Place, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<String> =
    listOfNotNull("반경 ${p.radius}m", endLabel(p.endDate), suppressReason(p, now, zone)?.label)

/** Coupons that still alert, soonest-expiring first; and the rest (used or expired). */
fun splitCoupons(
    coupons: List<Coupon>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<List<Coupon>, List<Coupon>> {
    val (active, past) = coupons.partition { isActive(it, now, zone) }
    return active.sortedBy { it.expiresAt ?: "9999" } to past
}

/** "방금", "12분 전", "3시간 전". */
fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
    val min = Math.round((now - at) / 60_000.0)
    return when {
        min < 1 -> "방금"
        min < 60 -> "${min}분 전"
        else -> "${Math.round(min / 60.0)}시간 전"
    }
}

/** The permissions that decide whether alerts reach the user with the app closed. */
data class AlertPermissions(val notif: Boolean, val fg: Boolean, val bg: Boolean) {
    val alertsOn: Boolean get() = notif && fg && bg
    val missingAny: Boolean get() = !(notif && fg && bg)
}

enum class SlotKind { PLACE, BRAND, REFRESH }

/** One colored segment of the slot meter. [brandIndex] picks the brand's shade of ink. */
data class SlotPart(val label: String, val count: Int, val kind: SlotKind, val brandIndex: Int = 0)

private fun km(radiusM: Int): String {
    val k = radiusM / 1000.0
    return if (k == Math.floor(k)) k.toInt().toString() else k.toString()
}

/**
 * How the OS geofence slots are shared right now: saved places, each coupon brand, and the refresh circle
 * (which also takes a slot). Brands are named as written on the coupon.
 */
fun slotParts(win: WindowState?, brandNames: Map<String, String>, refreshRadiusM: Int = REFRESH_RADIUS_M): List<SlotPart> {
    val groups = win?.byGroup.orEmpty()
    val parts = mutableListOf(SlotPart("저장한 장소", groups[PLACE_GROUP] ?: 0, SlotKind.PLACE))
    var i = 0
    for ((g, n) in groups) {
        if (g == PLACE_GROUP) continue
        parts += SlotPart("${brandNames[g] ?: g} 지점", n, SlotKind.BRAND, i++)
    }
    if (win?.here != null) parts += SlotPart("갱신 구역 (반경 ${km(refreshRadiusM)}km)", 1, SlotKind.REFRESH)
    return parts
}

/**
 * The region id the "알림 테스트" button of a coupon pretends was entered: the nearest registered branch of its
 * brand, or a made-up one when none is registered (the alert then names the brand instead of a branch).
 */
fun testBranchId(c: Coupon, win: WindowState?, here: Coords?): String {
    val key = brandKey(c.brand)
    val own = win?.branches.orEmpty().filter { (id, b) -> id.startsWith("b|$key|") && b.brandKey == key }
    val nearest = if (here == null) own.keys.firstOrNull() else own.minByOrNull { (_, b) -> distanceMeters(here, Coords(b.latitude, b.longitude)) }?.key
    return nearest ?: "b|$key|test"
}
