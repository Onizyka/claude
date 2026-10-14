package ru.inventory.dc.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ru.inventory.dc.ui.theme.BrandColors
import ru.inventory.dc.ui.theme.DcInventoryTheme

/** Запуск сканера: на входе подсказка, на выходе прочитанное значение (или null). */
class ScanCodeContract : ActivityResultContract<String, String?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(context, ScannerActivity::class.java).putExtra(EXTRA_PROMPT, input)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(EXTRA_RESULT) else null

    companion object {
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_RESULT = "result"
    }
}

/**
 * Сканер QR-кодов и штрихкодов на CameraX + ML Kit (модель встроена в приложение,
 * интернет и сервисы Google не нужны).
 *
 * - непрерывный автофокус, нажатие на экран — фокус в точке;
 * - анализ кадра в Full HD по всей площади, а не только в центральной рамке;
 * - зум (кнопки, ползунок, жест двумя пальцами) и фонарик;
 * - значение принимается, только если прочитано в двух кадрах подряд;
 * - несколько кодов в кадре — пользователь выбирает нужный из списка;
 * - запасной вариант: распознать код со снимка из галереи.
 */
class ScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        val prompt = intent.getStringExtra(ScanCodeContract.EXTRA_PROMPT) ?: "Наведите камеру на код"
        setContent {
            DcInventoryTheme {
                ScannerScreen(prompt = prompt, onResult = ::finishWith, onClose = ::finish)
            }
        }
    }

    private fun finishWith(value: String) {
        setResult(RESULT_OK, Intent().putExtra(ScanCodeContract.EXTRA_RESULT, value))
        finish()
    }
}

/** Код, найденный в кадре; [box] — в координатах экрана предпросмотра. */
private data class Detected(val value: String, val box: Rect?)

@Composable
private fun ScannerScreen(prompt: String, onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        denied = !granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            CameraScanner(prompt, onResult, onClose)
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (denied) "Нет доступа к камере. Разрешите его в настройках телефона." else "Запрос доступа к камере…",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(16.dp))
                Button(onClick = onClose) { Text("Закрыть") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameraScanner(prompt: String, onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // Высокое разрешение кадра — мелкие коды на шильдиках читаются заметно лучше.
            setImageAnalysisResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
        }
    }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
        )
    }

    var boxes by remember { mutableStateOf<List<Detected>>(emptyList()) }
    // Значения, замеченные в последние секунды, — для выбора, если кодов несколько.
    var recent by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var lastSingle by remember { mutableStateOf<String?>(null) }
    var stableCount by remember { mutableIntStateOf(0) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() }
    var accepted by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var torchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<String?>(null) }

    val accept: (String) -> Unit = { value ->
        if (!accepted) {
            accepted = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
            onResult(value)
        }
    }

    val onFrame: (List<Detected>) -> Unit = { codes ->
        val now = System.currentTimeMillis()
        boxes = codes
        recent = (recent + codes.associate { it.value to now }).filterValues { now - it < RECENT_MS }
        if (codes.size == 1) {
            val v = codes[0].value
            if (v == lastSingle) stableCount++ else {
                lastSingle = v
                stableCount = 1
            }
            // Один код, прочитан дважды подряд и рядом не мелькали другие — берём.
            if (stableCount >= STABLE_FRAMES && recent.size == 1) accept(v)
        } else {
            lastSingle = null
            stableCount = 0
        }
    }

    // Распознавание кода со снимка — когда вживую не читается.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            photoMessage = "Распознавание…"
            runCatching { InputImage.fromFilePath(context, uri) }
                .onSuccess { image ->
                    scanner.process(image)
                        .addOnSuccessListener { list ->
                            val values = list.mapNotNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }.distinct()
                            when {
                                values.isEmpty() -> photoMessage = "На снимке код не найден"
                                values.size == 1 -> accept(values[0])
                                else -> {
                                    photoMessage = "На снимке несколько кодов — выберите нужный"
                                    val now = System.currentTimeMillis() + PHOTO_HOLD_MS
                                    recent = values.associateWith { now }
                                }
                            }
                        }
                        .addOnFailureListener { photoMessage = "Не удалось распознать снимок" }
                }
                .onFailure { photoMessage = "Не удалось открыть снимок" }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                val codes = result?.getValue(scanner).orEmpty().mapNotNull { b ->
                    b.rawValue?.trim()?.takeIf { it.isNotEmpty() }?.let { Detected(it, b.boundingBox) }
                }
                if (!accepted) onFrame(codes)
            }
        )
        controller.bindToLifecycle(lifecycleOwner)
        controller.zoomState.observe(lifecycleOwner) { state ->
            zoomRatio = state.zoomRatio
            maxZoom = state.maxZoomRatio
            linearZoom = state.linearZoom
        }
        controller.torchState.observe(lifecycleOwner) { state -> torchOn = state == TorchState.ON }
        controller.initializationFuture.addListener({
            hasFlash = controller.cameraInfo?.hasFlashUnit() == true
        }, executor)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
            tone?.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Предпросмотр: нажатие — фокус в точке, два пальца — зум (встроено в PreviewView).
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Рамки вокруг найденных кодов (только рисование — нажатия проходят к камере для фокуса).
        Canvas(Modifier.fillMaxSize()) {
            boxes.forEach { d ->
                val r = d.box ?: return@forEach
                drawRoundRect(
                    color = BrandColors.Cyan,
                    topLeft = Offset(r.left.toFloat(), r.top.toFloat()),
                    size = ComposeSize(r.width().toFloat(), r.height().toFloat()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                    style = Stroke(width = 6f),
                )
            }
        }

        // Верх: подсказка и закрыть.
        Column(
            Modifier
                .fillMaxWidth()
                .background(PANEL)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(prompt, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Держите телефон в 15–25 см и увеличьте зум. Нажмите на код, чтобы навести фокус.",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // Низ: найденные коды, зум, фонарик, «из фото».
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(PANEL)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val candidates = recent.keys.toList()
            if (candidates.size > 1) {
                Text("Найдено несколько кодов — нажмите нужный:", color = Color.White, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.forEach { value ->
                        Surface(
                            onClick = { accept(value) },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            contentColor = BrandColors.DarkBlue,
                        ) {
                            Text(
                                value,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            photoMessage?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall) }

            if (maxZoom > 1.01f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format(java.util.Locale.US, "×%.1f", zoomRatio),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(52.dp),
                    )
                    Slider(
                        value = linearZoom,
                        onValueChange = { controller.setLinearZoom(it) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = BrandColors.LightBlue),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f, 2f, 4f).filter { it <= maxZoom + 0.01f }.forEach { ratio ->
                    ScannerChip(
                        text = "×${ratio.toInt()}",
                        selected = kotlin.math.abs(zoomRatio - ratio) < 0.15f,
                        onClick = { controller.setZoomRatio(ratio) },
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = "Распознать со снимка", tint = Color.White)
                }
                if (hasFlash) {
                    IconButton(onClick = { controller.enableTorch(!torchOn) }) {
                        Icon(
                            if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                            contentDescription = if (torchOn) "Выключить фонарик" else "Включить фонарик",
                            tint = if (torchOn) BrandColors.Cyan else Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) BrandColors.Blue else Color.Transparent,
        contentColor = Color.White,
        border = BorderStroke(1.dp, if (selected) BrandColors.Blue else Color.White),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontWeight = FontWeight.Bold,
        )
    }
}

private val PANEL = Color(0xB3001A3D)
private const val STABLE_FRAMES = 2
private const val RECENT_MS = 1500L
private const val PHOTO_HOLD_MS = 60_000L
