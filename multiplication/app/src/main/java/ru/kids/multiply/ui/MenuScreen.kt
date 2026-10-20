package ru.kids.multiply.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kids.multiply.game.Mode

@Composable
fun MenuScreen(vm: TrainerViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Таблица умножения",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        SectionTitle("Учим по одной")
        Text(
            text = "Выбери число: сначала посмотри таблицу, потом проверь себя.",
            style = MaterialTheme.typography.bodyLarge,
        )
        for (row in listOf(1..5, 6..10)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in row) {
                    TableCell(n, vm.stars(Mode.TABLE, n), Modifier.weight(1f)) { vm.openTable(n) }
                }
            }
        }

        SectionTitle("Проверка")
        ModeButton(
            title = "Всё вперемешку",
            subtitle = "Вся таблица от 2 до 10 в случайном порядке",
            color = MaterialTheme.colorScheme.primary,
            stars = vm.stars(Mode.MIXED),
        ) { vm.start(Mode.MIXED) }
        ModeButton(
            title = "Сложный уровень",
            subtitle = "Квадраты 10 × 10, 11 × 11 … 20 × 20 и трудные примеры до 12 × 12",
            color = Palette.Bad,
            stars = vm.stars(Mode.HARD),
        ) { vm.start(Mode.HARD) }
        if (vm.mistakesCount > 0) {
            ModeButton(
                title = "Работа над ошибками",
                subtitle = "Трудных примеров: ${vm.mistakesCount}",
                color = Palette.Orange,
                stars = null,
            ) { vm.start(Mode.MISTAKES) }
        }

        SectionTitle("Настройки")
        Text("Время на ответ", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(listOf(5, 8, 10, 15, 20), vm.seconds, { "$it с" }, vm::changeSeconds)
        Text("Примеров в тренировке", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(listOf(10, 20, 30), vm.questionCount, { "$it" }, vm::changeQuestionCount)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun TableCell(n: Int, stars: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(0.85f),
        shape = RoundedCornerShape(16.dp),
        color = Palette.table(n),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("$n", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Stars(stars, size = 13.sp, color = Color.White)
        }
    }
}

@Composable
private fun ModeButton(title: String, subtitle: String, color: Color, stars: Int?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = color,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitle, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))
            }
            if (stars != null) Stars(stars, size = 22.sp, color = Color.White)
        }
    }
}
