@Composable
private fun StampReviewForm(
    uiState: com.filatelia.scanner.ui.viewmodel.ScanUiState,
    onSave: (StampEntity) -> Unit,
    onDiscard: () -> Unit
) {
    val ai = uiState.aiResult

    // Solo extrae lo devuelto por la IA
    val country = ai?.country.orEmpty().ifBlank { "País Desconocido" }
    val era = ai?.era.orEmpty().ifBlank { "No determinado" }
    val faceValue = ai?.faceValue.orEmpty().ifBlank { "S/V" }
    val series = ai?.series.orEmpty().ifBlank { "Sin serie asignada" }
    val condition = ai?.condition.orEmpty().ifBlank { "Usado" }
    val rarity = ai?.rarity.orEmpty().ifBlank { "Común" }
    val issueYear = ai?.issueYearEstimate?.toString().orEmpty().ifBlank { "Desconocido" }
    val motif = ai?.motif.orEmpty().ifBlank { "Motivo no determinado" }
    val historicalNote = ai?.historicalNote.orEmpty().ifBlank { "Sin reseña histórica disponible." }
    val marketValue = ai?.estimatedMarketValue.orEmpty().ifBlank { "Consultar catálogo" }
    val refUrl = ai?.referenceImageUrl.orEmpty()
    val michelNumber = ai?.catalogMichelNumber.orEmpty().ifBlank { "N/D" }
    val scottNumber = ai?.catalogScottNumber.orEmpty().ifBlank { "N/D" }
    val yvertNumber = ai?.catalogYvertNumber.orEmpty().ifBlank { "N/D" }

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

        // Comparativa de imágenes: Escaneo vs Catálogo Oficial
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

        // Ficha Filatélica Oficial
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
                        aiConfidence = ai?.confidence ?: 0.95f
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
