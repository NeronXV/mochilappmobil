package com.mochilapp.mobile.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochilapp.mobile.data.BookingFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BookingViewModel(private val repository: FirebaseRepository, private val travelerEmail: String) : ViewModel() {
    private val _bookingResult = MutableStateFlow<String?>(null)
    val bookingResult: StateFlow<String?> = _bookingResult

    private val _selectedDate = MutableStateFlow("")
    private val _currentServiceId = MutableStateFlow("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentBookings: StateFlow<List<BookingFirestore>> = combine(_currentServiceId, _selectedDate) { id, date ->
        Pair(id, date)
    }.flatMapLatest { (id, date) ->
        if (id.isEmpty() || date.isEmpty()) flowOf(emptyList())
        else repository.getBookingsByServiceAndDate(id, date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myBookings: StateFlow<List<BookingFirestore>> = repository.getBookingsForUser(travelerEmail)
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
        promoId: String = "",
        promoCode: String = "",
        discountPercent: Int = 0,
        discountAmount: Double = 0.0,
        originalTotal: Double = 0.0
    ) {
        viewModelScope.launch {
            // Generate a professional confirmation code MOCHI-XXXXXX
            val randomPart = java.util.UUID.randomUUID().toString().take(6).uppercase()
            val code = "MOCHI-$randomPart"
            
            val booking = BookingFirestore(
                serviceId = serviceId,
                serviceName = serviceName,
                travelerEmail = travelerEmail,
                travelerName = travelerName,
                date = date,
                slots = slots,
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
            val id = repository.addBooking(booking)
            _bookingResult.value = id
        }
    }

    fun confirmPayment(bookingId: String) {
        viewModelScope.launch {
            repository.updateBookingStatus(bookingId, "PAID")
        }
    }

    suspend fun createPaymentIntent(
        bookingId: String,
        amount: Double,
        currency: String = "mxn",
        serviceId: String,
        ownerEmail: String,
        travelerEmail: String,
        promoCode: String = "",
        discountAmount: Double = 0.0
    ): Map<String, Any>? {
        val data = mapOf(
            "bookingId" to bookingId,
            "amount" to (amount * 100).toInt(), // Cents
            "currency" to currency,
            "serviceId" to serviceId,
            "ownerEmail" to ownerEmail,
            "travelerEmail" to travelerEmail,
            "promoCode" to promoCode,
            "discountAmount" to discountAmount
        )
        return repository.createPaymentIntent(data)
    }
}
