package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.MarketplaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailScreen(
    serviceId: String,
    viewModel: MarketplaceViewModel,
    onBookClick: (String) -> Unit,
    onBack: () -> Unit
) {
    var service by remember { mutableStateOf<ServiceFirestore?>(null) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val activePromo by viewModel.activePromo.collectAsState()

    LaunchedEffect(serviceId) {
        service = viewModel.getServiceById(serviceId)
    }

    Scaffold(
        bottomBar = {
            service?.let { s ->
                Surface(
                    tonalElevation = 12.dp,
                    shadowElevation = 24.dp,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t("total_price"), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                "$${s.price}",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Button(
                            onClick = { onBookClick(s.id) },
                            modifier = Modifier
                                .height(56.dp)
                                .weight(1.2f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(t("book_now"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        service?.let { s ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .background(Color(0xFFF8F9FA))
            ) {
                // Image Header
                Box(modifier = Modifier.height(400.dp)) {
                    AsyncImage(
                        model = if (s.imageUrl.isNotEmpty()) s.imageUrl else "https://picsum.photos/seed/${s.id}/800/800",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Gradient Overlays
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(0.4f), Color.Transparent, Color.Black.copy(0.6f)))
                    ))

                    // Top Bar Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.background(Color.White.copy(0.2f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                        IconButton(
                            onClick = { /* Share */ },
                            modifier = Modifier.background(Color.White.copy(0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                        }
                    }

                    // Promo Banner
                    activePromo?.let { promo ->
                        if (promo.serviceId == s.id) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 100.dp),
                                color = Color(0xFFFFD43B),
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = 8.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "OFERTA APLICADA: ${promo.discountPercent}% OFF",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }

                    // Floating Title on Image
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                s.type,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.name,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
                        )
                    }
                }

                // Info Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailBadge(Icons.Default.Star, "4.9", t("rating"))
                    DetailBadge(Icons.Default.Timer, "2h", t("duration"))
                    DetailBadge(Icons.Default.People, "1-10", t("people_count"))
                }

                // Description
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(t("about"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        s.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.DarkGray,
                        lineHeight = 26.sp
                    )
                    
                    Spacer(Modifier.height(32.dp))
                    
                    // Location Preview
                    Text(t("location"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(s.location, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                            
                            if (s.address.isNotEmpty()) {
                                Column {
                                    Text("Dirección:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text(s.address, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            
                            if (s.meetingPoint.isNotEmpty()) {
                                Column {
                                    Text("Punto de encuentro:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text(s.meetingPoint, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1ABC9C), fontWeight = FontWeight.Bold)
                                }
                            }

                            if (s.latitude != 0.0 && s.longitude != 0.0) {
                                Button(
                                    onClick = { 
                                        // Open external map
                                        val gmmIntentUri = android.net.Uri.parse("geo:${s.latitude},${s.longitude}?q=${s.latitude},${s.longitude}(${s.name})")
                                        val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri)
                                        mapIntent.setPackage("com.google.android.apps.maps")
                                        context.startActivity(mapIntent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F3F5), contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ver en Google Maps", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(150.dp))
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun DetailBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(56.dp),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}
