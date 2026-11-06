package com.autodroid.phone

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat

class MainActivity : Activity() {

    private lateinit var status: TextView
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { render(); h.postDelayed(this, 1000) }
    }
    private val prefs by lazy { getSharedPreferences("phone", 0) }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(Color.BLACK)
        }
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f }
        root.addView(status)

        fun title(t: String) {
            root.addView(TextView(this).apply {
                text = t; setTextColor(0xFF90CAF9.toInt()); textSize = 15f
                setPadding(0, dp(22), 0, 0)
            })
        }
        fun btn(label: String, color: Int, height: Int = 72, click: () -> Unit) {
            root.addView(Button(this).apply {
                text = label; textSize = 20f; setTextColor(Color.WHITE)
                setBackgroundColor(color)
                setOnClickListener { click() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height)).apply { setMargins(0, dp(10), 0, 0) })
        }

        val green = 0xFF2E7D32.toInt(); val red = 0xFFC62828.toInt()
        val blue = 0xFF1565C0.toInt(); val gray = 0xFF455A64.toInt()

        title("ENVIO PARA O KIT")
        btn("ATIVAR ENVIO PARA O KIT", green, 100) { startLink() }
        btn("DESATIVAR ENVIO", red) { stopService(Intent(this, LinkService::class.java)) }
        btn("LIGAR O HOTSPOT (abre as configurações)", blue) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }

        title("CONFIGURAR (UMA VEZ)")
        btn("1. ACESSO ÀS NOTIFICAÇÕES", gray) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btn("2. ESCOLHER OS APPS QUE APARECEM NO KIT", blue) { pickApps() }
        btn("3. PERMITIR AVISOS", gray) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        btn("4. BATERIA SEM RESTRIÇÕES", gray) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }

        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(root) })

        if (s == null && listenerOn()) startLink()
    }

    private fun startLink() { startForegroundService(Intent(this, LinkService::class.java)) }

    private fun listenerOn() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun allowed(): Set<String> =
        prefs.getString("allow", null)?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: PhoneListener.defaults

    private fun pickApps() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val keys = apps.map { it.first }
        val labels = apps.map { it.second }.toTypedArray()
        val cur = allowed()
        val checked = BooleanArray(keys.size) { keys[it] in cur }
        AlertDialog.Builder(this)
            .setTitle("Apps que podem aparecer no kit")
            .setMultiChoiceItems(labels, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("SALVAR") { _, _ ->
                val keep = cur - keys.toSet()      // apps sem ícone (ex.: discador) continuam como estavam
                val sel = keys.filterIndexed { i, _ -> checked[i] }
                prefs.edit().putString("allow", (keep + sel).joinToString(",")).apply()
            }
            .setNegativeButton("CANCELAR", null).show()
    }

    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { h.removeCallbacks(tick); super.onPause() }

    private fun render() {
        status.text = "Envio ao kit: ${if (LinkService.running) "ATIVO" else "desligado"}\n" +
            "Kit: ${if (KitLink.connected) "CONECTADO" else "não conectado"}\n" +
            "Acesso às notificações: ${if (listenerOn()) "OK" else "FALTA (botão 1)"}\n" +
            "Apps permitidos: ${allowed().size}\n\n" +
            "Dica: mensagens, ligações e códigos de SMS aparecem na tela do kit. " +
            "Tire da lista (botão 2) os apps que você não quer mostrar."
    }
}
