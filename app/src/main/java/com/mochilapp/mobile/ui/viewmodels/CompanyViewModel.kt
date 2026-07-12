package com.mochilapp.mobile.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochilapp.mobile.data.BookingFirestore
import com.mochilapp.mobile.data.CompanyType
import com.mochilapp.mobile.data.PromoFirestore
import com.mochilapp.mobile.data.RoomFirestore
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.data.StoryFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Borrador del formulario de nuevo servicio. Vive en el ViewModel para que
// los datos sobrevivan la navegación al mapa y de regreso.
data class ServiceDraft(
    val name: String = "",
    val description: String = "",
    val price: String = "",
    val location: String = "",
    val type: CompanyType = CompanyType.HOTEL,
    val imageUri: Uri? = null,
    val capacity: String = "",
    // Modalidad de venta (spec privada/colectiva). En PRIVADA, `capacity` es
    // la capacidad máxima del grupo y `price` deja de ser campo de captura
    // (se guarda precioBase como price legado para apps viejas).
    val modalidad: String = "COLECTIVA",
    val precioBase: String = "",
    val personasIncluidas: String = "",
    val precioPersonaExtra: String = "",
    val departureTimes: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    // Hospedaje: lista de habitaciones/camas configuradas (alimenta la distribución de planta)
    val rooms: List<RoomFirestore> = emptyList(),
    val amenities: String = "",
    val rules: String = "",
    val routeName: String = "",
    val origin: String = "",
    val destination: String = "",
    val vehicleName: String = "",
    val driverName: String = "",
    val guideName: String = "",
    val businessHours: String = "",
    val isOpen: Boolean = true,
    val address: String = "",
    // Puesto de comida: opciones de entrega y costo de envío a domicilio
    val offersPickup: Boolean = true,
    val offersDelivery: Boolean = false,
    val deliveryFee: String = "",
    // null = creando servicio nuevo; con valor = editando ese servicio
    val editingServiceId: String? = null,
    val existingImageUrl: String = ""
)

