package com.mochilapp.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.ui.viewmodels.AuthViewModel
import com.mochilapp.mobile.ui.viewmodels.RewardEvent
import kotlinx.coroutines.delay

/**
 * Overlay global del Pasaporte: escucha los eventos de recompensa del
 * [AuthViewModel] y muestra una tarjeta celebratoria que entra desde arriba
 * y se va sola. Se monta sobre el NavDisplay para aparecer en cualquier
 * pantalla cuando una Cloud Function acredita puntos o una insignia.
 */
@Composable
fun RewardOverlay(authViewModel: AuthViewModel, modifier: Modifier = Modifier) {
    var event by remember { mutableStateOf<RewardEvent?>(null) }
    // Conserva la última recompensa para que la animación de salida tenga
    // contenido que renderizar mientras se desliza hacia arriba.
    var lastEvent by remember { mutableStateOf<RewardEvent?>(null) }
    event?.let { lastEvent = it }

    LaunchedEffect(Unit) {
        authViewModel.rewardEvents.collect { e ->
            event = e
            delay(4000)
            event = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = event != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            lastEvent?.let { RewardCard(it, onDismiss = { event = null }) }
        }
    }
}

@Composable
private fun RewardCard(event: RewardEvent, onDismiss: () -> Unit) {
    val hasBadge = event.newBadge != null

    Surface(
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onDismiss)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emblema circular
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = if (hasBadge) "🏅" else "🎒", fontSize = 22.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (hasBadge) {
                    Text(
                        text = "¡Insignia desbloqueada!",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    Text(
                        text = event.newBadge!! +
                            if (event.pointsGained > 0) "  ·  +${event.pointsGained} MochiPuntos" else "",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "+${event.pointsGained} MochiPuntos",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "¡Sigue explorando y sella tu pasaporte!",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
