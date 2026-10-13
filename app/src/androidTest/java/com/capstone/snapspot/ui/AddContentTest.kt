package com.capstone.snapspot.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.domain.draft.Draft
import com.capstone.snapspot.domain.draft.Stage
import com.capstone.snapspot.domain.model.Branch
import com.capstone.snapspot.domain.model.Candidate
import com.capstone.snapspot.domain.model.CandidateSource
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Kind
import com.capstone.snapspot.ui.add.AddActions
import com.capstone.snapspot.ui.add.AddContent
import com.capstone.snapspot.ui.theme.SnapSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val here = Coords(37.5446, 127.0565)
    private val onion = Candidate("1", "어니언 성수", "서울 성동구 아차산로9길 8", "카페", 37.5447, 127.0582, "u", 0.95, CandidateSource.KEYWORD)
    private val other = Candidate("2", "어니언 안국", "서울 종로구 계동길 5", "카페", 37.58, 126.98, "u", 0.62, CandidateSource.KEYWORD)

    private fun placeDraft(coords: Coords? = Coords(37.5447, 127.0582), matched: Candidate? = onion, candidates: List<Candidate> = listOf(onion, other)) =
        Draft(
            stage = Stage.READY, kind = Kind.PLACE, imageUri = "/nonexistent/shot.jpg", name = "어니언 성수", address = "서울 성동구 아차산로9길 8",
            category = "카페", note = "소금빵 맛집", coords = coords, matched = matched, candidates = candidates,
        )

    private fun couponDraft(branches: List<Branch>? = null, brand: String = "스타벅스") =
        Draft(stage = Stage.READY, kind = Kind.COUPON, imageUri = "/nonexistent/c.jpg", brand = brand, benefit = "아메리카노 Tall", endDate = "2026-12-31", branches = branches, isPlace = false)

    private class Calls {
        var closed = 0
        var saved = 0
        var kind: Kind? = null
        var refindAddress = 0
        var pinHere = 0
        var picked: Candidate? = null
        var refreshBranches = 0
    }

    /** Hosts the screen with real state, applying edits the way the ViewModel would. */
    private fun show(initial: Draft, here: Coords? = this.here, busy: Boolean = false): Pair<Calls, () -> Draft> {
        val calls = Calls()
        var current by mutableStateOf(initial)
        rule.setContent {
            SnapSpotTheme {
                AddContent(
                    draft = current,
                    here = here,
                    busy = busy,
                    actions = AddActions(
                        onClose = { calls.closed++ },
                        onSave = { calls.saved++ },
                        onKind = { calls.kind = it },
                        onChange = { current = it(current) },
                        onRefindAddress = { calls.refindAddress++ },
                        onPinHere = { calls.pinHere++ },
                        onPickCandidate = { calls.picked = it },
                        onRefreshBranches = { calls.refreshBranches++ },
                    ),
                )
            }
        }
        return calls to { current }
    }

    // --- reading ---

    @Test
    fun whileReadingTheTitleAndStepsShow() {
        show(Draft(stage = Stage.READING, imageUri = "/nonexistent/shot.jpg"))
        rule.onNodeWithText("스크린샷 읽는 중").assertExists()
        rule.onNodeWithText("스크린샷에서 장소 정보 읽기").assertExists()
        rule.onNodeWithText("카카오맵에서 장소 찾기").assertExists()
        rule.onAllNodesWithText("찾은 이름", substring = true).assertCountEquals(0)
    }

    @Test
    fun whileLocatingTheFoundNameShowsAndACouponSearchesBranches() {
        show(Draft(stage = Stage.LOCATING, kind = Kind.COUPON, imageUri = "/nonexistent/c.jpg", name = "스타벅스"))
        rule.onNodeWithText("근처 지점 찾기").assertExists()
        rule.onNodeWithText("찾은 이름: 스타벅스").assertExists()
    }

    // --- place form ---

    @Test
    fun aPlaceShowsItsFieldsAndTheMatch() {
        show(placeDraft())
        rule.onNodeWithText("새 장소 확인").assertExists()
        rule.onNodeWithText("소금빵 맛집").assertExists()
        rule.onNodeWithText("95%").assertExists()
        rule.onNodeWithText("카카오맵에서 찾은 장소예요").assertExists()
        rule.onAllNodesWithText("여기서", substring = true).assertCountEquals(1)
    }

    @Test
    fun saveNeedsAPin() {
        show(placeDraft(coords = null, matched = null, candidates = emptyList()))
        rule.onNodeWithText("위치를 못 찾았어요").assertExists()
        rule.onNodeWithText("지도에 저장").assertIsNotEnabled()
    }

    @Test
    fun saveWorksWithAPinAndCallsBack() {
        val (calls, _) = show(placeDraft())
        rule.onNodeWithText("지도에 저장").assertIsEnabled().performClick()
        assertEquals(1, calls.saved)
    }

    @Test
    fun aWeakMatchIsMarkedAsAGuess() {
        show(placeDraft(matched = other, candidates = listOf(other)))
        rule.onNodeWithText("비슷한 곳을 찾았어요. 맞는지 확인해 주세요").assertExists()
    }

    @Test
    fun aPinFromTheDeviceGeocoderSaysSo() {
        show(placeDraft(matched = null, candidates = emptyList()))
        rule.onNodeWithText("휴대폰 지도에서 위치를 찾았어요").assertExists()
    }

    @Test
    fun aScreenshotWithoutAPlaceShowsTheNotice() {
        show(placeDraft().copy(isPlace = false))
        rule.onNodeWithText("이 이미지에서는 장소를 찾기 어려웠어요. 이름과 주소를 직접 고쳐 주세요.").assertExists()
    }

    @Test
    fun otherCandidatesAreListedAndPickable() {
        val (calls, _) = show(placeDraft())
        rule.onNodeWithText("다른 후보").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("어니언 안국, 일치도 62퍼센트로 바꾸기").performScrollTo().performClick()
        assertEquals(other, calls.picked)
    }

    @Test
    fun theMatchedCandidateIsNotListedAgain() {
        show(placeDraft(candidates = listOf(onion)))
        rule.onAllNodesWithText("다른 후보").assertCountEquals(0)
    }

    @Test
    fun typingInAFieldEditsTheDraft() {
        val (_, current) = show(placeDraft())
        rule.onNode(hasSetTextAction() and hasContentDescription("장소 이름")).performTextReplacement("새 이름")
        assertEquals("새 이름", current().name)
        rule.onNode(hasSetTextAction() and hasContentDescription("메모")).performTextReplacement("고친 메모")
        assertEquals("고친 메모", current().note)
    }

    @Test
    fun pickingARadiusUpdatesTheDraft() {
        val (_, current) = show(placeDraft())
        rule.onNodeWithText("500m").performScrollTo().performClick()
        assertEquals(500, current().radius)
        rule.onNodeWithText("100m").performClick()
        assertEquals(100, current().radius)
    }

    @Test
    fun theFindAgainButtonsCallBack() {
        val (calls, _) = show(placeDraft())
        rule.onNodeWithText("주소로 다시 찾기").performScrollTo().performClick()
        rule.onNodeWithText("지금 위치로").performScrollTo().performClick()
        assertEquals(1, calls.refindAddress)
        assertEquals(1, calls.pinHere)
    }

    @Test
    fun whileBusyTheFindAgainButtonIsOff() {
        show(placeDraft(), busy = true)
        rule.onNodeWithText("주소로 다시 찾기").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun theKindSwitchCallsBackAndTheCloseButtonCloses() {
        val (calls, _) = show(placeDraft())
        rule.onNodeWithText("프랜차이즈 쿠폰").performClick()
        assertEquals(Kind.COUPON, calls.kind)
        rule.onNodeWithContentDescription("닫기").performClick()
        assertEquals(1, calls.closed)
    }

    // --- coupon form ---

    private fun branch(name: String, lat: Double) = Branch(lat, 127.0565, name, name, "서울")

    @Test
    fun aCouponShowsItsTitleAndSavesOnlyWithABrand() {
        val (calls, _) = show(couponDraft(branches = null, brand = ""))
        rule.onNodeWithText("새 쿠폰 확인").assertExists()
        rule.onNodeWithText("쿠폰함에 저장").assertIsNotEnabled()
        rule.onNode(hasSetTextAction() and hasContentDescription("브랜드")).performTextReplacement("스타벅스")
        rule.onNodeWithText("쿠폰함에 저장").assertIsEnabled().performClick()
        assertEquals(1, calls.saved)
    }

    @Test
    fun theBranchPreviewHasFourStates() {
        // Position unknown.
        show(couponDraft(), here = null)
        rule.onNodeWithText("위치를 아직 몰라요").assertExists()
    }

    @Test
    fun theBranchPreviewSearchingState() {
        show(couponDraft(branches = null))
        rule.onNodeWithText("근처 지점을 찾는 중이에요").assertExists()
    }

    @Test
    fun theBranchPreviewEmptyState() {
        show(couponDraft(branches = emptyList()))
        rule.onNodeWithText("근처에서 스타벅스 지점을 못 찾았어요").assertExists()
    }

    @Test
    fun theBranchPreviewListsTheNearestOnes() {
        show(couponDraft(branches = listOf(branch("스타벅스 가까운점", 37.5448), branch("스타벅스 중간점", 37.5476), branch("스타벅스 먼점", 37.5546), branch("스타벅스 더먼점", 37.6))))
        rule.onNodeWithText("근처 스타벅스 4곳").assertExists()
        rule.onNodeWithText("가장 가까운 곳은 스타벅스 가까운점이에요. 이 중 어디든 가까워지면 알려 드려요.").assertExists()
        rule.onNodeWithText("스타벅스 먼점").performScrollTo().assertExists()
        rule.onAllNodesWithText("스타벅스 더먼점").assertCountEquals(0) // only the first three are listed
    }

    @Test
    fun refindByBrandCallsBack() {
        val (calls, _) = show(couponDraft(branches = emptyList()))
        rule.onNodeWithText("브랜드로 다시 찾기").performScrollTo().performClick()
        assertTrue(calls.refreshBranches == 1)
    }
}
