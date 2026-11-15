package io.github.dwicki.tidytudo.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** `POST_NOTIFICATIONS` exists from Android 13; on Android 12 (minSdk) notifications are always allowed. */
object NotificationAccess {
    val needsRuntimePermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context): Boolean =
        !needsRuntimePermission || context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Only valid when [needsRuntimePermission] is true; callers check that first. */
    @SuppressLint("InlinedApi")
    const val PERMISSION = Manifest.permission.POST_NOTIFICATIONS
}
