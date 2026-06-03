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
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(
    viewModel: CompanyViewModel,
    onMapClick: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CompanyType.HOTEL) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    // Advanced fields
    var capacity by remember { mutableStateOf("") }
    var departureTimes by remember { mutableStateOf("") }
    var meetingPoint by remember { mutableStateOf("") }
    var checkIn by remember { mutableStateOf("") }
    var checkOut by remember { mutableStateOf("") }
    var amenities by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf("") }
    var routeName by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var vehicleName by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var guideName by remember { mutableStateOf("") }
    var businessHours by remember { mutableStateOf("") }
    var isOpen by remember { mutableStateOf(true) }
    
    // Coordinates and Address
    var address by remember { mutableStateOf("") }
    val selectedLat by viewModel.selectedLat.collectAsState()
    val selectedLng by viewModel.selectedLng.collectAsState()
    
    val isLoading by viewModel.isLoading.collectAsState()
    val scrollState = rememberScrollState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Publicar Experiencia", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        if (imageUri != null) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Overlay to show we can change it
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
                            value = name, 
                            onValueChange = { name = it }, 
                            label = { Text("¿Cómo se llama tu servicio?") }, 
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Title, contentDescription = null, tint = Color(0xFF007BFF)) }
                        )
                        
                        OutlinedTextField(
                            value = description, 
                            onValueChange = { description = it }, 
                            label = { Text("Cuéntale a los viajeros de qué trata...") }, 
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = price, 
                                onValueChange = { price = it }, 
                                label = { Text("Precio USD") }, 
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = Color(0xFF2E7D32)) }
                            )
                            
                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1.2f)) {
                                OutlinedTextField(
                                    value = type.name,
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
                                            onClick = { type = t; expanded = false }
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = location, 
                            onValueChange = { location = it }, 
                            label = { Text("Ubicación (Ciudad, País)") }, 
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Red) }
                        )
                    }
                }

                // Advanced Fields Card (Business Dashboard alignment)
                if (type != CompanyType.NONE) {
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
                                text = "Estos datos ayudan a que tu panel web muestre cupos, horarios y disponibilidad.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )

                            // Capacity (Common for many)
                            if (type in listOf(CompanyType.BOAT_TOUR, CompanyType.TOUR_AGENCY, CompanyType.TRANSPORT, CompanyType.HOTEL, CompanyType.HOSTEL, CompanyType.PROPERTY_RENTAL)) {
                                OutlinedTextField(
                                    value = capacity,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) capacity = it },
                                    label = { Text("Capacidad máxima") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    leadingIcon = { Icon(Icons.Default.People, contentDescription = null, tint = Color.Gray) }
                                )
                            }

                            // Departure Times (Tours and Transport)
                            if (type in listOf(CompanyType.BOAT_TOUR, CompanyType.TOUR_AGENCY, CompanyType.TRANSPORT)) {
                                OutlinedTextField(
                                    value = departureTimes,
                                    onValueChange = { departureTimes = it },
                                    label = { Text("Horarios (ej: 09:00, 13:00)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Gray) }
                                )
                            }

                            // Guide Name (Agency)
                            if (type == CompanyType.TOUR_AGENCY) {
                                OutlinedTextField(
                                    value = guideName,
                                    onValueChange = { guideName = it },
                                    label = { Text("Nombre del guía") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray) }
                                )
                            }

                            // Lodging Specifics
                            if (type in listOf(CompanyType.HOTEL, CompanyType.HOSTEL, CompanyType.PROPERTY_RENTAL)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = checkIn,
                                        onValueChange = { checkIn = it },
                                        label = { Text("Check-in") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = checkOut,
                                        onValueChange = { checkOut = it },
                                        label = { Text("Check-out") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                OutlinedTextField(
                                    value = amenities,
                                    onValueChange = { amenities = it },
                                    label = { Text("Servicios (ej: WiFi, Piscina)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = rules,
                                    onValueChange = { rules = it },
                                    label = { Text("Reglas (ej: No mascotas)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            // Restaurant Specifics
                            if (type in listOf(CompanyType.RESTAURANT, CompanyType.FOOD_STAND)) {
                                OutlinedTextField(
                                    value = businessHours,
                                    onValueChange = { businessHours = it },
                                    label = { Text("Horario (ej: 09:00 - 22:00)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("¿Está abierto ahora?", modifier = Modifier.weight(1f))
                                    Switch(checked = isOpen, onCheckedChange = { isOpen = it })
                                }
                            }

                            // Transport Specifics
                            if (type == CompanyType.TRANSPORT) {
                                OutlinedTextField(
                                    value = routeName,
                                    onValueChange = { routeName = it },
                                    label = { Text("Nombre de la ruta") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = origin,
                                        onValueChange = { origin = it },
                                        label = { Text("Origen") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = destination,
                                        onValueChange = { destination = it },
                                        label = { Text("Destino") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                OutlinedTextField(
                                    value = vehicleName,
                                    onValueChange = { vehicleName = it },
                                    label = { Text("Vehículo / Placas") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = driverName,
                                    onValueChange = { driverName = it },
                                    label = { Text("Nombre del chofer") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                // Service Location Section
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
                        Text(
                            text = "Agrega la dirección o punto de encuentro para que los viajeros puedan ubicar tu servicio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Dirección o referencia exacta") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Map, contentDescription = null, tint = Color.Gray) }
                        )

                        OutlinedTextField(
                            value = meetingPoint,
                            onValueChange = { meetingPoint = it },
                            label = { Text("Punto de encuentro (Opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = Color.Gray) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

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
                        viewModel.addService(
                            ServiceFirestore(
                                name = name,
                                description = description,
                                price = price.toDoubleOrNull() ?: 0.0,
                                type = type.name,
                                location = location,
                                capacity = capacity.toIntOrNull() ?: 0,
                                departureTimes = departureTimes.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                meetingPoint = meetingPoint,
                                checkIn = checkIn,
                                checkOut = checkOut,
                                amenities = amenities.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                rules = rules.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                routeName = routeName,
                                origin = origin,
                                destination = destination,
                                vehicleName = vehicleName,
                                driverName = driverName,
                                guideName = guideName,
                                businessHours = if (businessHours.isNotBlank()) mapOf("general" to businessHours) else emptyMap(),
                                isOpen = isOpen,
                                isVisible = true,
                                address = address,
                                latitude = selectedLat,
                                longitude = selectedLng
                            ),
                            imageUri = imageUri,
                            onComplete = {
                                viewModel.clearCoordinates()
                                onBack()
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BFF)),
                    enabled = name.isNotBlank() && price.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Publicar Ahora", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
