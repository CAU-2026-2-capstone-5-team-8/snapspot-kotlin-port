package com.capstone.snapspot.data.net

import com.capstone.snapspot.domain.KakaoLocalApi
import com.capstone.snapspot.domain.model.AddressDoc
import com.capstone.snapspot.domain.model.KakaoDoc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import javax.inject.Inject

@Serializable
data class KakaoDocDto(
    val id: String = "",
    @SerialName("place_name") val placeName: String = "",
    @SerialName("category_name") val categoryName: String = "",
    @SerialName("category_group_code") val categoryGroupCode: String = "",
    @SerialName("address_name") val addressName: String = "",
    @SerialName("road_address_name") val roadAddressName: String = "",
    val x: String = "",
    val y: String = "",
    @SerialName("place_url") val placeUrl: String = "",
)

@Serializable
data class KeywordResponse(val documents: List<KakaoDocDto> = emptyList())

@Serializable
data class RoadAddressDto(@SerialName("address_name") val addressName: String = "")

@Serializable
data class AddressDocDto(
    @SerialName("address_name") val addressName: String = "",
    val x: String = "",
    val y: String = "",
    @SerialName("road_address") val roadAddress: RoadAddressDto? = null,
)

@Serializable
data class AddressResponse(val documents: List<AddressDocDto> = emptyList())

interface KakaoService {
    @GET("v2/local/search/keyword.json")
    suspend fun keyword(
        @Header("Authorization") auth: String,
        @Query("query") query: String,
        @Query("size") size: Int,
        @Query("x") x: Double?,
        @Query("y") y: Double?,
        @Query("radius") radius: Int?,
        @Query("sort") sort: String?,
        @Query("page") page: Int,
    ): Response<KeywordResponse>

    @GET("v2/local/search/address.json")
    suspend fun address(
        @Header("Authorization") auth: String,
        @Query("query") query: String,
        @Query("size") size: Int,
    ): Response<AddressResponse>
}

/** Kakao Local over HTTP. Messages match the React Native app's. */
class RetrofitKakaoApi @Inject constructor(
    private val service: KakaoService,
    private val keys: ApiKeys,
) : KakaoLocalApi {

    override suspend fun keyword(
        query: String,
        size: Int,
        x: Double?,
        y: Double?,
        radius: Int?,
        sort: String?,
        page: Int,
    ): List<KakaoDoc> =
        body(service.keyword(auth(), query, size, x, y, radius, sort, page)).documents.map {
            KakaoDoc(
                it.id, it.placeName, it.categoryName, it.categoryGroupCode, it.addressName,
                it.roadAddressName, it.x, it.y, it.placeUrl,
            )
        }

    override suspend fun address(query: String, size: Int): List<AddressDoc> =
        body(service.address(auth(), query, size)).documents.map {
            AddressDoc(it.addressName, it.x, it.y, it.roadAddress?.addressName)
        }

    private fun auth(): String {
        if (keys.kakaoRestKey.isBlank()) {
            throw ApiException("카카오 REST API 키가 없어요. local.properties의 KAKAO_REST_KEY를 설정하세요.")
        }
        return "KakaoAK ${keys.kakaoRestKey}"
    }

    private fun <T> body(res: Response<T>): T {
        if (!res.isSuccessful) {
            val text = res.errorBody()?.string().orEmpty()
            throw ApiException("카카오 검색 실패 (${res.code()}): ${text.take(200)}")
        }
        return res.body() ?: throw ApiException("카카오 검색 실패 (${res.code()}): 응답이 비어 있어요.")
    }
}
