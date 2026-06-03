package com.mochilapp.mobile.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochilapp.mobile.data.BookingFirestore
import com.mochilapp.mobile.data.PromoFirestore
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CompanyViewModel(
    private val repository: FirebaseRepository, 
    private val ownerEmail: String,
    private val ownerUid: String
) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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

    val myServices: StateFlow<List<ServiceFirestore>> = repository.getServicesByOwner(ownerEmail)
        .map { list -> list.filter { it.isVisible } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myBookings: StateFlow<List<BookingFirestore>> = repository.getBookingsForOwner(ownerEmail)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalRevenue: StateFlow<Double> = myBookings.map { list ->
        list.filter { it.status == "PAID" }.sumOf { it.totalPrice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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
            try {
                repository.addService(
                    service.copy(
                        ownerEmail = ownerEmail,
                        ownerUid = ownerUid,
                        isVisible = true
                    ), 
                    imageUri
                )
                onComplete()
            } catch (e: Exception) {
                // Error handling
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

    fun sendFlashPromo(content: String, discount: String, companyName: String) {
        viewModelScope.launch {
            val promo = PromoFirestore(
                ownerEmail = ownerEmail,
                companyName = companyName,
                content = content,
                discount = discount,
                isActive = true,
                timestamp = System.currentTimeMillis()
            )
            repository.addPromo(promo)
        }
    }
}
