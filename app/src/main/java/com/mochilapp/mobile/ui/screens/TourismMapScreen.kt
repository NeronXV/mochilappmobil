package com.mochilapp.mobile.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.MarketplaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourismMapScreen(
    marketplaceViewModel: MarketplaceViewModel,
    onServiceClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val services by marketplaceViewModel.services.collectAsState()
    var selectedService by remember { mutableStateOf<ServiceFirestore?>(null) }

    // Filtro local de categoría (no afecta los filtros del home)
    var selectedCategory by remember { mutableStateOf<MapCategory?>(null) }
    val filteredServices = remember(services, selectedCategory) {
        services.filter { service ->
            service.isVisible && service.latitude != 0.0 && service.longitude != 0.0 &&
                (selectedCategory == null || service.type in selectedCategory!!.types)
        }
    }
    // Si el servicio seleccionado queda fuera del filtro, cerrar su tarjeta
    LaunchedEffect(selectedCategory) {
        if (selectedService != null && selectedCategory != null && selectedService!!.type !in selectedCategory!!.types) {
            selectedService = null
        }
    }

    // Fallback: La Paz, BCS, Mexico
    val defaultLocation = LatLng(24.1426, -110.3128)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 12f)
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Centrar la cámara en la ubicación del viajero al obtener permiso
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                val fused = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                @Suppress("MissingPermission")
                fused.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        cameraPositionState.move(
                            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                                LatLng(location.latitude, location.longitude), 14f
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                // Sin permiso: se queda en la ubicación por defecto
            }
        }
    }

    val mapProperties by remember(hasLocationPermission) { 
        mutableStateOf(MapProperties(isMyLocationEnabled = hasLocationPermission)) 
    }
    val uiSettings by remember { 
        mutableStateOf(MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true)) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mochila Map", fontWeight = FontWeight.Black) },
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
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = uiSettings,
                onMapClick = { selectedService = null }
            ) {
                filteredServices.forEach { service ->
                    val isSelected = selectedService?.id == service.id
                    val markerIcon = remember(service.id, service.price, isSelected) {
                        createPricePillMarker(service.price, service.type, isSelected)
                    }

                    Marker(
                        state = MarkerState(position = LatLng(service.latitude, service.longitude)),
                        icon = markerIcon,
                        title = service.name,
                        zIndex = if (isSelected) 1f else 0f,
                        onClick = {
                            selectedService = service
                            true
                        }
                    )
                }
            }

            // Chips flotantes de categoría
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    MapFilterChip(
                        label = "Todos",
                        emoji = "🗺️",
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                }
                items(MapCategory.entries.size) { index ->
                    val category = MapCategory.entries[index]
                    MapFilterChip(
                        label = category.label,
                        emoji = category.emoji,
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = if (selectedCategory == category) null else category
                        }
                    )
                }
            }

            // Service Preview Card
            selectedService?.let { service ->
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp).navigationBarsPadding()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = if (service.imageUrl.isNotEmpty()) service.imageUrl else "https://picsum.photos/200",
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(service.name, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
                                    Text(service.location, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Text("$${service.price} MXN", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                                }
                                IconButton(
                                    onClick = { onServiceClick(service.id) },
                                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Ver", tint = Color.White)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = {
                                    // Navegación con ruta desde la ubicación actual del viajero
                                    val navUri = Uri.parse("google.navigation:q=${service.latitude},${service.longitude}")
                                    val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    if (navIntent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(navIntent)
                                    } else {
                                        // Fallback: ruta en el navegador
                                        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${service.latitude},${service.longitude}")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F3F5), contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Cómo llegar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Categorías del filtro del mapa: agrupan tipos afines para no saturar la UI
enum class MapCategory(val label: String, val emoji: String, val types: List<String>) {
    BOATS("Lanchas", "⛵", listOf("BOAT_TOUR")),
    LODGING("Hospedaje", "🏨", listOf("HOTEL", "HOSTEL", "PROPERTY_RENTAL")),
    FOOD("Comida", "🍽️", listOf("RESTAURANT", "FOOD_STAND")),
    TOURS("Tours", "🧭", listOf("TOUR_AGENCY")),
    TRANSPORT("Transporte", "🚐", listOf("TRANSPORT")),
    OTHER("Otros", "🎒", listOf("OTHER"))
}

@Composable
private fun MapFilterChip(
    label: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color(0xFF1A1C1E)
            )
        }
    }
}

// Emoji representativo por tipo de servicio para el marcador
private fun emojiForType(type: String): String = when (type) {
    "HOTEL", "HOSTEL" -> "🏨"
    "BOAT_TOUR" -> "⛵"
    "RESTAURANT", "FOOD_STAND" -> "🍽️"
    "TRANSPORT" -> "🚐"
    "TOUR_AGENCY" -> "🧭"
    "PROPERTY_RENTAL" -> "🏡"
    else -> "🎒"
}

// Marcador estilo Google Maps/Airbnb: píldora blanca con emoji + precio.
// Al seleccionarse se invierte a azul de marca con texto blanco.
private fun createPricePillMarker(price: Double, type: String, isSelected: Boolean): BitmapDescriptor {
    val label = "${emojiForType(type)} $${if (price % 1.0 == 0.0) price.toInt().toString() else price.toString()}"

    val density = 3f // escala para nitidez en pantallas de alta densidad
    val textSizePx = 13f * density
    val paddingH = 12f * density
    val paddingV = 8f * density
    val cornerRadius = 18f * density
    val shadowOffset = 2f * density

    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = textSizePx
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        color = if (isSelected) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#1A1C1E")
    }

    val textWidth = textPaint.measureText(label)
    val fontMetrics = textPaint.fontMetrics
    val textHeight = fontMetrics.descent - fontMetrics.ascent

    val pillWidth = textWidth + paddingH * 2
    val pillHeight = textHeight + paddingV * 2
    val bitmapWidth = (pillWidth + shadowOffset * 2).toInt()
    val bitmapHeight = (pillHeight + shadowOffset * 2).toInt()

    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Sombra suave
    val shadowPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#33000000")
    }
    canvas.drawRoundRect(
        shadowOffset, shadowOffset * 1.5f, pillWidth + shadowOffset, pillHeight + shadowOffset * 1.5f,
        cornerRadius, cornerRadius, shadowPaint
    )

    // Cuerpo de la píldora
    val bgPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = if (isSelected) android.graphics.Color.parseColor("#006495") else android.graphics.Color.WHITE
    }
    canvas.drawRoundRect(
        shadowOffset, shadowOffset / 2, pillWidth + shadowOffset, pillHeight + shadowOffset / 2,
        cornerRadius, cornerRadius, bgPaint
    )

    // Borde sutil (solo en estado normal)
    if (!isSelected) {
        val borderPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1f * density
            color = android.graphics.Color.parseColor("#E0E0E0")
        }
        canvas.drawRoundRect(
            shadowOffset, shadowOffset / 2, pillWidth + shadowOffset, pillHeight + shadowOffset / 2,
            cornerRadius, cornerRadius, borderPaint
        )
    }

    // Texto centrado
    canvas.drawText(
        label,
        shadowOffset + paddingH,
        shadowOffset / 2 + paddingV - fontMetrics.ascent,
        textPaint
    )

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
