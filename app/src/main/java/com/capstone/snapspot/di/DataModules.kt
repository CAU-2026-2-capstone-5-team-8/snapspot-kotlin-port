package com.capstone.snapspot.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.capstone.snapspot.BuildConfig
import com.capstone.snapspot.data.db.BranchCacheDao
import com.capstone.snapspot.data.db.CouponDao
import com.capstone.snapspot.data.db.EventDao
import com.capstone.snapspot.data.db.PlaceDao
import com.capstone.snapspot.data.db.SnapSpotDatabase
import com.capstone.snapspot.data.geo.AndroidGeocoder
import com.capstone.snapspot.data.geofence.GeofenceRegistrar
import com.capstone.snapspot.data.net.ApiKeys
import com.capstone.snapspot.data.net.GEMINI_BASE_URL
import com.capstone.snapspot.data.net.GeminiAnalyzer
import com.capstone.snapspot.data.net.GeminiService
import com.capstone.snapspot.data.net.KAKAO_BASE_URL
import com.capstone.snapspot.data.net.KakaoService
import com.capstone.snapspot.data.net.RetrofitKakaoApi
import com.capstone.snapspot.data.net.buildRetrofit
import com.capstone.snapspot.data.net.loggingInterceptor
import com.capstone.snapspot.data.notify.Notifier
import com.capstone.snapspot.data.repo.RoomBranchCache
import com.capstone.snapspot.domain.AlertSink
import com.capstone.snapspot.domain.BranchCache
import com.capstone.snapspot.domain.GeocodePort
import com.capstone.snapspot.domain.KakaoLocalApi
import com.capstone.snapspot.domain.RegionRegistrar
import com.capstone.snapspot.domain.ScreenshotAnalyzer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

private val Context.snapspotStore: DataStore<Preferences> by preferencesDataStore(name = "snapspot")

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun apiKeys(): ApiKeys = ApiKeys(
        geminiKey = BuildConfig.GEMINI_API_KEY,
        geminiModel = BuildConfig.GEMINI_MODEL,
        kakaoRestKey = BuildConfig.KAKAO_REST_KEY,
    )

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            // BASIC logs only the request line and status; the key headers are redacted regardless.
            if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor())
        }
        .build()

    /** Reading a screenshot can take a while. */
    @Provides
    @Singleton
    @GeminiClient
    fun geminiOkHttp(base: OkHttpClient): OkHttpClient = base.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()

    @Provides
    @Singleton
    fun kakaoService(client: OkHttpClient): KakaoService = buildRetrofit(KAKAO_BASE_URL, client).create(KakaoService::class.java)

    @Provides
    @Singleton
    fun geminiService(@GeminiClient client: OkHttpClient): GeminiService =
        buildRetrofit(GEMINI_BASE_URL, client).create(GeminiService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun kakaoApi(impl: RetrofitKakaoApi): KakaoLocalApi

    /** The one line to change to read screenshots with something other than Gemini. */
    @Binds
    abstract fun analyzer(impl: GeminiAnalyzer): ScreenshotAnalyzer

    @Binds
    abstract fun geocoder(impl: AndroidGeocoder): GeocodePort

    @Binds
    abstract fun branchCache(impl: RoomBranchCache): BranchCache

    @Binds
    abstract fun alertSink(impl: Notifier): AlertSink

    @Binds
    abstract fun regionRegistrar(impl: GeofenceRegistrar): RegionRegistrar
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SnapSpotDatabase =
        Room.databaseBuilder(context, SnapSpotDatabase::class.java, "snapspot.db").build()

    @Provides
    fun placeDao(db: SnapSpotDatabase): PlaceDao = db.places()

    @Provides
    fun couponDao(db: SnapSpotDatabase): CouponDao = db.coupons()

    @Provides
    fun eventDao(db: SnapSpotDatabase): EventDao = db.events()

    @Provides
    fun branchCacheDao(db: SnapSpotDatabase): BranchCacheDao = db.branchCache()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.snapspotStore
}
