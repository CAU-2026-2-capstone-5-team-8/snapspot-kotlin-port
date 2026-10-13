package com.capstone.snapspot.ui.map

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.WindowState
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ask the map to move to a place. A new [nonce] means a new request, even for the same place. */
class MapFocus(val lat: Double, val lng: Double, val nonce: Int)

/** Everything the map needs from the screen around it. */
class MapParams(
    val places: List<Place>,
    val here: Coords?,
    val win: WindowState?,
    val selectedId: String?,
    val focus: MapFocus?,
    val topInsetPx: Int,
    val bottomInsetPx: Int,
    val onSelect: (String?) -> Unit,
    val onFailure: (Exception) -> Unit,
)

/** The Kakao map inside Compose. Its lifecycle follows the screen's, as the SDK requires. */
@Composable
fun KakaoMapView(params: MapParams, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(params)

    val renderer = remember { MapRenderer(density) }
    val mapView = remember { MapView(context) }
    val thumbs = remember { mutableStateMapOf<String, Bitmap>() }
    var ready by remember { mutableStateOf(false) }

    DisposableEffect(mapView) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {}

                override fun onMapError(error: Exception) {
                    latest.onFailure(error)
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    renderer.attach(map) { id -> latest.onSelect(id) }
                    ready = true
                }
            },
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    // Pin thumbnails are decoded off the main thread, once per image.
    LaunchedEffect(params.places) {
        for (p in params.places) {
            val path = p.imageUri ?: continue
            if (path in thumbs) continue
            val bmp = withContext(Dispatchers.IO) { MapBitmaps.thumbnail(path) }
            if (bmp != null) thumbs[path] = bmp
        }
    }

    LaunchedEffect(ready, params.places, params.here, params.win, params.selectedId, thumbs.size) {
        if (ready) renderer.render(MapScene(params.places, params.here, params.win, params.selectedId, thumbs.toMap()))
    }
    LaunchedEffect(ready, params.topInsetPx, params.bottomInsetPx) {
        if (ready) renderer.setInsets(params.topInsetPx, params.bottomInsetPx)
    }
    LaunchedEffect(ready, params.focus?.nonce) {
        val f = params.focus
        if (ready && f != null) renderer.focus(f.lat, f.lng)
    }

    AndroidView(factory = { mapView }, modifier = modifier.fillMaxSize())
}
