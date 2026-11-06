package com.autodroid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import kotlin.math.max

/** Tela principal do KIT (estilo Android Auto): apps do kit à esquerda, notificações à direita. */
class KitActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("kit", 0) }
    private lateinit var status: TextView
    private lateinit var grid: GridLayout
    private lateinit var notifBox: LinearLayout
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { renderStatus(); h.postDelayed(this, 2000) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wc = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF0B0B0F.toInt())
        }

        // ---- esquerda: relógio + apps ----
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(6))
        }
        top.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "HH:mm"
            textSize = 34f; setTextColor(Color.WHITE)
        })
        status = TextView(this).apply {
            setTextColor(0xFF9E9E9E.toInt()); textSize = 13f
            setPadding(dp(16), 0, dp(8), 0)
        }
        top.addView(status, LinearLayout.LayoutParams(0, wc, 1f))
        top.addView(Button(this).apply {
            text = "⚙ MENU"; textSize = 17f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(0xFF2A2A33.toInt()); cornerRadius = dp(12).toFloat() }
            setOnClickListener { menu() }
        }, LinearLayout.LayoutParams(wc, dp(56)))
        left.addView(top)

        val cols = max(2, (resources.displayMetrics.widthPixels * 0.55f / dp(156)).toInt())
        grid = GridLayout(this).apply { columnCount = cols; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        left.addView(ScrollView(this).apply { addView(grid) }, LinearLayout.LayoutParams(mp, 0, 1f))

        // ---- direita: notificações ----
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF14141A.toInt())
        }
        right.addView(TextView(this).apply {
            text = "Notificações"; textSize = 20f; setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(16), dp(16), dp(8))
        })
        notifBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(10), dp(10)) }
        right.addView(ScrollView(this).apply { addView(notifBox) }, LinearLayout.LayoutParams(mp, 0, 1f))

        root.addView(left, LinearLayout.LayoutParams(0, mp, 1.25f))
        root.addView(right, LinearLayout.LayoutParams(0, mp, 1f))
        setContentView(root)

        prefs.edit().putBoolean("enabled", true).apply()
        startForegroundService(Intent(this, KitService::class.java))
    }

    override fun onResume() {
        super.onResume()
        KitState.dashboardVisible = true
        KitState.listener = { runOnUiThread { renderNotifs() } }
        renderGrid(); renderNotifs(); renderStatus()
        h.post(tick)
    }

    override fun onPause() {
        KitState.dashboardVisible = false
        KitState.listener = null
        h.removeCallbacks(tick)
        super.onPause()
    }

    // ---------- estado ----------
    private fun listenerOn() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun online(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun renderStatus() {
        status.text = (if (KitState.connected) "Celular: conectado" else "Celular: não conectado") +
            "\n" + (if (online()) "Internet: OK" else "Sem internet (hotspot)")
    }

    // ---------- apps ----------
    private fun pins(): List<String> {
        val s = prefs.getString("pins", null)
            ?: return listOf("com.waze", "com.spotify.music", "com.whatsapp", "com.google.android.apps.maps")
        return s.split(",").filter { it.isNotBlank() }
    }

    private fun tile(label: String, d: Drawable?, emoji: String? = null, click: () -> Unit): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply { setColor(0xFF1E1E26.toInt()); cornerRadius = dp(20).toFloat() }
            setOnClickListener { click() }
        }
        if (emoji != null) {
            v.addView(TextView(this).apply { text = emoji; textSize = 40f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) })
        } else {
            v.addView(ImageView(this).apply { setImageDrawable(d) }, LinearLayout.LayoutParams(dp(72), dp(72)))
        }
        v.addView(TextView(this).apply {
            text = label; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, 0)
        })
        v.layoutParams = GridLayout.LayoutParams().apply {
            width = dp(140); height = dp(140); setMargins(dp(8), dp(8), dp(8), dp(8))
        }
        return v
    }

    private fun renderGrid() {
        grid.removeAllViews()
        val pm = packageManager
        for (pkg in pins()) {
            if (pm.getLaunchIntentForPackage(pkg) == null) continue
            val label = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            grid.addView(tile(label, pm.getApplicationIcon(pkg)) {
                pm.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            })
        }
        grid.addView(tile("Wi-Fi", null, "📶") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        grid.addView(tile("Editar apps", null, "＋") { pickApps() })
    }

    // ---------- notificações ----------
    private fun iconFor(n: KitState.Notif): Drawable? =
        KitState.icons[n.pkg]?.let { BitmapDrawable(resources, it) }
            ?: try { packageManager.getApplicationIcon(n.pkg) } catch (e: Exception) { null }

    private fun renderNotifs() {
        notifBox.removeAllViews()
        if (KitState.notifs.isEmpty()) {
            notifBox.addView(TextView(this).apply {
                text = if (KitState.connected) "Sem notificações."
                else "Sem notificações.\nLigue o hotspot do celular e ative o envio no app AutoDroid Celular."
                textSize = 16f; setTextColor(0xFF9E9E9E.toInt())
                setPadding(dp(6), dp(6), dp(6), dp(6))
            })
            return
        }
        val replies = KitState.replies(this)
        for (n in KitState.notifs) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { setColor(0xFF1F1F27.toInt()); cornerRadius = dp(14).toFloat() }
            }
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            iconFor(n)?.let { ic ->
                head.addView(ImageView(this).apply { setImageDrawable(ic) }, LinearLayout.LayoutParams(dp(24), dp(24)))
            }
            head.addView(TextView(this).apply {
                text = n.label + (if (n.phoneId >= 0) "  •  celular" else "  •  kit")
                textSize = 13f; setTextColor(0xFF90CAF9.toInt()); setPadding(dp(8), 0, 0, 0)
            })
            card.addView(head)
            card.addView(TextView(this).apply {
                text = n.sender; textSize = 18f; setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            })
            card.addView(TextView(this).apply {
                text = n.text; textSize = 16f; setTextColor(0xFFCCCCCC.toInt())
                maxLines = 3; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, dp(8))
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun b(label: String, color: Int, w: Float, click: () -> Unit) {
                row.addView(Button(this).apply {
                    text = label; textSize = 14f; setTextColor(Color.WHITE)
                    background = GradientDrawable().apply { setColor(color); cornerRadius = dp(10).toFloat() }
                    setOnClickListener { click() }
                }, LinearLayout.LayoutParams(0, dp(56), w).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            if (n.phoneId >= 0 && n.canReply) {
                replies.forEach { r ->
                    b(KitState.short(r), 0xFF1565C0.toInt(), 1f) {
                        KitService.instance?.sendReply(n.phoneId, r)
                        Toast.makeText(this@KitActivity, "Enviado: $r", Toast.LENGTH_SHORT).show()
                        KitState.removePhone(n.phoneId)
                    }
                }
            } else if (n.phoneId < 0) {
                b("ABRIR", 0xFF1565C0.toInt(), 3f) { KitState.open(this@KitActivity, n) }
            }
            b("✕", 0xFF444444.toInt(), 0.6f) { KitState.notifs.remove(n); renderNotifs() }
            card.addView(row)
            notifBox.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(10))
            })
        }
    }

    // ---------- menu ----------
    private fun openNotifAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            status.text = "Este kit não tem a tela de acesso às notificações."
        }
    }

    private fun menu() {
        val popup = prefs.getBoolean("popup", true)
        val all = prefs.getBoolean("all", false)
        val items = arrayOf(
            "Escolher apps da tela principal",
            "Respostas rápidas (editar)",
            "Aviso sobre outros apps: ${if (popup) "LIGADO" else "DESLIGADO"}",
            "Permitir exibir sobre outros apps",
            "Testar o aviso",
            "Notificações dos apps do kit: ${if (listenerOn()) "ativas" else "ATIVAR"}",
            "Diagnóstico (mostrar tudo do kit): ${if (all) "LIGADO" else "DESLIGADO"}",
            "IP do celular (avançado)",
            "Configurações de Wi-Fi"
        )
        AlertDialog.Builder(this).setTitle("AutoDroid Kit").setItems(items) { _, i ->
            when (i) {
                0 -> pickApps()
                1 -> editReplies()
                2 -> prefs.edit().putBoolean("popup", !popup).apply()
                3 -> try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    status.text = "Este kit não tem a tela de sobreposição."
                }
                4 -> Popup.test(this)
                5 -> openNotifAccess()
                6 -> prefs.edit().putBoolean("all", !all).apply()
                7 -> askIp()
                8 -> startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }.show()
    }

    private fun editReplies() {
        val e = EditText(this)
        e.setText(KitState.replies(this).joinToString("\n"))
        e.minLines = 3
        e.gravity = Gravity.TOP
        AlertDialog.Builder(this).setTitle("Respostas rápidas (uma por linha, até 3)").setView(e)
            .setPositiveButton("SALVAR") { _, _ -> prefs.edit().putString("replies", e.text.toString()).apply(); renderNotifs() }
            .setNegativeButton("CANCELAR", null).show()
    }

    private fun askIp() {
        val e = EditText(this)
        e.setText(prefs.getString("ip", ""))
        e.hint = "vazio = automático"
        AlertDialog.Builder(this).setTitle("IP do celular").setView(e)
            .setPositiveButton("SALVAR") { _, _ -> prefs.edit().putString("ip", e.text.toString().trim()).apply() }
            .setNegativeButton("CANCELAR", null).show()
    }

    private fun pickApps() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val keys = apps.map { it.first }
        val labels = apps.map { it.second }.toTypedArray()
        val cur = pins()
        val checked = BooleanArray(keys.size) { keys[it] in cur }
        AlertDialog.Builder(this)
            .setTitle("Apps na tela principal")
            .setMultiChoiceItems(labels, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("SALVAR") { _, _ ->
                val sel = keys.filterIndexed { i, _ -> checked[i] }
                val ordered = cur.filter { it in sel } + sel.filter { it !in cur }
                prefs.edit().putString("pins", ordered.joinToString(",")).apply()
                renderGrid()
            }
            .setNegativeButton("CANCELAR", null).show()
    }
}
