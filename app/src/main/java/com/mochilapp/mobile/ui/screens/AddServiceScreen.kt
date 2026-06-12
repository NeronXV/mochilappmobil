package com.mochilapp.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mochilapp.mobile.data.CompanyType
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.ui.viewmodels.CompanyViewModel

// Tipos donde el punto de encuentro puede diferir de la dirección del negocio
private val meetingPointTypes = listOf(CompanyType.BOAT_TOUR, CompanyType.TOUR_AGENCY, CompanyType.TRANSPORT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(
    viewModel: CompanyViewModel,
    onMapClick: () -> Unit,
    onBack: () -> Unit
) {
    // El borrador vive en el ViewModel: sobrevive la ida y vuelta al mapa
    val draft by viewModel.serviceDraft.collectAsState()
    val selectedLat by viewModel.selectedLat.collectAsState()
    val selectedLng by viewModel.selectedLng.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scrollState = rememberScrollState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.updateServiceDraft(draft.copy(imageUri = uri))
    }

    val isEditing = draft.editingServiceId != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isEditing) "Editar Servicio" else "Publicar Experiencia", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.clearServiceDraft()
                    }) {
                        Text("Limpiar", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF8F9FA))
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Image Selector Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { launcher.launch("image/*") },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (draft.imageUri != null || draft.existingImageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = draft.imageUri ?: draft.existingImageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.padding(12.dp))
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                                Spacer(Modifier.height(8.dp))
                                Text("Añadir foto de portada", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { viewModel.updateServiceDraft(draft.copy(name = it)) },
                            label = { Text("¿Cómo se llama tu servicio?") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Title, contentDescription = null, tint = Color(0xFF007BFF)) }
                        )

                        OutlinedTextField(
                            value = draft.description,
                            onValueChange = { viewModel.updateServiceDraft(draft.copy(description = it)) },
                            label = { Text("Cuéntale a los viajeros de qué trata...") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = draft.price,
                                onValueChange = { viewModel.updateServiceDraft(draft.copy(price = it)) },
                                label = { Text("Precio MXN") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = Color(0xFF2E7D32)) }
                            )

                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1.2f)) {
                                OutlinedTextField(
                                    value = draft.type.name,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Categoría") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        IconButton(onClick = { expanded = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    }
                                )
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    CompanyType.entries.filter { it != CompanyType.NONE }.forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text(t.name) },
                                            onClick = {
                                                viewModel.updateServiceDraft(draft.copy(type = t))
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Advanced Fields Card (Business Dashboard alignment)
                if (draft.type != CompanyType.NONE) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Datos para tu panel empresarial",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF106154)
                            )
                            Text(
                                text = "Estos datos alimentan tu panel de control: cupos, horarios y disponibilidad.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )

                            // Capacity (Common for many)
                            if (draft.type in listOf(CompanyType.BOAT_TOUR, CompanyType.TOUR_AGENCY, CompanyType.TRANSPORT, CompanyType.HOTEL, CompanyType.HOSTEL, CompanyType.PROPERTY_RENTAL)) {
                                OutlinedTextField(
                                    value = draft.capacity,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) viewModel.updateServiceDraft(draft.copy(capacity = it)) },
                                    label = { Text("Capacidad máxima") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    leadingIcon = { Icon(Icons.Default.People, contentDescription = null, tint = Color.Gray) }
                                )
                            }

                            // Departure Times (Tours and Transport)
                            if (draft.type in listOf(CompanyType.BOAT_TOUR, CompanyType.TOUR_AGENCY, CompanyType.TRANSPORT)) {
                                OutlinedTextField(
                                    value = draft.departureTimes,
                                    onValueChange = { viewModel.updateServiceDraft(draft.copy(departureTimes = it)) },
                                    label = { Text("Horarios (ej: 09:00, 13:00)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Gray) }
                                )
                            }

                            // Guide Name (Agency)
                            if (draft.type == CompanyType.TOUR_AGENCY) {
                                OutlinedTextField(
                                    value = draft.guideName,
                                    onValueChange = { viewModel.updateServiceDraft(draft.copy(guideName = it)) },
                                    label = { Text("Nombre del guía") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray) }
                                )
                            }

                            // Lodging Specifics
                            if (draft.type in listOf(CompanyType.HOTEL, CompanyType.HOSTEL, CompanyType.PROPERTY_RENTAL)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = draft.checkIn,
                                        onValueChange = { viewModel.updateServiceDraft(draft.copy(checkIn = it)) },
                                        label = { Text("Check-in") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = draft.checkOut,
                                        onValueChange = { viewModel.updateServiceDraft(draft.copy(checkOut = it)) },
                                        label = { Text("Check-out") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                OutlinedTextField(
                                    value = draft.amenities,
                                    onValueChange = { viewModel.updateServiceDraft(draft.copy(amenities = it)) },
                                    label = { Text("Servicios (ej: WiFi, Piscina)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = draft.rules,
                                    onValueChange = { viewModel.updateServiceDraft(draft.copy(rules = it)) },
                                    label = { Text("Reglas (ej: No mascotas)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            // Restaurant Specifics
                            if (draft.type in listOf(CompanyType.RESTAURANT, CompanyType.FOOD_STAND)) {
                                OutlinedTextField(
                                    value = draft.businessHours,
                                    onValueChange = { viewModel.updateServiceDraft(draft.copy(businessHours = it)) },
                                    label = { Text("Horario (ej: 09:00 - 22:00)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("¿Está abierto ahora?", modifier = Modifier.weight(1f))
                                    Switch(checked = draft.isOpen, onCheckedChange = { viewModel.updateServiceDraft(draft.copy(isOpen = it)) })
                                }
                            }

                            // Transport Specifics
                            if (draft.type == CompanyType.TRANSPORT) {
                                OutlinedTextField(
                                    value = draft.routeName,
                                    onValueChange = { viewModel.updateServiceDraft(draft.copy(routeName = it)) },
                                    label = { Text("Nombre de la ruta") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = draft.origin,
                                        onValueChange = { viewModel.updateServiceDraft(draft.copy(origin = it)) },
                                        label = { Text("Origen") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = draft.destination,
                                        onValueChange = { viewModel.updateServiceDraft(draft.copy(destination = it)) },
                                        label = { Text("Destino") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                OutlinedTextField(
                                    value = draft.vehicleName,
                                    onValueChange = { viewModel.updateServiceDraft(draft.copy(vehicleName = it)) },
                                    label = { Text("Vehículo / Placas") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = draft.driverName,
                                    onValueChange = { viewModel.updateServiceDraft(draft.copy(driverName = it)) },
                                    label = { Text("Nombre del chofer") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                // Ubicación: una sola sección consolidada
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Ubicación del servicio",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF106154)
                        )

                        OutlinedTextField(
                            value = draft.location,
                            onValueChange = { viewModel.updateServiceDraft(draft.copy(location = it)) },
                            label = { Text("Ciudad o pueblo (ej: Tulum)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Red) }
                        )

                        OutlinedTextField(
                            value = draft.address,
                            onValueChange = { viewModel.updateServiceDraft(draft.copy(address = it)) },
                            label = { Text("Dirección o referencia exacta") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Map, contentDescription = null, tint = Color.Gray) }
                        )

                        // Solo para tours/transporte, donde el punto de salida puede
                        // diferir de la dirección del negocio
                        if (draft.type in meetingPointTypes) {
                            OutlinedTextField(
                                value = draft.meetingPoint,
                                onValueChange = { viewModel.updateServiceDraft(draft.copy(meetingPoint = it)) },
                                label = { Text("Punto de encuentro / salida (Opcional)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = Color.Gray) }
                            )
                        }

                        Button(
                            onClick = onMapClick,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedLat != 0.0) Color(0xFFD4EFDF) else Color(0xFFF1F3F5),
                                contentColor = if (selectedLat != 0.0) Color(0xFF1D8348) else Color.Black
                            )
                        ) {
                            Icon(if (selectedLat != 0.0) Icons.Default.CheckCircle else Icons.Default.Map, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (selectedLat != 0.0) "Ubicación seleccionada en mapa"
                                else "Seleccionar en mapa (Opcional)",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedLat == 0.0) {
                            Text(
                                "Aún no has seleccionado un punto en el mapa",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }

            // Bottom Action Bar
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                color = Color.White
            ) {
                Button(
                    onClick = {
                        val service = ServiceFirestore(
                            name = draft.name,
                            description = draft.description,
                            price = draft.price.toDoubleOrNull() ?: 0.0,
                            type = draft.type.name,
                            location = draft.location,
                            imageUrl = draft.existingImageUrl,
                            capacity = draft.capacity.toIntOrNull() ?: 0,
                            departureTimes = draft.departureTimes.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            meetingPoint = draft.meetingPoint,
                            checkIn = draft.checkIn,
                            checkOut = draft.checkOut,
                            amenities = draft.amenities.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            rules = draft.rules.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            routeName = draft.routeName,
                            origin = draft.origin,
                            destination = draft.destination,
                            vehicleName = draft.vehicleName,
                            driverName = draft.driverName,
                            guideName = draft.guideName,
                            businessHours = if (draft.businessHours.isNotBlank()) mapOf("general" to draft.businessHours) else emptyMap(),
                            isOpen = draft.isOpen,
                            isVisible = true,
                            address = draft.address,
                            latitude = selectedLat,
                            longitude = selectedLng
                        )
                        val onDone = {
                            viewModel.clearServiceDraft()
                            onBack()
                        }
                        if (isEditing) {
                            viewModel.updateService(draft.editingServiceId!!, service, draft.imageUri, onDone)
                        } else {
                            viewModel.addService(service, draft.imageUri, onDone)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BFF)),
                    enabled = draft.name.isNotBlank() && draft.price.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(if (isEditing) "Guardar Cambios" else "Publicar Ahora", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