class CompanyViewModel(
    private val repository: FirebaseRepository,
    private val ownerEmail: String,
    private val ownerUid: String
) : ViewModel() {
    // Email de la sesión de Auth (siempre en minúsculas): es con el que se
    // escriben servicios y reservas. El del perfil puede diferir en mayúsculas
    // y dejaba el panel vacío aunque el marketplace sí mostrara los servicios.
    private val queryEmail: String = repository.getCurrentUserEmail() ?: ownerEmail

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _serviceError = MutableStateFlow<String?>(null)
    val serviceError: StateFlow<String?> = _serviceError

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    // Temporary storage for location selection
    private val _selectedLat = MutableStateFlow(0.0)
    val selectedLat: StateFlow<Double> = _selectedLat

    private val _selectedLng = MutableStateFlow(0.0)
    val selectedLng: StateFlow<Double> = _selectedLng

    fun updateCoordinates(lat: Double, lng: Double) {
        _selectedLat.value = lat
        _selectedLng.value = lng
    }

    fun clearCoordinates() {
        _selectedLat.value = 0.0
        _selectedLng.value = 0.0
    }

    private val _serviceDraft = MutableStateFlow(ServiceDraft())
    val serviceDraft: StateFlow<ServiceDraft> = _serviceDraft

    fun updateServiceDraft(draft: ServiceDraft) {
        _serviceDraft.value = draft
    }

    fun clearServiceDraft() {
        _serviceDraft.value = ServiceDraft()
        clearCoordinates()
    }

    // Incluye servicios ocultos para que la empresa pueda gestionarlos;
    // el marketplace del viajero ya filtra por isVisible por su cuenta
    val myServices: StateFlow<List<ServiceFirestore>> = repository.getServicesByOwner(queryEmail)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myBookings: StateFlow<List<BookingFirestore>> = repository.getBookingsForOwner(queryEmail)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myPromos: StateFlow<List<PromoFirestore>> = repository.getPromosByOwner(queryEmail)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePromosCount: StateFlow<Int> = myPromos.map { list ->
        list.count { it.isActive }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalRevenue: StateFlow<Double> = myBookings.map { list ->
        list.filter { it.status == "PAID" }.sumOf { it.totalPrice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Ingresos del mes: reservas PAID cuya fecha (YYYY-MM-DD) cae en el mes actual
    val monthlyRevenue: StateFlow<Double> = myBookings.map { list ->
        val monthPrefix = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(java.util.Date())
        list.filter { it.status == "PAID" && it.date.startsWith(monthPrefix) }.sumOf { it.totalPrice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val paidBookingsCount: StateFlow<Int> = myBookings.map { list ->
        list.count { it.status == "PAID" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cancelledBookingsCount: StateFlow<Int> = myBookings.map { list ->
        list.count { it.status == "CANCELLED" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentBookings: StateFlow<List<BookingFirestore>> = myBookings.map { list ->
        list.sortedByDescending { it.date }.take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Filtros de la pestaña Reservas ---
    private val _bookingStatusFilter = MutableStateFlow("ALL")
    val bookingStatusFilter: StateFlow<String> = _bookingStatusFilter

    private val _bookingSearchQuery = MutableStateFlow("")
    val bookingSearchQuery: StateFlow<String> = _bookingSearchQuery

    fun setBookingStatusFilter(status: String) { _bookingStatusFilter.value = status }
    fun setBookingSearchQuery(query: String) { _bookingSearchQuery.value = query }

    val filteredBookings: StateFlow<List<BookingFirestore>> =
        combine(myBookings, _bookingStatusFilter, _bookingSearchQuery) { list, status, query ->
            list.filter { booking ->
                val matchesStatus = status == "ALL" || booking.status == status
                val matchesSearch = query.isBlank() ||
                    booking.travelerEmail.contains(query, ignoreCase = true) ||
                    booking.travelerName.contains(query, ignoreCase = true) ||
                    booking.serviceName.contains(query, ignoreCase = true)
                matchesStatus && matchesSearch
            }.sortedByDescending { it.date }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingBookingsCount: StateFlow<Int> = myBookings.map { list ->
        list.count { it.status == "PENDING" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayBookingsCount: StateFlow<Int> = myBookings.map { list ->
        // Assuming simple date match for today (YYYY-MM-DD)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        list.count { it.date == today && it.status != "CANCELLED" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun addService(service: ServiceFirestore, imageUri: Uri? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _serviceError.value = null
            try {
                repository.addService(
                    service.copy(
                        ownerEmail = queryEmail,
                        ownerUid = ownerUid,
                        isVisible = true
                    ),
                    imageUri
                )
                onComplete()
            } catch (e: Exception) {
                _serviceError.value = e.localizedMessage ?: "No se pudo publicar el servicio."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteService(id: String) {
        viewModelScope.launch {
            // Updated to use Soft Delete instead of physical delete
            repository.updateServiceVisibility(id, false)
        }
    }

    // Aforo (lugares) de un servicio, editable desde el módulo de embarcaciones
    fun updateServiceCapacity(id: String, capacity: Int) {
        viewModelScope.launch {
            try {
                repository.updateServiceCapacity(id, capacity)
            } catch (_: Exception) {
                // El listener de myServices refleja el estado real si falla
            }
        }
    }

    fun setServiceVisibility(id: String, isVisible: Boolean) {
        viewModelScope.launch {
            repository.updateServiceVisibility(id, isVisible)
        }
    }

    // Número de Firestore → texto de campo de captura ("8400", "850.5", "")
    private fun numToInput(n: Double): String = when {
        n <= 0.0 -> ""
        n % 1.0 == 0.0 -> n.toInt().toString()
        else -> n.toString()
    }

    // Prepara el borrador con los datos de un servicio existente para editarlo
    fun startEditingService(service: ServiceFirestore) {
        val esPrivado = service.modalidad == "PRIVADA"
        _serviceDraft.value = ServiceDraft(
            name = service.name,
            description = service.description,
            price = numToInput(service.price),
            location = service.location,
            type = runCatching { CompanyType.valueOf(service.type) }.getOrDefault(CompanyType.HOTEL),
            capacity = if (service.capacity > 0) service.capacity.toString() else "",
            modalidad = if (esPrivado) "PRIVADA" else "COLECTIVA",
            precioBase = if (esPrivado) numToInput((service.pricing["precioBase"] as? Number)?.toDouble() ?: 0.0) else "",
            personasIncluidas = if (esPrivado) numToInput((service.pricing["personasIncluidas"] as? Number)?.toDouble() ?: 0.0) else "",
            precioPersonaExtra = if (esPrivado) numToInput((service.pricing["precioPersonaExtra"] as? Number)?.toDouble() ?: 0.0) else "",
            departureTimes = service.departureTimes.joinToString(", "),
            checkIn = service.checkIn,
            checkOut = service.checkOut,
            rooms = service.rooms,
            amenities = service.amenities.joinToString(", "),
            rules = service.rules.joinToString(", "),
            routeName = service.routeName,
            origin = service.origin,
            destination = service.destination,
            vehicleName = service.vehicleName,
            driverName = service.driverName,
            guideName = service.guideName,
            businessHours = service.businessHours["general"] ?: "",
            isOpen = service.isOpen,
            address = service.address,
            offersPickup = service.offersPickup,
            offersDelivery = service.offersDelivery,
            deliveryFee = if (service.deliveryFee % 1.0 == 0.0) service.deliveryFee.toInt().toString() else service.deliveryFee.toString(),
            editingServiceId = service.id,
            existingImageUrl = service.imageUrl
        )
        updateCoordinates(service.latitude, service.longitude)
    }

    // Limpia un borrador de edición pendiente antes de crear un servicio nuevo
    fun startNewService() {
        if (_serviceDraft.value.editingServiceId != null) clearServiceDraft()
    }

    fun updateService(id: String, service: ServiceFirestore, imageUri: Uri? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _serviceError.value = null
            try {
                repository.updateService(id, service, imageUri)
                onComplete()
            } catch (e: Exception) {
                _serviceError.value = e.localizedMessage ?: "No se pudo guardar el servicio."
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Avisos operativos para viajeros ---
    val myNotices: StateFlow<List<com.mochilapp.mobile.data.NoticeFirestore>> =
        repository.getNoticesByOwner(queryEmail)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendNotice(
        serviceId: String,
        serviceName: String,
        companyName: String,
        date: String,
        message: String,
        severity: String
    ) {
        viewModelScope.launch {
            try {
                repository.addNotice(
                    com.mochilapp.mobile.data.NoticeFirestore(
                        ownerEmail = queryEmail,
                        companyName = companyName,
                        serviceId = serviceId,
                        serviceName = serviceName,
                        date = date,
                        message = message,
                        severity = severity,
                        isActive = true,
                        // Aviso con fecha caduca al final de ese día; general dura 7 días
                        expiresAt = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {
                // El listener de myNotices refleja el estado real
            }
        }
    }

    fun deactivateNotice(id: String) {
        viewModelScope.launch {
            repository.deactivateNotice(id)
        }
    }

    // Reemplaza el menú del servicio gastronómico (alta/edición/baja de platillos)
    fun updateServiceMenu(serviceId: String, menu: List<com.mochilapp.mobile.data.MenuItemFirestore>) {
        viewModelScope.launch {
            try {
                repository.updateServiceMenu(serviceId, menu)
            } catch (_: Exception) {
                // El listener de myServices restaura el estado real si falla
            }
        }
    }

    // Avanza el estado de preparación de una comanda de puesto de comida
    // (PREPARING → READY → DELIVERED). No toca el status de pago.
    fun updateOrderStatus(bookingId: String, orderStatus: String) {
        viewModelScope.launch {
            repository.updateBookingFields(bookingId, mapOf("orderStatus" to orderStatus))
        }
    }

    // Check-in de una reserva verificada por código de ticket
    fun checkInBooking(bookingId: String) {
        viewModelScope.launch {
            repository.updateBookingFields(bookingId, mapOf(
                "status" to "CHECKED_IN",
                "checkedInAt" to System.currentTimeMillis(),
                "checkedInBy" to ownerUid
            ))
        }
    }

    fun sendFlashPromo(content: String, discount: String, companyName: String, serviceId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val promo = PromoFirestore(
                serviceId = serviceId,
                ownerEmail = queryEmail,
                companyName = companyName,
                content = content,
                discount = discount,
                discountPercent = discount.filter { it.isDigit() }.toIntOrNull() ?: 0,
                promoCode = "FLASH-${java.util.UUID.randomUUID().toString().take(4).uppercase()}",
                isActive = true,
                timestamp = now,
                expiresAt = now + 24 * 60 * 60 * 1000L // promo relámpago: vigencia 24h
            )
            repository.addPromo(promo)
        }
    }

    // Publica una historia efímera (24h) visible en el círculo de la empresa.
    fun addStory(imageUri: Uri, caption: String, companyName: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val now = System.currentTimeMillis()
                val story = StoryFirestore(
                    ownerEmail = queryEmail,
                    companyName = companyName,
                    caption = caption,
                    timestamp = now,
                    expiresAt = now + 24 * 60 * 60 * 1000L
                )
                repository.addStory(story, imageUri)
                onComplete()
            } catch (e: Exception) {
                _serviceError.value = e.localizedMessage ?: "No se pudo publicar la historia."
            } finally {
                _isLoading.value = false
            }
        }
    }
}
