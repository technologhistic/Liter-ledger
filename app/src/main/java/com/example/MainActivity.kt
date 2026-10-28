package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.LedgerViewModel
import com.example.ui.Screen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ItemsScreen
import com.example.ui.screens.MonthScreen
import com.example.ui.screens.SellTodayScreen
import com.example.ui.screens.UpdatesScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.UpdateStatus

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LitreLedgerApp(viewModel = viewModel)
            }
        }
    }
}

data class NavTabItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LitreLedgerApp(viewModel: LedgerViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val hasUpdateAvailable = updateStatus is UpdateStatus.Available

    // Handle back button to return to SellToday if on another tab
    if (currentScreen != Screen.SellToday) {
        BackHandler {
            viewModel.navigateTo(Screen.SellToday)
        }
    }

    val tabs = listOf(
        NavTabItem(
            screen = Screen.SellToday,
            label = "Sell",
            selectedIcon = Icons.Filled.LocalDrink,
            unselectedIcon = Icons.Outlined.LocalDrink,
            testTag = "tab_sell_today"
        ),
        NavTabItem(
            screen = Screen.Items,
            label = "Items",
            selectedIcon = Icons.Filled.Category,
            unselectedIcon = Icons.Outlined.Category,
            testTag = "tab_items"
        ),
        NavTabItem(
            screen = Screen.ThisMonth,
            label = "Month",
            selectedIcon = Icons.Filled.BarChart,
            unselectedIcon = Icons.Outlined.BarChart,
            testTag = "tab_this_month"
        ),
        NavTabItem(
            screen = Screen.History,
            label = "History",
            selectedIcon = Icons.Filled.History,
            unselectedIcon = Icons.Outlined.History,
            testTag = "tab_history"
        ),
        NavTabItem(
            screen = Screen.Updates,
            label = "Updates",
            selectedIcon = Icons.Filled.SystemUpdate,
            unselectedIcon = Icons.Outlined.SystemUpdate,
            testTag = "tab_updates"
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(
                            text = "Litre Ledger",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Liquid & Dairy Sales (BS Calendar)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Updates) },
                        modifier = Modifier.testTag("topbar_btn_updates")
                    ) {
                        BadgedBox(
                            badge = {
                                if (hasUpdateAvailable) {
                                    Badge { Text("1") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Updates",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                tabs.forEach { tab ->
                    val isSelected = currentScreen == tab.screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.navigateTo(tab.screen) },
                        icon = {
                            if (tab.screen == Screen.Updates && hasUpdateAvailable) {
                                BadgedBox(badge = { Badge { Text("!") } }) {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    Screen.SellToday -> SellTodayScreen(viewModel = viewModel)
                    Screen.Items -> ItemsScreen(viewModel = viewModel)
                    Screen.ThisMonth -> MonthScreen(viewModel = viewModel)
                    Screen.History -> HistoryScreen(viewModel = viewModel)
                    Screen.Updates -> UpdatesScreen(viewModel = viewModel)
                }
            }
        }
    }
}
