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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.mochilapp.mobile.ui.viewmodels.CompanyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    viewModel: CompanyViewModel,
    onBack: () -> Unit
) {
    // Si ya hay coordenadas (edición o precarga del perfil), abrir centrado ahí;
    // si no, posición inicial: La Paz, BCS, México
    val savedLat = remember { viewModel.selectedLat.value }
    val savedLng = remember { viewModel.selectedLng.value }
    val hasSaved = savedLat != 0.0 || savedLng != 0.0
    val initialLocation = if (hasSaved) LatLng(savedLat, savedLng) else LatLng(24.1422, -110.3127)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLocation, if (hasSaved) 15f else 12f)
    }

    var markerPosition by remember { mutableStateOf(if (hasSaved) initialLocation else null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Selecciona Ubicación", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            viewModel.updateCoordinates(it.latitude, it.longitude)
                            onBack()
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
                onMapClick = { latLng ->
                    markerPosition = latLng
                }
            ) {
                markerPosition?.let { pos ->
                    val markerState = rememberMarkerState(position = pos)
                    LaunchedEffect(pos) {
                        markerState.position = pos
                    }
                    Marker(
                        state = markerState,
                        title = "Ubicación seleccionada"
                    )
                }
            }

            // Top Tip
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp, start = 20.dp, end = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Toca el mapa para marcar el punto del servicio", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
