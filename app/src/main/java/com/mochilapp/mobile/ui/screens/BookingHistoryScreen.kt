package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.data.BookingFirestore
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.BookingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingHistoryScreen(
    viewModel: BookingViewModel,
    onBookingClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val bookings by viewModel.myBookings.collectAsState()
    val activeNotices by viewModel.activeNotices.collectAsState()
    val today = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(t("bookings"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (bookings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CalendarToday, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp), 
                        tint = Color.LightGray
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(t("no_bookings"), color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF8F9FA)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(bookings) { booking ->
                    // Aviso del negocio que afecta esta reserva (por fecha o general)
                    val notice = activeNotices.firstOrNull {
                        it.serviceId == booking.serviceId &&
                            booking.status != "CANCELLED" &&
                            booking.date >= today &&
                            (it.date.isEmpty() || it.date == booking.date)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        notice?.let { NoticeBanner(it) }
                        BookingCard(booking, onClick = { onBookingClick(booking.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun BookingCard(booking: BookingFirestore, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reserva ${booking.confirmationCode.ifEmpty { "#" + booking.id.takeLast(6).uppercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = booking.serviceName.ifEmpty { "Servicio Mochilapp" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (booking.departureTime.isNotEmpty()) {
                    Text(
                        text = "Hora: ${booking.departureTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (booking.discountAmount > 0) {
                    Text(
                        text = "Ahorraste: ${formatMxn(booking.discountAmount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2ECC71),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    if (booking.checkOutDate.isNotEmpty()) {
                        Text(text = "${booking.date} → ${booking.checkOutDate}", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        Text(text = booking.date, fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        Text(text = "${booking.slots} personas", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMxn(booking.totalPrice),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                StatusBadge(booking.status)
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "PAID" -> Color(0xFF2E7D32)
        "PENDING" -> Color(0xFFF57C00)
        else -> Color.Gray
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (status == "PAID") Icons.Default.CheckCircle else Icons.Default.Pending,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (status == "PAID") "Pagado" else "Pendiente",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
