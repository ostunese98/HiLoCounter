package com.example.hilocounter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FloatingCounterService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var tvRc: TextView
    private lateinit var tvTc: TextView
    private lateinit var tvAdvantage: TextView
    private lateinit var tvBet: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvAction: TextView
    private lateinit var tvActionDetail: TextView

    private val totalCards = 6 * 52
    private val ranks = listOf("2","3","4","5","6","7","8","9","10","J","Q","K","A")
    private val low = setOf("2","3","4","5","6")
    private val high = setOf("10","J","Q","K","A")

    private val playerCards = mutableListOf<String>()
    private val dealerCards = mutableListOf<String>()
    private val history = mutableListOf<Pair<String, String>>()
    private var rc = 0

    private data class HandRecord(
        val tc: Double, val bet: Int, val result: String, val profit: Double
    )
    private val hands = mutableListOf<HandRecord>()
    private var handStartTc: Double? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        setupOverlay()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_counter, null)

        tvRc = overlayView.findViewById(R.id.tvRc)
        tvTc = overlayView.findViewById(R.id.tvTc)
        tvAdvantage = overlayView.findViewById(R.id.tvAdvantage)
        tvBet = overlayView.findViewById(R.id.tvBet)
        tvStats = overlayView.findViewById(R.id.tvStats)
        tvAction = overlayView.findViewById(R.id.tvAction)
        tvActionDetail = overlayView.findViewById(R.id.tvActionDetail)

        buildPad(overlayView.findViewById(R.id.padPlayer), "P")
        buildPad(overlayView.findViewById(R.id.padDealer), "D")

        overlayView.findViewById<Button>(R.id.btnUndo).setOnClickListener { undo() }
        overlayView.findViewById<Button>(R.id.btnNextHand).setOnClickListener { nextHand() }
        overlayView.findViewById<Button>(R.id.btnClose).setOnClickListener { stopSelf() }
        overlayView.findViewById<Button>(R.id.btnWin).setOnClickListener { recordResult("W") }
        overlayView.findViewById<Button>(R.id.btnBj).setOnClickListener { recordResult("BJ") }
        overlayView.findViewById<Button>(R.id.btnPush).setOnClickListener { recordResult("P") }
        overlayView.findViewById<Button>(R.id.btnLose).setOnClickListener { recordResult("L") }
        overlayView.findViewById<Button>(R.id.btnExport).setOnClickListener { exportCsv() }
        overlayView.findViewById<Button>(R.id.btnResetAll).setOnClickListener { resetAll() }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 120
        }

        windowManager.addView(overlayView, params)
        enableDrag(overlayView.findViewById(R.id.tvHandle))
        update()
    }

    private fun buildPad(pad: GridLayout, side: String) {
        for (r in ranks) {
            val b = Button(this)
            b.text = r
            b.textSize = 10f
            b.minWidth = 0
            b.minimumWidth = 0
            b.setPadding(4, 2, 4, 2)
            val bgRes = when {
                low.contains(r) -> R.drawable.bg_card_low
                high.contains(r) -> R.drawable.bg_card_high
                else -> R.drawable.bg_card_mid
            }
            b.setBackgroundResource(bgRes)
            b.setTextColor(Color.WHITE)
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            lp.setMargins(2, 2, 2, 2)
            b.layoutParams = lp
            b.setOnClickListener { addCard(side, r) }
            pad.addView(b)
        }
    }

    private fun enableDrag(handle: View) {
        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun hiLo(c: String): Int = when {
        low.contains(c) -> 1
        high.contains(c) -> -1
        else -> 0
    }

    private fun currentTc(): Double {
        val decksRemaining = (totalCards - history.size).coerceAtLeast(1) / 52.0
        return rc / decksRemaining
    }

    private fun betUnits(tc: Double): Int = when {
        tc < 1.0 -> 1
        tc < 2.0 -> 2
        tc < 3.0 -> 4
        tc < 4.0 -> 6
        tc < 5.0 -> 8
        else -> 10
    }

    private fun addCard(side: String, c: String) {
        if (history.isEmpty()) handStartTc = currentTc()
        history.add(side to c)
        if (side == "P") playerCards.add(c) else dealerCards.add(c)
        rc += hiLo(c)
        update()
    }

    private fun undo() {
        if (history.isEmpty()) { handStartTc = null; return }
        val (side, c) = history.removeAt(history.size - 1)
        if (side == "P") playerCards.removeAt(playerCards.size - 1)
        else dealerCards.removeAt(dealerCards.size - 1)
        rc -= hiLo(c)
        if (history.isEmpty()) handStartTc = null
        update()
    }

    private fun nextHand() {
        clearHand()
        update()
    }

    private fun clearHand() {
        playerCards.clear(); dealerCards.clear(); history.clear()
        handStartTc = null
    }

    private fun recordResult(result: String) {
        val tc = handStartTc ?: currentTc()
        val bet = betUnits(tc)
        val profit = when (result) {
            "W" -> bet.toDouble()
            "BJ" -> bet * 1.5
            "P" -> 0.0
            "L" -> -bet.toDouble()
            else -> 0.0
        }
        hands.add(HandRecord(tc, bet, result, profit))
        clearHand()
        update()
    }

    private fun resetAll() {
        clearHand(); rc = 0; hands.clear(); update()
        Toast.makeText(this, "Sessione azzerata", Toast.LENGTH_SHORT).show()
    }

    private fun update() {
        val tc = currentTc()
        tvRc.text = "RC:$rc"
        tvTc.text = "TC:%.1f".format(tc)

        val (advText, advColor, bet) = when {
            tc < 1.0 -> Triple("Banco +0,5% a +1,5%", Color.parseColor("#F87171"), 1)
            tc < 2.0 -> Triple("Neutro ~0%", Color.parseColor("#FBBF24"), 2)
            tc < 3.0 -> Triple("Tu +0,5% a +1%", Color.parseColor("#4ADE80"), 4)
            tc < 4.0 -> Triple("Tu +1% a +1,5%", Color.parseColor("#4ADE80"), 6)
            tc < 5.0 -> Triple("Tu +1,5% a +2%", Color.parseColor("#22C55E"), 8)
            else -> Triple("Tu +2%+ (massimo)", Color.parseColor("#16A34A"), 10)
        }
        tvAdvantage.text = advText
        tvAdvantage.setTextColor(advColor)
        tvTc.setTextColor(advColor)
        tvBet.text = "Puntata: ${bet}u"

        val dealerUp = dealerCards.firstOrNull()
        if (dealerUp != null && playerCards.size >= 2) {
            val rec = Strategy.recommend(playerCards, dealerUp, tc)
            if (rec != null) {
                tvAction.text = rec.action.label
                tvAction.setBackgroundColor(actionColor(rec.action))
                tvActionDetail.text = if (rec.isDeviation)
                    "★ ${rec.detail}" else rec.detail
                tvActionDetail.setTextColor(
                    if (rec.isDeviation) Color.parseColor("#FBBF24")
                    else Color.parseColor("#94A3B8")
                )
            } else {
                tvAction.text = "—"
                tvAction.setBackgroundColor(Color.parseColor("#334155"))
                tvActionDetail.text = ""
            }
        } else {
            tvAction.text = "—"
            tvAction.setBackgroundColor(Color.parseColor("#334155"))
            tvActionDetail.text = if (dealerUp == null)
                "Inserisci la carta scoperta del banco"
            else "Inserisci le tue due carte"
        }

        val total = hands.size
        val net = hands.sumOf { it.profit }
        val evPerHand = if (total > 0) net / total else 0.0
        val bands = listOf(
            "<1" to hands.filter { it.tc < 1.0 },
            "1-2" to hands.filter { it.tc >= 1.0 && it.tc < 2.0 },
            "2-3" to hands.filter { it.tc >= 2.0 && it.tc < 3.0 },
            "3-4" to hands.filter { it.tc >= 3.0 && it.tc < 4.0 },
            "4+" to hands.filter { it.tc >= 4.0 }
        )
        val bandStr = bands.joinToString("  ") { (name, list) ->
            val ev = if (list.isNotEmpty()) list.sumOf { it.profit } / list.size else 0.0
            "$name:${"%+.2f".format(ev)}(${list.size})"
        }
        tvStats.text = buildString {
            append("Mani: $total   Netto: ${"%+.1f".format(net)}u\n")
            append("EV/mano: ${"%+.3f".format(evPerHand)}u\n")
            append("EV per TC: $bandStr")
        }
    }

    private fun actionColor(a: Strategy.Action): Int = when (a) {
        Strategy.Action.HIT -> Color.parseColor("#166534")
        Strategy.Action.STAND -> Color.parseColor("#B91C1C")
        Strategy.Action.DOUBLE -> Color.parseColor("#1E40AF")
        Strategy.Action.SPLIT -> Color.parseColor("#6B21A8")
        Strategy.Action.SURRENDER -> Color.parseColor("#78350F")
    }

    private fun exportCsv() {
        if (hands.isEmpty()) {
            Toast.makeText(this, "Nessuna mano da esportare", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "hilo_session_$ts.csv"
            val sb = StringBuilder("hand,tc,bet,result,profit,band\n")
            hands.forEachIndexed { i, h ->
                val band = when {
                    h.tc < 1.0 -> "banco"
                    h.tc < 2.0 -> "neutro"
                    h.tc < 3.0 -> "tu_05_1"
                    h.tc < 4.0 -> "tu_1_15"
                    h.tc < 5.0 -> "tu_15_2"
                    else -> "tu_2plus"
                }
                sb.append("${i+1},${"%.3f".format(h.tc)},${h.bet},${h.result},${"%.2f".format(h.profit)},$band\n")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { os ->
                        os.write(sb.toString().toByteArray())
                    }
                    Toast.makeText(this, "Salvato in Download/$filename", Toast.LENGTH_LONG).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val f = java.io.File(dir, filename)
                f.writeText(sb.toString())
                Toast.makeText(this, "Salvato in ${f.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Errore export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "hilocounter"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Hi-Lo Counter", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Hi-Lo Counter attivo")
            .setContentText("Overlay di conteggio manuale + strategia")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            try { windowManager.removeView(overlayView) } catch (_: Exception) {}
        }
    }
}
