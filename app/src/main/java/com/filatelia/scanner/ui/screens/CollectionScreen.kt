package com.filatelia.scanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filatelia.scanner.data.StampEntity
import com.filatelia.scanner.ui.viewmodel.CollectionViewModel
import com.filatelia.scanner.util.CountryFlagHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onStampClick: (StampEntity) -> Unit
) {
    val stamps by viewModel.stamps.collectAsState()
    var selectedCountry by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val groupedByCountry = remember(stamps) {
        stamps.groupBy { it.country?.ifBlank { "Sin País Asignado" } ?: "Sin País Asignado" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedCountry ?: "Colección por Países",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (selectedCountry != null) {
                        IconButton(onClick = { selectedCountry = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar a países")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            // Barra de búsqueda
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por motivo, año o facial...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(14.dp))

            if (stamps.isEmpty()) {
                EmptyCollectionNotice()
            } else if (selectedCountry == null) {
                // VISTA 1: CARPETAS POR PAÍS
                val filteredCountries = groupedByCountry.keys.filter {
                    it.contains(searchQuery, ignoreCase = true)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredCountries) { countryName ->
                        val stampsInCountry = groupedByCountry[countryName].orEmpty()
                        val flag = CountryFlagHelper.getFlag(countryName)
                        val previewStamp = stampsInCountry.firstOrNull()

                        CountryFolderCard(
                            countryName = countryName,
                            flag = flag,
                            stampCount = stampsInCountry.size,
                            previewStamp = previewStamp,
                            onClick = { selectedCountry = countryName }
                        )
                    }
                }
            } else {
                // VISTA 2: SELLOS DEL PAÍS SELECCIONADO
                val stampsOfSelected = groupedByCountry[selectedCountry].orEmpty().filter {
                    it.motif.orEmpty().contains(searchQuery, ignoreCase = true) ||
                    (it.issueYear?.toString() ?: "").contains(searchQuery) ||
                    it.faceValue.orEmpty().contains(searchQuery, ignoreCase = true)
                }

                if (stampsOfSelected.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay sellos que coincidan con la búsqueda.")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(stampsOfSelected) { stamp ->
                            StampItemCard(
                                stamp = stamp,
                                onClick = { onStampClick(stamp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryFolderCard(
    countryName: String,
    flag: String,
    stampCount: Int,
    previewStamp: StampEntity?,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                val imgFile = previewStamp?.imagePath?.let { File(it) }
                if (imgFile != null && imgFile.exists()) {
                    AsyncImage(
                        model = imgFile,
                        contentDescription = countryName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Text(flag, fontSize = 48.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$flag $countryName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$stampCount ${if (stampCount == 1) "sello" else "sellos"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StampItemCard(
    stamp: StampEntity,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val imgFile = File(stamp.imagePath ?: "")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = if (imgFile.exists()) imgFile else stamp.referenceImageUrl,
                    contentDescription = stamp.motif,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stamp.motif?.ifBlank { "Sello sin título" } ?: "Sello sin título",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stamp.faceValue.orEmpty().ifBlank { "S/V" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stamp.issueYear?.toString() ?: stamp.era.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCollectionNotice() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CollectionsBookmark,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Tu colección está vacía",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Usa el botón 'Escanear' para añadir y catalogar tus primeros timbres postales.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
