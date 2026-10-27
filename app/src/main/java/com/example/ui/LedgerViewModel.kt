package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ItemEntity
import com.example.data.LedgerRepository
import com.example.data.SaleEntity
import com.example.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

enum class Screen(val title: String) {
    SellToday("Sell Today"),
    Items("Items"),
    ThisMonth("This Month"),
    History("History")
}

data class DayHistoryGroup(
    val date: String,
    val dayOfWeek: String,
    val formattedDateAndDay: String,
    val totalLitres: Double,
    val totalAmount: Double,
    val salesCount: Int,
    val sales: List<SaleEntity>
)

data class ItemMonthBreakdown(
    val itemName: String,
    val litres: Double,
    val amount: Double,
    val percentageOfTotalAmount: Float
)

data class MonthAnalytics(
    val totalLitres: Double = 0.0,
    val totalAmount: Double = 0.0,
    val salesCount: Int = 0,
    val avgPricePerLitre: Double = 0.0,
    val breakdowns: List<ItemMonthBreakdown> = emptyList()
)

data class SaleStatusMessage(
    val text: String,
    val isSuccess: Boolean
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = LedgerRepository(db.itemDao(), db.saleDao())
    private val prefs = application.getSharedPreferences("litre_ledger_prefs", Context.MODE_PRIVATE)

    private val _currentScreen = MutableStateFlow(Screen.SellToday)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val savedCurrency = prefs.getString("currency_symbol", null)
    private val initialCurrency = if (savedCurrency.isNullOrBlank() || savedCurrency == "$") {
        prefs.edit().putString("currency_symbol", "Rs.").apply()
        "Rs."
    } else {
        savedCurrency
    }
    private val _currencySymbol = MutableStateFlow(initialCurrency)
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    private val _useBikramSambat = MutableStateFlow(prefs.getBoolean("use_bs_calendar", true))
    val useBikramSambat: StateFlow<Boolean> = _useBikramSambat.asStateFlow()

    val allItems: StateFlow<List<ItemEntity>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSales: StateFlow<List<SaleEntity>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _todayDate = MutableStateFlow(DateUtils.getTodayDate())
    val todayDate: StateFlow<String> = _todayDate.asStateFlow()

    val todaySales: StateFlow<List<SaleEntity>> = combine(allSales, _todayDate) { sales, today ->
        sales.filter { it.date == today }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sell tab inputs
    private val _selectedItemId = MutableStateFlow<String?>(null)
    val selectedItemId: StateFlow<String?> = _selectedItemId.asStateFlow()

    private val _litresInput = MutableStateFlow("")
    val litresInput: StateFlow<String> = _litresInput.asStateFlow()

    private val _priceInput = MutableStateFlow("")
    val priceInput: StateFlow<String> = _priceInput.asStateFlow()

    private val _statusMessage = MutableStateFlow<SaleStatusMessage?>(null)
    val statusMessage: StateFlow<SaleStatusMessage?> = _statusMessage.asStateFlow()

    // Month tab (defaults to current BS month e.g. 2083-07 for Kartik 2083 BS)
    private val _selectedMonth = MutableStateFlow(DateUtils.getCurrentBsMonth())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    val monthSales: StateFlow<List<SaleEntity>> = combine(allSales, _selectedMonth, _useBikramSambat) { sales, month, isBs ->
        if (isBs) {
            sales.filter { DateUtils.getBsDate(it.date).yearMonthKey == month }
        } else {
            sales.filter { it.date.startsWith(month) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthAnalytics: StateFlow<MonthAnalytics> = monthSales.combine(allItems) { sales, _ ->
        var totalLit = 0.0
        var totalAmt = 0.0
        val byItem = mutableMapOf<String, Pair<Double, Double>>() // Name -> (litres, total)

        for (sale in sales) {
            totalLit += sale.litres
            totalAmt += sale.total
            val current = byItem[sale.item] ?: Pair(0.0, 0.0)
            byItem[sale.item] = Pair(current.first + sale.litres, current.second + sale.total)
        }

        val roundedAmt = DateUtils.roundToTwoDecimals(totalAmt)
        val roundedLit = DateUtils.roundToTwoDecimals(totalLit)
        val avgPrice = if (roundedLit > 0) DateUtils.roundToTwoDecimals(roundedAmt / roundedLit) else 0.0

        val breakdowns = byItem.map { (name, pair) ->
            val lit = DateUtils.roundToTwoDecimals(pair.first)
            val amt = DateUtils.roundToTwoDecimals(pair.second)
            val pct = if (roundedAmt > 0) (amt / roundedAmt).toFloat() else 0f
            ItemMonthBreakdown(
                itemName = name,
                litres = lit,
                amount = amt,
                percentageOfTotalAmount = pct
            )
        }.sortedByDescending { it.amount }

        MonthAnalytics(
            totalLitres = roundedLit,
            totalAmount = roundedAmt,
            salesCount = sales.size,
            avgPricePerLitre = avgPrice,
            breakdowns = breakdowns
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthAnalytics())

    // History tab filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredHistorySales: StateFlow<List<SaleEntity>> = combine(allSales, _searchQuery) { sales, query ->
        if (query.isBlank()) {
            sales
        } else {
            val q = query.trim().lowercase()
            sales.filter { sale ->
                val bs = DateUtils.getBsDate(sale.date)
                sale.item.lowercase().contains(q) ||
                sale.date.contains(q) ||
                bs.formattedBsDate.lowercase().contains(q) ||
                bs.monthNameEn.lowercase().contains(q) ||
                bs.monthNameNp.contains(q) ||
                bs.dayOfWeek.lowercase().contains(q) ||
                bs.dayOfWeekNp.contains(q) ||
                bs.year.toString().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyGroupedByDay: StateFlow<List<DayHistoryGroup>> = combine(filteredHistorySales, _useBikramSambat) { sales, isBs ->
        sales.groupBy { it.date }
            .map { (date, groupSales) ->
                val totalLit = DateUtils.roundToTwoDecimals(groupSales.sumOf { it.litres })
                val totalAmt = DateUtils.roundToTwoDecimals(groupSales.sumOf { it.total })
                val formattedDateAndDay = DateUtils.formatFullDateAndDay(date, useBs = isBs)
                val dayOfWeek = DateUtils.formatDayOfWeek(date)
                DayHistoryGroup(
                    date = date,
                    dayOfWeek = dayOfWeek,
                    formattedDateAndDay = formattedDateAndDay,
                    totalLitres = totalLit,
                    totalAmount = totalAmt,
                    salesCount = groupSales.size,
                    sales = groupSales.sortedByDescending { it.timestamp }
                )
            }
            .sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Automatically select the first item when items load if none selected
        viewModelScope.launch {
            allItems.collect { items ->
                if (_selectedItemId.value == null && items.isNotEmpty()) {
                    _selectedItemId.value = items.first().id
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        _statusMessage.value = null
    }

    fun setCurrency(symbol: String) {
        val sym = symbol.trim().ifEmpty { "$" }
        _currencySymbol.value = sym
        prefs.edit().putString("currency_symbol", sym).apply()
    }

    fun selectItem(itemId: String) {
        _selectedItemId.value = itemId
    }

    fun setLitresInput(text: String) {
        _litresInput.value = text
        _statusMessage.value = null
    }

    fun addQuickLitres(amount: Double) {
        val current = _litresInput.value.toDoubleOrNull() ?: 0.0
        val updated = current + amount
        _litresInput.value = if (updated % 1.0 == 0.0) updated.toInt().toString() else "%.2f".format(updated).trimEnd('0').trimEnd('.')
        _statusMessage.value = null
    }

    fun setPriceInput(text: String) {
        _priceInput.value = text
        _statusMessage.value = null
    }

    fun calculateCurrentTotal(selectedItem: ItemEntity?): Double {
        val litres = _litresInput.value.toDoubleOrNull() ?: 0.0
        val customPrice = _priceInput.value.toDoubleOrNull()
        val effectivePrice = customPrice ?: (selectedItem?.price ?: 0.0)
        return if (litres > 0 && effectivePrice >= 0) {
            DateUtils.roundToTwoDecimals(litres * effectivePrice)
        } else {
            0.0
        }
    }

    fun saveSale() {
        val currentItems = allItems.value
        val selectedItem = currentItems.find { it.id == _selectedItemId.value }
            ?: currentItems.firstOrNull()

        if (selectedItem == null) {
            _statusMessage.value = SaleStatusMessage("Add an item first in the Items tab.", false)
            return
        }

        val litres = _litresInput.value.toDoubleOrNull()
        if (litres == null || litres <= 0.0) {
            _statusMessage.value = SaleStatusMessage("Please enter valid litres sold.", false)
            return
        }

        val customPrice = _priceInput.value.toDoubleOrNull()
        val effectivePrice = if (customPrice != null && customPrice >= 0.0) customPrice else selectedItem.price
        val total = DateUtils.roundToTwoDecimals(litres * effectivePrice)

        viewModelScope.launch {
            repository.insertSale(
                date = _todayDate.value,
                itemName = selectedItem.name,
                litres = litres,
                price = effectivePrice
            )
            _litresInput.value = ""
            _priceInput.value = ""
            val formattedLitres = DateUtils.formatLitres(litres)
            val formattedAmt = DateUtils.formatAmount(total, _currencySymbol.value)
            _statusMessage.value = SaleStatusMessage("Saved: $formattedLitres of ${selectedItem.name} for $formattedAmt", true)
        }
    }

    fun deleteSale(sale: SaleEntity) {
        viewModelScope.launch {
            repository.deleteSale(sale)
        }
    }

    fun addNewItem(name: String, price: Double, onComplete: () -> Unit) {
        viewModelScope.launch {
            val item = repository.insertItem(name, price)
            _selectedItemId.value = item.id
            onComplete()
        }
    }

    fun updateItemPrice(id: String, newPrice: Double) {
        viewModelScope.launch {
            repository.updateItemPrice(id, newPrice)
        }
    }

    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(item)
            if (_selectedItemId.value == item.id) {
                _selectedItemId.value = allItems.value.firstOrNull { it.id != item.id }?.id
            }
        }
    }

    fun seedSampleItems() {
        viewModelScope.launch {
            repository.seedSampleItems()
        }
    }

    fun toggleCalendarSystem() {
        val newValue = !_useBikramSambat.value
        _useBikramSambat.value = newValue
        prefs.edit().putBoolean("use_bs_calendar", newValue).apply()
        _selectedMonth.value = if (newValue) DateUtils.getCurrentBsMonth() else DateUtils.getCurrentMonth()
    }

    fun previousMonth() {
        _selectedMonth.value = DateUtils.shiftMonth(_selectedMonth.value, -1, _useBikramSambat.value)
    }

    fun nextMonth() {
        _selectedMonth.value = DateUtils.shiftMonth(_selectedMonth.value, 1, _useBikramSambat.value)
    }

    fun setSelectedMonth(ym: String) {
        _selectedMonth.value = ym
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllData()
            _selectedItemId.value = null
            _litresInput.value = ""
            _priceInput.value = ""
            onComplete()
        }
    }

    fun exportBackup(context: Context, targetUri: Uri? = null, shareText: Boolean = false, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = repository.exportBackupJson(allItems.value, allSales.value)
                if (targetUri != null) {
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    onResult(true, "Backup file saved successfully!")
                } else if (shareText) {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TITLE, "Litre Ledger Backup")
                        putExtra(Intent.EXTRA_TEXT, json)
                        type = "application/json"
                    }
                    val shareChooser = Intent.createChooser(sendIntent, "Export Litre Ledger Backup")
                    shareChooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareChooser)
                    onResult(true, "Backup ready to share.")
                } else {
                    onResult(false, "No export destination provided.")
                }
            } catch (e: Exception) {
                onResult(false, "Export failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun importBackup(context: Context, uri: Uri, replaceExisting: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val sb = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            sb.append(line).append('\n')
                            line = reader.readLine()
                        }
                    }
                }
                val content = sb.toString()
                val (itemsCount, salesCount) = repository.importBackupJson(content, replaceExisting)
                onResult(true, "Successfully imported $itemsCount items and $salesCount sales records!")
            } catch (e: Exception) {
                onResult(false, "Import failed: ${e.localizedMessage ?: "Invalid backup file"}")
            }
        }
    }

    fun shareDailyReport(context: Context) {
        val today = _todayDate.value
        val sales = todaySales.value
        val totalLit = sales.sumOf { it.litres }
        val totalAmt = sales.sumOf { it.total }
        val sym = _currencySymbol.value

        val sb = StringBuilder()
        sb.append("📊 Litre Ledger Daily Report - ").append(DateUtils.formatDisplayDate(today)).append("\n\n")
        sb.append("Total Litres Sold: ").append(DateUtils.formatLitres(totalLit)).append("\n")
        sb.append("Total Amount: ").append(DateUtils.formatAmount(totalAmt, sym)).append("\n")
        sb.append("Total Sales: ").append(sales.size).append("\n\n")

        if (sales.isNotEmpty()) {
            sb.append("Sales Breakdown:\n")
            sales.forEachIndexed { i, s ->
                sb.append("${i + 1}. ${s.item}: ${DateUtils.formatLitres(s.litres)} @ $sym${s.price} = $sym${s.total} (${DateUtils.formatTime(s.timestamp)})\n")
            }
        } else {
            sb.append("No sales recorded today.\n")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, "Daily Sales Report")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Share Daily Report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
