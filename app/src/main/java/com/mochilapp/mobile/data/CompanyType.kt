package com.mochilapp.mobile.data

// Giros de negocio de la plataforma. Vive como string en users.companyType
// y services.type (Firestore).
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
