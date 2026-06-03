package com.mochilapp.mobile.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "services")
data class Service(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerEmail: String,
    val name: String,
    val description: String,
    val price: Double,
    val type: CompanyType,
    val location: String,
    val imageUrl: String = ""
)
