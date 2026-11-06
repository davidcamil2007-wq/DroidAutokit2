package com.autodroid

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Aviso pequeno que aparece no canto da tela, por cima do Waze, Spotify etc. */
object Popup {
    private val h = Handler(Looper.getMainLooper())
    private var card: View? = null
    private var wm: WindowManager? = null
    private val hideR = Runnable { hide() }

    fun test(ctx: Context) = show(
        ctx, KitState.Notif("test", -1, "AutoDroid", "Teste", "Se você está vendo este aviso, a sobreposição funciona.", "", null, false), true
    )

    fun show(ctx: Context, n: KitState.Notif, force: Boolean = false) {
        val app = ctx.applicationContext
        h.post {
            val prefs = app.getSharedPreferences("kit", 0)
            if (!force && (!prefs.getBoolean("popup", true) || KitState.dashboardVisible)) return@post
            if (!Settings.canDrawOverlays(app)) return@post
            hide()
            val d = app.resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()

            val root = LinearLayout(app).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply { setColor(0xF0202020.toInt()); cornerRadius = dp(16).toFloat() }
            }
            root.addView(TextView(app).apply {
                text = n.label; setTextColor(0xFF90CAF9.toInt()); textSize = 13f
            })
            root.addView(TextView(app).apply {
                text = n.sender; setTextColor(Color.WHITE); textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
            root.addView(TextView(app).apply {
                text = n.text; setTextColor(0xFFDDDDDD.toInt()); textSize = 19f
                maxLines = 3; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, dp(10))
            })
            val row = LinearLayout(app).apply { orientation = LinearLayout.HORIZONTAL }
            fun b(label: String, color: Int, w: Float, click: () -> Unit) {
                row.addView(Button(app).apply {
                    text = label; textSize = 15f; setTextColor(Color.WHITE)
                    minHeight = dp(60); minimumHeight = dp(60)
                    background = GradientDrawable().apply { setColor(color); cornerRadius = dp(10).toFloat() }
                    setOnClickListener { click() }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
            if (n.phoneId >= 0 && n.canReply) {
                KitState.replies(app).forEach { r ->
                    b(KitState.short(r), 0xFF1565C0.toInt(), 1f) {
                        KitService.instance?.sendReply(n.phoneId, r)
                        Toast.makeText(app, "Enviado: $r", Toast.LENGTH_SHORT).show()
                        KitState.removePhone(n.phoneId)
                        hide()
                    }
                }
            } else if (n.phoneId < 0 && !force) {
                b("ABRIR", 0xFF1565C0.toInt(), 3f) { hide(); KitState.open(app, n) }
            }
            b("✕", 0xFF555555.toInt(), 0.6f) { hide() }
            root.addView(row)

            val lp = WindowManager.LayoutParams(
                dp(440), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(8) }
            try {
                val w = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                w.addView(root, lp)
                wm = w
                card = root
                h.postDelayed(hideR, 20_000)
            } catch (e: Exception) { }
        }
    }

    fun hide() {
        h.removeCallbacks(hideR)
        card?.let { try { wm?.removeView(it) } catch (e: Exception) { } }
        card = null
    }
}
