package com.autodroid.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/** Mantém o envio ao kit ativo (serviço em primeiro plano, para o sistema não encerrar). */
class LinkService : Service() {

    companion object { @Volatile var running = false }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("link", "Envio ao kit", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, "link")
            .setContentTitle("AutoDroid: enviando notificações ao kit")
            .setContentText("Ligue o hotspot do celular.")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        KitLink.start(applicationContext)
        running = true
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        running = false
        KitLink.stop()
        super.onDestroy()
    }
}
