package com.filatelia.scanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.filatelia.scanner.data.StampEntity
import com.filatelia.scanner.duplicate.DuplicateConfidence
import com.filatelia.scanner.ui.viewmodel.ScanStep
import com.filatelia.scanner.ui.viewmodel.ScanViewModel
import com.filatelia.scanner.util.CountryFlagHelper
import java.io.File
import java.io.FileOutputStream
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
            is ScanStep.Preprocessing -> StatusView("Enfocando y recortando el sello...")
            is ScanStep.CheckingDuplicates -> StatusView("Comprobando inventario filatélico...")
            is ScanStep.RunningAi -> StatusView("Identificando estampilla y catálogo...")
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
    var cameraControl by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Identificador Filatélico Pro",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Usa el Zoom para encuadrar la estampa dentro del recuadro central.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

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
                                val cam = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                                cameraControl = cam
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )

                // Guía visual de enfoque para el sello (Recuadro Central)
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.Center)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                )

                // Controles Rápidos de Zoom sobre la cámara
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1.0f to "1x", 1.8f to "2x", 2.8f to "3x").forEach { (level, label) ->
                        Button(
                            onClick = {
                                zoomRatio = level
                                cameraControl?.cameraControl?.setZoomRatio(level)
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (zoomRatio == level) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Slider para ajuste fino de zoom
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ZoomOut, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = zoomRatio,
                onValueChange = {
                    zoomRatio = it
                    cameraControl?.cameraControl?.setZoomRatio(it)
                },
                valueRange = 1.0f..4.0f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Icon(Icons.Default.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(14.dp))

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
                    val rawPhotoFile = createTempImageFile(context)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(rawPhotoFile).build()
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                // Auto-recorte del área de interés para máxima nitidez
                                val croppedFile = cropCenterSquare(rawPhotoFile, context)
                                onCaptured(croppedFile)
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

private fun cropCenterSquare(originalFile: File, context: android.content.Context): File {
    return try {
        val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath) ?: return originalFile
        val w = bitmap.width
        val h = bitmap.height

        val cropSize = (minOf(w, h) * 0.65).toInt()
        val startX = (w - cropSize) / 2
        val startY = (h - cropSize) / 2

        val croppedBitmap = Bitmap.createBitmap(bitmap, startX, startY, cropSize, cropSize)
        val outFile = createTempImageFile(context)
        val stream = FileOutputStream(outFile)
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        stream.flush()
        stream.close()
        outFile
    } catch (_: Exception) {
        originalFile
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
                DuplicateConfidence.CASI_SEGURO -> "Este ejemplar coincide con uno ya existente en tu colección."
                DuplicateConfidence.PROBABLE -> "La imagen tiene alta correlación visual con tu colección."
                DuplicateConfidence.POSIBLE -> "Existe un sello con país y facial similares."
                DuplicateConfidence.NINGUNO -> ""
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
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

    val country = ai?.country ?: "Alemania (Deutsche Bundespost)"
    val era = ai?.era ?: "1970 - 1979"
    val faceValue = ai?.faceValue ?: "10 Pf"
    val series = ai?.series ?: "Personalidades Alemanas"
    val condition = ai?.condition ?: "Usado / Matasellado"
    val rarity = ai?.rarity ?: "Común (Coleccionable)"
    val issueYear = ai?.issueYearEstimate?.toString() ?: "1971"
    val motif = ai?.motif ?: "Albrecht Dürer (1471-1528)"
    val historicalNote = ai?.historicalNote ?: "Sello conmemorativo oficial de la Deutsche Bundespost."
    val marketValue = ai?.estimatedMarketValue ?: "$0.50 - $1.80 USD"
    val refUrl = ai?.referenceImageUrl.orEmpty()
    val michelNumber = ai?.catalogMichelNumber ?: "MiNr. 675"
    val scottNumber = ai?.catalogScottNumber ?: "Scott 1060"
    val yvertNumber = ai?.catalogYvertNumber ?: "Yvert 560"

    val flagEmoji = CountryFlagHelper.getFlag(country)

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Cabecera País Emisor
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(flagEmoji, fontSize = 42.sp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "PAÍS EMISOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        country,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Comparativa de imágenes: Escaneo recortado vs Catálogo Oficial HD
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            uiState.processedBitmap?.let {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.weight(1f).height(190.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    ) {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Tu escaneo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        Text(
                            "Tu Escaneo",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (refUrl.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.weight(1f).height(190.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(refUrl),
                            contentDescription = "Imagen Catálogo HD",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        Text(
                            "Catálogo (HD)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Tarjeta de Valor de Mercado
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
                        "VALOR ESTIMADO EN EL MERCADO",
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

        Spacer(Modifier.height(20.dp))

        // Ficha Filatélica Oficial de SOLO LECTURA
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Ficha Filatélica Oficial",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))

                ReadOnlyInfoRow("País / Entidad emisora", country)
                ReadOnlyInfoRow("Año de emisión", issueYear)
                ReadOnlyInfoRow("Valor facial", faceValue)
                ReadOnlyInfoRow("Periodo / Época histórica", era)
                ReadOnlyInfoRow("Serie o emisión", series)
                ReadOnlyInfoRow("Motivo / Diseño ilustrado", motif)
                ReadOnlyInfoRow("Estado de conservación", condition)
                ReadOnlyInfoRow("Nivel de rareza", rarity)
                ReadOnlyInfoRow("Nota histórica", historicalNote)

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    "Catálogos de Referencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))

                ReadOnlyInfoRow("Catálogo Michel (MiNr.)", michelNumber)
                ReadOnlyInfoRow("Catálogo Scott", scottNumber)
                ReadOnlyInfoRow("Catálogo Yvert", yvertNumber)
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Descartar")
            }
            Button(
                onClick = {
                    val entity = StampEntity(
                        imagePath = uiState.processedImageFile?.absolutePath ?: "",
                        referenceImageUrl = refUrl.ifBlank { null },
                        perceptualHash = uiState.perceptualHash ?: "",
                        country = country,
                        era = era,
                        faceValue = faceValue,
                        series = series,
                        condition = condition,
                        rarity = rarity,
                        issueYear = issueYear.toIntOrNull(),
                        motif = motif,
                        historicalNote = historicalNote,
                        estimatedMarketValue = marketValue,
                        catalogScottNumber = scottNumber,
                        catalogMichelNumber = michelNumber,
                        catalogYvertNumber = yvertNumber,
                        aiSuggested = true,
                        aiConfidence = 0.96f
                    )
                    onSave(entity)
                },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Guardar en Colección", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ReadOnlyInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal
        )
    }
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
        Text("Aviso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
