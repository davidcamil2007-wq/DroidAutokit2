package com.autodroid.phone

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Lê as notificações do celular e envia ao kit. Só os apps permitidos (ver tela do app) + ligações e alarmes. */
class PhoneListener : NotificationListenerService() {

    companion object {
        val defaults = setOf(
            // redes sociais e mensagens
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.instagram.android",
            "com.facebook.katana", "com.facebook.orca", "com.twitter.android", "com.zhiliaoapp.musically",
            // SMS
            "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging",
            // ligações
            "com.google.android.dialer", "com.android.dialer", "com.android.incallui",
            "com.android.server.telecom", "com.samsung.android.dialer",
            // alarme e e-mail
            "com.google.android.deskclock", "com.android.deskclock", "com.google.android.gm"
        )
    }

    override fun onListenerConnected() { KitLink.start(applicationContext) }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (e: Exception) { pkg }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val cat = n.category
        val allowed = getSharedPreferences("phone", 0).getString("allow", null)
            ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: defaults
        if (pkg !in allowed && cat != Notification.CATEGORY_CALL && cat != Notification.CATEGORY_ALARM) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0 && cat != Notification.CATEGORY_CALL) return

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        if (Regex("^\\d+ (novas )?mensagens?").containsMatchIn(text)) return

        val reply = n.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
            ?: Notification.WearableExtender(n).actions.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
        val label = appLabel(pkg)
        val cleanTitle = title.replace(Regex("\\s*\\(\\d+.*\\)$"), "").trim().ifBlank { label }

        val msg = Msg(Store.nextId(), sbn.key, label, cleanTitle, text, pkg, reply)
        Store.add(msg)
        KitLink.push(applicationContext, msg)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Store.byKey(sbn.key)?.let { KitLink.removed(it.id) }
    }
}
