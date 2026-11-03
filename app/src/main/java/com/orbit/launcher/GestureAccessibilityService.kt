package com.orbit.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/** Performs explicit user-requested system actions only. It never reads window content. */
class GestureAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: WeakReference<GestureAccessibilityService>? = null
        fun perform(action: Int): Boolean = instance?.get()?.performGlobalAction(action) == true
    }
    override fun onServiceConnected() { super.onServiceConnected(); instance = WeakReference(this) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { if (instance?.get() === this) instance = null; super.onDestroy() }
}
