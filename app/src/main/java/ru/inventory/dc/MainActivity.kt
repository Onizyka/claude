package ru.inventory.dc

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.inventory.dc.data.DbState
import ru.inventory.dc.ui.AppViewModel
import ru.inventory.dc.ui.DatabaseSetupScreen
import ru.inventory.dc.ui.EditorScreen
import ru.inventory.dc.ui.HistoryScreen
import ru.inventory.dc.ui.LockScreen
import ru.inventory.dc.ui.PlacementScreen
import ru.inventory.dc.ui.SettingsScreen
import ru.inventory.dc.ui.WhatsNewDialog
import ru.inventory.dc.ui.WhatsNewPrefs
import ru.inventory.dc.ui.notesSince
import ru.inventory.dc.ui.theme.DcInventoryTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Конфиденциальные данные: запрет скриншотов и превью в списке недавних приложений.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        // Светлые иконки статус-бара поверх синей шапки.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            DcInventoryTheme { AppRoot(vm) }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        vm.onAppBackground()
    }
}

private object Routes {
    const val EDITOR = "editor"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PLACEMENT = "placement"
}

/** Без базы — экран создания/открытия, база заблокирована — ввод пароля, иначе — приложение. */
@Composable
private fun AppRoot(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    val state by vm.dbState.collectAsStateWithLifecycle()
    when (state) {
        DbState.NO_DATABASE -> DatabaseSetupScreen(vm, snackbar)
        DbState.LOCKED -> LockScreen(vm, snackbar)
        DbState.UNLOCKED -> InventoryApp(vm, snackbar)
    }
}

@Composable
private fun InventoryApp(vm: AppViewModel, snackbar: SnackbarHostState) {
    val nav = rememberNavController()
    val context = LocalContext.current

    // После обновления показываем, что изменилось.
    var whatsNew by remember { mutableStateOf(notesSince(WhatsNewPrefs.lastSeen(context))) }
    if (whatsNew.isNotEmpty()) {
        WhatsNewDialog("Что нового", whatsNew) {
            WhatsNewPrefs.markSeen(context)
            whatsNew = emptyList()
        }
    }

    // Место установки ещё не выбрано — спрашиваем его первым делом.
    val startDestination = remember { if (vm.currentPlacement().chosen) Routes.EDITOR else Routes.PLACEMENT }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.PLACEMENT) {
            val canGoBack = nav.previousBackStackEntry != null
            PlacementScreen(
                initial = vm.currentPlacement(),
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
