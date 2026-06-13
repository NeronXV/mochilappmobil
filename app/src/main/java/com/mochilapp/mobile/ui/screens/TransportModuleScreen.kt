package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.ui.viewmodels.CompanyViewModel
import java.text.SimpleDateFormat
import java.util.*

private val TransportAccent = Color(0xFF2563EB) // blue-600
private val SeatPaidBlue = Color(0xFF2563EB)
private val SeatPendingAmber = Color(0xFFFBBF24)
private val SeatFreeGreen = Color(0xFF10B981)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportModuleScreen(
    viewModel: CompanyViewModel,
    onBack: () -> Unit
) {
    val services by viewModel.myServices.collectAsState()
    val bookings by viewModel.myBookings.collectAsState()

    val transportServices = remember(services) {
        services.filter { it.type == "TRANSPORT" && it.isVisible }
    }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var selectedServiceId by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(sdf.format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(transportServices) {
        if (transportServices.isNotEmpty() && transportServices.none { it.id == selectedServiceId }) {
            selectedServiceId = transportServices.first().id
        }
    }
    val selectedService = transportServices.find { it.id == selectedServiceId }

    // Fallbacks del web: 4 corridas estándar y unidad de 14 asientos
    val departureTimes = selectedService?.departureTimes?.takeIf { it.isNotEmpty() }
        ?: listOf("08:00", "12:00", "16:00", "20:00")
    val capacity = (selectedService?.capacity ?: 0).let { if (it <= 0) 14 else it }
    val vehicleName = selectedService?.vehicleName?.ifEmpty { "Unidad por asignar" } ?: "Unidad por asignar"
    val driverName = selectedService?.driverName?.ifEmpty { "Chofer por asignar" } ?: "Chofer por asignar"
    val routeName = selectedService?.routeName?.ifEmpty { selectedService.name } ?: ""
    val origin = selectedService?.origin.orEmpty()
    val destination = selectedService?.destination.orEmpty()

    val dayBookings = remember(bookings, selectedServiceId, selectedDate) {
        bookings.filter {
            it.serviceId == selectedServiceId && it.date == selectedDate && it.status != "CANCELLED"
        }
    }
    val departures = groupBookingsByDeparture(departureTimes, capacity, dayBookings)
    val expectedPassengers = dayBookings.sumOf { it.slots }
    val dayRevenue = dayBookings.sumOf { it.totalPrice }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Control de Rutas", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "Corridas, asientos y pasajeros por horario",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (transportServices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DirectionsBus, contentDescription = null, modifier = Modifier.size(64.dp), tint = TransportAccent)
                    Spacer(Modifier.height(16.dp))
                    Text("Sin rutas registradas", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Este módulo requiere al menos un servicio registrado con la categoría Transporte (TRANSPORT).",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Selector de ruta
            if (transportServices.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(transportServices) { service ->
                            FilterChip(
                                selected = selectedServiceId == service.id,
                                onClick = { selectedServiceId = service.id },
                                label = { Text(service.routeName.ifEmpty { service.name }, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Default.DirectionsBus, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Ficha de la ruta
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Route, contentDescription = null, tint = TransportAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(routeName, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                if (origin.isNotEmpty() && destination.isNotEmpty()) {
                                    Text("$origin ➔ $destination", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(vehicleName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                }
                            }
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(driverName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(selectedDate, fontWeight = FontWeight.Bold)
                        }

                        // Próximas fechas con reservas
                        val today = sdf.format(Date())
                        val upcomingDates = bookings
                            .filter { it.serviceId == selectedServiceId && it.status != "CANCELLED" && it.date >= today }
                            .groupBy { it.date }
                            .toSortedMap()
                            .entries.take(14)
                        if (upcomingDates.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(upcomingDates.toList()) { (date, dateBookings) ->
                                    FilterChip(
                                        selected = selectedDate == date,
                                        onClick = { selectedDate = date },
                                        label = {
                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                Text(if (date == today) "Hoy" else date.substring(5), fontSize = 12.sp, fontWeight = FontWeight.Black)
                                                Text("${dateBookings.sumOf { it.slots }} pas.", fontSize = 10.sp)
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // KPIs del día
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransportKpiCard(Modifier.weight(1f), "CORRIDAS", departures.size.toString())
                    TransportKpiCard(Modifier.weight(1f), "PASAJEROS", expectedPassengers.toString())
                    TransportKpiCard(Modifier.weight(1.3f), "INGRESOS", formatMxn(dayRevenue))
                }
            }

            // Tablero de corridas
            items(departures) { dep ->
                var showSeatMap by remember(dep.time, selectedServiceId, selectedDate) { mutableStateOf(false) }
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = if (dep.status == DepartureStatus.OVERBOOKED) BorderStroke(2.dp, DepartureStatus.OVERBOOKED.fg) else null
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (dep.isGeneral) Icons.Default.HelpOutline else Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = TransportAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (dep.isGeneral) dep.time else "Corrida de las ${dep.time}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                            DepartureStatusBadge(dep.status)
                        }

                        DepartureOccupancyBar(dep, capacity, "asientos")
                        DepartureMetricsRow(dep, "asientos")

                        // Mapa de asientos desplegable
                        TextButton(onClick = { showSeatMap = !showSeatMap }) {
                            Icon(
                                if (showSeatMap) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (showSeatMap) "Ocultar distribución de la unidad" else "Ver distribución de asientos",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        if (showSeatMap) {
                            VehicleSeatMapNative(
                                capacity = capacity,
                                paidSlots = dep.paidSlots,
                                pendingSlots = dep.pendingSlots
                            )
                        }

                        if (dep.bookings.isNotEmpty()) {
                            Column {
                                Text(
                                    "MANIFIESTO DE PASAJEROS (${dep.bookings.size})",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Gray,
                                    letterSpacing = 1.sp
                                )
                                dep.bookings.forEach { booking ->
                                    DeparturePassengerRow(booking, "asientos")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { sdf.parse(selectedDate)?.time }.getOrNull()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // DatePicker entrega millis en UTC: formatear en UTC para no desfasar el día
                        val utcSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        selectedDate = utcSdf.format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TransportKpiCard(modifier: Modifier, label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
        }
    }
}

// Cabina tipo van/shuttle como el VehicleSeatMap del web: chofer + copiloto (asiento 1)
// al frente y el resto en filas de 3; los asientos se llenan secuencialmente
@Composable
private fun VehicleSeatMapNative(capacity: Int, paidSlots: Int, pendingSlots: Int) {
    fun seatColor(index: Int): Color = when {
        index < paidSlots -> SeatPaidBlue
        index < paidSlots + pendingSlots -> SeatPendingAmber
        else -> SeatFreeGreen
    }

    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cabina: chofer y copiloto (asiento 1)
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                border = BorderStroke(2.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE2E8F0),
                            border = BorderStroke(3.dp, Color(0xFF334155)),
                            modifier = Modifier.size(32.dp)
                        ) {}
                        Spacer(Modifier.height(4.dp))
                        Text("CHOFER", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    }
                    Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "UNIDAD",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = SeatFreeGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                    if (capacity >= 1) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SeatBox(number = 1, color = seatColor(0))
                            Spacer(Modifier.height(4.dp))
                            Text("COPILOTO", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                        }
                    }
                }
            }

            // Compartimento de pasajeros: asientos 2..capacity en filas de 3
            (2..capacity).chunked(3).forEach { rowSeats ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowSeats.forEach { seatNumber ->
                        SeatBox(number = seatNumber, color = seatColor(seatNumber - 1))
                    }
                }
            }

            // Leyenda
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SeatLegend(SeatFreeGreen, "Libre")
                SeatLegend(SeatPendingAmber, "Apartado")
                SeatLegend(SeatPaidBlue, "Pagado")
            }
        }
    }
}

@Composable
private fun SeatBox(number: Int, color: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(color, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            number.toString(),
            color = if (color == SeatPendingAmber) Color(0xFF1E293B) else Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SeatLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
    }
}
