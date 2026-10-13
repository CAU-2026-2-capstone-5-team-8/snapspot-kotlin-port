package com.capstone.snapspot.ui.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.capstone.snapspot.domain.BRANCH_RADIUS_M
import com.capstone.snapspot.domain.REFRESH_RADIUS_M
import com.capstone.snapspot.domain.distanceMeters
import com.capstone.snapspot.domain.isNear
import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.WindowState
import com.capstone.snapspot.ui.theme.SnapColor
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.DotPoints
import com.kakao.vectormap.shape.Polygon
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles
import kotlin.math.max

/** What the map should show right now. */
class MapScene(
    val places: List<Place>,
    val here: Coords?,
    val win: WindowState?,
    val selectedId: String?,
    val thumbs: Map<String, Bitmap>,
    /** Coupon branches to draw and make tappable; null keeps the watch plan's own, not tappable. */
    val branches: Map<String, BranchRef>? = null,
    /** The first view is placed on the user (when known) instead of fitted around the saved places. */
    val following: Boolean = false,
)

/** Marks a badge label so a tap on it is not mistaken for a tap on a place pin (those carry the place id). */
private class BranchTag(val brandKey: String)

/**
 * Draws a [MapScene] on a Kakao map: pins, alert-radius circles, coupon-branch badges, the refresh circle
 * and the user's dot. Each item remembers a signature of how it looks, and is only redrawn when that changes.
 * Must be used from the main thread.
 */
class MapRenderer(private val density: Float) {
    private var map: KakaoMap? = null
    private var onPlaceClick: (String?) -> Unit = {}

    private class Drawn<T>(val item: T, val signature: String)

    private val pins = HashMap<String, Drawn<Label>>()
    private val circles = HashMap<String, Drawn<Polygon>>()
    private val badges = HashMap<String, Drawn<Label>>()
    private var me: Drawn<Label>? = null
    private var refresh: Drawn<Polygon>? = null
    private var fitted = false
    private val meBitmap by lazy { MapBitmaps.meDot(density) }

    fun attach(
        map: KakaoMap,
        onPlaceClick: (String?) -> Unit,
        onBranchClick: (String) -> Unit = {},
        onCameraRest: (Double, Double, Int, Boolean) -> Unit = { _, _, _, _ -> },
    ) {
        this.map = map
        this.onPlaceClick = onPlaceClick
        map.setOnLabelClickListener { _, _, label ->
            when (val tag = label.tag) {
                is String -> {
                    onPlaceClick(tag)
                    true
                }
                is BranchTag -> {
                    onBranchClick(tag.brandKey)
                    true
                }
                else -> false
            }
        }
        map.setOnMapClickListener { _, _, _, _ -> onPlaceClick(null) }
        // A move the app makes itself (following, focusing, the first view) reports no gesture; a hand does.
        map.setOnCameraMoveEndListener { _, position, gesture ->
            onCameraRest(position.position.latitude, position.position.longitude, position.zoomLevel, gesture != GestureType.Unknown)
        }
    }

    /** Keeps the visible middle of the map clear of the overlays, so a focused pin is not hidden behind the cards. */
    fun setInsets(topPx: Int, bottomPx: Int) {
        map?.setPadding(0, topPx, 0, bottomPx)
    }

    fun render(scene: MapScene) {
        val m = map ?: return
        drawPlaces(m, scene)
        drawBranches(m, scene)
        drawRefresh(m, scene)
        drawMe(m, scene.here)
        fitOnce(m, scene)
    }

    private fun latLng(lat: Double, lng: Double) = LatLng.from(lat, lng)

    private fun argb(c: Color, alpha: Float = c.alpha): Int = c.copy(alpha = alpha).toArgb()

    private fun drawPlaces(m: KakaoMap, scene: MapScene) {
        val labels = m.labelManager?.layer ?: return
        val shapes = m.shapeManager?.layer ?: return
        val keep = scene.places.map { it.id }.toSet()
        for (id in pins.keys - keep) pins.remove(id)?.let { labels.remove(it.item) }
        for (id in circles.keys - keep) circles.remove(id)?.let { shapes.remove(it.item) }

        for (p in scene.places) {
            val near = scene.here?.let { isNear(distanceMeters(it, Coords(p.latitude, p.longitude)), p.radius) } ?: false
            val selected = p.id == scene.selectedId
            val thumb = p.imageUri?.let { scene.thumbs[it] }

            val pinSig = "$near|$selected|${thumb != null}|${p.latitude},${p.longitude}"
            if (pins[p.id]?.signature != pinSig) {
                pins.remove(p.id)?.let { labels.remove(it.item) }
                val style = LabelStyle.from(MapBitmaps.pin(thumb, near, selected, density)).setAnchorPoint(0.5f, 1f)
                val options = LabelOptions.from(p.id, latLng(p.latitude, p.longitude))
                    .setStyles(LabelStyles.from(style))
                    .setClickable(true)
                    .setTag(p.id)
                    // The selected pin sits above the others, a near one above the rest.
                    .setRank(if (selected) 2L else if (near) 1L else 0L)
                labels.addLabel(options)?.let { pins[p.id] = Drawn(it, pinSig) }
            }

            val circleSig = "$near|${p.radius}|${p.latitude},${p.longitude}"
            if (circles[p.id]?.signature != circleSig) {
                circles.remove(p.id)?.let { shapes.remove(it.item) }
                val fill = if (near) argb(SnapColor.Highlight, 0.35f) else argb(SnapColor.Green, 0.08f)
                val stroke = if (near) android.graphics.Color.rgb(0xC9, 0xA8, 0x00) else argb(SnapColor.Green, 0.6f)
                val polygon = shapes.addPolygon(
                    PolygonOptions.from(
                        DotPoints.fromCircle(latLng(p.latitude, p.longitude), p.radius.toFloat()),
                        PolygonStyles.from(fill, 1.5f * density, stroke),
                    ),
                )
                if (polygon != null) circles[p.id] = Drawn(polygon, circleSig)
            }
        }
    }

