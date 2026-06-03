package com.mochilapp.mobile.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole {
    TRAVELER,
    COMPANY
}

enum class CompanyType {
    HOTEL,
    HOSTEL,
    PROPERTY_RENTAL,
    RESTAURANT,
    FOOD_STAND,
    BOAT_TOUR,
    TOUR_AGENCY,
    TRANSPORT,
    OTHER,
    NONE
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String,
    val name: String,
    val role: UserRole,
    val companyType: CompanyType = CompanyType.NONE
)
