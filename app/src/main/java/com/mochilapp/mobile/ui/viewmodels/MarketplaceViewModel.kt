package com.mochilapp.mobile.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochilapp.mobile.data.PromoFirestore
import com.mochilapp.mobile.data.ReviewFirestore
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.mochilapp.mobile.data.CompanyType

enum class PriceRange { ALL, ECONOMY, MEDIUM, PREMIUM }

class MarketplaceViewModel(private val repository: FirebaseRepository) : ViewModel() {
    private val _selectedType = MutableStateFlow<String?>(null)
    val selectedType: StateFlow<String?> = _selectedType

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _priceRange = MutableStateFlow(PriceRange.ALL)
    val priceRange: StateFlow<PriceRange> = _priceRange

    private val _guestsCount = MutableStateFlow(1)
    val guestsCount: StateFlow<Int> = _guestsCount

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate

    private val _activePromo = MutableStateFlow<PromoFirestore?>(null)
    val activePromo: StateFlow<PromoFirestore?> = _activePromo

    private val _isSeeding = MutableStateFlow(false)
    val isSeeding: StateFlow<Boolean> = _isSeeding

    val activePromos: StateFlow<List<PromoFirestore>> = repository.getActivePromos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val services: StateFlow<List<ServiceFirestore>> = combine(
        _selectedType, _searchQuery, _priceRange, _guestsCount
    ) { type, query, price, guests ->
        FilterState(type, query, price, guests)
    }.flatMapLatest { filter ->
        val flow = if (filter.type == null) repository.getAllServices()
        else repository.getServicesByType(filter.type)
        
        flow.map { list ->
            list.filter { it.isVisible }
                .filter { if (filter.query.isBlank()) true else it.matchesQuery(filter.query) }
                .filter { it.matchesPriceRange(filter.priceRange) }
                .filter { it.matchesGuests(filter.guests) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recommendedServices: StateFlow<List<ServiceFirestore>> = repository.getAllServices()
        .map { list ->
            list.filter { it.isVisible }
                .sortedWith(compareByDescending<ServiceFirestore> { it.isRecommended }
                    .thenByDescending { it.rating }
                    .thenByDescending { it.reviewCount })
                .take(6)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class FilterState(
        val type: String?,
        val query: String,
        val priceRange: PriceRange,
        val guests: Int
    )

    private fun ServiceFirestore.matchesQuery(query: String): Boolean {
        return name.contains(query, ignoreCase = true) || 
               location.contains(query, ignoreCase = true) ||
               description.contains(query, ignoreCase = true)
    }

    private fun ServiceFirestore.matchesPriceRange(range: PriceRange): Boolean {
        return when(range) {
            PriceRange.ALL -> true
            PriceRange.ECONOMY -> price <= 500
            PriceRange.MEDIUM -> price in 501.0..1500.0
            PriceRange.PREMIUM -> price > 1500
        }
    }

    private fun ServiceFirestore.matchesGuests(count: Int): Boolean {
        return if (capacity > 0) capacity >= count else true
    }

    fun selectType(type: String?) {
        _selectedType.value = type
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updatePriceRange(range: PriceRange) {
        _priceRange.value = range
    }

    fun updateGuests(count: Int) {
        _guestsCount.value = count
    }

    fun applyPromo(promo: PromoFirestore?) {
        _activePromo.value = promo
    }

    fun updateDate(date: String?) {
        _selectedDate.value = date
    }

    fun clearFilters() {
        _selectedType.value = null
        _searchQuery.value = ""
        _priceRange.value = PriceRange.ALL
        _guestsCount.value = 1
        _selectedDate.value = null
        _activePromo.value = null
    }

    fun seedSampleData() {
        viewModelScope.launch {
            _isSeeding.value = true
            val samples = listOf(
                ServiceFirestore(
                    name = "Tour en Catamarán al Atardecer",
                    description = "Disfruta de una vista increíble del Caribe con barra libre y música a bordo. Ideal para parejas y grupos de amigos.",
                    price = 85.0,
                    location = "Cancún, México",
                    type = "BOAT_TOUR",
                    imageUrl = "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?q=80&w=1000&auto=format&fit=crop"
                ),
                ServiceFirestore(
                    name = "Hotel Boutique Selva Maya",
                    description = "Una experiencia de lujo sustentable en medio de la selva. Incluye desayuno artesanal y acceso privado a cenote.",
                    price = 220.0,
                    location = "Tulum, México",
                    type = "HOTEL",
                    imageUrl = "https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?q=80&w=1000&auto=format&fit=crop"
                ),
                ServiceFirestore(
                    name = "Cata de Mezcal Artesanal",
                    description = "Aprende el proceso de destilación y prueba 5 variedades de mezcales premium acompañados de maridaje local.",
                    price = 45.0,
                    location = "Oaxaca, México",
                    type = "RESTAURANT",
                    imageUrl = "https://images.unsplash.com/photo-1527281405159-45512743db13?q=80&w=1000&auto=format&fit=crop"
                ),
                ServiceFirestore(
                    name = "Expedición Volcán Acatenango",
                    description = "Caminata épica de dos días para ver las erupciones del Volcán de Fuego desde la cima. Incluye guía y equipo.",
                    price = 95.0,
                    location = "Antigua, Guatemala",
                    type = "OTHER",
                    imageUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?q=80&w=1000&auto=format&fit=crop"
                )
            )
            
            samples.forEach { repository.addService(it) }
            _isSeeding.value = false
        }
    }

    suspend fun getServiceById(id: String): ServiceFirestore? {
        return repository.getServiceById(id)
    }

    fun getReviewsForService(serviceId: String): Flow<List<ReviewFirestore>> =
        repository.getReviewsForService(serviceId)

    fun addReview(serviceId: String, authorName: String, rating: Int, comment: String) {
        viewModelScope.launch {
            val review = ReviewFirestore(
                serviceId = serviceId,
                authorName = authorName,
                rating = rating,
                comment = comment
            )
            repository.addReview(review)
        }
    }
}
