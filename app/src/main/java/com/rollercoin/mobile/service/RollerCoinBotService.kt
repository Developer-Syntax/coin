package com.rollercoin.mobile.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import com.rollercoin.mobile.Formatters
import com.rollercoin.mobile.MainActivity
import com.rollercoin.mobile.R
import com.rollercoin.mobile.engine.BotEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlin.math.abs

class RollerCoinBotService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var windowManager: WindowManager? = null

    private var floatingBallView: View? = null
    private var floatingInfoCardView: View? = null
    private var isInfoCardVisible = false

    private var ballLayoutParams: WindowManager.LayoutParams? = null
    private var cardLayoutParams: WindowManager.LayoutParams? = null

    // UI references in Floating Info Card
    private var tvGameTitle: TextView? = null
    private var tvGameStatus: TextView? = null
    private var tvTimeRemaining: TextView? = null
    private var pbGameProgress: ProgressBar? = null
    private var tvTotalPlayed: TextView? = null
    private var tvTotalPower: TextView? = null
    private var tvCycleInfo: TextView? = null
    private var btnStopBot: Button? = null
    private var btnOpenApp: Button? = null

    // UI references on Floating Ball
    private var ballIndicatorView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification("Bot RollerCoin Siap", "Menjalankan latar belakang..."))

        observeBotStates()
        setupFloatingOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                BotEngine.stopBot(this)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_BUBBLE -> {
                showFloatingBall()
            }
            ACTION_HIDE_BUBBLE -> {
                removeFloatingViews()
            }
            ACTION_UPDATE_STATUS -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Bot RollerCoin"
                val text = intent.getStringExtra(EXTRA_TEXT) ?: "Sedang berjalan..."
                updateNotificationContent(title, text)
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RollerCoin:BotWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RollerCoin Bot Runner",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status background bot dan game RollerCoin"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RollerCoinBotService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rollercoin_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_rollercoin_logo, "Hentikan Bot", stopPendingIntent)
            .build()
    }

    private fun updateNotificationContent(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun observeBotStates() {
        serviceScope.launch {
            combine(
                BotEngine.botUiState,
                BotEngine.activeGameUiState,
                BotEngine.isFloatingBubbleEnabled
            ) { bot, game, bubbleEnabled ->
                Triple(bot, game, bubbleEnabled)
            }.collect { (bot, game, bubbleEnabled) ->
                // Check if overlay permission is granted
                if (bubbleEnabled && Settings.canDrawOverlays(this@RollerCoinBotService)) {
                    if (floatingBallView == null) {
                        showFloatingBall()
                    }
                } else {
                    removeFloatingViews()
                }

                // Update Ball Indicator
                ballIndicatorView?.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (game.activeGame != null) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
                }

                // Update Info Card if visible
                updateCardUi(bot, game)
            }
        }
    }

    private fun updateCardUi(bot: com.rollercoin.mobile.BotUiState, game: com.rollercoin.mobile.ActiveGameUiState) {
        val active = game.activeGame
        if (active != null) {
            tvGameTitle?.text = active.game.definition.name
            tvGameStatus?.text = if (game.isSubmitting) "Mengirim hasil skor..." else "Sedang Dimainkan (Level ${active.game.level})"
            tvTimeRemaining?.text = "${game.remainingSeconds}s tersisa"
            pbGameProgress?.progress = (game.progress * 100).toInt()
        } else if (bot.cooldownWaitSeconds > 0) {
            tvGameTitle?.text = "Semua Game Cooldown"
            tvGameStatus?.text = bot.statusMessage
            tvTimeRemaining?.text = Formatters.timeRemaining(bot.cooldownWaitSeconds)
            pbGameProgress?.progress = 0
        } else {
            tvGameTitle?.text = if (bot.isAutoRunning) "Rotasi Game Siap" else "Bot Standby"
            tvGameStatus?.text = bot.statusMessage
            tvTimeRemaining?.text = "--"
            pbGameProgress?.progress = 0
        }

        tvTotalPlayed?.text = "${bot.totalGamesPlayed} game"
        tvTotalPower?.text = Formatters.hashPower(bot.totalSessionPower.toInt())
        tvCycleInfo?.text = "Putaran #${bot.currentCycle} (+${Formatters.hashPower(bot.cyclePower)})"
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        showFloatingBall()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBall() {
        if (!Settings.canDrawOverlays(this) || floatingBallView != null) return

        val wm = windowManager ?: return
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val ballSize = (56 * density).toInt()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            ballSize,
            ballSize,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = displayMetrics.widthPixels - ballSize - (12 * density).toInt()
            y = displayMetrics.heightPixels / 3
        }
        ballLayoutParams = params

        // Create Semi-Transparent Floating Ball View ("bola mengambang disamping berwarna transparan")
        val ballContainer = FrameLayout(this).apply {
            // Semi-transparent glowing circular container
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                // Translucent dark-navy color with 75% opacity (0xCC0F172A)
                setColor(Color.argb(200, 15, 23, 42))
                // Glowing border
                setStroke((2 * density).toInt(), Color.parseColor("#F59E0B"))
            }
            background = bg
            elevation = 16f
        }

        // Inner icon
        val iconView = ImageView(this).apply {
            setImageResource(R.drawable.ic_rollercoin_logo)
            val pad = (10 * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        ballContainer.addView(iconView)

        // Status indicator dot
        val indicatorSize = (14 * density).toInt()
        val indicator = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981"))
                setStroke((1.5f * density).toInt(), Color.BLACK)
            }
            layoutParams = FrameLayout.LayoutParams(indicatorSize, indicatorSize, Gravity.TOP or Gravity.END).apply {
                topMargin = (2 * density).toInt()
                rightMargin = (2 * density).toInt()
            }
        }
        ballIndicatorView = indicator
        ballContainer.addView(indicator)

        // Drag and Tap listener
        ballContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val currentParams = ballLayoutParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = currentParams.x
                        initialY = currentParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isClick = false
                        }
                        currentParams.x = initialX + dx.toInt()
                        currentParams.y = initialY + dy.toInt()
                        try {
                            wm.updateViewLayout(ballContainer, currentParams)
                        } catch (e: Exception) {
                            // View might have been detached
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            toggleFloatingInfoCard()
                        } else {
                            // Snap to closest edge
                            val screenWidth = displayMetrics.widthPixels
                            val targetX = if (currentParams.x + ballSize / 2 < screenWidth / 2) {
                                (10 * density).toInt()
                            } else {
                                screenWidth - ballSize - (10 * density).toInt()
                            }
                            currentParams.x = targetX
                            try {
                                wm.updateViewLayout(ballContainer, currentParams)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            wm.addView(ballContainer, params)
            floatingBallView = ballContainer
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleFloatingInfoCard() {
        if (isInfoCardVisible) {
            hideFloatingInfoCard()
        } else {
            showFloatingInfoCard()
        }
    }

    private fun showFloatingInfoCard() {
        if (floatingInfoCardView != null || !Settings.canDrawOverlays(this)) return
        val wm = windowManager ?: return
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val cardWidth = (displayMetrics.widthPixels * 0.88f).toInt().coerceAtMost((360 * density).toInt())
        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        cardLayoutParams = params

        // Inflate / Construct programmatic high-tech floating info card
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.argb(240, 15, 23, 42)) // Deep dark semi-transparent
                cornerRadius = 20 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#F59E0B"))
            }
            elevation = 24f
        }

        // 1. Header Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val headerTitle = TextView(this).apply {
            text = "ROLLERCOIN PLAY GAME"
            setTextColor(Color.parseColor("#F59E0B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        titleCol.addView(headerTitle)

        val headerSubtitle = TextView(this).apply {
            text = "Informasi Auto-Bot Real-time"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        titleCol.addView(headerSubtitle)
        headerRow.addView(titleCol)

        // Close Button
        val btnClose = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#334155"))
            }
            val btnSize = (32 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            setOnClickListener { hideFloatingInfoCard() }
        }
        headerRow.addView(btnClose)
        cardLayout.addView(headerRow)

        // Divider
        val divider1 = View(this).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#1E293B")) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * density).toInt()
            ).apply {
                topMargin = (10 * density).toInt()
                bottomMargin = (12 * density).toInt()
            }
        }
        cardLayout.addView(divider1)

        // 2. Active Game Banner Box
        val gameBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val gpad = (12 * density).toInt()
            setPadding(gpad, gpad, gpad, gpad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 12 * density
                setStroke((1 * density).toInt(), Color.parseColor("#334155"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tvGameTitle = TextView(this).apply {
            text = "Memuat game..."
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        gameBox.addView(tvGameTitle)

        tvGameStatus = TextView(this).apply {
            text = "Status: Menyiapkan putaran..."
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val tpad = (3 * density).toInt()
            setPadding(0, tpad, 0, tpad)
        }
        gameBox.addView(tvGameStatus)

        // Progress bar + Time Remaining Row
        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * density).toInt() }
        }

        pbGameProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(0, (8 * density).toInt(), 1f)
        }
        progressRow.addView(pbGameProgress)

        tvTimeRemaining = TextView(this).apply {
            text = "--s"
            setTextColor(Color.parseColor("#10B981"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((10 * density).toInt(), 0, 0, 0)
        }
        progressRow.addView(tvTimeRemaining)
        gameBox.addView(progressRow)

        cardLayout.addView(gameBox)

        // 3. Stats Row (Total Played & Total Mined Power)
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * density).toInt() }
        }

        // Left Stat (Total Played)
        val statLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val spad = (8 * density).toInt()
            setPadding(spad, spad, spad, spad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 8 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (6 * density).toInt()
            }
        }
        statLeft.addView(TextView(this).apply {
            text = "Game Dimainkan"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        })
        tvTotalPlayed = TextView(this).apply {
            text = "0 game"
            setTextColor(Color.parseColor("#F59E0B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        statLeft.addView(tvTotalPlayed)
        statsRow.addView(statLeft)

        // Right Stat (Total Power)
        val statRight = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val spad = (8 * density).toInt()
            setPadding(spad, spad, spad, spad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 8 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = (6 * density).toInt()
            }
        }
        statRight.addView(TextView(this).apply {
            text = "Mined Power"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        })
        tvTotalPower = TextView(this).apply {
            text = "0 Gh/s"
            setTextColor(Color.parseColor("#10B981"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        statRight.addView(tvTotalPower)
        statsRow.addView(statRight)

        cardLayout.addView(statsRow)

        // Cycle Info Text
        tvCycleInfo = TextView(this).apply {
            text = "Putaran #1"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            val cpad = (6 * density).toInt()
            setPadding(0, cpad, 0, 0)
        }
        cardLayout.addView(tvCycleInfo)

        // 4. Action Buttons (Buka Aplikasi & Hentikan Bot)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (14 * density).toInt() }
        }

        btnOpenApp = Button(this).apply {
            text = "Buka App"
            setTextColor(Color.parseColor("#0F172A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F59E0B"))
                cornerRadius = 10 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, (40 * density).toInt(), 1f).apply {
                rightMargin = (6 * density).toInt()
            }
            setOnClickListener {
                hideFloatingInfoCard()
                val intent = Intent(this@RollerCoinBotService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
        }
        btnRow.addView(btnOpenApp)

        btnStopBot = Button(this).apply {
            text = "Hentikan Bot"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444"))
                cornerRadius = 10 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, (40 * density).toInt(), 1f).apply {
                leftMargin = (6 * density).toInt()
            }
            setOnClickListener {
                BotEngine.stopBot(this@RollerCoinBotService)
                hideFloatingInfoCard()
            }
        }
        btnRow.addView(btnStopBot)
        cardLayout.addView(btnRow)

        // Initial populate
        updateCardUi(BotEngine.botUiState.value, BotEngine.activeGameUiState.value)

        try {
            wm.addView(cardLayout, params)
            floatingInfoCardView = cardLayout
            isInfoCardVisible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideFloatingInfoCard() {
        if (floatingInfoCardView != null) {
            try {
                windowManager?.removeView(floatingInfoCardView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingInfoCardView = null
            isInfoCardVisible = false
        }
    }

    private fun removeFloatingViews() {
        hideFloatingInfoCard()
        if (floatingBallView != null) {
            try {
                windowManager?.removeView(floatingBallView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingBallView = null
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        removeFloatingViews()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "rc_bot_channel_id"
        const val NOTIFICATION_ID = 101

        const val ACTION_START = "com.rollercoin.mobile.action.START"
        const val ACTION_STOP = "com.rollercoin.mobile.action.STOP"
        const val ACTION_SHOW_BUBBLE = "com.rollercoin.mobile.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "com.rollercoin.mobile.action.HIDE_BUBBLE"
        const val ACTION_UPDATE_STATUS = "com.rollercoin.mobile.action.UPDATE_STATUS"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"

        fun updateNotification(context: Context, title: String, text: String) {
            try {
                val intent = Intent(context, RollerCoinBotService::class.java).apply {
                    action = ACTION_UPDATE_STATUS
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_TEXT, text)
                }
                context.startService(intent)
            } catch (e: Exception) {
                // Service may not be running yet
            }
        }
    }
}
