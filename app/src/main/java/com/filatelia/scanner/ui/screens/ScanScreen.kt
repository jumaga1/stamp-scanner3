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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
            is ScanStep.Preprocessing -> StatusView("Procesando encuadre y resolución...")
            is ScanStep.CheckingDuplicates -> StatusView("Comparando con tu inventario de sellos...")
            is ScanStep.RunningAi -> StatusView("Consultando base filatélica de IA...")
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
        Text(
            "Identificador Filatélico Pro",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Enfoca el sello dentro del recuadro para tasar y catalogar automáticamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth().height(420.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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

                // Guía visual de enfoque para el sello
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Galería")
            }

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
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Escanear e Identificar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
            modifier = Modifier.size(54.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Posible Ejemplar Duplicado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            val message = when (confidence) {
                DuplicateConfidence.CASI_SEGURO -> "Este ejemplar coincide plenamente con un sello registrado previamente en tu inventario."
                DuplicateConfidence.PROBABLE -> "La imagen tiene alta correlación visual con tu colección."
                DuplicateConfidence.POSIBLE -> "Existe un sello con país y valor facial similares."
                DuplicateConfidence.NINGUNO -> ""
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancelar")
                }
                Button(onClick = onContinueAnyway, modifier = Modifier.weight(1f)) {
                    Text("Continuar")
                }
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
    var marketValue by remember { mutableStateOf(ai?.estimatedMarketValue ?: "$1.00 - $3.50 USD") }
    var michelNumber by remember { mutableStateOf(ai?.catalogMichelNumber ?: "") }
    var scottNumber by remember { mutableStateOf(ai?.catalogScottNumber ?: "") }
    var yvertNumber by remember { mutableStateOf(ai?.catalogYvertNumber ?: "") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Resultado de Identificación", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // Visualizador de imagen del sello
        uiState.processedBitmap?.let {
            Spacer(Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth().height(250.dp)
            ) {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Sello escaneado",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.05f))
                )
            }
        }

        // Tarjeta destacada de Valor de Mercado
        Spacer(Modifier.height(14.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.MonetizationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "VALOR DE MERCADO ESTIMADO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        marketValue,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        if (ai != null) {
            Spacer(Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Identificado con ${(ai.confidence * 100).toInt()}% de confianza",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        uiState.aiUnavailableReason?.let { reason ->
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(reason, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Ficha Filatélica", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LabeledField("País / Entidad emisora", country) { country = it }
        LabeledField("Año de emisión", issueYear) { issueYear = it }
        LabeledField("Valor facial", faceValue) { faceValue = it }
        LabeledField("Precio de Mercado Estimado", marketValue) { marketValue = it }
        LabeledField("Periodo / Época histórica", era) { era = it }
        LabeledField("Serie o emisión", series) { series = it }
        LabeledField("Motivo o diseño ilustrado", motif) { motif = it }
        LabeledField("Estado de conservación", condition) { condition = it }
        LabeledField("Rareza", rarity) { rarity = it }
        LabeledField("Nota histórica", historicalNote) { historicalNote = it }

        Spacer(Modifier.height(14.dp))
        Text("Catálogos Filatélicos de Referencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LabeledField("Nº Catálogo Michel (MiNr.)", michelNumber) { michelNumber = it }
        LabeledField("Nº Catálogo Scott", scottNumber) { scottNumber = it }
        LabeledField("Nº Catálogo Yvert", yvertNumber) { yvertNumber = it }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp)) {
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
                        estimatedMarketValue = marketValue.ifBlank { null },
                        catalogScottNumber = scottNumber.ifBlank { null },
                        catalogMichelNumber = michelNumber.ifBlank { null },
                        catalogYvertNumber = yvertNumber.ifBlank { null },
                        aiSuggested = ai != null,
                        aiConfidence = ai?.confidence
                    )
                    onSave(entity)
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar en Colección", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun PermissionMissingView(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Permiso de Cámara Requerido", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Para escanear tus estampas y sellos postales se requiere acceso a la cámara.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest, shape = RoundedCornerShape(12.dp)) { Text("Conceder Permiso") }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(16.dp))
        Text("Aviso de Conexión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) { Text("Reintentar") }
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
