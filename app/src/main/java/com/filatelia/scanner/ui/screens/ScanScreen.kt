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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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
            is ScanStep.Preprocessing -> StatusView("Procesando imagen (recorte y normalización)...")
            is ScanStep.CheckingDuplicates -> StatusView("Verificando duplicados en tu colección...")
            is ScanStep.RunningAi -> StatusView("Analizando con IA de visión filatélica...")
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
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Escanear Sello Postal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Centra el sello con buena iluminación sobre un fondo neutro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
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
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val photoFile = createTempImageFile(context)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onCaptured(photoFile)
                            }
                            override fun onError(exception: ImageCaptureException) {}
                        }
                    )
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Capturar", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            }
        }
    }
}

@Composable
private fun StatusView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
        Spacer(Modifier.height(20.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DuplicateWarningView(
    confidence: DuplicateConfidence,
    onContinueAnyway: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("¡Posible Sello Duplicado!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            val message = when (confidence) {
                DuplicateConfidence.CASI_SEGURO -> "Este sello es idéntico a uno ya existente en tu colección."
                DuplicateConfidence.PROBABLE -> "La imagen tiene alta coincidencia con otro de tus sellos guardados."
                DuplicateConfidence.POSIBLE -> "Existe un sello con datos similares registrados previamente."
                DuplicateConfidence.NINGUNO -> ""
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Descartar") }
                Button(onClick = onContinueAnyway, modifier = Modifier.weight(1f)) { Text("Continuar") }
            }
        }
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
    var michelNumber by remember { mutableStateOf(ai?.catalogMichelNumber ?: "") }
    var scottNumber by remember { mutableStateOf(ai?.catalogScottNumber ?: "") }
    var yvertNumber by remember { mutableStateOf(ai?.catalogYvertNumber ?: "") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Ficha del Sello Identificado", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        uiState.processedBitmap?.let {
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Sello escaneado",
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            }
        }

        ai?.let {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Identificado por IA (Certeza: ${(it.confidence * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Información Básica", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LabeledField("País / Entidad emisora (ej. Alemania, RDA)", country) { country = it }
        LabeledField("Año de emisión", issueYear) { issueYear = it }
        LabeledField("Valor facial (ej. 40 Pf)", faceValue) { faceValue = it }
        LabeledField("Época histórica", era) { era = it }
        LabeledField("Serie o emisión", series) { series = it }
        LabeledField("Diseño / Motivo ilustrado", motif) { motif = it }
        LabeledField("Estado de conservación", condition) { condition = it }

        Spacer(Modifier.height(12.dp))
        Text("Catálogos Filatélicos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LabeledField("Nº Catálogo Michel (ej. MiNr. 814)", michelNumber) { michelNumber = it }
        LabeledField("Nº Catálogo Scott", scottNumber) { scottNumber = it }
        LabeledField("Nº Catálogo Yvert", yvertNumber) { yvertNumber = it }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f).height(48.dp)) {
                Text("Descartar")
            }
            Button(
                onClick = {
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
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Guardar Sello")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun PermissionMissingView(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(10.dp))
        Text("Permiso de cámara necesario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequest) { Text("Conceder permiso") }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Aviso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
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
