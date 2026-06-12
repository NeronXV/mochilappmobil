package com.mochilapp.mobile.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.R
import kotlinx.coroutines.delay

// Identidad Mochilapp: del azul marino profundo al azul de marca,
// con el verde como acento (no como fondo, para que no se ensucie el degradado)
private val DeepNavy = Color(0xFF00131F)
private val BrandBlue = Color(0xFF006495)
private val BrandGreen = Color(0xFF2E7D32)
private val AccentAqua = Color(0xFF4DD0A6)

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    // Puntos de carga con desfase (efecto "ola")
    val dotPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )

    val fadeAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        fadeAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900)
        )
        delay(2000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepNavy, BrandBlue)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Resplandor verde sutil en la esquina inferior (acento de marca)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandGreen.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(fadeAnim.value)
        ) {
            // Logo con halo suave
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(scale)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                )
                Image(
                    painter = painterResource(id = R.drawable.logoblanco),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(150.dp)
                        .scale(scale),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Mochilapp",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Línea de acento azul → verde: la firma visual de la marca
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(AccentAqua, BrandGreen)
                        )
                    )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "EXPLORA • CONECTA • VIAJA",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
        }

        // Puntos de carga animados, en lugar del spinner genérico
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { index ->
                val isActive = dotPhase.toInt() == index
                Box(
                    modifier = Modifier
                        .size(if (isActive) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) AccentAqua else Color.White.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}
