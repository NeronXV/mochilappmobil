package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.BookingViewModel
import com.mochilapp.mobile.ui.viewmodels.MarketplaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingFlowScreen(
    serviceId: String,
    travelerName: String,
    marketplaceViewModel: MarketplaceViewModel,
    bookingViewModel: BookingViewModel,
    onPaymentNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    var service by remember { mutableStateOf<ServiceFirestore?>(null) }
    var slots by remember { mutableIntStateOf(1) }
    
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    val selectedDateText = datePickerState.selectedDateMillis?.let { dateFormatter.format(java.util.Date(it)) } ?: "Seleccionar fecha"

    var selectedTime by remember { mutableStateOf<String?>(null) }
    val bookingsByDate by bookingViewModel.currentBookings.collectAsState()

    val bookingResult by bookingViewModel.bookingResult.collectAsState()
    val activePromo by marketplaceViewModel.activePromo.collectAsState()

    LaunchedEffect(serviceId) {
        service = marketplaceViewModel.getServiceById(serviceId)
    }

    LaunchedEffect(selectedDateText) {
        if (datePickerState.selectedDateMillis != null) {
            bookingViewModel.updateAvailabilityQuery(serviceId, selectedDateText)
        }
    }

    LaunchedEffect(bookingResult) {
        bookingResult?.let { id ->
            onPaymentNavigate(id)
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("OK")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = t("confirm_details"), 
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Slots Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(t("people"), fontWeight = FontWeight.Bold)
                            Text("Indica cuántas personas irán", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (slots > 1) slots-- }) {
                                Icon(Icons.Default.RemoveCircle, contentDescription = null, tint = Color(0xFF007BFF))
                            }
                            Text(slots.toString(), fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { slots++ }) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFF007BFF))
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F3F5))

                    // Date Selection
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                    ) {
                        Text(t("date"), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF007BFF), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(selectedDateText, color = if (datePickerState.selectedDateMillis == null) Color.Gray else Color.Black)
                        }
                    }

                    // Departure Time Selection (Dynamic)
                    service?.let { s ->
                        if (s.departureTimes.isNotEmpty()) {
                            HorizontalDivider(color = Color(0xFFF1F3F5))
                            Column {
                                Text("Horario de salida", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    s.departureTimes.forEach { time ->
                                        val isSelected = selectedTime == time
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedTime = time },
                                            label = { Text(time) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF007BFF),
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Availability Info
                    service?.let { s ->
                        if (s.capacity > 0 && datePickerState.selectedDateMillis != null) {
                            val usedSlots = bookingsByDate
                                .filter { if (s.departureTimes.isNotEmpty()) it.departureTime == selectedTime else true }
                                .sumOf { it.slots }
                            val available = s.capacity - usedSlots
                            
                            HorizontalDivider(color = Color(0xFFF1F3F5))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = if (available >= slots) Color(0xFF2ECC71) else Color.Red, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (available >= slots) "Cupos disponibles: $available" else "Sin cupos suficientes ($available disponibles)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (available >= slots) Color.Gray else Color.Red
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val originalTotal = (service?.price ?: 0.0) * slots
            val discountPercent = if (activePromo?.serviceId == serviceId) activePromo?.discountPercent ?: 0 else 0
            val discountAmount = originalTotal * (discountPercent.toDouble() / 100.0)
            val totalPrice = originalTotal - discountAmount
            
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (discountAmount > 0) {
                            Text(
                                text = "$$originalTotal",
                                style = MaterialTheme.typography.labelSmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                                color = Color.Gray
                            )
                        }
                        Text(t("total_price"), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Text(
                            "$$totalPrice", 
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = if (discountAmount > 0) Color(0xFF2ECC71) else Color(0xFF007BFF)
                            )
                        )
                    }
                    Button(
                        onClick = {
                            val currentService = service
                            if (currentService != null && datePickerState.selectedDateMillis != null) {
                                bookingViewModel.createBooking(
                                    serviceId = serviceId,
                                    serviceName = currentService.name,
                                    travelerName = travelerName,
                                    date = selectedDateText,
                                    slots = slots,
                                    totalPrice = totalPrice,
                                    ownerEmail = currentService.ownerEmail,
                                    departureTime = selectedTime ?: "",
                                    promoId = if (discountAmount > 0) activePromo?.id ?: "" else "",
                                    promoCode = if (discountAmount > 0) activePromo?.promoCode ?: "" else "",
                                    discountPercent = discountPercent,
                                    discountAmount = discountAmount,
                                    originalTotal = originalTotal
                                )
                            }
                        },
                        enabled = datePickerState.selectedDateMillis != null && 
                                 service != null && 
                                 (service?.departureTimes?.isEmpty() == true || selectedTime != null),
                        modifier = Modifier.height(56.dp).padding(start = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BFF))
                    ) {
                        Text(t("payment_continue"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
