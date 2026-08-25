package com.filatelia.scanner.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.filatelia.scanner.StampScannerApp
import com.filatelia.scanner.ui.screens.CollectionScreen
import com.filatelia.scanner.ui.screens.ScanScreen
import com.filatelia.scanner.ui.screens.StampDetailScreen
import com.filatelia.scanner.ui.viewmodel.CollectionViewModel
import com.filatelia.scanner.ui.viewmodel.ScanViewModel
import com.filatelia.scanner.ui.viewmodel.ViewModelFactory

private const val ROUTE_SCAN = "scan"
private const val ROUTE_COLLECTION = "collection"
private const val ROUTE_DETAIL = "detail/{stampId}"

@Composable
fun AppNavigation(app: StampScannerApp) {
    val navController = rememberNavController()
    val factory = ViewModelFactory(app.stampRepository, app.aiRepository)

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == ROUTE_SCAN } == true,
                    onClick = {
                        navController.navigate(ROUTE_SCAN) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    label = { Text("Escanear") }
                )
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == ROUTE_COLLECTION } == true,
                    onClick = {
                        navController.navigate(ROUTE_COLLECTION) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Collections, contentDescription = null) },
                    label = { Text("Colección") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_SCAN,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable(ROUTE_SCAN) {
                val scanViewModel: ScanViewModel = viewModel(factory = factory)
                ScanScreen(viewModel = scanViewModel, onStampSaved = {
                    navController.navigate(ROUTE_COLLECTION) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                    }
                })
            }
            composable(ROUTE_COLLECTION) {
                val collectionViewModel: CollectionViewModel = viewModel(factory = factory)
                CollectionScreen(
                    viewModel = collectionViewModel,
                    onStampClick = { stamp -> navController.navigate("detail/${stamp.id}") }
                )
            }
            composable(ROUTE_DETAIL) { backStackEntry ->
                val stampId = backStackEntry.arguments?.getString("stampId")?.toLongOrNull()
                val collectionViewModel: CollectionViewModel = viewModel(factory = factory)
                val stamps by collectionViewModel.stamps.collectAsState()
                val stamp = stamps.firstOrNull { it.id == stampId }
                if (stamp != null) {
                    StampDetailScreen(
                        stamp = stamp,
                        onDelete = {
                            collectionViewModel.deleteStamp(stamp)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
