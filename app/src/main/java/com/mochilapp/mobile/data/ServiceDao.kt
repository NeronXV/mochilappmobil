package com.mochilapp.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services")
    fun getAllServices(): Flow<List<Service>>

    @Query("SELECT * FROM services WHERE type = :type")
    fun getServicesByType(type: CompanyType): Flow<List<Service>>

    @Query("SELECT * FROM services WHERE ownerEmail = :email")
    fun getServicesByOwner(email: String): Flow<List<Service>>

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun getServiceById(id: Int): Service?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: Service)

    @Query("DELETE FROM services WHERE id = :id")
    suspend fun deleteService(id: Int)
}
