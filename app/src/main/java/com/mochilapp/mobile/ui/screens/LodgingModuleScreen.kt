package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
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
import com.mochilapp.mobile.data.BookingFirestore
import com.mochilapp.mobile.data.RoomFirestore
import com.mochilapp.mobile.ui.viewmodels.CompanyViewModel
import java.text.SimpleDateFormat
import java.util.*

// Colores alineados al RoomGrid del panel web (emerald/rose/amber/slate)
private val RoomOccupied = Color(0xFFE11D48)   // rose-600
private val RoomPending = Color(0xFFD68910)    // amber oscuro (texto)
private val RoomAvailable = Color(0xFF059669)  // emerald-600
private val RoomOccupiedBg = Color(0xFFFFE4E6) // rose-100
private val RoomPendingBg = Color(0xFFFDEBD0)  // amber claro
private val RoomAvailableBg = Color(0xFFD1FAE5) // emerald-100
private val RoomServiceGray = Color(0xFF64748B) // slate-500 (limpieza/mantenimiento)
private val RoomServiceGrayBg = Color(0xFFE2E8F0) // slate-200
private val LodgingAccent = Color(0xFF059669)

private enum class RoomState { OCCUPIED, PENDING, AVAILABLE, CLEANING, MAINTENANCE }

private data class RoomUi(
    val name: String,
    val type: String,
    val state: RoomState
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LodgingModuleScreen(
    viewModel: CompanyViewModel,
    onBack: () -> Unit
) {
    val services by viewModel.myServices.collectAsState()
    val bookings by viewModel.myBookings.collectAsState()

    val lodgingServices = remember(services) {
        services.filter { (it.type == "HOTEL" || it.type == "HOSTEL") && it.isVisible }
    }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var selectedServiceId by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(sdf.format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(lodgingServices) {
        if (lodgingServices.isNotEmpty() && lodgingServices.none { it.id == selectedServiceId }) {
            selectedServiceId = lodgingServices.first().id
        }
    }
    val selectedService = lodgingServices.find { it.id == selectedServiceId }
    val isHotel = selectedService?.type != "HOSTEL"
    val unitLabel = if (isHotel) "habs" else "camas"

    // Igual que el LodgingModule.tsx del web: las habitaciones configuradas definen
    // el total de unidades; sin configurar, fallback de 8 unidades estándar
    val totalUnits = selectedService?.rooms?.size?.takeIf { it > 0 } ?: 8

    val activeBookings = remember(bookings, selectedServiceId, selectedDate) {
        bookings.filter {
            it.serviceId == selectedServiceId && it.date == selectedDate && it.status != "CANCELLED"
        }
    }

    // A diferencia del web, la app maneja CHECKED_IN: el huésped ya está adentro,
    // así que cuenta como unidad ocupada igual que una reserva pagada
    val paidUnits = activeBookings.filter { it.status == "PAID" || it.status == "CHECKED_IN" }.sumOf { it.slots }
    val pendingUnits = activeBookings.filter { it.status == "PENDING" }.sumOf { it.slots }
    val occupiedUnits = paidUnits + pendingUnits
    val availableUnits = totalUnits - occupiedUnits
    val dayRevenue = activeBookings.sumOf { it.totalPrice }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gestión de Hospedaje", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "Aforo diario de ${if (isHotel) "habitaciones" else "camas"} y manifiesto de huéspedes",
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
        if (lodgingServices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Bed, contentDescription = null, modifier = Modifier.size(64.dp), tint = LodgingAccent)
                    Spacer(Modifier.height(16.dp))
                    Text("Sin servicios de hospedaje", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Este módulo requiere al menos un servicio registrado con la categoría Hotel u Hostal.",
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
            // Selector de establecimiento
            if (lodgingServices.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(lodgingServices) { service ->
                            FilterChip(
                                selected = selectedServiceId == service.id,
                                onClick = { selectedServiceId = service.id },
                                label = { Text(service.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        if (service.type == "HOSTEL") Icons.Default.Bed else Icons.Default.Hotel,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Agenda operativa: fecha a consultar
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = LodgingAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Agenda Operativa", fontWeight = FontWeight.Black, fontSize = 13.sp)
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

                        // Próximas fechas con reservas: un toque y saltas al día
                        val today = sdf.format(Date())
                        val upcomingDates = bookings
                            .filter { it.serviceId == selectedServiceId && it.status != "CANCELLED" && it.date >= today }
                            .groupBy { it.date }
                            .toSortedMap()
                            .entries.take(14)
                        if (upcomingDates.isNotEmpty()) {
                            Column {
                                Text(
                                    "PRÓXIMAS FECHAS CON RESERVAS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Gray,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(upcomingDates.toList()) { (date, dateBookings) ->
                                        val units = dateBookings.sumOf { it.slots }
                                        FilterChip(
                                            selected = selectedDate == date,
                                            onClick = { selectedDate = date },
                                            label = {
                                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                    Text(
                                                        if (date == today) "Hoy" else date.substring(5), // MM-DD
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                    Text("$units $unitLabel", fontSize = 10.sp)
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(14.dp))
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // KPIs de aforo del día
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LodgingKpiCard(Modifier.weight(1f), "TOTAL", totalUnits.toString(), MaterialTheme.colorScheme.onSurface)
                    LodgingKpiCard(Modifier.weight(1f), "OCUPADAS", paidUnits.toString(), RoomOccupied)
                    LodgingKpiCard(Modifier.weight(1f), "RESERVADAS", pendingUnits.toString(), RoomPending)
                    LodgingKpiCard(
                        Modifier.weight(1f), "LIBRES", availableUnits.toString(),
                        if (availableUnits < 0) RoomOccupied else RoomAvailable
                    )
                }
            }

            // Ingresos estimados del día
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = LodgingAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("INGRESOS DEL DÍA", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                            Text("${formatMxn(dayRevenue)} MXN", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }
            }

            // Alerta de sobreventa
            if (availableUnits < 0) {
                item {
                    Surface(
                        color = Color(0xFFFADBD8),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, Color(0xFFF1948A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC0392B))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("¡SOBREVENTA CRÍTICA!", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF922B21))
                                Text(
                                    "Las unidades asignadas ($occupiedUnits) superan la capacidad ($totalUnits) por ${-availableUnits}. Reubica huéspedes o ajusta las habitaciones.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC0392B)
                                )
                            }
                        }
                    }
                }
            }

            // Distribución de planta (grid de habitaciones/camas)
            item {
                RoomGridCard(
                    rooms = selectedService?.rooms.orEmpty(),
                    isHotel = isHotel,
                    paidUnits = paidUnits,
                    pendingUnits = pendingUnits
                )
            }

            // Manifiesto de huéspedes
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = LodgingAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Manifiesto de Huéspedes (${activeBookings.size})", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                    if (selectedService != null && selectedService.price > 0) {
                        Text("${formatMxn(selectedService.price)}/noche", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
            }

            if (activeBookings.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No hay check-ins programados para esta fecha.",
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(activeBookings) { booking ->
                    GuestManifestRow(booking, unitLabel)
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
private fun LodgingKpiCard(modifier: Modifier, label: String, value: String, valueColor: Color) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 20.sp, color = valueColor)
        }
    }
}

// Misma asignación secuencial que el RoomGrid del web: las reservas del día se
// reparten sobre las unidades respetando limpieza/mantenimiento configurados
@Composable
private fun RoomGridCard(
    rooms: List<RoomFirestore>,
    isHotel: Boolean,
    paidUnits: Int,
    pendingUnits: Int
) {
    val baseRooms = if (rooms.isNotEmpty()) {
        rooms.mapIndexed { index, room ->
            RoomUi(
                name = room.name.ifEmpty { if (isHotel) "Habitación ${index + 1}" else "Cama ${index + 1}" },
                type = room.type.ifEmpty { if (isHotel) "Habitación" else "Cama" },
                state = when (room.status.uppercase()) {
                    "CLEANING" -> RoomState.CLEANING
                    "MAINTENANCE" -> RoomState.MAINTENANCE
                    else -> RoomState.AVAILABLE
                }
            )
        }
    } else {
        (1..8).map { i ->
            RoomUi(
                name = if (isHotel) "Hab 10$i" else "Cama $i",
                type = if (isHotel) "Habitación Standard" else "Cama Litera",
                state = RoomState.AVAILABLE
            )
        }
    }

    var paidRemaining = paidUnits
    var pendingRemaining = pendingUnits
    val finalRooms = baseRooms.map { room ->
        if (room.state == RoomState.CLEANING || room.state == RoomState.MAINTENANCE) return@map room
        when {
            paidRemaining > 0 -> { paidRemaining--; room.copy(state = RoomState.OCCUPIED) }
            pendingRemaining > 0 -> { pendingRemaining--; room.copy(state = RoomState.PENDING) }
            else -> room
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridView, contentDescription = null, tint = LodgingAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Distribución de Planta", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }

            finalRooms.chunked(2).forEach { rowRooms ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowRooms.forEach { room ->
                        RoomCard(room = room, isHotel = isHotel, modifier = Modifier.weight(1f))
                    }
                    if (rowRooms.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            // Leyenda de estados
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                LegendDot(RoomAvailable, "Libre (${finalRooms.count { it.state == RoomState.AVAILABLE }})")
                LegendDot(RoomPending, "Reservada (${finalRooms.count { it.state == RoomState.PENDING }})")
                LegendDot(RoomOccupied, "Ocupada (${finalRooms.count { it.state == RoomState.OCCUPIED }})")
            }
            val serviceCount = finalRooms.count { it.state == RoomState.CLEANING || it.state == RoomState.MAINTENANCE }
            if (serviceCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    LegendDot(RoomServiceGray, "Limpieza (${finalRooms.count { it.state == RoomState.CLEANING }})")
                    LegendDot(RoomServiceGray, "Mantenimiento (${finalRooms.count { it.state == RoomState.MAINTENANCE }})")
                }
            }

            Text(
                "La asignación de huéspedes a unidades es secuencial según las reservas activas del día.",
                fontSize = 10.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RoomCard(room: RoomUi, isHotel: Boolean, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (room.state) {
        RoomState.OCCUPIED -> Triple(RoomOccupiedBg, RoomOccupied, "Ocupada")
        RoomState.PENDING -> Triple(RoomPendingBg, RoomPending, "Reservada")
        RoomState.AVAILABLE -> Triple(RoomAvailableBg, RoomAvailable, "Libre")
        RoomState.CLEANING -> Triple(RoomServiceGrayBg, RoomServiceGray, "Limpieza")
        RoomState.MAINTENANCE -> Triple(RoomServiceGrayBg, RoomServiceGray, "Mantenimiento")
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
                    Icon(
                        if (isHotel) Icons.Default.Hotel else Icons.Default.Bed,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.padding(6.dp).size(16.dp)
                    )
                }
                Text(label.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Black, color = fg, letterSpacing = 0.5.sp)
            }
            Column {
                Text(room.name, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
                Text(room.type, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
    }
}

@Composable
private fun GuestManifestRow(booking: BookingFirestore, unitLabel: String) {
    val (badgeBg, badgeFg, badgeText) = when (booking.status) {
        "PAID" -> Triple(RoomOccupiedBg, RoomOccupied, "PAGADA")
        "CHECKED_IN" -> Triple(Color(0xFFD6EAF8), Color(0xFF1A5276), "EN CASA")
        "COMPLETED" -> Triple(Color(0xFFE8E8E8), Color(0xFF555555), "COMPLETADA")
        else -> Triple(RoomPendingBg, RoomPending, "PENDIENTE")
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    booking.travelerName.ifEmpty { booking.travelerEmail },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Text("${booking.slots} $unitLabel", fontSize = 11.sp, color = Color.Gray)
            }
            Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                Text(
                    badgeText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = badgeFg
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(formatMxn(booking.totalPrice), fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}
