package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SaleEntity
import com.example.ui.DayHistoryGroup
import com.example.ui.LedgerViewModel
import com.example.ui.Screen
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyListPlaceholder
import com.example.ui.components.SaleRowItem
import com.example.util.DateUtils

enum class HistoryViewMode {
    DayByDay,
    DailySummaryOnly,
    AllList
}

@Composable
fun HistoryScreen(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    // Back navigation pops to Sell Today
    BackHandler {
        viewModel.navigateTo(Screen.SellToday)
    }

    val context = LocalContext.current
    val sales by viewModel.filteredHistorySales.collectAsStateWithLifecycle()
    val dayGroups by viewModel.historyGroupedByDay.collectAsStateWithLifecycle()
    val allSales by viewModel.allSales.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val useBikramSambat by viewModel.useBikramSambat.collectAsStateWithLifecycle()

    var viewMode by remember { mutableStateOf(HistoryViewMode.DayByDay) }
    var saleToDelete by remember { mutableStateOf<SaleEntity?>(null) }
    var showClearDialogFirst by remember { mutableStateOf(false) }
    var showClearDialogSecond by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var backupStatusMsg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // Track collapsed days (optional convenience for user)
    var collapsedDays by remember { mutableStateOf(setOf<String>()) }

    // File picker for exporting JSON
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackup(context, targetUri = uri) { success, msg ->
                backupStatusMsg = Pair(msg, success)
            }
        }
    }

    // File picker for importing JSON
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importBackup(context, uri, replaceExisting = true) { success, msg ->
                backupStatusMsg = Pair(msg, success)
            }
        }
    }

    val totalMatchingLitres = sales.sumOf { it.litres }
    val totalMatchingAmount = sales.sumOf { it.total }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Search & Filter Bar
        item {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search by day, date (YYYY-MM-DD), or item...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        // Aggregate Summary Header when sales exist
        if (sales.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Volume Sold",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = DateUtils.formatLitres(totalMatchingLitres),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Days Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${dayGroups.size} days",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Total Amount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = DateUtils.formatAmount(totalMatchingAmount, currencySymbol),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // View Mode Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = viewMode == HistoryViewMode.DayByDay,
                    onClick = { viewMode = HistoryViewMode.DayByDay },
                    label = { Text("By Day & Date", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                FilterChip(
                    selected = viewMode == HistoryViewMode.DailySummaryOnly,
                    onClick = { viewMode = HistoryViewMode.DailySummaryOnly },
                    label = { Text("Daily Totals", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                FilterChip(
                    selected = viewMode == HistoryViewMode.AllList,
                    onClick = { viewMode = HistoryViewMode.AllList },
                    label = { Text("All Items", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Empty state
        if (sales.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EmptyListPlaceholder(
                        message = if (searchQuery.isBlank()) "No sales recorded yet." else "No sales matching \"$searchQuery\".",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        actionLabel = if (searchQuery.isNotEmpty()) "Clear Filter" else null,
                        onAction = { viewModel.setSearchQuery("") }
                    )
                }
            }
        } else {
            when (viewMode) {
                // Mode 1: Day by Day with Date & Day header, daily sold totals, and item details
                HistoryViewMode.DayByDay -> {
                    dayGroups.forEach { dayGroup ->
                        val isCollapsed = collapsedDays.contains(dayGroup.date)

                        item(key = "header_${dayGroup.date}") {
                            DayGroupHeaderCard(
                                dayGroup = dayGroup,
                                currencySymbol = currencySymbol,
                                useBs = useBikramSambat,
                                isCollapsed = isCollapsed,
                                onToggleCollapse = {
                                    collapsedDays = if (isCollapsed) {
                                        collapsedDays - dayGroup.date
                                    } else {
                                        collapsedDays + dayGroup.date
                                    }
                                }
                            )
                        }

                        if (!isCollapsed) {
                            items(dayGroup.sales, key = { it.id }) { sale ->
                                SaleRowItem(
                                    sale = sale,
                                    currencySymbol = currencySymbol,
                                    showDate = false, // Date & Day is prominent in the group header
                                    onDelete = { saleToDelete = sale },
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Mode 2: Daily Totals overview cards (Date, Day, How much sold)
                HistoryViewMode.DailySummaryOnly -> {
                    items(dayGroups, key = { it.date }) { dayGroup ->
                        DailySummaryCard(
                            dayGroup = dayGroup,
                            currencySymbol = currencySymbol,
                            useBs = useBikramSambat,
                            onViewDetails = {
                                viewModel.setSearchQuery(dayGroup.date)
                                viewMode = HistoryViewMode.DayByDay
                            }
                        )
                    }
                }

                // Mode 3: Chronological list of all sales with full date and day
                HistoryViewMode.AllList -> {
                    items(sales, key = { it.id }) { sale ->
                        SaleRowItem(
                            sale = sale,
                            currencySymbol = currencySymbol,
                            showDate = true, // Shows full date and day e.g. "Monday, 27 Oct 2026"
                            onDelete = { saleToDelete = sale }
                        )
                    }
                }
            }
        }

        // Backup and Data Management Card
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Backup and data",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Export your items and sales as a JSON backup file, restore from an existing file, or configure settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Export JSON File Button
                    Button(
                        onClick = {
                            val defaultFileName = "litre-ledger-backup-${DateUtils.getTodayDate()}.json"
                            exportLauncher.launch(defaultFileName)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_backup_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export backup (JSON file)", fontWeight = FontWeight.Bold)
                    }

                    // Share JSON text Button
                    OutlinedButton(
                        onClick = {
                            viewModel.exportBackup(context, shareText = true) { success, msg ->
                                backupStatusMsg = Pair(msg, success)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share backup via app", fontWeight = FontWeight.SemiBold)
                    }

                    // Import Backup Button
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/*"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("import_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import backup", fontWeight = FontWeight.SemiBold)
                    }

                    // Calendar System Toggle Button
                    OutlinedButton(
                        onClick = { viewModel.toggleCalendarSystem() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (useBikramSambat) "Calendar: Bikram Sambat (BS) • Active" else "Calendar: Gregorian (AD)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Currency Setting Button
                    OutlinedButton(
                        onClick = { showCurrencyDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Currency Symbol: $currencySymbol", fontWeight = FontWeight.SemiBold)
                    }

                    // Clear All Data Button (Danger)
                    OutlinedButton(
                        onClick = { showClearDialogFirst = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("clear_data_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear all data", fontWeight = FontWeight.Bold)
                    }

                    // Backup Status Message
                    AnimatedVisibility(
                        visible = backupStatusMsg != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        backupStatusMsg?.let { (msg, isOk) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isOk) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (isOk) Color(0xFF15803D) else Color(0xFFB91C1C),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isOk) Color(0xFF15803D) else Color(0xFFB91C1C)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Delete single sale dialog
    saleToDelete?.let { sale ->
        ConfirmDeleteDialog(
            title = "Delete Sale",
            message = "Delete this sale of ${DateUtils.formatLitres(sale.litres)} of ${sale.item} on ${DateUtils.formatFullDateAndDay(sale.date)}?",
            onConfirm = { viewModel.deleteSale(sale) },
            onDismiss = { saleToDelete = null }
        )
    }

    // First clear warning dialog
    if (showClearDialogFirst) {
        ConfirmDeleteDialog(
            title = "Clear All Data?",
            message = "Delete ALL items and sales from this device? This action cannot be undone.",
            confirmText = "Continue",
            onConfirm = {
                showClearDialogFirst = false
                showClearDialogSecond = true
            },
            onDismiss = { showClearDialogFirst = false }
        )
    }

    // Second clear confirmation dialog
    if (showClearDialogSecond) {
        ConfirmDeleteDialog(
            title = "Are you absolutely sure?",
            message = "Export a backup first if you may need this data. All records will be wiped immediately.",
            confirmText = "Clear Everything",
            onConfirm = {
                viewModel.clearAllData {
                    backupStatusMsg = Pair("All data cleared from device.", true)
                }
            },
            onDismiss = { showClearDialogSecond = false }
        )
    }

    // Currency selector dialog
    if (showCurrencyDialog) {
        var customCurrency by remember { mutableStateOf(currencySymbol) }
        val commonCurrencies = listOf("Rs.", "रू", "₹", "$", "€", "£", "₨", "¥")

        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Choose Currency Symbol", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Select a preset or enter a custom currency symbol:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonCurrencies.take(4).forEach { sym ->
                            FilterChip(
                                selected = customCurrency == sym,
                                onClick = { customCurrency = sym },
                                label = { Text(sym, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonCurrencies.drop(4).forEach { sym ->
                            FilterChip(
                                selected = customCurrency == sym,
                                onClick = { customCurrency = sym },
                                label = { Text(sym, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customCurrency,
                        onValueChange = { customCurrency = it },
                        label = { Text("Custom Currency") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setCurrency(customCurrency)
                        showCurrencyDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dedicated Card displaying the Date, Day of the Week, and Daily Sales Totals
 */
@Composable
fun DayGroupHeaderCard(
    dayGroup: DayHistoryGroup,
    currencySymbol: String,
    useBs: Boolean,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleCollapse() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day and Date Display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = dayGroup.dayOfWeek.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Text(
                        text = DateUtils.formatDisplayDate(dayGroup.date, useBs = useBs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${dayGroup.salesCount} sale${if (dayGroup.salesCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Icon(
                        imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isCollapsed) "Expand" else "Collapse",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // How much was sold on this date and day
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalDrink,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Sold:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Text(
                        text = DateUtils.formatLitres(dayGroup.totalLitres),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Paid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Earned:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Text(
                        text = DateUtils.formatAmount(dayGroup.totalAmount, currencySymbol),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Compact Daily Totals card showing date, day of week, and total sold
 */
@Composable
fun DailySummaryCard(
    dayGroup: DayHistoryGroup,
    currencySymbol: String,
    useBs: Boolean,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dayGroup.dayOfWeek,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = DateUtils.formatDisplayDate(dayGroup.date, useBs = useBs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${dayGroup.salesCount} sale${if (dayGroup.salesCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DateUtils.formatLitres(dayGroup.totalLitres),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = DateUtils.formatAmount(dayGroup.totalAmount, currencySymbol),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
