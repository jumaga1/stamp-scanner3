package com.filatelia.scanner.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.filatelia.scanner.catalog.CatalogLinkBuilder
import com.filatelia.scanner.data.StampEntity

@Composable
fun StampDetailScreen(stamp: StampEntity, onDelete: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(stamp.imagePath) {
        runCatching { BitmapFactory.decodeFile(stamp.imagePath) }.getOrNull()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(260.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        Text(
            listOfNotNull(stamp.country, stamp.issueYear?.toString()).joinToString(" · ").ifBlank { "Sin identificar" },
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(12.dp))

        DetailRow("Época", stamp.era)
        DetailRow("Valor nominal", stamp.faceValue)
        DetailRow("Serie", stamp.series)
        DetailRow("Estado de conservación", stamp.condition)
        DetailRow("Rareza", stamp.rarity)
        DetailRow("Motivo / diseño", stamp.motif)
        DetailRow("Tiraje", stamp.printRun)
        DetailRow("Valor histórico", stamp.historicalNote)
        DetailRow("N° catálogo Scott", stamp.catalogScottNumber)
        DetailRow("N° catálogo Michel", stamp.catalogMichelNumber)
        DetailRow("N° catálogo Yvert", stamp.catalogYvertNumber)

        if (stamp.aiSuggested) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Datos sugeridos por IA (confianza ${((stamp.aiConfidence ?: 0f) * 100).toInt()}%)",
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("Consultar catálogos internacionales", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val links = CatalogLinkBuilder.buildLinks(stamp.country, stamp.series, stamp.faceValue)
        links.forEach { link ->
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
            }) {
                Text(link.label)
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Volver") }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Eliminar de mi colección") }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
