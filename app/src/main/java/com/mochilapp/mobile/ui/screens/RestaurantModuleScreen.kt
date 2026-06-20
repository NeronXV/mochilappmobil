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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.data.BookingFirestore
import com.mochilapp.mobile.data.MenuItemFirestore
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.ui.viewmodels.CompanyViewModel
import java.text.SimpleDateFormat
import java.util.*

private val FoodAccent = Color(0xFF059669)     // emerald-600
private val FoodSpecial = Color(0xFFD68910)    // amber
private val FoodSpecialBg = Color(0xFFFEF3C7)  // amber-100
private val FoodSoldOut = Color(0xFFE11D48)    // rose-600
private val FoodSoldOutBg = Color(0xFFFFE4E6)  // rose-100
private val FoodAvailableBg = Color(0xFFD1FAE5) // emerald-100

// Orden de categorías del MenuBoard web
private val MenuCategories = listOf("Especial", "Entrada", "Platillo Fuerte", "Postre", "Bebida")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantModuleScreen(
    viewModel: CompanyViewModel,
    onBack: () -> Unit
) {
    val services by viewModel.myServices.collectAsState()
    val bookings by viewModel.myBookings.collectAsState()

    val foodServices = remember(services) {
        services.filter { (it.type == "RESTAURANT" || it.type == "FOOD_STAND") && it.isVisible }
    }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var selectedServiceId by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(sdf.format(Date())) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(foodServices) {
        if (foodServices.isNotEmpty() && foodServices.none { it.id == selectedServiceId }) {
            selectedServiceId = foodServices.first().id
        }
    }
    val selectedService = foodServices.find { it.id == selectedServiceId }
    val isFoodStand = selectedService?.type == "FOOD_STAND"

    val activeBookings = remember(bookings, selectedServiceId, selectedDate) {
        bookings.filter {
            it.serviceId == selectedServiceId && it.date == selectedDate && it.status != "CANCELLED"
        }
    }
    val dailyRevenue = activeBookings.sumOf { it.totalPrice }

    // Comandas del puesto: pedidos pagados con productos, los no entregados arriba
    val foodOrders = remember(bookings, selectedServiceId, selectedDate) {
        bookings.filter {
            it.serviceId == selectedServiceId && it.date == selectedDate &&
                it.status == "PAID" && it.orderItems.isNotEmpty()
        }.sortedBy { if (it.orderStatus == "DELIVERED") 1 else 0 }
    }
    val foodRevenue = foodOrders.sumOf { it.totalPrice }

    val menuItems = remember(selectedService) { resolveMenu(selectedService) }
    val availableCount = menuItems.count { it.isAvailable }
    // Con menú real se habilita la edición; con el de muestra solo se invita a crear el propio
    val isRealMenu = selectedService?.menu?.isNotEmpty() == true
    val realMenu = selectedService?.menu.orEmpty()

    var showItemDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MenuItemFirestore?>(null) }

    fun saveMenuItem(item: MenuItemFirestore) {
        val newMenu = if (realMenu.any { it.id == item.id }) {
            realMenu.map { if (it.id == item.id) item else it }
        } else {
            realMenu + item
        }
        viewModel.updateServiceMenu(selectedServiceId, newMenu)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gestión Gastronómica", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "Menú digital, estado del local y libro de reservas",
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
        if (foodServices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(64.dp), tint = FoodAccent)
                    Spacer(Modifier.height(16.dp))
                    Text("Sin servicios gastronómicos", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Este módulo requiere al menos un servicio registrado con la categoría Restaurante o Puesto de comida.",
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
            if (foodServices.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(foodServices) { service ->
                            FilterChip(
                                selected = selectedServiceId == service.id,
                                onClick = { selectedServiceId = service.id },
                                label = { Text(service.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        if (service.type == "FOOD_STAND") Icons.Default.Fastfood else Icons.Default.Restaurant,
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

            // Estado comercial
            selectedService?.let { s ->
                item {
                    val isOpen = s.isOpen
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (isOpen) FoodAccent else FoodSoldOut,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.padding(10.dp).size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("ESTADO DEL SERVICIO", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                                    Text(
                                        if (isOpen) "Abierto al Público" else "Cerrado temporalmente",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = if (isOpen) FoodAccent else FoodSoldOut
                                    )
                                    val hours = s.businessHours["general"].orEmpty()
                                    if (hours.isNotEmpty()) {
                                        Text("Horario: $hours", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }

                            // Especial de hoy: el platillo recomendado del menú o el servicio mismo
                            val special = menuItems.firstOrNull { it.category == "Especial" }
                            if (special != null) {
                                Surface(
                                    color = FoodSpecialBg,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FoodSpecial, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("ESPECIAL DE HOY", fontSize = 8.sp, fontWeight = FontWeight.Black, color = FoodSpecial, letterSpacing = 1.sp)
                                            Text(special.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Menú del establecimiento
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = FoodAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Menú del Establecimiento", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Text(
                                "$availableCount de ${menuItems.size} disponibles",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    }
                    Button(
                        onClick = { editingItem = null; showItemDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FoodAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Platillo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!isRealMenu) {
                item {
                    Surface(
                        color = Color(0xFFFFF7ED),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = FoodSpecial, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Este es un menú de muestra. Agrega tus platillos reales con el botón \"Platillo\" y lo reemplazarán.",
                                fontSize = 11.sp,
                                color = Color(0xFF9A3412),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            if (isFoodStand) {
                // Modo simple para puestos: lista plana
                items(menuItems) { item ->
                    MenuItemCard(
                        item = item,
                        editable = isRealMenu,
                        onToggleAvailable = { saveMenuItem(item.copy(isAvailable = !item.isAvailable)) },
                        onEdit = { editingItem = item; showItemDialog = true },
                        onDelete = { viewModel.updateServiceMenu(selectedServiceId, realMenu.filter { it.id != item.id }) }
                    )
                }
            } else {
                // Modo restaurante: agrupado por categoría
                MenuCategories.forEach { category ->
                    val categoryItems = menuItems.filter { it.category == category }
                    if (categoryItems.isNotEmpty()) {
                        item {
                            Text(
                                if (category == "Especial") "ESPECIALIDADES DE LA CASA" else category.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Gray,
                                letterSpacing = 1.sp
                            )
                        }
                        items(categoryItems) { item ->
                            MenuItemCard(
                                item = item,
                                editable = isRealMenu,
                                onToggleAvailable = { saveMenuItem(item.copy(isAvailable = !item.isAvailable)) },
                                onEdit = { editingItem = item; showItemDialog = true },
                                onDelete = { viewModel.updateServiceMenu(selectedServiceId, realMenu.filter { it.id != item.id }) }
                            )
                        }
                    }
                }
            }

            // Agenda de comandas: fecha + KPIs
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = FoodAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Agenda de Comandas", fontWeight = FontWeight.Black, fontSize = 13.sp)
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        if (isFoodStand) "PEDIDOS DÍA" else "RESERVAS DÍA",
                                        fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp
                                    )
                                    Text(
                                        if (isFoodStand) foodOrders.size.toString() else activeBookings.size.toString(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("INGRESOS DÍA", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                                    Text(
                                        formatMxn(if (isFoodStand) foodRevenue else dailyRevenue),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Comandas (puestos de comida) vs libro de reservas (restaurantes)
            if (isFoodStand) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fastfood, contentDescription = null, tint = FoodAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Comandas del Día (${foodOrders.size})", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
                if (foodOrders.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Sin pedidos pagados para esta fecha. Aquí aparecerán las comandas en cuanto los clientes paguen.",
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(foodOrders) { order ->
                        FoodOrderCard(
                            order = order,
                            onAdvance = { next -> viewModel.updateOrderStatus(order.id, next) }
                        )
                    }
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = FoodAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Libro de Reservas (${activeBookings.size})", fontWeight = FontWeight.Black, fontSize = 13.sp)
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
                                "Sin reservaciones de mesa para esta fecha.",
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(activeBookings) { booking ->
                        TableBookingRow(booking)
                    }
                }
            }
        }
    }

    if (showItemDialog) {
        MenuItemDialog(
            existing = editingItem,
            onSave = { item ->
                saveMenuItem(item)
                showItemDialog = false
            },
            onDismiss = { showItemDialog = false }
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
private fun MenuItemDialog(
    existing: MenuItemFirestore?,
    onSave: (MenuItemFirestore) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var priceText by remember { mutableStateOf(existing?.price?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "Platillo Fuerte") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo platillo" else "Editar platillo", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Precio (MXN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = {
                            IconButton(onClick = { categoryMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        MenuCategories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    category = option
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        MenuItemFirestore(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            description = description.trim(),
                            price = priceText.toDoubleOrNull() ?: 0.0,
                            category = category,
                            isAvailable = existing?.isAvailable ?: true,
                            isRecommended = existing?.isRecommended ?: false
                        )
                    )
                },
                enabled = name.isNotBlank() && (priceText.toDoubleOrNull() ?: 0.0) > 0.0
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// Mismo fallback del MenuBoard web: si el servicio no tiene menú configurado,
// se genera uno de muestra a partir de los datos del servicio
private fun resolveMenu(service: ServiceFirestore?): List<MenuItemFirestore> {
    if (service == null) return emptyList()
    if (service.menu.isNotEmpty()) {
        return service.menu.mapIndexed { index, m ->
            m.copy(
                id = m.id.ifEmpty { (index + 1).toString() },
                name = m.name.ifEmpty { "Platillo sin nombre" },
                category = m.category.ifEmpty { "Platillo Fuerte" }
            )
        }
    }
    val isFoodStand = service.type == "FOOD_STAND"
    return listOf(
        MenuItemFirestore(
            id = "special-day",
            name = service.name,
            description = service.description.ifEmpty { "Nuestra especialidad de la casa preparada al momento." },
            price = service.price,
            category = "Especial",
            isAvailable = true
        ),
        MenuItemFirestore(
            id = "stub-starter",
            name = if (isFoodStand) "Tacos de Cortesía" else "Entrada Típica del Puerto",
            description = "Perfecto para empezar mientras se prepara tu orden.",
            price = (service.price * 0.3).takeIf { it > 0 } ?: 45.0,
            category = "Entrada",
            isAvailable = true
        ),
        MenuItemFirestore(
            id = "stub-drink",
            name = "Agua Fresca del Día",
            description = "Frutas tropicales de temporada e ingredientes 100% locales.",
            price = 30.0,
            category = "Bebida",
            isAvailable = true
        ),
        MenuItemFirestore(
            id = "stub-dessert",
            name = "Postre Casero de Guayaba",
            description = "Delicioso dulce tradicional cocinado a fuego lento.",
            price = (service.price * 0.4).takeIf { it > 0 } ?: 60.0,
            category = "Postre",
            isAvailable = false
        )
    )
}

@Composable
private fun MenuItemCard(
    item: MenuItemFirestore,
    editable: Boolean = false,
    onToggleAvailable: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val isSpecial = item.category == "Especial"
    Surface(
        color = when {
            isSpecial -> FoodSpecialBg
            !item.isAvailable -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isSpecial) Color(0xFFFCD34D) else Color(0xFFE9ECEF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (isSpecial) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = FoodSpecial, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        item.name,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1,
                        color = if (!item.isAvailable) Color.Gray else Color.Unspecified
                    )
                }
                Surface(
                    color = if (item.isAvailable) FoodAvailableBg else FoodSoldOutBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (item.isAvailable) "DISPONIBLE" else "AGOTADO",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = if (item.isAvailable) FoodAccent else FoodSoldOut
                    )
                }
            }
            if (item.description.isNotEmpty()) {
                Text(
                    item.description,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 15.sp,
                    maxLines = 2
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatMxn(item.price),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = if (isSpecial) FoodSpecial else FoodAccent
                )
                if (editable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = item.isAvailable,
                            onCheckedChange = { onToggleAvailable() },
                            modifier = Modifier.scale(0.7f),
                            colors = SwitchDefaults.colors(checkedTrackColor = FoodAccent)
                        )
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar platillo", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar platillo", tint = FoodSoldOut, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// Comanda de puesto de comida: productos, tipo de entrega y avance de estado
@Composable
private fun FoodOrderCard(
    order: BookingFirestore,
    onAdvance: (String) -> Unit
) {
    val isDelivery = order.fulfillmentType == "DELIVERY"
    val status = order.orderStatus.ifEmpty { "PREPARING" }
    val (statusLabel, statusColor, statusBg) = when (status) {
        "READY" -> Triple("LISTO", FoodAccent, FoodAvailableBg)
        "DELIVERED" -> Triple("ENTREGADO", Color.Gray, Color(0xFFEDEDED))
        else -> Triple("EN PREPARACIÓN", FoodSpecial, FoodSpecialBg)
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        order.travelerName.ifEmpty { order.travelerEmail },
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    if (order.confirmationCode.isNotEmpty()) {
                        Text(order.confirmationCode, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(color = statusBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                }
            }

            // Productos del pedido
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                order.orderItems.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.quantity}× ${item.name}", fontSize = 12.sp)
                        Text(formatMxn(item.unitPrice * item.quantity), fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // Entrega + total
            Surface(
                color = if (isDelivery) FoodSpecialBg else FoodAvailableBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isDelivery) Icons.Default.DeliveryDining else Icons.Default.Storefront,
                        contentDescription = null,
                        tint = if (isDelivery) FoodSpecial else FoodAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            if (isDelivery) "Entrega a domicilio" else "Recoge en el local",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isDelivery && order.deliveryAddress.isNotEmpty()) {
                            Text(order.deliveryAddress, fontSize = 10.sp, color = Color.Gray, maxLines = 2)
                        }
                        if (order.deliveryFee > 0) {
                            Text("Envío: ${formatMxn(order.deliveryFee)}", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total ${formatMxn(order.totalPrice)}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = FoodAccent)
                when (status) {
                    "PREPARING" -> Button(
                        onClick = { onAdvance("READY") },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FoodAccent),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("Marcar listo", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    "READY" -> Button(
                        onClick = { onAdvance("DELIVERED") },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FoodSpecial),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text(if (isDelivery) "Marcar entregado" else "Marcar recogido", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    else -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FoodAccent, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun TableBookingRow(booking: BookingFirestore) {
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
                Text(
                    "${booking.slots} lugares" + if (booking.departureTime.isNotEmpty()) " • ${booking.departureTime}" else "",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            Text(formatMxn(booking.totalPrice), fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}
