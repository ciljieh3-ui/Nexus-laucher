package com.example.system

import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 100% local, event-driven notification badge counter without Coroutines/Flow overhead.
 * Notification text/title/content is NEVER read, stored, or transmitted.
 */
class NexusNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        recomputeCounts()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recomputeCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        recomputeCounts()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        updateBadges(emptyMap())
    }

    private fun recomputeCounts() {
        val active = try {
            activeNotifications
        } catch (_: Exception) {
            null
        } ?: return

        val map = HashMap<String, Int>()
        for (sbn in active) {
            if (sbn.isOngoing) continue
            val pkg = sbn.packageName ?: continue
            map[pkg] = (map[pkg] ?: 0) + 1
        }
        updateBadges(map)
    }

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val listeners = CopyOnWriteArrayList<(Map<String, Int>) -> Unit>()

        @Volatile
        var currentBadges: Map<String, Int> = emptyMap()
            private set

        fun addBadgeListener(listener: (Map<String, Int>) -> Unit) {
            listeners.addIfAbsent(listener)
            listener(currentBadges)
        }

        fun removeBadgeListener(listener: (Map<String, Int>) -> Unit) {
            listeners.remove(listener)
        }

        private fun updateBadges(next: Map<String, Int>) {
            currentBadges = next
            mainHandler.post {
                for (listener in listeners) {
                    listener(next)
                }
            }
        }
    }
}
