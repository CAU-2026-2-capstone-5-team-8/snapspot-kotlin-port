package com.capstone.snapspot.domain.draft

import com.capstone.snapspot.domain.FakeKakao
import com.capstone.snapspot.domain.GeocodePort
import com.capstone.snapspot.domain.ScreenshotAnalyzer
import com.capstone.snapspot.domain.kakaoDoc
import com.capstone.snapspot.domain.memoryCache
import com.capstone.snapspot.domain.model.CandidateSource
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Extracted
import com.capstone.snapspot.domain.model.KakaoDoc
import com.capstone.snapspot.domain.model.Kind
import com.capstone.snapspot.domain.model.Place
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddFlowTest {
    private val seongsu = Coords(37.5446, 127.0565)
    private val clock = 1_700_000_000_000L

    private class FakeImages : ImageSource {
        val prepared = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var failWith: Exception? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun prepare(uri: String): PreparedImage {
            gate?.await()
            failWith?.let { throw it }
            val path = "/img/${prepared.size + 1}.jpg"
            prepared += path
            return PreparedImage(path, byteArrayOf(1, 2, 3))
        }

        override suspend fun delete(path: String) {
            deleted += path
        }
    }

    private class FakeAnalyzer(var results: MutableList<Extracted> = mutableListOf()) : ScreenshotAnalyzer {
        var failWith: Exception? = null
        var gates = ArrayDeque<CompletableDeferred<Unit>>()
        var calls = 0

        override suspend fun analyze(jpeg: ByteArray): Extracted {
            calls++
            failWith?.let { throw it }
            // Take this call's answer first, so a call cancelled while waiting cannot steal the next one's.
            val answer = results.removeFirst()
            gates.removeFirstOrNull()?.await()
            return answer
        }
    }

    private class FakeGeocoder(var result: Coords? = null) : GeocodePort {
        var calls = 0
        override suspend fun geocode(name: String, address: String): Coords? {
            calls++
            return result
        }
    }

    private class FakeHere(var at: Coords?) : HerePort {
        override suspend fun current(): Coords? = at
    }

    private class FakeStore : SavePort {
        val places = mutableListOf<Place>()
        val coupons = mutableListOf<Coupon>()
        override suspend fun savePlace(place: Place) {
            places += place
        }

        override suspend fun saveCoupon(coupon: Coupon) {
            coupons += coupon
        }
    }

    private fun placeEx(name: String = "어니언 성수", address: String = "서울 성동구 아차산로9길 8", isPlace: Boolean = true, endDate: String? = null) =
        Extracted(Kind.PLACE, "", "", isPlace, name, address, "카페", "소금빵 맛집", endDate)

    private fun couponEx(brand: String = "스타벅스", benefit: String = "아메리카노 Tall", endDate: String? = "2026-12-31") =
        Extracted(Kind.COUPON, brand, benefit, false, "", "", "기타", "", endDate)

    private val onion: KakaoDoc = kakaoDoc(x = "127.0582", y = "37.5447")
    private val starbucks = List(3) { i ->
        kakaoDoc(
            id = "s$i", placeName = "스타벅스 ${i}호점", categoryName = "음식점 > 카페 > 커피전문점 > 스타벅스", categoryGroupCode = "",
            x = (127.0565 + (i + 1) * 0.001).toString(), y = "37.5446",
        )
    }
    private fun kakao() = FakeKakao(mapOf("성동구 어니언 성수" to listOf(onion), "스타벅스" to starbucks))

    private class Rig(
        val flow: AddFlow,
        val images: FakeImages,
        val analyzer: FakeAnalyzer,
        val geocoder: FakeGeocoder,
        val store: FakeStore,
        val here: FakeHere,
    )

    /**
     * A scope driven by the test clock and cancelled when the test ends. (`backgroundScope` is not used:
     * its coroutines did not run under `advanceUntilIdle()` in this coroutines-test version.)
     */
    private fun TestScope.flowScope(dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler)): CoroutineScope =
        CoroutineScope(SupervisorJob(backgroundScope.coroutineContext[Job]) + dispatcher)

    private fun TestScope.rig(
        results: List<Extracted> = emptyList(),
        withKakao: Boolean = true,
        at: Coords? = seongsu,
        kakao: FakeKakao = kakao(),
    ): Rig {
        val images = FakeImages()
        val analyzer = FakeAnalyzer(results.toMutableList())
        val geocoder = FakeGeocoder()
        val store = FakeStore()
        val herePort = FakeHere(at)
        val flow = AddFlow(
            scope = flowScope(), images = images, analyzer = analyzer, kakao = if (withKakao) kakao else null,
            geocoder = geocoder, branchCache = memoryCache(), here = herePort, store = store, now = { clock },
        )
        return Rig(flow, images, analyzer, geocoder, store, herePort)
    }

    private fun <T> List<T>.collapse(): List<T> = fold(mutableListOf()) { acc, x -> if (acc.isEmpty() || acc.last() != x) acc.add(x); acc }

    // --- reading a place ---

    @Test
    fun `a place goes from reading to locating to ready and ends up pinned on the Kakao match`() = runTest {
        val r = rig(listOf(placeEx()))
        val stages = mutableListOf<Stage?>()
        flowScope(UnconfinedTestDispatcher(testScheduler)).launch { r.flow.draft.collect { stages += it?.stage } }
        runCurrent() // let the collector subscribe before anything happens

        r.flow.start("content://picker/1")
        advanceUntilIdle()

        assertEquals(listOf(null, Stage.READING, Stage.LOCATING, Stage.READY), stages.collapse())
        val d = r.flow.draft.value!!
        assertEquals("/img/1.jpg", d.imageUri) // the app-storage copy replaced the picker URI
        assertEquals("어니언 성수", d.name)
        assertEquals("카페", d.category)
        assertEquals(Coords(37.5447, 127.0582), d.coords)
        assertEquals("어니언 성수", d.matched?.name)
        assertEquals(200, d.radius)
        assertNull(d.branches)
    }

    @Test
    fun `the draft shows the picked photo right away`() = runTest {
        val r = rig(listOf(placeEx()))
        r.images.gate = CompletableDeferred()
        r.flow.start("content://picker/1")
        advanceUntilIdle()
        val d = r.flow.draft.value!!
        assertEquals(Stage.READING, d.stage)
        assertEquals("content://picker/1", d.imageUri)
    }

    @Test
    fun `a screenshot with no place skips the map search`() = runTest {
        val kakao = kakao()
        val r = rig(listOf(placeEx(name = "", address = "", isPlace = false)), kakao = kakao)
        r.flow.start("u")
        advanceUntilIdle()
        val d = r.flow.draft.value!!
        assertEquals(Stage.READY, d.stage)
        assertNull(d.coords)
        assertFalse(d.isPlace)
        assertEquals(0, kakao.keywordCalls)
        assertEquals(0, r.geocoder.calls)
    }

    @Test
    fun `a weak Kakao result is offered but the device geocoder places the pin`() = runTest {
        val weak = kakaoDoc(placeName = "김밥천국", categoryGroupCode = "FD6", roadAddressName = "서울 마포구 어딘가 1")
        val kakao = FakeKakao(everyKeyword = listOf(weak))
        val r = rig(listOf(placeEx()), kakao = kakao)
        r.geocoder.result = Coords(37.0, 127.0)
        r.flow.start("u")
        advanceUntilIdle()
        val d = r.flow.draft.value!!
        assertEquals(Coords(37.0, 127.0), d.coords)
        assertNull(d.matched)
        assertTrue(d.candidates.isNotEmpty())
    }

    @Test
    fun `without a Kakao key the device geocoder is used`() = runTest {
        val r = rig(listOf(placeEx()), withKakao = false)
        r.geocoder.result = Coords(37.1, 127.1)
        r.flow.start("u")
        advanceUntilIdle()
        assertEquals(Coords(37.1, 127.1), r.flow.draft.value!!.coords)
    }

    // --- reading a coupon ---

    @Test
    fun `a coupon previews the branches around the user, nearest first`() = runTest {
        val r = rig(listOf(couponEx()))
        r.flow.start("u")
        advanceUntilIdle()
        val d = r.flow.draft.value!!
        assertEquals(Stage.READY, d.stage)
        assertEquals(Kind.COUPON, d.kind)
        assertEquals("스타벅스", d.brand)
        assertEquals("2026-12-31", d.endDate)
        assertNull(d.coords)
        assertEquals(listOf("스타벅스 0호점", "스타벅스 1호점", "스타벅스 2호점"), d.branches!!.map { it.name })
    }

    @Test
    fun `a coupon without a known position has no preview, and saving still works`() = runTest {
        val r = rig(listOf(couponEx()), at = null)
        r.flow.start("u")
        advanceUntilIdle()
        assertNull(r.flow.draft.value!!.branches)
        assertNotNull(r.flow.save())
    }

    // --- failures ---

    @Test
    fun `when reading fails the form still opens and the user is told why`() = runTest {
        val r = rig()
        r.analyzer.failWith = IllegalStateException("Gemini 요청 실패 (429): quota")
        r.flow.start("u")
        advanceUntilIdle()
        val d = r.flow.draft.value!!
        assertEquals(Stage.READY, d.stage)
        assertEquals("/img/1.jpg", d.imageUri)
        assertEquals(AddEvent.ReadFailed("Gemini 요청 실패 (429): quota"), r.flow.events.first())
    }

    @Test
    fun `when the image cannot even be opened the form opens empty and the user is told`() = runTest {
        val r = rig()
        r.images.failWith = java.io.IOException("이미지를 열 수 없어요.")
        r.flow.start("u")
        advanceUntilIdle()
        assertEquals(Stage.READY, r.flow.draft.value!!.stage)
        assertEquals(AddEvent.ReadFailed("이미지를 열 수 없어요."), r.flow.events.first())
        assertEquals(0, r.analyzer.calls)
        assertNull(r.flow.save()) // nothing to save without a pin
    }

    // --- replacing and cancelling ---

    @Test
    fun `picking again drops the old pick, its late result and its image copy`() = runTest {
        val first = CompletableDeferred<Unit>()
        val r = rig(listOf(placeEx(name = "첫번째"), placeEx(name = "두번째")))
        r.analyzer.gates.add(first)
        r.flow.start("u1")
        advanceUntilIdle() // first pick is waiting inside the analyzer, its image already copied
        r.flow.start("u2")
        advanceUntilIdle()
        assertEquals("두번째", r.flow.draft.value!!.name)

        first.complete(Unit) // the old pick finally answers
        advanceUntilIdle()
        assertEquals("두번째", r.flow.draft.value!!.name)
        assertEquals("/img/2.jpg", r.flow.draft.value!!.imageUri)
        assertEquals(listOf("/img/1.jpg"), r.images.deleted)
    }

    @Test
    fun `a pick replaced before its image was copied leaves no file behind`() = runTest {
        val r = rig(listOf(placeEx()))
        val gate = CompletableDeferred<Unit>()
        r.images.gate = gate
        r.flow.start("u1")
        advanceUntilIdle()
        r.images.gate = null
        r.flow.start("u2")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, r.images.prepared.size)
        assertEquals(emptyList<String>(), r.images.deleted)
    }

    @Test
    fun `cancelling clears the draft and deletes the image copy`() = runTest {
        val r = rig(listOf(placeEx()))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.cancel()
        advanceUntilIdle()
        assertNull(r.flow.draft.value)
        assertEquals(listOf("/img/1.jpg"), r.images.deleted)
    }

    @Test
    fun `cancelling while still reading stops the work`() = runTest {
        val r = rig(listOf(placeEx()))
        r.analyzer.gates.add(CompletableDeferred())
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.cancel()
        advanceUntilIdle()
        assertNull(r.flow.draft.value)
        assertEquals(listOf("/img/1.jpg"), r.images.deleted)
    }

    // --- editing ---

    @Test
    fun `update edits the draft`() = runTest {
        val r = rig(listOf(placeEx()))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.update { it.copy(name = "고친 이름", radius = 500) }
        assertEquals("고친 이름", r.flow.draft.value!!.name)
        assertEquals(500, r.flow.draft.value!!.radius)
    }

    @Test
    fun `choosing a Kakao candidate takes its name, address and pin`() = runTest {
        val r = rig(listOf(placeEx()))
        r.flow.start("u")
        advanceUntilIdle()
        val other = r.flow.draft.value!!.candidates.first().copy(name = "다른 가게", address = "서울 어딘가", latitude = 37.6, longitude = 127.1)
        r.flow.pickCandidate(other)
        val d = r.flow.draft.value!!
        assertEquals("다른 가게", d.name)
        assertEquals("서울 어딘가", d.address)
        assertEquals(Coords(37.6, 127.1), d.coords)
        assertEquals(other, d.matched)
    }

    @Test
    fun `choosing an address-only candidate keeps the typed name`() = runTest {
        val r = rig(listOf(placeEx()))
        r.flow.start("u")
        advanceUntilIdle()
        val byAddress = r.flow.draft.value!!.candidates.first().copy(name = "주소 이름", address = "서울 성동구 1", source = CandidateSource.ADDRESS)
        r.flow.pickCandidate(byAddress)
        assertEquals("어니언 성수", r.flow.draft.value!!.name)
        assertEquals("서울 성동구 1", r.flow.draft.value!!.address)
    }

    @Test
    fun `use my position pins here and forgets the Kakao match`() = runTest {
        val r = rig(listOf(placeEx()))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.pinToHere()
        advanceUntilIdle()
        val d = r.flow.draft.value!!
        assertEquals(seongsu, d.coords)
        assertNull(d.matched)
    }

    @Test
    fun `use my position does nothing when the position is unknown`() = runTest {
        val r = rig(listOf(placeEx()), at = null)
        r.flow.start("u")
        advanceUntilIdle()
        val before = r.flow.draft.value
        r.flow.pinToHere()
        advanceUntilIdle()
        assertEquals(before, r.flow.draft.value)
    }

    @Test
    fun `find again by address uses the edited text and reports when nothing is found`() = runTest {
        val r = rig(listOf(placeEx()))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.update { it.copy(name = "없는 가게", address = "", coords = Coords(1.0, 1.0)) }
        r.flow.regeocode()
        advanceUntilIdle()
        assertNull(r.flow.draft.value!!.coords)
        assertEquals(AddEvent.LocationNotFound, r.flow.events.first())
        assertFalse(r.flow.busy.value)
    }

    @Test
    fun `find again by address can recover a pin`() = runTest {
        val r = rig(listOf(placeEx(name = "없는 가게", address = "")))
        r.flow.start("u")
        advanceUntilIdle()
        assertNull(r.flow.draft.value!!.coords)
        r.flow.update { it.copy(name = "어니언 성수", address = "서울 성동구 아차산로9길 8") }
        r.flow.regeocode()
        advanceUntilIdle()
        assertEquals(Coords(37.5447, 127.0582), r.flow.draft.value!!.coords)
    }

    // --- switching what it is ---

    @Test
    fun `switching a place to a coupon uses the name as the brand and previews branches`() = runTest {
        val r = rig(listOf(placeEx(name = "스타벅스")))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.setKind(Kind.COUPON)
        advanceUntilIdle()
        val d = r.flow.draft.value!!
        assertEquals(Kind.COUPON, d.kind)
        assertEquals("스타벅스", d.brand)
        assertEquals(3, d.branches!!.size)
    }

    @Test
    fun `switching a coupon to a place finds a pin when there is none`() = runTest {
        val r = rig(listOf(couponEx(brand = "어니언 성수")))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.update { it.copy(name = "어니언 성수", address = "서울 성동구 아차산로9길 8", category = "카페") }
        r.flow.setKind(Kind.PLACE)
        advanceUntilIdle()
        assertEquals(Kind.PLACE, r.flow.draft.value!!.kind)
        assertEquals(Coords(37.5447, 127.0582), r.flow.draft.value!!.coords)
    }

    @Test
    fun `refreshing the branch preview looks the brand up again`() = runTest {
        val r = rig(listOf(couponEx(brand = "없는브랜드")))
        r.flow.start("u")
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), r.flow.draft.value!!.branches)
        r.flow.update { it.copy(brand = "스타벅스") }
        r.flow.refreshBranchPreview()
        advanceUntilIdle()
        assertEquals(3, r.flow.draft.value!!.branches!!.size)
        assertFalse(r.flow.busy.value)
    }

    // --- saving ---

    @Test
    fun `saving a place stores what the user confirmed and keeps its image`() = runTest {
        val r = rig(listOf(placeEx(endDate = "2026-10-31")))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.update { it.copy(radius = 300, note = "메모") }
        val saved = r.flow.save()!!
        advanceUntilIdle()

        assertEquals(Saved(Kind.PLACE, "p_${clock.toString(36)}"), saved)
        val p = r.store.places.single()
        assertEquals("어니언 성수", p.name)
        assertEquals("서울 성동구 아차산로9길 8", p.address)
        assertEquals("카페", p.category)
        assertEquals("메모", p.note)
        assertEquals("2026-10-31", p.endDate)
        assertEquals(37.5447, p.latitude, 1e-9)
        assertEquals(127.0582, p.longitude, 1e-9)
        assertEquals(300, p.radius)
        assertEquals("/img/1.jpg", p.imageUri)
        assertEquals("1", p.kakaoId)
        assertEquals("http://place.map.kakao.com/1", p.placeUrl)
        assertEquals(clock, p.createdAt)
        assertNull(p.lastNotifiedAt)
        assertNull(r.flow.draft.value)
        assertEquals("a saved item keeps its image", emptyList<String>(), r.images.deleted)
    }

    @Test
    fun `a place with blank fields gets the same defaults as before`() = runTest {
        val r = rig(listOf(placeEx(name = "", address = "")))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.pinToHere()
        advanceUntilIdle()
        r.flow.update { it.copy(category = "", endDate = "10월 31일") }
        r.flow.save()
        val p = r.store.places.single()
        assertEquals("이름 없는 장소", p.name)
        assertEquals("기타", p.category)
        assertNull(p.endDate)
        assertNull(p.kakaoId)
        assertNull(p.placeUrl)
    }

    @Test
    fun `a place cannot be saved without a pin`() = runTest {
        val r = rig(listOf(placeEx(name = "", address = "")))
        r.flow.start("u")
        advanceUntilIdle()
        assertNull(r.flow.save())
        assertTrue(r.store.places.isEmpty())
        assertNotNull("the draft stays open", r.flow.draft.value)
    }

    @Test
    fun `saving a coupon stores brand, benefit, expiry and image`() = runTest {
        val r = rig(listOf(couponEx()))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.update { it.copy(brand = " 스타벅스 ", benefit = " 아메리카노 Tall ") }
        val saved = r.flow.save()!!
        assertEquals(Saved(Kind.COUPON, "c_${clock.toString(36)}"), saved)
        val c = r.store.coupons.single()
        assertEquals("스타벅스", c.brand)
        assertEquals("아메리카노 Tall", c.benefit)
        assertEquals("2026-12-31", c.expiresAt)
        assertEquals("/img/1.jpg", c.imageUri)
        assertNull(c.usedAt)
        assertNull(c.lastNotifiedAt)
        assertTrue(r.store.places.isEmpty())
        assertNull(r.flow.draft.value)
    }

    @Test
    fun `a coupon with no benefit is called 쿠폰, and no expiry is stored when the date is not a date`() = runTest {
        val r = rig(listOf(couponEx(benefit = "", endDate = null)))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.update { it.copy(endDate = "곧") }
        r.flow.save()
        val c = r.store.coupons.single()
        assertEquals("쿠폰", c.benefit)
        assertNull(c.expiresAt)
    }

    @Test
    fun `a coupon cannot be saved without a brand`() = runTest {
        val r = rig(listOf(couponEx(brand = "")))
        r.flow.start("u")
        advanceUntilIdle()
        r.flow.update { it.copy(brand = "  ") }
        assertNull(r.flow.save())
        assertTrue(r.store.coupons.isEmpty())
    }

    @Test
    fun `saving with nothing open does nothing`() = runTest {
        val r = rig()
        assertNull(r.flow.save())
    }
}
