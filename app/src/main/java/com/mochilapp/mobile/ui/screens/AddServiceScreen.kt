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
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.mochilapp.mobile.data.CompanyType
import com.mochilapp.mobile.data.RoomFirestore
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.ui.viewmodels.CompanyViewModel

// Tipos donde el punto de encuentro puede diferir de la dirección del negocio
private val meetingPointTypes = listOf(CompanyType.BOAT_TOUR, CompanyType.TOUR_AGENCY, CompanyType.TRANSPORT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(
    viewModel: CompanyViewModel,
    onMapClick: () -> Unit,
    onBack: () -> Unit,
    // Datos del perfil de la empresa para precargar la ubicación del servicio
    companyLocation: String = "",
    companyLat: Double = 0.0,
    companyLng: Double = 0.0
) {
    // El borrador vive en el ViewModel: sobrevive la ida y vuelta al mapa
    val draft by viewModel.serviceDraft.collectAsState()
    val selectedLat by viewModel.selectedLat.collectAsState()
    val selectedLng by viewModel.selectedLng.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serviceError by viewModel.serviceError.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.updateServiceDraft(draft.copy(imageUri = uri))
    }

    val isEditing = draft.editingServiceId != null

    // Servicio nuevo: precargar ciudad y pin del perfil de la empresa
    // (como Airbnb/Booking, que parten de la dirección del anfitrión)
    LaunchedEffect(Unit) {
        val current = viewModel.serviceDraft.value
        if (current.editingServiceId == null) {
            if (current.location.isBlank() && companyLocation.isNotBlank()) {
                viewModel.updateServiceDraft(current.copy(location = companyLocation))
            }
            if (viewModel.selectedLat.value == 0.0 && viewModel.selectedLng.value == 0.0 &&
                (companyLat != 0.0 || companyLng != 0.0)
            ) {
                viewModel.updateCoordinates(companyLat, companyLng)
            }
        }
    }

    LaunchedEffect(serviceError) {
        serviceError?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            leadingIcon = { Icon(Icons.Default.Title, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
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

                            // Capacity (transporte/tours). En hospedaje la capacidad se
                            // deriva de las habitaciones/camas configuradas más abajo.
                            if (draft.type in listOf(CompanyType.BOAT_TOUR, CompanyType.TOUR_AGENCY, CompanyType.TRANSPORT)) {
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

                                // Editor de habitaciones/camas: alimenta la distribución de planta del panel
                                LodgingRoomsEditor(
                                    rooms = draft.rooms,
                                    isHostel = draft.type == CompanyType.HOSTEL,
                                    onRoomsChange = { viewModel.updateServiceDraft(draft.copy(rooms = it)) }
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

                            // Food Stand: opciones de entrega (el pedido es por orden, no por persona)
                            if (draft.type == CompanyType.FOOD_STAND) {
                                Text(
                                    "Entrega del pedido",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF106154)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Para recoger en el puesto", modifier = Modifier.weight(1f))
                                    Switch(checked = draft.offersPickup, onCheckedChange = { viewModel.updateServiceDraft(draft.copy(offersPickup = it)) })
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DeliveryDining, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Entrega a domicilio", modifier = Modifier.weight(1f))
                                    Switch(checked = draft.offersDelivery, onCheckedChange = { viewModel.updateServiceDraft(draft.copy(offersDelivery = it)) })
                                }
                                if (draft.offersDelivery) {
                                    OutlinedTextField(
                                        value = draft.deliveryFee,
                                        onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) viewModel.updateServiceDraft(draft.copy(deliveryFee = it)) },
                                        label = { Text("Costo de envío (MXN)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = Color(0xFF2E7D32)) }
                                    )
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

                        if (selectedLat != 0.0 || selectedLng != 0.0) {
                            // Mini-mapa con el pin elegido; tocarlo abre el selector
                            val pin = LatLng(selectedLat, selectedLng)
                            val miniCamera = rememberCameraPositionState()
                            LaunchedEffect(pin) {
                                miniCamera.position = CameraPosition.fromLatLngZoom(pin, 15f)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                GoogleMap(
                                    modifier = Modifier.matchParentSize(),
                                    cameraPositionState = miniCamera,
                                    googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }
                                ) {
                                    val markerState = rememberMarkerState(position = pin)
                                    LaunchedEffect(pin) { markerState.position = pin }
                                    Marker(state = markerState)
                                }
                                // Captura el toque por encima del mapa lite
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { onMapClick() }
                                )
                            }
                            TextButton(onClick = onMapClick, modifier = Modifier.align(Alignment.End)) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ajustar pin en el mapa", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onMapClick,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF1F3F5),
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text("Seleccionar en mapa", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "Marca el punto exacto: así apareces en el mapa turístico del viajero",
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
                        val isLodging = draft.type in listOf(CompanyType.HOTEL, CompanyType.HOSTEL, CompanyType.PROPERTY_RENTAL)
                        // En hospedaje la capacidad (aforo de personas) la mandan las
                        // camas configuradas; en el resto, el campo "Capacidad máxima".
                        val resolvedCapacity = if (isLodging)
                            draft.rooms.sumOf { it.capacity.coerceAtLeast(0) }
                        else
                            draft.capacity.toIntOrNull() ?: 0
                        val service = ServiceFirestore(
                            name = draft.name,
                            description = draft.description,
                            price = draft.price.toDoubleOrNull() ?: 0.0,
                            type = draft.type.name,
                            location = draft.location,
                            imageUrl = draft.existingImageUrl,
                            capacity = resolvedCapacity,
                            departureTimes = draft.departureTimes.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            meetingPoint = draft.meetingPoint,
                            checkIn = draft.checkIn,
                            checkOut = draft.checkOut,
                            rooms = draft.rooms,
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
                            longitude = selectedLng,
                            offersPickup = draft.offersPickup,
                            offersDelivery = draft.offersDelivery,
                            deliveryFee = if (draft.offersDelivery) draft.deliveryFee.toDoubleOrNull() ?: 0.0 else 0.0
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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

// Editor de habitaciones (hotel/renta) o camas (hostal) para hospedaje.
// Cada unidad guarda nombre y número de camas; la lista define la distribución
// de planta y el total de unidades que ve el negocio en su panel.
@Composable
private fun LodgingRoomsEditor(
    rooms: List<RoomFirestore>,
    isHostel: Boolean,
    onRoomsChange: (List<RoomFirestore>) -> Unit
) {
    // En hostal cada unidad ES una cama (1 plaza): no tiene sentido un sub-campo
    // "Camas". En hotel/renta la unidad es una habitación con N camas.
    val unitLabel = if (isHostel) "Cama" else "Habitación"
    val totalBeds = rooms.sumOf { it.capacity.coerceAtLeast(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MeetingRoom, contentDescription = null, tint = Color(0xFF106154), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("${unitLabel}s (${rooms.size})", fontWeight = FontWeight.Bold, color = Color(0xFF106154))
        }
        Text(
            text = if (isHostel)
                "Agrega cada cama disponible. El total define las unidades y la distribución de planta de tu panel."
            else
                "Agrega cada habitación y cuántas camas tiene. Esto define las unidades, las plazas totales y la distribución de planta de tu panel.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        rooms.forEachIndexed { index, room ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = room.name,
                    onValueChange = { newName ->
                        onRoomsChange(rooms.toMutableList().also { it[index] = room.copy(name = newName) })
                    },
                    label = { Text("$unitLabel ${index + 1}") },
                    singleLine = true,
                    modifier = Modifier.weight(1.6f),
                    shape = RoundedCornerShape(12.dp)
                )
                // Solo hotel/renta lleva número de camas por habitación
                if (!isHostel) {
                    OutlinedTextField(
                        value = if (room.capacity > 0) room.capacity.toString() else "",
                        onValueChange = { v ->
                            if (v.all { c -> c.isDigit() }) {
                                onRoomsChange(rooms.toMutableList().also { it[index] = room.copy(capacity = v.toIntOrNull() ?: 0) })
                            }
                        },
                        label = { Text("Camas") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                IconButton(onClick = {
                    onRoomsChange(rooms.toMutableList().also { it.removeAt(index) })
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar $unitLabel", tint = Color(0xFFD32F2F))
                }
            }
        }

        // Resumen de aforo: para hotel/renta se ven plazas totales; en hostal el
        // total de camas coincide con el número de unidades.
        if (rooms.isNotEmpty()) {
            Text(
                text = if (isHostel)
                    "Total: ${rooms.size} camas"
                else
                    "Total: ${rooms.size} habitaciones · $totalBeds camas",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF106154)
            )
        }

        OutlinedButton(
            onClick = {
                onRoomsChange(
                    rooms + RoomFirestore(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "$unitLabel ${rooms.size + 1}",
                        type = unitLabel,
                        capacity = 1
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Agregar $unitLabel")
        }
    }
}
