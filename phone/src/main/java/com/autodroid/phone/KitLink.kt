package com.autodroid.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * CELULAR -> KIT pela rede do hotspot (porta 5556).
 *  celular -> kit: 20 ícone [pkg][int tam][png] | 10 notificação [id][app][título][texto][pkg][podeResponder] | 12 removida [id]
 *  kit -> celular: 11 resposta [id][texto]
 */
object KitLink {
    const val PORT = 5556
    @Volatile private var started = false
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: Socket? = null
    @Volatile private var out: DataOutputStream? = null
    private val io = Executors.newSingleThreadExecutor()
    private val iconSent = HashSet<String>()          // só usado na thread "io"
    private val iconCache = HashMap<String, ByteArray>()

    val connected: Boolean get() = out != null

    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        Thread {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(PORT))
                server = ss
                while (started) {
                    val s = ss.accept()
                    s.tcpNoDelay = true
                    s.keepAlive = true
                    try { client?.close() } catch (e: IOException) { }
                    client = s
                    out = DataOutputStream(BufferedOutputStream(s.getOutputStream(), 65536))
                    io.execute { iconSent.clear() }
                    Thread {
                        try { read(app, DataInputStream(BufferedInputStream(s.getInputStream()))) } catch (e: IOException) { }
                        if (client === s) out = null
                        try { s.close() } catch (e: IOException) { }
                    }.start()
                }
            } catch (e: IOException) { }
            started = false
        }.start()
    }

    fun stop() {
        started = false
        try { server?.close() } catch (e: IOException) { }
        try { client?.close() } catch (e: IOException) { }
        out = null
    }

    private fun iconPng(ctx: Context, pkg: String): ByteArray = try {
        val d = ctx.packageManager.getApplicationIcon(pkg)
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        val bo = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bo)
        bo.toByteArray()
    } catch (e: Exception) { ByteArray(0) }

    fun push(ctx: Context, m: Msg) {
        val app = ctx.applicationContext
        io.execute {
            val o = out ?: return@execute
            try {
                if (iconSent.add(m.pkg)) {
                    val png = iconCache.getOrPut(m.pkg) { iconPng(app, m.pkg) }
                    if (png.isNotEmpty()) synchronized(o) {
                        o.writeByte(20); o.writeUTF(m.pkg); o.writeInt(png.size); o.write(png)
                    }
                }
                synchronized(o) {
                    o.writeByte(10)
                    o.writeInt(m.id)
                    o.writeUTF(m.label)
                    o.writeUTF(m.title)
                    o.writeUTF(m.text)
                    o.writeUTF(m.pkg)
                    o.writeBoolean(m.reply != null)
                    o.flush()
                }
            } catch (e: IOException) {
                out = null
                try { client?.close() } catch (e2: IOException) { }
            }
        }
    }

    fun removed(id: Int) {
        io.execute {
            val o = out ?: return@execute
            try {
                synchronized(o) { o.writeByte(12); o.writeInt(id); o.flush() }
            } catch (e: IOException) {
                out = null
                try { client?.close() } catch (e2: IOException) { }
            }
        }
    }

    private fun read(ctx: Context, i: DataInputStream) {
        while (true) {
            when (i.readUnsignedByte()) {
                11 -> {
                    val id = i.readInt()
                    val text = i.readUTF()
                    val m = Store.byId(id)
                    if (m != null) Reply.send(ctx, m, text)
                }
            }
        }
    }
}
