package com.capstone.snapspot.ui.map

import com.capstone.snapspot.domain.FakeKakao
import com.capstone.snapspot.domain.KakaoLocalApi
import com.capstone.snapspot.domain.brandKey
import com.capstone.snapspot.domain.coupon
import com.capstone.snapspot.domain.kakaoDoc
import com.capstone.snapspot.domain.memoryCache
import com.capstone.snapspot.domain.model.AddressDoc
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.KakaoDoc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** When the map looks for coupon branches by itself, and when it waits to be asked. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapBranchViewModelTest {
    private val here = Coords(37.5446, 127.0565)
    private val sameArea = Coords(here.latitude, here.longitude + 0.002) // about 180 m
    private val farAway = Coords(here.latitude, here.longitude + 0.05) // about 4.4 km, another grid cell

    private val starbucksKey = brandKey("스타벅스")
    private val oliveKey = brandKey("올리브영")

    private fun branch(id: String, name: String, brand: String) =
        kakaoDoc(id = id, placeName = name, categoryName = "음식점 > 카페 > 커피전문점 > $brand", categoryGroupCode = "")

    private val starbucks = listOf(branch("s1", "스타벅스 성수점", "스타벅스"), branch("s2", "스타벅스 뚝섬점", "스타벅스"))
    private val olive = listOf(
        kakaoDoc(id = "o1", placeName = "올리브영 성수점", categoryName = "가정,생활 > 드럭스토어 > 올리브영", categoryGroupCode = ""),
    )

    private fun api() = FakeKakao(byQuery = mapOf("스타벅스" to starbucks, "올리브영" to olive))

    private fun viewModel(api: KakaoLocalApi) = MapBranchViewModel(api, memoryCache())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the first position looks up the branches of the brands that have a coupon`() {
        val vm = viewModel(api())
        vm.onHere(here, following = true, coupons = listOf(coupon(brand = "스타벅스")))

        val s = vm.state.value
        assertEquals(here, s.loadedAt)
        assertEquals(setOf(starbucksKey), s.brandKeys)
        assertEquals(2, s.branches.size)
        assertTrue(s.branches.values.all { it.brandKey == starbucksKey })
        assertFalse(s.loading)
    }

    @Test
    fun `staying inside the first area while following does not look again`() {
        val api = api()
        val vm = viewModel(api)
        val coupons = listOf(coupon(brand = "스타벅스"))
        vm.onHere(here, true, coupons)
        val calls = api.keywordCalls
        vm.onHere(sameArea, true, coupons)
        vm.onHere(here, true, coupons)

        assertEquals(calls, api.keywordCalls)
        assertEquals(here, vm.state.value.loadedAt)
    }

    @Test
    fun `a following user who leaves the first area gets a new lookup around the new place`() {
        val api = api()
        val vm = viewModel(api)
        val coupons = listOf(coupon(brand = "스타벅스"))
        vm.onHere(here, true, coupons)
        val calls = api.keywordCalls
        vm.onHere(farAway, true, coupons)

        assertTrue(api.keywordCalls > calls)
        assertEquals(farAway, vm.state.value.loadedAt)
    }

    @Test
    fun `a user who is not followed is never looked up for by itself, however far the position moves`() {
        val api = api()
        val vm = viewModel(api)
        val coupons = listOf(coupon(brand = "스타벅스"))
        vm.onHere(here, true, coupons)
        val calls = api.keywordCalls
        vm.onHere(farAway, following = false, coupons = coupons)

        assertEquals(calls, api.keywordCalls)
        assertEquals(here, vm.state.value.loadedAt)
    }

    @Test
    fun `a coupon for a new brand is filled in around the same first area`() {
        val api = api()
        val vm = viewModel(api)
        vm.onHere(here, true, listOf(coupon(id = "a", brand = "스타벅스")))
        val both = listOf(coupon(id = "a", brand = "스타벅스"), coupon(id = "b", brand = "올리브영"))
        vm.onHere(sameArea, true, both)

        val s = vm.state.value
        assertEquals(here, s.loadedAt) // not the new position
        assertEquals(setOf(starbucksKey, oliveKey), s.brandKeys)
        assertEquals(setOf(starbucksKey, oliveKey), s.branches.values.map { it.brandKey }.toSet())
        assertEquals(3, s.branches.size)
    }

    @Test
    fun `a position that arrives while a lookup is on its way is ignored, and the lookup still lands`() {
        val gate = CompletableDeferred<List<KakaoDoc>>()
        val held = GatedKakao(gate)
        val vm = viewModel(held)
        val coupons = listOf(coupon(brand = "스타벅스"))

        vm.onHere(here, true, coupons)
        assertTrue(vm.state.value.loading)
        vm.onHere(farAway, true, coupons) // ignored: one is already on its way
        vm.onHere(sameArea, true, coupons)
        assertEquals(1, held.calls)
        assertNull(vm.state.value.loadedAt)

        gate.complete(starbucks)
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(here, s.loadedAt)
        assertEquals(2, s.branches.size)
    }

    @Test
    fun `asking again searches where the map is, following or not, and the newest ask wins`() {
        val api = api()
        val vm = viewModel(api)
        val coupons = listOf(coupon(brand = "스타벅스"))
        vm.onHere(here, true, coupons)

        vm.searchAt(farAway, coupons)
        assertEquals(farAway, vm.state.value.loadedAt)

        // A second ask while the first is held: the first is dropped, the last one is the result.
        val gate = CompletableDeferred<List<KakaoDoc>>()
        val held = GatedKakao(gate)
        val vm2 = viewModel(held)
        vm2.searchAt(here, coupons)
        vm2.searchAt(farAway, coupons)
        gate.complete(starbucks)
        assertEquals(farAway, vm2.state.value.loadedAt)
        assertFalse(vm2.state.value.loading)
    }

    @Test
    fun `without an active coupon nothing is looked up`() {
        val api = api()
        val vm = viewModel(api)
        vm.onHere(here, true, listOf(coupon(brand = "스타벅스", usedAt = 1L), coupon(id = "x", brand = "올리브영", expiresAt = "2020-01-01")))

        assertEquals(0, api.keywordCalls)
        assertTrue(vm.state.value.branches.isEmpty())
        assertEquals(here, vm.state.value.loadedAt)
        assertFalse(vm.state.value.loading)
    }

    /** A Kakao that answers only when the test lets it, to look at the state while a lookup is on its way. */
    private class GatedKakao(private val gate: CompletableDeferred<List<KakaoDoc>>) : KakaoLocalApi {
        var calls = 0
            private set

        override suspend fun keyword(query: String, size: Int, x: Double?, y: Double?, radius: Int?, sort: String?, page: Int): List<KakaoDoc> {
            calls++
            return gate.await()
        }

        override suspend fun address(query: String, size: Int): List<AddressDoc> = emptyList()
    }
}
