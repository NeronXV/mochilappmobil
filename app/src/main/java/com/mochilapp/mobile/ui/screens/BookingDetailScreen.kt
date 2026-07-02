package com.mochilapp.mobile.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mochilapp.mobile.data.BookingFirestore
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.data.UserFirestore
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.BookingViewModel
import com.mochilapp.mobile.ui.viewmodels.MarketplaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    bookingId: String,
    bookingViewModel: BookingViewModel,
    marketplaceViewModel: MarketplaceViewModel,
    onPayNow: (String) -> Unit,
    onBack: () -> Unit
) {
    val myBookings by bookingViewModel.myBookings.collectAsState()
    val booking = myBookings.find { it.id == bookingId }
    var service by remember { mutableStateOf<ServiceFirestore?>(null) }
    var ownerContact by remember { mutableStateOf<UserFirestore?>(null) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(booking) {
        booking?.let {
            service = marketplaceViewModel.getServiceById(it.serviceId)
        }
    }

    LaunchedEffect(booking?.ownerEmail) {
        val email = booking?.ownerEmail
        if (!email.isNullOrEmpty()) ownerContact = marketplaceViewModel.getUserByEmail(email)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(t("booking_details"), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (booking == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF8F9FA))
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Header
                Surface(
                    color = when (booking.status) {
                        "PAID" -> Color(0xFFD4EFDF)
                        "PENDING" -> Color(0xFFFDEBD0)
                        else -> Color(0xFFE9ECEF)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (booking.status == "PAID") Icons.Default.CheckCircle else Icons.Default.Pending,
                            contentDescription = null,
                            tint = when (booking.status) {
                                "PAID" -> Color(0xFF1D8348)
                                "PENDING" -> Color(0xFFD68910)
                                else -> Color.Gray
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (booking.status) {
                                "PAID" -> t("paid_status")
                                "PENDING" -> t("pending_status")
                                "CANCELLED" -> t("cancelled_status")
                                else -> booking.status
                            },
                            fontWeight = FontWeight.Bold,
                            color = when (booking.status) {
                                "PAID" -> Color(0xFF1D8348)
                                "PENDING" -> Color(0xFFD68910)
                                else -> Color.Gray
                            }
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Ticket Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = booking.serviceName.ifEmpty { "Experiencia Mochilapp" },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            if (booking.checkOutDate.isNotEmpty()) {
                                TicketInfoItem("ENTRADA", booking.date, Modifier.weight(1f))
                                TicketInfoItem("SALIDA", booking.checkOutDate, Modifier.weight(1f))
                            } else {
                                TicketInfoItem(t("date").uppercase(), booking.date, Modifier.weight(1f))
                                TicketInfoItem("HORA", booking.departureTime.ifEmpty { "--" }, Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TicketInfoItem(t("people").uppercase(), "${booking.slots} pers.", Modifier.weight(1f))
                            TicketInfoItem("TOTAL", "${formatMxn(booking.totalPrice)} MXN", Modifier.weight(1f))
                        }
                        
                        if (booking.discountAmount > 0) {
                            Spacer(Modifier.height(16.dp))
                            Surface(color = Color(0xFFD4EFDF), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "Ahorro aplicado: ${formatMxn(booking.discountAmount)} (${booking.promoCode})",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D8348)
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = Color(0xFFF1F3F5))
                        Spacer(Modifier.height(24.dp))
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("CÓDIGO DE CONFIRMACIÓN", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                text = booking.confirmationCode.ifEmpty { booking.id.takeLast(6).uppercase() },
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.height(16.dp))
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(140.dp), tint = Color.DarkGray)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Reserva sin pagar: permitir completar el pago
                if (booking.status == "PENDING") {
                    Button(
                        onClick = { onPayNow(booking.id) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Pagar ahora · ${formatMxn(booking.totalPrice)}", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Check-in "Vive": el viajero registra su visita en el lugar
                if (booking.status == "PAID" || booking.status == "CHECKED_IN") {
                    TravelerCheckInCard(
                        booking = booking,
                        service = service,
                        onCheckIn = { bookingViewModel.travelerCheckIn(booking.id) }
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // Action Buttons
                Button(
                    onClick = {
                        val lat = service?.latitude ?: 0.0
                        val lng = service?.longitude ?: 0.0
                        val addr = service?.address ?: service?.location ?: ""
                        
                        val gmmIntentUri = if (lat != 0.0 && lng != 0.0) {
                            Uri.parse("geo:0,0?q=$lat,$lng(${booking.serviceName})")
                        } else {
                            Uri.parse("geo:0,0?q=${Uri.encode(addr)}")
                        }
                        
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F3F5), contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(t("how_to_get_there"), fontWeight = FontWeight.Bold)
                }
                
                // Contacto directo con la empresa sobre esta reserva
                ownerContact?.let { owner ->
                    if (owner.whatsapp.isNotBlank() || owner.phone.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        val code = booking.confirmationCode.ifEmpty { booking.id.takeLast(6).uppercase() }
                        ContactCompanyButton(
                            whatsapp = owner.whatsapp,
                            phone = owner.phone,
                            message = "¡Hola! Tengo una reserva en \"${booking.serviceName}\" para el ${booking.date} (código $code). Tengo una pregunta 🎒"
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = onBack) {
                    Text(t("continue"), color = Color.Gray)
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun TravelerCheckInCard(
    booking: BookingFirestore,
    service: ServiceFirestore?,
    onCheckIn: () -> Unit
) {
    val context = LocalContext.current

    // Ya hizo check-in: estado de éxito.
    if (booking.travelerCheckedInAt > 0L) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFD4EFDF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF1D8348))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("¡Visita registrada! 🎒", fontWeight = FontWeight.Black, color = Color(0xFF1D8348))
                    Text("Sellaste esta aventura en tu Pasaporte.", fontSize = 12.sp, color = Color(0xFF1D8348))
                }
            }
        }
        return
    }

    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    fun attemptCheckIn() {
        val lat = service?.latitude ?: 0.0
        val lng = service?.longitude ?: 0.0
        // Sin coordenadas no se puede verificar proximidad: registramos directo.
        if (lat == 0.0 && lng == 0.0) {
            onCheckIn()
            return
        }
        loading = true
        message = null
        try {
            val fused = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            @Suppress("MissingPermission")
            fused.lastLocation
                .addOnSuccessListener { loc ->
                    loading = false
                    if (loc == null) {
                        message = "No pudimos obtener tu ubicación. Activa el GPS e intenta de nuevo."
                        return@addOnSuccessListener
                    }
                    val res = FloatArray(1)
                    android.location.Location.distanceBetween(loc.latitude, loc.longitude, lat, lng, res)
                    val meters = res[0]
                    if (meters <= 500f) {
                        onCheckIn()
                    } else {
                        val dist = if (meters >= 1000f)
                            String.format(java.util.Locale.US, "%.1f km", meters / 1000f)
                        else "${meters.toInt()} m"
                        message = "Estás a $dist del lugar. Acércate para registrar tu visita."
                    }
                }
                .addOnFailureListener {
                    loading = false
                    message = "No pudimos obtener tu ubicación. Intenta de nuevo."
                }
        } catch (e: SecurityException) {
            loading = false
            message = "Concede el permiso de ubicación para hacer check-in."
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("¿Ya llegaste?", fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Haz check-in en el lugar para sellar esta aventura en tu Pasaporte y ganar MochiPuntos.",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (!hasPermission) {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        message = "Concede el permiso de ubicación y vuelve a tocar."
                    } else {
                        attemptCheckIn()
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Verified, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Hacer check-in", fontWeight = FontWeight.Bold)
                }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
