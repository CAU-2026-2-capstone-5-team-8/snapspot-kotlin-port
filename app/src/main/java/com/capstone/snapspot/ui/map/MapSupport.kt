package com.capstone.snapspot.ui.map

import android.content.Context
import com.capstone.snapspot.BuildConfig
import com.kakao.vectormap.KakaoMapSdk

/** Whether the Kakao map can be used in this app run, and if not, why. */
enum class MapSupport {
    READY,

    /** No native app key in local.properties. */
    NO_KEY,

    /** The SDK's native library cannot load here, as on an x86 emulator without ARM translation. */
    UNSUPPORTED,
    ;

    companion object {
        /** Set once at startup by [init]. */
        @Volatile
        var current: MapSupport = NO_KEY
            private set

        /** Starts the SDK. A device that cannot load it must not crash the app, only lose the map. */
        fun init(context: Context) {
            current = when {
                BuildConfig.KAKAO_NATIVE_KEY.isBlank() -> NO_KEY
                else -> try {
                    KakaoMapSdk.init(context, BuildConfig.KAKAO_NATIVE_KEY)
                    READY
                } catch (_: LinkageError) {
                    UNSUPPORTED
                }
            }
        }
    }
}
