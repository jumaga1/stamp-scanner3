package com.filatelia.scanner.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.filatelia.scanner.data.StampEntity
import com.filatelia.scanner.ui.viewmodel.CollectionViewModel
import com.filatelia.scanner.util.CountryFlagHelper
import java.io.File

@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onStampClick: (StampEntity) -> Unit
) {
    val stamps by viewModel.stamps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val groupedStamps = remember(stamps) {
        stamps.groupBy { it.country ?: "Sin País Asignado" }
            .mapValues { entry ->
                entry.value.groupBy { it.issueYear ?: 0 }
                    .toSortedMap(compareByDescending { it })
            }
            .toSortedMap()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Álbum de Colección",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${stamps.size} sellos en ${groupedStamps.size} países",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = { Text("Buscar por país, Michel Nº o motivo...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        if (stamps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Tu álbum está vacío.\nEscanea un sello para comenzar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                groupedStamps.forEach { (country, yearsMap) ->
                    item {
                        val flag = CountryFlagHelper.getFlag(country)
                        val totalInCountry = yearsMap.values.sumOf { it.size }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(flag, fontSize = 28.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    country,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "$totalInCountry sellos",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    yearsMap.forEach { (year, stampList) ->
                        item {
                            val yearText = if (year > 0) "Año $year" else "Año no especificado"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    yearText,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    " (${stampList.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(stampList.chunked(2)) { rowStamps ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                rowStamps.forEach { stamp ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        StampCard(stamp = stamp, onClick = { onStampClick(stamp) })
                                    }
                                }
                                if (rowStamps.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StampCard(stamp: StampEntity, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(135.dp)
                    .background(Color.Black.copy(alpha = 0.04f))
            ) {
                // Prioriza la imagen HD oficial si está disponible; de lo contrario muestra la foto escaneada
                val imageModel = stamp.referenceImageUrl?.ifBlank { null } ?: File(stamp.imagePath)

                Image(
                    painter = rememberAsyncImagePainter(model = imageModel),
                    contentDescription = stamp.motif ?: "Sello",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp)
                )
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    stamp.motif ?: (stamp.series ?: "Sello Postal"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    "Facial: ${stamp.faceValue ?: "S/V"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!stamp.estimatedMarketValue.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            stamp.estimatedMarketValue!!,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
