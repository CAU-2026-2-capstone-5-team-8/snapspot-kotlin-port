package com.capstone.snapspot.domain

/**
 * Whether the map follows the user, and whether it offers a new lookup. Only a move made by the user's hand
 * counts as leaving; the map moving itself (following, focusing a place, the first view) never shows the button.
 */
data class MapFollow(
    val following: Boolean = true,
    /** "이 지역에서 다시 탐색" */
    val showSearchHere: Boolean = false,
    /** "확대해 주세요", instead of the button when the map is zoomed out too far. */
    val zoomHint: Boolean = false,
) {
    /** The camera stopped moving. [byUser] is false when the app moved it. */
    fun cameraMoved(byUser: Boolean, zoom: Int): MapFollow {
        if (!byUser) return this
        val z = zoomState(zoom)
        return MapFollow(following = false, showSearchHere = z == ZoomState.SEARCHABLE, zoomHint = z == ZoomState.TOO_FAR)
    }

    /** A pin or a card was chosen: stop following so the next position update does not pull the map back. */
    fun placeFocused(): MapFollow = copy(following = false)

    /** "내 위치로": follow again, no lookup offered. */
    fun backToMe(): MapFollow = MapFollow()

    /** The lookup was run. */
    fun searched(): MapFollow = copy(showSearchHere = false, zoomHint = false)
}
