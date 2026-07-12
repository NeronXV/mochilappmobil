package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.data.OrderItemFirestore
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.ui.viewmodels.BookingViewModel
import com.mochilapp.mobile.ui.viewmodels.MarketplaceViewModel

// Producto ofertable del puesto: sale del menú real o, si no hay, del servicio mismo
private data class FoodProduct(
    val id: String,
    val name: String,
    val description: String,
    val price: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodOrderScreen(
    serviceId: String,
    travelerName: String,
    marketplaceViewModel: MarketplaceViewModel,
    bookingViewModel: BookingViewModel,
    onPaymentNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    var service by remember { mutableStateOf<ServiceFirestore?>(null) }
    val cart = remember { mutableStateMapOf<String, Int>() }
    var fulfillment by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val bookingResult by bookingViewModel.bookingResult.collectAsState()
    val bookingError by bookingViewModel.bookingError.collectAsState()

    LaunchedEffect(serviceId) {
        service = marketplaceViewModel.getServiceById(serviceId)
    }

    LaunchedEffect(bookingError) {
        bookingError?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            submitting = false
            bookingViewModel.clearBookingError()
        }
    }

    LaunchedEffect(bookingResult) {
        if (submitting) bookingResult?.let {
            // Consumir antes de navegar (mismo fix que BookingFlow)
            bookingViewModel.clearBookingResult()
            onPaymentNavigate(it)
        }
    }

    val products = remember(service) { resolveProducts(service) }
    // Opciones de entrega que ofrece el puesto (pickup viene por defecto en true)
    val options = remember(service) {
        buildList {
            if (service?.offersPickup != false) add("PICKUP")
            if (service?.offersDelivery == true) add("DELIVERY")
        }.ifEmpty { listOf("PICKUP") }
    }
    LaunchedEffect(options) {
        if (fulfillment !in options) fulfillment = options.first()
    }

    val subtotal = products.sumOf { (cart[it.id] ?: 0) * it.price }
    val deliveryFee = service?.deliveryFee ?: 0.0
    val fee = if (fulfillment == "DELIVERY") deliveryFee else 0.0
    val total = subtotal + fee
    val itemCount = products.sumOf { cart[it.id] ?: 0 }
    val canPay = itemCount > 0 &&
        (fulfillment != "DELIVERY" || address.isNotBlank()) &&
        !submitting

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Arma tu pedido", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 12.dp, shadowElevation = 24.dp, color = Color.White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            formatMxn(total),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        if (fee > 0) Text("Incluye envío ${formatMxn(fee)}", fontSize = 10.sp, color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            val items = products.mapNotNull { p ->
                                val qty = cart[p.id] ?: 0
                                if (qty > 0) OrderItemFirestore(name = p.name, quantity = qty, unitPrice = p.price) else null
                            }
                            val s = service
                            if (s != null && items.isNotEmpty()) {
                                submitting = true
                                bookingViewModel.createFoodOrder(
                                    serviceId = serviceId,
                                    serviceName = s.name,
                                    travelerName = travelerName,
                                    ownerEmail = s.ownerEmail,
                                    items = items,
                                    fulfillmentType = fulfillment,
                                    deliveryAddress = address,
                                    deliveryFee = deliveryFee
                                )
                            }
                        },
                        enabled = canPay,
                        modifier = Modifier.height(56.dp).weight(1.1f).padding(start = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        } else {
                            Text(if (itemCount > 0) "Pagar ($itemCount)" else "Pagar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (service == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(service?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black))

            // Productos con selector de cantidad
            Text("PRODUCTOS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
            products.forEach { product ->
                val qty = cart[product.id] ?: 0
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (product.description.isNotEmpty()) {
                                Text(product.description, fontSize = 11.sp, color = Color.Gray, maxLines = 2)
                            }
                            Text(formatMxn(product.price), fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (qty > 0) cart[product.id] = qty - 1 }, enabled = qty > 0) {
                                Icon(Icons.Default.RemoveCircle, contentDescription = "Quitar", tint = if (qty > 0) MaterialTheme.colorScheme.primary else Color.LightGray)
                            }
                            Text(qty.toString(), fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.widthIn(min = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick = { cart[product.id] = qty + 1 }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Agregar", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Tipo de entrega
            if (options.size > 1 || options.first() == "DELIVERY") {
                Text("ENTREGA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { option ->
                        val selected = fulfillment == option
                        val isDelivery = option == "DELIVERY"
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                            onClick = { fulfillment = option }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    if (isDelivery) Icons.Default.DeliveryDining else Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = if (selected) Color.White else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (isDelivery) "A domicilio" else "Recoger",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else Color.Black
                                )
                                if (isDelivery && deliveryFee > 0) {
                                    Text(
                                        "+${formatMxn(deliveryFee)}",
                                        fontSize = 10.sp,
                                        color = if (selected) Color.White.copy(alpha = 0.85f) else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                if (fulfillment == "DELIVERY") {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Dirección de entrega") },
                        placeholder = { Text("Calle, número, referencias…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

// Productos a ordenar: menú real disponible o, si no hay, el servicio como único producto
private fun resolveProducts(service: ServiceFirestore?): List<FoodProduct> {
    if (service == null) return emptyList()
    val realMenu = service.menu.filter { it.isAvailable && it.price > 0 }
    if (realMenu.isNotEmpty()) {
        return realMenu.mapIndexed { index, m ->
            FoodProduct(
                id = m.id.ifEmpty { "item-$index" },
                name = m.name.ifEmpty { "Producto" },
                description = m.description,
                price = m.price
            )
        }
    }
    return listOf(
        FoodProduct(
            id = "self",
            name = service.name,
            description = service.description,
            price = service.price
        )
    )
}
