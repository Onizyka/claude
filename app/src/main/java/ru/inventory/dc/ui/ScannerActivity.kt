package ru.inventory.dc.ui

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Сканер QR-кодов и штрихкодов с зумом (ползунок, кнопки 1×/2×/4×, жест «щипок») и фонариком.
 * Мелкие штрихкоды на шильдиках серверов читаются заметно лучше с увеличением.
 */
class ScannerActivity : CaptureActivity() {

    private lateinit var scannerView: DecoratedBarcodeView
    private lateinit var zoomBar: SeekBar
    private lateinit var zoomLabel: TextView
    private lateinit var torchButton: TextView
    private lateinit var zoomPanel: View
    private val presetButtons = mutableMapOf<Int, TextView>()

    private var maxZoom = 0
    private var zoomRatios: List<Int> = emptyList()
    private var zoomLevel = 0
    private var pinchAccumulator = 0f
    private var torchOn = false

    private val scaleDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (maxZoom <= 0) return false
                pinchAccumulator += (detector.scaleFactor - 1f) * maxZoom * PINCH_SENSITIVITY
                val step = pinchAccumulator.toInt()
                if (step != 0) {
                    pinchAccumulator -= step
                    setZoom(zoomLevel + step)
                }
                return true
            }
        })
    }

    override fun initializeContent(): DecoratedBarcodeView {
        val root = FrameLayout(this)
        scannerView = DecoratedBarcodeView(this)
        root.addView(scannerView, FrameLayout.LayoutParams(MATCH, MATCH))
        // Подсказку показываем сверху сами, нижняя строка библиотеки перекрывалась бы панелью.
        scannerView.statusView?.visibility = View.GONE

        val prompt = TextView(this).apply {
            text = intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE) ?: "Наведите камеру на код"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(PANEL_COLOR)
        }
        root.addView(prompt, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        val panel = buildPanel()
        root.addView(panel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        // Отступы от системных панелей (Android 15 рисует приложение под ними).
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            prompt.setPadding(dp(16), dp(12) + bars.top, dp(16), dp(12))
            panel.setPadding(dp(16) + bars.left, dp(12), dp(16) + bars.right, dp(16) + bars.bottom)
            insets
        }

        setContentView(root)

        scannerView.barcodeView.addStateListener(object : CameraPreview.StateListener {
            override fun previewSized() = Unit
            override fun previewStarted() = readZoomCapabilities()
            override fun previewStopped() = Unit
            override fun cameraError(error: Exception?) = Unit
            override fun cameraClosed() = Unit
        })
        scannerView.setTorchListener(object : DecoratedBarcodeView.TorchListener {
            override fun onTorchOn() = updateTorch(true)
            override fun onTorchOff() = updateTorch(false)
        })
        return scannerView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        zoomLevel = savedInstanceState?.getInt(STATE_ZOOM) ?: 0
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_ZOOM, zoomLevel)
    }

    override fun onPause() {
        super.onPause()
        // Камера закрывается — фонарик гаснет вместе с ней.
        updateTorch(false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1 || scaleDetector.isInProgress) {
            scaleDetector.onTouchEvent(ev)
            return true
        }
        scaleDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---------- Панель управления ----------

    private fun buildPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PANEL_COLOR)
        }

        val zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        zoomLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            minWidth = dp(56)
            text = "×1.0"
        }
        zoomBar = SeekBar(this).apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser && maxZoom > 0) setZoom((progress * maxZoom / 100f).roundToInt(), fromSlider = true)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        zoomRow.addView(zoomLabel, LinearLayout.LayoutParams(WRAP, WRAP))
        zoomRow.addView(zoomBar, LinearLayout.LayoutParams(0, WRAP, 1f))
        zoomPanel = zoomRow
        panel.addView(zoomRow, LinearLayout.LayoutParams(MATCH, WRAP))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        listOf(100, 200, 400).forEach { ratio ->
            val b = chip("×${ratio / 100}") { setZoomRatio(ratio) }
            presetButtons[ratio] = b
            buttons.addView(b, LinearLayout.LayoutParams(WRAP, dp(44)).apply { marginEnd = dp(8) })
        }
        buttons.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        torchButton = chip("Фонарик") { toggleTorch() }
        val hasFlash = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        torchButton.visibility = if (hasFlash) View.VISIBLE else View.GONE
        buttons.addView(torchButton, LinearLayout.LayoutParams(WRAP, dp(44)))

        panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
        zoomPanel.visibility = View.INVISIBLE
        return panel
    }

    private fun chip(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(16), 0, dp(16), 0)
        background = chipBackground(selected = false)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun chipBackground(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(22).toFloat()
        if (selected) {
            setColor(BRAND_BLUE)
        } else {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), Color.WHITE)
        }
    }

    // ---------- Зум ----------

    /** Узнаём возможности камеры, когда превью запущено; заодно возвращаем выбранный зум. */
    private fun readZoomCapabilities() {
        scannerView.changeCameraParameters { params ->
            val supported = params.isZoomSupported
            val max = if (supported) params.maxZoom else 0
            val ratios = if (supported) params.zoomRatios?.toList().orEmpty() else emptyList()
            if (supported && zoomLevel > 0) params.zoom = zoomLevel.coerceIn(0, max)
            runOnUiThread {
                maxZoom = max
                zoomRatios = ratios
                zoomPanel.visibility = if (max > 0) View.VISIBLE else View.INVISIBLE
                presetButtons.forEach { (ratio, button) ->
                    // Кнопку 4× прячем, если камера столько не умеет.
                    val reachable = ratios.isEmpty() || ratio <= (ratios.lastOrNull() ?: 100)
                    button.visibility = if (max > 0 && reachable) View.VISIBLE else View.GONE
                }
                updateZoomUi()
            }
            params
        }
    }

    private fun setZoomRatio(ratio: Int) {
        if (maxZoom <= 0) return
        val index = if (zoomRatios.isNotEmpty()) {
            zoomRatios.indices.minByOrNull { abs(zoomRatios[it] - ratio) } ?: 0
        } else {
            ((ratio - 100) / 300f * maxZoom).roundToInt()
        }
        setZoom(index)
    }

    private fun setZoom(level: Int, fromSlider: Boolean = false) {
        val target = level.coerceIn(0, maxZoom)
        if (target == zoomLevel && !fromSlider) return
        zoomLevel = target
        scannerView.changeCameraParameters { params ->
            if (params.isZoomSupported) params.zoom = target.coerceIn(0, params.maxZoom)
            params
        }
        updateZoomUi(updateSlider = !fromSlider)
    }

    private fun updateZoomUi(updateSlider: Boolean = true) {
        val ratio = zoomRatios.getOrNull(zoomLevel) ?: (100 + zoomLevel * 300 / maxOf(1, maxZoom))
        zoomLabel.text = String.format(Locale.US, "×%.1f", ratio / 100f)
        if (updateSlider && maxZoom > 0) zoomBar.progress = zoomLevel * 100 / maxZoom
        val nearest = presetButtons.keys.minByOrNull { abs(it - ratio) }
        presetButtons.forEach { (r, b) -> b.background = chipBackground(selected = r == nearest && abs(r - ratio) < 15) }
    }

    // ---------- Фонарик ----------

    private fun toggleTorch() {
        if (torchOn) scannerView.setTorchOff() else scannerView.setTorchOn()
    }

    private fun updateTorch(on: Boolean) {
        torchOn = on
        if (!::torchButton.isInitialized) return
        torchButton.text = if (on) "Фонарик: вкл" else "Фонарик"
        torchButton.background = chipBackground(selected = on)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val PANEL_COLOR = 0xB3001A3D.toInt()
        const val BRAND_BLUE = 0xFF025EA1.toInt()
        const val PINCH_SENSITIVITY = 1.5f
        const val STATE_ZOOM = "zoom_level"
    }
}
