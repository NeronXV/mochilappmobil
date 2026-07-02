package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

// Punto de partida por defecto: La Paz, BCS, México
private val DefaultLocation = LatLng(24.1422, -110.3127)

// Selector de ubicación autocontenido (sin ViewModel): sirve tanto en el
// registro de empresa como en cualquier flujo que necesite un pin en el mapa.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerDialog(
    initialLat: Double = 0.0,
    initialLng: Double = 0.0,
    title: String = "Selecciona Ubicación",
    hint: String = "Toca el mapa para marcar el punto exacto",
    onPick: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val startPosition = if (initialLat != 0.0 || initialLng != 0.0)
        LatLng(initialLat, initialLng) else DefaultLocation
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(startPosition, if (startPosition == DefaultLocation) 12f else 15f)
    }

    var markerPosition by remember {
        mutableStateOf(if (initialLat != 0.0 || initialLng != 0.0) LatLng(initialLat, initialLng) else null)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Black) },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                if (markerPosition != null) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            markerPosition?.let {
                                onPick(it.latitude, it.longitude)
                                onDismiss()
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Usar esta ubicación", fontWeight = FontWeight.Bold)
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { latLng -> markerPosition = latLng }
                ) {
                    markerPosition?.let { pos ->
                        val markerState = rememberMarkerState(position = pos)
                        LaunchedEffect(pos) { markerState.position = pos }
                        Marker(state = markerState, title = "Ubicación seleccionada")
                    }
                }

                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp, start = 20.dp, end = 20.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    shadowElevation = 4.dp
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(hint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
