package com.autodroid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/** Roda no KIT: conecta no celular (gateway do hotspot) e recebe as notificações dele. */
class KitService : Service() {

    companion object {
        const val PORT = 5556
        @Volatile var status = "parado"
        @Volatile var instance: KitService? = null
    }

    @Volatile private var active = false
    @Volatile private var sock: Socket? = null
    @Volatile private var outS: DataOutputStream? = null
    private val sender = Executors.newSingleThreadExecutor()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("kit", "Painel do kit", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, "kit")
            .setContentTitle("AutoDroid Kit ativo")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true).build()
        ServiceCompat.startForeground(this, 3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        instance = this
        active = true
        Thread { loop() }.start()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int = START_STICKY

    @Suppress("DEPRECATION")
    private fun gateway(): String {
        val w = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val g = w.dhcpInfo.gateway
        return if (g == 0) "192.168.43.1"
        else "${g and 255}.${(g shr 8) and 255}.${(g shr 16) and 255}.${(g shr 24) and 255}"
    }

    private fun loop() {
        val prefs = getSharedPreferences("kit", 0)
        while (active) {
            val manual = prefs.getString("ip", "").orEmpty().trim()
            val ip = if (manual.isNotEmpty()) manual else gateway()
            status = "procurando o celular em $ip (hotspot ligado? envio ativo no celular?)"
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, PORT), 4000)
                s.tcpNoDelay = true
                s.keepAlive = true
                sock = s
                outS = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                status = "conectado ao celular ($ip)"
                KitState.connected = true
                KitState.changed()
                val inp = DataInputStream(BufferedInputStream(s.getInputStream(), 65536))
                while (active) {
                    when (inp.readUnsignedByte()) {
                        20 -> {
                            val pkg = inp.readUTF()
                            val b = ByteArray(inp.readInt())
                            inp.readFully(b)
                            BitmapFactory.decodeByteArray(b, 0, b.size)?.let { KitState.icons[pkg] = it }
                            KitState.changed()
                        }
                        10 -> {
                            val id = inp.readInt()
                            val label = inp.readUTF()
                            val title = inp.readUTF()
                            val text = inp.readUTF()
                            val pkg = inp.readUTF()
                            val canReply = inp.readBoolean()
                            val n = KitState.Notif("p$id", id, label, title, text, pkg, null, canReply)
                            KitState.add(n)
                            Popup.show(applicationContext, n)
                        }
                        12 -> KitState.removePhone(inp.readInt())
                    }
                }
            } catch (e: Exception) {
            } finally {
                try { sock?.close() } catch (e: Exception) { }
                sock = null
                outS = null
                KitState.connected = false
                KitState.changed()
            }
            if (active) {
                status = "desconectado, tentando de novo..."
                try { Thread.sleep(2500) } catch (e: InterruptedException) { }
            }
        }
    }

    fun sendReply(id: Int, text: String) {
        sender.execute {
            try {
                outS?.let { synchronized(it) { it.writeByte(11); it.writeInt(id); it.writeUTF(text); it.flush() } }
            } catch (e: Exception) { }
        }
    }

    override fun onDestroy() {
        active = false
        status = "parado"
        instance = null
        try { sock?.close() } catch (e: Exception) { }
        Popup.hide()
        super.onDestroy()
    }
}
