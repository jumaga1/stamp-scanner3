package com.filatelia.scanner.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit
) {
    // Fondo degradado plateado metálico / titanio
    val silverGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF2F5F8), // Plateado claro brillante
            Color(0xFFD6DCE2), // Plata metálica
            Color(0xFFB8C2CC), // Plata satinada
            Color(0xFF9BA6B2)  // Titanio oscuro
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(silverGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // SECCIÓN SUPERIOR: ESTAMPILLA FILATÉLICA ANIMADA Y TÍTULO
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                // Ícono de Estampilla Animada
                AnimatedStampIcon()

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "STAMP SCANNER PRO",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF2B3A4A),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.5.sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Identificador & Álbum Filatélico",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF141C24),
                    textAlign = TextAlign.Center
                )
            }

            // SECCIÓN CENTRAL: TARJETAS CON ACABADO PLATEADO
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureCard(
                    icon = Icons.Default.AutoAwesome,
                    title = "Reconocimiento con IA & Zoom",
                    description = "Identifica país, catálogo Michel, Scott, Yvert y año al instante."
                )

                FeatureCard(
                    icon = Icons.Default.MonetizationOn,
                    title = "Tasación y Valor de Mercado",
                    description = "Estima el precio comercial actualizado en el mercado numismático y filatélico."
                )

                FeatureCard(
                    icon = Icons.Default.CollectionsBookmark,
                    title = "Álbum por País y Año",
                    description = "Organiza tu colección clasificada cronológicamente con banderas del mundo."
                )
            }

            // SECCIÓN INFERIOR: BOTÓN DE INICIO ELEVADO
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A2634)
                    )
                ) {
                    Text(
                        text = "Comenzar a Escanear",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Edición Filatélica Premium • Colección Ilimitada",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF3A4754),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Componente gráfico: Estampilla Postal con animación de levitación, rotación suave,
 * bordes perforados (perforation teeth) y matasellos vintage circular.
 */
@Composable
fun AnimatedStampIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "stampAnimation")

    // Animación de levitación vertical
    val translateY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translateY"
    )

    // Animación de ligera oscilación/rotación
    val rotation by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    // Animación de pulso/escala
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(y = translateY.dp)
            .rotate(rotation)
            .scale(scale)
            .size(110.dp)
            .shadow(12.dp, RoundedCornerShape(6.dp), ambientColor = Color(0xFF1A2634))
    ) {
        // Lienzo para dibujar la estampilla perforada
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Fondo blanco crema de la estampilla
            drawRoundRect(
                color = Color(0xFFFAFBFD),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            )

            // Borde dentado filatélico (perforaciones punteadas)
            val strokeWidth = 3.dp.toPx()
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
            drawRoundRect(
                color = Color(0xFFB0BAC5),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                style = Stroke(width = strokeWidth, pathEffect = dashEffect)
            )

            // Recuadro interior de la ilustración
            val margin = 10.dp.toPx()
            drawRect(
                color = Color(0xFF233549),
                topLeft = Offset(margin, margin),
                size = androidx.compose.ui.geometry.Size(w - margin * 2, h - margin * 2)
            )

            // Matasellos circular vintage superpuesto
            val postmarkRadius = 24.dp.toPx()
            val postmarkCenter = Offset(w * 0.75f, h * 0.35f)
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                radius = postmarkRadius,
                center = postmarkCenter,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.28f),
                start = Offset(postmarkCenter.x - postmarkRadius, postmarkCenter.y),
                end = Offset(postmarkCenter.x + postmarkRadius + 14.dp.toPx(), postmarkCenter.y),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // Contenido dentro del sello
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = "DEUTSCHE POST",
                color = Color(0xFFD4AF37),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "👑",
                fontSize = 22.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "80 Pf",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.88f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB8C2CC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF233549))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFE2E8F0),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A2634)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4A5766)
                )
            }
        }
    }
}
