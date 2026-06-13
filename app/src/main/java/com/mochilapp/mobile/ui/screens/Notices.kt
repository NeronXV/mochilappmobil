package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.data.NoticeFirestore
import com.mochilapp.mobile.data.ServiceFirestore
import java.text.SimpleDateFormat
import java.util.*

// Estilo por severidad del aviso (compartido entre panel de empresa y viajero)
data class NoticeStyle(val bg: Color, val border: Color, val fg: Color, val icon: ImageVector, val label: String)

fun noticeStyle(severity: String): NoticeStyle = when (severity) {
    "URGENT" -> NoticeStyle(Color(0xFFFFE4E6), Color(0xFFFDA4AF), Color(0xFF9F1239), Icons.Default.Error, "Urgente")
    "IMPORTANT" -> NoticeStyle(Color(0xFFFEF3C7), Color(0xFFFCD34D), Color(0xFF92400E), Icons.Default.Warning, "Importante")
    else -> NoticeStyle(Color(0xFFDBEAFE), Color(0xFF93C5FD), Color(0xFF1E40AF), Icons.Default.Campaign, "Aviso")
}

// Banner que ve el viajero en el detalle del servicio y en sus reservas
@Composable
fun NoticeBanner(notice: NoticeFirestore, modifier: Modifier = Modifier) {
    val style = noticeStyle(notice.severity)
    Surface(
        color = style.bg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, style.border),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(style.icon, contentDescription = null, tint = style.fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${style.label.uppercase()} DE ${notice.companyName.uppercase().ifEmpty { "TU RESERVA" }}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = style.fg,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(notice.message, fontSize = 12.sp, color = style.fg, lineHeight = 17.sp)
                if (notice.date.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Aplica para el ${notice.date}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = style.fg.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// Diálogo del panel de empresa: crear avisos y desactivar los vigentes
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyNoticesDialog(
    services: List<ServiceFirestore>,
    notices: List<NoticeFirestore>,
    companyName: String,
    onSend: (serviceId: String, serviceName: String, date: String, message: String, severity: String) -> Unit,
    onDeactivate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val visibleServices = services.filter { it.isVisible }
    var serviceId by remember { mutableStateOf(visibleServices.firstOrNull()?.id.orEmpty()) }
    var serviceMenuExpanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("INFO") }
    var date by remember { mutableStateOf("") } // vacío = aviso general
    var showDatePicker by remember { mutableStateOf(false) }

    val selectedService = visibleServices.find { it.id == serviceId }
    val now = System.currentTimeMillis()
    val activeNotices = notices.filter { it.isActive && (it.expiresAt <= 0L || it.expiresAt > now) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Avisos a Viajeros", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Informa retrasos, cambios o cierres. Con fecha le llega push solo a quienes tienen reserva ese día; sin fecha es un aviso general en tu servicio.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 15.sp
                )

                // Servicio
                Box {
                    OutlinedTextField(
                        value = selectedService?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Servicio") },
                        trailingIcon = {
                            IconButton(onClick = { serviceMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = serviceMenuExpanded,
                        onDismissRequest = { serviceMenuExpanded = false }
                    ) {
                        visibleServices.forEach { service ->
                            DropdownMenuItem(
                                text = { Text(service.name) },
                                onClick = {
                                    serviceId = service.id
                                    serviceMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Fecha (opcional)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(date.ifEmpty { "Aviso general (sin fecha)" }, fontSize = 12.sp)
                    }
                    if (date.isNotEmpty()) {
                        IconButton(onClick = { date = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar fecha", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Severidad
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("INFO" to "Aviso", "IMPORTANT" to "Importante", "URGENT" to "Urgente").forEach { (value, label) ->
                        FilterChip(
                            selected = severity == value,
                            onClick = { severity = value },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Mensaje (Ej: La salida de las 3pm se retrasa 30 min)") },
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )

                // Avisos vigentes
                if (activeNotices.isNotEmpty()) {
                    Text(
                        "AVISOS VIGENTES (${activeNotices.size})",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    activeNotices.forEach { notice ->
                        val style = noticeStyle(notice.severity)
                        Surface(
                            color = style.bg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        notice.serviceName + if (notice.date.isNotEmpty()) " • ${notice.date}" else " • General",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = style.fg
                                    )
                                    Text(notice.message, fontSize = 11.sp, color = style.fg, maxLines = 2)
                                }
                                TextButton(onClick = { onDeactivate(notice.id) }) {
                                    Text("Quitar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = style.fg)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSend(serviceId, selectedService?.name.orEmpty(), date, message.trim(), severity)
                },
                enabled = message.isNotBlank() && serviceId.isNotEmpty()
            ) { Text("Publicar aviso") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )

    if (showDatePicker) {
        val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { sdf.parse(date)?.time }.getOrNull()
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
                        date = utcSdf.format(Date(millis))
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
