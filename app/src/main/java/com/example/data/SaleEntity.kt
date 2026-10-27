package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val date: String, // "YYYY-MM-DD"
    val item: String, // Name of the item
    val litres: Double,
    val price: Double,
    val total: Double // litres * price rounded
)
