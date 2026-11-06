package com.autodroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Religa a conexão com o celular quando o kit liga. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (ctx.getSharedPreferences("kit", 0).getBoolean("enabled", false)) {
            try { ctx.startForegroundService(Intent(ctx, KitService::class.java)) } catch (e: Exception) { }
        }
    }
}
