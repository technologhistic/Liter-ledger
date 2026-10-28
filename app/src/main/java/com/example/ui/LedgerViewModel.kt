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
import com.example.data.Shift
import com.example.util.AppUpdateInfo
import com.example.util.AppUpdateManager
import com.example.util.DateUtils
import com.example.util.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

enum class Screen(val title: String) {
    SellToday("Sell Today"),
    Items("Items"),
    ThisMonth("This Month"),
    History("History"),
    Updates("Updates")
}

data class DayHistoryGroup(
    val date: String,
    val dayOfWeek: String,
    val formattedDateAndDay: String,
    val totalLitres: Double,
    val totalAmount: Double,
    val salesCount: Int,
    val morningLitres: Double,
    val noonLitres: Double,
    val eveningLitres: Double,
    val sales: List<SaleEntity>
)

data class ShiftSummary(
    val shift: Shift,
    val litres: Double = 0.0,
    val amount: Double = 0.0,
    val count: Int = 0
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
    val morningLitres: Double = 0.0,
    val noonLitres: Double = 0.0,
    val eveningLitres: Double = 0.0,
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

    val updateManager = AppUpdateManager(application)
    val updateStatus: StateFlow<UpdateStatus> = updateManager.updateStatus
    val updateManifestUrl: StateFlow<String> = updateManager.updateManifestUrl

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

    // Active Shift for logging sales
    private val _activeShift = MutableStateFlow(Shift.currentShift())
    val activeShift: StateFlow<Shift> = _activeShift.asStateFlow()

    // Shift filter on Today's list: "all", "morning", "noon", "evening"
    private val _todayShiftFilter = MutableStateFlow("all")
    val todayShiftFilter: StateFlow<String> = _todayShiftFilter.asStateFlow()

    val todaySales: StateFlow<List<SaleEntity>> = combine(allSales, _todayDate) { sales, today ->
        sales.filter { it.date == today }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTodaySales: StateFlow<List<SaleEntity>> = combine(todaySales, _todayShiftFilter) { sales, filter ->
        if (filter == "all") {
            sales
        } else {
            sales.filter { it.shift == filter }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Shift summaries for today
    val todayShiftSummaries: StateFlow<Map<Shift, ShiftSummary>> = todaySales.map { sales ->
        val map = mutableMapOf<Shift, ShiftSummary>()
        for (shift in Shift.values()) {
            val shiftSales = sales.filter { it.shift == shift.id }
            val litres = DateUtils.roundToTwoDecimals(shiftSales.sumOf { it.litres })
            val amount = DateUtils.roundToTwoDecimals(shiftSales.sumOf { it.total })
            map[shift] = ShiftSummary(
                shift = shift,
                litres = litres,
                amount = amount,
                count = shiftSales.size
            )
        }
        map
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

    private val _monthShiftFilter = MutableStateFlow("all")
    val monthShiftFilter: StateFlow<String> = _monthShiftFilter.asStateFlow()

    val monthSales: StateFlow<List<SaleEntity>> = combine(allSales, _selectedMonth, _useBikramSambat, _monthShiftFilter) { sales, month, isBs, shiftFilter ->
        val inMonth = if (isBs) {
            sales.filter { DateUtils.getBsDate(it.date).yearMonthKey == month }
        } else {
            sales.filter { it.date.startsWith(month) }
        }
        if (shiftFilter == "all") inMonth else inMonth.filter { it.shift == shiftFilter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthAnalytics: StateFlow<MonthAnalytics> = monthSales.combine(allItems) { sales, _ ->
        var totalLit = 0.0
        var totalAmt = 0.0
        var mornLit = 0.0
        var noonLit = 0.0
        var eveLit = 0.0
        val byItem = mutableMapOf<String, Pair<Double, Double>>()

        for (sale in sales) {
            totalLit += sale.litres
            totalAmt += sale.total
            when (sale.shift) {
                Shift.MORNING.id -> mornLit += sale.litres
                Shift.NOON.id -> noonLit += sale.litres
                Shift.EVENING.id -> eveLit += sale.litres
            }
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
            morningLitres = DateUtils.roundToTwoDecimals(mornLit),
            noonLitres = DateUtils.roundToTwoDecimals(noonLit),
            eveningLitres = DateUtils.roundToTwoDecimals(eveLit),
            breakdowns = breakdowns
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthAnalytics())

    // History tab filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _historyShiftFilter = MutableStateFlow("all")
    val historyShiftFilter: StateFlow<String> = _historyShiftFilter.asStateFlow()

    val filteredHistorySales: StateFlow<List<SaleEntity>> = combine(allSales, _searchQuery, _historyShiftFilter) { sales, query, shiftFilter ->
        val shiftFiltered = if (shiftFilter == "all") sales else sales.filter { it.shift == shiftFilter }
        if (query.isBlank()) {
            shiftFiltered
        } else {
            val q = query.trim().lowercase()
            shiftFiltered.filter { sale ->
                val bs = DateUtils.getBsDate(sale.date)
                val shiftObj = Shift.fromId(sale.shift)
                sale.item.lowercase().contains(q) ||
                sale.date.contains(q) ||
                sale.shift.contains(q) ||
                shiftObj.titleEn.lowercase().contains(q) ||
                shiftObj.titleNp.contains(q) ||
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
                val mornLit = DateUtils.roundToTwoDecimals(groupSales.filter { it.shift == Shift.MORNING.id }.sumOf { it.litres })
                val noonLit = DateUtils.roundToTwoDecimals(groupSales.filter { it.shift == Shift.NOON.id }.sumOf { it.litres })
                val eveLit = DateUtils.roundToTwoDecimals(groupSales.filter { it.shift == Shift.EVENING.id }.sumOf { it.litres })
                val formattedDateAndDay = DateUtils.formatFullDateAndDay(date, useBs = isBs)
                val dayOfWeek = DateUtils.formatDayOfWeek(date)
                DayHistoryGroup(
                    date = date,
                    dayOfWeek = dayOfWeek,
                    formattedDateAndDay = formattedDateAndDay,
                    totalLitres = totalLit,
                    totalAmount = totalAmt,
                    salesCount = groupSales.size,
                    morningLitres = mornLit,
                    noonLitres = noonLit,
                    eveningLitres = eveLit,
                    sales = groupSales.sortedByDescending { it.timestamp }
                )
            }
            .sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            allItems.collect { items ->
                if (_selectedItemId.value == null && items.isNotEmpty()) {
                    _selectedItemId.value = items.first().id
                }
            }
        }

        if (updateManager.autoCheck.value) {
            viewModelScope.launch {
                updateManager.checkForUpdates()
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        _statusMessage.value = null
    }

    fun setActiveShift(shift: Shift) {
        _activeShift.value = shift
    }

    fun setTodayShiftFilter(filter: String) {
        _todayShiftFilter.value = filter
    }

    fun setMonthShiftFilter(filter: String) {
        _monthShiftFilter.value = filter
    }

    fun setHistoryShiftFilter(filter: String) {
        _historyShiftFilter.value = filter
    }

    fun setCurrency(symbol: String) {
        val sym = symbol.trim().ifEmpty { "Rs." }
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
        val shift = _activeShift.value

        viewModelScope.launch {
            repository.insertSale(
                date = _todayDate.value,
                itemName = selectedItem.name,
                litres = litres,
                price = effectivePrice,
                shift = shift.id
            )
            _litresInput.value = ""
            _priceInput.value = ""
            val formattedLitres = DateUtils.formatLitres(litres)
            val formattedAmt = DateUtils.formatAmount(total, _currencySymbol.value)
            _statusMessage.value = SaleStatusMessage(
                "Saved: $formattedLitres of ${selectedItem.name} for $formattedAmt (${shift.displayName})",
                true
            )
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

    fun shareDailyReport(context: Context, shiftFilter: String = "all") {
        val today = _todayDate.value
        val allToday = todaySales.value
        val sales = if (shiftFilter == "all") allToday else allToday.filter { it.shift == shiftFilter }
        val totalLit = sales.sumOf { it.litres }
        val totalAmt = sales.sumOf { it.total }
        val sym = _currencySymbol.value

        val shiftLabel = if (shiftFilter == "all") "All Shifts (दिनभर)" else Shift.fromId(shiftFilter).displayName

        val sb = StringBuilder()
        sb.append("📊 Litre Ledger Daily Report\n")
        sb.append("📅 Date: ").append(DateUtils.formatDisplayDate(today)).append(" (").append(DateUtils.formatDayOfWeek(today)).append(")\n")
        sb.append("⏰ Shift: ").append(shiftLabel).append("\n\n")
        sb.append("🥛 Total Litres Sold: ").append(DateUtils.formatLitres(totalLit)).append("\n")
        sb.append("💰 Total Amount: ").append(DateUtils.formatAmount(totalAmt, sym)).append("\n")
        sb.append("📝 Transactions: ").append(sales.size).append("\n\n")

        if (shiftFilter == "all") {
            val morn = allToday.filter { it.shift == Shift.MORNING.id }
            val noon = allToday.filter { it.shift == Shift.NOON.id }
            val eve = allToday.filter { it.shift == Shift.EVENING.id }
            sb.append("Shift Breakdown:\n")
            sb.append("• 🌅 Morning (बिहान): ").append(DateUtils.formatLitres(morn.sumOf { it.litres })).append(" | ").append(DateUtils.formatAmount(morn.sumOf { it.total }, sym)).append("\n")
            sb.append("• ☀️ Noon (दिउँसो): ").append(DateUtils.formatLitres(noon.sumOf { it.litres })).append(" | ").append(DateUtils.formatAmount(noon.sumOf { it.total }, sym)).append("\n")
            sb.append("• 🌙 Evening (बेलुका): ").append(DateUtils.formatLitres(eve.sumOf { it.litres })).append(" | ").append(DateUtils.formatAmount(eve.sumOf { it.total }, sym)).append("\n\n")
        }

        if (sales.isNotEmpty()) {
            sb.append("Sales Records:\n")
            sales.forEachIndexed { i, s ->
                val sShift = Shift.fromId(s.shift)
                sb.append("${i + 1}. ${s.item} (${sShift.titleEn}): ${DateUtils.formatLitres(s.litres)} @ $sym${s.price} = $sym${s.total} [${DateUtils.formatTime(s.timestamp)}]\n")
            }
        } else {
            sb.append("No sales recorded for this shift.\n")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, "Daily Sales Report - $shiftLabel")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Share Daily Report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    // Remote Update methods
    fun checkForUpdates() {
        viewModelScope.launch {
            updateManager.checkForUpdates()
        }
    }

    fun downloadAndInstallUpdate(apkUrl: String) {
        viewModelScope.launch {
            updateManager.downloadAndInstallApk(apkUrl)
        }
    }

    fun dismissUpdate() {
        updateManager.dismissUpdate()
    }

    fun setUpdateManifestUrl(url: String) {
        updateManager.setUpdateManifestUrl(url)
    }

    fun promptApkInstall(file: File) {
        updateManager.promptApkInstall(file)
    }

    // Remote Catalog / Price Sync across devices
    fun syncCatalogFromRemote(jsonUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(jsonUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Failed to fetch prices: HTTP ${response.code}")
                    }
                    return@launch
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val itemsArray = json.optJSONArray("items") ?: JSONArray()
                val updatedItems = mutableListOf<ItemEntity>()
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.getJSONObject(i)
                    val name = obj.optString("name", "").trim()
                    val price = obj.optDouble("price", 0.0)
                    if (name.isNotEmpty() && price >= 0) {
                        val existing = allItems.value.find { it.name.equals(name, ignoreCase = true) }
                        updatedItems.add(
                            ItemEntity(
                                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name,
                                price = price
                            )
                        )
                    }
                }
                if (updatedItems.isNotEmpty()) {
                    db.itemDao().insertAll(updatedItems)
                    withContext(Dispatchers.Main) {
                        onResult(true, "Synced ${updatedItems.size} items and latest prices successfully!")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false, "No valid items found in remote catalog.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Sync failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }
}
