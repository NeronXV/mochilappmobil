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
import com.mochilapp.mobile.data.Pricing
import com.mochilapp.mobile.data.esPrivado
import com.mochilapp.mobile.data.holdsSeats
import com.mochilapp.mobile.data.pricingModel
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
    
    // Inicio del día actual (fecha local) expresado en medianoche UTC, como lo maneja el DatePicker
    val todayUtcStart = remember {
        val local = java.util.Calendar.getInstance()
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(java.util.Calendar.YEAR),
                local.get(java.util.Calendar.MONTH),
                local.get(java.util.Calendar.DAY_OF_MONTH)
            )
        }.timeInMillis
    }
    val selectableDates = remember {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcStart
            override fun isSelectableYear(year: Int): Boolean =
                year >= java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        }
    }
    val datePickerState = rememberDatePickerState(selectableDates = selectableDates)
    val dateRangePickerState = rememberDateRangePickerState(selectableDates = selectableDates)
    var showDatePicker by remember { mutableStateOf(false) }
    // El picker entrega millis en medianoche UTC: formatear en UTC para no desfasar el día
    val dateFormatter = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }

    // Hospedaje usa rango entrada→salida; el resto, una sola fecha
    val isLodging = service?.type == "HOTEL" || service?.type == "HOSTEL" || service?.type == "PROPERTY_RENTAL"
    val checkInMillis = if (isLodging) dateRangePickerState.selectedStartDateMillis else datePickerState.selectedDateMillis
    val checkOutMillis = dateRangePickerState.selectedEndDateMillis
    val checkInText = checkInMillis?.let { dateFormatter.format(java.util.Date(it)) } ?: ""
    val checkOutText = checkOutMillis?.let { dateFormatter.format(java.util.Date(it)) } ?: ""
    val nights = if (isLodging && checkInMillis != null && checkOutMillis != null)
        ((checkOutMillis - checkInMillis) / 86_400_000L).toInt() else 0
    val dateSelected = if (isLodging) nights > 0 else checkInMillis != null

    var selectedTime by remember { mutableStateOf<String?>(null) }
    val bookingsByDate by bookingViewModel.currentBookings.collectAsState()
    val serviceBookings by bookingViewModel.serviceBookings.collectAsState()

    // Modalidad de venta: en PRIVADA se reserva la unidad completa y una sola
    // reserva activa bloquea la salida entera (spec privada/colectiva §4)
    val pricing = service?.pricingModel()
    val esPrivado = service?.esPrivado == true

    // Disponibilidad real para evitar sobreventa. capacity ya es el aforo total
    // (en hospedaje = camas configuradas). Si no hay aforo definido (0), no se bloquea.
    val capacity = pricing?.capacidadMaxima ?: (service?.capacity ?: 0)
    // Solo cuentan las reservas que retienen cupo: las PENDING abandonadas
    // liberan su lugar al expirar el hold (holdsSeats)
    val usedSlots = when {
        service == null -> 0
        // Hospedaje: suma las plazas de reservas cuyo rango de noches solapa el solicitado
        isLodging -> if (dateSelected) serviceBookings.filter { it.holdsSeats() }.sumOf { b ->
            val bIn = b.date
            val bOut = b.checkOutDate
            val overlaps = if (bOut.isNotEmpty()) bIn < checkOutText && checkInText < bOut
                           else bIn < checkOutText && checkInText <= bIn // legado sin salida: ocupa su noche
            if (overlaps) b.slots else 0
        } else 0
        // Resto: reservas de la misma fecha (y horario si aplica)
        checkInMillis != null -> bookingsByDate
            .filter { it.holdsSeats() }
            .filter { if (service?.departureTimes?.isNotEmpty() == true) it.departureTime == selectedTime else true }
            .sumOf { it.slots }
        else -> 0
    }
    val remainingSlots = capacity - usedSlots

    // Privada: la salida (fecha + horario) está tomada si CUALQUIER reserva
    // activa la retiene, sin importar cuántas personas incluya
    val salidaOcupada = esPrivado && dateSelected && bookingsByDate
        .filter { it.holdsSeats() }
        .any {
            if (service?.departureTimes?.isNotEmpty() == true) it.departureTime == selectedTime
            else true
        }
    // ¿La que bloquea es MI propia reserva pendiente de pago? Cambiar el
    // mensaje: "reservada por otro viajero" era mentira y confundía
    val myBookings by bookingViewModel.myBookings.collectAsState()
    val salidaOcupadaPorMi = salidaOcupada && myBookings.any {
        it.serviceId == serviceId && it.date == checkInText && it.holdsSeats() &&
            (service?.departureTimes?.isEmpty() == true || it.departureTime == (selectedTime ?: ""))
    }

    // Solo bloquea cuando hay aforo definido, fechas elegidas y no alcanza
    val capacityBlocks = if (esPrivado) salidaOcupada
                         else capacity > 0 && dateSelected && remainingSlots < slots

    val bookingResult by bookingViewModel.bookingResult.collectAsState()
    val bookingError by bookingViewModel.bookingError.collectAsState()
    val activePromo by marketplaceViewModel.activePromo.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(bookingError) {
        bookingError?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            bookingViewModel.clearBookingError()
        }
    }

    LaunchedEffect(serviceId) {
        service = marketplaceViewModel.getServiceById(serviceId)
    }

    LaunchedEffect(checkInText) {
        if (checkInText.isNotEmpty()) {
            bookingViewModel.updateAvailabilityQuery(serviceId, checkInText)
        }
    }

    LaunchedEffect(bookingResult) {
        bookingResult?.let { id ->
            // Consumir ANTES de navegar: si queda pegado, la siguiente reserva
            // salta directo al pago de esta (bug detectado en la sesión 12-jul)
            bookingViewModel.clearBookingResult()
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
            if (isLodging) {
                DateRangePicker(
                    state = dateRangePickerState,
                    title = {
                        Text(
                            "Selecciona entrada y salida",
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                        )
                    },
                    modifier = Modifier.height(520.dp)
                )
            } else {
                DatePicker(state = datePickerState)
            }
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
                        // weight(1f): sin él, el texto largo de privado empujaba el
                        // botón "+" fuera de la pantalla (reporte de Pedro 12-jul)
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(t("people"), fontWeight = FontWeight.Bold)
                            Text(
                                if (esPrivado) "Reservas el servicio completo para tu grupo"
                                else "Indica cuántas personas irán",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (esPrivado) Color(0xFF1D8348) else Color.Gray
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (slots > 1) slots-- }) {
                                Icon(Icons.Default.RemoveCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(slots.toString(), fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            // Privada: tope = capacidad máxima del grupo (la unidad es
                            // tuya). Colectiva: no pedir más lugares de los que quedan
                            val canAddMore = if (esPrivado) capacity <= 0 || slots < capacity
                                             else capacity <= 0 || !dateSelected || slots < remainingSlots
                            IconButton(onClick = { if (canAddMore) slots++ }, enabled = canAddMore) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, tint = if (canAddMore) MaterialTheme.colorScheme.primary else Color.LightGray)
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
                        if (isLodging) {
                            Text("Fechas de estancia", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Entrada", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(checkInText.ifEmpty { "Elegir" }, color = if (checkInText.isEmpty()) Color.Gray else Color.Black)
                                    }
                                }
                                Text("→", color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text("Salida", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(checkOutText.ifEmpty { "Elegir" }, color = if (checkOutText.isEmpty()) Color.Gray else Color.Black)
                                    }
                                }
                            }
                            if (nights > 0) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "$nights ${if (nights == 1) "noche" else "noches"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(t("date"), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(checkInText.ifEmpty { "Seleccionar fecha" }, color = if (checkInText.isEmpty()) Color.Gray else Color.Black)
                            }
                        }
                    }

                    // Departure Time Selection (Dynamic)
                    service?.let { s ->
                        if (s.departureTimes.isNotEmpty()) {
                            HorizontalDivider(color = Color(0xFFF1F3F5))
                            Column {
                                Text("Horario de salida", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    s.departureTimes.forEach { time ->
                                        val isSelected = selectedTime == time
                                        val activasEnHorario = if (dateSelected) bookingsByDate
                                            .filter { it.holdsSeats() && it.departureTime == time } else emptyList()
                                        // Privada: una sola reserva activa toma la salida
                                        // completa. Colectiva: cupos restantes del horario
                                        val timeUsed = activasEnHorario.sumOf { it.slots }
                                        val timeRemaining = capacity - timeUsed
                                        val showAvailability = dateSelected && (esPrivado || capacity > 0)
                                        val isFull = if (esPrivado) dateSelected && activasEnHorario.isNotEmpty()
                                                     else showAvailability && timeRemaining <= 0
                                        FilterChip(
                                            selected = isSelected,
                                            enabled = !isFull,
                                            onClick = { selectedTime = time },
                                            label = {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    Text(time, fontWeight = FontWeight.Bold)
                                                    if (showAvailability) {
                                                        Text(
                                                            when {
                                                                esPrivado && isFull -> "Reservado"
                                                                esPrivado -> "Disponible"
                                                                isFull -> "Lleno"
                                                                timeRemaining == 1 -> "Queda 1"
                                                                else -> "Quedan $timeRemaining"
                                                            },
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Privada: estado de la salida completa (libre u ocupada)
                    if (esPrivado && dateSelected) {
                        HorizontalDivider(color = Color(0xFFF1F3F5))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = if (salidaOcupada) Color.Red else Color(0xFF2ECC71),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when {
                                    salidaOcupadaPorMi -> "Tienes una reserva pendiente de pago para esta salida. Págala o cancélala desde Mis Reservas."
                                    salidaOcupada -> "Esta salida ya fue reservada por otro viajero"
                                    else -> "Esta salida está libre para tu grupo"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (salidaOcupada) Color.Red else Color.Gray
                            )
                        }
                    }

                    // Availability Info (hospedaje y resto): se muestra cuando hay
                    // aforo definido y fechas elegidas. En hospedaje refleja las
                    // plazas libres en el rango de noches solicitado.
                    if (!esPrivado && capacity > 0 && dateSelected) {
                        val available = remainingSlots.coerceAtLeast(0)
                        val enough = remainingSlots >= slots
                        val unitWord = if (isLodging) "plazas" else "cupos"
                        HorizontalDivider(color = Color(0xFFF1F3F5))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = if (enough) Color(0xFF2ECC71) else Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (enough) "${unitWord.replaceFirstChar { it.uppercase() }} disponibles: $available"
                                       else "Sin $unitWord suficientes ($available disponibles)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (enough) Color.Gray else Color.Red
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Total con la misma fórmula que el servidor (Pricing es espejo de
            // functions/pricing.js). Esto es solo display: el cobro real lo
            // calcula createPaymentIntent leyendo el servicio de Firestore.
            val originalTotal = pricing?.calcularTotal(slots, if (isLodging) nights else 0) ?: 0.0
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
                                text = formatMxn(originalTotal),
                                style = MaterialTheme.typography.labelSmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                                color = Color.Gray
                            )
                        }
                        Text(t("total_price"), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Text(
                            formatMxn(totalPrice),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = if (discountAmount > 0) Color(0xFF2ECC71) else MaterialTheme.colorScheme.primary
                            )
                        )
                        if (!esPrivado && isLodging && nights > 0) {
                            Text(
                                "${formatMxn(service?.price ?: 0.0)} × $nights ${if (nights == 1) "noche" else "noches"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        // Desglose privado: el viajero ve de dónde sale el total (§8.2)
                        (pricing as? Pricing.Privada)?.let { p ->
                            val extras = maxOf(0, slots - p.personasIncluidas)
                            Text(
                                buildString {
                                    if (extras > 0) {
                                        append("Base (hasta ${p.personasIncluidas}): ${formatMxn(p.precioBase)} + ")
                                        append("$extras × ${formatMxn(p.precioPersonaExtra)}")
                                    } else {
                                        append("Servicio completo (hasta ${p.personasIncluidas} personas)")
                                    }
                                    if (isLodging && nights > 1) append(" × $nights noches")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val currentService = service
                            if (currentService != null && dateSelected) {
                                bookingViewModel.createBooking(
                                    serviceId = serviceId,
                                    serviceName = currentService.name,
                                    travelerName = travelerName,
                                    date = checkInText,
                                    checkOutDate = if (isLodging) checkOutText else "",
                                    slots = slots,
                                    totalPrice = totalPrice,
                                    ownerEmail = currentService.ownerEmail,
                                    departureTime = selectedTime ?: "",
                                    promoId = if (discountAmount > 0) activePromo?.id ?: "" else "",
                                    promoCode = if (discountAmount > 0) activePromo?.promoCode ?: "" else "",
                                    discountPercent = discountPercent,
                                    discountAmount = discountAmount,
                                    originalTotal = originalTotal,
                                    modalidad = if (esPrivado) "PRIVADA" else "COLECTIVA"
                                )
                            }
                        },
                        enabled = dateSelected &&
                                 service != null &&
                                 (service?.departureTimes?.isEmpty() == true || selectedTime != null) &&
                                 !capacityBlocks,
                        modifier = Modifier.height(56.dp).padding(start = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(t("payment_continue"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
