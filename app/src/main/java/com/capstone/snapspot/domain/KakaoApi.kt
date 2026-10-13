package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.AddressDoc
import com.capstone.snapspot.domain.model.KakaoDoc

/**
 * The two Kakao Local calls the app needs. The network client implements this (data layer); the
 * domain only sees the interface, so tests use a fake. Implementations throw on a failed request.
 */
interface KakaoLocalApi {
    /** Keyword search. [x]/[y] (longitude/latitude) with [radius] meters bias results to an area. */
    suspend fun keyword(
        query: String,
        size: Int,
        x: Double? = null,
        y: Double? = null,
        radius: Int? = null,
        sort: String? = null,
        page: Int = 1,
    ): List<KakaoDoc>

    suspend fun address(query: String, size: Int): List<AddressDoc>
}
