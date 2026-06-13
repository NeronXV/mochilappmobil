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
import com.mochilapp.mobile.ui.theme.serviceTypeLabel
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.MarketplaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailScreen(
    serviceId: String,
    viewModel: MarketplaceViewModel,
    onBookClick: (String) -> Unit,
    onBack: () -> Unit,
    userName: String = ""
) {
    var service by remember { mutableStateOf<ServiceFirestore?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val activePromo by viewModel.activePromo.collectAsState()
    val reviews by remember(serviceId) { viewModel.getReviewsForService(serviceId) }
        .collectAsState(initial = emptyList())
    val activeNotices by viewModel.activeNotices.collectAsState()
    var showReviewDialog by remember { mutableStateOf(false) }

    LaunchedEffect(serviceId, refreshKey) {
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
                                formatMxn(s.price),
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
                            onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        android.content.Intent.EXTRA_TEXT,
                                        "¡Mira esta experiencia en Mochilapp! 🎒\n\n${s.name} en ${displayLocation(s.location)} desde ${formatMxn(s.price)} MXN.\n\nDescarga Mochilapp para reservar."
                                    )
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir experiencia"))
                            },
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
                                serviceTypeLabel(s.type),
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

                // Avisos operativos del negocio (retrasos, cierres, cambios)
                val today = remember {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(java.util.Date())
                }
                val serviceNotices = activeNotices.filter {
                    it.serviceId == s.id && (it.date.isEmpty() || it.date >= today)
                }
                if (serviceNotices.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        serviceNotices.forEach { notice ->
                            NoticeBanner(notice)
                        }
                    }
                }

                // Info Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailBadge(
                        Icons.Default.Star,
                        if (s.reviewCount > 0) String.format(java.util.Locale.US, "%.1f", s.rating) else "Nuevo",
                        if (s.reviewCount > 0) "${s.reviewCount} reseñas" else t("rating")
                    )
                    DetailBadge(
                        Icons.Default.People,
                        if (s.capacity > 0) "1-${s.capacity}" else "Libre",
                        t("people_count")
                    )
                    DetailBadge(
                        Icons.Default.Place,
                        displayLocation(s.location.split(",").first().trim()).take(10),
                        t("location")
                    )
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
                                Text(displayLocation(s.location), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
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
                    
                    Spacer(Modifier.height(32.dp))

                    // Reseñas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reseñas (${reviews.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showReviewDialog = true }) {
                            Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Escribir reseña", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (reviews.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.StarBorder, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Sé el primero en reseñar esta experiencia", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            reviews.take(5).forEach { review ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(review.authorName.ifEmpty { "Viajero" }, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                            repeat(5) { i ->
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = if (i < review.rating) Color(0xFFFFD43B) else Color(0xFFE9ECEF),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        if (review.comment.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(review.comment, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
                                        }
                                    }
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

    if (showReviewDialog) {
        var newRating by remember { mutableIntStateOf(5) }
        var newComment by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showReviewDialog = false },
            title = { Text("Tu reseña", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        repeat(5) { i ->
                            IconButton(onClick = { newRating = i + 1 }) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Estrella ${i + 1}",
                                    tint = if (i < newRating) Color(0xFFFFD43B) else Color(0xFFE9ECEF),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newComment,
                        onValueChange = { newComment = it },
                        label = { Text("Cuéntanos tu experiencia (opcional)") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addReview(serviceId, userName.ifEmpty { "Viajero" }, newRating, newComment.trim())
                    showReviewDialog = false
                    refreshKey++
                }) { Text("Publicar") }
            },
            dismissButton = {
                TextButton(onClick = { showReviewDialog = false }) { Text("Cancelar") }
            }
        )
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
