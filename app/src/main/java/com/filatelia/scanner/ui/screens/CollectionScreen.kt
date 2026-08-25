package com.filatelia.scanner.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import com.filatelia.scanner.data.SortOrder
import com.filatelia.scanner.data.StampEntity
import com.filatelia.scanner.ui.viewmodel.CollectionViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onStampClick: (StampEntity) -> Unit
) {
    val stamps by viewModel.stamps.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("Buscar por país, serie, motivo o N° de catálogo") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScrollableChips()) {
            SortChip("Recientes", sortOrder == SortOrder.RECIENTES) { viewModel.setSortOrder(SortOrder.RECIENTES) }
            SortChip("País", sortOrder == SortOrder.PAIS) { viewModel.setSortOrder(SortOrder.PAIS) }
            SortChip("Año", sortOrder == SortOrder.ANIO) { viewModel.setSortOrder(SortOrder.ANIO) }
            SortChip("Serie", sortOrder == SortOrder.SERIE) { viewModel.setSortOrder(SortOrder.SERIE) }
            SortChip("Rareza", sortOrder == SortOrder.RAREZA) { viewModel.setSortOrder(SortOrder.RAREZA) }
        }

        Spacer(Modifier.height(8.dp))

        if (stamps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Aún no tienes sellos escaneados. Ve a la pestaña Escanear para agregar el primero.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(stamps, key = { it.id }) { stamp ->
                    StampListItem(stamp = stamp, onClick = { onStampClick(stamp) })
                }
            }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(end = 6.dp)
    )
}

private fun Modifier.horizontalScrollableChips(): Modifier =
    this.then(Modifier)

@Composable
private fun StampListItem(stamp: StampEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            val bitmap = remember(stamp.imagePath) {
                runCatching { BitmapFactory.decodeFile(stamp.imagePath) }.getOrNull()
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                bitmap?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    listOfNotNull(stamp.country, stamp.issueYear?.toString()).joinToString(" · ").ifBlank { "Sin identificar" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    listOfNotNull(stamp.series, stamp.faceValue).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall
                )
                stamp.rarity?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (stamp.aiSuggested) {
                AssistChip(onClick = {}, label = { Text("IA") })
            }
        }
    }
}
