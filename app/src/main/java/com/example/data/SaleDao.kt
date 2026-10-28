package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE date = :date ORDER BY timestamp DESC")
    fun getSalesForDate(date: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE date = :date AND shift = :shift ORDER BY timestamp DESC")
    fun getSalesForDateAndShift(date: String, shift: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE date LIKE :monthPrefix || '%' ORDER BY timestamp DESC")
    fun getSalesForMonth(monthPrefix: String): Flow<List<SaleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sales: List<SaleEntity>)

    @Delete
    suspend fun deleteSale(sale: SaleEntity)

    @Query("DELETE FROM sales WHERE id = :id")
    suspend fun deleteSaleById(id: String)

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()
}
