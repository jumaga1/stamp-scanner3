package com.filatelia.scanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.filatelia.scanner.data.StampEntity
import com.filatelia.scanner.duplicate.DuplicateConfidence
import com.filatelia.scanner.ui.viewmodel.ScanStep
import com.filatelia.scanner.ui.viewmodel.ScanViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onStampSaved: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val file = copyUriToCacheFile(context, uri)
            viewModel.onImageCaptured(file)
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        when (val step = uiState.step) {
            is ScanStep.Idle -> {
                if (hasCameraPermission) {
                    CameraCaptureArea(
                        onCaptured = { file -> viewModel.onImageCaptured(file) },
                        onPickFromGallery = { galleryLauncher.launch("image/*") }
                    )
                } else {
                    PermissionMissingView { permissionLauncher.launch(Manifest.permission.CAMERA) }
                }
            }
            is ScanStep.Preprocessing -> StatusView("Procesando imagen (recorte, limpieza, normalización)...")
            is ScanStep.CheckingDuplicates -> StatusView("Comparando contra tu colección...")
            is ScanStep.RunningAi -> StatusView("Consultando IA para identificar el sello...")
            is ScanStep.DuplicateFound -> DuplicateWarningView(
                confidence = step.result.confidence,
                onContinueAnyway = { viewModel.continueDespiteDuplicate() },
                onCancel = { viewModel.reset() }
            )
            is ScanStep.ReadyToSave -> StampReviewForm(
                uiState = uiState,
                onSave = { entity -> viewModel.saveStamp(entity, onSaved = { onStampSaved() }) },
                onDiscard = { viewModel.reset() }
            )
            is ScanStep.Error -> ErrorView(step.message) { viewModel.reset() }
        }
    }
}

@Composable
private fun CameraCaptureArea(onCaptured: (File) -> Unit, onPickFromGallery: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Escanea un sello", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Coloca el sello sobre un fondo plano y con buena luz. Encuadra dejando un pequeño margen alrededor.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))

        AndroidView(
            modifier = Modifier.fillMaxWidth().height(360.dp),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val photoFile = createTempImageFile(context)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            onCaptured(photoFile)
                        }
                        override fun onError(exception: ImageCaptureException) {
                        }
                    }
                )
            }) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Capturar")
            }
            OutlinedButton(onClick = onPickFromGallery) {
                Text("Elegir de galería / escáner")
            }
        }
    }
}

@Composable
private fun PermissionMissingView(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null)
        Spacer(Modifier.height(8.dp))
        Text("Se necesita permiso de cámara para escanear sellos.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequest) { Text("Conceder permiso") }
    }
}

@Composable
private fun StatusView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DuplicateWarningView(
    confidence: DuplicateConfidence,
    onContinueAnyway: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(12.dp))
        Text("Posible sello duplicado", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        val message = when (confidence) {
            DuplicateConfidence.CASI_SEGURO -> "Este sello parece ser idéntico (imagen y datos) a uno que ya tienes en tu colección."
            DuplicateConfidence.PROBABLE -> "La imagen es muy similar a un sello que ya tienes registrado."
            DuplicateConfidence.POSIBLE -> "Encontramos un sello con datos parecidos en tu colección. Podría ser el mismo."
            DuplicateConfidence.NINGUNO -> ""
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            Button(onClick = onContinueAnyway) { Text("Es distinto, continuar") }
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ocurrió un error", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun StampReviewForm(
    uiState: com.filatelia.scanner.ui.viewmodel.ScanUiState,
    onSave: (StampEntity) -> Unit,
    onDiscard: () -> Unit
) {
    val ai = uiState.aiResult

    var country by remember { mutableStateOf(ai?.country ?: "") }
    var era by remember { mutableStateOf(ai?.era ?: "") }
    var faceValue by remember { mutableStateOf(ai?.faceValue ?: "") }
    var series by remember { mutableStateOf(ai?.series ?: "") }
    var condition by remember { mutableStateOf(ai?.condition ?: "") }
    var rarity by remember { mutableStateOf(ai?.rarity ?: "") }
    var issueYear by remember { mutableStateOf(ai?.issueYearEstimate?.toString() ?: "") }
    var motif by remember { mutableStateOf(ai?.motif ?: "") }
    var historicalNote by remember { mutableStateOf(ai?.historicalNote ?: "") }
    var scottNumber by remember { mutableStateOf("") }
    var michelNumber by remember { mutableStateOf("") }
    var yvertNumber by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Ficha del sello", style = MaterialTheme.typography.headlineSmall)

        uiState.processedBitmap?.let {
            Spacer(Modifier.height(12.dp))
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Sello escaneado",
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        }

        uiState.aiUnavailableReason?.let { reason ->
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(reason, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        ai?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Sugerido por IA (confianza ${(it.confidence * 100).toInt()}%) — revisa y corrige antes de guardar.",
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(Modifier.height(12.dp))
        LabeledField("País", country) { country = it }
        LabeledField("Época (ej. 1950-1959)", era) { era = it }
        LabeledField("Año de emisión", issueYear) { issueYear = it }
        LabeledField("Valor nominal", faceValue) { faceValue = it }
        LabeledField("Serie", series) { series = it }
        LabeledField("Estado de conservación", condition) { condition = it }
        LabeledField("Rareza", rarity) { rarity = it }
        LabeledField("Motivo / diseño", motif) { motif = it }
        LabeledField("Nota histórica", historicalNote) { historicalNote = it }
        Spacer(Modifier.height(8.dp))
        Text("Números de catálogo (opcional, verifica en la fuente oficial)", style = MaterialTheme.typography.labelLarge)
        LabeledField("N° catálogo Scott", scottNumber) { scottNumber = it }
        LabeledField("N° catálogo Michel", michelNumber) { michelNumber = it }
        LabeledField("N° catálogo Yvert", yvertNumber) { yvertNumber = it }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDiscard) { Text("Descartar") }
            Button(onClick = {
                val entity = StampEntity(
                    imagePath = uiState.processedImageFile?.absolutePath ?: "",
                    perceptualHash = uiState.perceptualHash ?: "",
                    country = country.ifBlank { null },
                    era = era.ifBlank { null },
                    faceValue = faceValue.ifBlank { null },
                    series = series.ifBlank { null },
                    condition = condition.ifBlank { null },
                    rarity = rarity.ifBlank { null },
                    issueYear = issueYear.toIntOrNull(),
                    motif = motif.ifBlank { null },
                    historicalNote = historicalNote.ifBlank { null },
                    catalogScottNumber = scottNumber.ifBlank { null },
                    catalogMichelNumber = michelNumber.ifBlank { null },
                    catalogYvertNumber = yvertNumber.ifBlank { null },
                    aiSuggested = ai != null,
                    aiConfidence = ai?.confidence
                )
                onSave(entity)
            }) { Text("Guardar en mi colección") }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

private fun createTempImageFile(context: android.content.Context): File {
    val dir = File(context.cacheDir, "stamps_cache").apply { mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
    return File(dir, "IMG_$timestamp.jpg")
}

private fun copyUriToCacheFile(context: android.content.Context, uri: Uri): File {
    val dir = File(context.cacheDir, "stamps_cache").apply { mkdirs() }
    val outFile = File(dir, "PICKED_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        outFile.outputStream().use { output -> input.copyTo(output) }
    }
    return outFile
}
