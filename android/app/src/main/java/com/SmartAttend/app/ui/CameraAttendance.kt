package com.SmartAttend.app.ui

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Base64
import androidx.camera.core.ExperimentalGetImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    CameraDialogShell(
        title = "Scan Session QR",
        body = "Point the back camera at the faculty QR code. SmartAttend will extract the token and record attendance if the session is active.",
        onDismiss = onDismiss
    ) {
        CameraPermissionGate {
            LiveQrScanner(onQrScanned = onQrScanned)
        }
    }
}

@Composable
fun FaceCaptureDialog(
    title: String,
    body: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    onCapture: (snapshot: FaceSnapshot) -> Unit
) {
    var faceDetected by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<FaceSnapshot?>(null) }

    CameraDialogShell(
        title = title,
        body = body,
        onDismiss = onDismiss
    ) {
        CameraPermissionGate {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveFaceScanner(
                    onFaceStateChanged = { detected, currentSnapshot ->
                        faceDetected = detected
                        snapshot = currentSnapshot
                    }
                )
                Text(
                    if (faceDetected) {
                        "Face detected. Capture when the preview is steady."
                    } else {
                        "Center your face in the preview and keep the camera steady."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { snapshot?.let(onCapture) },
                        modifier = Modifier.weight(1f),
                        enabled = faceDetected && snapshot != null
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraDialogShell(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium)
                content()
            }
        }
    }
}

@Composable
private fun CameraPermissionGate(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        content()
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Camera access is required for QR and face scan attendance.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Camera Access")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalGetImage::class)
private fun LiveQrScanner(
    onQrScanned: (String) -> Unit
) {
    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }

    DisposableEffect(Unit) {
        onDispose { scanner.close() }
    }

    AnalyzerCameraPreview(
        lensFacing = CameraSelector.LENS_FACING_BACK,
        stopAfterFirstResult = true,
        onAnalyze = { imageProxy, _, report ->
            val mediaImage = imageProxy.image ?: run {
                imageProxy.close()
                return@AnalyzerCameraPreview
            }
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val rawValue = barcodes.firstNotNullOfOrNull { it.rawValue }
                    if (!rawValue.isNullOrBlank()) {
                        report(rawValue)
                        onQrScanned(rawValue)
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    )
}

@Composable
@OptIn(ExperimentalGetImage::class)
private fun LiveFaceScanner(
    onFaceStateChanged: (detected: Boolean, snapshot: FaceSnapshot?) -> Unit
) {
    val options = remember {
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .build()
    }
    val detector = remember { FaceDetection.getClient(options) }

    DisposableEffect(Unit) {
        onDispose { detector.close() }
    }

    AnalyzerCameraPreview(
        lensFacing = CameraSelector.LENS_FACING_FRONT,
        stopAfterFirstResult = false,
        onAnalyze = { imageProxy, previewBitmap, report ->
            val mediaImage = imageProxy.image ?: run {
                imageProxy.close()
                return@AnalyzerCameraPreview
            }
            val width = imageProxy.width
            val height = imageProxy.height
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val snapshot = if (faces.isNotEmpty() && previewBitmap != null) {
                        FaceSnapshot(
                            width = width,
                            height = height,
                            photoBase64 = previewBitmap.toBase64(),
                            signature = previewBitmap.toFaceSignature()
                        )
                    } else {
                        null
                    }
                    onFaceStateChanged(faces.isNotEmpty(), snapshot)
                    if (faces.isNotEmpty()) {
                        report("face")
                    }
                }
                .addOnFailureListener {
                    onFaceStateChanged(false, null)
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    )
}

@Composable
@OptIn(ExperimentalGetImage::class)
private fun AnalyzerCameraPreview(
    lensFacing: Int,
    stopAfterFirstResult: Boolean,
    onAnalyze: (imageProxy: androidx.camera.core.ImageProxy, previewBitmap: Bitmap?, report: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProvider by produceState<ProcessCameraProvider?>(initialValue = null, context) {
        value = context.awaitCameraProvider()
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val handled = remember { AtomicBoolean(false) }
    val latestPreviewBitmap = remember { AtomicReference<Bitmap?>(null) }

    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    LaunchedEffect(previewView) {
        val view = previewView ?: return@LaunchedEffect
        while (true) {
            latestPreviewBitmap.set(view.bitmap)
            delay(250)
        }
    }

    DisposableEffect(cameraProvider, previewView, lensFacing) {
        val provider = cameraProvider
        val view = previewView
        if (provider == null || view == null) {
            onDispose { }
        } else {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { useCase ->
                    useCase.setAnalyzer(analyzerExecutor) { imageProxy ->
                        if (handled.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        onAnalyze(imageProxy, latestPreviewBitmap.get()) {
                            if (stopAfterFirstResult) {
                                handled.set(true)
                            }
                        }
                    }
                }
            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)

            onDispose {
                provider.unbindAll()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (cameraProvider == null) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { previewView = it }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView = it }
        )
        Text(
            text = "Scanning live camera feed...",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

data class FaceSnapshot(
    val width: Int,
    val height: Int,
    val photoBase64: String,
    val signature: String
)

private fun Bitmap.toBase64(): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 70, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

private fun Bitmap.toFaceSignature(): String {
    val scaled = Bitmap.createScaledBitmap(this, 8, 8, true)
    val values = buildList {
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val pixel = scaled.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                add((red + green + blue) / 3)
            }
        }
    }
    val average = values.average()
    return values.joinToString(separator = "") { if (it >= average) "1" else "0" }
}

@SuppressLint("BlockingMethodInNonBlockingContext")
private suspend fun android.content.Context.awaitCameraProvider(): ProcessCameraProvider {
    val future = ProcessCameraProvider.getInstance(this)
    return suspendCancellableCoroutine { continuation ->
        future.addListener(
            {
                continuation.resume(future.get())
            },
            ContextCompat.getMainExecutor(this)
        )
    }
}
