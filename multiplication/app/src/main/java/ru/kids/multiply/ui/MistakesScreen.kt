package ru.kids.multiply.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kids.multiply.game.Mode

/** Работа над ошибками по уровням: каждый уровень прорабатывается отдельно. */
@Composable
fun MistakesScreen(vm: TrainerViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = vm::toMenu) { Text("← Назад", fontSize = 18.sp) }
        }
        Text(
            text = "Работа над ошибками",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Palette.Orange,
        )
        Text(
            text = "Здесь только примеры, в которых ты ошибался. Пример уходит отсюда, " +
                "когда ответишь на него правильно два раза.",
            style = MaterialTheme.typography.bodyLarge,
        )

        MistakesButton(vm, Mode.MIXED, MaterialTheme.colorScheme.primary)
        MistakesButton(vm, Mode.HARD, Palette.Bad)
        MistakesButton(vm, Mode.BRACKETS, Palette.table(9))
    }
}

@Composable
private fun MistakesButton(vm: TrainerViewModel, level: Mode, color: Color) {
    val count = vm.mistakeCounts[level] ?: 0
    ModeButton(
        title = "${modeTitle(level, 0)} (работа над ошибками)",
        subtitle = if (count > 0) "Примеров с ошибками: $count" else "Ошибок нет — молодец!",
        color = if (count > 0) color else Palette.Disabled,
        stars = null,
        enabled = count > 0,
    ) { vm.start(Mode.MISTAKES, focus = level) }
}
