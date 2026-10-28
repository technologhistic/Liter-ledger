package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ItemEntity
import com.example.data.SaleEntity
import com.example.data.Shift
import com.example.ui.LedgerViewModel
import com.example.ui.ShiftSummary
import com.example.util.DateUtils

@Composable
fun SellTodayScreen(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val todayDate by viewModel.todayDate.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val todayShiftFilter by viewModel.todayShiftFilter.collectAsStateWithLifecycle()
    val filteredSales by viewModel.filteredTodaySales.collectAsStateWithLifecycle()
    val shiftSummaries by viewModel.todayShiftSummaries.collectAsStateWithLifecycle()
    val selectedItemId by viewModel.selectedItemId.collectAsStateWithLifecycle()
    val litresInput by viewModel.litresInput.collectAsStateWithLifecycle()
    val priceInput by viewModel.priceInput.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val useBs by viewModel.useBikramSambat.collectAsStateWithLifecycle()

    val selectedItem = items.find { it.id == selectedItemId } ?: items.firstOrNull()
    val currentTotal = viewModel.calculateCurrentTotal(selectedItem)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("sell_today_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Banner with BS Date and Day
        item {
            DateAndCalendarBanner(
                todayIsoDate = todayDate,
                useBs = useBs,
                onToggleCalendar = { viewModel.toggleCalendarSystem() }
            )
        }

        // 2. Schedule / Shift Pages (Morning, Noon, Evening)
        item {
            TimeSchedulePagesSection(
                activeShift = activeShift,
                onShiftSelected = { shift ->
                    viewModel.setActiveShift(shift)
                    viewModel.setTodayShiftFilter(shift.id)
                },
                shiftSummaries = shiftSummaries,
                currencySymbol = currencySymbol
            )
        }

        // 3. New Sale Entry Form
        item {
            SaleEntryCard(
                items = items,
                selectedItem = selectedItem,
                onSelectItem = { viewModel.selectItem(it.id) },
                activeShift = activeShift,
                litresInput = litresInput,
                onLitresChanged = { viewModel.setLitresInput(it) },
                onAddQuickLitres = { viewModel.addQuickLitres(it) },
                priceInput = priceInput,
                onPriceChanged = { viewModel.setPriceInput(it) },
                currentTotal = currentTotal,
                currencySymbol = currencySymbol,
                onSave = { viewModel.saveSale() },
                statusMessage = statusMessage
            )
        }

        // 4. Today's Sales List Header with Shift Filter Chips and Share
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today's Shift Sales",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = when (todayShiftFilter) {
                            "all" -> "Showing all shifts (${filteredSales.size} sales)"
                            else -> "Showing ${Shift.fromId(todayShiftFilter).displayName} (${filteredSales.size} sales)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.shareDailyReport(context, todayShiftFilter) },
                    modifier = Modifier.testTag("btn_share_report")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Share", fontSize = 13.sp)
                }
            }
        }

        // Shift Filter Selector for the list below
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = todayShiftFilter == "all",
                    onClick = { viewModel.setTodayShiftFilter("all") },
                    label = { Text("All (सबै)") },
                    modifier = Modifier.testTag("filter_shift_all")
                )
                Shift.values().forEach { s ->
                    FilterChip(
                        selected = todayShiftFilter == s.id,
                        onClick = { viewModel.setTodayShiftFilter(s.id) },
                        label = { Text("${s.iconName} ${s.titleEn}") },
                        modifier = Modifier.testTag("filter_shift_${s.id}")
                    )
                }
            }
        }

        // 5. Sales records
        if (filteredSales.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalDrink,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (todayShiftFilter == "all") "No sales recorded yet today." else "No sales recorded for ${Shift.fromId(todayShiftFilter).displayName}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Enter litres sold above and tap Record Sale.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        } else {
            items(filteredSales, key = { it.id }) { sale ->
                TodaySaleItemCard(
                    sale = sale,
                    currencySymbol = currencySymbol,
                    onDelete = { viewModel.deleteSale(sale) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DateAndCalendarBanner(
    todayIsoDate: String,
    useBs: Boolean,
    onToggleCalendar: () -> Unit
) {
    val fullDateString = DateUtils.formatFullDateAndDay(todayIsoDate, useBs = useBs)
    val dayOfWeekNp = DateUtils.formatDayOfWeekNepali(todayIsoDate)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("date_calendar_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Date",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = fullDateString,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (useBs) "नेपाली मिति (वि.सं.) • $dayOfWeekNp" else "English Calendar (AD)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            FilterChip(
                selected = useBs,
                onClick = onToggleCalendar,
                label = {
                    Text(
                        text = if (useBs) "BS" else "AD",
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.testTag("btn_toggle_calendar")
            )
        }
    }
}

@Composable
fun TimeSchedulePagesSection(
    activeShift: Shift,
    onShiftSelected: (Shift) -> Unit,
    shiftSummaries: Map<Shift, ShiftSummary>,
    currencySymbol: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("time_schedule_pages_section"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Time Schedule",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Time Schedule (सिफ्ट तालिका)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Active: ${activeShift.titleEn}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Shift Tabs / Pages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Shift.values().forEach { shift ->
                    val isSelected = activeShift == shift
                    val summary = shiftSummaries[shift] ?: ShiftSummary(shift)

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onShiftSelected(shift) }
                            .testTag("tab_shift_${shift.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isSelected) 4.dp else 0.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = shift.iconName,
                                fontSize = 22.sp
                            )
                            Text(
                                text = shift.titleEn,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = shift.titleNp,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Divider(
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = DateUtils.formatLitres(summary.litres),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = DateUtils.formatAmount(summary.amount, currencySymbol),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SaleEntryCard(
    items: List<ItemEntity>,
    selectedItem: ItemEntity?,
    onSelectItem: (ItemEntity) -> Unit,
    activeShift: Shift,
    litresInput: String,
    onLitresChanged: (String) -> Unit,
    onAddQuickLitres: (Double) -> Unit,
    priceInput: String,
    onPriceChanged: (String) -> Unit,
    currentTotal: Double,
    currencySymbol: String,
    onSave: () -> Unit,
    statusMessage: com.example.ui.SaleStatusMessage?
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sale_entry_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header showing active shift
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Record Sale",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Schedule: ${activeShift.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Item Selector Chips
            Text(
                text = "Select Liquid / Dairy Item:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (items.isEmpty()) {
                Text(
                    text = "No items found. Go to 'Items' tab to add items first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val isSelected = item.id == selectedItem?.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectItem(item) },
                            label = {
                                Text(
                                    text = "${item.name} (${currencySymbol}${item.price}/L)",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("item_chip_${item.id}")
                        )
                    }
                }
            }

            // Litres Sold Input
            OutlinedTextField(
                value = litresInput,
                onValueChange = onLitresChanged,
                label = { Text("Litres Sold (लिटर)") },
                placeholder = { Text("e.g. 2.5") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = { Text("L", modifier = Modifier.padding(end = 12.dp), fontWeight = FontWeight.Bold) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_litres")
            )

            // Quick Litre Add Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.5, 1.0, 2.0, 5.0).forEach { amount ->
                    OutlinedButton(
                        onClick = { onAddQuickLitres(amount) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_quick_${amount}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text(text = "+${if (amount % 1.0 == 0.0) amount.toInt() else amount}L", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom Price override (optional)
            OutlinedTextField(
                value = priceInput,
                onValueChange = onPriceChanged,
                label = {
                    Text(
                        text = if (selectedItem != null) "Price per Litre (${currencySymbol}, default ${selectedItem.price})" else "Price per Litre"
                    )
                },
                placeholder = { Text(selectedItem?.price?.toString() ?: "0.0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = { Text(currencySymbol, modifier = Modifier.padding(end = 12.dp), fontWeight = FontWeight.Bold) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_price")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Total summary bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Calculated Total:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = DateUtils.formatAmount(currentTotal, currencySymbol),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save Button
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_save_sale"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Record Sale (${activeShift.titleEn} / ${activeShift.titleNp})",
                    fontWeight = FontWeight.Bold
                )
            }

            // Status message
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                statusMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (msg.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (msg.isSuccess) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.isSuccess) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TodaySaleItemCard(
    sale: SaleEntity,
    currencySymbol: String,
    onDelete: () -> Unit
) {
    val shiftObj = Shift.fromId(sale.shift)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sale_item_${sale.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sale.item,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${shiftObj.iconName} ${shiftObj.titleEn}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${DateUtils.formatLitres(sale.litres)} @ $currencySymbol${sale.price}/L • ${DateUtils.formatTime(sale.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = DateUtils.formatAmount(sale.total, currencySymbol),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_delete_sale_${sale.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Sale",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
