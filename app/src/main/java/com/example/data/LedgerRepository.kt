package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class LedgerRepository(
    private val itemDao: ItemDao,
    private val saleDao: SaleDao
) {
    val allItems: Flow<List<ItemEntity>> = itemDao.getAllItems()
    val allSales: Flow<List<SaleEntity>> = saleDao.getAllSales()

    fun getSalesForDate(date: String): Flow<List<SaleEntity>> =
        saleDao.getSalesForDate(date)

    fun getSalesForMonth(monthPrefix: String): Flow<List<SaleEntity>> =
        saleDao.getSalesForMonth(monthPrefix)

    suspend fun insertItem(name: String, price: Double): ItemEntity = withContext(Dispatchers.IO) {
        val item = ItemEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            price = price
        )
        itemDao.insertItem(item)
        item
    }

    suspend fun updateItemPrice(id: String, newPrice: Double) = withContext(Dispatchers.IO) {
        val existing = itemDao.getItemById(id)
        if (existing != null) {
            itemDao.updateItem(existing.copy(price = newPrice))
        }
    }

    suspend fun updateItem(item: ItemEntity) = withContext(Dispatchers.IO) {
        itemDao.updateItem(item)
    }

    suspend fun deleteItem(item: ItemEntity) = withContext(Dispatchers.IO) {
        itemDao.deleteItem(item)
    }

    suspend fun insertSale(
        date: String,
        itemName: String,
        litres: Double,
        price: Double
    ): SaleEntity = withContext(Dispatchers.IO) {
        val roundedTotal = kotlin.math.round(litres * price * 100.0) / 100.0
        val sale = SaleEntity(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            date = date,
            item = itemName,
            litres = litres,
            price = price,
            total = roundedTotal
        )
        saleDao.insertSale(sale)
        sale
    }

    suspend fun deleteSale(sale: SaleEntity) = withContext(Dispatchers.IO) {
        saleDao.deleteSale(sale)
    }

    suspend fun deleteSaleById(id: String) = withContext(Dispatchers.IO) {
        saleDao.deleteSaleById(id)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        saleDao.deleteAllSales()
        itemDao.deleteAllItems()
    }

    suspend fun seedSampleItems() = withContext(Dispatchers.IO) {
        itemDao.insertAll(
            listOf(
                ItemEntity(name = "Cow Milk", price = 50.0),
                ItemEntity(name = "Buffalo Milk", price = 65.0),
                ItemEntity(name = "Full Cream Milk", price = 70.0),
                ItemEntity(name = "Curd / Dahi", price = 60.0)
            )
        )
    }

    suspend fun exportBackupJson(items: List<ItemEntity>, sales: List<SaleEntity>): String =
        withContext(Dispatchers.Default) {
            val root = JSONObject()
            root.put("app", "litre-ledger")
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            root.put("exported", isoFormat.format(Date()))

            val itemsArray = JSONArray()
            items.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("price", item.price)
                itemsArray.put(obj)
            }
            root.put("items", itemsArray)

            val salesArray = JSONArray()
            sales.forEach { sale ->
                val obj = JSONObject()
                obj.put("id", sale.id)
                obj.put("ts", sale.timestamp)
                obj.put("date", sale.date)
                obj.put("item", sale.item)
                obj.put("litres", sale.litres)
                obj.put("price", sale.price)
                obj.put("total", sale.total)
                salesArray.put(obj)
            }
            root.put("sales", salesArray)

            root.toString(2)
        }

    suspend fun importBackupJson(
        jsonString: String,
        replaceExisting: Boolean = true
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonString)
        val itemsArray = root.optJSONArray("items") ?: JSONArray()
        val salesArray = root.optJSONArray("sales") ?: JSONArray()

        val parsedItems = mutableListOf<ItemEntity>()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.getJSONObject(i)
            val name = obj.optString("name", "").trim()
            val price = obj.optDouble("price", 0.0)
            val id = if (obj.has("id")) obj.optString("id") else UUID.randomUUID().toString()
            if (name.isNotEmpty() && price >= 0.0) {
                parsedItems.add(ItemEntity(id = id, name = name, price = price))
            }
        }

        val parsedSales = mutableListOf<SaleEntity>()
        for (i in 0 until salesArray.length()) {
            val obj = salesArray.getJSONObject(i)
            val item = obj.optString("item", "").trim()
            val date = obj.optString("date", "")
            val litres = obj.optDouble("litres", 0.0)
            val price = obj.optDouble("price", 0.0)
            val total = obj.optDouble("total", litres * price)
            val ts = obj.optLong("ts", System.currentTimeMillis())
            val id = if (obj.has("id")) obj.optString("id") else UUID.randomUUID().toString()

            if (item.isNotEmpty() && litres > 0.0 && date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                parsedSales.add(
                    SaleEntity(
                        id = id,
                        timestamp = ts,
                        date = date,
                        item = item,
                        litres = litres,
                        price = price,
                        total = kotlin.math.round(total * 100.0) / 100.0
                    )
                )
            }
        }

        if (replaceExisting) {
            saleDao.deleteAllSales()
            itemDao.deleteAllItems()
        }

        if (parsedItems.isNotEmpty()) {
            itemDao.insertAll(parsedItems)
        }
        if (parsedSales.isNotEmpty()) {
            saleDao.insertAll(parsedSales)
        }

        Pair(parsedItems.size, parsedSales.size)
    }
}
