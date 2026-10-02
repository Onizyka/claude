package ru.inventory.dc

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.inventory.dc.ui.AppViewModel
import ru.inventory.dc.ui.EditorScreen
import ru.inventory.dc.ui.HistoryScreen
import ru.inventory.dc.ui.PlacementScreen
import ru.inventory.dc.ui.SettingsScreen
import ru.inventory.dc.ui.theme.DcInventoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Светлые иконки статус-бара поверх синей шапки.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            DcInventoryTheme { InventoryApp() }
        }
    }
}

private object Routes {
    const val EDITOR = "editor"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PLACEMENT = "placement"
}

@Composable
private fun InventoryApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // При первом запуске спрашиваем место установки.
    val startDestination = remember { if (vm.placement.value.chosen) Routes.EDITOR else Routes.PLACEMENT }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.PLACEMENT) {
            val canGoBack = nav.previousBackStackEntry != null
            PlacementScreen(
                initial = vm.placement.value,
                canGoBack = canGoBack,
                onBack = { nav.popBackStack() },
                onDone = { placement ->
                    vm.setPlacement(placement)
                    if (canGoBack) {
                        nav.popBackStack()
                    } else {
                        nav.navigate(Routes.EDITOR) { popUpTo(Routes.PLACEMENT) { inclusive = true } }
                    }
                },
            )
        }
        composable(Routes.EDITOR) {
            EditorScreen(
                vm = vm,
                snackbar = snackbar,
                onOpenHistory = { nav.navigate(Routes.HISTORY) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onChangePlacement = { nav.navigate(Routes.PLACEMENT) { launchSingleTop = true } },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                vm = vm,
                snackbar = snackbar,
                onBack = { nav.popBackStack(Routes.EDITOR, inclusive = false) },
                onOpen = { record ->
                    vm.openRecord(record)
                    nav.popBackStack(Routes.EDITOR, inclusive = false)
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm = vm,
                snackbar = snackbar,
                onBack = { nav.popBackStack(Routes.EDITOR, inclusive = false) },
            )
        }
    }
}
