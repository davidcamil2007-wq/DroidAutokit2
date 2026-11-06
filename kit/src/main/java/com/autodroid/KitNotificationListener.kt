package com.autodroid

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Notificações dos apps instalados NO PRÓPRIO KIT (WhatsApp do kit, alarme, SMS/ligação do kit...). */
class KitNotificationListener : NotificationListenerService() {

    companion object {
        private val apps = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
            "com.instagram.android", "com.facebook.orca", "com.google.android.apps.messaging"
        )
        private val hints = listOf("bluetooth", "dialer", "telecom", "phone", "mms", "sms", "clock", "alarm")
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (e: Exception) { pkg }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val all = getSharedPreferences("kit", 0).getBoolean("all", false)
        val cat = n.category
        val important = pkg in apps ||
            cat == Notification.CATEGORY_MESSAGE || cat == Notification.CATEGORY_CALL ||
            cat == Notification.CATEGORY_ALARM || cat == Notification.CATEGORY_REMINDER ||
            hints.any { pkg.contains(it, ignoreCase = true) }
        if (!important) {
            if (!all) return
            if (n.flags and Notification.FLAG_ONGOING_EVENT != 0 || pkg == "android" || pkg == "com.android.systemui") return
        }
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        if (Regex("^\\d+ (novas )?mensagens?").containsMatchIn(text)) return

        val label = appLabel(pkg)
        val sender = title.replace(Regex("\\s*\\(\\d+.*\\)$"), "").trim().ifBlank { label }
        val item = KitState.Notif("k${sbn.key}", -1, label, sender, text, pkg, n.contentIntent, false)
        KitState.add(item)
        Popup.show(applicationContext, item)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        KitState.removeUid("k${sbn.key}")
    }
}
