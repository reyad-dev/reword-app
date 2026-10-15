package com.reword.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.reword.app.R
import kotlin.math.abs

class FloatingShieldService : Service() {

    companion object {
        const val CHANNEL_ID = "reword_floating_shield_channel"
        const val NOTIFICATION_ID = 2001

        var isRunning: Boolean = false
            private set

        private var instance: FloatingShieldService? = null

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return
            }
            val intent = Intent(context, FloatingShieldService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingShieldService::class.java)
            context.stopService(intent)
        }

        fun togglePicker() {
            instance?.launchPicker()
        }

        fun onInputFieldFocused(pkg: String?) {
            // Could animate or wake up shield if needed
        }

        fun onPackageChanged(pkg: String?) {
            // Updated package
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true

        startForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            setupFloatingView()
        }
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "درع ReWord العائم",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار تشغيل زر درع ReWord للملء السريع"
            }
            manager.createNotificationChannel(channel)
        }

        val pickerIntent = Intent(this, FloatingPickerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 101, pickerIntent, flags)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("درع ReWord للملء السريع")
            .setContentText("انقر للوصول الفوري لكلمات المرور وتعبئتها")
            .setSmallIcon(R.drawable.ic_reword_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val sizePx = (52 * resources.displayMetrics.density).toInt()
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = (resources.displayMetrics.heightPixels * 0.4).toInt()
        }

        // Circular Floating Button with shield icon and glowing border
        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.ic_reword_shield)
            setBackgroundResource(R.drawable.ic_reword_logo)
            val pad = (10 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            elevation = 12 * resources.displayMetrics.density
        }

        floatingView = imageView

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        imageView.setOnTouchListener { _, event ->
            val curParams = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = curParams.x
                    initialY = curParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    curParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    curParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, curParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    // If tap instead of drag
                    if (diffX < 15 && diffY < 15) {
                        launchPicker()
                    } else {
                        // Snap to nearest screen edge (left or right)
                        val screenWidth = resources.displayMetrics.widthPixels
                        curParams.x = if (event.rawX > screenWidth / 2f) screenWidth - curParams.width - 20 else 20
                        windowManager?.updateViewLayout(floatingView, curParams)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (_: Exception) {
            // WindowManager permission or add view failed
        }
    }

    fun launchPicker() {
        val intent = Intent(this, FloatingPickerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (_: Exception) {}
            floatingView = null
        }
    }
}
