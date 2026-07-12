package com.mochilapp.mobile.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochilapp.mobile.data.BookingFirestore
import com.mochilapp.mobile.data.OrderItemFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import com.mochilapp.mobile.utils.Telemetry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BookingViewModel(private val repository: FirebaseRepository, private val travelerEmail: String) : ViewModel() {
    // Email de la sesión de Auth: es el que validan las reglas de Firestore y
    // con el que se escriben las reservas (el del perfil puede diferir en mayúsculas)
    private val effectiveEmail: String
        get() = repository.getCurrentUserEmail() ?: travelerEmail

    private val _bookingResult = MutableStateFlow<String?>(null)
    val bookingResult: StateFlow<String?> = _bookingResult

    private val _bookingError = MutableStateFlow<String?>(null)
    val bookingError: StateFlow<String?> = _bookingError

    fun clearBookingError() {
        _bookingError.value = null
    }

    // Consumir el resultado tras navegar al pago: el ViewModel se comparte
    // entre pantallas y, si el id queda pegado, el siguiente flujo de reserva
    // catapulta al viajero a la pantalla de pago de la reserva ANTERIOR.
    fun clearBookingResult() {
        _bookingResult.value = null
    }

    private val _selectedDate = MutableStateFlow("")
    private val _currentServiceId = MutableStateFlow("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentBookings: StateFlow<List<BookingFirestore>> = combine(_currentServiceId, _selectedDate) { id, date ->
        Pair(id, date)
    }.flatMapLatest { (id, date) ->
        if (id.isEmpty() || date.isEmpty()) flowOf(emptyList())
        else repository.getBookingsByServiceAndDate(id, date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Todas las reservas activas del servicio consultado: hospedaje las cruza por
    // rango de noches para calcular disponibilidad real y evitar sobreventa.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val serviceBookings: StateFlow<List<BookingFirestore>> = _currentServiceId
        .flatMapLatest { id ->
            if (id.isEmpty()) flowOf(emptyList())
            else repository.getActiveBookingsForService(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myBookings: StateFlow<List<BookingFirestore>> = repository.getBookingsForUser(effectiveEmail)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Avisos vigentes para cruzar contra las reservas del viajero
    val activeNotices: StateFlow<List<com.mochilapp.mobile.data.NoticeFirestore>> =
        repository.getActiveNotices()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reservas recibidas como empresa (para el detalle de reserva del panel)
    val ownerBookings: StateFlow<List<BookingFirestore>> = repository.getBookingsForOwner(effectiveEmail)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateAvailabilityQuery(serviceId: String, date: String) {
        _currentServiceId.value = serviceId
        _selectedDate.value = date
    }

    fun createBooking(
        serviceId: String, 
        serviceName: String,
        travelerName: String, 
        date: String, 
        slots: Int, 
        totalPrice: Double, 
        ownerEmail: String,
        departureTime: String = "",
        checkOutDate: String = "",
        promoId: String = "",
        promoCode: String = "",
        discountPercent: Int = 0,
        discountAmount: Double = 0.0,
        originalTotal: Double = 0.0,
        // "PRIVADA" | "COLECTIVA": marcador de que esta app entiende la
        // semántica; el servidor lo confirma y calcula el cobro real
        modalidad: String = "COLECTIVA"
    ) {
        // Las fechas se guardan como yyyy-MM-dd, así que la comparación de strings es cronológica
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        if (date < today) {
            _bookingError.value = "No se pueden hacer reservas en fechas pasadas"
            return
        }
        if (checkOutDate.isNotEmpty() && checkOutDate <= date) {
            _bookingError.value = "La fecha de salida debe ser posterior a la de entrada"
            return
        }
        viewModelScope.launch {
            // Código de confirmación corto: 6 dígitos, fácil de dictar en persona
            val code = generateConfirmationCode()

            val booking = BookingFirestore(
                createdAt = System.currentTimeMillis(),
                serviceId = serviceId,
                serviceName = serviceName,
                travelerEmail = effectiveEmail,
                travelerName = travelerName,
                date = date,
                checkOutDate = checkOutDate,
                slots = slots,
                personas = slots,
                modalidad = modalidad,
                totalPrice = totalPrice,
                status = "PENDING",
                ownerEmail = ownerEmail,
                departureTime = departureTime,
                confirmationCode = code,
                promoId = promoId,
                promoCode = promoCode,
                discountPercent = discountPercent,
                discountAmount = discountAmount,
                originalTotal = originalTotal
            )
            try {
                val id = repository.addBooking(booking)
                _bookingResult.value = id
                Telemetry.logBookingCreated(serviceId, serviceName, slots, totalPrice, isFoodOrder = false)
            } catch (e: Exception) {
                android.util.Log.e("BookingViewModel", "Error creating booking", e)
                Telemetry.recordError("createBooking", e)
                _bookingError.value = if (e.message?.contains("PERMISSION_DENIED") == true) {
                    "No se pudo crear la reserva: permisos insuficientes. Intenta cerrar sesión y volver a entrar."
                } else {
                    "No se pudo crear la reserva. Revisa tu conexión e intenta de nuevo."
                }
            }
        }
    }

    // Pedido de puesto de comida: se cobra por orden (productos + cantidades),
    // con recoger o entrega a domicilio (+ deliveryFee). Reusa la misma reserva
    // y el mismo flujo de pago; el puesto lo despacha desde su panel.
    fun createFoodOrder(
        serviceId: String,
        serviceName: String,
        travelerName: String,
        ownerEmail: String,
        items: List<OrderItemFirestore>,
        fulfillmentType: String,
        deliveryAddress: String,
        deliveryFee: Double
    ) {
        if (items.isEmpty()) {
            _bookingError.value = "Agrega al menos un producto a tu pedido"
            return
        }
        if (fulfillmentType == "DELIVERY" && deliveryAddress.isBlank()) {
            _bookingError.value = "Indica la dirección de entrega"
            return
        }
        viewModelScope.launch {
            val code = generateConfirmationCode()
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            val subtotal = items.sumOf { it.unitPrice * it.quantity }
            val fee = if (fulfillmentType == "DELIVERY") deliveryFee else 0.0

            val booking = BookingFirestore(
                createdAt = System.currentTimeMillis(),
                serviceId = serviceId,
                serviceName = serviceName,
                travelerEmail = effectiveEmail,
                travelerName = travelerName,
                date = today,
                slots = items.sumOf { it.quantity },
                totalPrice = subtotal + fee,
                status = "PENDING",
                ownerEmail = ownerEmail,
                confirmationCode = code,
                orderItems = items,
                fulfillmentType = fulfillmentType,
                deliveryAddress = if (fulfillmentType == "DELIVERY") deliveryAddress.trim() else "",
                deliveryFee = fee,
                orderStatus = "PREPARING"
            )
            try {
                val id = repository.addBooking(booking)
                _bookingResult.value = id
                Telemetry.logBookingCreated(
                    serviceId, serviceName, booking.slots, booking.totalPrice, isFoodOrder = true
                )
            } catch (e: Exception) {
                android.util.Log.e("BookingViewModel", "Error creating food order", e)
                Telemetry.recordError("createFoodOrder", e)
                _bookingError.value = if (e.message?.contains("PERMISSION_DENIED") == true) {
                    "No se pudo crear el pedido: permisos insuficientes. Intenta cerrar sesión y volver a entrar."
                } else {
                    "No se pudo crear el pedido. Revisa tu conexión e intenta de nuevo."
                }
            }
        }
    }

    // 6 dígitos (100000-999999): el viajero lo dicta o muestra al llegar y la
    // empresa lo teclea en "Verificar Ticket"; nunca se le muestra a la empresa.
    private fun generateConfirmationCode(): String =
        (100000..999999).random().toString()

    fun updateStatus(bookingId: String, status: String, userUid: String) {
        viewModelScope.launch {
            val updateData = mutableMapOf<String, Any>(
                "status" to status
            )
            val now = System.currentTimeMillis()
            if (status == "CHECKED_IN") {
                updateData["checkedInAt"] = now
                updateData["checkedInBy"] = userUid
            } else if (status == "COMPLETED") {
                updateData["completedAt"] = now
                updateData["completedBy"] = userUid
            }
            repository.updateBookingFields(bookingId, updateData)
        }
    }

    // Check-in del viajero en el lugar ("Vive"). El gate de proximidad GPS lo
    // hace la pantalla; aquí solo registramos la visita. Una Cloud Function
    // acredita los MochiPuntos del Pasaporte.
    fun travelerCheckIn(bookingId: String) {
        viewModelScope.launch {
            repository.updateBookingFields(
                bookingId,
                mapOf("travelerCheckedInAt" to System.currentTimeMillis())
            )
        }
    }

    // El servidor calcula el monto y los datos del cobro leyendo la reserva de
    // Firestore: el cliente solo dice QUÉ reserva paga, nunca CUÁNTO.
    suspend fun createPaymentIntent(bookingId: String): Map<String, Any>? {
        return repository.createPaymentIntent(mapOf("bookingId" to bookingId))
    }
}
