package com.filatelia.scanner.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filatelia.scanner.ai.AiRecognitionOutcome
import com.filatelia.scanner.ai.AiRecognitionRepository
import com.filatelia.scanner.ai.StampRecognitionResult
import com.filatelia.scanner.data.StampEntity
import com.filatelia.scanner.data.StampRepository
import com.filatelia.scanner.duplicate.DuplicateResult
import com.filatelia.scanner.imageprocessing.ImagePreprocessor
import com.filatelia.scanner.imageprocessing.PerceptualHash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ScanStep {
    object Idle : ScanStep()
    object Preprocessing : ScanStep()
    object CheckingDuplicates : ScanStep()
    object RunningAi : ScanStep()
    object ReadyToSave : ScanStep()
    data class DuplicateFound(val result: DuplicateResult) : ScanStep()
    data class Error(val message: String) : ScanStep()
}

data class ScanUiState(
    val step: ScanStep = ScanStep.Idle,
    val processedBitmap: Bitmap? = null,
    val processedImageFile: File? = null,
    val perceptualHash: String? = null,
    val aiResult: StampRecognitionResult? = null,
    val aiUnavailableReason: String? = null
)

class ScanViewModel(
    private val stampRepository: StampRepository,
    private val aiRepository: AiRecognitionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /**
     * Punto de entrada tras capturar la foto (cámara o escáner conectado).
     * 1) Preprocesa (recorte/normalización)
     * 2) Calcula el hash perceptual
     * 3) Revisa duplicados en la colección local
     * 4) Si no hay duplicado bloqueante, consulta la IA para sugerir metadatos
     */
    fun onImageCaptured(originalFile: File) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(step = ScanStep.Preprocessing)
                val rawBitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
                val processed = ImagePreprocessor.processFull(originalFile, rawBitmap)

                val outputFile = File(originalFile.parentFile, "processed_${originalFile.name}")
                ImagePreprocessor.saveToFile(processed, outputFile)

                val hash = PerceptualHash.compute(processed)

                _uiState.value = _uiState.value.copy(
                    step = ScanStep.CheckingDuplicates,
                    processedBitmap = processed,
                    processedImageFile = outputFile,
                    perceptualHash = hash
                )

                val duplicate = stampRepository.checkForDuplicate(hash, null, null, null)
                if (duplicate.confidence.name != "NINGUNO") {
                    _uiState.value = _uiState.value.copy(step = ScanStep.DuplicateFound(duplicate))
                    return@launch
                }

                runAiRecognition(processed)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(step = ScanStep.Error(t.message ?: "Error al procesar la imagen"))
            }
        }
    }

    /** El usuario decide seguir de todas formas aunque se haya detectado un posible duplicado. */
    fun continueDespiteDuplicate() {
        val bitmap = _uiState.value.processedBitmap ?: return
        viewModelScope.launch { runAiRecognition(bitmap) }
    }

    private suspend fun runAiRecognition(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(step = ScanStep.RunningAi)
        when (val outcome = aiRepository.recognize(bitmap)) {
            is AiRecognitionOutcome.Success -> {
                _uiState.value = _uiState.value.copy(step = ScanStep.ReadyToSave, aiResult = outcome.result)
            }
            is AiRecognitionOutcome.MissingApiKey -> {
                // No es un error fatal: el usuario puede llenar los campos a mano.
                _uiState.value = _uiState.value.copy(
                    step = ScanStep.ReadyToSave,
                    aiUnavailableReason = "No hay una API key de IA configurada. Puedes llenar los datos manualmente o configurarla en local.properties."
                )
            }
            is AiRecognitionOutcome.QuotaExceeded -> {
                _uiState.value = _uiState.value.copy(
                    step = ScanStep.ReadyToSave,
                    aiUnavailableReason = "Se alcanzó el límite gratuito de solicitudes de hoy. Intenta más tarde o llena los datos manualmente."
                )
            }
            is AiRecognitionOutcome.Error -> {
                _uiState.value = _uiState.value.copy(
                    step = ScanStep.ReadyToSave,
                    aiUnavailableReason = "No se pudo consultar la IA: ${outcome.message}. Puedes llenar los datos manualmente."
                )
            }
        }
    }

    fun saveStamp(entity: StampEntity, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = stampRepository.saveStamp(entity)
            _uiState.value = ScanUiState() // reset
            onSaved(id)
        }
    }

    fun reset() {
        _uiState.value = ScanUiState()
    }
}
