package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.ui.viewmodels.BookingViewModel
import kotlinx.coroutines.delay

@Composable
fun PaymentScreen(
    bookingId: String,
    viewModel: BookingViewModel,
    onPaymentSuccess: () -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    val myBookings by viewModel.myBookings.collectAsState()
    val currentBooking = myBookings.find { it.id == bookingId }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (!isSuccess) Arrangement.Center else Arrangement.Top
    ) {
        if (!isSuccess) {
            // ... (Lock icon and Stripe text remains same)
            Icon(
                Icons.Default.Lock, 
                contentDescription = null, 
                tint = Color(0xFF6772E5),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Pago Seguro con Stripe", 
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = Color(0xFF6772E5)
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            // Booking Summary Card before paying
            currentBooking?.let { b ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Resumen de tu aventura", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Text(b.serviceName.ifEmpty { "Experiencia Mochilapp" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${b.date} • ${b.slots} personas", fontSize = 14.sp)
                        if (b.departureTime.isNotEmpty()) Text("Hora: ${b.departureTime}", fontSize = 14.sp)
                        
                        if (b.discountAmount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = Color(0xFFD4EFDF), shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = "PROMO ${b.promoCode}", 
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D8348)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "-$${b.discountAmount} USD",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2ECC71)
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                // ... (Card fields remain same)
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = "**** **** **** 4242", 
                        onValueChange = {}, 
                        label = { Text("Número de Tarjeta") }, 
                        modifier = Modifier.fillMaxWidth(), 
                        enabled = false,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = "12/26", 
                            onValueChange = {}, 
                            label = { Text("Exp") }, 
                            modifier = Modifier.weight(1f), 
                            enabled = false,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = "123", 
                            onValueChange = {}, 
                            label = { Text("CVC") }, 
                            modifier = Modifier.weight(1f), 
                            enabled = false,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = {
                    isProcessing = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6772E5)),
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Pagar $${currentBooking?.totalPrice ?: ""}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        } else {
            // Digital Ticket Design
            Spacer(modifier = Modifier.height(40.dp))
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = Color(0xFFD4EFDF)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "¡Reserva Confirmada!", 
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
            )
            Text(
                text = "Tu aventura está lista para comenzar.", 
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Digital Ticket / Voucher
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = currentBooking?.serviceName ?: "Experiencia Mochilapp",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TicketInfoItem("FECHA", currentBooking?.date ?: "--", Modifier.weight(1f))
                        TicketInfoItem("HORA", currentBooking?.departureTime?.ifEmpty { "Por confirmar" } ?: "N/A", Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TicketInfoItem("PERSONAS", "${currentBooking?.slots} pers.", Modifier.weight(1f))
                        TicketInfoItem("TOTAL", "$${currentBooking?.totalPrice} USD", Modifier.weight(1f))
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color(0xFFF1F3F5))
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("CÓDIGO DE CONFIRMACIÓN", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            text = currentBooking?.confirmationCode ?: "MOCHI-${bookingId.takeLast(6).uppercase()}",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp,
                                color = Color(0xFF007BFF)
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(100.dp), tint = Color.DarkGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = onPaymentSuccess,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BFF))
            ) {
                Text("Ir a mis Reservas", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onPaymentSuccess, modifier = Modifier.padding(top = 8.dp)) {
                Text("Volver al Inicio", color = Color.Gray)
            }
        }
    }

    if (isProcessing) {
        LaunchedEffect(Unit) {
            delay(2500)
            viewModel.confirmPayment(bookingId)
            isProcessing = false
            isSuccess = true
        }
    }
}

@Composable
fun TicketInfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
    }
}
