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
}
