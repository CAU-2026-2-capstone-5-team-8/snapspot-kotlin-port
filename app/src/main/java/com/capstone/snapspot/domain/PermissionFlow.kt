package com.capstone.snapspot.domain

/** The permission the guide asks for next. Order matters: "always" location is only offered once location works. */
enum class PermissionStep { FOREGROUND_LOCATION, NOTIFICATIONS, BACKGROUND_LOCATION, DONE }

/** A system pop-up can answer the request, or only the system settings screen can. */
enum class HowToAsk { DIALOG, SETTINGS }

// API levels where the rules change.
private const val API_Q = 29 // separate background location permission
private const val API_R = 30 // background location can only be granted in settings
private const val API_TIRAMISU = 33 // notifications became a runtime permission

/** The first missing permission, in the order the guide walks through them. */
fun nextStep(perms: AlertPermissions, sdk: Int): PermissionStep = when {
    !perms.fg -> PermissionStep.FOREGROUND_LOCATION
    !perms.notif -> PermissionStep.NOTIFICATIONS
    // Before Android 10 there is nothing to grant beyond foreground location.
    !perms.bg && sdk >= API_Q -> PermissionStep.BACKGROUND_LOCATION
    else -> PermissionStep.DONE
}

/**
 * Whether a pop-up can still grant [step]. [denied] means it was refused before: after that the system may
 * stop showing the pop-up, so the only way left is the settings screen. [PermissionStep.DONE] asks for nothing.
 */
fun howToAsk(step: PermissionStep, sdk: Int, denied: Boolean): HowToAsk = when (step) {
    PermissionStep.FOREGROUND_LOCATION -> if (denied) HowToAsk.SETTINGS else HowToAsk.DIALOG
    PermissionStep.NOTIFICATIONS -> if (sdk >= API_TIRAMISU && !denied) HowToAsk.DIALOG else HowToAsk.SETTINGS
    PermissionStep.BACKGROUND_LOCATION -> if (sdk == API_Q && !denied) HowToAsk.DIALOG else HowToAsk.SETTINGS
    PermissionStep.DONE -> HowToAsk.SETTINGS
}

/** Which steps the guide lists at all on this Android version. */
fun guideSteps(sdk: Int): List<PermissionStep> = buildList {
    add(PermissionStep.FOREGROUND_LOCATION)
    add(PermissionStep.NOTIFICATIONS)
    if (sdk >= API_Q) add(PermissionStep.BACKGROUND_LOCATION)
}

/** Whether [step] is already satisfied by [perms]. */
fun isGranted(step: PermissionStep, perms: AlertPermissions): Boolean = when (step) {
    PermissionStep.FOREGROUND_LOCATION -> perms.fg
    PermissionStep.NOTIFICATIONS -> perms.notif
    PermissionStep.BACKGROUND_LOCATION -> perms.bg
    PermissionStep.DONE -> true
}
