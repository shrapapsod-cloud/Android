package app.ioshome

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

/** Счётчики непрочитанного: пакет -> число. */
object Badges {
    val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
}

/** Считает активные уведомления по приложениям (как красные кружки на iOS). */
class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onListenerDisconnected() { Badges.counts.value = emptyMap() }

    private fun refresh() {
        val list = try { activeNotifications } catch (_: Exception) { null } ?: return
        val m = HashMap<String, Int>()
        for (s in list) {
            if (s.isOngoing || !s.isClearable) continue
            val n = s.notification ?: continue
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            m[s.packageName] = (m[s.packageName] ?: 0) + (if (n.number > 0) n.number else 1)
        }
        Badges.counts.value = m
    }
}
