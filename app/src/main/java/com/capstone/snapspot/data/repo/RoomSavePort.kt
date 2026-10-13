package com.capstone.snapspot.data.repo

import com.capstone.snapspot.domain.draft.SavePort
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import javax.inject.Inject

/** Where a confirmed draft is saved. */
class RoomSavePort @Inject constructor(
    private val places: PlaceRepository,
    private val coupons: CouponRepository,
) : SavePort {
    override suspend fun savePlace(place: Place) = places.save(place)

    override suspend fun saveCoupon(coupon: Coupon) = coupons.save(coupon)
}
