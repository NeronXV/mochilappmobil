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
                // Filter real services with valid coordinates
                services.filter { it.isVisible && it.latitude != 0.0 && it.longitude != 0.0 }.forEach { service ->
                    val markerIcon = remember(service.type) {
                        createCustomMarkerIcon(context, service.type)
                    }

                    Marker(
                        state = MarkerState(position = LatLng(service.latitude, service.longitude)),
                        icon = markerIcon,
                        title = service.name,
                        onClick = {
                            selectedService = service
                            true 
                        }
                    )
                }
            }

            // Floating Tip
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp, start = 20.dp, end = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 8.dp
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Explore, contentDescription = null, tint = Color(0xFF007BFF))
                    Spacer(Modifier.width(12.dp))
                    Text("Explorando experiencias reales", fontWeight = FontWeight.Bold, color = Color.DarkGray, fontSize = 13.sp)
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
                                    Text("$${service.price} USD", fontWeight = FontWeight.ExtraBold, color = Color(0xFF007BFF), fontSize = 16.sp)
                                }
                                IconButton(
                                    onClick = { onServiceClick(service.id) },
                                    modifier = Modifier.clip(CircleShape).background(Color(0xFF007BFF))
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Ver", tint = Color.White)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = {
                                    val gmmIntentUri = Uri.parse("geo:${service.latitude},${service.longitude}?q=${service.latitude},${service.longitude}(${service.name})")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    mapIntent.setPackage("com.google.android.apps.maps")
                                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(mapIntent)
                                    } else {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
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

private fun createCustomMarkerIcon(context: Context, type: String): BitmapDescriptor {
    val (iconRes, color) = when (type) {
        "HOTEL" -> Pair(android.R.drawable.ic_menu_myplaces, android.graphics.Color.parseColor("#007BFF"))
        "BOAT_TOUR" -> Pair(android.R.drawable.ic_menu_directions, android.graphics.Color.parseColor("#28A745"))
        "RESTAURANT" -> Pair(android.R.drawable.ic_menu_compass, android.graphics.Color.parseColor("#FD7E14"))
        else -> Pair(android.R.drawable.ic_menu_mapmode, android.graphics.Color.parseColor("#6F42C1"))
    }

    val size = 100
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = android.graphics.Paint().apply { this.color = color; isAntiAlias = true }
    
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.apply { style = android.graphics.Paint.Style.STROKE; this.color = android.graphics.Color.WHITE; strokeWidth = 6f }
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 3, paint)

    ContextCompat.getDrawable(context, iconRes)?.let {
        it.setTint(android.graphics.Color.WHITE)
        val iconSize = 50
        val offset = (size - iconSize) / 2
        it.setBounds(offset, offset, offset + iconSize, offset + iconSize)
        it.draw(canvas)
    }
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
