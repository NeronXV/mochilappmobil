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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.BookingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailCompanyScreen(
    bookingId: String,
    bookingViewModel: BookingViewModel,
    userUid: String,
    onBack: () -> Unit
) {
    val ownerBookings by bookingViewModel.ownerBookings.collectAsState()
    val booking = ownerBookings.find { it.id == bookingId }
    val scrollState = rememberScrollState()

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
                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(booking.travelerName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(booking.travelerName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(booking.travelerEmail, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = Color(0xFFF1F3F5))
                        Spacer(Modifier.height(24.dp))

                        Text(text = booking.serviceName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Spacer(Modifier.height(12.dp))
                        
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
                            Text(
                                text = "Descuento aplicado: ${formatMxn(booking.discountAmount)} (${booking.promoCode})",
                                color = Color(0xFF1D8348),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = Color(0xFFF1F3F5))
                        Spacer(Modifier.height(24.dp))

                        Text("CÓDIGO MOCHI", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            text = booking.confirmationCode.ifEmpty { booking.id.takeLast(6).uppercase() },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        
                        Spacer(Modifier.height(32.dp))
                        
                        // Status Actions
                        when (booking.status) {
                            "PAID" -> {
                                Button(
                                    onClick = { bookingViewModel.updateStatus(booking.id, "CHECKED_IN", userUid) },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.HowToReg, contentDescription = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(t("confirm_arrival"), fontWeight = FontWeight.Bold)
                                }
                            }
                            "CHECKED_IN" -> {
                                Button(
                                    onClick = { bookingViewModel.updateStatus(booking.id, "COMPLETED", userUid) },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71))
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(t("mark_completed"), fontWeight = FontWeight.Bold)
                                }
                            }
                            "COMPLETED" -> {
                                Surface(
                                    color = Color(0xFFD4EFDF),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1D8348))
                                        Spacer(Modifier.width(12.dp))
                                        Text("SERVICIO EJECUTADO", fontWeight = FontWeight.Bold, color = Color(0xFF1D8348))
                                    }
                                }
                            }
                            else -> {
                                Text("Estado: ${booking.status}", color = Color.Gray)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
