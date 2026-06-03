package com.mochilapp.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings WHERE travelerEmail = :email")
    fun getBookingsForTraveler(email: String): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE serviceId IN (SELECT id FROM services WHERE ownerEmail = :ownerEmail)")
    fun getBookingsForOwner(ownerEmail: String): Flow<List<Booking>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: Booking): Long

    @Query("UPDATE bookings SET status = :status WHERE id = :id")
    suspend fun updateBookingStatus(id: Int, status: BookingStatus)
}
