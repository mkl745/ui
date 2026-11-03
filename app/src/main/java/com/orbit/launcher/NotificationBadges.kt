package com.orbit.launcher

import android.os.Process
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Counts only the current profile's active notifications; contents are never retained. */
class NotificationBadges : NotificationListenerService() {
    companion object {
        private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val badgeCounts = counts.asStateFlow()
    }

    override fun onListenerConnected() { super.onListenerConnected(); refresh() }
    override fun onListenerDisconnected() { counts.value = emptyMap(); super.onListenerDisconnected() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh() }

    private fun refresh() {
        val active = try { activeNotifications ?: emptyArray() } catch (_: SecurityException) { emptyArray() }
        counts.value = active.asSequence().filter { it.user == Process.myUserHandle() && !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .groupingBy { it.packageName }.eachCount()
    }
}
