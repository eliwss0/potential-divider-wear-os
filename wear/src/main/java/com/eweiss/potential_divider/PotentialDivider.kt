package com.eweiss.potential_divider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.*
import android.text.format.DateFormat
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.*


/**
 * Updates rate in milliseconds for interactive mode.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

class PotentialDivider : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: PotentialDivider.Engine) : Handler() {
        private val mWeakReference: WeakReference<PotentialDivider.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false

        private lateinit var mTextPaint: Paint

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mBackgroundBitmap: Bitmap
        private lateinit var mGrayBackgroundBitmap: Bitmap

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@PotentialDivider)
                    .setAcceptsTapEvents(true)
                    .build())

            mCalendar = Calendar.getInstance()

            initializeBackground()
            initializeWatchFace()
        }

        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.potential_divider_bg)
        }

        private fun initializeWatchFace() {
            val scale = Resources.getSystem().displayMetrics.scaledDensity

            mTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = scale*14F
                textAlign = Paint.Align.CENTER
                typeface = Typeface.SANS_SERIF
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()

            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchStyle()

            // Check and trigger whether or not timer should be running (only in active mode).
            updateTimer()
        }

//        fun checkCharging() {
//            //TODO charging animation?
//            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
//                registerReceiver(null, ifilter)
//            }
//            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
//            val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
//        }

        private fun updateWatchStyle() {
            if (mAmbient) {
                mTextPaint.color = Color.WHITE
                mTextPaint.isAntiAlias = false
                mBackgroundPaint.alpha = 100
                mTextPaint.alpha = 100
            } else {
                mTextPaint.color = Color.WHITE
                mTextPaint.isAntiAlias = true
                mBackgroundPaint.alpha = 255
                mTextPaint.alpha = 255
            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mTextPaint.alpha = if (inMuteMode) 100 else 255
                mBackgroundPaint.alpha = if (inMuteMode) 100 else 255
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            val scale = width.toFloat() / mBackgroundBitmap.width.toFloat()

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (mBackgroundBitmap.width * scale).toInt(),
                    (mBackgroundBitmap.height * scale).toInt(), true)
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
//                    Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT).show()
                }
            }
            invalidate()
        }

        private fun initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.width,
                    mBackgroundBitmap.height,
                    Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(mGrayBackgroundBitmap)
            val grayPaint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            grayPaint.colorFilter = filter
            canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, grayPaint)
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            canvas.drawColor(Color.BLACK)
            drawBackground(canvas)
            drawDigital(canvas)
        }

        private fun drawBackground(canvas: Canvas) {

            if (mAmbient && mBurnInProtection) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 25f, 0f, mBackgroundPaint)
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 25f, 0f, mBackgroundPaint)
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 25f, 0f, mBackgroundPaint)
            }
        }

        private fun drawDigital(canvas: Canvas) {
            val screenWidth = Resources.getSystem().displayMetrics.widthPixels
            val screenHeight = Resources.getSystem().displayMetrics.heightPixels

//            val seconds = mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f    2560 pixel perimeter
            val minutes = mCalendar.get(Calendar.MINUTE).toString().padStart(2, '0')
            val hours = if(!DateFormat.is24HourFormat(applicationContext) && mCalendar.get(Calendar.HOUR)==0)
                            "12"
                        else
                            mCalendar.get(Calendar.HOUR).toString().padStart(2, '0')
            val day = mCalendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
            val vOut = minutes.toDouble()/(hours+minutes).toDouble()*day.toDouble()

            //Designed with absolute positioning on 320x320 screen, scaled based on device
            canvas.drawText("$hours Ω", (screenWidth * 175 / 320).toFloat(), (screenHeight * 120 / 320).toFloat(), mTextPaint)
            canvas.drawText("$minutes Ω", (screenWidth * 175 / 320).toFloat(), (screenHeight * 215 / 320).toFloat(), mTextPaint)
            canvas.drawText("$day V", (screenWidth * 120 / 320).toFloat(), (screenHeight * 162.5 / 320).toFloat(), mTextPaint)
            canvas.drawText(String.format("%.2f", vOut) + " V", (screenWidth * 260 / 320).toFloat(), (screenHeight * 150 / 320).toFloat(), mTextPaint)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@PotentialDivider.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@PotentialDivider.unregisterReceiver(mTimeZoneReceiver)
        }

//        Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }


//        Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
//        should only run in active mode.
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

//        Handle updating the time periodically in interactive mode.
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}
