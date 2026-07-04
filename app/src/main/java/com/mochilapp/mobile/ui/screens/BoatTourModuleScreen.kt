package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.data.BookingFirestore
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.ui.viewmodels.CompanyViewModel
import java.text.SimpleDateFormat
import java.util.*

// Colores del módulo (alineados al BoatSeatMap del panel web)
private val SeatPaid = Color(0xFFE11D48)      // rose-600
private val SeatPending = Color(0xFFFBBF24)   // amber-400
private val SeatAvailable = Color(0xFF10B981) // emerald-500
private val DeckDark = Color(0xFF0F172A)      // slate-900
private val HullDark = Color(0xFF020617)      // slate-950
private val HullBorder = Color(0xFF334155)    // slate-700

private enum class SeatStatus { PAID, PENDING, AVAILABLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoatTourModuleScreen(
    viewModel: CompanyViewModel,
    onBack: () -> Unit
) {
    val services by viewModel.myServices.collectAsState()
    val bookings by viewModel.myBookings.collectAsState()

    val boatServices = remember(services) { services.filter { it.type == "BOAT_TOUR" && it.isVisible } }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var selectedServiceId by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(sdf.format(Date())) }
    var selectedTime by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    // Inicializar selección al cargar servicios
    LaunchedEffect(boatServices) {
        if (boatServices.isNotEmpty() && boatServices.none { it.id == selectedServiceId }) {
            selectedServiceId = boatServices.first().id
        }
    }
    val selectedService = boatServices.find { it.id == selectedServiceId }

    LaunchedEffect(selectedService?.id) {
        selectedTime = selectedService?.departureTimes?.firstOrNull() ?: ""
    }

    // Aforo real del servicio; 0 = sin configurar (se pide configurarlo en
    // pantalla en vez de asumir 12 lugares en silencio)
    val capacity = selectedService?.capacity ?: 0
    var showCapacityDialog by remember { mutableStateOf(false) }

    val dayBookings = remember(bookings, selectedServiceId, selectedDate) {
        bookings.filter {
            it.serviceId == selectedServiceId && it.date == selectedDate && it.status != "CANCELLED"
        }
    }
    val hasTimes = !selectedService?.departureTimes.isNullOrEmpty()

    // Reservas legado sin horario: antes contaban en TODAS las salidas (cada
    // horario las sumaba a su ocupación). Ahora van aparte, visibles en el
    // manifiesto pero sin descontar cupo de una salida concreta. Crear nuevas
    // reservas sin horario ya no es posible: BookingFlow exige elegir salida.
    val noTimeBookings = remember(dayBookings, hasTimes) {
        if (hasTimes) dayBookings.filter { it.departureTime.isEmpty() } else emptyList()
    }
    val activeBookings = remember(dayBookings, selectedTime, hasTimes) {
        when {
            hasTimes && selectedTime.isNotEmpty() -> dayBookings.filter { it.departureTime == selectedTime }
            hasTimes -> dayBookings.filter { it.departureTime.isNotEmpty() }
            else -> dayBookings
        }
    }

