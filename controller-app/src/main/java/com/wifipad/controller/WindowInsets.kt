package com.wifipad.controller

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keeps interactive content clear of system bars while allowing edge-to-edge drawing.
 * Existing view padding is preserved and insets are added exactly once.
 */
internal fun View.applySystemBarInsets() {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val safeInsets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(
            initialLeft + safeInsets.left,
            initialTop + safeInsets.top,
            initialRight + safeInsets.right,
            initialBottom + safeInsets.bottom
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

/** Disables touch haptics on every view owned by this app screen. */
internal fun View.disableHapticFeedbackRecursively() {
    isHapticFeedbackEnabled = false
    if (this is ViewGroup) {
        for (index in 0 until childCount) getChildAt(index).disableHapticFeedbackRecursively()
    }
}

/** Apply the same no-haptics policy to app-owned dialogs after show(). */
internal fun Dialog.disableHapticFeedbackRecursively() {
    window?.decorView?.disableHapticFeedbackRecursively()
}
