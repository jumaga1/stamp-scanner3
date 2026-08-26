package com.filatelia.scanner.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.filatelia.scanner.data.StampEntity
import com.filatelia.scanner.ui.screens.CollectionScreen
import com.filatelia.scanner.ui.screens.ScanScreen
import com.filatelia.scanner.ui.screens.StampDetailScreen
import com.filatelia.scanner.ui.viewmodel.CollectionViewModel
import com.filatelia.scanner.ui.viewmodel.ScanViewModel
import com.filatelia.scanner.ui.viewmodel.ViewModelFactory

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Scan : Screen("scan", "Escanear", Icons.Default.CameraAlt)
    object Collection : Screen("collection", "Colección", Icons.Default.CollectionsBookmark)
}

@Composable
fun AppNavigation(
    factory: ViewModelFactory
) {
    val navController = rememberNavController()
    var selectedStampForDetail by remember { mutableStateOf<StampEntity?>(null) }

    val scanViewModel: ScanViewModel = viewModel(factory = factory)
    val collectionViewModel: CollectionViewModel = viewModel(factory = factory)

    Scaffold(
        bottomBar = {
            if (selectedStampForDetail == null) {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    val items = listOf(Screen.Scan, Screen.Collection)
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (selectedStampForDetail != null) {
            StampDetailScreen(
                stamp = selectedStampForDetail!!,
                onBack = { selectedStampForDetail = null },
                onDelete = {
                    collectionViewModel.deleteStamp(selectedStampForDetail!!)
                    selectedStampForDetail = null
                }
            )
        } else {
            NavHost(
                navController = navController,
                startDestination = Screen.Scan.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Scan.route) {
                    ScanScreen(
                        viewModel = scanViewModel,
                        onStampSaved = {
                            navController.navigate(Screen.Collection.route) {
                                popUpTo(Screen.Scan.route) { inclusive = false }
                            }
                        }
                    )
                }
                composable(Screen.Collection.route) {
                    CollectionScreen(
                        viewModel = collectionViewModel,
                        onStampClick = { stamp ->
                            selectedStampForDetail = stamp
                        }
                    )
                }
            }
        }
    }
}
