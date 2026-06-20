package com.mochilapp.mobile.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data object Splash : Destination

    @Serializable
    data object LanguageSelection : Destination

    @Serializable
    data object Login : Destination

    @Serializable
    data object Registration : Destination

    @Serializable
    data object TravelerDashboard : Destination

    @Serializable
    data object CompanyDashboard : Destination

    @Serializable
    data object AddService : Destination

    @Serializable
    data class ServiceDetail(val serviceId: String) : Destination

    @Serializable
    data class BookingFlow(val serviceId: String) : Destination

    // Pedido de puesto de comida (carrito de productos), alterno a BookingFlow
    @Serializable
    data class FoodOrder(val serviceId: String) : Destination

    @Serializable
    data class Payment(val bookingId: String) : Destination

    @Serializable
    data object SocialFeed : Destination

    @Serializable
    data object CreatePost : Destination

    @Serializable
    data object TourismMap : Destination

    @Serializable
    data object AiAssistant : Destination

    @Serializable
    data object BookingHistory : Destination

    @Serializable
    data object UserProfile : Destination

    @Serializable
    data class BookingDetail(val bookingId: String) : Destination

    @Serializable
    data class BookingDetailCompany(val bookingId: String) : Destination

    @Serializable
    data object Search : Destination

    @Serializable
    data object MapPicker : Destination

    @Serializable
    data object BoatTourModule : Destination

    @Serializable
    data object LodgingModule : Destination

    @Serializable
    data object PropertyRentalModule : Destination

    @Serializable
    data object RestaurantModule : Destination

    @Serializable
    data object TourAgencyModule : Destination

    @Serializable
    data object TransportModule : Destination
}
