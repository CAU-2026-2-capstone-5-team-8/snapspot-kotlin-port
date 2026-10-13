package com.capstone.snapspot.data.geofence

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.capstone.snapspot.data.location.LocationSource
import com.capstone.snapspot.data.store.WindowRepository
import com.capstone.snapspot.data.sync.WindowSync
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

private const val WORK_NAME = "replan"
private const val KEY_FALLBACK = "fallbackToSaved"

/**
 * Plans again around where the user is now, outside the broadcast that asked for it: a re-plan searches Kakao for
 * every coupon brand and would outlast the few seconds a receiver is allowed. A request that arrives while one
 * is already waiting is dropped, since the waiting one will use the newest position anyway.
 *
 * [fallbackToSaved]: when no position can be found, plan around the last saved one (after a reboot the plan is
 * gone and any position is better than none). Otherwise the existing plan stays.
 */
fun enqueueReplan(context: Context, fallbackToSaved: Boolean) {
    val request = OneTimeWorkRequestBuilder<ReplanWorker>()
        .setInputData(Data.Builder().putBoolean(KEY_FALLBACK, fallbackToSaved).build())
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
}

class ReplanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun sync(): WindowSync

        fun location(): LocationSource

        fun windows(): WindowRepository
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val fix = deps.location().quickFix()
        val here = fix ?: if (inputData.getBoolean(KEY_FALLBACK, false)) deps.windows().load()?.here else null
        if (here == null) return Result.success() // no position: keep the plan we have
        if (fix != null) deps.location().update(fix)
        deps.sync().sync(here)
        return Result.success()
    }
}
