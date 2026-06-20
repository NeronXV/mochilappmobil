package com.mochilapp.mobile.data

import com.google.firebase.firestore.DocumentId

data class UserFirestore(
    @DocumentId val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "TRAVELER",
    val companyType: String = "NONE",
    val profileImageUrl: String = "",
    val bio: String = "",
    val mochiPoints: Long = 0,
    val passportLevel: String = "Explorador",
    val badges: List<String> = emptyList(),
    // IDs de servicios que el viajero guardó en "Mis Aventuras"
    val savedServices: List<String> = emptyList(),
    val businessName: String = "",
    val businessDescription: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val businessLocation: String = "",
    val rfc: String = "",
    val rnt: String = "",
    val businessVerified: Boolean = false,
    val status: String = "PENDING",
    val checkIn: String = "",
    val checkOut: String = "",
    val meetingPoint: String = "",
    val fcmToken: String = ""
)

data class MenuItemFirestore(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val category: String = "",
    val isAvailable: Boolean = true,
    val isRecommended: Boolean = false
)

data class RoomFirestore(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val status: String = "AVAILABLE",
    val capacity: Int = 1
)

data class ServiceFirestore(
    @DocumentId val id: String = "",
    val ownerEmail: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val type: String = "OTHER",
    val location: String = "",
    val imageUrl: String = "",
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val capacity: Int = 0,
    val departureTimes: List<String> = emptyList(),
    val businessHours: Map<String, String> = emptyMap(),
    val isOpen: Boolean = true,
    val amenities: List<String> = emptyList(),
    val rules: List<String> = emptyList(),
    val routeName: String = "",
    val origin: String = "",
    val destination: String = "",
    val vehicleName: String = "",
    val driverName: String = "",
    val guideName: String = "",
    val meetingPoint: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val menu: List<MenuItemFirestore> = emptyList(),
    val rooms: List<RoomFirestore> = emptyList(),
    val isVisible: Boolean = true,
    val isRecommended: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    // Puesto de comida: el pedido se cobra por orden (no por persona) y se
    // entrega para recoger o a domicilio. deliveryFee se suma cuando es domicilio.
    val offersPickup: Boolean = true,
    val offersDelivery: Boolean = false,
    val deliveryFee: Double = 0.0
)

// Línea de un pedido de puesto de comida: producto, cantidad y precio unitario.
data class OrderItemFirestore(
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0
)

data class BookingFirestore(
    @DocumentId val id: String = "",
    val serviceId: String = "",
    val travelerEmail: String = "",
    val travelerName: String = "",
    val date: String = "",
    // Para hospedaje: fecha de salida (check-out). El campo `date` es la entrada (check-in).
    val checkOutDate: String = "",
    val departureTime: String = "",
    val slots: Int = 1,
    val totalPrice: Double = 0.0,
    val status: String = "PENDING",
    val ownerEmail: String = "",
    val serviceName: String = "",
    val confirmationCode: String = "",
    val promoId: String = "",
    val promoCode: String = "",
    val discountPercent: Int = 0,
    val discountAmount: Double = 0.0,
    val originalTotal: Double = 0.0,
    val ticketCode: String = "",
    val checkedInAt: Long = 0L,
    val checkedInBy: String = "",
    val completedAt: Long = 0L,
    val completedBy: String = "",
    // Check-in del viajero ("Vive"): distinto al checkedInAt operativo que
    // marca la empresa. >0 cuando el viajero registra su visita en el lugar.
    val travelerCheckedInAt: Long = 0L,
    // Puesto de comida: el pedido lleva productos por orden, tipo de entrega y un
    // estado de preparación propio (orderStatus) distinto al status de pago.
    val orderItems: List<OrderItemFirestore> = emptyList(),
    val fulfillmentType: String = "",      // "PICKUP" | "DELIVERY"
    val deliveryAddress: String = "",
    val deliveryFee: Double = 0.0,
    val orderStatus: String = ""           // "PREPARING" | "READY" | "DELIVERED"
)

data class PostFirestore(
    @DocumentId val id: String = "",
    val authorEmail: String = "",
    val authorName: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val likes: Int = 0,
    val likedBy: List<String> = emptyList(),
    val linkedServiceId: String? = null // Allow booking from post
)

// Historia efímera de una empresa (estilo stories): visible 24h. El viajero
// la ve en un visor a pantalla completa al tocar el círculo de la empresa.
data class StoryFirestore(
    @DocumentId val id: String = "",
    val ownerEmail: String = "",
    val companyName: String = "",
    val imageUrl: String = "",
    val caption: String = "",
    val serviceId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0L
)

data class CommentFirestore(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ReviewFirestore(
    @DocumentId val id: String = "",
    val serviceId: String = "",
    val authorName: String = "",
    // Necesario para que la Cloud Function de puntos sepa a quién acreditar
    val authorEmail: String = "",
    val rating: Int = 5,
    val comment: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class PromoFirestore(
    @DocumentId val id: String = "",
    val serviceId: String = "",
    val ownerEmail: String = "",
    val companyName: String = "",
    val content: String = "",
    val discount: String = "",
    val discountPercent: Int = 0,
    val promoCode: String = "",
    val isActive: Boolean = true,
    val expiresAt: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

// Aviso operativo del negocio hacia los viajeros (distinto a una promo:
// informa, no vende). Con date apunta a las reservas de ese día; sin date
// es un aviso general visible en el detalle del servicio
data class NoticeFirestore(
    @DocumentId val id: String = "",
    val ownerEmail: String = "",
    val companyName: String = "",
    val serviceId: String = "",
    val serviceName: String = "",
    val date: String = "",
    val message: String = "",
    val severity: String = "INFO", // INFO | IMPORTANT | URGENT
    val isActive: Boolean = true,
    val expiresAt: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