    val paidSlots = activeBookings.filter { it.status == "PAID" }.sumOf { it.slots }
    val pendingSlots = activeBookings.filter { it.status == "PENDING" }.sumOf { it.slots }
    val occupiedSlots = paidSlots + pendingSlots
    val availableSlots = capacity - occupiedSlots

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Control de Embarcaciones", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "Salidas, ocupación y cupos en tiempo real",
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
        if (boatServices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Anchor, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF0891B2))
                    Spacer(Modifier.height(16.dp))
                    Text("Sin servicios marítimos", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Este módulo requiere al menos un servicio registrado con la categoría Tour en Lancha (BOAT_TOUR).",
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
            // Selector de embarcación / tour
            item {
                BoatServiceSelector(
                    services = boatServices,
                    selectedServiceId = selectedServiceId,
                    onSelect = { selectedServiceId = it }
                )
            }

            // Programación de salida: fecha y horario
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF0891B2), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Programación de Salida", fontWeight = FontWeight.Black, fontSize = 13.sp)
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

                        // Próximas fechas con reservas de esta embarcación: un toque y saltas al día
                        val today = sdf.format(Date())
                        val upcomingDates = bookings
                            .filter { it.serviceId == selectedServiceId && it.status != "CANCELLED" && it.date >= today }
                            .groupBy { it.date }
                            .toSortedMap()
                            .entries.take(14)
                        if (upcomingDates.isNotEmpty()) {
                            Column {
                                Text(
                                    "PRÓXIMAS SALIDAS CON RESERVAS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Gray,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(upcomingDates.toList()) { (date, dateBookings) ->
                                        val passengers = dateBookings.sumOf { it.slots }
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
                                                    Text("$passengers pers.", fontSize = 10.sp)
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

                        val times = selectedService?.departureTimes ?: emptyList()
                        if (times.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(times) { time ->
                                    FilterChip(
                                        selected = selectedTime == time,
                                        onClick = { selectedTime = time },
                                        label = { Text(time, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        } else {
                            Text("Salida única por fecha general", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // KPIs de ocupación
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoatKpiCard(
                        Modifier.weight(1f), "CAPACIDAD",
                        if (capacity > 0) capacity.toString() else "--",
                        MaterialTheme.colorScheme.onSurface,
                        onEdit = { showCapacityDialog = true }
                    )
                    BoatKpiCard(Modifier.weight(1f), "PAGADOS", paidSlots.toString(), SeatPaid)
                    BoatKpiCard(Modifier.weight(1f), "APARTADOS", pendingSlots.toString(), Color(0xFFD68910))
                    BoatKpiCard(
                        Modifier.weight(1f), "LIBRES",
                        if (capacity > 0) availableSlots.toString() else "--",
                        if (capacity > 0 && availableSlots < 0) SeatPaid else Color(0xFF059669)
                    )
                }
            }

            // Aforo sin configurar: pedirlo explícitamente en vez de inventar lugares
            if (capacity <= 0) {
                item {
                    Surface(
                        color = Color(0xFFFDEBD0),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFF5CBA7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AirlineSeatReclineNormal, contentDescription = null, tint = Color(0xFFD68910))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Configura el aforo de tu embarcación", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF9C640C))
                                    Text(
                                        "Define cuántos lugares tiene para controlar cupos y evitar sobreventa.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFB9770E)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showCapacityDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD68910)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Definir lugares", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Alerta de sobreventa
            if (capacity > 0 && availableSlots < 0) {
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
                                Text("¡ALERTA DE SOBREVENTA!", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF922B21))
                                Text(
                                    "Las reservas activas ($occupiedSlots) superan la capacidad ($capacity) por ${-availableSlots} lugar(es). Reubica pasajeros o ajusta el aforo.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC0392B)
                                )
                            }
                        }
                    }
                }
            }

            // Mapa de asientos de la embarcación (solo con aforo configurado)
            if (capacity > 0) {
                item {
                    BoatSeatMap(
                        capacity = capacity,
                        paidSlots = paidSlots,
                        pendingSlots = pendingSlots,
                        availableSlots = availableSlots
                    )
                }
            }

            // Manifiesto de pasajeros
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFF0891B2), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Manifiesto de Pasajeros (${activeBookings.size})", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                    if (selectedService != null && selectedService.price > 0) {
                        Text(formatMxn(selectedService.price), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
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
                            "No hay reservas para esta fecha${if (selectedTime.isNotEmpty()) " a las $selectedTime" else ""}.",
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(activeBookings) { booking ->
                    ManifestRow(booking)
                }
            }

            // Reservas legado sin horario: visibles para que la empresa las
            // reubique, pero fuera del conteo de cupos de la salida
            if (noTimeBookings.isNotEmpty()) {
                item {
                    Text(
                        "SIN HORARIO ASIGNADO (${noTimeBookings.size}) — no descuentan cupo de esta salida",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFD68910),
                        letterSpacing = 1.sp
                    )
                }
                items(noTimeBookings) { booking ->
                    ManifestRow(booking)
                }
            }
        }
    }

    // Diálogo para definir/ajustar los lugares de la embarcación
    if (showCapacityDialog && selectedService != null) {
        var capacityInput by remember(selectedService.id) {
            mutableStateOf(if (capacity > 0) capacity.toString() else "")
        }
        AlertDialog(
            onDismissRequest = { showCapacityDialog = false },
            title = { Text("Lugares de la embarcación", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "¿Cuántos pasajeros puede llevar \"${selectedService.name}\"?",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = capacityInput,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 3) capacityInput = v },
                        label = { Text("Número de lugares") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (occupiedSlots > 0 && (capacityInput.toIntOrNull() ?: 0) in 1 until occupiedSlots) {
                        Text(
                            "Ojo: ya tienes $occupiedSlots lugar(es) reservados en la salida seleccionada.",
                            fontSize = 11.sp,
                            color = Color(0xFFC0392B)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        capacityInput.toIntOrNull()?.let { newCapacity ->
                            if (newCapacity > 0) {
                                viewModel.updateServiceCapacity(selectedService.id, newCapacity)
                            }
                        }
                        showCapacityDialog = false
                    },
                    enabled = (capacityInput.toIntOrNull() ?: 0) > 0
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showCapacityDialog = false }) { Text("Cancelar") }
            }
        )
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
private fun BoatServiceSelector(
    services: List<ServiceFirestore>,
    selectedServiceId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = services.find { it.id == selectedServiceId }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsBoat, contentDescription = null, tint = Color(0xFF0891B2))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("EMBARCACIÓN / TOUR", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    Text(selected?.name ?: "Selecciona", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            services.forEach { service ->
                DropdownMenuItem(
                    text = { Text(service.name, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.DirectionsBoat, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        onSelect(service.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun BoatKpiCard(
    modifier: Modifier,
    label: String,
    value: String,
    valueColor: Color,
    onEdit: (() -> Unit)? = null
) {
    Card(
        modifier = if (onEdit != null) modifier.clickable(onClick = onEdit) else modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (onEdit != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, contentDescription = "Ajustar", modifier = Modifier.size(12.dp), tint = Color(0xFF0891B2))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = valueColor)
        }
    }
}

@Composable
private fun ManifestRow(booking: BookingFirestore) {
    val isPaid = booking.status == "PAID"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    booking.travelerName.ifEmpty { booking.travelerEmail },
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1
                )
                Text(
                    "${booking.slots} lugar(es)${if (booking.departureTime.isNotEmpty()) " • ${booking.departureTime}" else ""}",
                    fontSize = 11.sp, color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMxn(booking.totalPrice), fontWeight = FontWeight.Black, fontSize = 13.sp)
                Surface(
                    color = if (isPaid) Color(0xFFFDE8EC) else Color(0xFFFDEBD0),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (isPaid) "PAGADO" else "PENDIENTE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isPaid) SeatPaid else Color(0xFFD68910)
                    )
                }
            }
        }
    }
}

@Composable
fun BoatSeatMap(
    capacity: Int,
    paidSlots: Int,
    pendingSlots: Int,
    availableSlots: Int
) {
    val safeCapacity = maxOf(capacity, 1)

    // Asignación secuencial: primero pagados, luego apartados, el resto libres (igual que el web)
    val seats = (1..safeCapacity).map { i ->
        when {
            i <= paidSlots -> SeatStatus.PAID
            i <= paidSlots + pendingSlots -> SeatStatus.PENDING
            else -> SeatStatus.AVAILABLE
        }
    }

    // Filas de 2; si la capacidad es impar, la proa lleva 1 asiento centrado
    val rows = mutableListOf<List<Pair<Int, SeatStatus>>>()
    val indexed = seats.mapIndexed { i, s -> (i + 1) to s }
    var start = 0
    if (safeCapacity % 2 != 0) {
        rows.add(listOf(indexed[0]))
        start = 1
    }
    while (start < indexed.size) {
        rows.add(indexed.subList(start, minOf(start + 2, indexed.size)))
        start += 2
    }

    Surface(
        color = DeckDark,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "VISTA DE CUBIERTA EN TIEMPO REAL",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF22D3EE),
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(16.dp))

            // Casco de la lancha
            Surface(
                color = HullDark,
                shape = RoundedCornerShape(topStart = 120.dp, topEnd = 120.dp, bottomStart = 32.dp, bottomEnd = 32.dp),
                border = BorderStroke(2.dp, HullBorder),
                modifier = Modifier.widthIn(max = 260.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Luz de navegación en proa
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Spacer(Modifier.height(8.dp))
                    // Parabrisas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF06B6D4).copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.height(16.dp))

                    // Asientos
                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            row.forEach { (num, status) ->
                                val (bg, icon) = when (status) {
                                    SeatStatus.PAID -> SeatPaid to Icons.Default.VerifiedUser
                                    SeatStatus.PENDING -> SeatPending to Icons.Default.Schedule
                                    SeatStatus.AVAILABLE -> SeatAvailable to Icons.Default.CheckCircle
                                }
                                val fg = if (status == SeatStatus.PENDING) DeckDark else Color.White
                                Surface(
                                    color = bg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(num.toString(), fontSize = 9.sp, color = fg.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = fg)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    // Motor de popa
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        modifier = Modifier.width(64.dp).height(16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("MOTOR", fontSize = 7.sp, color = Color(0xFF94A3B8), letterSpacing = 2.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Leyenda de estados
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(HullDark.copy(alpha = 0.6f))
                    .padding(12.dp)
            ) {
                SeatLegend("Libre ($availableSlots)", SeatAvailable)
                SeatLegend("Apartado ($pendingSlots)", SeatPending)
                SeatLegend("Pagado ($paidSlots)", SeatPaid)
            }
        }
    }
}

@Composable
private fun SeatLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))
    }
}
