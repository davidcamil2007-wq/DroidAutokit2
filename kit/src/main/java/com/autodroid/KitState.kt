package com.autodroid

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Estado compartilhado no KIT. */
object KitState {
    /** phoneId >= 0: veio do celular; phoneId < 0: veio de um app do próprio kit. */
    class Notif(
        val uid: String, val phoneId: Int, val label: String, val sender: String,
        val text: String, val pkg: String, val intent: PendingIntent?, val canReply: Boolean
    )

    val notifs = CopyOnWriteArrayList<Notif>()
    val icons = ConcurrentHashMap<String, Bitmap>()
    @Volatile var connected = false
    @Volatile var dashboardVisible = false
    @Volatile var listener: (() -> Unit)? = null

    fun changed() { listener?.invoke() }

    fun add(n: Notif) {
        notifs.removeAll { it.pkg == n.pkg && it.sender == n.sender && (it.phoneId >= 0) == (n.phoneId >= 0) }
        notifs.add(0, n)
        while (notifs.size > 30) notifs.removeAt(notifs.size - 1)
        changed()
    }

    fun removePhone(id: Int) { if (notifs.removeAll { it.phoneId == id }) changed() }
    fun removeUid(uid: String) { if (notifs.removeAll { it.uid == uid }) changed() }

    fun replies(ctx: Context): List<String> {
        val s = ctx.getSharedPreferences("kit", 0).getString("replies", null)
        val l = s?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(3)
        return if (l.isNullOrEmpty()) listOf("Já estou chegando.", "Estou dirigindo, respondo depois.", "Ok!") else l
    }

    fun short(s: String) = if (s.length > 15) s.take(14) + "…" else s

    /** Abre a notificação de um app do próprio kit. */
    fun open(ctx: Context, n: Notif) {
        try {
            val pi = n.intent
            if (pi != null) { pi.send(); return }
        } catch (e: Exception) { }
        ctx.packageManager.getLaunchIntentForPackage(n.pkg)?.let {
            ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
