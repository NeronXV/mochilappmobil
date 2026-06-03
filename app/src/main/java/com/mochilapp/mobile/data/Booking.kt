package com.mochilapp.mobile.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BookingStatus {
    PENDING,
    PAID,
    CANCELLED
}

@Entity(tableName = "bookings")
data class Booking(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serviceId: Int,
    val travelerEmail: String,
    val date: String,
    val slots: Int,
    val totalPrice: Double,
    val status: BookingStatus = BookingStatus.PENDING
)
