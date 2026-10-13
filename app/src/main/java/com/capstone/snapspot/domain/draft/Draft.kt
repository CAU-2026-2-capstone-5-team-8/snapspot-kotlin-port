package com.capstone.snapspot.domain.draft

import com.capstone.snapspot.domain.model.Branch
import com.capstone.snapspot.domain.model.Candidate
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Kind
import com.capstone.snapspot.domain.model.Place

/** How close the user must get before a saved place alerts, in meters. */
val RADII: List<Int> = listOf(100, 200, 300, 500)

const val DEFAULT_RADIUS = 200

/** READING: the analyzer is reading the screenshot. LOCATING: searching maps. READY: the user confirms. */
enum class Stage { READING, LOCATING, READY }

/** A screenshot on its way to becoming a saved place or coupon. The user can edit every field before saving. */
data class Draft(
    val stage: Stage,
    /** What gets saved: a place, or a franchise coupon (alerts at any branch). */
    val kind: Kind = Kind.PLACE,
    /** What the screen shows: the picked photo at first, then the copy in app storage. */
    val imageUri: String,
    val brand: String = "",
    val benefit: String = "",
    val name: String = "",
    val address: String = "",
    val category: String = "",
    val note: String = "",
    /** YYYY-MM-DD as typed; empty means no end date. */
    val endDate: String = "",
    val coords: Coords? = null,
    val isPlace: Boolean = true,
    /** Coupons only: branches of the brand near the user, for a preview before saving. null = not looked up yet. */
    val branches: List<Branch>? = null,
    /** The Kakao result the pin came from, if any. */
    val matched: Candidate? = null,
    val candidates: List<Candidate> = emptyList(),
    val radius: Int = DEFAULT_RADIUS,
)

/** Things the screen should tell the user once. */
sealed interface AddEvent {
    /** Reading failed; the form is still open so the user can type the details. */
    data class ReadFailed(val message: String) : AddEvent

    /** "Find again by address" found nothing. */
    data object LocationNotFound : AddEvent
}

data class Saved(val kind: Kind, val id: String)

/** The picked photo, copied into app storage, plus the small JPEG sent to the analyzer. */
class PreparedImage(val path: String, val jpeg: ByteArray)

interface ImageSource {
    /** Copies the picked image into app storage and makes the analyzer's JPEG. Throws if it cannot be read. */
    suspend fun prepare(uri: String): PreparedImage

    suspend fun delete(path: String)
}

/** The user's current position, or null when it is unknown or not allowed. */
interface HerePort {
    suspend fun current(): Coords?
}

interface SavePort {
    suspend fun savePlace(place: Place)

    suspend fun saveCoupon(coupon: Coupon)
}
