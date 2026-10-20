package ru.kids.multiply

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.kids.multiply.game.Mode
import ru.kids.multiply.ui.GameScreen
import ru.kids.multiply.ui.IntroScreen
import ru.kids.multiply.ui.MenuScreen
import ru.kids.multiply.ui.MistakesScreen
import ru.kids.multiply.ui.MultiplicationTheme
import ru.kids.multiply.ui.ResultScreen
import ru.kids.multiply.ui.Screen
import ru.kids.multiply.ui.TrainerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Фон всегда светлый, поэтому и значки системных панелей тёмные.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            MultiplicationTheme { App() }
        }
    }
}

@Composable
private fun App(vm: TrainerViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resume() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            when (val screen = vm.screen) {
                Screen.Menu -> MenuScreen(vm)
                is Screen.Intro -> {
                    BackHandler { vm.toMenu() }
                    IntroScreen(
                        table = screen.table,
                        onStart = { vm.start(Mode.TABLE, screen.table) },
                        onBack = vm::toMenu,
                    )
                }
                Screen.Mistakes -> {
                    BackHandler { vm.toMenu() }
                    MistakesScreen(vm)
                }
                Screen.Game -> {
                    BackHandler { vm.leaveRound() }
                    vm.round?.let { GameScreen(it, onAnswer = vm::answer, onQuit = vm::leaveRound) }
                }
                Screen.Result -> {
                    BackHandler { vm.leaveRound() }
                    vm.round?.let { ResultScreen(it, onAgain = vm::restart, onMenu = vm::leaveRound) }
                }
            }
        }
    }
}