    private fun drawBranches(m: KakaoMap, scene: MapScene) {
        val labels = m.labelManager?.layer ?: return
        // With a list from the screen the badges open their brand's coupons; without one, as before, they only show.
        val tappable = scene.branches != null
        val branches = scene.branches ?: scene.win?.branches.orEmpty()
        for (id in badges.keys - branches.keys) badges.remove(id)?.let { labels.remove(it.item) }
        for ((id, b) in branches) {
            val near = scene.here?.let { isNear(distanceMeters(it, Coords(b.latitude, b.longitude)), BRANCH_RADIUS_M) } ?: false
            val letter = b.name.trim().take(1)
            val sig = "$near|$letter|${b.latitude},${b.longitude}"
            if (badges[id]?.signature == sig) continue
            badges.remove(id)?.let { labels.remove(it.item) }
            val options = LabelOptions.from(id, latLng(b.latitude, b.longitude))
                .setStyles(LabelStyles.from(LabelStyle.from(MapBitmaps.badge(letter, near, density)).setAnchorPoint(0.5f, 0.5f)))
                .setClickable(tappable)
            if (tappable) options.setTag(BranchTag(b.brandKey))
            labels.addLabel(options)?.let { badges[id] = Drawn(it, sig) }
        }
    }

    private fun drawRefresh(m: KakaoMap, scene: MapScene) {
        val shapes = m.shapeManager?.layer ?: return
        val at = scene.win?.here
        if (at == null) {
            refresh?.let { shapes.remove(it.item) }
            refresh = null
            return
        }
        val sig = "${at.latitude},${at.longitude}"
        if (refresh?.signature == sig) return
        refresh?.let { shapes.remove(it.item) }
        val polygon = shapes.addPolygon(
            PolygonOptions.from(
                DotPoints.fromCircle(latLng(at.latitude, at.longitude), REFRESH_RADIUS_M.toFloat()),
                PolygonStyles.from(android.graphics.Color.TRANSPARENT, 2f * density, argb(SnapColor.Ink, 0.55f)),
            ),
        )
        refresh = polygon?.let { Drawn(it, sig) }
    }

    private fun drawMe(m: KakaoMap, here: Coords?) {
        val labels = m.labelManager?.layer ?: return
        if (here == null) {
            me?.let { labels.remove(it.item) }
            me = null
            return
        }
        val sig = "${here.latitude},${here.longitude}"
        val current = me
        if (current != null && current.signature == sig) return
        current?.let { labels.remove(it.item) }
        val options = LabelOptions.from("me", latLng(here.latitude, here.longitude))
            .setStyles(LabelStyles.from(LabelStyle.from(meBitmap).setAnchorPoint(0.5f, 0.5f)))
            .setClickable(false)
        me = labels.addLabel(options)?.let { Drawn(it, sig) }
    }

    /** The camera is placed once, when there is something to show; after that the user owns it. */
    private fun fitOnce(m: KakaoMap, scene: MapScene) {
        if (fitted) return
        // Following: show where the user is. Only when the position is not known, fit the saved places as before.
        val here = scene.here
        if (scene.following && here != null) {
            fitted = true
            m.moveCamera(CameraUpdateFactory.newCenterPosition(latLng(here.latitude, here.longitude), DEFAULT_ZOOM))
            return
        }
        val points = scene.places.map { latLng(it.latitude, it.longitude) } + listOfNotNull(scene.here?.let { latLng(it.latitude, it.longitude) })
        if (points.isEmpty()) return
        fitted = true
        if (points.size == 1) {
            m.moveCamera(CameraUpdateFactory.newCenterPosition(points[0], DEFAULT_ZOOM))
        } else {
            m.moveCamera(CameraUpdateFactory.fitMapPoints(points.toTypedArray(), (50 * density).toInt()))
        }
    }

    /** Keeps the user in view while following; like [focus], it does not zoom in further than needed. */
    fun followTo(lat: Double, lng: Double) {
        val m = map ?: return
        m.moveCamera(CameraUpdateFactory.newCenterPosition(latLng(lat, lng), max(m.zoomLevel, DEFAULT_ZOOM)), CameraAnimation.from(400))
    }

    /** Moves the camera to a place, keeping the current zoom unless it is far out. */
    fun focus(lat: Double, lng: Double) {
        val m = map ?: return
        m.moveCamera(CameraUpdateFactory.newCenterPosition(latLng(lat, lng), max(m.zoomLevel, DEFAULT_ZOOM)), CameraAnimation.from(500))
    }

    private companion object {
        const val DEFAULT_ZOOM = 15
    }
}
