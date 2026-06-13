package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.data.BookingFirestore

// Lógica compartida de los tableros de salidas (tour agency y transporte),
// equivalente al agrupado por horario de DepartureBoard/RouteBoard del panel web

enum class DepartureStatus(val label: String, val fg: Color, val bg: Color) {
    EMPTY("Sin reservas", Color(0xFF64748B), Color(0xFFF1F5F9)),
    OPEN("Disponible", Color(0xFF047857), Color(0xFFD1FAE5)),
    ALMOST_FULL("Casi lleno", Color(0xFFB45309), Color(0xFFFEF3C7)),
    FULL("Lleno", Color(0xFFBE123C), Color(0xFFFFE4E6)),
    OVERBOOKED("Sobrecupo", Color(0xFF881337), Color(0xFFFECDD3))
}

data class DepartureGroup(
    val time: String,
    val isGeneral: Boolean,
    val bookings: List<BookingFirestore>,
    val paidSlots: Int,
    val pendingSlots: Int,
    val occupiedSlots: Int,
    val availableSlots: Int,
    val revenue: Double,
    val status: DepartureStatus
)

// El web solo conoce PAID; en la app CHECKED_IN/COMPLETED también ocupan lugar
private fun isPaidTier(status: String) =
    status == "PAID" || status == "CHECKED_IN" || status == "COMPLETED"

fun groupBookingsByDeparture(
    times: List<String>,
    capacity: Int,
    dayBookings: List<BookingFirestore>
): List<DepartureGroup> {
    fun buildGroup(time: String, isGeneral: Boolean, bookings: List<BookingFirestore>): DepartureGroup {
        val paid = bookings.filter { isPaidTier(it.status) }.sumOf { it.slots }
        val pending = bookings.filter { it.status == "PENDING" }.sumOf { it.slots }
        val occupied = paid + pending
        val available = capacity - occupied
        val status = when {
            occupied == 0 -> DepartureStatus.EMPTY
            available < 0 -> DepartureStatus.OVERBOOKED
            available == 0 -> DepartureStatus.FULL
            available <= 3 -> DepartureStatus.ALMOST_FULL
            else -> DepartureStatus.OPEN
        }
        return DepartureGroup(
            time = time,
            isGeneral = isGeneral,
            bookings = bookings,
            paidSlots = paid,
            pendingSlots = pending,
            occupiedSlots = occupied,
            availableSlots = available,
            revenue = bookings.sumOf { it.totalPrice },
            status = status
        )
    }

    val groups = times.map { time ->
        buildGroup(time, isGeneral = false, dayBookings.filter { it.departureTime == time })
    }
    val general = dayBookings.filter { it.departureTime.isEmpty() || it.departureTime !in times }
    return if (general.isEmpty()) groups
    else groups + buildGroup("General / Sin horario", isGeneral = true, general)
}

@Composable
fun DepartureStatusBadge(status: DepartureStatus) {
    Surface(color = status.bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            status.label.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = status.fg,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun DepartureOccupancyBar(group: DepartureGroup, capacity: Int, seatLabel: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Aforo de la salida", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text(
                "${group.occupiedSlots} / $capacity $seatLabel",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = if (group.availableSlots < 0) DepartureStatus.OVERBOOKED.fg else Color.DarkGray
            )
        }
        Spacer(Modifier.height(6.dp))
        val progress = if (capacity > 0) (group.occupiedSlots.toFloat() / capacity).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = when (group.status) {
                DepartureStatus.ALMOST_FULL -> Color(0xFFF59E0B)
                DepartureStatus.FULL, DepartureStatus.OVERBOOKED -> Color(0xFFE11D48)
                else -> Color(0xFF10B981)
            },
            trackColor = Color(0xFFF1F5F9)
        )
    }
}

@Composable
fun DepartureMetricsRow(group: DepartureGroup, seatLabel: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            DepartureMetric(Modifier.weight(1f), "LIBRES", "${group.availableSlots}",
                if (group.availableSlots < 0) DepartureStatus.OVERBOOKED.fg else Color.Unspecified)
            DepartureMetric(Modifier.weight(1f), "PAGADOS", "${group.paidSlots}")
            DepartureMetric(Modifier.weight(1f), "PENDIENTES", "${group.pendingSlots}")
            DepartureMetric(Modifier.weight(1.4f), "INGRESOS", formatMxn(group.revenue), Color(0xFF047857))
        }
    }
}

@Composable
private fun DepartureMetric(modifier: Modifier, label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(modifier = modifier) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 0.5.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = valueColor, maxLines = 1)
    }
}

@Composable
fun DeparturePassengerRow(booking: BookingFirestore, seatLabel: String) {
    val isPaid = isPaidTier(booking.status)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                booking.travelerName.ifEmpty { booking.travelerEmail },
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1
            )
            Text("${booking.slots} $seatLabel", fontSize = 10.sp, color = Color.Gray)
        }
        Surface(
            color = if (isPaid) Color(0xFFD1FAE5) else Color(0xFFFEF3C7),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                if (isPaid) "PAGADO" else "PENDIENTE",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = if (isPaid) Color(0xFF047857) else Color(0xFFB45309)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(formatMxn(booking.totalPrice), fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}
