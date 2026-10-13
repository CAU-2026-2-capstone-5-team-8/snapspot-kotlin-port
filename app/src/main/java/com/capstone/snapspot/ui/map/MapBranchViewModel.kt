package com.capstone.snapspot.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.snapspot.domain.BranchCache
import com.capstone.snapspot.domain.KakaoLocalApi
import com.capstone.snapspot.domain.activeBrands
import com.capstone.snapspot.domain.leftLoadedArea
import com.capstone.snapspot.domain.loadMapBranches
import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapBranchState(
    val branches: Map<String, BranchRef> = emptyMap(),
    /** Where the lookup was made; the map looks again by itself only after the user leaves the area around it. */
    val loadedAt: Coords? = null,
    val brandKeys: Set<String> = emptySet(),
    val loading: Boolean = false,
)

/**
 * The coupon branches the map shows around where the user looks, apart from the watch plan. It looks when the map
 * is first opened, again by itself only when a following user leaves the first area (or a new brand has a coupon),
 * and otherwise only when asked ("이 지역에서 다시 탐색"). Lookups go through the shared branch cache.
 */
@HiltViewModel
class MapBranchViewModel @Inject constructor(
    private val api: KakaoLocalApi,
    private val cache: BranchCache,
) : ViewModel() {
    private val _state = MutableStateFlow(MapBranchState())
    val state: StateFlow<MapBranchState> = _state.asStateFlow()

    private var job: Job? = null

    /** The map is open and the user is at [here]. */
    fun onHere(here: Coords, following: Boolean, coupons: List<Coupon>) {
        val s = _state.value
        if (s.loading) return // a lookup is already on its way
        val keys = activeBrands(coupons, System.currentTimeMillis()).keys
        val at = s.loadedAt
        when {
            at == null -> load(here, coupons)
            keys != s.brandKeys -> load(at, coupons) // a coupon for a new brand: fill it in around the same area
            following && leftLoadedArea(here, at) -> load(here, coupons)
        }
    }

    /** "이 지역에서 다시 탐색". */
    fun searchAt(center: Coords, coupons: List<Coupon>) = load(center, coupons)

    private fun load(center: Coords, coupons: List<Coupon>) {
        job?.cancel()
        _state.value = _state.value.copy(loading = true)
        job = viewModelScope.launch {
            try {
                val r = loadMapBranches(coupons, center, api, cache)
                _state.value = MapBranchState(r.branches, center, r.brandKeys, loading = false)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }
}
