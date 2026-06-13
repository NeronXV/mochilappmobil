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
import com.mochilapp.mobile.data.BookingFirestore
import com.mochilapp.mobile.ui.viewmodels.CompanyViewModel
import java.util.Calendar

// Colores alineados al AvailabilityCalendar del panel web (tema oscuro slate)
private val CalDark = Color(0xFF020617)        // slate-950
private val CalDarkBorder = Color(0xFF1E293B)  // slate-800
private val CalPaid = Color(0xFFE11D48)        // rose-600
private val CalPending = Color(0xFFFBBF24)     // amber-400
private val CalAvailable = Color(0xFF10B981)   // emerald-500
private val RentalAccent = Color(0xFF10B981)

private val MonthNames = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

private enum class DayStatus { PAID, PENDING, AVAILABLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyRentalModuleScreen(
    viewModel: CompanyViewModel,
    onBack: () -> Unit
) {
    val services by viewModel.myServices.collectAsState()
    val bookings by viewModel.myBookings.collectAsState()

    val rentalServices = remember(services) {
        services.filter { it.type == "PROPERTY_RENTAL" && it.isVisible }
    }

    var selectedServiceId by remember { mutableStateOf("") }
    val now = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH)) } // 0-11

    LaunchedEffect(rentalServices) {
        if (rentalServices.isNotEmpty() && rentalServices.none { it.id == selectedServiceId }) {
            selectedServiceId = rentalServices.first().id
        }
    }
    val selectedService = rentalServices.find { it.id == selectedServiceId }

    val daysInMonth = remember(selectedYear, selectedMonth) {
        Calendar.getInstance().apply {
            clear(); set(selectedYear, selectedMonth, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val monthPrefix = "%04d-%02d".format(selectedYear, selectedMonth + 1)
    val activeMonthBookings = remember(bookings, selectedServiceId, monthPrefix) {
        bookings.filter {
            it.serviceId == selectedServiceId && it.status != "CANCELLED" && it.date.startsWith(monthPrefix)
        }.sortedBy { it.date }
    }

    // Una propiedad se renta completa: el estado del día lo define su mejor reserva.
    // CHECKED_IN/COMPLETED cuentan como ocupado igual que PAID (el web solo conoce PAID)
    fun statusForDate(date: String): DayStatus {
        val dayBookings = activeMonthBookings.filter { it.date == date }
        return when {
            dayBookings.any { it.status == "PAID" || it.status == "CHECKED_IN" || it.status == "COMPLETED" } -> DayStatus.PAID
            dayBookings.any { it.status == "PENDING" } -> DayStatus.PENDING
            else -> DayStatus.AVAILABLE
        }
    }

    var paidNights = 0
    var pendingNights = 0
    for (day in 1..daysInMonth) {
        when (statusForDate("%s-%02d".format(monthPrefix, day))) {
            DayStatus.PAID -> paidNights++
            DayStatus.PENDING -> pendingNights++
            DayStatus.AVAILABLE -> {}
        }
    }
    val occupiedNights = paidNights + pendingNights
    val availableNights = daysInMonth - occupiedNights
    val occupancyRate = if (daysInMonth > 0) occupiedNights * 100 / daysInMonth else 0
    val monthRevenue = activeMonthBookings.sumOf { it.totalPrice }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Renta de Propiedades", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "Calendario de disponibilidad y rentas del mes",
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
        if (rentalServices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Cottage, contentDescription = null, modifier = Modifier.size(64.dp), tint = RentalAccent)
                    Spacer(Modifier.height(16.dp))
                    Text("Sin propiedades registradas", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Este módulo requiere al menos un servicio registrado con la categoría Renta vacacional (PROPERTY_RENTAL).",
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
            // Selector de propiedad
            if (rentalServices.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rentalServices) { service ->
                            FilterChip(
                                selected = selectedServiceId == service.id,
                                onClick = { selectedServiceId = service.id },
                                label = { Text(service.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Default.Cottage, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Ficha de la propiedad
            selectedService?.let { s ->
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (s.location.isNotEmpty()) displayLocation(s.location) else "Ubicación no especificada",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("POR NOCHE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                                    Text(
                                        if (s.price > 0) formatMxn(s.price) else "Sin precio",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = RentalAccent
                                    )
                                }
                            }

                            if (s.amenities.isNotEmpty()) {
                                Column {
                                    Text("AMENIDADES", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                                    Spacer(Modifier.height(6.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(s.amenities) { amenity ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    amenity,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (s.rules.isNotEmpty()) {
                                Column {
                                    Text("REGLAS DE LA CASA", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(s.rules.joinToString(" • "), fontSize = 11.sp, color = Color.DarkGray, lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Calendario de disponibilidad (tema oscuro como el web)
            item {
                MonthAvailabilityCalendar(
                    year = selectedYear,
                    month = selectedMonth,
                    daysInMonth = daysInMonth,
                    statusForDate = { date -> statusForDate(date) },
                    onPrevMonth = {
                        if (selectedMonth == 0) { selectedYear--; selectedMonth = 11 } else selectedMonth--
                    },
                    onNextMonth = {
                        if (selectedMonth == 11) { selectedYear++; selectedMonth = 0 } else selectedMonth++
                    }
                )
            }

            // KPIs del mes
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RentalKpiCard(Modifier.weight(1f), "OCUPADAS", paidNights.toString(), CalPaid)
                    RentalKpiCard(Modifier.weight(1f), "APARTADAS", pendingNights.toString(), Color(0xFFD68910))
                    RentalKpiCard(Modifier.weight(1f), "LIBRES", availableNights.toString(), RentalAccent)
                    RentalKpiCard(Modifier.weight(1f), "OCUPACIÓN", "$occupancyRate%", MaterialTheme.colorScheme.onSurface)
                }
            }

            // Ingresos del mes
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = RentalAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "INGRESOS DE ${MonthNames[selectedMonth].uppercase()}",
                                fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp
                            )
                            Text("${formatMxn(monthRevenue)} MXN", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }
            }

            // Manifiesto de rentas del mes
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = RentalAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Rentas de ${MonthNames[selectedMonth]} (${activeMonthBookings.size})",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }

            if (activeMonthBookings.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No hay reservas registradas para este mes.",
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(activeMonthBookings) { booking ->
                    RentalManifestRow(booking)
                }
            }
        }
    }
}

@Composable
private fun MonthAvailabilityCalendar(
    year: Int,
    month: Int,
    daysInMonth: Int,
    statusForDate: (String) -> DayStatus,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val firstDayIndex = remember(year, month) {
        Calendar.getInstance().apply {
            clear(); set(year, month, 1)
        }.get(Calendar.DAY_OF_WEEK) - 1 // 0 = domingo
    }

    Surface(
        color = CalDark,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, CalDarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Encabezado con navegación de meses
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("${MonthNames[month]} $year", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text(
                        "CALENDARIO DE DISPONIBILIDAD",
                        color = CalAvailable,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
                Row {
                    IconButton(onClick = onPrevMonth) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior", tint = Color(0xFF94A3B8))
                    }
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente", tint = Color(0xFF94A3B8))
                    }
                }
            }

            // Días de la semana
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("D", "L", "M", "M", "J", "V", "S").forEach { d ->
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Celdas del mes en filas de 7
            val cells: List<Int?> = List(firstDayIndex) { null } + (1..daysInMonth).toList()
            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = "%04d-%02d-%02d".format(year, month + 1, day)
                            val (bg, fg) = when (statusForDate(date)) {
                                DayStatus.PAID -> CalPaid to Color.White
                                DayStatus.PENDING -> CalPending to CalDark
                                DayStatus.AVAILABLE -> CalAvailable to Color.White
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(bg, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(day.toString(), color = fg, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                    // Completar la última semana con espacios
                    repeat(7 - week.size) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }

            // Leyenda
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                CalendarLegendDot(CalAvailable, "Disponible")
                CalendarLegendDot(CalPending, "Apartado")
                CalendarLegendDot(CalPaid, "Ocupado")
            }
        }
    }
}

@Composable
private fun CalendarLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
    }
}

@Composable
private fun RentalKpiCard(modifier: Modifier, label: String, value: String, valueColor: Color) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = valueColor)
        }
    }
}

@Composable
private fun RentalManifestRow(booking: BookingFirestore) {
    val isPaid = booking.status == "PAID" || booking.status == "CHECKED_IN" || booking.status == "COMPLETED"
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
                Text(booking.date, fontSize = 11.sp, color = Color.Gray)
            }
            Surface(
                color = if (isPaid) Color(0xFFFFE4E6) else Color(0xFFFDEBD0),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (isPaid) "OCUPADA" else "APARTADA",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isPaid) CalPaid else Color(0xFFD68910)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(formatMxn(booking.totalPrice), fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}
