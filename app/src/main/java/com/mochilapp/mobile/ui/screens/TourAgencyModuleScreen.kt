package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

private val AgencyAccent = Color(0xFF7C3AED) // violet-600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourAgencyModuleScreen(
    viewModel: CompanyViewModel,
    onBack: () -> Unit
) {
    val services by viewModel.myServices.collectAsState()
    val bookings by viewModel.myBookings.collectAsState()

    val agencyServices = remember(services) {
        services.filter { it.type == "TOUR_AGENCY" && it.isVisible }
    }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var selectedServiceId by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(sdf.format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(agencyServices) {
        if (agencyServices.isNotEmpty() && agencyServices.none { it.id == selectedServiceId }) {
            selectedServiceId = agencyServices.first().id
        }
    }
    val selectedService = agencyServices.find { it.id == selectedServiceId }

    // Fallback del web: sin horarios configurados se asumen 3 salidas estándar
    val departureTimes = selectedService?.departureTimes?.takeIf { it.isNotEmpty() }
        ?: listOf("09:00", "13:00", "17:00")
    val capacity = (selectedService?.capacity ?: 0).let { if (it <= 0) 12 else it }
    val guideName = selectedService?.guideName?.ifEmpty { "Guía por asignar" } ?: "Guía por asignar"

    val dayBookings = remember(bookings, selectedServiceId, selectedDate) {
        bookings.filter {
            it.serviceId == selectedServiceId && it.date == selectedDate && it.status != "CANCELLED"
        }
    }
    val departures = groupBookingsByDeparture(departureTimes, capacity, dayBookings)
    val expectedClients = dayBookings.sumOf { it.slots }
    val dayRevenue = dayBookings.sumOf { it.totalPrice }
    val almostFullCount = departures.count {
        it.status == DepartureStatus.ALMOST_FULL || it.status == DepartureStatus.FULL || it.status == DepartureStatus.OVERBOOKED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Control de Salidas", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "Tablero de salidas y ocupación por horario",
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
        if (agencyServices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(64.dp), tint = AgencyAccent)
                    Spacer(Modifier.height(16.dp))
                    Text("Sin tours registrados", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Este módulo requiere al menos un servicio registrado con la categoría Agencia de tours (TOUR_AGENCY).",
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
            // Selector de tour
            if (agencyServices.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(agencyServices) { service ->
                            FilterChip(
                                selected = selectedServiceId == service.id,
                                onClick = { selectedServiceId = service.id },
                                label = { Text(service.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Fecha de operación
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = AgencyAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Día de Operación", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(guideName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
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
                                                Text("${dateBookings.sumOf { it.slots }} pers.", fontSize = 10.sp)
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
                    AgencyKpiCard(Modifier.weight(1f), "SALIDAS", departures.size.toString())
                    AgencyKpiCard(Modifier.weight(1f), "CLIENTES", expectedClients.toString())
                    AgencyKpiCard(Modifier.weight(1f), "POR LLENARSE", almostFullCount.toString())
                    AgencyKpiCard(Modifier.weight(1.3f), "INGRESOS", formatMxn(dayRevenue))
                }
            }

            // Tablero de salidas
            items(departures) { dep ->
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
                                    tint = AgencyAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (dep.isGeneral) dep.time else "Salida de las ${dep.time}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                            DepartureStatusBadge(dep.status)
                        }

                        DepartureOccupancyBar(dep, capacity, "pasajeros")
                        DepartureMetricsRow(dep, "lugares")

                        if (dep.bookings.isNotEmpty()) {
                            Column {
                                Text(
                                    "CLIENTES REGISTRADOS (${dep.bookings.size})",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Gray,
                                    letterSpacing = 1.sp
                                )
                                dep.bookings.forEach { booking ->
                                    DeparturePassengerRow(booking, "lugares")
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
private fun AgencyKpiCard(modifier: Modifier, label: String, value: String) {
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
