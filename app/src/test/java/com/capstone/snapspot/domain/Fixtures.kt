package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.AddressDoc
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.KakaoDoc
import java.time.LocalDateTime
import java.time.ZoneId

/** Tests pin the zone so end-of-day rules do not depend on the machine running them. */
val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

fun seoulMillis(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0): Long =
    LocalDateTime.of(y, mo, d, h, mi).atZone(SEOUL).toInstant().toEpochMilli()

fun kakaoDoc(
    id: String = "1",
    placeName: String = "어니언 성수",
    categoryName: String = "음식점 > 카페",
    categoryGroupCode: String = "CE7",
    addressName: String = "서울 성동구 성수동2가 277-135",
    roadAddressName: String = "서울 성동구 아차산로9길 8",
    x: String = "127.0582",
    y: String = "37.5447",
    placeUrl: String = "http://place.map.kakao.com/1",
) = KakaoDoc(id, placeName, categoryName, categoryGroupCode, addressName, roadAddressName, x, y, placeUrl)

fun coupon(
    id: String = "c",
    brand: String = "스타벅스",
    benefit: String = "아메리카노",
    expiresAt: String? = null,
    usedAt: Long? = null,
    lastNotifiedAt: Long? = null,
) = Coupon(id, brand, benefit, expiresAt, imageUri = null, createdAt = 0, usedAt = usedAt, lastNotifiedAt = lastNotifiedAt)

/** Fake Kakao: answers keyword searches by exact query text and records what was asked. */
class FakeKakao(
    private val byQuery: Map<String, List<KakaoDoc>> = emptyMap(),
    private val everyKeyword: List<KakaoDoc>? = null,
) : KakaoLocalApi {
    val asked = mutableListOf<String>()
    var keywordCalls = 0
        private set

    override suspend fun keyword(
        query: String,
        size: Int,
        x: Double?,
        y: Double?,
        radius: Int?,
        sort: String?,
        page: Int,
    ): List<KakaoDoc> {
        keywordCalls++
        asked += "kw:$query"
        return everyKeyword ?: byQuery[query].orEmpty()
    }

    override suspend fun address(query: String, size: Int): List<AddressDoc> {
        asked += "addr:$query"
        return emptyList()
    }
}
